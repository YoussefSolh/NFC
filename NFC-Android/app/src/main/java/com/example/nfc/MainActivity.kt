package com.example.nfc

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.nfc.model.NFCReadResult
import com.example.nfc.model.IDDocumentData
import com.example.nfc.reader.MrtdReader
import com.example.nfc.ui.MainScreen
import com.example.nfc.ui.theme.NFCTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppStatus { READY, IN_PROGRESS, SUCCESS, WAITING }

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent

    private val statusState = mutableStateOf(AppStatus.WAITING)
    private val resultState = mutableStateOf<NFCReadResult?>(null)

    // MRZ input states
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

        // NFC permission
        val permissionLauncher = registerForActivityResult(RequestPermission()) { granted ->
            statusState.value = if (granted) AppStatus.READY else AppStatus.WAITING
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.NFC)
            == PackageManager.PERMISSION_GRANTED
        ) {
            statusState.value = AppStatus.READY
        } else {
            permissionLauncher.launch(Manifest.permission.NFC)
        }

        // UI
        setContent {
            NFCTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    MainScreen(
                        status = statusState.value,
                        result = resultState.value,
                        modifier = Modifier.padding(inner),
                        onRetry = {
                            resultState.value = null
                            statusState.value = AppStatus.READY
                            nfcAdapter?.enableForegroundDispatch(
                                this@MainActivity, pendingIntent, null, null
                            )
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
        if (statusState.value == AppStatus.READY) {
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
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

        lifecycleScope.launch(Dispatchers.IO) {

            // 👉 Build MRZ data from user inputs
            val mrzData = MrtdReader.MrzData(
                documentNumber = docNumber.value.trim(),
                dateOfBirthYYMMDD = dob.value.trim(),
                dateOfExpiryYYMMDD = expiry.value.trim()
            )

            val result = runCatching {
                val reader = MrtdReader()
                val responses = reader.readIdCardRaw(tag, mrzData)

                if (responses.isNotEmpty()) {
                    val first = responses.first()
                    NFCReadResult(
                        success = true,
                        idDocumentData = IDDocumentData(
                            firstName = first.toString(),  // placeholder: parse DG1 later
                            documentNumber = first.fileId,
                            dateOfBirth = dob.value,
                            dateOfExpiry = expiry.value
                        ),
                        dg1Info = null,
                        validityInfo = null,
                        idImage = null,
                        readTimestamp = System.currentTimeMillis(),
                        processingTimeMs = 0,
                        warnings = null
                    )
                } else {
                    NFCReadResult(success = false, errorMessage = "Failed to read NFC chip")
                }
            }.getOrElse {
                NFCReadResult(success = false, errorMessage = it.message)
            }

            withContext(Dispatchers.Main) {
                resultState.value = result
                statusState.value = if (result.success) AppStatus.SUCCESS else AppStatus.WAITING
            }
        }
    }
}
