package com.ynks.burndown.data

import com.ynks.burndown.data.github.GitHubApi
import com.ynks.burndown.data.github.GitHubAuthException
import com.ynks.burndown.domain.Metric
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class GitHubApiTest {

    private class Fake(private val replies: List<Pair<Int, String>>) : Interceptor {
        val requests = mutableListOf<String>()
        private var next = 0
        override fun intercept(chain: Interceptor.Chain): Response {
            val buffer = okio.Buffer()
            chain.request().body?.writeTo(buffer)
            requests += buffer.readUtf8()
            val (code, body) = replies[minOf(next++, replies.lastIndex)]
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fake")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun api(fake: Fake) = GitHubApi("token", OkHttpClient.Builder().addInterceptor(fake).build())

    @Test
    fun userOwnedProjectResolvesEvenThoughOrganizationLookupErrors() = runBlocking {
        val fake = Fake(listOf(200 to """
            {"data":{"organization":null,"user":{"projectV2":{"id":"PVT_1","title":"Roadmap","fields":{"nodes":[
              {"id":"F1","name":"Title","dataType":"TITLE"},
              {"id":"F2","name":"Sprint","dataType":"ITERATION","configuration":{"iterations":[],"completedIterations":[]}},
              {"id":"F3","name":"Story points","dataType":"NUMBER"},
              {},
              null
            ]}}}},
             "errors":[{"type":"NOT_FOUND","message":"Could not resolve to an Organization with the login of 'me'."}]}
        """.trimIndent()))
        val project = api(fake).loadProject("me", 4)
        assertEquals("PVT_1", project.id)
        assertEquals(listOf("Sprint"), project.iterationFields)
        assertEquals(listOf("Story points"), project.numberFields)
        assertTrue(fake.requests.single().contains("\"number\":4"))
    }

    @Test
    fun iterationsIncludeCurrentAndCompleted() = runBlocking {
        val fake = Fake(listOf(200 to """
            {"data":{"node":{"field":{"configuration":{
              "iterations":[{"id":"i2","title":"S2","startDate":"2026-10-05","duration":14}],
              "completedIterations":[{"id":"i1","title":"S1","startDate":"2026-09-21","duration":14}]}}}}}
        """.trimIndent()))
        val iterations = api(fake).loadIterations("PVT_1", "Sprint")
        assertEquals(listOf("i2", "i1"), iterations.map { it.id })
        assertEquals(14, iterations.first().durationDays)
    }

    private fun itemsPage(hasNext: Boolean, cursor: String?, nodes: String) = """
        {"data":{"node":{"items":{"pageInfo":{"hasNextPage":$hasNext,"endCursor":${cursor?.let { "\"$it\"" }}},"nodes":[$nodes]}}}}
    """.trimIndent()

    @Test
    fun itemsArePagedFilteredAndWeighted() = runBlocking {
        val page1 = itemsPage(true, "c1", """
            {"isArchived":false,"iteration":{"iterationId":"i2"},"points":{"number":5.0},"content":{"__typename":"Issue","closedAt":"2026-10-06T10:00:00Z"}},
            {"isArchived":false,"iteration":{"iterationId":"other"},"points":{"number":8.0},"content":{"__typename":"Issue","closedAt":null}},
            {"isArchived":true,"iteration":{"iterationId":"i2"},"points":{"number":2.0},"content":{"__typename":"Issue","closedAt":null}}
        """.trimIndent())
        val page2 = itemsPage(false, null, """
            {"isArchived":false,"iteration":{"iterationId":"i2"},"points":{"number":3.0},"content":{"__typename":"PullRequest","closedAt":"2026-10-07T00:00:00Z","mergedAt":"2026-10-06T22:00:00Z"}},
            {"isArchived":false,"iteration":{"iterationId":"i2"},"points":null,"content":{"__typename":"DraftIssue"}},
            {"isArchived":false,"iteration":{"iterationId":"i2"},"points":{"number":1.0},"content":{"__typename":"Issue","closedAt":null}},
            null
        """.trimIndent())
        val fake = Fake(listOf(200 to page1, 200 to page2))

        val points = api(fake).loadSprintItems("PVT_1", "Sprint", "i2", Metric.POINTS, "Story points")
        assertEquals(listOf(5.0, 3.0, 0.0, 1.0), points.map { it.weight })
        assertEquals(Instant.parse("2026-10-06T10:00:00Z").toEpochMilli(), points[0].doneAt)
        assertEquals(Instant.parse("2026-10-06T22:00:00Z").toEpochMilli(), points[1].doneAt)
        assertEquals(listOf(null, null), points.drop(2).map { it.doneAt })

        assertEquals(2, fake.requests.size)
        assertTrue(fake.requests[0].contains("\"withPoints\":true"))
        assertTrue(fake.requests[1].contains("\"after\":\"c1\""))

        val counted = api(Fake(listOf(200 to page2))).loadSprintItems("PVT_1", "Sprint", "i2", Metric.COUNT, null)
        assertEquals(listOf(1.0, 1.0, 1.0), counted.map { it.weight })
    }

    @Test
    fun badCredentialsAreReportedAsAuthErrors() {
        val fake = Fake(listOf(401 to """{"message":"Bad credentials"}"""))
        try {
            runBlocking { api(fake).loadIterations("PVT_1", "Sprint") }
            fail("expected GitHubAuthException")
        } catch (e: GitHubAuthException) {
            assertTrue(e.message!!.contains("401"))
        }
    }

    @Test
    fun missingScopesAreReportedAsAuthErrors() {
        val fake = Fake(listOf(200 to """{"data":null,"errors":[{"type":"INSUFFICIENT_SCOPES","message":"needs read:project"}]}"""))
        try {
            runBlocking { api(fake).loadIterations("PVT_1", "Sprint") }
            fail("expected GitHubAuthException")
        } catch (e: GitHubAuthException) {
            assertTrue(e.message!!.contains("read:project"))
        }
    }
}
