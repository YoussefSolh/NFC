package com.example.nfc.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nfc.model.NFCReadResult

@Composable
fun ResultScreen(
    result: NFCReadResult?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Scan Result", fontWeight = FontWeight.Bold, fontSize = 20.sp)

        result?.let { res ->
            res.idDocumentData?.let { doc ->
                doc.firstName?.let { Text("First Name: $it") }
                doc.secondName?.let { Text("Second Name: $it") }
                doc.thirdName?.let { Text("Third Name: $it") }
                doc.lastname?.let { Text("Surname: $it") }
                doc.nationality?.let { Text("Nationality: $it") }
                doc.dateOfBirth?.let { Text("Birth Date: $it") }
                doc.sex?.let { Text("Gender: $it") }
                doc.dateOfExpiry?.let { Text("Expiry Date: $it") }
            }
            res.idImage?.let { bytes ->
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                bitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onRetry) {
            Text("Scan Another")
        }
    }
}
