package com.allaltv.app

import android.app.Activity
import android.content.res.Configuration
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
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

            val displayName =
                line.substringAfterLast(",", "").trim()

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

        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

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

    var loading by remember {
        mutableStateOf(true)
    }

    var loadMessage by remember {
        mutableStateOf("جاري تحميل القنوات الرياضية...")
    }

    var selectedChannelUrl by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    val selectedChannel = channels.firstOrNull {
        it.url == selectedChannelUrl
    }

    var selectedCategory by rememberSaveable {
        mutableStateOf("الكل")
    }

    var searchText by rememberSaveable {
        mutableStateOf("")
    }

    var selectedTab by rememberSaveable {
        mutableStateOf("الرئيسية")
    }

    var favoriteUrls by rememberSaveable {
        mutableStateOf("")
    }

    val favorites = favoriteUrls
        .split("\n")
        .filter { it.isNotBlank() }
        .toSet()

    val configuration = LocalConfiguration.current

    val gridColumns =
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            5
        } else {
            3
        }

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

    BackHandler(
        enabled = selectedChannelUrl != null ||
            selectedTab != "الرئيسية"
    ) {
        if (selectedChannelUrl != null) {
            selectedChannelUrl = null
        } else {
            selectedTab = "الرئيسية"
        }
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
                    channel = selectedChannel,
                    onBack = {
                        selectedChannelUrl = null
                    }
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
                        onSearchChange = {
                            searchText = it
                        }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {

                        when (selectedTab) {

                            "الرئيسية" -> {

                                Column(
                                    modifier = Modifier.fillMaxSize()
                                ) {

                                    CategoryBar(
                                        selectedCategory = selectedCategory,
                                        onSelect = {
                                            selectedCategory = it
                                        }
                                    )

                                    val filteredChannels =
                                        channels.filter {

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

                                    ChannelGrid(
                                        channels = filteredChannels,
                                        loading = loading,
                                        loadMessage = loadMessage,
                                        title = "القنوات الرياضية والمميزة",
                                        favorites = favorites,
                                        columns = gridColumns,
                                        onFavorite = { url ->

                                            val updated =
                                                if (favorites.contains(url)) {
                                                    favorites - url
                                                } else {
                                                    favorites + url
                                                }

                                            favoriteUrls =
                                                updated.joinToString("\n")
                                        },
                                        onChannelClick = {
                                            selectedChannelUrl = it.url
                                        }
                                    )
                                }
                            }

                            "المفضلة" -> {

                                val favoriteChannels =
                                    channels.filter {
                                        it.url != null &&
                                        favorites.contains(it.url)
                                    }

                                ChannelGrid(
                                    channels = favoriteChannels,
                                    loading = loading,
                                    loadMessage = loadMessage,
                                    title = "قنواتك المفضلة",
                                    favorites = favorites,
                                    columns = gridColumns,
                                    onFavorite = { url ->
                                        favoriteUrls =
                                            (favorites - url).joinToString("\n")
                                    },
                                    onChannelClick = {
                                        selectedChannelUrl = it.url
                                    }
                                )
                            }

                            "الأحداث" -> {
                                DestinationPage(
                                    tab = selectedTab,
                                    channelCount = channels.size
                                )
                            }

                            "المباريات" -> {
                                MatchesPage(
                                    onWatchSports = {
                                        selectedCategory = "رياضة"
                                        selectedTab = "الرئيسية"
                                    }
                                )
                            }

                            "إعدادات" -> {
                                DestinationPage(
                                    tab = selectedTab,
                                    channelCount = channels.size
                                )
                            }
                        }
                    }

                    BottomNavigationBar(
                        selectedTab = selectedTab,
                        onSelect = {
                            selectedTab = it
                        }
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Green),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "A",
                    color = Navy,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.width(10.dp))

            Column {
                Row {
                    Text(
                        "ALLAL ",
                        color = White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )

                    Text(
                        "TV",
                        color = Green,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Text(
                    "LIVE TELEVISION",
                    color = Green,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                "عالمي",
                color = White,
                fontSize = 11.sp
            )

            Spacer(Modifier.width(6.dp))

            Text(
                "●",
                color = Green,
                fontSize = 12.sp
            )

            Spacer(Modifier.width(5.dp))

            Text(
                "مباشر",
                color = White,
                fontSize = 11.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 13.dp,
                    end = 13.dp,
                    bottom = 11.dp
                )
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF1A3048))
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                "⌕",
                color = Green,
                fontSize = 24.sp
            )

            Spacer(Modifier.width(9.dp))

            Box(
                modifier = Modifier.weight(1f)
            ) {

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
                    fontSize = 21.sp,
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
fun CategoryBar(
    selectedCategory: String,
    onSelect: (String) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {

        listOf(
            "رياضة",
            "أخبار",
            "ترفيه",
            "عالمية"
        ).forEach { category ->

            val active = selectedCategory == category

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (active) Green else CardColor)
                    .clickable {
                        onSelect(if (active) "الكل" else category)
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {

                Text(
                    category,
                    color = if (active) Navy else White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (selectedCategory == "الكل") Green else CardColor
                )
                .clickable {
                    onSelect("الكل")
                }
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {

            Text(
                "الكل",
                color = if (selectedCategory == "الكل") Navy else White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ChannelGrid(
    channels: List<TVChannel>,
    loading: Boolean,
    loadMessage: String,
    title: String,
    favorites: Set<String>,
    columns: Int,
    onFavorite: (String) -> Unit,
    onChannelClick: (TVChannel) -> Unit
) {

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                title,
                color = White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.weight(1f))

            Text(
                if (loading) "تحميل..." else "${channels.size} قناة",
                color = Green,
                fontSize = 11.sp
            )
        }

        if (loading || channels.isEmpty()) {

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    if (loading) {
                        CircularProgressIndicator(color = Green)
                    }

                    Text(
                        if (loading) loadMessage
                        else "لا توجد قنوات هنا حالياً",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

        } else {

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = 13.dp,
                    vertical = 8.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                items(
                    items = channels,
                    key = { it.url ?: it.name }
                ) { channel ->

                    ChannelCard(
                        channel = channel,
                        isFavorite = channel.url != null &&
                            favorites.contains(channel.url),
                        onFavorite = {
                            channel.url?.let(onFavorite)
                        },
                        onClick = {
                            onChannelClick(channel)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelCard(
    channel: TVChannel,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFF142A40))
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
            color = White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                channel.category,
                color = Muted,
                fontSize = 9.sp,
                modifier = Modifier.weight(1f)
            )

            Text(
                if (isFavorite) "♥" else "♡",
                color = if (isFavorite) Green else Muted,
                fontSize = 19.sp,
                modifier = Modifier.clickable {
                    onFavorite()
                }
            )
        }
    }
}

@Composable
fun DestinationPage(
    tab: String,
    channelCount: Int
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(Modifier.height(25.dp))

        Text(
            when (tab) {
                "الأحداث" -> "الأحداث الرياضية"
                "المباريات" -> "المباريات"
                else -> "إعدادات ALLAL TV"
            },
            color = White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(20.dp))

        when (tab) {

            "الأحداث" -> {

                Text(
                    "الأحداث الرياضية",
                    color = Green,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "لا توجد أحداث متاحة حالياً.",
                    color = Muted,
                    fontSize = 13.sp
                )
            }

            "المباريات" -> {

                Text(
                    "المباريات والنتائج",
                    color = Green,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "سيتم عرض المباريات عند ربط مصدر بيانات النتائج.",
                    color = Muted,
                    fontSize = 13.sp
                )
            }

            "إعدادات" -> {

                Text(
                    "عدد القنوات المحمّلة: $channelCount",
                    color = Muted,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(25.dp))

                Text(
                    "تحديث التطبيق",
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                UpdateSection()
            }
        }
    }
}

@Composable
fun MatchesPage(
    onWatchSports: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(Modifier.height(25.dp))

        Text(
            "المباريات",
            color = White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(25.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardColor)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    "⚽",
                    fontSize = 42.sp
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "جدول المباريات والنتائج",
                    color = Green,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "سيتم عرض المباريات ومواعيدها ونتائجها عند ربط مصدر بيانات رياضي مباشر.",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 21.sp
                )
            }
        }

        Spacer(Modifier.height(25.dp))

        Button(
            onClick = onWatchSports,
            colors = ButtonDefaults.buttonColors(
                containerColor = Green,
                contentColor = Navy
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                "مشاهدة القنوات الرياضية",
                fontWeight = FontWeight.Bold
            )
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

    var showDialog by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = !checking && activity != null
            ) {

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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            if (checking) "…" else "⟳",
            color = Green,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "التحقق من التحديثات",
            color = Muted,
            fontSize = 11.sp
        )
    }

    if (showDialog) {

        AlertDialog(
            onDismissRequest = {
                showDialog = false
            },
            title = {
                Text("تحديث ALLAL TV")
            },
            text = {
                Text(status)
            },
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
                        onClick = {
                            showDialog = false
                        }
                    ) {
                        Text("حسناً")
                    }
                }
            }
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
            .padding(horizontal = 3.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {

        listOf(
            "الرئيسية" to "⌂",
            "الأحداث" to "▦",
            "المباريات" to "⚽",
            "المفضلة" to "♡",
            "إعدادات" to "⚙"
        ).forEach { item ->

            val label = item.first
            val symbol = item.second
            val active = selectedTab == label

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onSelect(label)
                    }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    symbol,
                    color = if (active) Green else Muted,
                    fontSize = 21.sp
                )

                Text(
                    label,
                    color = if (active) Green else Muted,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
fun PlayerScreen(
    channel: TVChannel,
    onBack: () -> Unit
) {

    val context = LocalContext.current

    var retryCount by remember(channel.url) {
        mutableIntStateOf(0)
    }

    var isBuffering by remember(channel.url, retryCount) {
        mutableStateOf(true)
    }

    var playbackError by remember(channel.url, retryCount) {
        mutableStateOf<String?>(null)
    }

    var isPlaying by remember(channel.url, retryCount) {
        mutableStateOf(false)
    }

    val player = remember(channel.url, retryCount) {

        channel.url?.let { url ->

            runCatching {
                ExoPlayer.Builder(context)
                    .build()
                    .apply {
                        setMediaItem(MediaItem.fromUri(url))
                        prepare()
                        playWhenReady = true
                    }
            }.getOrNull()
        }
    }

    DisposableEffect(player) {

        val listener = object : Player.Listener {

            override fun onPlaybackStateChanged(
                playbackState: Int
            ) {

                when (playbackState) {

                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                        playbackError = null
                    }

                    Player.STATE_READY -> {
                        isBuffering = false
                        playbackError = null
                    }

                    Player.STATE_ENDED -> {
                        isBuffering = false
                    }

                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onIsPlayingChanged(
                playing: Boolean
            ) {
                isPlaying = playing
            }

            override fun onPlayerError(
                error: PlaybackException
            ) {

                isBuffering = false
                isPlaying = false

                playbackError =
                    error.message
                        ?: "تعذر تشغيل القناة. تحقق من رابط البث."
            }
        }

        player?.addListener(listener)

        if (player == null) {
            isBuffering = false
            playbackError = "تعذر إنشاء مشغل الفيديو."
        }

        onDispose {
            player?.removeListener(listener)
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
                modifier = Modifier.clickable {
                    onBack()
                }
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

                            keepScreenOn = true

                            setShowBuffering(
                                PlayerView.SHOW_BUFFERING_NEVER
                            )
                        }
                    },

                    update = {
                        it.player = player
                        it.keepScreenOn = true
                    }
                )

                if (isBuffering && playbackError == null) {

                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Color.Black.copy(alpha = 0.78f)
                            )
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        CircularProgressIndicator(
                            color = Green
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            "جاري تحميل البث...",
                            color = White,
                            fontSize = 12.sp
                        )
                    }
                }

                if (playbackError != null) {

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color.Black.copy(alpha = 0.90f)
                            )
                            .padding(18.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Text(
                            "تعذر تشغيل القناة",
                            color = White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            playbackError ?: "",
                            color = Muted,
                            fontSize = 12.sp
                        )

                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = {
                                playbackError = null
                                isBuffering = true
                                retryCount++
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Green,
                                contentColor = Navy
                            )
                        ) {

                            Text(
                                "إعادة المحاولة",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    "ALLAL TV",
                    color = White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            Color.Black.copy(alpha = 0.85f)
                        )
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

                    Spacer(Modifier.height(14.dp))

                    Text(
                        "البث غير متوفر حالياً",
                        color = White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        playbackError ?: "رابط القناة غير متوفر",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 7.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            retryCount++
                            playbackError = null
                            isBuffering = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Green,
                            contentColor = Navy
                        )
                    ) {
                        Text("إعادة المحاولة")
                    }
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
                when {
                    playbackError != null ->
                        "حدث خطأ أثناء تشغيل البث."

                    isBuffering ->
                        "يرجى الانتظار، جاري الاتصال بالقناة."

                    isPlaying ->
                        "القناة تعمل الآن."

                    else ->
                        "استمتع بمشاهدة البث المباشر."
                },
                color = Muted,
                fontSize = 12.sp
            )
        }
    }
}
