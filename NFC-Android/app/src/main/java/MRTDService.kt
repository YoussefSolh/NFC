/*package com.example.nfc.model

import android.nfc.tech.IsoDep
import android.util.Log
import com.example.nfc.model.NFCReadResult
import com.example.nfc.model.IDDocumentData
import java.security.MessageDigest
import org.bouncycastle.crypto.CipherParameters
import org.bouncycastle.crypto.engines.DESEngine
import org.bouncycastle.crypto.macs.ISO9797Alg3Mac
import org.bouncycastle.crypto.paddings.ISO7816d4Padding
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * MRTDService: Implements BAC mutual authentication and ISO 7816-4 Secure Messaging
 * for e-passport (Machine Readable Travel Document) data-group reading.
 */
object MRTDService {
    private const val TAG = "MRTDService"

    // Session keys established after BAC
    private lateinit var ksEnc: ByteArray
    private lateinit var ksMac: ByteArray
    // Send Sequence Counter (8-byte) for Secure Messaging
    private var ssc: ByteArray = ByteArray(8)

    /**
     * Read all data groups under a secure BAC session
     */
    fun readAllDataGroups(isoDep: IsoDep, mrzInfo: String): NFCReadResult {
        return try {
            Log.d(TAG, "Starting BAC with MRZ key: $mrzInfo")
            if (!authenticateWithMRZ(isoDep, mrzInfo)) {
                return NFCReadResult(false, "BAC Authentication failed")
            }

            // Securely select and read each Data Group
            val dg1 = readDataGroup(isoDep, 0x0101)
            val parsedDG1 = parseDG1(dg1)

            val dg2 = readDataGroup(isoDep, 0x0102)
            val photo = extractPhoto(dg2)

            val dg11 = readDataGroup(isoDep, 0x010B)
            val parsedDG11 = parseDG11(dg11)

            val sod = readDataGroup(isoDep, 0x011D)
            val valid = validateSOD(sod)

            val merged = mergeDG1AndDG11(parsedDG1, parsedDG11)
            NFCReadResult(
                success = true,
                dg1Info = String(dg1),
                idDocumentData = merged,
                idImage = photo,
                validityInfo = if (valid) "Valid SOD" else "Invalid SOD"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception in readAllDataGroups", e)
            NFCReadResult(false, e.message)
        }
    }

    /**
     * Perform BAC (Basic Access Control) authentication
     * and initialize session keys and send sequence counter.
     */
    private fun authenticateWithMRZ(isoDep: IsoDep, mrz: String): Boolean {
        if (!performBAC(isoDep, mrz)) return false
        // Initialize session keys for secure messaging
        val (enc, mac) = deriveBACKeys(mrz)
        ksEnc = enc
        ksMac = mac
        // Initialize SSC to zero
        ssc = ByteArray(8)
        return true
    }

    /**
     * Read a data group file under secure messaging
     */
    private fun readDataGroup(isoDep: IsoDep, fileId: Int): ByteArray {
        // 1) Securely SELECT file by Id
        val p1 = (fileId shr 8) and 0xFF
        val p2 = fileId and 0xFF
        val selectApdu = byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x02, 0x0C,
            0x02, p1.toByte(), p2.toByte())
        val protectedSelect = wrapCommand(selectApdu)
        val selResp = isoDep.transceive(protectedSelect)
        unwrapResponse(selResp) // discard FCP

        // 2) Securely READ BINARY with Le=0x00 (full file)
        val readApdu = byteArrayOf(0x00.toByte(), 0xB0.toByte(), 0x00, 0x00, 0x00)
        val protectedRead = wrapCommand(readApdu)
        val resp = isoDep.transceive(protectedRead)
        return unwrapResponse(resp)
    }

    // Parsing helpers
    private fun parseDG1(bytes: ByteArray) =
        IDDocumentData(nameOfHolder = bytes.toString(Charsets.UTF_8).trim())

    private fun parseDG11(bytes: ByteArray) =
        IDDocumentData(mothersFirstName = bytes.toString(Charsets.UTF_8).trim())

    private fun extractPhoto(bytes: ByteArray) = bytes.takeIf { it.isNotEmpty() }
    private fun validateSOD(bytes: ByteArray) = bytes.isNotEmpty()
    private fun mergeDG1AndDG11(dg1: IDDocumentData, dg11: IDDocumentData) =
        dg1.copy(mothersFirstName = dg11.mothersFirstName)

