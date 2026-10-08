package com.ynks.burndown.data.github

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GraphQlRequest(val query: String, val variables: Map<String, JsonElement> = emptyMap())

@Serializable
data class GraphQlError(val type: String? = null, val message: String = "")

@Serializable
data class GraphQlResponse<T>(val data: T? = null, val errors: List<GraphQlError>? = null, val message: String? = null)

@Serializable
data class ProjectOwnerData(val organization: ProjectOwner? = null, val user: ProjectOwner? = null)

@Serializable
data class ProjectOwner(val projectV2: ProjectNode? = null)

@Serializable
data class ProjectNode(val id: String, val title: String, val fields: FieldConnection)

@Serializable
data class FieldConnection(val nodes: List<FieldNode?> = emptyList())

@Serializable
data class FieldNode(
    val id: String? = null,
    val name: String? = null,
    val dataType: String? = null,
    val configuration: IterationConfiguration? = null,
)

@Serializable
data class IterationConfiguration(
    val iterations: List<IterationNode> = emptyList(),
    val completedIterations: List<IterationNode> = emptyList(),
)

@Serializable
data class IterationNode(val id: String, val title: String, val startDate: String, val duration: Int)

@Serializable
data class IterationFieldData(val node: IterationFieldProject? = null)

@Serializable
data class IterationFieldProject(val field: FieldNode? = null)

@Serializable
data class ItemsData(val node: ItemsProject? = null)

@Serializable
data class ItemsProject(val items: ItemConnection)

@Serializable
data class ItemConnection(val pageInfo: PageInfo, val nodes: List<ItemNode?> = emptyList())

@Serializable
data class PageInfo(val hasNextPage: Boolean, val endCursor: String? = null)

@Serializable
data class ItemNode(
    val isArchived: Boolean = false,
    val iteration: IterationValue? = null,
    val points: NumberValue? = null,
    val content: ContentNode? = null,
)

@Serializable
data class IterationValue(val iterationId: String? = null)

@Serializable
data class NumberValue(val number: Double? = null)

@Serializable
data class ContentNode(
    val __typename: String? = null,
    val closedAt: String? = null,
    val mergedAt: String? = null,
)
