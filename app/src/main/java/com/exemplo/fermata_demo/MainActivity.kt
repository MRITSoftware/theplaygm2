package com.exemplo.fermata_demo

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

class MainActivity : AppCompatActivity() {

    private var desbloqueioAtivo = BuildConfig.DESBLOQUEADO
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var player: ExoPlayer? = null

    private var listaCompleta = emptyList<M3uEntry>()
    private var categoriaAtiva: String? = null
    private var termoBusca = ""
    private var abaAtiva = R.id.nav_youtube

    private lateinit var pvFullscreen: PlayerView
    private lateinit var overlayFullscreen: FrameLayout
    private var isFullscreen = false
    private var fullscreenOriginPlayer: PlayerView? = null
    private var fullscreenWebCallback: WebChromeClient.CustomViewCallback? = null

    private val videoLibrary = mutableListOf<VideoEntry>()

    private val selecionarVideo = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { adicionarVideoNaBiblioteca(it) } }

    private val pedirPermissao = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) selecionarVideo.launch("video/*")
        else Toast.makeText(this, "Permissão negada para acessar vídeos", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pvFullscreen = findViewById(R.id.player_view_fullscreen)
        overlayFullscreen = findViewById(R.id.fullscreen_overlay)

        if (!desbloqueioAtivo) {
            aplicarModoBloqueado()
            return
        }

        inicializarPlayer()
        configurarWebView()
        configurarM3u()
        configurarNavegacao()
        configurarBotaoVoltar()
        configurarFullscreenPlayers()
        carregarBiblioteca()
        configurarListaOffline()
        mostrarAba(R.id.nav_youtube)
    }

    private fun configurarBotaoVoltar() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isFullscreen -> sairFullscreen()
                    fullscreenWebCallback != null -> fullscreenWebCallback?.onCustomViewHidden()
                    abaAtiva == R.id.nav_tv && categoriaAtiva != null -> mostrarCategorias()
                    else -> {
                        val webview = findViewById<WebView>(R.id.webview)
                        if (abaAtiva == R.id.nav_youtube && webview.canGoBack()) {
                            webview.goBack()
                        } else {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                }
            }
        })
    }

    private fun aplicarModoBloqueado() {
        findViewById<View>(R.id.bottom_nav).visibility = View.GONE
        findViewById<View>(R.id.layout_tv).visibility = View.GONE
        findViewById<View>(R.id.layout_offline).visibility = View.GONE
        findViewById<View>(R.id.layout_youtube_nav).visibility = View.GONE
        val webview = findViewById<WebView>(R.id.webview)
        webview.visibility = View.VISIBLE
        webview.loadDataWithBaseURL(null, htmlBloqueado(), "text/html", "UTF-8", null)
    }

    private fun htmlBloqueado() = """
        <html><body style='background:#1a1a2e;color:#fff;text-align:center;
        padding:60px 30px;font-family:sans-serif;'>
        <h2 style='color:#e94560;font-size:28px'>🔒 BLOQUEADO</h2>
        <p style='font-size:16px'>BuildConfig.DESBLOQUEADO = <b>false</b></p>
        <p style='color:#888;font-size:13px'>Este APK foi compilado no modo seguro.<br>
        Tente descobrir como reverter o bloqueio.</p>
        </body></html>
    """.trimIndent()

    private fun inicializarPlayer() {
        player = ExoPlayer.Builder(this).build().also { exo ->
            AutoMediaService.onPlay  = { exo.play() }
            AutoMediaService.onPause = { exo.pause() }
            AutoMediaService.onStop  = { exo.stop() }

            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@MainActivity, "Erro: ${error.message}", Toast.LENGTH_LONG).show()
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_IDLE || state == Player.STATE_ENDED)
                        AutoMediaService.atualizarParado()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying &&
                        exo.playbackState != Player.STATE_IDLE &&
                        exo.playbackState != Player.STATE_ENDED
                    ) AutoMediaService.atualizarPausado()
                }
            })
        }
    }

    private fun configurarWebView() {
        val webView = findViewById<WebView>(R.id.webview)
        webView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportZoom(true)
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                val permitidos = listOf("https://m.youtube.com", "https://www.youtube.com",
                    "https://youtu.be", "https://accounts.google.com")
                return !permitidos.any { url.startsWith(it) }
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                val bloqueados = listOf("doubleclick.net", "googlesyndication.com",
                    "googletagservices.com", "googleadservices.com",
                    "google-analytics.com", "pagead2.googlesyndication.com")
                if (bloqueados.any { url.contains(it) })
                    return WebResourceResponse("text/plain", "UTF-8", null)
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                atualizarBotoesNavWeb(view)
                findViewById<TextView>(R.id.tv_web_url).text = Uri.parse(url).host ?: url

                // Envia título do vídeo para o painel do carro
                view.evaluateJavascript("document.title") { raw ->
                    val titulo = raw?.trim('"')?.takeIf {
                        it.isNotBlank() && !it.equals("youtube", ignoreCase = true)
                    } ?: return@evaluateJavascript
                    AutoMediaService.atualizarNowPlaying(titulo, "YouTube")
                }

                // Detecta anúncio: acelera a 32x enquanto rola, restaura ao terminar
                view.evaluateJavascript("""
                    (function() {
                        if (window._gm2active) return;
                        window._gm2active = true;
                        var adRolando = false;
                        function tick() {
                            ['.ytp-skip-ad-button','.ytp-ad-skip-button',
                             '.ytp-ad-skip-button-modern','.ytp-ad-skip-button-slot button'
                            ].forEach(function(s){
                                var b=document.querySelector(s); if(b) b.click();
                            });
                            var overlay=document.querySelector('.ytp-ad-overlay-close-button');
                            if(overlay) overlay.click();
                            var video=document.querySelector('video');
                            if(!video) return;
                            var temAd=!!(document.querySelector('.ytp-ad-player-overlay,.ytp-ad-module'));
                            if(temAd && !adRolando){
                                adRolando=true;
                                video.muted=true;
                                video.playbackRate=32;
                            } else if(!temAd && adRolando){
                                adRolando=false;
                                video.playbackRate=1;
                                video.muted=false;
                            }
                        }
                        tick();
                        setInterval(tick,300);
                    })();
                """.trimIndent(), null)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenWebCallback = callback
                overlayFullscreen.addView(view)
                overlayFullscreen.visibility = View.VISIBLE
                hideSystemUi()
            }

            override fun onHideCustomView() {
                overlayFullscreen.removeAllViews()
                overlayFullscreen.visibility = View.GONE
                fullscreenWebCallback = null
                showSystemUi()
            }
        }

        findViewById<ImageButton>(R.id.btn_web_back).setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        findViewById<ImageButton>(R.id.btn_web_forward).setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        findViewById<ImageButton>(R.id.btn_web_refresh).setOnClickListener {
            webView.reload()
        }
    }

    private fun atualizarBotoesNavWeb(view: WebView) {
        findViewById<ImageButton>(R.id.btn_web_back).alpha = if (view.canGoBack()) 1f else 0.3f
        findViewById<ImageButton>(R.id.btn_web_forward).alpha = if (view.canGoForward()) 1f else 0.3f
    }

    // Mapeia qualquer group-title para uma das 3 super-categorias
    private fun superCategoria(group: String): String {
        val g = group.uppercase()
        return when {
            "SERIE" in g -> "SÉRIES"
            "FILM" in g || "MOVIE" in g -> "FILMES"
            else -> "CANAIS"
        }
    }

    private fun configurarM3u() {
        val rvCategories = findViewById<RecyclerView>(R.id.rv_categories)
        val rvPlaylist = findViewById<RecyclerView>(R.id.rv_playlist)
        rvCategories.layoutManager = LinearLayoutManager(this)
        rvCategories.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        rvPlaylist.layoutManager = LinearLayoutManager(this)
        rvPlaylist.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        findViewById<EditText>(R.id.et_search).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { termoBusca = s?.toString() ?: ""; filtrarLista() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        findViewById<ImageButton>(R.id.btn_back_categories).setOnClickListener {
            mostrarCategorias()
        }

        findViewById<Button>(R.id.btn_carregar).setOnClickListener {
            val url = findViewById<EditText>(R.id.et_m3u_url).text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Digite a URL da lista M3U", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            carregarLista(url)
        }

        // Carrega a URL salva automaticamente ao abrir o app
        val urlSalva = getSharedPreferences("gm2_m3u", Context.MODE_PRIVATE)
            .getString("url", "") ?: ""
        if (urlSalva.isNotEmpty()) {
            findViewById<EditText>(R.id.et_m3u_url).setText(urlSalva)
            carregarLista(urlSalva)
        }
    }

    private fun carregarLista(url: String) {
        val btn = findViewById<Button>(R.id.btn_carregar)
        btn.isEnabled = false
        btn.text = "Carregando…"

        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { parsearM3u(url) }
            btn.isEnabled = true
            btn.text = "Carregar"

            if (items.isEmpty()) {
                Toast.makeText(this@MainActivity, "Lista vazia ou URL inválida", Toast.LENGTH_LONG).show()
                return@launch
            }

            // Salva a URL para auto-carregar na próxima sessão
            getSharedPreferences("gm2_m3u", Context.MODE_PRIVATE)
                .edit().putString("url", url).apply()

            listaCompleta = items
            mostrarCategorias()

            val canais = items.count { superCategoria(it.group) == "CANAIS" }
            val filmes = items.count { superCategoria(it.group) == "FILMES" }
            val series = items.count { superCategoria(it.group) == "SÉRIES" }
            Toast.makeText(this@MainActivity,
                "📺 $canais canais  🎬 $filmes filmes  🎞 $series séries",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun parsearM3u(url: String): List<M3uEntry> = try {
        val linhas = URL(url).readText(Charsets.UTF_8).lines()
        val resultado = mutableListOf<M3uEntry>()
        var titulo = ""
        var grupo = "Outros"
        val regexGroup = Regex("""group-title="([^"]*)"""")
        for (linha in linhas) {
            when {
                linha.startsWith("#EXTINF") -> {
                    titulo = linha.substringAfterLast(",").trim()
                    grupo = regexGroup.find(linha)?.groupValues?.get(1)?.trim()
                        ?.ifEmpty { "Outros" } ?: "Outros"
                }
                linha.startsWith("http") -> {
                    resultado.add(M3uEntry(titulo.ifEmpty { "Item ${resultado.size + 1}" }, linha.trim(), grupo))
                    titulo = ""
                    grupo = "Outros"
                }
            }
        }
        resultado
    } catch (e: Exception) { emptyList() }

    private fun mostrarCategorias() {
        categoriaAtiva = null
        termoBusca = ""

        findViewById<View>(R.id.layout_category_header).visibility = View.GONE
        findViewById<View>(R.id.et_search).visibility = View.GONE
        findViewById<View>(R.id.tv_placeholder_tv).visibility = View.GONE
        findViewById<View>(R.id.rv_playlist).visibility = View.GONE
        findViewById<View>(R.id.rv_categories).visibility = View.VISIBLE

        // Ordem fixa: CANAIS → FILMES → SÉRIES
        val ordem = listOf("CANAIS", "FILMES", "SÉRIES")
        val categorias = ordem.mapNotNull { superCat ->
            val count = listaCompleta.count { superCategoria(it.group) == superCat }
            if (count > 0) CategoryItem(superCat, count) else null
        }

        val rvCategories = findViewById<RecyclerView>(R.id.rv_categories)
        rvCategories.adapter = CategoryAdapter(categorias) { cat -> abrirCategoria(cat.name) }
    }

    private fun abrirCategoria(nome: String) {
        categoriaAtiva = nome
        termoBusca = ""

        val count = listaCompleta.count { superCategoria(it.group) == nome }
        findViewById<TextView>(R.id.tv_category_name_header).text = nome
        findViewById<TextView>(R.id.tv_channel_count_header).text = "$count item(s)"

        findViewById<View>(R.id.layout_category_header).visibility = View.VISIBLE
        findViewById<View>(R.id.et_search).visibility = View.VISIBLE
        (findViewById<EditText>(R.id.et_search)).setText("")
        findViewById<View>(R.id.rv_categories).visibility = View.GONE
        findViewById<View>(R.id.rv_playlist).visibility = View.VISIBLE

        filtrarLista()
    }

    private fun filtrarLista() {
        val cat = categoriaAtiva ?: return
        val filtrada = listaCompleta.filter { entry ->
            superCategoria(entry.group) == cat &&
            (termoBusca.isEmpty() || entry.title.contains(termoBusca, ignoreCase = true))
        }
        val recycler = findViewById<RecyclerView>(R.id.rv_playlist)
        recycler.adapter = PlaylistAdapter(filtrada) { reproduzirTV(it) }
    }

    private fun configurarNavegacao() {
        findViewById<BottomNavigationView>(R.id.bottom_nav)
            .setOnItemSelectedListener { item -> mostrarAba(item.itemId); true }
    }

    private fun mostrarAba(itemId: Int) {
        abaAtiva = itemId
        val webview = findViewById<WebView>(R.id.webview)
        val layoutTv = findViewById<View>(R.id.layout_tv)
        val layoutOffline = findViewById<View>(R.id.layout_offline)
        val youtubeNav = findViewById<View>(R.id.layout_youtube_nav)
        val pvTV = findViewById<PlayerView>(R.id.player_view_tv)
        val pvOffline = findViewById<PlayerView>(R.id.player_view_offline)

        webview.visibility = View.GONE
        layoutTv.visibility = View.GONE
        layoutOffline.visibility = View.GONE
        youtubeNav.visibility = View.GONE
        pvTV.player = null
        pvOffline.player = null

        when (itemId) {
            R.id.nav_youtube -> {
                webview.visibility = View.VISIBLE
                youtubeNav.visibility = View.VISIBLE
                if (webview.url.isNullOrEmpty() || webview.url == "about:blank")
                    webview.loadUrl("https://m.youtube.com")
            }
            R.id.nav_tv -> {
                layoutTv.visibility = View.VISIBLE
                pvTV.player = player
            }
            R.id.nav_offline -> {
                layoutOffline.visibility = View.VISIBLE
                pvOffline.player = player
            }
        }
    }

    private fun configurarListaOffline() {
        val recycler = findViewById<RecyclerView>(R.id.rv_video_library)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        atualizarListaVideos()
        findViewById<Button>(R.id.btn_add_video).setOnClickListener { abrirVideoLocal() }
    }

    private fun configurarFullscreenPlayers() {
        val pvTV = findViewById<PlayerView>(R.id.player_view_tv)
        val pvOffline = findViewById<PlayerView>(R.id.player_view_offline)

        pvTV.setFullscreenButtonClickListener { entering ->
            if (entering) entrarFullscreenPlayer(pvTV)
        }
        pvOffline.setFullscreenButtonClickListener { entering ->
            if (entering) entrarFullscreenPlayer(pvOffline)
        }
        pvFullscreen.setFullscreenButtonClickListener { entering ->
            if (!entering) sairFullscreen()
        }
    }

    private fun entrarFullscreenPlayer(origin: PlayerView) {
        isFullscreen = true
        fullscreenOriginPlayer = origin
        origin.player = null
        pvFullscreen.player = player
        overlayFullscreen.visibility = View.VISIBLE
        hideSystemUi()
    }

    private fun sairFullscreen() {
        isFullscreen = false
        pvFullscreen.player = null
        fullscreenOriginPlayer?.player = player
        fullscreenOriginPlayer = null
        overlayFullscreen.visibility = View.GONE
        showSystemUi()
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    private fun showSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(
                android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun carregarBiblioteca() {
        val json = getSharedPreferences("gm2_biblioteca", Context.MODE_PRIVATE)
            .getString("videos", "[]") ?: "[]"
        videoLibrary.clear()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                videoLibrary.add(VideoEntry(obj.getString("title"), obj.getString("uri")))
            }
        } catch (e: Exception) { }
    }

    private fun salvarBiblioteca() {
        val arr = JSONArray()
        videoLibrary.forEach { arr.put(JSONObject().put("title", it.title).put("uri", it.uri)) }
        getSharedPreferences("gm2_biblioteca", Context.MODE_PRIVATE)
            .edit().putString("videos", arr.toString()).apply()
    }

    private fun adicionarVideoNaBiblioteca(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) { }

        val nome = contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "Vídeo"

        val entry = VideoEntry(nome, uri.toString())
        if (videoLibrary.none { it.uri == entry.uri }) {
            videoLibrary.add(0, entry)
            salvarBiblioteca()
            atualizarListaVideos()
        }
        reproduzirLocal(uri)
    }

    private fun removerVideoDaBiblioteca(entry: VideoEntry, position: Int) {
        videoLibrary.removeAt(position)
        salvarBiblioteca()
        atualizarListaVideos()
        Toast.makeText(this, "${entry.title} removido", Toast.LENGTH_SHORT).show()
    }

    private fun atualizarListaVideos() {
        val recycler = findViewById<RecyclerView>(R.id.rv_video_library)
        val placeholder = findViewById<TextView>(R.id.tv_placeholder_offline)
        if (videoLibrary.isEmpty()) {
            placeholder.visibility = View.VISIBLE
            recycler.visibility = View.GONE
        } else {
            placeholder.visibility = View.GONE
            recycler.visibility = View.VISIBLE
            recycler.adapter = VideoLibraryAdapter(videoLibrary,
                onPlay = { reproduzirLocal(Uri.parse(it.uri)) },
                onDelete = { entry, pos -> removerVideoDaBiblioteca(entry, pos) }
            )
        }
    }

    private fun reproduzirTV(entrada: M3uEntry) {
        player?.run { setMediaItem(MediaItem.fromUri(entrada.url)); prepare(); play() }
        AutoMediaService.atualizarNowPlaying(entrada.title, entrada.group.ifEmpty { "GM2 Play" })
        Toast.makeText(this, "▶ ${entrada.title}", Toast.LENGTH_SHORT).show()
    }

    private fun reproduzirLocal(uri: Uri) {
        player?.run { setMediaItem(MediaItem.fromUri(uri)); prepare(); play() }
        val nome = videoLibrary.find { it.uri == uri.toString() }?.title
            ?: uri.lastPathSegment ?: "Vídeo"
        AutoMediaService.atualizarNowPlaying(nome, "Vídeos Offline")
    }

    private fun abrirVideoLocal() {
        val permissao = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(permissao) == PackageManager.PERMISSION_GRANTED)
            selecionarVideo.launch("video/*")
        else
            pedirPermissao.launch(permissao)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true) {
            val params = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(isInPiP: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPiP, newConfig)
        val v = if (isInPiP) View.GONE else View.VISIBLE
        findViewById<View>(R.id.bottom_nav).visibility = v
        findViewById<View>(R.id.layout_m3u).visibility = v
        findViewById<View>(R.id.layout_youtube_nav).visibility = v
        findViewById<View>(R.id.btn_add_video).visibility = v
        if (isInPiP) {
            findViewById<View>(R.id.layout_category_header).visibility = View.GONE
            findViewById<View>(R.id.et_search).visibility = View.GONE
        } else if (categoriaAtiva != null) {
            findViewById<View>(R.id.layout_category_header).visibility = View.VISIBLE
            findViewById<View>(R.id.et_search).visibility = View.VISIBLE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001)
            Toast.makeText(this,
                if (resultCode == Activity.RESULT_OK) "Espelhamento autorizado"
                else "Espelhamento negado",
                Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        AutoMediaService.onPlay  = null
        AutoMediaService.onPause = null
        AutoMediaService.onStop  = null
        player?.release()
    }
}
