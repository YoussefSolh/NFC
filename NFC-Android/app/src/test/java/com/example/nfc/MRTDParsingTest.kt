package com.example.nfc

import com.example.nfc.model.MRTDDataGroupReader
import com.example.nfc.model.MRTDService
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MRTDParsingTest {
    @Test
    fun parseDG1Data_parsesFieldsCorrectly() {
        val dg1 = "DOE<JOHN<<A123456785USA9001013M2501017ZZZ"
        val result = MRTDService.parseDG1Data(dg1)

        assertEquals("DOE", result.lastname)
        assertEquals("JOHN", result.firstName)
        assertEquals("A12345678", result.documentNumber)
        assertEquals("USA", result.nationality)
        assertEquals("1990-01-01", result.dateOfBirth)
        assertEquals("2025-01-01", result.dateOfExpiry)
        assertEquals('M', result.sex)
    }

    @Test
    fun parseTlvs_returnsIndividualEntries() {
        val data = byteArrayOf(
            0x5F.toByte(), 0x0E.toByte(), 0x03, 'A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte(),
            0x5F.toByte(), 0x0F.toByte(), 0x01, 0x05
        )

        val tlvs = MRTDDataGroupReader.parseTlvs(data)
        assertEquals(2, tlvs.size)
        assertEquals("5F0E", tlvs[0].tag)
        assertEquals(3, tlvs[0].length)
        assertArrayEquals("ABC".toByteArray(), tlvs[0].value)
        assertEquals("5F0F", tlvs[1].tag)
        assertEquals(1, tlvs[1].length)
        assertArrayEquals(byteArrayOf(0x05), tlvs[1].value)
    }

    @Test
    fun extractDg11Data_parsesNameAndAddress() {
        val name = "JOHN<PAUL<DOE".toByteArray()
        val mother = "JANE".toByteArray()
        val idNumber = "12345".toByteArray()
        val address = "STREET".toByteArray()

        val data = byteArrayOf(
            0x5F.toByte(), 0x0E.toByte(), name.size.toByte(), *name,
            0x5F.toByte(), 0x0F.toByte(), mother.size.toByte(), *mother,
            0x5F.toByte(), 0x10.toByte(), idNumber.size.toByte(), *idNumber,
            0x5F.toByte(), 0x11.toByte(), address.size.toByte(), *address
        )

        val result = MRTDDataGroupReader.extractDg11Data(data)
        assertEquals("JOHN", result.firstName)
        assertEquals("PAUL", result.secondName)
        assertEquals("DOE", result.thirdName)
        assertEquals("JANE", result.mothersFirstName)
        assertEquals("12345", result.personalIdNumber)
        assertEquals("STREET", result.address)
    }

    @Test
    fun extractDg2Data_parsesImageAndText() {
        val data = byteArrayOf(
            0x5F.toByte(), 0x2E.toByte(), 0x03, 0x01, 0x02, 0x03,
            0x87.toByte(), 0x01, 0x04,
            0x88.toByte(), 0x01, 0x05,
            0x99.toByte(), 0x02, 'O'.code.toByte(), 'K'.code.toByte()
        )

        val result = MRTDDataGroupReader.extractDG2Data(data)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), result.image)
        assertArrayEquals(byteArrayOf(0x04), result.formatOwner)
        assertArrayEquals(byteArrayOf(0x05), result.formatType)
        assertEquals("OK", result.rawText)
    }
}
