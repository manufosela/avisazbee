package com.manufosela.avisazbee.app.navigation

object Routes {
    const val SPLASH = "splash"
    const val SIGN_IN = "signin"
    const val CHANNELS = "channels"
    const val CREATE_CHANNEL = "channels/new"
    const val JOIN_CHANNEL = "channels/join"

    const val CHANNEL_DETAIL_PATTERN = "channels/{channelId}/detail"
    const val BUTTONS_PATTERN = "channels/{channelId}/buttons"
    const val CREATE_BUTTON_PATTERN = "channels/{channelId}/buttons/new"
    const val ESCALATION_PATTERN = "channels/{channelId}/escalation"
    const val DIALERS_PATTERN = "channels/{channelId}/dialers"
    const val NOTIFICATION_PREFERENCES = "preferences/notifications"
    const val ARG_CHANNEL_ID = "channelId"

    fun channelDetail(channelId: String): String = "channels/$channelId/detail"
    fun buttons(channelId: String): String = "channels/$channelId/buttons"
    fun createButton(channelId: String): String = "channels/$channelId/buttons/new"
    fun escalation(channelId: String): String = "channels/$channelId/escalation"
    fun dialers(channelId: String): String = "channels/$channelId/dialers"
}
