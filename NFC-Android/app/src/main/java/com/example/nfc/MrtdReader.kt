package com.example.nfc.reader

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.IsoDepCardService
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo
import java.io.ByteArrayOutputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

/**
 * **MrtdReader** – high‑level reader inspired by Tananaev's *passport‑reader*.
 *
 * ✦ Tries **PACE** first (via EF.CardAccess). Falls back to **BAC** automatically.
 * ✦ After successful auth, reads **EF.COM**, **EF.SOD** and **DG1** using
 *   JMRTD's secure‐messaging‑aware `PassportService` streams (no raw APDUs).
 * ✦ Validates & normalises MRZ input before authentication (ICAO Doc 9303).
 */
class MrtdReader {

    /* ------------------------------------------------------------------- */
    /*  Constants                                                          */
    /* ------------------------------------------------------------------- */

    companion object {
        private const val TAG = "MrtdReader"

        // TLV FIDs (absolute) – JMRTD constants exist for COM/SOD/DG1
        private const val DG1_FID = 0x0101

        /* Strict MRZ patterns */
        private val DOC_NUM_REGEX: Pattern = Pattern.compile("^[A-Z0-9<]{1,9}")
        private val DATE_YYMMDD_REGEX: Pattern = Pattern.compile("^\\d{6}")

        /** Convert "yyyy‑MM‑dd" or "yyMMdd" into YYMMDD */
        private fun normaliseDate(input: String): String? =
            if (DATE_YYMMDD_REGEX.matcher(input).matches()) input else try {
                SimpleDateFormat("yyMMdd", Locale.US).format(
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(input)!!
                )
            } catch (e: ParseException) { null }
    }

    /* ------------------------------------------------------------------- */
    /*  Public data classes                                                */
    /* ------------------------------------------------------------------- */

    data class MrzData(
        val documentNumber: String,
        val dateOfBirthYYMMDD: String,
        val dateOfExpiryYYMMDD: String
    ) {
        /** Returns sanitised copy (padded document number, YYMMDD dates) or null if invalid. */
        fun sanitised(): MrzData? {
            val doc = documentNumber.trim().uppercase(Locale.US).replace(" ", "")
            val dob = normaliseDate(dateOfBirthYYMMDD.trim())
            val doe = normaliseDate(dateOfExpiryYYMMDD.trim())
            return if (DOC_NUM_REGEX.matcher(doc).matches() && dob != null && doe != null) {
                MrzData(doc.padEnd(9, '<'), dob, doe)
            } else null
        }
    }

    data class RawResult(val fileId: String, val dataHex: String)

    /* ------------------------------------------------------------------- */
    /*  Public API                                                         */
    /* ------------------------------------------------------------------- */

    /**
     * Reads EF.COM, EF.SOD and DG1 using secure messaging after PACE/BAC.
     * Returns list of [RawResult] with `fileId` = "COM" | "SOD" | "DG1".
     */
    suspend fun readIdCardRaw(tag: Tag, mrz: MrzData): List<RawResult> = withContext(Dispatchers.IO) {
        val cleanMrz = mrz.sanitised() ?: run {
            Log.e(TAG, "Invalid MRZ – aborting")
            return@withContext emptyList()
        }

        val isoDep = IsoDep.get(tag) ?: return@withContext emptyList()
        val results = mutableListOf<RawResult>()

        var cardService: CardService? = null
        var passportService: PassportService? = null

        try {
            isoDep.connect()
            Log.d(TAG, "IsoDep connected – ID=${tag.id.toHexString()}")

            cardService = IsoDepCardService(isoDep).apply { open() }
            passportService = PassportService(
                cardService,
                PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                PassportService.DEFAULT_MAX_BLOCKSIZE,
                false,
                false
            ).apply { open() }

            // ---------------- Authentication ----------------
            val bacKey: BACKeySpec = BACKey(
                cleanMrz.documentNumber,
                cleanMrz.dateOfBirthYYMMDD,
                cleanMrz.dateOfExpiryYYMMDD
            )

            val paceSucceeded = tryPace(passportService, bacKey)
            if (!paceSucceeded) {
                Log.i(TAG, "Attempting BAC …")
                passportService.doBAC(bacKey)
            }
            Log.i(TAG, "Authentication OK (PACE=$paceSucceeded)")



            // ---------------- Read DG1 -------------------
            readEfStream(passportService, DG1_FID)?.let {
                results += RawResult("DG1", it.toHexString())
            }

            if (results.isEmpty()) Log.w(TAG, "No files readable after auth")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Reader error", e)
            emptyList()
        } finally {
            try { passportService?.close() } catch (_: Exception) {}
            try { cardService?.close() } catch (_: Exception) {}
            try { if (isoDep.isConnected) isoDep.close() } catch (_: Exception) {}
        }
    }

    /* ------------------------------------------------------------------- */
    /*  Internal helpers                                                   */
    /* ------------------------------------------------------------------- */

    /** Tries PACE using EF.CardAccess; returns true if successful. */
    private fun tryPace(service: PassportService, bacKey: BACKeySpec): Boolean {
        return try {
            val caInput = service.getInputStream(PassportService.EF_CARD_ACCESS)
            val cardAccess = CardAccessFile(caInput)
            for (info in cardAccess.securityInfos) {
                if (info is PACEInfo) {
                    service.doPACE(
                        bacKey,
                        info.objectIdentifier,
                        PACEInfo.toParameterSpec(info.parameterId),
                        null
                    )
                    Log.i(TAG, "PACE succeeded")
                    // JMRTD requires re‑SELECT after PACE
                    service.sendSelectApplet(true)
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "PACE not available / failed: ${e.message}")
            false
        }
    }

    /** Reads a full EF via PassportService secure messaging. */
    private fun readEfStream(service: PassportService, fid: Int): ByteArray? {
        return try {
            val `in` = service.getInputStream(fid.toShort())
            val buffer = ByteArrayOutputStream()
            val tmp = ByteArray(512)
            while (true) {
                val read = `in`.read(tmp)
                if (read < 0) break
                buffer.write(tmp, 0, read)
            }
            buffer.toByteArray().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read EF ${fid.toHex4()}: ${e.message}")
            null
        }
    }

    /* ------------------------------------------------------------------- */
    /*  Extension helpers                                                  */
    /* ------------------------------------------------------------------- */

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
    private fun ByteArray.id() = joinToString(" ") { "%02X".format(it) }
    private fun Int.toHex4(): String = "%04X".format(this)
}
