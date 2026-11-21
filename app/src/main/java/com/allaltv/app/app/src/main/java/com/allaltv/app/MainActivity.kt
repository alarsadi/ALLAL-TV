package com.allaltv.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
    val url: String,
    val logo: String? = null
)

private fun parseM3U(
    content: String,
    forceSports: Boolean = false
): List<TVChannel> {
    val result = mutableListOf<TVChannel>()
    var name = ""
    var logo: String? = null
    var group = ""

    val nameRegex = Regex("""tvg-name="([^"]*)"""")
    val logoRegex = Regex("""tvg-logo="([^"]*)"""")
    val groupRegex = Regex("""group-title="([^"]*)"""")

    for (raw in content.lineSequence()) {
        val line = raw.trim()

        if (line.startsWith("#EXTINF", true)) {
            val displayName = line.substringAfterLast(",").trim()

            name = nameRegex.find(line)?.groupValues?.get(1)
                ?.takeIf { it.isNotBlank() } ?: displayName

            logo = logoRegex.find(line)?.groupValues?.get(1)
            group = groupRegex.find(line)?.groupValues?.get(1) ?: ""
        } else if (
            line.isNotBlank() &&
            !line.startsWith("#") &&
            name.isNotBlank()
        ) {
            val value = "$name $group".lowercase()

            val category = when {
                forceSports -> "رياضة"

                listOf("sport", "football", "soccer", "bein", "match")
                    .any { value.contains(it) } -> "رياضة"

                listOf(
                    "entertain", "movie", "cinema", "film", "series",
                    "kids", "music", "comedy", "drama", "animation"
                ).any { value.contains(it) } -> "ترفيه"

                else -> null
            }

            if (category != null) {
                result.add(TVChannel(name, category, line, logo))
            }

            name = ""
            logo = null
            group = ""
        }
    }

    return result
}

private fun downloadPlaylist(
    playlistUrl: String,
    forceSports: Boolean = false
): List<TVChannel> {
    val connection = URL(playlistUrl).openConnection() as HttpURLConnection

    connection.connectTimeout = 20000
    connection.readTimeout = 45000
    connection.instanceFollowRedirects = true
    connection.setRequestProperty("User-Agent", "ALLAL-TV Android")

    return try {
        connection.inputStream.bufferedReader(Charsets.UTF_8).use {
            parseM3U(it.readText(), forceSports)
        }
    } finally {
        connection.disconnect()
    }
}

class MainActivity : ComponentActivity() {

    // تم تغيير الاسم لتجنب التعارض مع دالة Activity
    fun applyImmersiveMode(enabled: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)

        val controller = WindowInsetsControllerCompat(
            window,
            window.decorView
        )

        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())

            controller.systemBarsBehavior =
                WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyImmersiveMode(true)

        setContent {
            ALLALTVApp()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            applyImmersiveMode(true)
        }
    }
}

@Composable
fun ALLALTVApp() {
    val context = LocalContext.current

    var channels by remember {
        mutableStateOf<List<TVChannel>>(emptyList())
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var selectedChannelUrl by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var selectedCategory by rememberSaveable {
        mutableStateOf("الكل")
    }

    var selectedTab by rememberSaveable {
        mutableStateOf("الرئيسية")
    }

    var searchText by rememberSaveable {
        mutableStateOf("")
    }

    var favoriteUrls by rememberSaveable {
        mutableStateOf("")
    }

    var drawerOpen by remember {
        mutableStateOf(false)
    }

    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )

    LaunchedEffect(drawerOpen) {
        if (drawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }

    val favorites = favoriteUrls
        .split("\n")
        .filter { it.isNotBlank() }
        .toSet()

    val selectedChannel = channels.firstOrNull {
        it.url == selectedChannelUrl
    }

    val configuration = LocalConfiguration.current

    val columns = if (
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    ) 5 else 3

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val sports = runCatching {
                downloadPlaylist(SPORTS_PLAYLIST, true)
            }.getOrDefault(emptyList())

            val entertainment = runCatching {
                downloadPlaylist(WORLD_PLAYLIST)
            }.getOrDefault(emptyList())

            (sports + entertainment)
                .distinctBy { it.url }
                .sortedBy {
                    if (it.category == "رياضة") 0 else 1
                }
        }

        channels = loaded
        loading = false
    }

    BackHandler(
        enabled = selectedChannelUrl != null ||
                drawerState.isOpen ||
                selectedTab != "الرئيسية"
    ) {
        when {
            selectedChannelUrl != null -> {
                selectedChannelUrl = null
            }

            drawerState.isOpen -> {
                drawerOpen = false
            }

            else -> {
                selectedTab = "الرئيسية"
            }
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
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = true,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = Color(0xFF10263A),
                            modifier = Modifier.width(285.dp)
                        ) {
                            Spacer(Modifier.height(25.dp))

                            Text(
                                "ALLAL TV",
                                color = Green,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(20.dp)
                            )

                            Divider(color = CardColor)

                            listOf(
                                "الرئيسية",
                                "الرياضة",
                                "الترفيه",
                                "المباريات",
                                "المفضلة",
                                "الإعدادات"
                            ).forEach { item ->

                                NavigationDrawerItem(
                                    label = {
                                        Text(
                                            item,
                                            color = White
                                        )
                                    },
                                    selected = selectedTab == item,
                                    onClick = {
                                        selectedTab = when (item) {
                                            "الرياضة" -> "الرئيسية"
                                            "الترفيه" -> "الرئيسية"
                                            else -> item
                                        }

                                        selectedCategory = when (item) {
                                            "الرياضة" -> "رياضة"
                                            "الترفيه" -> "ترفيه"
                                            else -> "الكل"
                                        }

                                        drawerOpen = false
                                    },
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor =
                                            Color(0xFF194C45),
                                        unselectedContainerColor =
                                            Color.Transparent
                                    )
                                )
                            }

                            Spacer(Modifier.weight(1f))

                            Text(
                                "جميع الحقوق محفوظة © ALLAL TV",
                                color = Muted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Navy)
                    ) {

                        // الهيدر
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0B2033))
                                .padding(
                                    horizontal = 15.dp,
                                    vertical = 13.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "☰",
                                color = Green,
                                fontSize = 27.sp,
                                modifier = Modifier.clickable {
                                    drawerOpen = true
                                }
                            )

                            Spacer(Modifier.width(15.dp))

                            Text(
                                "ALLAL ",
                                color = White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black
                            )

                            Text(
                                "TV",
                                color = Green,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black
                            )

                            Spacer(Modifier.weight(1f))

                            Text(
                                "● مباشر",
                                color = Green,
                                fontSize = 12.sp
                            )
                        }

                        // البحث
                        if (selectedTab == "الرئيسية") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(CardColor)
                                    .padding(
                                        horizontal = 12.dp,
                                        vertical = 10.dp
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "⌕",
                                    color = Green,
                                    fontSize = 23.sp
                                )

                                Spacer(Modifier.width(8.dp))

                                Box(Modifier.weight(1f)) {
                                    if (searchText.isEmpty()) {
                                        Text(
                                            "ابحث عن قناة...",
                                            color = Muted,
                                            fontSize = 13.sp
                                        )
                                    }

                                    BasicTextField(
                                        value = searchText,
                                        onValueChange = {
                                            searchText = it
                                        },
                                        singleLine = true,
                                        cursorBrush = SolidColor(Green),
                                        textStyle = TextStyle(
                                            color = White,
                                            fontSize = 14.sp
                                        )
                                    )
                                }

                                if (searchText.isNotEmpty()) {
                                    Text(
                                        "×",
                                        color = White,
                                        modifier = Modifier.clickable {
                                            searchText = ""
                                        }
                                    )
                                }
                            }
                        }

                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            when (selectedTab) {

                                "الرئيسية" -> {
                                    val shown = channels.filter { channel ->
                                        (
                                            selectedCategory == "الكل" ||
                                                    channel.category == selectedCategory
                                        ) && (
                                            channel.name.contains(
                                                searchText,
                                                true
                                            ) ||
                                                    channel.category.contains(
                                                        searchText,
                                                        true
                                                    )
                                        )
                                    }

                                    Column(Modifier.fillMaxSize()) {

                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp),
                                            horizontalArrangement =
                                                Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(
                                                "الكل",
                                                "رياضة",
                                                "ترفيه"
                                            ).forEach { category ->

                                                val active =
                                                    selectedCategory == category

                                                Box(
                                                    Modifier
                                                        .clip(
                                                            RoundedCornerShape(20.dp)
                                                        )
                                                        .background(
                                                            if (active) Green
                                                            else CardColor
                                                        )
                                                        .clickable {
                                                            selectedCategory =
                                                                category
                                                        }
                                                        .padding(
                                                            horizontal = 17.dp,
                                                            vertical = 10.dp
                                                        )
                                                ) {
                                                    Text(
                                                        category,
                                                        color = if (active)
                                                            Navy
                                                        else White,
                                                        fontWeight =
                                                            FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            if (selectedCategory == "الكل")
                                                "القنوات الرياضية والترفيهية"
                                            else
                                                "قنوات $selectedCategory",
                                            color = White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(15.dp)
                                        )

                                        if (loading) {
                                            Box(
                                                Modifier.fillMaxSize(),
                                                contentAlignment =
                                                    Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color = Green
                                                )
                                            }
                                        } else {
                                            ChannelGrid(
                                                channels = shown,
                                                favorites = favorites,
                                                columns = columns,
                                                onFavorite = { url ->
                                                    favoriteUrls =
                                                        (
                                                            if (favorites.contains(url))
                                                                favorites - url
                                                            else
                                                                favorites + url
                                                        ).joinToString("\n")
                                                },
                                                onChannelClick = {
                                                    selectedChannelUrl = it.url
                                                }
                                            )
                                        }
                                    }
                                }

                                "المفضلة" -> {
                                    ChannelGrid(
                                        channels = channels.filter {
                                            favorites.contains(it.url)
                                        },
                                        favorites = favorites,
                                        columns = columns,
                                        onFavorite = { url ->
                                            favoriteUrls =
                                                (favorites - url).joinToString("\n")
                                        },
                                        onChannelClick = {
                                            selectedChannelUrl = it.url
                                        }
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

                                "الإعدادات" -> {
                                    SettingsPage()
                                }

                                else -> {
                                    MatchesPage(
                                        onWatchSports = {
                                            selectedCategory = "رياضة"
                                            selectedTab = "الرئيسية"
                                        }
                                    )
                                }
                            }
                        }

                        // شريط التنقل السفلي
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF091827))
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            listOf(
                                "الرئيسية" to "⌂",
                                "المباريات" to "⚽",
                                "المفضلة" to "♡",
                                "الإعدادات" to "⚙"
                            ).forEach { item ->

                                Column(
                                    horizontalAlignment =
                                        Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            selectedTab = item.first
                                        }
                                        .padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        item.second,
                                        color = if (
                                            selectedTab == item.first
                                        ) Green else Muted,
                                        fontSize = 21.sp
                                    )

                                    Text(
                                        item.first,
                                        color = if (
                                            selectedTab == item.first
                                        ) Green else Muted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelGrid(
    channels: List<TVChannel>,
    favorites: Set<String>,
    columns: Int,
    onFavorite: (String) -> Unit,
    onChannelClick: (TVChannel) -> Unit
) {
    if (channels.isEmpty()) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "لا توجد قنوات متاحة في هذا التصنيف",
                color = Muted
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                channels,
                key = { it.url }
            ) { channel ->

                Column(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardColor)
                        .clickable {
                            onChannelClick(channel)
                        }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(78.dp)
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
                                fontSize = 30.sp
                            )
                        }
                    }

                    Text(
                        channel.name,
                        color = White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 7.dp)
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            channel.category,
                            color = Muted,
                            fontSize = 9.sp
                        )

                        Spacer(Modifier.weight(1f))

                        Text(
                            if (favorites.contains(channel.url))
                                "♥" else "♡",
                            color = Green,
                            fontSize = 19.sp,
                            modifier = Modifier.clickable {
                                onFavorite(channel.url)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchesPage(
    onWatchSports: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(25.dp))

        Text(
            "المباريات",
            color = White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(20.dp))

        Text(
            "قسم المباريات",
            color = Green,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "سيتم عرض المواعيد والنتائج عند ربط مصدر بيانات رياضي مباشر.",
            color = Muted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(25.dp))

        Button(
            onClick = onWatchSports,
            colors = ButtonDefaults.buttonColors(
                containerColor = Green,
                contentColor = Navy
            )
        ) {
            Text("مشاهدة القنوات الرياضية")
        }
    }
}

