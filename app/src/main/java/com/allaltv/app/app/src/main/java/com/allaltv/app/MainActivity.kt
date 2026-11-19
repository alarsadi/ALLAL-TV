
package com.allaltv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AllalTVApp() }
    }
}

data class TVChannel(
    val name: String,
    val category: String,
    val streamUrl: String? = null
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

    val green = Color(0xFF00C878)

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF101820)
        ) {
            if (selectedChannel != null) {
                ChannelPlayerScreen(
                    channel = selectedChannel!!,
                    onBack = { selectedChannel = null }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        "ALLAL TV",
                        color = green,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "عالم التلفزيون بين يديك",
                        color = Color.LightGray
                    )

                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("ابحث عن قناة...")
                        },
                        singleLine = true
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("الرئيسية", "القنوات", "المباريات").forEach {
                            TextButton(
                                onClick = { selectedTab = it }
                            ) {
                                Text(
                                    it,
                                    color = if (selectedTab == it)
                                        green else Color.White
                                )
                            }
                        }
                    }

                    Text(
                        when (selectedTab) {
                            "القنوات" -> "جميع القنوات"
                            "المباريات" -> "المباريات والنتائج"
                            else -> "القنوات المقترحة"
                        },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(12.dp))

                    if (selectedTab == "المباريات") {
                        Text(
                            "ستظهر المباريات والنتائج هنا.",
                            color = Color.LightGray
                        )
                    } else {
                        val filtered = channels.filter {
                            it.name.contains(searchText, true)
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filtered) { channel ->
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
                                            "▶",
                                            color = green,
                                            fontSize = 25.sp
                                        )

                                        Spacer(Modifier.width(16.dp))

                                        Column {
                                            Text(
                                                channel.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                channel.category,
                                                color = Color.LightGray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        "ALLAL TV © 2026",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelPlayerScreen(
    channel: TVChannel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val player = remember(channel.streamUrl) {
        channel.streamUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            player?.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101820))
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← رجوع إلى القنوات", color = Color(0xFF00C878))
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black, RoundedCornerShape(12.dp))
        ) {
            if (player != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                        }
                    },
                    update = {
                        it.player = player
                    }
                )
            }

            Text(
                "ALLAL TV",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(
                        Color.Black.copy(alpha = 0.45f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )

            if (player == null) {
                Text(
                    "البث غير متوفر حالياً\nسيتم إضافة رابط القناة لاحقاً",
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            channel.name,
            color = Color(0xFF00C878),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "مشغل ALLAL TV",
            color = Color.LightGray,
            fontSize = 14.sp
        )
    }
}
