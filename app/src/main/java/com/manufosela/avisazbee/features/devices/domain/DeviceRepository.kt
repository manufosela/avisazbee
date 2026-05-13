package com.manufosela.avisazbee.features.devices.domain

interface DeviceRepository {
    suspend fun findById(id: String): Device?
    suspend fun upsert(device: Device)
}
