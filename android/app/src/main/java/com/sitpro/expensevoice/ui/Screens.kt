package com.sitpro.expensevoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sitpro.expensevoice.R
import com.sitpro.expensevoice.data.CachedCategory
import com.sitpro.expensevoice.data.CachedTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
private fun UpdatedAt(millis: Long?) {
    if (millis == null) return
    Text(
        rememberTexts().updatedAt(millis),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PeriodHeader(title: String, canForward: Boolean, onBack: () -> Unit, onForward: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous))
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onForward, enabled = canForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next))
        }
    }
}

// ---------------- Today ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(vm: TodayViewModel, contentPadding: PaddingValues) {
    val texts = rememberTexts()
    val day by vm.day.collectAsState()
    val list by vm.transactions.collectAsState()
    val updatedAt by vm.updatedAt.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val categories by vm.categories.collectAsState()
    val selected by vm.selected.collectAsState()
    var editing by remember { mutableStateOf<CachedTransaction?>(null) }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting, onBack = vm::clearSelection)

    PullToRefreshBox(refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        // Bottom padding keeps the last rows clear of the big centered mic button.
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
            item {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    PeriodHeader(texts.dayTitle(day, today()), canGoForward(day, today()), { vm.shiftDay(-1) }, { vm.shiftDay(1) })
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        Text(
                            formatVnd(list.sumOf { it.amountVnd }),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(texts.transactionCount(list.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        UpdatedAt(updatedAt)
                    }
                }
                HorizontalDivider()
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_transactions),
                        Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(list, key = { it.id }) { tx ->
                TransactionRow(
                    tx, texts,
                    selected = if (selecting) tx.id in selected else null,
                    onClick = { if (selecting) vm.toggleSelected(tx.id) else editing = tx },
                    onLongClick = { vm.toggleSelected(tx.id) },
                )
                HorizontalDivider()
            }
        }
    }

    editing?.let { tx ->
        EditSheet(
            tx = tx,
            categories = categories,
            onDismiss = { editing = null },
            onSave = { edit -> vm.save(tx, edit) { editing = null } },
            onDelete = { vm.delete(tx) { editing = null } },
        )
    }
}

/** [selected] is null outside selection mode, otherwise whether this row is ticked. */
@Composable
private fun TransactionRow(
    tx: CachedTransaction,
    texts: Texts,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected == true) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = if (selected != null) 8.dp else 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected != null) {
            // The whole row toggles; the checkbox only shows the state.
            Checkbox(checked = selected, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            formatTime(Instant.ofEpochMilli(tx.occurredAtMillis)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.item.replaceFirstChar { it.uppercase() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(texts.category(tx.category), texts.method(tx.method), tx.merchant).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(formatVnd(tx.amountVnd), fontWeight = FontWeight.Medium)
    }
}

private val METHODS = listOf(
    "cash" to R.string.method_cash,
    "card" to R.string.method_card,
    "transfer" to R.string.method_transfer_short,
    null to R.string.method_unset,
)

/** Material date pickers work in UTC millis at midnight. */
private fun LocalDate.toPickerMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.fromPickerMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    tx: CachedTransaction,
    categories: List<CachedCategory>,
    onDismiss: () -> Unit,
    onSave: (TransactionEdit) -> Unit,
    onDelete: () -> Unit,
) {
    val texts = rememberTexts()
    var edit by remember(tx.id) { mutableStateOf(TransactionEdit.from(tx)) }
    var showError by remember { mutableStateOf(false) }
    var categoryOpen by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    // Fully expanded so Save is visible; scrollable for the small cover screen.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.edit_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                edit.item, { edit = edit.copy(item = it) }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_item)) }, singleLine = true,
            )
            OutlinedTextField(
                edit.amount,
                { edit = edit.copy(amount = it) },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PickerField(stringResource(R.string.field_date), texts.shortDate(edit.date), Modifier.weight(1f)) { pickDate = true }
                PickerField(stringResource(R.string.field_time), "%02d:%02d".format(edit.time.hour, edit.time.minute), Modifier.weight(1f)) {
                    pickTime = true
                }
            }
            ExposedDropdownMenuBox(categoryOpen, { categoryOpen = it }) {
                OutlinedTextField(
                    value = texts.category(categories.firstOrNull { it.id == edit.categoryId }?.name ?: tx.category).orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.field_category)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(categoryOpen, { categoryOpen = false }) {
                    categories.forEach { c ->
                        DropdownMenuItem(text = { Text(texts.category(c.name).orEmpty()) }, onClick = {
                            edit = edit.copy(categoryId = c.id)
                            categoryOpen = false
                        })
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                METHODS.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = edit.method == value,
                        onClick = { edit = edit.copy(method = value) },
                        shape = SegmentedButtonDefaults.itemShape(i, METHODS.size),
                    ) { Text(stringResource(label), maxLines = 1) }
                }
            }
            OutlinedTextField(
                edit.merchant, { edit = edit.copy(merchant = it) }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_merchant)) }, singleLine = true,
            )
            if (showError) edit.error()?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDelete, Modifier.weight(1f)) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = {
                    showError = true
                    if (edit.error() == null) onSave(edit)
                }, Modifier.weight(1f)) { Text(stringResource(R.string.save)) }
            }
        }
    }

    if (pickDate) {
        val lastDay = today()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = edit.date.toPickerMillis(),
            selectableDates = object : SelectableDates {
                // No future dates (approved 2026-10-14).
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.fromPickerMillis() <= lastDay
                override fun isSelectableYear(year: Int) = year <= lastDay.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { edit = edit.copy(date = it.fromPickerMillis()) }
                    pickDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }

    if (pickTime) {
        val state = rememberTimePickerState(initialHour = edit.time.hour, initialMinute = edit.time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    edit = edit.copy(time = LocalTime.of(state.hour, state.minute))
                    pickTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state) },
        )
    }
}

