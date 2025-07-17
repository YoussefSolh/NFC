package com.example.nfc.model

import java.text.SimpleDateFormat
import java.util.Locale

object MRTDService {
    fun parseDG1Data(dg1Text: String): IDDocumentData {
        val result = IDDocumentData()
        val parts = dg1Text.split("<<")
        if (parts.size >= 2) {
            val nameParts = parts[0].split("<")
            result.lastname = nameParts.firstOrNull()?.replace("<", " ")?.trim()
            result.firstName = nameParts.drop(1).joinToString(" ") { it.replace("<", " ") }.trim()
            val remaining = parts[1]
            if (remaining.length >= 30) {
                result.documentNumber = remaining.substring(0, 9).trim('<')
                result.nationality = remaining.substring(10, 13)
                val dobRaw = remaining.substring(13, 19)
                val expRaw = remaining.substring(21, 27)
                val sdf = SimpleDateFormat("yyMMdd", Locale.US)
                result.dateOfBirth = try { sdf.parse(dobRaw)?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it) } } catch (e: Exception) { dobRaw }
                result.dateOfExpiry = try { sdf.parse(expRaw)?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it) } } catch (e: Exception) { expRaw }
                result.sex = remaining[20]
            }
        }
        return result
    }
}
