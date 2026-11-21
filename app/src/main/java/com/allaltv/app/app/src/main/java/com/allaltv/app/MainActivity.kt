package com.allaltv.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val Navy = Color(0xFF061426)
private val CardColor = Color(0xFF142A40)
private val Green = Color(0xFF00D995)
private val White = Color(0xFFF5F8FC)
private val Muted = Color(0xFF91A4B8)

private const val SPORTS_PLAYLIST =
    "https://iptv-org.github.io/iptv/categories/sports.m3u"
private const val WORLD_PLAYLIST =
    "https://iptv-org.github.io/iptv/index.m3u"

data class TVChannel(
    val name: String,
    val category: String,
    val url: String?,
    val logo: String? = null
)

private fun parseM3U(
    content: String,
    forceSports: Boolean = false
): List<TVChannel> {
    val channels = mutableListOf<TVChannel>()
    var channelName = ""
    var channelLogo: String? = null
    var groupTitle = ""

    val nameRegex = Regex("""tvg-name="([^"]*)"""")
    val logoRegex = Regex("""tvg-logo="([^"]*)"""")
    val groupRegex = Regex("""group-title="([^"]*)"""")

    for (rawLine in content.lineSequence()) {
        val line = rawLine.trim()

        if (line.startsWith("#EXTINF", ignoreCase = true)) {
            val displayName = line.substringAfterLast(",", "").trim()

            channelName = nameRegex.find(line)
                ?.groupValues?.get(1)
                ?.takeIf { it.isNotBlank() }
                ?: displayName

            channelLogo = logoRegex.find(line)
                ?.groupValues?.get(1)
                ?.takeIf {
                    it.startsWith("https://") ||
                    it.startsWith("http://")
                }

            groupTitle = groupRegex.find(line)
                ?.groupValues?.get(1) ?: ""

        } else if (
            line.isNotBlank() &&
            !line.startsWith("#") &&
            channelName.isNotBlank()
        ) {
            val category = when {
                forceSports ||
                groupTitle.contains("sport", ignoreCase = true) ||
                channelName.contains("sport", ignoreCase = true) ->
                    "رياضة"

                groupTitle.contains("news", ignoreCase = true) ->
                    "أخبار"

                groupTitle.contains("movie", ignoreCase = true) ||
                groupTitle.contains("entertain", ignoreCase = true) ->
                    "ترفيه"

                else -> "عالمية"
            }

            channels.add(
                TVChannel(
                    name = channelName,
                    category = category,
                    url = line,
                    logo = channelLogo
                )
            )

            channelName = ""
            channelLogo = null
            groupTitle = ""
        }
    }

    return channels
}

private fun downloadPlaylist(
    playlistUrl: String,
    forceSports: Boolean = false
): List<TVChannel> {
    val connection =
        URL(playlistUrl).openConnection() as HttpURLConnection

    connection.requestMethod = "GET"
    connection.connectTimeout = 20000
    connection.readTimeout = 45000
    connection.instanceFollowRedirects = true
    connection.setRequestProperty(
        "User-Agent",
        "ALLAL-TV Android App"
    )

    return try {
        connection.inputStream.bufferedReader(Charsets.UTF_8).use {
            parseM3U(it.readText(), forceSports)
        }
    } finally {
        connection.disconnect()
    }
}

class MainActivity : ComponentActivity() {

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
    var channels by remember {
        mutableStateOf<List<TVChannel>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }
    var loadMessage by remember {
        mutableStateOf("جاري تحميل القنوات الرياضية...")
    }
    var selectedChannel by remember {
        mutableStateOf<TVChannel?>(null)
    }
    var selectedCategory by remember { mutableStateOf("الكل") }
    var searchText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("الرئيسية") }

    LaunchedEffect(Unit) {
        val loadedChannels = withContext(Dispatchers.IO) {
            val sports = runCatching {
                downloadPlaylist(SPORTS_PLAYLIST, true)
            }.getOrDefault(emptyList())

            val world = runCatching {
                downloadPlaylist(WORLD_PLAYLIST)
            }.getOrDefault(emptyList())

            (sports + world).distinctBy {
                it.url ?: it.name.lowercase()
            }
        }

        channels = loadedChannels.sortedBy {
            if (it.category == "رياضة") 0 else 1
        }
        loading = false
        loadMessage = if (loadedChannels.isEmpty()) {
            "تعذر تحميل القنوات. تحقق من الإنترنت وحاول مجدداً."
        } else {
            "تم تحميل ${loadedChannels.size} قناة"
        }
    }

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
            if (selectedChannel != null) {
                PlayerScreen(
                    channel = selectedChannel!!,
                    onBack = { selectedChannel = null }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF0B263B),
                                    Navy,
                                    Color(0xFF06101D)
                                )
                            )
                        )
                ) {
                    Header(
                        searchText = searchText,
                        onSearchChange = { searchText = it }
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        SportsBanner()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 14.dp,
                                    vertical = 10.dp
                                ),
                            horizontalArrangement =
                                Arrangement.spacedBy(7.dp)
                        ) {
                            listOf(
                                "الكل", "رياضة", "أخبار", "ترفيه", "عالمية"
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
                                            horizontal = 11.dp,
                                            vertical = 9.dp
                                        )
                                ) {
                                    Text(
                                        category,
                                        color = if (active) Navy else White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 17.dp,
                                    vertical = 7.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (selectedCategory == "الكل")
                                    "القنوات الرياضية والمميزة"
                                else "قنوات $selectedCategory",
                                color = White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (loading) "تحميل..."
                                else "${channels.size} قناة",
                                color = Green,
                                fontSize = 11.sp
                            )
                        }

                        val filteredChannels = channels.filter {
                            val categoryMatches =
                                selectedCategory == "الكل" ||
                                it.category == selectedCategory
                            val searchMatches =
                                it.name.contains(
                                    searchText,
                                    ignoreCase = true
                                ) ||
                                it.category.contains(
                                    searchText,
                                    ignoreCase = true
                                )
                            categoryMatches && searchMatches
                        }

                        if (loading || filteredChannels.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment =
                                        Alignment.CenterHorizontally
                                ) {
                                    if (loading) {
                                        CircularProgressIndicator(
                                            color = Green
                                        )
                                    }
                                    Text(
                                        if (loading) loadMessage
                                        else if (channels.isEmpty())
                                            loadMessage
                                        else "لا توجد قنوات مطابقة",
                                        color = Muted,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(
                                    horizontal = 13.dp,
                                    vertical = 8.dp
                                ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(10.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredChannels) { channel ->
                                    ChannelCard(channel) {
                                        selectedChannel = channel
                                    }
                                }
                            }
                        }
                    }

                    BottomNavigationBar(
                        selectedTab = selectedTab,
                        onSelect = { selectedTab = it }
                    )
                }
            }
        }
    }
}

