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
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.icao.MRZInfo
import org.jmrtd.lds.iso19794.FaceImageInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

/**
 * High‑level reader for eMRTDs: PACE→BAC auth + DG1 & DG2 parsing.
 */
class MrtdReader {

    companion object {
        private const val TAG = "MrtdReader"
        private val DOC_NUM_REGEX = Pattern.compile("^[A-Z0-9<]{1,9}")
        private val DATE_YYMMDD_REGEX = Pattern.compile("^\\d{6}")

        private fun normaliseDate(input: String): String? =
            if (DATE_YYMMDD_REGEX.matcher(input).matches()) input else try {
                SimpleDateFormat("yyMMdd", Locale.US).format(
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(input)!!
                )
            } catch (e: ParseException) {
                null
            }
    }

    data class MrzData(
        val documentNumber: String,
        val dateOfBirthYYMMDD: String,
        val dateOfExpiryYYMMDD: String
    ) {
        fun sanitised(): MrzData? {
            val doc = documentNumber.trim().uppercase(Locale.US).replace(" ", "")
            val dob = normaliseDate(dateOfBirthYYMMDD.trim())
            val doe = normaliseDate(dateOfExpiryYYMMDD.trim())
            return if (DOC_NUM_REGEX.matcher(doc).matches() && dob != null && doe != null) {
                MrzData(doc.padEnd(9, '<'), dob, doe)
            } else null
        }
    }

    data class MrzFields(
        val documentCode: String,
        val documentNumber: String,
        val issuingState: String,
        val nationality: String,
        val primaryIdentifier: String,
        val secondaryIdentifier: String,
        val gender: String,
        val dateOfBirthYYMMDD: String,
        val dateOfExpiryYYMMDD: String,
        val optionalData1: String?,
        val optionalData2: String?,
        val personalNumber: String?
    )

    data class Result(
        val mrz: MrzFields,
        val dg1RawHex: String,
        val dg2Image: ByteArray?
    )

    /**
     * Perform PACE (if available) or BAC, then read DG1 & DG2 and parse both.
     */
    suspend fun readIdCard(tag: Tag, mrzInput: MrzData): Result? = withContext(Dispatchers.IO) {
        val cleanMrz = mrzInput.sanitised() ?: run {
            Log.e(TAG, "Invalid MRZ – aborting")
            return@withContext null
        }
        val isoDep = IsoDep.get(tag) ?: return@withContext null
        var cardService: CardService? = null
        var passportService: PassportService? = null

        try {
            isoDep.connect()
            cardService = IsoDepCardService(isoDep).apply { open() }
            passportService = PassportService(
                cardService,
                PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                PassportService.DEFAULT_MAX_BLOCKSIZE,
                false,
                false
            ).apply { open() }

            // Authentication: PACE -> BAC fallback
            val bacKey = BACKey(
                cleanMrz.documentNumber,
                cleanMrz.dateOfBirthYYMMDD,
                cleanMrz.dateOfExpiryYYMMDD
            )
            val paceOk = tryPACE(passportService, bacKey)
            if (!paceOk) passportService.doBAC(bacKey)
            Log.i(TAG, "Auth OK – PACE=$paceOk")

            // Read DG1 & DG2
            val dg1Bytes = readEF(passportService, PassportService.EF_DG1)
                ?: throw IllegalStateException("DG1 read failed after auth")
            val dg2Bytes = readEF(passportService, PassportService.EF_DG2)

            // Parse MRZ fields
            val fields = parseDG1(dg1Bytes)

            // Parse face image from DG2
            val faceImage: ByteArray? = try {
                dg2Bytes?.let {
                    val dg2 = DG2File(ByteArrayInputStream(it))
                    dg2.faceInfos
                        .flatMap { fi -> fi.faceImageInfos }
                        .firstOrNull()
                        ?.imageInputStream
                        ?.readBytes()
                }
            } catch (e: Exception) {
                Log.w(TAG, "DG2 parse error: ${e.message}")
                null
            }

            Result(fields, dg1Bytes.toHexString(), faceImage)
        } catch (e: Exception) {
            Log.e(TAG, "Reader error", e)
            null
        } finally {
            try { passportService?.close() } catch (_: Exception) {}
            try { cardService?.close() } catch (_: Exception) {}
            try { if (isoDep.isConnected) isoDep.close() } catch (_: Exception) {}
        }
    }

    private fun tryPACE(service: PassportService, bacKey: BACKeySpec): Boolean = try {
        val caf = CardAccessFile(service.getInputStream(PassportService.EF_CARD_ACCESS))
        caf.securityInfos.filterIsInstance<PACEInfo>().forEach { info ->
            service.doPACE(bacKey, info.objectIdentifier, PACEInfo.toParameterSpec(info.parameterId), null)
        }
        service.sendSelectApplet(true)
        true
    } catch (e: Exception) {
        Log.w(TAG, "PACE unavailable: ${e.message}")
        false
    }

    private fun readEF(service: PassportService, fid: Short): ByteArray? = try {
        service.getInputStream(fid).use { input ->
            val buf = ByteArrayOutputStream()
            val tmp = ByteArray(512)
            while (true) {
                val r = input.read(tmp).takeIf { it > 0 } ?: break
                buf.write(tmp, 0, r)
            }
            buf.toByteArray().takeIf { it.isNotEmpty() }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Read EF ${"%04X".format(fid)} failed: ${e.message}")
        null
    }

    private fun parseDG1(bytes: ByteArray): MrzFields {
        val dg1 = DG1File(ByteArrayInputStream(bytes))
        val m: MRZInfo = dg1.mrzInfo
        return MrzFields(
            documentCode    = m.documentCode,
            documentNumber  = m.documentNumber,
            issuingState    = m.issuingState,
            nationality     = m.nationality,
            primaryIdentifier   = m.primaryIdentifier,
            secondaryIdentifier = m.secondaryIdentifier,
            gender          = m.gender.toString(),
            dateOfBirthYYMMDD  = m.dateOfBirth,
            dateOfExpiryYYMMDD = m.dateOfExpiry,
            optionalData1   = m.optionalData1,
            optionalData2   = m.optionalData2,
            personalNumber  = m.personalNumber
        )
    }

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
}
