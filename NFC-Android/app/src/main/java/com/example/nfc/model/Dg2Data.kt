package com.example.nfc.model

data class Dg2Data(
    var image: ByteArray? = null,
    var formatOwner: ByteArray? = null,
    var formatType: ByteArray? = null,
    var rawText: String? = null
)
