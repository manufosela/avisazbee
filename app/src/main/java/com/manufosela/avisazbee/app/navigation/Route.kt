package com.manufosela.avisazbee.app.navigation

object Routes {
    const val SPLASH = "splash"
    const val SIGN_IN = "signin"
    const val CHANNELS = "channels"

    const val CHANNEL_DETAIL_PATTERN = "channels/{channelId}"
    const val ARG_CHANNEL_ID = "channelId"

    fun channelDetail(channelId: String): String = "channels/$channelId"
}
