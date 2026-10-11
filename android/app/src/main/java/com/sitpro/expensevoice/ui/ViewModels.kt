package com.sitpro.expensevoice.ui

import androidx.lifecycle.ViewModel
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

/** Vietnamese message for a failed server call. */
fun errorMessage(e: Throwable): String = when {
    e is HttpException && e.code() == 401 -> "Token không hợp lệ, kiểm tra Cài đặt"
    e is HttpException -> "Lỗi máy chủ (${e.code()})"
    e is IOException -> "Không kết nối được máy chủ"
    else -> "Có lỗi xảy ra"
}

/** One-off message for a Snackbar, optionally with an undo action. */
data class UiMessage(val text: String, val undoTransactionId: String? = null)

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
                _messages.tryEmit(UiMessage(errorMessage(e)))
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
                repo.update(original.id, patch)
                onDone()
            } catch (e: Exception) {
                _messages.tryEmit(UiMessage(errorMessage(e)))
            }
        }
    }

    fun delete(tx: CachedTransaction, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.delete(tx.id)
                onDone()
                _messages.tryEmit(UiMessage("Đã xóa ${tx.item}", undoTransactionId = tx.id))
            } catch (e: Exception) {
                _messages.tryEmit(UiMessage(errorMessage(e)))
            }
        }
    }

    fun restore(id: String) {
        viewModelScope.launch {
            try {
                repo.restore(id)
            } catch (e: Exception) {
                _messages.tryEmit(UiMessage(errorMessage(e)))
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
    val error = MutableStateFlow<String?>(null)

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
    val status = MutableStateFlow<String?>(null)

    val initial get() = container.settings.current

    fun save(url: String, token: String) {
        container.settings.save(url, token)
        status.value = "Đang kiểm tra kết nối…"
        viewModelScope.launch {
            status.value = try {
                container.repository.refreshCategories()
                container.resendQueue()
                "Kết nối OK"
            } catch (e: Exception) {
                errorMessage(e)
            }
        }
    }

    fun resend() {
        container.resendQueue()
        status.value = "Đã xếp lại hàng đợi"
    }
}