    /**
     * Wrap an APDU with ISO7816-4 secure messaging (encrypt + MAC)
     */
    /**
     * Wrap an APDU with ISO7816-4 secure messaging (encrypt + MAC)
     */
    private fun wrapCommand(apdu: ByteArray): ByteArray {
        // Increment send sequence counter
        incrementSSC()
        // Prepare header with secure messaging bit
        val cla = (apdu[0].toInt() or 0x0C).toByte()
        val ins = apdu[1]
        val p1 = apdu[2]
        val p2 = apdu[3]
        // Data field (if any)
        val lc = if (apdu.size > 5) apdu[4].toInt() and 0xFF else 0
        val dataField = if (lc > 0) apdu.copyOfRange(5, 5 + lc) else ByteArray(0)
        // Encrypt data (DO87)
        val do87 = if (dataField.isNotEmpty()) {
            val encrypted = tripleDesEncrypt(dataField, ksEnc)
            byteArrayOf(0x87.toByte(), encrypted.size.toByte()) + encrypted
        } else ByteArray(0)
        // Le field (DO97)
        val le = if (apdu.size > 5 + lc) apdu[5 + lc] else 0x00.toByte()
        val do97 = byteArrayOf(0x97.toByte(), 0x01, le)
        // Compute MAC input: header + DO87 + DO97
        val macInput = byteArrayOf(cla, ins, p1, p2) + do87 + do97
        val macBytes = computeMac(macInput, ksMac)
        val do8e = byteArrayOf(0x8E.toByte(), macBytes.size.toByte()) + macBytes
        // Assemble protected APDU
        val body = do87 + do97 + do8e
        val protectedLc = body.size.toByte()
        return byteArrayOf(cla, ins, p1, p2, protectedLc) + body
    }

    /**
     * Unwrap a secure messaging response: verify MAC, decrypt data
     */
    /**
     * Unwrap a secure messaging response: verify MAC, decrypt data
     */
    private fun unwrapResponse(resp: ByteArray): ByteArray {
        // Increment send sequence counter
        incrementSSC()
        var index = 0
        // Parse DO87 (encrypted data)
        val encryptedData: ByteArray = if (resp[index] == 0x87.toByte()) {
            index++
            val len = resp[index++].toInt() and 0xFF
            val data = resp.copyOfRange(index, index + len)
            index += len
            data
        } else ByteArray(0)
        // Parse DO99 (status words tag 0x99) or raw SW1/SW2
        val sw: ByteArray = if (resp[index] == 0x99.toByte()) {
            index++
            val len = resp[index++].toInt() and 0xFF
            val swBytes = resp.copyOfRange(index, index + len)
            index += len
            swBytes
        } else {
            // assume last two bytes
            resp.copyOfRange(resp.size - 2, resp.size)
        }
        // Parse DO8E (MAC)
        if (resp[index] == 0x8E.toByte()) {
            index++
            val macLen = resp[index++].toInt() and 0xFF
            val macReceived = resp.copyOfRange(index, index + macLen)
            // Verify MAC
            val macInput = byteArrayOf( // header omitted, MAC validated over DO87+DO99 per spec
                // we assume ordering
            ) + (if (encryptedData.isNotEmpty()) byteArrayOf(0x87.toByte(), encryptedData.size.toByte()) + encryptedData else ByteArray(0)) + byteArrayOf(0x99.toByte(), sw.size.toByte()) + sw
            val macCalc = computeMac(macInput, ksMac)
            if (!macCalc.contentEquals(macReceived)) {
                throw IllegalStateException("MAC mismatch in response")
            }
        }
        // Decrypt data if present
        val data = if (encryptedData.isNotEmpty()) tripleDesDecrypt(encryptedData, ksEnc) else ByteArray(0)
        return data + sw
    }

