package com.ledger.app.data

import androidx.core.net.toUri
import net.openid.appauth.AuthorizationServiceConfiguration

object ProjectConfig {
  const val clientId = "256d1cf8-d750-43c1-b1f5-1776196c5346"
  const val redirectUri = "com.ledger.app://oauth"

  private const val authEndpoint = "https://huggingface.co/oauth/authorize"
  private const val tokenEndpoint = "https://huggingface.co/oauth/token"

  val authServiceConfig = AuthorizationServiceConfiguration(
    authEndpoint.toUri(),
    tokenEndpoint.toUri(),
  )
}
