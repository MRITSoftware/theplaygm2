package com.exemplo.fermata_demo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MainActivity : AppCompatActivity() {

    private var desbloqueioAtivo = BuildConfig.DESBLOQUEADO
    private val CAPTURA_TELA = 1001
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var player: ExoPlayer? = null
    private var modoWebView = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!desbloqueioAtivo) {
            aplicarModoBloqueado()
            return
        }

        inicializarPlayer()
        configurarWebView()
        configurarM3u()
        configurarBotoes()
    }

    private fun aplicarModoBloqueado() {
        findViewById<View>(R.id.layout_m3u).visibility = View.GONE
        findViewById<View>(R.id.rv_playlist).visibility = View.GONE

        val webView = findViewById<WebView>(R.id.webview)
        webView.visibility = View.VISIBLE
        webView.loadDataWithBaseURL(null, htmlBloqueado(), "text/html", "UTF-8", null)

        listOf(R.id.btn_modo, R.id.btn_reverter, R.id.btn_mirror).forEach {
            findViewById<Button>(it).isEnabled = false
        }

        findViewById<Button>(R.id.btn_status).setOnClickListener {
            Toast.makeText(this, "BLOQUEADO | BuildConfig.DESBLOQUEADO = false", Toast.LENGTH_LONG).show()
        }
    }

    private fun htmlBloqueado() = """
        <html><body style='background:#1a1a2e;color:#fff;text-align:center;
        padding:40px;font-family:sans-serif;'>
        <h2 style='color:#e94560'>🔒 BLOQUEADO</h2>
        <p>BuildConfig.DESBLOQUEADO = <b>false</b></p>
        <p>Este APK foi compilado no modo seguro.</p>
        </body></html>
    """.trimIndent()

    private fun inicializarPlayer() {
        player = ExoPlayer.Builder(this).build().also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(this@MainActivity, "Erro de reprodução: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
        val playerView = findViewById<PlayerView>(R.id.player_view)
        playerView.player = player
        playerView.visibility = View.VISIBLE
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
                val permitidos = listOf(
                    "https://m.youtube.com",
                    "https://www.youtube.com",
                    "https://youtu.be",
                    "https://accounts.google.com"
                )
                return !permitidos.any { url.startsWith(it) }
            }
        }
        webView.webChromeClient = WebChromeClient()
    }

    private fun configurarM3u() {
        val recycler = findViewById<RecyclerView>(R.id.rv_playlist)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

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
        val btnCarregar = findViewById<Button>(R.id.btn_carregar)
        btnCarregar.isEnabled = false
        btnCarregar.text = "Carregando…"

        lifecycleScope.launch {
            val entradas = withContext(Dispatchers.IO) { parsearM3u(url) }

            btnCarregar.isEnabled = true
            btnCarregar.text = "Carregar"

            if (entradas.isEmpty()) {
                Toast.makeText(this@MainActivity, "Lista vazia ou URL inválida", Toast.LENGTH_LONG).show()
                return@launch
            }

            recycler.visibility = View.VISIBLE
            recycler.adapter = PlaylistAdapter(entradas) { reproduzir(it) }
            Toast.makeText(this@MainActivity, "${entradas.size} item(s) carregado(s)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parsearM3u(url: String): List<M3uEntry> = try {
        val linhas = URL(url).readText(Charsets.UTF_8).lines()
        val resultado = mutableListOf<M3uEntry>()
        var titulo = ""
        for (linha in linhas) {
            when {
                linha.startsWith("#EXTINF") -> titulo = linha.substringAfterLast(",").trim()
                linha.startsWith("http") -> {
                    resultado.add(M3uEntry(titulo.ifEmpty { "Item ${resultado.size + 1}" }, linha.trim()))
                    titulo = ""
                }
            }
        }
        resultado
    } catch (e: Exception) { emptyList() }

    private fun reproduzir(entrada: M3uEntry) {
        if (modoWebView) ativarModoPlayer()
        player?.run {
            setMediaItem(MediaItem.fromUri(entrada.url))
            prepare()
            play()
        }
        Toast.makeText(this, "▶ ${entrada.title}", Toast.LENGTH_SHORT).show()
    }

    private fun configurarBotoes() {
        findViewById<Button>(R.id.btn_status).setOnClickListener {
            Toast.makeText(this, "DESBLOQUEADO | BuildConfig.DESBLOQUEADO = true", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btn_modo).setOnClickListener {
            if (modoWebView) ativarModoPlayer() else ativarModoWebView()
        }

        findViewById<Button>(R.id.btn_reverter).setOnClickListener { reverter() }

        findViewById<Button>(R.id.btn_mirror).setOnClickListener { ativarMirroring() }
    }

    private fun ativarModoWebView() {
        modoWebView = true
        player?.pause()
        findViewById<PlayerView>(R.id.player_view).visibility = View.GONE
        val webView = findViewById<WebView>(R.id.webview)
        webView.visibility = View.VISIBLE
        if (webView.url.isNullOrEmpty() || webView.url == "about:blank") {
            webView.loadUrl("https://m.youtube.com")
        }
        findViewById<Button>(R.id.btn_modo).text = "Player"
    }

    private fun ativarModoPlayer() {
        modoWebView = false
        findViewById<PlayerView>(R.id.player_view).visibility = View.VISIBLE
        findViewById<WebView>(R.id.webview).visibility = View.GONE
        findViewById<Button>(R.id.btn_modo).text = "WebView"
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
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), CAPTURA_TELA)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAPTURA_TELA) {
            Toast.makeText(
                this,
                if (resultCode == Activity.RESULT_OK) "📱 Espelhamento autorizado" else "❌ Espelhamento negado",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
