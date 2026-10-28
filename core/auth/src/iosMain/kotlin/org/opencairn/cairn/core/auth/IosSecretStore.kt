package org.opencairn.cairn.core.auth

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/** Generic-password Keychain items under one service name, device-only, available after first unlock. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSecretStore(
    private val service: String = "org.opencairn.cairn",
) : SecretStore {
    override suspend fun get(key: String): String? =
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status =
                withQuery(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to service.cf(),
                    kSecAttrAccount to key.cf(),
                    kSecReturnData to kCFBooleanTrue,
                    kSecMatchLimit to kSecMatchLimitOne,
                ) { SecItemCopyMatching(it, result.ptr) }
            if (status != errSecSuccess) return@memScoped null
            val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
            data.toByteArray().decodeToString()
        }

    override suspend fun put(
        key: String,
        value: String,
    ) {
        remove(key)
        withQuery(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service.cf(),
            kSecAttrAccount to key.cf(),
            kSecValueData to CFBridgingRetain(value.encodeToByteArray().toNSData()),
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ) { SecItemAdd(it, null) }
    }

    override suspend fun remove(key: String) {
        withQuery(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service.cf(),
            kSecAttrAccount to key.cf(),
        ) { SecItemDelete(it) }
    }

    private inline fun <T> withQuery(
        vararg pairs: Pair<CFStringRef?, CFTypeRef?>,
        block: (CFDictionaryRef) -> T,
    ): T {
        val dict = requireNotNull(CFDictionaryCreateMutable(null, pairs.size.convert(), null, null))
        pairs.forEach { (k, v) -> CFDictionaryAddValue(dict, k, v) }
        return try {
            block(dict)
        } finally {
            CFRelease(dict)
        }
    }

    private fun String.cf(): CFTypeRef? = CFBridgingRetain(this)

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        val out = ByteArray(size)
        if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
        return out
    }

    private fun ByteArray.toNSData(): NSData =
        if (isEmpty()) {
            NSData()
        } else {
            usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }
        }
}
