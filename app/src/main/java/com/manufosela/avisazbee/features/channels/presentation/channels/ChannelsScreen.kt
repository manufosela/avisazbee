package com.manufosela.avisazbee.features.channels.presentation.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manufosela.avisazbee.features.channels.domain.Channel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "Mis canales") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* Phase 3c: navigate to CreateChannel */ }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Crear canal")
            }
        },
    ) { innerPadding ->
        when (val current = state) {
            ChannelsUiState.Loading -> LoadingState(Modifier.padding(innerPadding))
            ChannelsUiState.Unauthenticated -> EmptyState(
                modifier = Modifier.padding(innerPadding),
                title = "Sesión cerrada",
                body = "Vuelve a iniciar sesión para ver tus canales.",
            )
            is ChannelsUiState.Loaded -> if (current.channels.isEmpty()) {
                EmptyState(
                    modifier = Modifier.padding(innerPadding),
                    title = "Aún no tienes canales",
                    body = "Crea uno con el botón + o únete con un código de invitación.",
                )
            } else {
                ChannelsList(
                    channels = current.channels,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun ChannelsList(channels: List<Channel>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        items(channels, key = { it.id }) { channel -> ChannelCard(channel) }
    }
}

@Composable
private fun ChannelCard(channel: Channel) {
    Card(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = channel.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Código: ${channel.inviteCode}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, title: String, body: String) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
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
