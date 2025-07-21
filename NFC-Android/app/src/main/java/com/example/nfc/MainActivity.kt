package com.example.nfc

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.nfc.reader.MrtdReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Opt-in for experimental Material3 APIs
import androidx.compose.material3.ExperimentalMaterial3Api

enum class AppStatus { WAITING, READY, IN_PROGRESS, SUCCESS }

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent

    private val statusState = mutableStateOf(AppStatus.WAITING)
    private val resultState = mutableStateOf<MrtdReader.Result?>(null)

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
            NFCAppTheme {
                MainScreen(
                    status = statusState.value,
                    mrtdResult = resultState.value,
                    onRetry = {
                        resultState.value = null
                        statusState.value = AppStatus.READY
                        nfcAdapter?.enableForegroundDispatch(this@MainActivity, pendingIntent, null, null)
                    },
                    docNumber = docNumber.value,
                    onDocChange = { docNumber.value = it },
                    dob = dob.value,
                    onDobChange = { dob.value = it },
                    expiry = expiry.value,
                    onExpiryChange = { expiry.value = it }
                )
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
            val reader = MrtdReader()
            val mrzData = MrtdReader.MrzData(
                documentNumber = docNumber.value.trim(),
                dateOfBirthYYMMDD = dob.value.trim(),
                dateOfExpiryYYMMDD = expiry.value.trim()
            )
            val result = reader.readIdCard(tag, mrzData)

            withContext(Dispatchers.Main) {
                resultState.value = result
                statusState.value = if (result != null) AppStatus.SUCCESS else AppStatus.WAITING
            }
        }
    }
}

@Composable
fun MainScreen(
    status: AppStatus,
    mrtdResult: MrtdReader.Result?,
    onRetry: () -> Unit,
    docNumber: String,
    onDocChange: (String) -> Unit,
    dob: String,
    onDobChange: (String) -> Unit,
    expiry: String,
    onExpiryChange: (String) -> Unit
) {
    when (status) {
        AppStatus.WAITING -> InstructionScreen(onRetry, docNumber, onDocChange, dob, onDobChange, expiry, onExpiryChange)
        AppStatus.IN_PROGRESS -> LoadingScreen()
        AppStatus.SUCCESS -> SuccessScreen(mrtdResult!!, onRetry)
        else -> LoadingScreen()
    }
}

@Composable
fun InstructionScreen(
    onRetry: () -> Unit,
    docNumber: String,
    onDocChange: (String) -> Unit,
    dob: String,
    onDobChange: (String) -> Unit,
    expiry: String,
    onExpiryChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Hold your ID card to the back of the phone", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(value = docNumber, onValueChange = onDocChange, label = { Text("Document No") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = dob, onValueChange = onDobChange, label = { Text("Date of birth YYMMDD") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = expiry, onValueChange = onExpiryChange, label = { Text("Date of expiry YYMMDD") })
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("Start scan") }
    }
}

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuccessScreen(result: MrtdReader.Result, onRetry: () -> Unit) {
    val fields = result.mrz
    val imageBytes = result.dg2Image

    val keyValues = listOf(
        "Document code" to fields.documentCode,
        "Document number" to fields.documentNumber,
        "Issuing state" to fields.issuingState,
        "Nationality" to fields.nationality,
        "Primary identifier" to fields.primaryIdentifier,
        "Secondary identifier" to fields.secondaryIdentifier,
        "Gender" to fields.gender,
        "Date of birth YYMMDD" to fields.dateOfBirthYYMMDD,
        "Date of expiry YYMMDD" to fields.dateOfExpiryYYMMDD,
        "Optional data 1" to (fields.optionalData1 ?: ""),
        "Optional data 2" to (fields.optionalData2 ?: ""),
        "Personal number" to (fields.personalNumber ?: ""),
        "DG1 raw (hex)" to result.dg1RawHex
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("DG1 & Photo") }) }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = paddingValues,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (imageBytes != null) {
                item {
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "ID Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            items(keyValues) { (key, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = "$key:",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(150.dp)
                    )
                    Text(text = value)
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(onClick = onRetry) {
                        Text("Scan another card")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewSuccess() {
    val dummyFields = MrtdReader.MrzFields(
        documentCode = "P",
        documentNumber = "E17113085",
        issuingState = "LBN",
        nationality = "LBN",
        primaryIdentifier = "SOLH",
        secondaryIdentifier = "YOUSEF",
        gender = "M",
        dateOfBirthYYMMDD = "160115",
        dateOfExpiryYYMMDD = "280114",
        optionalData1 = null,
        optionalData2 = null,
        personalNumber = null
    )
    val dummyImage = ByteArray(0)
    NFCAppTheme {
        SuccessScreen(result = MrtdReader.Result(dummyFields, "DE AD BE EF", dummyImage), onRetry = {})
    }
}

@Composable
fun NFCAppTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        content()
    }
}
