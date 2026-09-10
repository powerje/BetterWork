package dev.betterwork.platform

import android.net.Uri
import co.touchlab.kermit.Logger
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dev.betterwork.data.LibraryCodec
import dev.betterwork.domain.Checkpoint
import dev.betterwork.domain.SessionCommand
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Wire format for the phone-owned session mirror. It never represents a local engine. */
@Serializable
data class RemoteSession(
    val schema: Int = 1,
    val sessionId: String,
    val revision: Long,
    val checkpoint: Checkpoint,
    val publishedAtElapsedMs: Long,
    val receivedAtElapsedMs: Long = 0,
)

@Serializable
data class RemoteCommand(
    val schema: Int = 1,
    val sessionId: String,
    val commandId: String,
    val expectedRevision: Long,
    val command: SessionCommand,
)

@Serializable
data class RemoteAck(
    val schema: Int = 1,
    val commandId: String,
    val accepted: Boolean,
    val reason: String? = null,
)

object SessionTransport {
    const val SESSION_PATH = "/betterwork/session"
    const val COMMAND_PATH = "/betterwork/session/command"
    const val ACK_PATH = "/betterwork/session/ack"
    const val SESSION_CAPABILITY = "betterwork_session_v1"
}

object SessionSync {
    suspend fun sendAck(app: BetterApp, nodeId: String, ack: RemoteAck) {
        runCatching {
                Wearable.getMessageClient(app)
                    .sendMessage(
                        nodeId,
                        SessionTransport.ACK_PATH,
                        LibraryCodec.json.encodeToString(ack).encodeToByteArray(),
                    )
                    .await()
            }
            .onFailure { Logger.w(it) { "Unable to acknowledge remote session command" } }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun sendCommand(app: BetterApp, command: RemoteCommand): Boolean {
        val node = app.remoteNodeId.value ?: return false
        return try {
            Wearable.getMessageClient(app)
                .sendMessage(
                    node,
                    SessionTransport.COMMAND_PATH,
                    LibraryCodec.json.encodeToString(command).encodeToByteArray(),
                )
                .await()
            true
        } catch (error: Exception) {
            Logger.w(error) { "Unable to send remote session command" }
            false
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun publish(app: BetterApp, checkpoint: Checkpoint?, revision: Long) {
        try {
            val client = Wearable.getDataClient(app)
            if (checkpoint == null) {
                client.deleteDataItems(Uri.parse("wear:${SessionTransport.SESSION_PATH}")).await()
                return
            }
            val payload =
                RemoteSession(
                    sessionId = app.sessionHandle,
                    revision = revision,
                    checkpoint = checkpoint,
                    publishedAtElapsedMs = android.os.SystemClock.elapsedRealtime(),
                )
            val request =
                PutDataMapRequest.create(SessionTransport.SESSION_PATH)
                    .apply {
                        dataMap.putAsset(
                            "session",
                            Asset.createFromBytes(
                                LibraryCodec.json.encodeToString(payload).encodeToByteArray()
                            ),
                        )
                        dataMap.putLong("revision", revision)
                        dataMap.putString("sessionId", app.sessionHandle)
                    }
                    .asPutDataRequest()
                    .setUrgent()
            client.putDataItem(request).await()
        } catch (error: Exception) {
            Logger.w(error) { "Unable to publish phone session mirror" }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun receive(app: BetterApp, asset: Asset, nodeId: String? = null) {
        if (!app.isWatch) return
        try {
            val response = Wearable.getDataClient(app).getFdForAsset(asset).await()
            val text =
                withContext(Dispatchers.IO) {
                    response.inputStream.use { it.readBytes().decodeToString() }
                }
            app.remoteSession.value =
                LibraryCodec.json
                    .decodeFromString<RemoteSession>(text)
                    .copy(receivedAtElapsedMs = android.os.SystemClock.elapsedRealtime())
            app.remoteNodeId.value = nodeId
            app.remoteConnected.value = nodeId != null
        } catch (error: Exception) {
            Logger.w(error) { "Rejected phone session mirror" }
        }
    }
}

class SessionSyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val app = application as BetterApp
        if (!app.isWatch) return
        for (event in events) {
            if (event.dataItem.uri.path != SessionTransport.SESSION_PATH) continue
            if (event.type == DataEvent.TYPE_DELETED) {
                app.remoteSession.value = null
                app.remoteNodeId.value = null
                app.remoteConnected.value = false
            } else {
                DataMapItem.fromDataItem(event.dataItem).dataMap.getAsset("session")?.let { asset ->
                    app.scope.launch { SessionSync.receive(app, asset, event.dataItem.uri.host) }
                }
            }
        }
    }

    override fun onPeerConnected(peer: com.google.android.gms.wearable.Node) {
        val app = application as BetterApp
        if (app.isWatch) {
            app.remoteNodeId.value = peer.id
            app.remoteConnected.value = true
        }
    }

    override fun onPeerDisconnected(peer: com.google.android.gms.wearable.Node) {
        val app = application as BetterApp
        if (app.isWatch && app.remoteNodeId.value == peer.id) app.remoteConnected.value = false
    }

    override fun onMessageReceived(messageEvent: com.google.android.gms.wearable.MessageEvent) {
        val app = application as BetterApp
        if (messageEvent.path == SessionTransport.ACK_PATH && app.isWatch) {
            runCatching {
                    LibraryCodec.json.decodeFromString<RemoteAck>(
                        messageEvent.data.decodeToString()
                    )
                }
                .onSuccess { app.remoteAck.value = it }
                .onFailure { Logger.w(it) { "Rejected malformed session acknowledgement" } }
            return
        }
        if (app.isWatch || messageEvent.path != SessionTransport.COMMAND_PATH) return
        runCatching {
                LibraryCodec.json.decodeFromString<RemoteCommand>(
                    messageEvent.data.decodeToString()
                )
            }
            .onSuccess { command ->
                app.scope.launch {
                    val accepted = app.acceptRemoteCommand(command)
                    SessionSync.sendAck(
                        app,
                        messageEvent.sourceNodeId,
                        RemoteAck(
                            commandId = command.commandId,
                            accepted = accepted,
                            reason =
                                if (accepted) null
                                else "Session changed or command was already applied",
                        ),
                    )
                }
            }
            .onFailure { Logger.w(it) { "Rejected malformed session command" } }
    }
}

fun newRemoteCommand(session: RemoteSession, command: SessionCommand): RemoteCommand =
    RemoteCommand(
        sessionId = session.sessionId,
        commandId = UUID.randomUUID().toString(),
        expectedRevision = session.revision,
        command = command,
    )
