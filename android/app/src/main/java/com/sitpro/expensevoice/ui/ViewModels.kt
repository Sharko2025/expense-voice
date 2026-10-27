package com.sitpro.expensevoice.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.sitpro.expensevoice.R
import androidx.lifecycle.viewModelScope
import com.sitpro.expensevoice.AppContainer
import com.sitpro.expensevoice.api.SummaryDto
import com.sitpro.expensevoice.data.CachedCategory
import com.sitpro.expensevoice.data.CachedTransaction
import com.sitpro.expensevoice.data.ExpenseRepository
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

fun today(): LocalDate = LocalDate.now(VN_ZONE)

fun currentMonth(): YearMonth = YearMonth.now(VN_ZONE)

/**
 * Text to show, resolved in the UI so it follows the app language.
 * `arg` is a String/Int format argument, a list of them, or a LocalDate shown as a short date.
 * With `count`, `res` is a plurals resource formatted with that count.
 */
data class UiMessage(
    val res: Int,
    val arg: Any? = null,
    val undoTransactionIds: List<String> = emptyList(),
    val count: Int? = null,
)

fun Texts.resolve(msg: UiMessage): String = if (msg.count != null) plural(msg.res, msg.count) else when (val a = msg.arg) {
    null -> get(msg.res)
    is LocalDate -> get(msg.res, shortDate(a))
    is List<*> -> get(msg.res, *a.filterNotNull().toTypedArray())
    else -> get(msg.res, a)
}

/** Message for a failed server call. */
fun errorMessage(e: Throwable): UiMessage = when {
    e is HttpException && e.code() == 401 -> UiMessage(R.string.error_token)
    e is HttpException -> UiMessage(R.string.error_server, e.code())
    e is IOException -> UiMessage(R.string.error_network)
    else -> UiMessage(R.string.error_generic)
}

/**
 * Edit sheet actions shared by Today and This month; outcomes go to [messages] (snackbars).
 * [onChanged] runs after a successful save, delete or restore.
 */
class TransactionEditor(
    private val repo: ExpenseRepository,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit = {},
) {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    fun emit(message: UiMessage) {
        _messages.tryEmit(message)
    }

    fun save(original: CachedTransaction, edit: TransactionEdit, onDone: () -> Unit) {
        val patch = buildPatch(original, edit)
        if (patch.isEmpty()) return onDone()
        scope.launch {
            try {
                val updated = repo.update(original.id, patch)
                onDone()
                onChanged()
                if (updated.day != original.day) {
                    // It left the list being viewed; say where it went so it doesn't look lost.
                    emit(UiMessage(R.string.moved_to, LocalDate.parse(updated.day)))
                }
            } catch (e: Exception) {
                emit(errorMessage(e))
            }
        }
    }

    fun delete(tx: CachedTransaction, onDone: () -> Unit) {
        scope.launch {
            try {
                repo.delete(tx.id)
                onDone()
                onChanged()
                emit(UiMessage(R.string.deleted_item, tx.item, undoTransactionIds = listOf(tx.id)))
            } catch (e: Exception) {
                emit(errorMessage(e))
            }
        }
    }

    fun restore(ids: List<String>) {
        scope.launch {
            val result = repo.restoreMany(ids)
            if (result.done.isNotEmpty()) onChanged()
            result.error?.let { emit(errorMessage(it)) }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val editor = TransactionEditor(repo, viewModelScope)

    val day = MutableStateFlow(today())
    val transactions: StateFlow<List<CachedTransaction>> =
        day.flatMapLatest { repo.observeDay(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val updatedAt: StateFlow<Long?> =
        day.flatMapLatest { repo.observeDayUpdatedAt(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val categories: StateFlow<List<CachedCategory>> =
        repo.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val refreshing = MutableStateFlow(false)
    val messages: SharedFlow<UiMessage> = editor.messages

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
                editor.emit(errorMessage(e))
            } finally {
                refreshing.value = false
            }
        }
    }

    fun shiftDay(days: Long) {
        val next = day.value.plusDays(days)
        if (next > today()) return
        clearSelection()
        day.value = next
        refresh()
    }

    /** The Today tab icon: always back to today, from any earlier day. */
    fun goToToday() {
        clearSelection()
        day.value = today()
        refresh()
    }

    // ---- Multi-select (long-press a row) ----

    /** Ids of selected rows; empty = not in selection mode. Lives here so it survives folding the phone. */
    val selected = MutableStateFlow<Set<String>>(emptySet())

    fun toggleSelected(id: String) {
        selected.value = if (id in selected.value) selected.value - id else selected.value + id
    }

    fun selectAll() {
        selected.value = transactions.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun deleteSelected() {
        val ids = transactions.value.map { it.id }.filter { it in selected.value }
        clearSelection()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val result = repo.deleteMany(ids)
            val message = when {
                result.error == null -> UiMessage(R.plurals.deleted_many, count = ids.size, undoTransactionIds = result.done)
                result.done.isEmpty() -> errorMessage(result.error)
                else -> UiMessage(R.string.deleted_partial, listOf(result.done.size, ids.size), undoTransactionIds = result.done)
            }
            editor.emit(message)
        }
    }

    fun save(original: CachedTransaction, edit: TransactionEdit, onDone: () -> Unit) = editor.save(original, edit, onDone)

    fun delete(tx: CachedTransaction, onDone: () -> Unit) = editor.delete(tx, onDone)

    fun restore(ids: List<String>) = editor.restore(ids)
}

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    // Totals change after an edit (amount, category, moved to another month), so reload them.
    private val editor = TransactionEditor(repo, viewModelScope, onChanged = ::refresh)

    val month = MutableStateFlow(currentMonth())
    val summary: StateFlow<Pair<SummaryDto, Long>?> =
        month.flatMapLatest { repo.observeSummary(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val byCategory: StateFlow<Map<Int?, List<CachedTransaction>>> =
        month.flatMapLatest { repo.observeMonth(it) }.map(::transactionsByCategory)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val categories: StateFlow<List<CachedCategory>> =
        repo.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val refreshing = MutableStateFlow(false)
    val error = MutableStateFlow<UiMessage?>(null)
    val messages: SharedFlow<UiMessage> = editor.messages

    /** Category ids opened in place; lives here so it survives folding the phone. */
    val expanded = MutableStateFlow<Set<Int?>>(emptySet())

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
                repo.refreshMonth(month.value)
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
        expanded.value = emptySet()
        month.value = next
        refresh()
    }

    fun toggleExpanded(categoryId: Int?) {
        expanded.value = if (categoryId in expanded.value) expanded.value - categoryId else expanded.value + categoryId
    }

    fun save(original: CachedTransaction, edit: TransactionEdit, onDone: () -> Unit) = editor.save(original, edit, onDone)

    fun delete(tx: CachedTransaction, onDone: () -> Unit) = editor.delete(tx, onDone)

    fun restore(ids: List<String>) = editor.restore(ids)
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
