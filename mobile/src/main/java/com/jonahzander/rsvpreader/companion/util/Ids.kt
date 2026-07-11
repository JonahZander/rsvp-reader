package com.jonahzander.rsvpreader.companion.util

import java.security.MessageDigest

/** Stable, filename-safe content-addressed id: identical content = same book. */
internal fun contentId(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-1").digest(bytes)
        .joinToString("") { "%02x".format(it) }
        .take(16)