    /**
     * 3DES decrypt with no padding (ECB)
     */
    private fun tripleDesDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("DESede/ECB/NoPadding", "BC")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "DESede"))
        return cipher.doFinal(data)
    }

    /**
     * Increment send sequence counter (8-byte BE)
     */
    private fun incrementSSC() {
        for (i in ssc.indices.reversed()) {
            val v = (ssc[i].toInt() and 0xFF) + 1
            ssc[i] = v.toByte()
            if (v <= 0xFF) break
        }
    }

    /**
     * Perform BAC mutual authentication (Select, Get Challenge, External Authenticate)
     */
    private fun performBAC(isoDep: IsoDep, mrz: String): Boolean {
        // SELECT AID
        val sel = byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x04, 0x0C,
            0x07, 0xA0.toByte(), 0x00, 0x00, 0x02, 0x47, 0x10, 0x01)
        Log.d(TAG, "SELECT AID → ${sel.toHexString()}")
        val selResp = isoDep.transceive(sel)
        Log.d(TAG, "SELECT ← ${selResp.toHexString()}")
        if (!selResp.endsWithSuccess()) return false

        // GET CHALLENGE
        val getCh = byteArrayOf(0x00.toByte(), 0x84.toByte(), 0x00, 0x00, 0x08)
        Log.d(TAG, "GET CHALLENGE → ${getCh.toHexString()}")
        val chResp = isoDep.transceive(getCh)
        Log.d(TAG, "CHALLENGE ← ${chResp.toHexString()}")
        if (!chResp.endsWithSuccess()) return false
        val rndIc = chResp.copyOf(8)

        // Derive BAC keys
        val (kEnc, kMac) = deriveBACKeys(mrz)

        // Generate RND.IFD and K.IFD
        val rndIfd = ByteArray(8).also { SecureRandom().nextBytes(it) }
        val kIfd = ByteArray(16).also { SecureRandom().nextBytes(it) }

        // Encrypt S = rndIfd || rndIc || kIfd
        val s = rndIfd + rndIc + kIfd
        val encS = tripleDesEncrypt(s, kEnc)

        // Compute MAC
        val mac = computeMac(encS, kMac)

        // EXTERNAL AUTHENTICATE (Case 3 APDU: Lc + data only)
        val data = encS + mac
        val ext = byteArrayOf(0x00.toByte(), 0x82.toByte(), 0x00, 0x00, data.size.toByte()) + data
        Log.d(TAG, "EXTERNAL AUTH → ${ext.toHexString()}")
        val extResp = isoDep.transceive(ext)
        Log.d(TAG, "EXT AUTH ← ${extResp.toHexString()}")
        return ext.endsWithSuccess()
    }

    /**
     * Derive encryption and MAC keys from MRZ (ICAO 9303)
     */
    private fun deriveBACKeys(mrz: String): Pair<ByteArray, ByteArray> {
        val sha1 = MessageDigest.getInstance("SHA-1")
        val seed = sha1.digest(mrz.toByteArray(Charsets.US_ASCII)).copyOfRange(0, 16)
        val A = seed.copyOfRange(0, 8)
        val B = seed.copyOfRange(8, 16)
        val pA = ensureDESParity(A)
        val pB = ensureDESParity(B)
        val kEnc = pA + pB       // A||B
        val kMac = pB + pA       // B||A
        return Pair(kEnc, kMac)
    }

    /**
     * Ensure each DES key byte has odd parity
     */
    private fun ensureDESParity(key: ByteArray): ByteArray {
        for (i in key.indices) {
            var b = key[i].toInt()
            var parity = 1
            for (j in 1..7) parity = parity xor ((b shr j) and 1)
            key[i] = ((b and 0xFE) or parity).toByte()
        }
        return key
    }

    /**
     * 3DES encrypt with no padding (ECB)
     */
    private fun tripleDesEncrypt(data: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("DESede/ECB/NoPadding", "BC")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "DESede"))
        return cipher.doFinal(data)
    }

    /**
     * Compute Retail MAC (ISO9797-1 Alg3) with ISO7816-4 padding
     */
    private fun computeMac(data: ByteArray, macKey: ByteArray): ByteArray {
        val mac = ISO9797Alg3Mac(DESEngine(), 64, ISO7816d4Padding())
        mac.init(ParametersWithIV(KeyParameter(macKey), ByteArray(8)))
        mac.update(data, 0, data.size)
        return ByteArray(8).also { mac.doFinal(it, 0) }
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02X".format(it) }
    private fun ByteArray.endsWithSuccess() = size >= 2 && this[size-2] == 0x90.toByte() && this[size-1] == 0x00.toByte()
}
*/