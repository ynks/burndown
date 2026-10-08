package com.ynks.burndown.data.cache

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.ynks.burndown.domain.HourPoint
import com.ynks.burndown.domain.SprintSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

enum class StatusKind { NONE, OK, AUTH, NETWORK, NO_SPRINT, OTHER }

@Serializable
data class SyncStatus(
    val kind: StatusKind = StatusKind.NONE,
    val message: String? = null,
    val atMillis: Long = 0,
)

@Serializable
data class StoreState(
    val snapshot: SprintSnapshot? = null,
    // keyed by iteration and metric then by epoch hour
    val history: Map<String, Map<Long, HourPoint>> = emptyMap(),
    val status: SyncStatus = SyncStatus(),
) {
    fun historyFor(snapshot: SprintSnapshot): Map<Long, HourPoint> =
        history[historyKey(snapshot)].orEmpty()

    companion object {
        fun historyKey(snapshot: SprintSnapshot) = "${snapshot.iteration.id}|${snapshot.metricKey}"
    }
}

internal object StoreSerializer : Serializer<StoreState> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val defaultValue = StoreState()

    override suspend fun readFrom(input: InputStream): StoreState = try {
        json.decodeFromString(StoreState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable sprint store", e)
    }

    override suspend fun writeTo(t: StoreState, output: OutputStream) {
        output.write(json.encodeToString(StoreState.serializer(), t).encodeToByteArray())
    }
}

private val Context.sprintDataStore by dataStore(fileName = "sprint_store.json", serializer = StoreSerializer)

class SprintStore(context: Context) {
    private val store = context.applicationContext.sprintDataStore

    val state: Flow<StoreState> = store.data

    suspend fun current(): StoreState = store.data.first()

    suspend fun update(transform: (StoreState) -> StoreState) {
        store.updateData(transform)
    }

    suspend fun recordSnapshot(snapshot: SprintSnapshot, nowMillis: Long) {
        update { old ->
            val key = StoreState.historyKey(snapshot)
            val hour = Math.floorDiv(nowMillis / 1000, 3600L)
            val point = HourPoint(
                total = snapshot.items.sumOf { it.weight },
                completed = snapshot.items.filter { it.doneAt != null }.sumOf { it.weight },
            )
            val mine = old.history[key].orEmpty() + (hour to point)
            old.copy(
                snapshot = snapshot,
                history = mapOf(key to mine),
                status = SyncStatus(StatusKind.OK, null, nowMillis),
            )
        }
    }
}
