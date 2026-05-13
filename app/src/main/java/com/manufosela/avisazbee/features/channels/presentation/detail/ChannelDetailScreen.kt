package com.manufosela.avisazbee.features.channels.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manufosela.avisazbee.features.alerts.domain.Alert
import com.manufosela.avisazbee.features.alerts.domain.AlertStatus
import com.manufosela.avisazbee.features.channels.domain.ChannelMember
import com.manufosela.avisazbee.features.channels.domain.ChannelRole
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelDetailScreen(
    onBack: () -> Unit,
    onManageButtons: () -> Unit = {},
    onOpenEscalation: () -> Unit = {},
    onOpenDialers: () -> Unit = {},
    viewModel: ChannelDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingResolve by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.actionError) {
        state.actionError?.let { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            viewModel.dismissActionError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = state.channel?.name ?: "Canal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onManageButtons) {
                        Icon(
                            imageVector = Icons.Filled.SettingsRemote,
                            contentDescription = "Gestionar botones",
                        )
                    }
                    IconButton(onClick = onOpenEscalation) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Escalada de llamadas",
                        )
                    }
                    IconButton(onClick = onOpenDialers) {
                        Icon(
                            imageVector = Icons.Filled.PhoneAndroid,
                            contentDescription = "Dispositivos dialer",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { innerPadding ->
        when {
            state.isLoading -> CenteredLoader(Modifier.padding(innerPadding))
            state.notFound -> CenteredMessage(
                modifier = Modifier.padding(innerPadding),
                title = "Canal no disponible",
                body = "Es posible que se haya borrado.",
            )
            else -> ChannelDetailContent(
                modifier = Modifier.padding(innerPadding),
                state = state,
                onClaim = viewModel::claim,
                onResolve = { alertId -> pendingResolve = alertId },
            )
        }
    }

    pendingResolve?.let { alertId ->
        ResolveDialog(
            onDismiss = { pendingResolve = null },
            onConfirm = { message ->
                viewModel.resolve(alertId, message)
                pendingResolve = null
            },
        )
    }
}

@Composable
private fun ChannelDetailContent(
    modifier: Modifier,
    state: ChannelDetailUiState,
    onClaim: (String) -> Unit,
    onResolve: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            ChannelHeader(
                inviteCode = state.channel?.inviteCode.orEmpty(),
                members = state.members,
            )
        }
        if (state.alerts.isEmpty()) {
            item {
                CenteredMessage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    title = "Sin avisos",
                    body = "Cuando alguien pulse un botón asociado a este canal, lo verás aquí.",
                )
            }
        } else {
            items(state.alerts, key = { it.id }) { alert ->
                AlertCard(
                    alert = alert,
                    currentUid = state.currentUser?.uid,
                    onClaim = { onClaim(alert.id) },
                    onResolve = { onResolve(alert.id) },
                )
            }
        }
    }
}

@Composable
private fun ChannelHeader(inviteCode: String, members: List<ChannelMember>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Código de invitación", style = MaterialTheme.typography.labelMedium)
            Text(text = inviteCode, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(text = "Miembros", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChannelRole.entries.forEach { role ->
                    val count = members.count { it.role == role }
                    if (count > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text(text = "${role.label}: $count") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: Alert,
    currentUid: String?,
    onClaim: () -> Unit,
    onResolve: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = alert.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(8.dp))
                StatusChip(alert.status)
            }
            Text(text = alert.message, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Botón: ${alert.buttonName} · ${formatTime(alert.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
            )
            when (alert.status) {
                AlertStatus.PENDING -> Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onClaim) { Text("Atiendo yo") }
                    OutlinedButton(onClick = onResolve) { Text("Cerrar") }
                }
                AlertStatus.CLAIMED -> {
                    val claimerLabel = if (alert.claimedBy == currentUid) "Tú" else "Otra persona"
                    Text(
                        text = "$claimerLabel está atendiendo",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = onResolve,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Cerrar")
                    }
                }
                AlertStatus.RESOLVED -> Text(
                    text = "Cerrado: ${alert.resolution.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: AlertStatus) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = when (status) {
                    AlertStatus.PENDING -> "Pendiente"
                    AlertStatus.CLAIMED -> "Atendido"
                    AlertStatus.RESOLVED -> "Cerrado"
                },
            )
        },
    )
}

@Composable
private fun ResolveDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var message by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cerrar aviso") },
        text = {
            Column {
                Text(
                    text = "Escribe un mensaje breve para el historial del canal.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Mensaje de cierre") },
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = message.trim().isNotEmpty(),
                onClick = { onConfirm(message.trim()) },
            ) {
                Text("Cerrar aviso")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun CenteredLoader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredMessage(modifier: Modifier = Modifier, title: String, body: String) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private val timeFormatter = DateTimeFormatter
    .ofPattern("dd/MM HH:mm", Locale("es", "ES"))
    .withZone(ZoneId.systemDefault())

private fun formatTime(instant: java.time.Instant): String = timeFormatter.format(instant)

private val ChannelRole.label: String
    get() = when (this) {
        ChannelRole.OWNER -> "owners"
        ChannelRole.ADMIN -> "admins"
        ChannelRole.RECEIVER -> "receivers"
    }
