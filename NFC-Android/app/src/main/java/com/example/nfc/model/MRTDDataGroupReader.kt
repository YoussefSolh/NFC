package com.example.nfc.model

object MRTDDataGroupReader {
    data class Tlv(val tag: String, val length: Int, val value: ByteArray)

    fun parseTlvs(data: ByteArray): List<Tlv> {
        val tlvs = mutableListOf<Tlv>()
        var offset = 0
        while (offset < data.size) {
            val tlv = parseTlv(data, offset)
            tlvs.add(tlv)
            offset += tlvHeaderLength(tlv) + tlv.length
        }
        return tlvs
    }

    private fun tlvHeaderLength(tlv: Tlv): Int {
        val tagBytes = tlv.tag.length / 2
        val lengthBytes = if (tlv.length < 0x80) 1 else 1 + Integer.toHexString(tlv.length).length / 2
        return tagBytes + lengthBytes
    }

    private fun parseTlv(data: ByteArray, start: Int): Tlv {
        var offset = start
        val tagBytes = mutableListOf<Byte>()
        tagBytes.add(data[offset++])
        if ((tagBytes[0].toInt() and 0x1F) == 0x1F) {
            while ((data[offset].toInt() and 0x80) == 0x80) {
                tagBytes.add(data[offset++])
            }
            tagBytes.add(data[offset++])
        }
        var length = data[offset++].toInt() and 0xFF
        if (length and 0x80 != 0) {
            val numBytes = length and 0x7F
            length = 0
            repeat(numBytes) {
                length = (length shl 8) + (data[offset++].toInt() and 0xFF)
            }
        }
        val value = data.copyOfRange(offset, offset + length)
        return Tlv(tagBytes.joinToString("") { String.format("%02X", it) }, length, value)
    }

    fun extractDg11Data(bytes: ByteArray): Dg11Data {
        val tlvs = parseTlvs(bytes)
        val result = Dg11Data()
        for (tlv in tlvs) {
            when (tlv.tag) {
                "5F0E" -> {
                    result.rawFullName = tlv.value.decodeToString()
                    val parts = result.rawFullName?.split('<')?.filter { it.isNotEmpty() } ?: emptyList()
                    if (parts.isNotEmpty()) result.firstName = parts[0]
                    if (parts.size > 1) result.secondName = parts[1]
                    if (parts.size > 2) result.thirdName = parts[2]
                    if (parts.size > 3) result.lastname = parts[3]
                }
                "5F0F" -> {
                    result.rawMothersName = tlv.value.decodeToString()
                    val parts = result.rawMothersName?.split('<')?.filter { it.isNotEmpty() } ?: emptyList()
                    if (parts.isNotEmpty()) result.mothersFirstName = parts[0]
                }
                "5F10" -> result.personalIdNumber = tlv.value.decodeToString()
                "5F11" -> result.address = tlv.value.decodeToString()
                "A015" -> if (tlv.value.size >= 3 && tlv.value[0] == 0x02.toByte() && tlv.value[1] == 0x01.toByte()) {
                    result.gender = when (tlv.value[2]) {
                        0x01.toByte() -> "Male"
                        0x02.toByte() -> "Female"
                        else -> "Unknown"
                    }
                }
            }
        }
        return result
    }

    fun extractDG2Data(bytes: ByteArray): Dg2Data {
        val tlvs = parseTlvs(bytes)
        val result = Dg2Data()
        for (tlv in tlvs) {
            when (tlv.tag) {
                "5F2E" -> result.image = tlv.value
                "87" -> result.formatOwner = tlv.value
                "88" -> result.formatType = tlv.value
                else -> result.rawText = (result.rawText ?: "") + tlv.value.decodeToString()
            }
        }
        return result
    }
}
