package com.calm.core.player

import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * 播放器界面（对应设计方案 5.12 / 5.14）：
 * 左 1/3 亮度、右 1/3 音量、中间 1/3 进度；双击两侧 ±10s / 中间暂停；边缘指示器。
 */
@Composable
fun PlayerScreen(
    controller: Player,
    source: String,
    title: String,
    ensureLoaded: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }

    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var speedMenu by remember { mutableStateOf(false) }

    // 手势指示器（PL-013）
    var indicator by remember { mutableStateOf("") }
    var indicatorProgress by remember { mutableFloatStateOf(0f) }
    var indicatorVisible by remember { mutableStateOf(false) }

    LaunchedEffect(controller) {
        ensureLoaded()
        while (true) {
            playing = controller.isPlaying
            if (!seeking) {
                position = controller.currentPosition
                duration = controller.duration.coerceAtLeast(0)
            }
            delay(300)
        }
    }

    fun showIndicator(text: String, progress: Float) {
        indicator = text
        indicatorProgress = progress.coerceIn(0f, 1f)
        indicatorVisible = true
    }

    LaunchedEffect(indicatorVisible) {
        if (indicatorVisible) {
            delay(2000)
            indicatorVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 视频画面
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setPlayer(controller)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 手势层（PL-009/011/012）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val w = size.width.toFloat()
                            when {
                                offset.x < w / 3 -> controller.seekTo((controller.currentPosition - 10_000).coerceAtLeast(0))
                                offset.x > w * 2 / 3 -> controller.seekTo(controller.currentPosition + 10_000)
                                else -> if (controller.isPlaying) controller.pause() else controller.play()
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val w = size.width.toFloat()
                            when {
                                offset.x < w / 3 -> showIndicator("亮度", windowBrightness(context))
                                offset.x > w * 2 / 3 -> {
                                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    showIndicator("音量", audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat())
                                }
                                else -> {
                                    seeking = true
                                    seekValue = controller.currentPosition.toFloat()
                                    showIndicator(fmtTime(controller.currentPosition), if (duration > 0) controller.currentPosition / duration.toFloat() else 0f)
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val startX = if (change.position.x < w / 3) "L" else if (change.position.x > w * 2 / 3) "R" else "C"
                            when (startX) {
                                "L" -> {
                                    val b = (windowBrightness(context) + dragAmount.y / -h * 1.5f).coerceIn(0f, 1f)
                                    setWindowBrightness(context, b)
                                    showIndicator("亮度 ${(b * 100).toInt()}%", b)
                                }
                                "R" -> {
                                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val nv = (cur + (dragAmount.y / -h * max).toInt()).coerceIn(0, max)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                                    showIndicator("音量 ${(nv * 100 / max)}%", nv / max.toFloat())
                                }
                                else -> {
                                    seekValue = (seekValue + dragAmount.y / -h * duration).coerceIn(0f, duration.toFloat())
                                    showIndicator(
                                        "${if (dragAmount.y < 0) "+" else "-"}${fmtTime(seekValue.toLong())} / ${fmtTime(duration)}",
                                        if (duration > 0) seekValue / duration else 0f,
                                    )
                                }
                            }
                        },
                        onDragEnd = {
                            if (seeking) {
                                controller.seekTo(seekValue.toLong())
                                seeking = false
                            }
                            indicatorVisible = false
                        },
                    )
                },
        )

        // 指示器（边缘进度条 + 百分比，2 秒后消失）
        if (indicatorVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x99333333))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(indicator, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = indicatorProgress,
                    modifier = Modifier.width(180.dp),
                    color = Color.White,
                )
            }
        }

        // 控制层
        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Box {
                    IconButton(onClick = { speedMenu = true }) {
                        Text(
                            "${if (speed == speed.toInt().toFloat()) speed.toInt() else speed}x",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    androidx.compose.material3.DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f).forEach { s ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("${if (s == s.toInt().toFloat()) s.toInt() else s}x") },
                                onClick = {
                                    speed = s
                                    controller.setPlaybackSpeed(s)
                                    speedMenu = false
                                },
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0x66000000))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmtTime(position), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = if (seeking) seekValue else position.toFloat(),
                        onValueChange = {
                            seeking = true
                            seekValue = it
                        },
                        onValueChangeFinished = {
                            controller.seekTo(seekValue.toLong())
                            seeking = false
                        },
                        valueRange = 0f..(duration.takeIf { it > 0 }?.toFloat() ?: 1f),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Text(fmtTime(duration), color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(onClick = { controller.seekTo((controller.currentPosition - 10_000).coerceAtLeast(0)) }) {
                        Text("-10s", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(24.dp))
                    IconButton(onClick = { if (playing) controller.pause() else controller.play() }) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "播放/暂停",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                    IconButton(onClick = { controller.seekTo(controller.currentPosition + 10_000) }) {
                        Text("+10s", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private fun windowBrightness(context: android.content.Context): Float {
    val wm = context.getSystemService(WindowManager::class.java)
    val lp = wm.attributes ?: return 0.5f
    return if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
}

private fun setWindowBrightness(context: android.content.Context, value: Float) {
    val wm = context.getSystemService(WindowManager::class.java)
    val lp = wm.attributes ?: return
    lp.screenBrightness = value
    wm.attributes = lp
}

fun fmtTime(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
