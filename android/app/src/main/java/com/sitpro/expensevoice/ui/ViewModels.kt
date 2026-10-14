package com.sitpro.expensevoice.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.sitpro.expensevoice.R
import androidx.lifecycle.viewModelScope
import com.sitpro.expensevoice.AppContainer
import com.sitpro.expensevoice.api.SummaryDto
import com.sitpro.expensevoice.data.CachedCategory
import com.sitpro.expensevoice.data.CachedTransaction
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

fun today(): LocalDate = LocalDate.now(VN_ZONE)

fun currentMonth(): YearMonth = YearMonth.now(VN_ZONE)

/**
 * Text to show, resolved in the UI so it follows the app language.
 * `arg` is a String/Int format argument, or a LocalDate shown as a short date.
 */
data class UiMessage(@StringRes val res: Int, val arg: Any? = null, val undoTransactionId: String? = null)

fun Texts.resolve(msg: UiMessage): String = when (val a = msg.arg) {
    null -> get(msg.res)
    is LocalDate -> get(msg.res, shortDate(a))
    else -> get(msg.res, a)
}

/** Message for a failed server call. */
fun errorMessage(e: Throwable): UiMessage = when {
    e is HttpException && e.code() == 401 -> UiMessage(R.string.error_token)
    e is HttpException -> UiMessage(R.string.error_server, e.code())
    e is IOException -> UiMessage(R.string.error_network)
    else -> UiMessage(R.string.error_generic)
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository

    val day = MutableStateFlow(today())
    val transactions: StateFlow<List<CachedTransaction>> =
        day.flatMapLatest { repo.observeDay(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val updatedAt: StateFlow<Long?> =
        day.flatMapLatest { repo.observeDayUpdatedAt(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val categories: StateFlow<List<CachedCategory>> =
        repo.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val refreshing = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    init {
        refresh()
        viewModelScope.launch { runCatching { repo.refreshCategories() } }
    }

    fun refresh() {
        if (!container.settings.current.isConfigured) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                repo.refreshDay(day.value)
            } catch (e: Exception) {
                _messages.tryEmit(errorMessage(e))
            } finally {
                refreshing.value = false
            }
        }
    }

    fun shiftDay(days: Long) {
        val next = day.value.plusDays(days)
        if (next > today()) return
        day.value = next
        refresh()
    }

    fun save(original: CachedTransaction, edit: TransactionEdit, onDone: () -> Unit) {
        val patch = buildPatch(original, edit)
        if (patch.isEmpty()) return onDone()
        viewModelScope.launch {
            try {
                val updated = repo.update(original.id, patch)
                onDone()
                if (updated.day != original.day) {
                    // It left the list being viewed; say where it went so it doesn't look lost.
                    _messages.tryEmit(UiMessage(R.string.moved_to, LocalDate.parse(updated.day)))
                }
            } catch (e: Exception) {
                _messages.tryEmit(errorMessage(e))
            }
        }
    }

    fun delete(tx: CachedTransaction, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.delete(tx.id)
                onDone()
                _messages.tryEmit(UiMessage(R.string.deleted_item, tx.item, undoTransactionId = tx.id))
            } catch (e: Exception) {
                _messages.tryEmit(errorMessage(e))
            }
        }
    }

    fun restore(id: String) {
        viewModelScope.launch {
            try {
                repo.restore(id)
            } catch (e: Exception) {
                _messages.tryEmit(errorMessage(e))
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository

    val month = MutableStateFlow(currentMonth())
    val summary: StateFlow<Pair<SummaryDto, Long>?> =
        month.flatMapLatest { repo.observeSummary(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val refreshing = MutableStateFlow(false)
    val error = MutableStateFlow<UiMessage?>(null)

    init {
        refresh()
    }

    fun refresh() {
        if (!container.settings.current.isConfigured) return
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            try {
                repo.refreshSummary(month.value)
            } catch (e: Exception) {
                error.value = errorMessage(e)
            } finally {
                refreshing.value = false
            }
        }
    }

    fun shiftMonth(months: Long) {
        val next = month.value.plusMonths(months)
        if (next > currentMonth()) return
        month.value = next
        refresh()
    }
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val pendingCount: StateFlow<Int> =
        container.db.pendingDao().countFlow().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val status = MutableStateFlow<UiMessage?>(null)

    val initial get() = container.settings.current

    fun save(url: String, token: String) {
        container.settings.save(url, token)
        status.value = UiMessage(R.string.checking)
        viewModelScope.launch {
            status.value = try {
                container.repository.refreshCategories()
                container.resendQueue()
                UiMessage(R.string.connection_ok)
            } catch (e: Exception) {
                errorMessage(e)
            }
        }
    }

    fun resend() {
        container.resendQueue()
        status.value = UiMessage(R.string.queue_resent)
    }
}
