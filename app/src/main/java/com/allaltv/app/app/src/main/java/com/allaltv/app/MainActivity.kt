@Composable
fun PlayerScreen(
    channel: TVChannel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var playbackState by remember(channel.url) {
        mutableIntStateOf(Player.STATE_IDLE)
    }

    var playbackError by remember(channel.url) {
        mutableStateOf<String?>(null)
    }

    var retryCount by remember(channel.url) {
        mutableIntStateOf(0)
    }

    val player = remember(channel.url) {
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
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {

                override fun onPlaybackStateChanged(state: Int) {
                    playbackState = state

                    if (state == Player.STATE_READY) {
                        playbackError = null
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    playbackError =
                        error.localizedMessage
                            ?: "تعذر تشغيل هذا البث"

                    playbackState = Player.STATE_IDLE
                }
            }

            player.addListener(listener)

            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }
    }

    LaunchedEffect(retryCount, player) {
        val url = channel.url

        if (retryCount > 0 && player != null && url != null) {
            playbackError = null
            playbackState = Player.STATE_BUFFERING

            runCatching {
                player.stop()
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.playWhenReady = true
            }.onFailure {
                playbackError = "تعذر إعادة تشغيل القناة"
            }
        }
    }

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Navy)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF091827))
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                "‹",
                color = White,
                fontSize = 35.sp,
                modifier = Modifier.clickable {
                    onBack()
                }
            )

            Spacer(Modifier.width(15.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    channel.name,
                    color = White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    "بث مباشر",
                    color = Green,
                    fontSize = 11.sp
                )
            }

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

                            controllerShowTimeoutMs = 5000

                            setShowBuffering(
                                PlayerView.SHOW_BUFFERING_WHEN_PLAYING
                            )

                            keepScreenOn = true
                        }
                    },
                    update = {
                        it.player = player
                    }
                )

                if (playbackState == Player.STATE_BUFFERING &&
                    playbackError == null
                ) {
                    CircularProgressIndicator(
                        color = Green,
                        modifier = Modifier.size(42.dp)
                    )
                }

                if (playbackError != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "تعذر تشغيل القناة",
                            color = White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            playbackError ?: "",
                            color = Muted,
                            fontSize = 11.sp,
                            maxLines = 3
                        )

                        Spacer(Modifier.height(15.dp))

                        Button(
                            onClick = {
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )

            } else {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "▶",
                        color = Green,
                        fontSize = 42.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "رابط القناة غير متوفر",
                        color = White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
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

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (playbackState == Player.STATE_READY)
                                Green
                            else
                                Muted
                        )
                )

                Spacer(Modifier.width(7.dp))

                Text(
                    when {
                        playbackError != null -> "حدث خطأ في البث"
                        playbackState == Player.STATE_READY -> "القناة جاهزة للمشاهدة"
                        playbackState == Player.STATE_BUFFERING -> "جاري تحميل البث..."
                        else -> "في انتظار البث"
                    },
                    color = Muted,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "مشغل ALLAL TV",
                color = Green,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(7.dp))

            Text(
                "استمتع بمشاهدة البث المباشر",
                color = Muted,
                fontSize = 12.sp
            )
        }
    }
}
