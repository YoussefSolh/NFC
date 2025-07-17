package com.example.nfc.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MrzAuthRequest(
    @SerialName("documentNumber") var documentNumber: String,
    @SerialName("dateOfBirth") var dateOfBirth: String,
    @SerialName("dateOfExpiry") var dateOfExpiry: String,
    @SerialName("verifySOD") var verifySOD: Boolean = false,
    @SerialName("timeoutSeconds") var timeoutSeconds: Int = 30
)