@Composable
fun Header(
    searchText: String,
    onSearchChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF102D43),
                        Color(0xFF071626)
                    )
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "☰",
                color = White,
                fontSize = 25.sp,
                modifier = Modifier.padding(end = 9.dp)
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Green),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "▶",
                    color = Navy,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.width(7.dp))
            Column {
                Row {
                    Text(
                        "ALLAL ",
                        color = White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "TV",
                        color = Green,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    "عالم التلفزيون بين يديك",
                    color = Muted,
                    fontSize = 8.sp
                )
            }
            Spacer(Modifier.weight(1f))
            Text("●", color = Green, fontSize = 13.sp)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF1A3048))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⌕", color = Green, fontSize = 23.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (searchText.isEmpty()) {
                    Text(
                        "ابحث عن قناة أو بلد...",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                BasicTextField(
                    value = searchText,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    cursorBrush = SolidColor(Green),
                    textStyle = TextStyle(
                        color = White,
                        fontSize = 13.sp
                    )
                )
            }
            if (searchText.isNotEmpty()) {
                Text(
                    "×",
                    color = Muted,
                    fontSize = 20.sp,
                    modifier = Modifier.clickable {
                        onSearchChange("")
                    }
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Green,
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
fun SportsBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 13.dp, vertical = 9.dp)
            .height(142.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF075B55),
                        Color(0xFF123A52),
                        Color(0xFF101C35)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(100.dp))
                .background(Green.copy(alpha = 0.10f))
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 18.dp)
        ) {
            Text(
                "ALLAL SPORTS",
                color = Green,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Text(
                "عِش أجواء\nالمباريات مباشرة",
                color = White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 27.sp
            )
            Text(
                "قنوات رياضية من العالم",
                color = Color(0xFFB6C9D8),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(
            "⚽",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp),
            fontSize = 65.sp
        )
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
    var checking by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !checking && activity != null) {
                checking = true
                updateInfo = null
                status = "جاري البحث عن تحديث..."
                activity?.let { currentActivity ->
                    UpdateManager.checkForUpdate(currentActivity) {
                        info, message ->
                        updateInfo = info
                        status = message
                        checking = false
                        showDialog = true
                    }
                }
            }
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (checking) "…" else "⟳",
            color = Green,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text("تحديث", color = Muted, fontSize = 8.sp)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("تحديث ALLAL TV") },
            text = { Text(status) },
            confirmButton = {
                if (updateInfo != null) {
                    TextButton(
                        onClick = {
                            activity?.let { currentActivity ->
                                UpdateManager.downloadAndInstall(
                                    currentActivity,
                                    updateInfo!!.apkUrl
                                ) { message ->
                                    status = message
                                }
                            }
                            showDialog = false
                        }
                    ) {
                        Text("تحميل وتثبيت")
                    }
                } else {
                    TextButton(
                        onClick = { showDialog = false }
                    ) {
                        Text("حسناً")
                    }
                }
            }
        )
    }
}

@Composable
fun ChannelCard(
    channel: TVChannel,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFFF4F7FA))
            .clickable(onClick = onClick)
            .padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (!channel.logo.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(7.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    "▶",
                    color = Green,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Text(
            channel.name,
            color = Color(0xFF152334),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp)
        )
        Text(
            channel.category,
            color = Color(0xFF617386),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BottomNavigationBar(
    selectedTab: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF091827))
            .padding(horizontal = 3.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            "الرئيسية" to "⌂",
            "الأحداث" to "▣",
            "المباريات" to "⚽",
            "المفضلة" to "♡",
            "إعدادات" to "⚙"
        ).forEach { item ->
            val label = item.first
            val symbol = item.second
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(label) }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    symbol,
                    color = if (selectedTab == label) Green else Muted,
                    fontSize = 19.sp
                )
                Text(
                    label,
                    color = if (selectedTab == label) Green else Muted,
                    fontSize = 8.sp
                )
            }
        }
        UpdateSection()
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
            Spacer(Modifier.width(15.dp))
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
            Spacer(Modifier.weight(1f))
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
                    update = { it.player = player }
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
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("▶", color = Green, fontSize = 45.sp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "البث غير متوفر حالياً",
                        color = White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "رابط القناة غير متوفر",
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
            Spacer(Modifier.height(6.dp))
            Text(
                "مشغل ALLAL TV",
                color = Green,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "استمتع بمشاهدة البث المباشر",
                color = Muted,
                fontSize = 12.sp
            )
        }
    }
}