/** Read-only field that opens a picker when tapped. */
@Composable
private fun PickerField(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier) {
        OutlinedTextField(value, {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text(label) }, singleLine = true)
        // Transparent overlay: a read-only text field swallows clicks otherwise. It carries the
        // label and value so screen readers (and adb UI dumps) still see the field.
        Box(
            Modifier.matchParentSize()
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = "$label: $value" }
        )
    }
}

/** Recordings not processed yet; shown above every tab, hidden when nothing is waiting. */
@Composable
fun PendingStrip(banner: PendingBanner, onOpenSettings: () -> Unit) {
    val texts = rememberTexts()
    val res = when (banner.kind) {
        PendingBanner.Kind.PROCESSING -> R.plurals.pending_processing
        PendingBanner.Kind.OFFLINE -> R.plurals.pending_offline
        PendingBanner.Kind.BLOCKED -> R.plurals.pending_blocked
    }
    val blocked = banner.kind == PendingBanner.Kind.BLOCKED
    Column(
        Modifier.fillMaxWidth()
            .background(if (blocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)
            .then(if (blocked) Modifier.clickable(onClick = onOpenSettings) else Modifier)
    ) {
        Text(
            texts.plural(res, banner.count),
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (blocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        )
        // No progress value = indeterminate: the app can't know how far the server has got.
        if (banner.kind == PendingBanner.Kind.PROCESSING) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

// ---------------- Month ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(vm: MonthViewModel, contentPadding: PaddingValues) {
    val texts = rememberTexts()
    val month by vm.month.collectAsState()
    val cached by vm.summary.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val error by vm.error.collectAsState()
    val byCategory by vm.byCategory.collectAsState()
    val expanded by vm.expanded.collectAsState()
    val categories by vm.categories.collectAsState()
    var editing by remember { mutableStateOf<CachedTransaction?>(null) }

    PullToRefreshBox(refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
            item {
                Column(Modifier.padding(8.dp)) {
                    PeriodHeader(texts.monthTitle(month), canGoForward(month, currentMonth()), { vm.shiftMonth(-1) }, { vm.shiftMonth(1) })
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        val summary = cached?.first
                        Text(
                            formatVnd(summary?.totalVnd ?: 0),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (summary != null) {
                            Text(
                                texts.diff(summary),
                                color = when {
                                    summary.diffVnd > 0 -> MaterialTheme.colorScheme.error
                                    summary.diffVnd < 0 -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        error?.let {
                            Text(texts.resolve(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        UpdatedAt(cached?.second)
                    }
                }
                HorizontalDivider()
            }
            val bars = cached?.first?.let(::categoryBars).orEmpty()
            if (cached != null && bars.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_spending_month),
                        Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            bars.forEach { bar ->
                val open = bar.categoryId in expanded
                item(key = "cat:${bar.categoryId}") { CategoryBarRow(bar, texts, open) { vm.toggleExpanded(bar.categoryId) } }
                if (open) {
                    items(byCategory[bar.categoryId].orEmpty(), key = { it.id }) { tx ->
                        MonthTransactionRow(tx, texts) { editing = tx }
                    }
                    item(key = "end:${bar.categoryId}") { HorizontalDivider() }
                }
            }
        }
    }

    editing?.let { tx ->
        EditSheet(
            tx = tx,
            categories = categories,
            onDismiss = { editing = null },
            onSave = { edit -> vm.save(tx, edit) { editing = null } },
            onDelete = { vm.delete(tx) { editing = null } },
        )
    }
}

/** One transaction under an opened category: date instead of time, the category is already the heading. */
@Composable
private fun MonthTransactionRow(tx: CachedTransaction, texts: Texts, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(start = 32.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            texts.shortDate(LocalDate.parse(tx.day)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.item.replaceFirstChar { it.uppercase() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(texts.method(tx.method), tx.merchant).joinToString(" · ")
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(formatVnd(tx.amountVnd))
    }
}

/** Tap to open/close the category's transactions in place. */
@Composable
private fun CategoryBarRow(bar: CategoryBar, texts: Texts, open: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(texts.category(bar.name ?: "Khác").orEmpty(), Modifier.weight(1f, fill = false))
            Icon(
                if (open) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(formatVnd(bar.totalVnd), fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        // Proportion bar drawn with plain Compose (SPEC: no chart library).
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier.fillMaxWidth(bar.fraction).height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            texts.transactionCount(bar.count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------- Settings ----------------

@Composable
fun SettingsScreen(vm: SettingsViewModel, contentPadding: PaddingValues) {
    val texts = rememberTexts()
    val pending by vm.pendingCount.collectAsState()
    val status by vm.status.collectAsState()
    var url by rememberSaveable { mutableStateOf(vm.initial.baseUrl) }
    var token by rememberSaveable { mutableStateOf(vm.initial.token) }
    var showToken by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            url, { url = it }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_url)) }, singleLine = true,
        )
        OutlinedTextField(
            token,
            { token = it },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_token)) },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showToken = !showToken }) {
                    Text(stringResource(if (showToken) R.string.hide else R.string.show))
                }
            },
        )
        Button(onClick = { vm.save(url, token) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_and_check)) }
        status?.let { Text(texts.resolve(it)) }
        HorizontalDivider()
        Text(stringResource(R.string.pending_count, pending), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = vm::resend, Modifier.fillMaxWidth()) { Text(stringResource(R.string.resend_queue)) }
    }
}
