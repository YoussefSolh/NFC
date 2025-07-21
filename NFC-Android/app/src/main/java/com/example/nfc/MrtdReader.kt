package com.example.nfc.reader

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.IsoDepCardService
import org.jmrtd.BACKey
import org.jmrtd.PassportService

/**
 * Robust low‑level reader for Iraqi eID / ePassport chips (MRTD).
 *
 * – Auto‑detects the correct SELECT‑AID format.
 * – Probes EF.CardSecurity to see whether BAC/PACE is required and, if so,
 *   performs BAC using provided MRZ info (document number, DOB, expiry).
 * – Uses a *matrix* of P1/P2 variants when selecting files to avoid `6A 80`
 *   caused by hierarchical vs. absolute addressing.
 */
class MrtdReader {

    companion object {
        private const val TAG = "MrtdReader"
        private val MRTD_AID = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x02, 0x47, 0x10, 0x01)
        private const val CARD_SECURITY_FID = 0x011C // EF.CardSecurity

        private val SELECT_AID_VARIANTS = listOf(
            0x0C to true,
            0x0C to false,
            0x00 to true,
            0x00 to false
        )
        private val SELECT_EF_VARIANTS = listOf(
            0x00 to 0x0C, 0x00 to 0x00, 0x02 to 0x0C, 0x02 to 0x00
        )

        internal fun isAuthRequired(sw1: Byte, sw2: Byte) =
            sw1 == 0x69.toByte() && (sw2 == 0x82.toByte() || sw2 == 0x85.toByte())

        private fun buildSelectAid(p2: Int, le: Boolean): ByteArray {
            val header = byteArrayOf(0x00, 0xA4.toByte(), 0x04, p2.toByte(), MRTD_AID.size.toByte())
            return if (le) header + MRTD_AID + 0x00 else header + MRTD_AID
        }
        private fun buildSelectFile(fid: Int, p1: Int, p2: Int) = byteArrayOf(
            0x00, 0xA4.toByte(), p1.toByte(), p2.toByte(), 0x02,
            (fid ushr 8).toByte(), (fid and 0xFF).toByte()
        )
    }

    /* ---------------------------- Public API ---------------------------- */

    data class MrzData(val documentNumber: String, val dateOfBirthYYMMDD: String, val dateOfExpiryYYMMDD: String)

    /**
     * Reads raw data and performs BAC automatically if required.
     */
    suspend fun readIdCardRaw(tag: Tag, mrz: MrzData? = null): List<RawResult> = withContext(Dispatchers.IO) {
        val isoDep = IsoDep.get(tag) ?: return@withContext emptyList()
        val results = mutableListOf<RawResult>()

        try {
            isoDep.connect()
            Log.d(TAG, "IsoDep connected – ID=${tag.id.toHexString()}")

            /* 1️⃣  Select LDS application */
            if (!selectLdsApplication(isoDep)) return@withContext emptyList()

            /* 2️⃣  Check if BAC is needed */
            if (needsBac(isoDep)) {
                Log.i(TAG, "Chip requires BAC/PACE. Attempting BAC …")
                if (mrz == null) {
                    Log.e(TAG, "MRZ not provided – cannot perform BAC")
                    return@withContext emptyList()
                }
                if (!performBac(isoDep, mrz)) {
                    Log.e(TAG, "BAC failed – aborting read")
                    return@withContext emptyList()
                }
                Log.i(TAG, "BAC successful – continuing file access")
            }

            /* 3️⃣  Read EF.SOD (0002) as proof-of-concept */
            val fid = 0x0002
            val selectResp = selectFileFlexible(isoDep, fid) ?: return@withContext results
            val data = readBinary(isoDep, 0, 0xFF) ?: return@withContext results
            results += RawResult(fid.toHex4(), selectResp.toHexString(), data.toHexString())
            results
        } catch (e: Exception) {
            Log.e(TAG, "Reader error: ${e.message}", e)
            emptyList()
        } finally {
            try { if (isoDep.isConnected) isoDep.close() } catch (_: Exception) {}
        }
    }

    /* ------------------------ BAC / PACE Support ------------------------ */

    private fun performBac(isoDep: IsoDep, mrz: MrzData): Boolean {
        isoDep.timeout = 30_000 // 30 s to avoid TagLost during mutual auth
        return try {
            val cardService: CardService = IsoDepCardService(isoDep)
            cardService.open()

            val passportService = PassportService(
                cardService,
                PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                PassportService.DEFAULT_MAX_BLOCKSIZE,
                true,
                false
            ).apply { open() }

            val bacKey = BACKey(mrz.documentNumber, mrz.dateOfBirthYYMMDD, mrz.dateOfExpiryYYMMDD)
            passportService.doBAC(bacKey)
            true
        } catch (e: Exception) {
            Log.e(TAG, "BAC failed: ${e.message}", e)
            false
        }
    }

    /* --------------------------- Helpers --------------------------------- */

    private fun selectLdsApplication(isoDep: IsoDep): Boolean {
        for ((p2, le) in SELECT_AID_VARIANTS) {
            val resp = isoDep.transceive(buildSelectAid(p2, le))
            if (resp.isSuccess()) return true
            if (resp.is6C()) {
                val fixed = buildSelectAid(p2, false) + resp.last()
                if (isoDep.transceive(fixed).isSuccess()) return true
            }
        }
        Log.e(TAG, "LDS SELECT failed")
        return false
    }

    private fun needsBac(isoDep: IsoDep): Boolean {
        for ((p1, p2) in SELECT_EF_VARIANTS) {
            val resp = isoDep.transceive(buildSelectFile(CARD_SECURITY_FID, p1, p2))
            if (resp.isSuccess()) return false
            if (resp.isAuthRequired()) return true
        }
        return false
    }

    private fun selectFileFlexible(isoDep: IsoDep, fid: Int): ByteArray? {
        for ((p1, p2) in SELECT_EF_VARIANTS) {
            val resp = isoDep.transceive(buildSelectFile(fid, p1, p2))
            if (resp.isSuccess()) return resp
        }
        Log.w(TAG, "Cannot select EF ${fid.toHex4()}")
        return null
    }

    private fun readBinary(isoDep: IsoDep, offset: Int, le: Int): ByteArray? {
        val cmd = byteArrayOf(0x00, 0xB0.toByte(), (offset ushr 8).toByte(), (offset and 0xFF).toByte(), le.toByte())
        val resp = isoDep.transceive(cmd)
        return if (resp.isSuccess()) resp.copyOfRange(0, resp.size - 2) else null
    }

    /* --------------------------- Data class ------------------------------ */

    data class RawResult(val fileId: String, val selectResponse: String, val dataRead: String)

    /* -------------------------- Extensions ------------------------------ */

    private fun ByteArray.toHexString() = joinToString(" ") { String.format("%02X", it) }
    private fun Int.toHex4() = String.format("%04X", this)
    private fun ByteArray.isSuccess() = size >= 2 && this[size - 2] == 0x90.toByte() && this[size - 1] == 0x00.toByte()
    private fun ByteArray.is6C() = size >= 2 && this[size - 2] == 0x6C.toByte()
    private fun ByteArray.isAuthRequired() = size >= 2 && MrtdReader.isAuthRequired(this[size - 2], this[size - 1])
}
