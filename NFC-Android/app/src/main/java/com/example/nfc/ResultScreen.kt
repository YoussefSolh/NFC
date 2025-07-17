package com.example.nfc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ResultScreen(
    json: String?,
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

        json?.let {
            val parsed = Json.parseToJsonElement(it).jsonObject
            parsed["ascii"]?.jsonPrimitive?.content?.let { txt ->
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

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onRetry) {
            Text("Scan Another")
        }
    }
}
