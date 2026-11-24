package com.iz.gunluk

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cihaz içi şifreleme: anahtar Android Keystore'da üretilir, uygulama dışına hiç çıkmaz.
 * Biçim: "IZE1" + 12 bayt IV + AES-256-GCM şifreli veri.
 */
object Crypto {
    private const val ALIAS = "iz_data_key"
    private val MAGIC = "IZE1".toByteArray()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return g.generateKey()
    }

    fun isSealed(b: ByteArray) = b.size > 16 && b.copyOfRange(0, 4).contentEquals(MAGIC)

    fun seal(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return MAGIC + c.iv + c.doFinal(plain)
    }

    /** Şifresiz (eski sürüm) veriyi olduğu gibi döndürür. */
    fun open(b: ByteArray): ByteArray {
        if (!isSealed(b)) return b
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 4, 12))
        return c.doFinal(b, 16, b.size - 16)
    }

    fun sealString(s: String): String = android.util.Base64.encodeToString(seal(s.toByteArray()), android.util.Base64.NO_WRAP)
    fun openString(s: String): String = try { String(open(android.util.Base64.decode(s, android.util.Base64.NO_WRAP))) } catch (_: Exception) { "" }
}

/**
 * Parolalı yedek: PBKDF2-SHA256 (150 000 tur) ile anahtar türetilir, AES-256-CTR ile şifrelenir,
 * HMAC-SHA256 ile bütünlük korunur. Akış hâlinde çalışır; büyük video yedekleri belleği doldurmaz.
 * Biçim: "IZB1" + 16 bayt tuz + 16 bayt IV + şifreli veri + 32 bayt HMAC.
 */
object BackupCrypto {
    val MAGIC = "IZB1".toByteArray()
    private const val ITER = 150_000

    private fun keys(pass: String, salt: ByteArray): Pair<SecretKeySpec, SecretKeySpec> {
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pass.toCharArray(), salt, ITER, 512)).encoded
        return SecretKeySpec(k.copyOfRange(0, 32), "AES") to SecretKeySpec(k.copyOfRange(32, 64), "HmacSHA256")
    }

    /** Verilen çıkışa şifreleyen bir akış döndürür; kapatıldığında HMAC yazılır. */
    fun encrypting(out: OutputStream, pass: String): OutputStream {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val (ek, mk) = keys(pass, salt)
        val c = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.ENCRYPT_MODE, ek, IvParameterSpec(iv)) }
        val mac = Mac.getInstance("HmacSHA256").apply { init(mk) }
        val head = MAGIC + salt + iv
        out.write(head); mac.update(head)
        return object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                val enc = c.update(b, off, len) ?: return
                mac.update(enc); out.write(enc)
            }
            override fun close() {
                c.doFinal()?.let { if (it.isNotEmpty()) { mac.update(it); out.write(it) } }
                out.write(mac.doFinal()); out.flush(); out.close()
            }
        }
    }

    /**
     * İki geçiş: önce [open] ile alınan akışta HMAC doğrulanır (yanlış parola / bozuk dosya = false),
     * sonra çözülmüş akış döndürülür. Toplam uzunluk bilinmeli.
     */
    fun verify(input: InputStream, total: Long, pass: String): Boolean {
        input.use { s ->
            val head = ByteArray(36); if (s.readFully(head) != 36 || !head.copyOfRange(0, 4).contentEquals(MAGIC)) return false
            val (_, mk) = keys(pass, head.copyOfRange(4, 20))
            val mac = Mac.getInstance("HmacSHA256").apply { init(mk); update(head) }
            var left = total - 36 - 32
            if (left < 0) return false
            val buf = ByteArray(64 * 1024)
            while (left > 0) { val n = s.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (n < 0) return false; mac.update(buf, 0, n); left -= n }
            val tag = ByteArray(32); if (s.readFully(tag) != 32) return false
            return MessageDigest.isEqual(tag, mac.doFinal())
        }
    }

    fun decrypting(input: InputStream, total: Long, pass: String): InputStream {
        val head = ByteArray(36); input.readFully(head)
        val (ek, _) = keys(pass, head.copyOfRange(4, 20))
        val c = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.DECRYPT_MODE, ek, IvParameterSpec(head.copyOfRange(20, 36))) }
        var left = total - 36 - 32
        return object : InputStream() {
            private var pend = ByteArray(0); private var pos = 0
            override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xff }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                while (pos >= pend.size) {
                    if (left <= 0) return -1
                    val buf = ByteArray(minOf(64L * 1024, left).toInt())
                    val n = input.read(buf); if (n < 0) return -1
                    left -= n; pend = c.update(buf, 0, n) ?: ByteArray(0); pos = 0
                }
                val k = minOf(len, pend.size - pos); System.arraycopy(pend, pos, b, off, k); pos += k; return k
            }
            override fun close() = input.close()
        }
    }

    private fun InputStream.readFully(b: ByteArray): Int {
        var r = 0
        while (r < b.size) { val n = read(b, r, b.size - r); if (n < 0) break; r += n }
        return r
    }
}
