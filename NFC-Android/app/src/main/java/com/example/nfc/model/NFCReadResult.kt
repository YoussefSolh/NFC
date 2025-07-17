package com.example.nfc.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NFCReadResult(
    @SerialName("success") val success: Boolean = false,
    @SerialName("errorMessage") val errorMessage: String? = null,
    @SerialName("IDDocumentData") val idDocumentData: IDDocumentData? = null,
    @SerialName("dg1Info") val dg1Info: String? = null,
    @SerialName("validityInfo") val validityInfo: String? = null,
    @SerialName("IDImage") val idImage: ByteArray? = null,
    @SerialName("readTimestamp") val readTimestamp: Long = System.currentTimeMillis(),
    @SerialName("processingTimeMs") val processingTimeMs: Long = 0,
    @SerialName("warnings") val warnings: List<String>? = null
)
