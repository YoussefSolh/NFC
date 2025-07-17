package com.example.nfc.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IDDocumentData(
    @SerialName("firstName") var firstName: String? = null,
    @SerialName("secondName") var secondName: String? = null,
    @SerialName("thirdName") var thirdName: String? = null,
    @SerialName("lastname") var lastname: String? = null,
    @SerialName("mothersFirstName") var mothersFirstName: String? = null,
    @SerialName("documentCode") var documentCode: String? = null,
    @SerialName("issuingState") var issuingState: String? = null,
    @SerialName("documentNumber") var documentNumber: String? = null,
    @SerialName("optionalData1") var optionalData1: String? = null,
    @SerialName("dateOfBirth") var dateOfBirth: String? = null,
    @SerialName("sex") var sex: Char? = null,
    @SerialName("dateOfExpiry") var dateOfExpiry: String? = null,
    @SerialName("nationality") var nationality: String? = null,
    @SerialName("optionalData2") var optionalData2: String? = null,
    @SerialName("nameOfHolder") var nameOfHolder: String? = null,
    @SerialName("ArabicFullName") var arabicFullName: String? = null,
    @SerialName("additionalFields") var additionalFields: Map<String, String>? = null
)
