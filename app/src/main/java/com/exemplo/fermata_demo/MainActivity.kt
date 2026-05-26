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
import android.os.Rational
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MainActivity : AppCompatActivity() {

    private var desbloqueioAtivo = BuildConfig.DESBLOQUEADO
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var player: ExoPlayer? = null

    private var listaCompleta = emptyList<M3uEntry>()
    private var categoriaAtiva = "Todos"
    private var termoBusca = ""
    private var listaCarregada = false

    private val selecionarVideo = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { reproduzirLocal(it) } }

    private val pedirPermissao = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) selecionarVideo.launch("video/*")
        else Toast.makeText(this, "Permissão negada para acessar vídeos", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        if (!desbloqueioAtivo) {
            aplicarModoBloqueado()
            return
        }

        inicializarPlayer()
        configurarWebView()
        configurarM3u()
        configurarNavegacao()
        configurarBotaoVoltar()
        mostrarAba(R.id.nav_youtube)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        for (i in 0 until menu.size()) menu.getItem(i).isVisible = desbloqueioAtivo
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_status -> {
            Toast.makeText(this, "DESBLOQUEADO | BuildConfig.DESBLOQUEADO = true", Toast.LENGTH_LONG).show()
            true
        }
        R.id.action_reverter -> { reverter(); true }
        R.id.action_mirror -> { ativarMirroring(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun configurarBotaoVoltar() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val webview = findViewById<WebView>(R.id.webview)
                if (webview.visibility == View.VISIBLE && webview.canGoBack()) {
                    webview.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun aplicarModoBloqueado() {
        supportActionBar?.subtitle = "BLOQUEADO"
        invalidateOptionsMenu()
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
        player = ExoPlayer.Builder(this).build().also {
            it.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@MainActivity, "Erro: ${error.message}", Toast.LENGTH_LONG).show()
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
                view.evaluateJavascript("""
                    (function() {
                        setInterval(function() {
                            var s = document.querySelector(
                                '.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern'
                            );
                            if (s) s.click();
                            var c = document.querySelector('.ytp-ad-overlay-close-button');
                            if (c) c.click();
                        }, 300);
                    })();
                """.trimIndent(), null)
            }
        }
        webView.webChromeClient = WebChromeClient()

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

    private fun configurarM3u() {
        val recycler = findViewById<RecyclerView>(R.id.rv_playlist)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        findViewById<EditText>(R.id.et_search).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { termoBusca = s?.toString() ?: ""; filtrarLista() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        findViewById<Button>(R.id.btn_carregar).setOnClickListener {
            val url = findViewById<EditText>(R.id.et_m3u_url).text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Digite a URL da lista M3U", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            carregarLista(url, recycler)
        }
    }

    private fun carregarLista(url: String, recycler: RecyclerView) {
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

            listaCompleta = items
            listaCarregada = true

            val grupos = items.map { it.group }.distinct().sorted()
            criarChipsCategorias(grupos)

            findViewById<View>(R.id.layout_search_chips).visibility = View.VISIBLE
            findViewById<View>(R.id.tv_placeholder_tv).visibility = View.GONE
            recycler.visibility = View.VISIBLE

            categoriaAtiva = "Todos"
            termoBusca = ""
            filtrarLista()
            Toast.makeText(this@MainActivity, "${items.size} item(s) em ${grupos.size} categoria(s)", Toast.LENGTH_SHORT).show()
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

    private fun criarChipsCategorias(grupos: List<String>) {
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_categories)
        chipGroup.removeAllViews()

        (listOf("Todos") + grupos).forEach { grupo ->
            val chip = Chip(this).apply {
                text = grupo
                isCheckable = true
                isChecked = grupo == "Todos"
                setTextColor(getColor(android.R.color.white))
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    if (grupo == "Todos") 0xFF1a1a2e.toInt() else 0xFF16213e.toInt()
                )
                chipStrokeWidth = 1f
                setChipStrokeColorResource(android.R.color.darker_gray)
            }
            chipGroup.addView(chip)
        }

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            categoriaAtiva = group.findViewById<Chip>(id)?.text?.toString() ?: "Todos"
            filtrarLista()
        }
    }

    private fun filtrarLista() {
        val filtrada = listaCompleta.filter { entry ->
            val matchCategoria = categoriaAtiva == "Todos" || entry.group == categoriaAtiva
            val matchBusca = termoBusca.isEmpty() ||
                entry.title.contains(termoBusca, ignoreCase = true) ||
                entry.group.contains(termoBusca, ignoreCase = true)
            matchCategoria && matchBusca
        }
        val recycler = findViewById<RecyclerView>(R.id.rv_playlist)
        recycler.adapter = PlaylistAdapter(filtrada) { reproduzirTV(it) }
    }

    private fun configurarNavegacao() {
        findViewById<BottomNavigationView>(R.id.bottom_nav)
            .setOnItemSelectedListener { item -> mostrarAba(item.itemId); true }
    }

    private fun mostrarAba(itemId: Int) {
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
                supportActionBar?.title = "GM2 Play — Youtube"
            }
            R.id.nav_tv -> {
                layoutTv.visibility = View.VISIBLE
                pvTV.player = player
                supportActionBar?.title = "GM2 Play — TV"
            }
            R.id.nav_offline -> {
                layoutOffline.visibility = View.VISIBLE
                pvOffline.player = player
                supportActionBar?.title = "GM2 Play — Vídeos Offline"
                findViewById<Button>(R.id.btn_local).setOnClickListener { abrirVideoLocal() }
            }
        }
    }

    private fun reproduzirTV(entrada: M3uEntry) {
        player?.run { setMediaItem(MediaItem.fromUri(entrada.url)); prepare(); play() }
        Toast.makeText(this, "▶ ${entrada.title}", Toast.LENGTH_SHORT).show()
    }

    private fun reproduzirLocal(uri: Uri) {
        player?.run { setMediaItem(MediaItem.fromUri(uri)); prepare(); play() }
        Toast.makeText(this, "▶ Vídeo local", Toast.LENGTH_SHORT).show()
    }

    private fun abrirVideoLocal() {
        val permissao = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(permissao) == PackageManager.PERMISSION_GRANTED)
            selecionarVideo.launch("video/*")
        else
            pedirPermissao.launch(permissao)
    }

    private fun reverter() {
        desbloqueioAtivo = false
        getSharedPreferences("fermata_demo", Context.MODE_PRIVATE)
            .edit().putBoolean("unknown_sources_ativado", false).apply()
        player?.stop()
        with(findViewById<WebView>(R.id.webview)) { clearCache(true); clearHistory() }
        aplicarModoBloqueado()
        Toast.makeText(this, "✅ Revertido para modo bloqueado", Toast.LENGTH_LONG).show()
    }

    private fun ativarMirroring() {
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), 1001)
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
        findViewById<View>(R.id.toolbar).visibility = v
        findViewById<View>(R.id.bottom_nav).visibility = v
        findViewById<View>(R.id.layout_m3u).visibility = v
        findViewById<View>(R.id.layout_youtube_nav).visibility = v
        findViewById<View>(R.id.btn_local).visibility = v
        if (isInPiP) {
            findViewById<View>(R.id.layout_search_chips).visibility = View.GONE
        } else if (listaCarregada) {
            findViewById<View>(R.id.layout_search_chips).visibility = View.VISIBLE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001)
            Toast.makeText(this,
                if (resultCode == Activity.RESULT_OK) "📱 Espelhamento autorizado"
                else "❌ Espelhamento negado",
                Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
