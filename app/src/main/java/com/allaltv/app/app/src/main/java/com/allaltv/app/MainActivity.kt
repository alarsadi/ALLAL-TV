
package com.allaltv.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private val Navy = Color(0xFF081321)
private val CardColor = Color(0xFF122337)
private val Green = Color(0xFF00C985)
private val White = Color(0xFFF5F8FC)
private val Muted = Color(0xFF91A4B8)

data class TVChannel(
    val name: String,
    val category: String,
    val url: String?
)

class MainActivity : ComponentActivity() {

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()

        setContent {
            ALLALTVApp()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
}

@Composable
fun ALLALTVApp() {

    val channels = remember {
        listOf(
            TVChannel(
                "قناة تجريبية 1",
                "عام",
                "https://cdn.freevisiontv.co.za/sttv/smil:1kzn.stream.smil/playlist.m3u8"
            ),
            TVChannel("قناة رياضية 1", "رياضة", null),
            TVChannel("قناة رياضية 2", "رياضة", null),
            TVChannel("قناة إخبارية", "أخبار", null),
            TVChannel("قناة ترفيهية", "ترفيه", null),
            TVChannel("قناة عامة", "عام", null)
        )
    }

    var selectedChannel by remember { mutableStateOf<TVChannel?>(null) }
    var selectedCategory by remember { mutableStateOf("الكل") }
    var searchText by remember { mutableStateOf("") }

    BackHandler(enabled = selectedChannel != null) {
        selectedChannel = null
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Navy
        ) {
            if (selectedChannel == null) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Navy)
                ) {

                    Header()

                    UpdateSection()

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                    ) {

                        Text(
                            "شاهد قنواتك المفضلة",
                            color = White,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "كل البث المباشر في مكان واحد",
                            color = Muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 5.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        BasicTextField(
                            value = searchText,
                            onValueChange = { newValue ->
                                searchText = newValue
                            },
                            singleLine = true,
                            cursorBrush = SolidColor(Green),
                            textStyle = TextStyle(
                                color = White,
                                fontSize = 15.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardColor)
                                .padding(16.dp),
                            decorationBox = { innerTextField ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⌕", color = Green, fontSize = 22.sp)
                                    Spacer(modifier = Modifier.width(10.dp))

                                    Box {
                                        if (searchText.isEmpty()) {
                                            Text(
                                                "ابحث عن قناة...",
                                                color = Muted
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            "التصنيفات",
                            color = White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "الكل",
                                "رياضة",
                                "أخبار",
                                "ترفيه",
                                "عام"
                            ).forEach { category ->

                                val active = selectedCategory == category

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (active) Green else CardColor
                                        )
                                        .clickable {
                                            selectedCategory = category
                                        }
                                        .padding(
                                            horizontal = 13.dp,
                                            vertical = 9.dp
                                        )
                                ) {
                                    Text(
                                        category,
                                        color = if (active) Navy else White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "القنوات المباشرة",
                                color = White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Text(
                                "● LIVE",
                                color = Green,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val filteredChannels = channels.filter {
                            (selectedCategory == "الكل" ||
                                it.category == selectedCategory) &&
                                it.name.contains(searchText, ignoreCase = true)
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(filteredChannels) { channel ->
                                ChannelCard(channel = channel) {
                                    selectedChannel = channel
                                }
                            }
                        }
                    }
                }

            } else {
                PlayerScreen(
                    channel = selectedChannel!!,
                    onBack = { selectedChannel = null }
                )
            }
        }
    }
}

@Composable
fun UpdateSection() {

    val context = LocalContext.current
    val activity = context as? Activity

    var updateInfo by remember {
        mutableStateOf<UpdateManager.UpdateInfo?>(null)
    }

    var status by remember {
        mutableStateOf("تحقق من وجود إصدار جديد")
    }

    var checking by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(CardColor)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "تحديثات ALLAL TV",
            color = White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            status,
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 5.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (activity != null) {
                    checking = true
                    updateInfo = null
                    status = "جاري البحث عن تحديث..."

                    UpdateManager.checkForUpdate(activity) { info, message ->
                        updateInfo = info
                        status = message
                        checking = false
                    }
                }
            },
            enabled = !checking && activity != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = Green,
                contentColor = Navy
            )
        ) {
            Text(
                if (checking) "جاري البحث..." else "التحقق من التحديثات"
            )
        }

        if (updateInfo != null) {
            Button(
                onClick = {
                    activity?.let { currentActivity ->
                        UpdateManager.downloadAndInstall(
                            currentActivity,
                            updateInfo!!.apkUrl
                        ) { message ->
                            status = message
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2476D2),
                    contentColor = White
                )
            ) {
                Text("تحميل وتثبيت ${updateInfo!!.versionName}")
            }
        }
    }
}

@Composable
fun Header() {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF102A3E),
                        Color(0xFF0A1727)
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 19.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(43.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Green),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "A",
                color = Navy,
                fontSize = 25.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                "ALLAL TV",
                color = White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                "LIVE TELEVISION",
                color = Green,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            "● مباشر",
            color = Green,
            fontSize = 12.sp
        )
    }
}

@Composable
fun ChannelCard(
    channel: TVChannel,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(CardColor)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF1B344A)),
            contentAlignment = Alignment.Center
        ) {
            Text("▶", color = Green, fontSize = 23.sp)
        }

        Spacer(modifier = Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.name,
                color = White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                channel.category,
                color = Muted,
                fontSize = 12.sp
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("● LIVE", color = Green, fontSize = 10.sp)

            Spacer(modifier = Modifier.height(7.dp))

            Text("▶", color = White, fontSize = 17.sp)
        }
    }
}

@Composable
fun PlayerScreen(
    channel: TVChannel,
    onBack: () -> Unit
) {

    val context = LocalContext.current

    val player = remember(channel.url) {
        channel.url?.let { url ->
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
            .background(Navy)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Navy)
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                "‹",
                color = White,
                fontSize = 34.sp,
                modifier = Modifier.clickable { onBack() }
            )

            Spacer(modifier = Modifier.width(15.dp))

            Column {
                Text(
                    channel.name,
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "بث مباشر",
                    color = Green,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                "ALLAL TV",
                color = Green,
                fontWeight = FontWeight.Black
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {

            if (player != null) {

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                            resizeMode =
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowBuffering(
                                PlayerView.SHOW_BUFFERING_WHEN_PLAYING
                            )
                        }
                    },
                    update = {
                        it.player = player
                    }
                )

                Text(
                    "ALLAL TV",
                    color = White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(
                            horizontal = 12.dp,
                            vertical = 7.dp
                        )
                )

            } else {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "▶",
                        color = Green,
                        fontSize = 45.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        "البث غير متوفر حالياً",
                        color = White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "سيتم إضافة رابط القناة لاحقاً",
                        color = Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {

            Text(
                channel.name,
                color = White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "مشغل ALLAL TV",
                color = Green,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "استمتع بمشاهدة البث المباشر",
                color = Muted,
                fontSize = 12.sp
            )
        }
    }
}
