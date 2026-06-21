package com.grupo4.finansync.ui.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * Maneja el cifrado y descifrado de credenciales usando Android Keystore.
 *
 * En esta versión:
 * - La contraseña se cifra con una clave guardada en Android Keystore.
 * - La autenticación biométrica se valida desde la UI con BiometricPrompt.
 * - Después de validar la huella, se descifra la contraseña y se inicia sesión.
 *
 * Para una versión más avanzada tipo banco real, se podría usar CryptoObject
 * para que la clave solo pueda descifrar después de una autenticación biométrica
 * criptográficamente vinculada.
 */
object BiometricKeyManager {

    private const val KEY_ALIAS = "finansync_biometric_key"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"

    // ── Crear o recuperar clave ───────────────────────────────────────────
    private fun obtenerOCrearClave(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }

        keyStore.getKey(KEY_ALIAS, null)?.let {
            return it as SecretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )

        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build()
        )

        return keyGenerator.generateKey()
    }

    // ── Cifrar texto ──────────────────────────────────────────────────────
    fun cifrar(texto: String): String {
        val clave = obtenerOCrearClave()

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, clave)

        val iv = Base64.encodeToString(
            cipher.iv,
            Base64.NO_WRAP
        )

        val datos = Base64.encodeToString(
            cipher.doFinal(texto.toByteArray()),
            Base64.NO_WRAP
        )

        return "$iv:$datos"
    }

    // ── Descifrar texto ───────────────────────────────────────────────────
    fun descifrar(textoCifrado: String): String? {
        return try {
            val partes = textoCifrado.split(":")

            if (partes.size != 2) return null

            val iv = Base64.decode(
                partes[0],
                Base64.NO_WRAP
            )

            val datos = Base64.decode(
                partes[1],
                Base64.NO_WRAP
            )

            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
                load(null)
            }

            val clave = keyStore.getKey(
                KEY_ALIAS,
                null
            ) as? SecretKey ?: return null

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                clave,
                IvParameterSpec(iv)
            )

            String(cipher.doFinal(datos))

        } catch (e: Exception) {
            null
        }
    }

    // ── Eliminar clave ────────────────────────────────────────────────────
    fun eliminarClave() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
                load(null)
            }

            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }

        } catch (e: Exception) {
            // Ignorar si no existe o no se puede eliminar
        }
    }
}