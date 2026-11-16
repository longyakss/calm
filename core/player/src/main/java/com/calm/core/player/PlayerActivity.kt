package com.calm.core.player

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.calm.core.common.theme.CalmTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var controllerState by mutableStateOf<MediaController?>(null)
    private var mediaLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val source = intent.dataString ?: intent.getStringExtra("path") ?: ""
        val title = intent.getStringExtra("title") ?: source.substringAfterLast('/').ifBlank { "播放" }

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            controllerState = try {
                controllerFuture?.get()
            } catch (_: Exception) {
                null
            }
        }, MoreExecutors.directExecutor())

        setContent {
            CalmTheme {
                controllerState?.let { controller ->
                    PlayerScreen(
                        controller = controller,
                        source = source,
                        title = title,
                        ensureLoaded = { ensureMediaLoaded(controller, source) },
                        onBack = { finish() },
                    )
                } ?: run {
                    androidx.compose.material3.Text("正在启动播放器…")
                }
            }
        }
    }

    private fun ensureMediaLoaded(controller: MediaController, source: String) {
        if (mediaLoaded || source.isBlank()) return
        mediaLoaded = true
        val item = androidx.media3.common.MediaItem.Builder()
            .setUri(source)
            .build()
        controller.setMediaItem(item)
        controller.prepare()
        controller.play()
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
