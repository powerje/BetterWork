package dev.betterwork.platform

import co.touchlab.kermit.Logger
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

object LibrarySync {
    const val PATH = "/betterwork/library"

    suspend fun publish(app: BetterApp) {
        try {
            val request =
                PutDataMapRequest.create(PATH)
                    .apply {
                        dataMap.putAsset(
                            "library",
                            Asset.createFromBytes(app.repository.export().encodeToByteArray()),
                        )
                        dataMap.putLong("revision", app.repository.library.value.revision)
                        dataMap.putString("origin", app.repository.library.value.origin)
                    }
                    .asPutDataRequest()
                    .setUrgent()
            Wearable.getDataClient(app).putDataItem(request).await()
            app.syncStatus.value = "Library queued for watch sync"
        } catch (error: ApiException) {
            Logger.w(error) { "Library sync unavailable; local library is retained" }
            app.syncStatus.value = "Saved locally; watch sync unavailable"
        } catch (error: IOException) {
            Logger.w(error) { "Library sync unavailable; local library is retained" }
            app.syncStatus.value = "Saved locally; watch sync unavailable"
        } catch (error: IllegalArgumentException) {
            Logger.w(error) { "Library sync unavailable; local library is retained" }
            app.syncStatus.value = "Saved locally; watch sync unavailable"
        } catch (error: SecurityException) {
            Logger.w(error) { "Library sync unavailable; local library is retained" }
            app.syncStatus.value = "Saved locally; watch sync unavailable"
        }
    }

    suspend fun readLatest(app: BetterApp) {
        try {
            val items = Wearable.getDataClient(app).dataItems.await()
            val assets =
                try {
                    items
                        .filter { it.uri.path == PATH }
                        .mapNotNull { DataMapItem.fromDataItem(it).dataMap.getAsset("library") }
                } finally {
                    items.release()
                }
            assets.forEach { receive(app, it) }
        } catch (error: ApiException) {
            Logger.w(error) { "Unable to read watch library updates" }
            app.syncStatus.value = "Using library saved on watch"
        } catch (error: IOException) {
            Logger.w(error) { "Unable to read watch library updates" }
            app.syncStatus.value = "Using library saved on watch"
        } catch (error: IllegalArgumentException) {
            Logger.w(error) { "Unable to read watch library updates" }
            app.syncStatus.value = "Using library saved on watch"
        } catch (error: SecurityException) {
            Logger.w(error) { "Unable to read watch library updates" }
            app.syncStatus.value = "Using library saved on watch"
        }
    }

    suspend fun receive(app: BetterApp, asset: Asset) {
        if (!app.isWatch) return
        try {
            val response = Wearable.getDataClient(app).getFdForAsset(asset).await()
            val text =
                withContext(Dispatchers.IO) {
                    response.inputStream.use { stream ->
                        requireNotNull(stream) { "Missing library asset" }
                        stream.readLibraryText()
                    }
                }
            withContext(Dispatchers.Main.immediate) {
                app.repository.receive(text)
                app.syncStatus.value = "Library received from phone"
            }
        } catch (error: ApiException) {
            Logger.w(error) { "Rejected library update" }
            app.syncStatus.value = "Update failed; saved library retained"
        } catch (error: IOException) {
            Logger.w(error) { "Rejected library update" }
            app.syncStatus.value = "Update failed; saved library retained"
        } catch (error: IllegalArgumentException) {
            Logger.w(error) { "Rejected library update" }
            app.syncStatus.value = "Update failed; saved library retained"
        } catch (error: SecurityException) {
            Logger.w(error) { "Rejected library update" }
            app.syncStatus.value = "Update failed; saved library retained"
        }
    }
}

class LibrarySyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val app = application as BetterApp
        if (!app.isWatch) return
        for (event in events) {
            if (
                event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == LibrarySync.PATH
            ) {
                val asset =
                    DataMapItem.fromDataItem(event.dataItem).dataMap.getAsset("library") ?: continue
                app.scope.launch { LibrarySync.receive(app, asset) }
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        val app = application as BetterApp
        app.scope.launch {
            if (app.isWatch) LibrarySync.readLatest(app) else LibrarySync.publish(app)
        }
    }
}
