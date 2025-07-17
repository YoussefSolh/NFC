package com.example.nfc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nfc.AppStatus
import com.example.nfc.R
import com.example.nfc.model.NFCReadResult

@Composable
fun MainScreen(
    status: AppStatus,
    result: NFCReadResult?,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    docNumber: String,
    onDocNumberChange: (String) -> Unit,
    dateOfBirth: String,
    onDobChange: (String) -> Unit,
    dateOfExpiry: String,
    onExpiryChange: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(100.dp)
        )
        Text(text = "Iraqi ID Reader", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        StatusBadge(status = status)

        OutlinedTextField(
            value = docNumber,
            onValueChange = onDocNumberChange,
            label = { Text("Document Number") }
        )
        OutlinedTextField(
            value = dateOfBirth,
            onValueChange = onDobChange,
            label = { Text("Date of Birth (YYMMDD)") }
        )
        OutlinedTextField(
            value = dateOfExpiry,
            onValueChange = onExpiryChange,
            label = { Text("Date of Expiry (YYMMDD)") }
        )

        result?.let { res ->
            res.errorMessage?.let { Text("Error: $it", color = Color.Red) }
            res.dg1Info?.let { Text("DG1: $it") }
            res.idDocumentData?.let { doc ->
                doc.lastname?.let { Text("Surname: $it") }
                doc.firstName?.let { Text("Given Names: $it") }
                doc.nationality?.let { Text("Nationality: $it") }
                doc.dateOfBirth?.let { Text("Birth Date: $it") }
                doc.sex?.let { Text("Gender: $it") }
                doc.dateOfExpiry?.let { Text("Expiry Date: $it") }
            }
        }

        if (status == AppStatus.WAITING || status == AppStatus.SUCCESS) {
            Button(onClick = onRetry) {
                Text("Retry")
            }
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
