package com.example.nfc

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityTest {
    @Test
    fun calculateCheckDigit_returnsExpectedDigit() {
        val activity = MainActivity()
        val check = activity.calculateCheckDigit("L898902C3")
        assertEquals("6", check)
    }
}
