package com.sitpro.expensevoice.capture

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import com.sitpro.expensevoice.R

/** Quick Settings tile: one tap starts recording, also from the lock screen. */
class VoiceTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.record_label)
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, RecordActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
    }
}

/** Home-screen / cover-screen widget: a single mic button. */
class QuickWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box(
                GlanceModifier.fillMaxSize()
                    .cornerRadius(28.dp)
                    .background(ColorProvider(Color(0xFF0F766E)))
                    .clickable(actionStartActivity<RecordActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_stat_mic),
                    contentDescription = context.getString(R.string.record_label),
                    colorFilter = ColorFilter.tint(ColorProvider(Color.White)),
                    modifier = GlanceModifier.size(36.dp),
                )
            }
        }
    }
}

class QuickWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickWidget()
}
