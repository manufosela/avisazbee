package com.manufosela.avisazbee.infrastructure.device

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.deviceDataStore by preferencesDataStore(name = "avisazbee_device")

@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.deviceDataStore

    suspend fun getOrCreate(): String {
        val current = store.data.map { it[DEVICE_ID_KEY] }.first()
        if (current != null) return current
        val fresh = UUID.randomUUID().toString()
        store.edit { it[DEVICE_ID_KEY] = fresh }
        return fresh
    }

    private companion object {
        val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    }
}
