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
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.*

@Composable
fun MainScreen(
    status: AppStatus,
    json: String?,
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

        json?.let {
            val parsed = Json.parseToJsonElement(it).jsonObject
            parsed["error"]?.jsonPrimitive?.content?.let { error ->
                Text("Error: $error", color = Color.Red)
            }
            parsed["mrzInfo"]?.jsonPrimitive?.content?.let { info ->
                Text("MRZ Info: $info")
            }
            parsed["dg1Hex"]?.jsonPrimitive?.content?.let { hex ->
                Text("DG1 (Hex): $hex")
            }
            parsed["ascii"]?.jsonPrimitive?.content?.let { txt ->
                Text("DG1 (ASCII): $txt")

                val parts = txt.split("<<")
                if (parts.size >= 2) {
                    val nameParts = parts[0].split("<")
                    val surname = nameParts.firstOrNull()?.replace("<", " ")?.trim()
                    val givenNames = nameParts.drop(1).joinToString(" ") { it.replace("<", " ") }.trim()
                    val nationality = parts[1].take(3)
                    val birthDateRaw = parts[1].substring(3, 9)
                    val birthDate = try {
                        SimpleDateFormat("yyMMdd", Locale.US).parse(birthDateRaw)?.let {
                            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it)
                        } ?: birthDateRaw
                    } catch (e: Exception) { birthDateRaw }
                    val gender = parts[1].substring(9, 10)
                    val expiryDateRaw = parts[1].substring(10, 16)
                    val expiryDate = try {
                        SimpleDateFormat("yyMMdd", Locale.US).parse(expiryDateRaw)?.let {
                            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it)
                        } ?: expiryDateRaw
                    } catch (e: Exception) { expiryDateRaw }

                    Text("Surname: $surname")
                    Text("Given Names: $givenNames")
                    Text("Nationality: $nationality")
                    Text("Birth Date: $birthDate")
                    Text("Gender: $gender")
                    Text("Expiry Date: $expiryDate")
                }
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
