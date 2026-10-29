package org.opencairn.cairn.core.data

/** Stable, short key for cache rows (FNV-1a 64 over a canonical string). */
internal fun cacheKey(vararg parts: Any?): String {
    var hash = -0x340d631b7bdddcdbL
    for (ch in parts.joinToString("|")) {
        hash = hash xor ch.code.toLong()
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16)
}
