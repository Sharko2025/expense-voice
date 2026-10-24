package com.sitpro.expensevoice.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sitpro.expensevoice.MainActivity
import com.sitpro.expensevoice.R
import com.sitpro.expensevoice.api.TransactionDto
import com.sitpro.expensevoice.container
import com.sitpro.expensevoice.sync.Rejection
import com.sitpro.expensevoice.sync.ResultSink
import com.sitpro.expensevoice.ui.Texts
import com.sitpro.expensevoice.ui.formatVnd

/** Text for the result notification; kept separate so it can be unit-tested. */
data class ResultText(val title: String, val lines: List<String>)

fun resultText(texts: Texts, transactions: List<TransactionDto>): ResultText {
    val lines = transactions.map { texts.line(it.item, it.amountVnd, it.category, it.method) }
    return if (transactions.size == 1) {
        ResultText("✓ " + lines.single(), emptyList())
    } else {
        ResultText(texts.get(R.string.notif_many, transactions.size, formatVnd(transactions.sumOf { it.amountVnd })), lines)
    }
}

class ResultNotifier(private val context: Context) : ResultSink {
    private val manager = NotificationManagerCompat.from(context)

    /** Read fresh each time so a change of app language is picked up. */
    private val texts get() = Texts(context.resources)
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, str(R.string.channel_result), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // pops up, but silently
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SENDING, str(R.string.channel_sending), NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun sending(entryId: String, online: Boolean) {
        if (online) processing(entryId)
        else post(entryId, base(CHANNEL_SENDING).setContentTitle(str(R.string.notif_offline)).setSilent(true))
    }

    /** Indeterminate bar: the app can't know how far the server has got. */
    override fun processing(entryId: String) = post(
        entryId,
        base(CHANNEL_SENDING)
            .setContentTitle(str(R.string.notif_sending))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true),
    )

    override fun success(entryId: String, transactions: List<TransactionDto>) {
        val text = resultText(texts, transactions)
        val builder = base(CHANNEL_RESULT)
            .setContentTitle(text.title)
            .addAction(0, str(R.string.undo), undoIntent(entryId))
        if (text.lines.isNotEmpty()) {
            builder.setStyle(NotificationCompat.InboxStyle().also { style -> text.lines.forEach(style::addLine) })
            builder.setContentText(text.lines.first())
        }
        post(entryId, builder)
    }

    override fun notUnderstood(entryId: String, rawText: String?) {
        val heard = rawText?.let { str(R.string.notif_heard, it) }
        post(
            entryId,
            base(CHANNEL_RESULT)
                .setContentTitle(str(R.string.notif_not_understood))
                .setContentText(heard)
                .setStyle(NotificationCompat.BigTextStyle().bigText(heard)),
        )
    }

    override fun rejected(entryId: String, reason: Rejection) {
        val title = when (reason) {
            Rejection.MISSING_FILE -> R.string.notif_missing_file
            Rejection.INVALID_RECORDING, Rejection.SERVER_ERROR -> R.string.notif_invalid_recording
        }
        post(entryId, base(CHANNEL_RESULT).setContentTitle(str(title)))
    }

    override fun unauthorized(entryId: String) = post(
        entryId,
        base(CHANNEL_RESULT)
            .setContentTitle(str(R.string.notif_token_title))
            .setContentText(str(R.string.notif_token_text)),
    )

    override fun undone(entryId: String) = post(
        entryId,
        base(CHANNEL_RESULT).setContentTitle(str(R.string.notif_undone)).setTimeoutAfter(4_000).setSilent(true),
    )

    fun undoing(entryId: String) =
        post(entryId, base(CHANNEL_SENDING).setContentTitle(str(R.string.notif_undoing)).setSilent(true))

    private fun base(channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_shark)
        .setAutoCancel(true)
        .setContentIntent(
            PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
        )

    private fun undoIntent(entryId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        entryId.hashCode(),
        Intent(context, UndoReceiver::class.java).putExtra(EXTRA_ENTRY_ID, entryId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission") // POST_NOTIFICATIONS is requested on first launch; no-op if denied.
    private fun post(entryId: String, builder: NotificationCompat.Builder) {
        if (manager.areNotificationsEnabled()) manager.notify(entryId.hashCode(), builder.build())
    }

    companion object {
        const val CHANNEL_RESULT = "result"
        const val CHANNEL_SENDING = "sending"
        const val EXTRA_ENTRY_ID = "entry_id"
    }
}

class UndoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getStringExtra(ResultNotifier.EXTRA_ENTRY_ID) ?: return
        context.container.notifier.undoing(entryId)
        context.container.scheduler.enqueueUndo(entryId)
    }
}
