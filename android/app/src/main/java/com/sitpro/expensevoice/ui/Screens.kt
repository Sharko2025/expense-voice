package com.sitpro.expensevoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
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
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sitpro.expensevoice.data.CachedCategory
import com.sitpro.expensevoice.data.CachedTransaction
import java.time.Instant
import java.time.format.DateTimeFormatter

private val UPDATED_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(VN_ZONE)

@Composable
private fun UpdatedAt(millis: Long?) {
    if (millis == null) return
    Text(
        "cập nhật lúc ${UPDATED_FORMAT.format(Instant.ofEpochMilli(millis))}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PeriodHeader(title: String, canForward: Boolean, onBack: () -> Unit, onForward: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Trước") }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onForward, enabled = canForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Sau")
        }
    }
}

// ---------------- Today ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(vm: TodayViewModel, contentPadding: PaddingValues) {
    val day by vm.day.collectAsState()
    val list by vm.transactions.collectAsState()
    val updatedAt by vm.updatedAt.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val categories by vm.categories.collectAsState()
    var editing by remember { mutableStateOf<CachedTransaction?>(null) }

    PullToRefreshBox(refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
            item {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    PeriodHeader(dayTitle(day, today()), canGoForward(day, today()), { vm.shiftDay(-1) }, { vm.shiftDay(1) })
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        Text(
                            formatVnd(list.sumOf { it.amountVnd }),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("${list.size} giao dịch", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        UpdatedAt(updatedAt)
                    }
                }
                HorizontalDivider()
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        "Chưa có giao dịch nào",
                        Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(list, key = { it.id }) { tx ->
                TransactionRow(tx, onClick = { editing = tx })
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

@Composable
private fun TransactionRow(tx: CachedTransaction, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatTime(Instant.ofEpochMilli(tx.occurredAtMillis)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.item.replaceFirstChar { it.uppercase() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(tx.category, methodLabel(tx.method), tx.merchant).joinToString(" · "),
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

private val METHODS = listOf("cash" to "Tiền mặt", "card" to "Thẻ", "transfer" to "C.khoản", null to "Chưa rõ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    tx: CachedTransaction,
    categories: List<CachedCategory>,
    onDismiss: () -> Unit,
    onSave: (TransactionEdit) -> Unit,
    onDelete: () -> Unit,
) {
    var edit by remember(tx.id) { mutableStateOf(TransactionEdit.from(tx)) }
    var showError by remember { mutableStateOf(false) }
    var categoryOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sửa giao dịch", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(edit.item, { edit = edit.copy(item = it) }, Modifier.fillMaxWidth(), label = { Text("Món") }, singleLine = true)
            OutlinedTextField(
                edit.amount,
                { edit = edit.copy(amount = it) },
                Modifier.fillMaxWidth(),
                label = { Text("Số tiền (đ)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            ExposedDropdownMenuBox(categoryOpen, { categoryOpen = it }) {
                OutlinedTextField(
                    value = categories.firstOrNull { it.id == edit.categoryId }?.name ?: tx.category.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Danh mục") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(categoryOpen, { categoryOpen = false }) {
                    categories.forEach { c ->
                        DropdownMenuItem(text = { Text(c.name) }, onClick = {
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
                    ) { Text(label, maxLines = 1) }
                }
            }
            OutlinedTextField(edit.merchant, { edit = edit.copy(merchant = it) }, Modifier.fillMaxWidth(), label = { Text("Nơi mua") }, singleLine = true)
            if (showError) edit.error()?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDelete, Modifier.weight(1f)) {
                    Text("Xóa", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = {
                    showError = true
                    if (edit.error() == null) onSave(edit)
                }, Modifier.weight(1f)) { Text("Lưu") }
            }
        }
    }
}

// ---------------- Month ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(vm: MonthViewModel, contentPadding: PaddingValues) {
    val month by vm.month.collectAsState()
    val cached by vm.summary.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val error by vm.error.collectAsState()

    PullToRefreshBox(refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
            item {
                Column(Modifier.padding(8.dp)) {
                    PeriodHeader(monthTitle(month), canGoForward(month, currentMonth()), { vm.shiftMonth(-1) }, { vm.shiftMonth(1) })
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        val summary = cached?.first
                        Text(
                            formatVnd(summary?.totalVnd ?: 0),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (summary != null) {
                            Text(
                                diffText(summary),
                                color = when {
                                    summary.diffVnd > 0 -> MaterialTheme.colorScheme.error
                                    summary.diffVnd < 0 -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        UpdatedAt(cached?.second)
                    }
                }
                HorizontalDivider()
            }
            val bars = cached?.first?.let(::categoryBars).orEmpty()
            if (cached != null && bars.isEmpty()) {
                item { Text("Chưa có chi tiêu trong tháng", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(bars, key = { it.name }) { bar -> CategoryBarRow(bar) }
        }
    }
}

@Composable
private fun CategoryBarRow(bar: CategoryBar) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(bar.name, Modifier.weight(1f))
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
            "${bar.count} giao dịch",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------- Settings ----------------

@Composable
fun SettingsScreen(vm: SettingsViewModel, contentPadding: PaddingValues) {
    val pending by vm.pendingCount.collectAsState()
    val status by vm.status.collectAsState()
    var url by rememberSaveable { mutableStateOf(vm.initial.baseUrl) }
    var token by rememberSaveable { mutableStateOf(vm.initial.token) }
    var showToken by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("URL máy chủ") }, singleLine = true)
        OutlinedTextField(
            token,
            { token = it },
            Modifier.fillMaxWidth(),
            label = { Text("Device token") },
            singleLine = true,
            visualTransformation = if (showToken) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) "Ẩn" else "Hiện") } },
        )
        Button(onClick = { vm.save(url, token) }, Modifier.fillMaxWidth()) { Text("Lưu và kiểm tra") }
        status?.let { Text(it) }
        HorizontalDivider()
        Text("Đang chờ gửi: $pending", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = vm::resend, Modifier.fillMaxWidth()) { Text("Gửi lại hàng đợi") }
    }
}
