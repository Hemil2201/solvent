package com.expensesplitter.app.ui.screens.expense

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.expensesplitter.app.data.repository.AuthRepository
import com.expensesplitter.app.data.repository.ExpenseRepository
import com.expensesplitter.app.ui.components.CategoryIcon
import com.expensesplitter.app.ui.theme.Spacing
import com.expensesplitter.app.ui.util.formatActivityTimestamp

@Composable
fun ExpenseDetailScreen(
    expenseId: String,
    expenseRepository: ExpenseRepository,
    authRepository: AuthRepository,
    onDeleted: () -> Unit,
) {
    val viewModel: ExpenseDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ExpenseDetailViewModel(expenseRepository, authRepository, expenseId) }
        },
    )
    val state = viewModel.state
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFullReceipt by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }

    // The ViewModel survives navigating to Edit Expense and back (same
    // NavBackStackEntry, same ViewModelStore), so its init-block load only
    // ever ran once. Without this, saving an edit and popping back here
    // would keep showing the pre-edit values until leaving and re-entering
    // the screen some other way.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.padding(24.dp)) }
        return
    }
    val expense = state.expense
    if (expense == null) {
        Box(Modifier.fillMaxSize()) { Text("Couldn't load this expense: ${state.error}", modifier = Modifier.padding(24.dp)) }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(categoryName = state.categoryName ?: "Other", icon = state.categoryIcon, size = 48.dp)
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(expense.description ?: "(no description)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${expense.date} · ${state.categoryName ?: "Uncategorized"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "${expense.currency} ${expense.amount}",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(if (expense.isShared) "Shared expense" else "Personal expense", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (expense.isShared && expense.splits.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                expense.splits.forEach { split ->
                    Text("${state.userNames[split.userId] ?: split.userId} owes ${split.amountOwed}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        expense.notes?.let { Text("Notes: $it", style = MaterialTheme.typography.bodyMedium) }

        expense.receiptPhotoUrl?.let { photoUrl ->
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Receipt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            // Receipts are near-always tall/portrait photos — a short wide
            // strip with Crop zooms into a random horizontal slice and cuts
            // off the merchant name at the top. A portrait-shaped thumbnail
            // keeps the top of the receipt (and its total, further down)
            // actually visible.
            AsyncImage(
                model = photoUrl,
                contentDescription = "Receipt photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(140.dp)
                    .height(190.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showFullReceipt = true },
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Comments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        state.comments.forEach { comment ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text("${state.userNames[comment.userId] ?: comment.userId}: ${comment.comment}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatActivityTimestamp(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                label = { Text("Add a comment") },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                viewModel.addComment(commentText)
                commentText = ""
            }) { Text("Post") }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Edit history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (state.history.isEmpty()) {
            Text("No edits yet", style = MaterialTheme.typography.bodySmall)
        } else {
            state.history.forEach { entry ->
                Text(
                    "${entry.fieldChanged}: ${entry.oldValue ?: "—"} → ${entry.newValue ?: "—"} (${formatActivityTimestamp(entry.editedAt)})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))
        OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete Expense") }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this expense?") },
            text = { Text("You can restore it later from Recently Deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete(onDeleted)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showFullReceipt && expense.receiptPhotoUrl != null) {
        Dialog(onDismissRequest = { showFullReceipt = false }) {
            AsyncImage(
                model = expense.receiptPhotoUrl,
                contentDescription = "Receipt photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showFullReceipt = false },
            )
        }
    }
}
