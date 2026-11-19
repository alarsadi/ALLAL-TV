
package com.allaltv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AllalTVApp()
        }
    }
}

data class TVChannel(
    val name: String,
    val category: String
)

@Composable
fun AllalTVApp() {
    val channels = listOf(
        TVChannel("قناة تجريبية 1", "عام"),
        TVChannel("قناة تجريبية 2", "رياضة"),
        TVChannel("قناة تجريبية 3", "أخبار"),
        TVChannel("قناة تجريبية 4", "ترفيه")
    )

    var selectedTab by remember { mutableStateOf("الرئيسية") }
    var searchText by remember { mutableStateOf("") }
    var selectedChannel by remember { mutableStateOf<TVChannel?>(null) }

    val backgroundColor = Color(0xFF101820)
    val greenColor = Color(0xFF00C878)

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = backgroundColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "ALLAL TV",
                    color = greenColor,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "عالم التلفزيون بين يديك",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("ابحث عن قناة...", color = Color.Gray)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("الرئيسية", "القنوات", "المباريات").forEach { tab ->
                        TextButton(
                            onClick = { selectedTab = tab }
                        ) {
                            Text(
                                text = tab,
                                color = if (selectedTab == tab)
                                    greenColor else Color.White
                            )
                        }
                    }
                }

                Text(
                    text = when (selectedTab) {
                        "القنوات" -> "جميع القنوات"
                        "المباريات" -> "المباريات والنتائج"
                        else -> "القنوات المقترحة"
                    },
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == "المباريات") {
                    Text(
                        text = "ستظهر المباريات والنتائج هنا.",
                        color = Color.LightGray
                    )
                } else {
                    val filteredChannels = channels.filter {
                        it.name.contains(searchText, ignoreCase = true)
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredChannels) { channel ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedChannel = channel
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF1D2B36)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "▶",
                                        color = greenColor,
                                        fontSize = 25.sp
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column {
                                        Text(
                                            text = channel.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = channel.category,
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "ALLAL TV © 2026",
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    selectedChannel?.let { channel ->
        AlertDialog(
            onDismissRequest = { selectedChannel = null },
            containerColor = Color(0xFF1D2B36),
            title = {
                Text(channel.name, color = greenColor)
            },
            text = {
                Text(
                    "تم اختيار القناة بنجاح. رابط البث غير متوفر حالياً، وسيتم إضافته لاحقاً.",
                    color = Color.White
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { selectedChannel = null }
                ) {
                    Text("حسناً", color = greenColor)
                }
            }
        )
    }
}
