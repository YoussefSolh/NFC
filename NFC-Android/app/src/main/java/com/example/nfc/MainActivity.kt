package com.example.nfc

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.nfc.ui.theme.NFCTheme
import com.example.nfc.model.NFCReadResult
import com.example.nfc.model.MRTDService

// Add import for MainScreen
import com.example.nfc.ui.MainScreen

enum class AppStatus { READY, IN_PROGRESS, SUCCESS, WAITING }

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private val statusState: MutableState<AppStatus> = mutableStateOf(AppStatus.WAITING)
    private val resultState: MutableState<NFCReadResult?> = mutableStateOf(null)
    private val docNumber = mutableStateOf("E17113085")
    private val dob = mutableStateOf("160115")
    private val expiry = mutableStateOf("280114")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        val permissionLauncher = registerForActivityResult(RequestPermission()) { granted ->
            statusState.value = if (granted) AppStatus.READY else AppStatus.WAITING
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.NFC) == PackageManager.PERMISSION_GRANTED) {
            statusState.value = AppStatus.READY
        } else {
            permissionLauncher.launch(Manifest.permission.NFC)
        }

        setContent {
            NFCTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        status = statusState.value,
                        result = resultState.value,
                        modifier = Modifier.padding(innerPadding),
                        onRetry = {
                            resultState.value = null
                            statusState.value = AppStatus.READY
                        },
                        docNumber = docNumber.value,
                        onDocNumberChange = { docNumber.value = it },
                        dateOfBirth = dob.value,
                        onDobChange = { dob.value = it },
                        dateOfExpiry = expiry.value,
                        onExpiryChange = { expiry.value = it }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post {
            if (statusState.value == AppStatus.READY) {
                nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return
        statusState.value = AppStatus.IN_PROGRESS

        val doc = docNumber.value.padEnd(9, '<')
        val dobStr = dob.value
        val expStr = expiry.value

        if (doc.isBlank() || dobStr.length != 6 || expStr.length != 6) {
            resultState.value = NFCReadResult(success = false, errorMessage = "Invalid MRZ fields")
            statusState.value = AppStatus.WAITING
            return
        }

        val mrzInfo = doc + calculateCheckDigit(doc) +
                dobStr + calculateCheckDigit(dobStr) +
                expStr + calculateCheckDigit(expStr)

        val result = runCatching {
            val isoDep = IsoDep.get(tag) ?: return@runCatching NFCReadResult(success = false, errorMessage = "IsoDep not supported")
            isoDep.connect()

            val selectApdu = byteArrayOf(
                0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x0C.toByte(), 0x07.toByte(),
                0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x47.toByte(), 0x10.toByte(), 0x01.toByte()
            )
            val selectResponse = isoDep.transceive(selectApdu)
            if (!selectResponse.takeLast(2).toByteArray().contentEquals(byteArrayOf(0x90.toByte(), 0x00.toByte()))) {
                isoDep.close()
                return@runCatching NFCReadResult(success = false, errorMessage = "Failed to select ePassport applet")
            }

            val readBinary = byteArrayOf(
                0x00.toByte(), 0xB0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x10.toByte()
            )
            val dg1Response = isoDep.transceive(readBinary)

            isoDep.close()

            val text = dg1Response.map { if (it in 0x20..0x7E) it.toInt().toChar() else '.' }.joinToString("")
            val idData = MRTDService.parseDG1Data(text)
            NFCReadResult(success = true, dg1Info = text, idDocumentData = idData)
        }.getOrElse {
            NFCReadResult(success = false, errorMessage = it.message)
        }

        resultState.value = result
        statusState.value = if (result.success) AppStatus.SUCCESS else AppStatus.WAITING
    }

    fun calculateCheckDigit(data: String): String {
        val weights = intArrayOf(7, 3, 1)
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ<"
        var sum = 0
        for ((i, c) in data.withIndex()) {
            val value = when {
                c in '0'..'9' -> c - '0'
                c in 'A'..'Z' -> c - 'A' + 10
                c == '<' -> 0
                else -> 0
            }
            sum += value * weights[i % 3]
        }
        return (sum % 10).toString()
    }
}
