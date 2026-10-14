package com.expensesplitter.app.ui.screens.accounts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.expensesplitter.app.data.repository.PlaidAccount
import com.expensesplitter.app.data.repository.PlaidRepository
import com.expensesplitter.app.ui.components.EmptyState
import com.expensesplitter.app.ui.components.SectionCard
import com.expensesplitter.app.ui.theme.Spacing
import com.plaid.link.OpenPlaidLink
import com.plaid.link.configuration.LinkTokenConfiguration
import com.plaid.link.result.LinkExit
import com.plaid.link.result.LinkSuccess

@Composable
fun AccountsScreen(plaidRepository: PlaidRepository) {
    val viewModel: AccountsViewModel = viewModel(
        factory = viewModelFactory { initializer { AccountsViewModel(plaidRepository) } },
    )
    val state = viewModel.state

    val linkLauncher = rememberLauncherForActivityResult(OpenPlaidLink()) { result ->
        when (result) {
            is LinkSuccess -> viewModel.onLinkSuccess(
                result.publicToken,
                result.metadata.institution?.id,
                result.metadata.institution?.name,
            )
            is LinkExit -> viewModel.onLinkExit()
        }
    }

    // Fires exactly once per successful startLink() call — consumed
    // immediately so recomposition (e.g. after Link closes) doesn't
    // relaunch it.
    LaunchedEffect(viewModel.pendingLinkToken) {
        val token = viewModel.pendingLinkToken ?: return@LaunchedEffect
        val config = LinkTokenConfiguration.Builder().token(token).build()
        linkLauncher.launch(config)
        viewModel.onLinkTokenConsumed()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Linked Accounts", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Button(onClick = viewModel::startLink, enabled = !viewModel.isLinking) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                Text(if (viewModel.isLinking) "Opening…" else "Add")
            }
        }

        when (val s = state) {
            is AccountsFlowState.Loading -> Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is AccountsFlowState.Error -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Couldn't load accounts: ${s.message}", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::load) { Text("Retry") }
            }
            is AccountsFlowState.Loaded -> {
                if (s.accounts.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.AccountBalance,
                        title = "No accounts linked yet",
                        message = "Add a bank or card to see balances and import transactions.",
                    )
                } else {
                    // Private per person by design — this list only ever
                    // contains accounts the signed-in user linked themselves.
                    s.accounts.groupBy { it.institutionName to it.itemId }.forEach { (key, accounts) ->
                        val (institutionName, itemId) = key
                        InstitutionCard(
                            institutionName = institutionName ?: "Linked account",
                            accounts = accounts,
                            onUnlink = { viewModel.unlink(itemId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstitutionCard(institutionName: String, accounts: List<PlaidAccount>, onUnlink: () -> Unit) {
    SectionCard(
        title = institutionName,
        trailing = {
            IconButton(onClick = onUnlink) {
                Icon(Icons.Filled.LinkOff, contentDescription = "Unlink $institutionName")
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            accounts.forEach { account ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(account.name, style = MaterialTheme.typography.bodyLarge)
                        account.mask?.let {
                            Text("•••• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        "${account.currencyCode ?: ""} ${account.currentBalance ?: "—"}".trim(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
