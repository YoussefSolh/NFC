package com.example.nfc

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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
import java.nio.charset.Charset

enum class AppStatus { READY, IN_PROGRESS, SUCCESS, WAITING }

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private val statusState: MutableState<AppStatus> = mutableStateOf(AppStatus.WAITING)
    private val jsonState: MutableState<String?> = mutableStateOf(null)

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
                        json = jsonState.value,
                        modifier = Modifier.padding(innerPadding)
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val tag: Tag = intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return
        statusState.value = AppStatus.IN_PROGRESS
        jsonState.value = readTag(tag)
        statusState.value = AppStatus.SUCCESS
    }

    private fun readTag(tag: Tag): String {
        return try {
            val ndef = Ndef.get(tag)
            ndef.connect()
            val msg: NdefMessage = ndef.ndefMessage
            val records = msg.records.mapIndexed { index: Int, record: NdefRecord ->
                val payload = String(record.payload, Charset.forName("UTF-8"))
                "\"record$index\": \"$payload\""
            }
            ndef.close()
            "{ " + records.joinToString(",") + " }"
        } catch (e: Exception) {
            "{}"
        }
    }
}

@Composable
fun MainScreen(status: AppStatus, json: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_round),
            contentDescription = null,
            modifier = Modifier.size(120.dp)
        )
        Text(text = "NFC Reader", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        StatusBadge(status = status)
        json?.let {
            Text(text = it)
        }
    }
}

@Composable
fun StatusBadge(status: AppStatus) {
    val color = when (status) {
        AppStatus.READY -> Color.Yellow
        AppStatus.IN_PROGRESS -> Color.Blue
        AppStatus.SUCCESS -> Color.Green
        AppStatus.WAITING -> Color.Gray
    }
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(text = status.name, color = Color.White)
    }
}
