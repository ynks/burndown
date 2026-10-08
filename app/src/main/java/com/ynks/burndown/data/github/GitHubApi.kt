package com.ynks.burndown.data.github

import com.ynks.burndown.domain.Iteration
import com.ynks.burndown.domain.Metric
import com.ynks.burndown.domain.SprintItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

class GitHubAuthException(message: String) : Exception(message)

class GitHubApiException(message: String) : Exception(message)

data class ProjectInfo(
    val id: String,
    val title: String,
    val iterationFields: List<String>,
    val numberFields: List<String>,
)

class GitHubApi(
    private val token: String,
    private val client: OkHttpClient = defaultClient,
    private val endpoint: String = "https://api.github.com/graphql",
) {

    suspend fun loadProject(owner: String, number: Int): ProjectInfo {
        val data = execute(
            ProjectOwnerData.serializer(),
            PROJECT_QUERY,
            mapOf("login" to JsonPrimitive(owner), "number" to JsonPrimitive(number)),
            // login is either an org or a user so one side always errors
            tolerateErrors = { it.organization?.projectV2 != null || it.user?.projectV2 != null },
        )
        val project = data.organization?.projectV2 ?: data.user?.projectV2
            ?: throw GitHubApiException("Project #$number not found for \"$owner\"")
        val fields = project.fields.nodes.filterNotNull()
        return ProjectInfo(
            id = project.id,
            title = project.title,
            iterationFields = fields.filter { it.configuration != null }.mapNotNull { it.name },
            numberFields = fields.filter { it.dataType == "NUMBER" }.mapNotNull { it.name },
        )
    }

    suspend fun loadIterations(projectId: String, fieldName: String): List<Iteration> {
        val data = execute(
            IterationFieldData.serializer(),
            ITERATIONS_QUERY,
            mapOf("id" to JsonPrimitive(projectId), "field" to JsonPrimitive(fieldName)),
        )
        val config = data.node?.field?.configuration
            ?: throw GitHubApiException("Iteration field \"$fieldName\" not found")
        return (config.iterations + config.completedIterations)
            .map { Iteration(it.id, it.title, it.startDate, it.duration) }
    }

    suspend fun loadSprintItems(
        projectId: String,
        iterationFieldName: String,
        iterationId: String,
        metric: Metric,
        pointsFieldName: String?,
    ): List<SprintItem> {
        val result = ArrayList<SprintItem>()
        var cursor: JsonElement = JsonNull
        do {
            val data = execute(
                ItemsData.serializer(),
                ITEMS_QUERY,
                mapOf(
                    "id" to JsonPrimitive(projectId),
                    "after" to cursor,
                    "iterationField" to JsonPrimitive(iterationFieldName),
                    "withPoints" to JsonPrimitive(metric == Metric.POINTS),
                    "pointsField" to JsonPrimitive(pointsFieldName ?: ""),
                ),
            )
            val items = data.node?.items ?: throw GitHubApiException("Project not found")
            for (node in items.nodes.filterNotNull()) {
                if (node.isArchived || node.iteration?.iterationId != iterationId) continue
                val weight = if (metric == Metric.POINTS) node.points?.number ?: 0.0 else 1.0
                result += SprintItem(weight, doneAtMillis(node.content))
            }
            cursor = items.pageInfo.endCursor?.let(::JsonPrimitive) ?: JsonNull
        } while (items.pageInfo.hasNextPage)
        return result
    }

    private suspend fun <T> execute(
        serializer: KSerializer<T>,
        query: String,
        variables: Map<String, JsonElement>,
        tolerateErrors: (T) -> Boolean = { false },
    ): T = withContext(Dispatchers.IO) {
        val body = json.encodeToString(GraphQlRequest.serializer(), GraphQlRequest(query, variables))
            .toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (response.code == 401) throw GitHubAuthException("GitHub rejected the token (401)")
            if (response.code == 403 && text.contains("rate limit", ignoreCase = true)) {
                throw IOException("GitHub rate limit reached")
            }
            if (!response.isSuccessful && text.isBlank()) throw IOException("GitHub HTTP ${response.code}")
            val parsed = try {
                json.decodeFromString(GraphQlResponse.serializer(serializer), text)
            } catch (e: Exception) {
                throw GitHubApiException("Unexpected GitHub response (HTTP ${response.code})")
            }
            val data = parsed.data
            val errors = parsed.errors.orEmpty()
            if (data != null && (errors.isEmpty() || tolerateErrors(data))) return@use data
            if (errors.any { it.type == "RATE_LIMITED" }) throw IOException("GitHub rate limit reached")
            if (errors.isNotEmpty()) {
                val message = errors.joinToString("; ") { it.message }
                if (errors.any { it.type == "INSUFFICIENT_SCOPES" || it.type == "FORBIDDEN" }) {
                    throw GitHubAuthException(message)
                }
                throw GitHubApiException(message)
            }
            throw GitHubApiException(parsed.message ?: "Empty response from GitHub")
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
        internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .build()
        }

        internal fun doneAtMillis(content: ContentNode?): Long? {
            val stamp = content?.mergedAt ?: content?.closedAt ?: return null
            return runCatching { Instant.parse(stamp).toEpochMilli() }.getOrNull()
        }

        private const val FIELD_FRAGMENT = """
            ... on ProjectV2FieldCommon { id name dataType }
            ... on ProjectV2IterationField {
              configuration {
                iterations { id title startDate duration }
                completedIterations { id title startDate duration }
              }
            }
        """

        private const val PROJECT_BODY = """
            projectV2(number: ${'$'}number) {
              id
              title
              fields(first: 50) { nodes { $FIELD_FRAGMENT } }
            }
        """

        internal const val PROJECT_QUERY = """
            query(${'$'}login: String!, ${'$'}number: Int!) {
              organization(login: ${'$'}login) { $PROJECT_BODY }
              user(login: ${'$'}login) { $PROJECT_BODY }
            }
        """

        internal const val ITERATIONS_QUERY = """
            query(${'$'}id: ID!, ${'$'}field: String!) {
              node(id: ${'$'}id) {
                ... on ProjectV2 {
                  field(name: ${'$'}field) {
                    ... on ProjectV2IterationField {
                      configuration {
                        iterations { id title startDate duration }
                        completedIterations { id title startDate duration }
                      }
                    }
                  }
                }
              }
            }
        """

        internal const val ITEMS_QUERY = """
            query(
              ${'$'}id: ID!, ${'$'}after: String, ${'$'}iterationField: String!,
              ${'$'}withPoints: Boolean!, ${'$'}pointsField: String!
            ) {
              node(id: ${'$'}id) {
                ... on ProjectV2 {
                  items(first: 100, after: ${'$'}after) {
                    pageInfo { hasNextPage endCursor }
                    nodes {
                      isArchived
                      iteration: fieldValueByName(name: ${'$'}iterationField) {
                        ... on ProjectV2ItemFieldIterationValue { iterationId }
                      }
                      points: fieldValueByName(name: ${'$'}pointsField) @include(if: ${'$'}withPoints) {
                        ... on ProjectV2ItemFieldNumberValue { number }
                      }
                      content {
                        __typename
                        ... on Issue { closedAt }
                        ... on PullRequest { closedAt mergedAt }
                      }
                    }
                  }
                }
              }
            }
        """
    }
}
