package me.fzzy.announcebot

import com.github.scribejava.core.builder.api.DefaultApi10a
import com.github.scribejava.core.builder.api.DefaultApi20
import com.github.scribejava.core.oauth2.clientauthentication.ClientAuthentication
import com.github.scribejava.core.oauth2.clientauthentication.RequestBodyAuthenticationScheme

class TwitchApi private constructor() : DefaultApi20() {

    companion object {
        val instance = TwitchApi()
    }

    override fun getAccessTokenEndpoint(): String? {
        return "https://id.twitch.tv/oauth2/token"
    }

    override fun getAuthorizationBaseUrl(): String? {
        return "https://id.twitch.tv/oauth2/authorize"
    }

    override fun getClientAuthentication(): ClientAuthentication? {
        return RequestBodyAuthenticationScheme.instance()
    }
}