@Composable
private fun SettingsPage() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "إعدادات ALLAL TV",
            color = White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(25.dp))

        Text(
            "تحديث التطبيق",
            color = Green,
            fontSize = 17.sp
        )

        Spacer(Modifier.height(12.dp))

        UpdateSection()
    }
}

@Composable
fun UpdateSection() {
    val activity = LocalContext.current as? Activity

    var info by remember {
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
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardColor)
            .clickable(
                enabled = !checking && activity != null
            ) {
                checking = true
                status = "جاري البحث عن تحديث..."
                info = null

                activity?.let { current ->
                    UpdateManager.checkForUpdate(current) { update, message ->
                        info = update
                        status = message
                        checking = false
                        showDialog = true
                    }
                }
            }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (checking) "…" else "⟳",
            color = Green,
            fontSize = 27.sp
        )

        Text(
            "التحقق من التحديثات",
            color = White,
            fontSize = 12.sp
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
                if (info != null) {
                    TextButton(
                        onClick = {
                            activity?.let { current ->
                                UpdateManager.downloadAndInstall(
                                    current,
                                    info!!.apkUrl
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
fun PlayerScreen(
    channel: TVChannel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val mainActivity = activity as? MainActivity

    var errorMessage by remember(channel.url) {
        mutableStateOf<String?>(null)
    }

    var retryCount by remember(channel.url) {
        mutableIntStateOf(0)
    }

    // تدوير الهاتف أفقياً وإرجاع الاتجاه السابق عند الخروج
    DisposableEffect(channel.url) {
        val oldOrientation = activity?.requestedOrientation

        mainActivity?.applyImmersiveMode(true)

        activity?.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        onDispose {
            activity?.requestedOrientation =
                oldOrientation
                    ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

            mainActivity?.applyImmersiveMode(true)
        }
    }

    val player = remember(channel.url, retryCount) {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                setMediaItem(
                    MediaItem.fromUri(channel.url)
                )

                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {

            override fun onPlayerError(
                error: PlaybackException
            ) {
                errorMessage =
                    error.message ?: "تعذر تشغيل القناة"
            }

            override fun onPlaybackStateChanged(
                state: Int
            ) {
                if (state == Player.STATE_READY) {
                    errorMessage = null
                }
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    BackHandler {
        onBack()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player

                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 3500

                    resizeMode =
                        AspectRatioFrameLayout.RESIZE_MODE_FIT

                    keepScreenOn = true

                    setShowBuffering(
                        PlayerView.SHOW_BUFFERING_WHEN_PLAYING
                    )

                    setShutterBackgroundColor(
                        android.graphics.Color.BLACK
                    )
                }
            },
            update = { view ->
                view.player = player
                view.useController = true
                view.keepScreenOn = true

                view.resizeMode =
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        )

        if (errorMessage != null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "تعذر تشغيل القناة",
                    color = White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    errorMessage ?: "",
                    color = Muted,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(15.dp))

                Button(
                    onClick = {
                        errorMessage = null
                        retryCount++
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Green,
                        contentColor = Navy
                    )
                ) {
                    Text("إعادة المحاولة")
                }

                Text(
                    "رجوع",
                    color = White,
                    modifier = Modifier
                        .padding(12.dp)
                        .clickable {
                            onBack()
                        }
                )
            }
        }
    }
}
