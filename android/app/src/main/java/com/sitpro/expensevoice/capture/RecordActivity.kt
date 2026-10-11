package com.sitpro.expensevoice.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.sitpro.expensevoice.MainActivity
import com.sitpro.expensevoice.audio.Recorder
import com.sitpro.expensevoice.audio.SilenceDetector
import com.sitpro.expensevoice.container
import com.sitpro.expensevoice.ui.AppTheme
import com.sitpro.expensevoice.ui.VN_ZONE
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Transparent, works over the lock screen. Starts recording immediately, stops on silence,
 * hands the file to the upload queue and closes. Launched by the side key, tile, widget and in-app mic.
 */
class RecordActivity : ComponentActivity() {
    private val recorder by lazy { Recorder(this) }
    private val detector = SilenceDetector()
    private var loop: Job? = null
    private var done = false

    private lateinit var entryId: String
    private lateinit var file: File
    private lateinit var recordedAt: String

    private var elapsedMs by mutableLongStateOf(0L)
    private var level by mutableFloatStateOf(0f)
    private var heardSpeech by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            toast("Cấp quyền micro trước nhé")
            finish()
            return
        }

        entryId = UUID.randomUUID().toString()
        file = File(container.pendingDir, "$entryId.m4a")
        recordedAt = OffsetDateTime.now(VN_ZONE).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

        setContent {
            AppTheme {
                RecordingCard(
                    elapsedMs = elapsedMs,
                    level = level,
                    heardSpeech = heardSpeech,
                    onStop = { finishRecording(save = true) },
                    onCancel = { finishRecording(save = false) },
                )
            }
        }

        try {
            recorder.start(file)
        } catch (e: Exception) {
            file.delete()
            toast("Không mở được micro")
            done = true
            finish()
            return
        }
        vibrate()
        loop = lifecycleScope.launch {
            while (isActive) {
                delay(100)
                val amp = recorder.maxAmplitude()
                val decision = detector.onAmplitude(amp)
                elapsedMs = detector.elapsedMs
                level = (amp / 12_000f).coerceIn(0f, 1f)
                heardSpeech = detector.heardSpeech
                when (decision) {
                    SilenceDetector.Decision.STOP -> finishRecording(save = true)
                    SilenceDetector.Decision.CANCEL_NO_SPEECH -> {
                        toast("Không nghe thấy gì")
                        finishRecording(save = false)
                    }
                    SilenceDetector.Decision.CONTINUE -> Unit
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Screen locked or app switched mid-recording: keep what was said rather than losing it.
        if (!done && !isChangingConfigurations) finishRecording(save = detector.heardSpeech)
    }

    override fun onDestroy() {
        if (!done) recorder.release()
        super.onDestroy()
    }

    private fun finishRecording(save: Boolean) {
        if (done) return
        done = true
        loop?.cancel()
        val ok = recorder.stop()
        if (save && ok && file.length() > 0) {
            container.submitRecording(entryId, file, recordedAt)
        } else {
            file.delete()
            if (save) toast("Bản ghi quá ngắn, nói lại nhé")
        }
        finish()
    }

    private fun vibrate() {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
            ?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    private fun toast(text: String) = Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
}

@androidx.compose.runtime.Composable
private fun RecordingCard(
    elapsedMs: Long,
    level: Float,
    heardSpeech: Boolean,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val pulse by animateFloatAsState(1f + level * 0.5f, label = "pulse")
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(Modifier.padding(24.dp)) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(88.dp).scale(pulse).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Mic, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(20.dp))
                Text(if (heardSpeech) "Đang nghe…" else "Nói đi…", style = MaterialTheme.typography.titleLarge)
                Text(
                    "%d:%02d".format(elapsedMs / 60_000, elapsedMs / 1000 % 60),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    OutlinedButton(onClick = onCancel) { Text("Hủy") }
                    Spacer(Modifier.width(16.dp))
                    Button(onClick = onStop) { Text("Dừng") }
                }
            }
        }
    }
}
