package com.chexo.scan.ui

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLEncoder
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TotpHelper {
    private const val TIME_STEP = 30L
    private const val DIGITS = 6
    private const val SECRET_BYTES = 20

    fun generateSecret(): String {
        val bytes = ByteArray(SECRET_BYTES)
        SecureRandom().nextBytes(bytes)
        return base32Encode(bytes)
    }

    fun buildOtpAuthUri(
        secret: String,
        accountName: String,
        issuer: String = "ChexoScan"
    ): String {
        val encodedIssuer = URLEncoder.encode(issuer, "UTF-8")
        val encodedAccount = URLEncoder.encode(accountName, "UTF-8")
        return "otpauth://totp/$encodedIssuer:$encodedAccount?secret=$secret&issuer=$encodedIssuer&algorithm=SHA1&digits=$DIGITS&period=$TIME_STEP"
    }

    fun verify(secret: String, code: String, window: Int = 1): Boolean {
        return try {
            val secretBytes = base32Decode(secret)
            val currentTime = System.currentTimeMillis() / 1000
            val currentStep = currentTime / TIME_STEP
            for (i in -window..window) {
                if (generateCode(secretBytes, currentStep + i) == code) return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun generateCode(secretBytes: ByteArray, step: Long): String {
        val timeBytes = ByteArray(8)
        var temp = step
        for (i in 7 downTo 0) {
            timeBytes[i] = (temp and 0xFF).toByte()
            temp = temp shr 8
        }
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA1"))
        val hash = mac.doFinal(timeBytes)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val code = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
        return (code % 1_000_000).toString().padStart(DIGITS, '0')
    }

    private fun base32Encode(data: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val result = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                result.append(alphabet[(buffer shr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) {
            result.append(alphabet[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return result.toString()
    }

    private fun base32Decode(input: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val cleaned = input.uppercase().replace(" ", "").replace("-", "")
        val bytes = mutableListOf<Byte>()
        var bits = 0
        var value = 0
        for (c in cleaned) {
            val index = alphabet.indexOf(c)
            if (index == -1) continue
            value = (value shl 5) or index
            bits += 5
            if (bits >= 8) {
                bits -= 8
                bytes.add((value shr bits).toByte())
            }
        }
        return bytes.toByteArray()
    }

    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }
}
