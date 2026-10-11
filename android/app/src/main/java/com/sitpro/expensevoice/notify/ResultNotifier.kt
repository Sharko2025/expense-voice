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
import com.sitpro.expensevoice.sync.ResultSink
import com.sitpro.expensevoice.ui.formatVnd
import com.sitpro.expensevoice.ui.transactionLine

/** Text for the result notification; kept separate so it can be unit-tested. */
data class ResultText(val title: String, val lines: List<String>)

fun resultText(transactions: List<TransactionDto>): ResultText {
    val lines = transactions.map { transactionLine(it.item, it.amountVnd, it.category, it.method) }
    return if (transactions.size == 1) {
        ResultText("✓ " + lines.single(), emptyList())
    } else {
        ResultText("✓ ${transactions.size} giao dịch · ${formatVnd(transactions.sumOf { it.amountVnd })}", lines)
    }
}

class ResultNotifier(private val context: Context) : ResultSink {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "Kết quả ghi chi tiêu", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // pops up, but silently
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SENDING, "Đang gửi", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun sending(entryId: String, online: Boolean) = post(
        entryId,
        base(CHANNEL_SENDING)
            .setContentTitle(if (online) "⏳ Đang gửi…" else "Đã lưu, sẽ gửi khi có mạng")
            .setOngoing(online)
            .setSilent(true),
    )

    override fun success(entryId: String, transactions: List<TransactionDto>) {
        val text = resultText(transactions)
        val builder = base(CHANNEL_RESULT)
            .setContentTitle(text.title)
            .addAction(0, "Hoàn tác", undoIntent(entryId))
        if (text.lines.isNotEmpty()) {
            builder.setStyle(NotificationCompat.InboxStyle().also { style -> text.lines.forEach(style::addLine) })
            builder.setContentText(text.lines.first())
        }
        post(entryId, builder)
    }

    override fun notUnderstood(entryId: String, rawText: String?) = post(
        entryId,
        base(CHANNEL_RESULT)
            .setContentTitle("Không hiểu, nói lại nhé")
            .setContentText(rawText?.let { "Nghe được: “$it”" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(rawText?.let { "Nghe được: “$it”" })),
    )

    override fun rejected(entryId: String, message: String) =
        post(entryId, base(CHANNEL_RESULT).setContentTitle(message))

    override fun unauthorized(entryId: String) = post(
        entryId,
        base(CHANNEL_RESULT)
            .setContentTitle("Token không hợp lệ")
            .setContentText("Mở Cài đặt để nhập lại token, bản ghi vẫn được giữ"),
    )

    override fun undone(entryId: String) = post(
        entryId,
        base(CHANNEL_RESULT).setContentTitle("Đã hoàn tác").setTimeoutAfter(4_000).setSilent(true),
    )

    fun undoing(entryId: String) =
        post(entryId, base(CHANNEL_SENDING).setContentTitle("Đang hoàn tác…").setSilent(true))

    private fun base(channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_mic)
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
