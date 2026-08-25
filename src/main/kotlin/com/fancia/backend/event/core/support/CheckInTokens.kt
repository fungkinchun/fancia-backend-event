package com.fancia.backend.event.core.support

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object CheckInTokens {
    const val DEFAULT_BEFORE_MINUTES = 120
    const val DEFAULT_AFTER_MINUTES = 60

    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun generate(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
