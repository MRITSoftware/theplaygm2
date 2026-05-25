// ============================================================
// DEMONSTRAÇÃO ACADÊMICA — Fermata Auto Demo
// Disciplina: Segurança da Informação
//
// Este app demonstra DOIS mecanismos de bypass do Android Auto:
//
//   1. WebView bypass: carrega m.youtube.com dentro do app,
//      escapando da whitelist de apps do Android Auto.
//
//   2. Screen Mirroring: espelha a tela do celular no display
//      do carro usando a API MediaProjection do Android.
//
// A classe BuildConfig é gerada automaticamente pelo Gradle
// com base no productFlavor selecionado no build.gradle:
//   • Flavor "bloqueado"     → BuildConfig.DESBLOQUEADO = false
//   • Flavor "desbloqueado"  → BuildConfig.DESBLOQUEADO = true
// ============================================================

package com.exemplo.fermata_demo

// Importações das classes Android necessárias
import android.app.Activity                          // Fornece RESULT_OK / RESULT_CANCELED
import android.content.Context                       // Contexto para SharedPreferences e serviços
import android.content.Intent                        // Representa uma intenção/ação do sistema
import android.media.projection.MediaProjectionManager // Gerencia permissões de captura de tela
import android.os.Build                              // Constantes de versão do Android (API levels)
import android.os.Bundle                             // Pacote de dados passado ao criar a Activity
import android.webkit.WebChromeClient                // Suporte a tela cheia e diálogos JS no WebView
import android.webkit.WebView                        // O "navegador embutido" — coração do bypass
import android.webkit.WebViewClient                  // Controla como o WebView navega entre páginas
import android.widget.Button                         // Componente de botão da UI
import android.widget.Toast                          // Mensagem temporária flutuante na tela
import androidx.appcompat.app.AppCompatActivity      // Activity base com suporte a temas modernos

class MainActivity : AppCompatActivity() {

    // ============================================================
    // PROPRIEDADES DA CLASSE (variáveis de estado)
    // ============================================================

    // Controla se o desbloqueio está ativo NESTE APK.
    // Inicializado com o valor definido em tempo de compilação pelo Gradle.
    //   • APK bloqueado:     BuildConfig.DESBLOQUEADO = false → esta var começa false
    //   • APK desbloqueado:  BuildConfig.DESBLOQUEADO = true  → esta var começa true
    // Isso é diferente de uma variável comum: o valor vem do FLAVOR do build.gradle,
    // não do código-fonte. Cada APK compilado tem um valor diferente "embutido".
    private var desbloqueioAtivo = BuildConfig.DESBLOQUEADO

    // Código numérico usado para identificar a nossa solicitação de mirroring.
    // O Android usa esse código em onActivityResult() para saber qual popup respondeu.
    // Pode ser qualquer inteiro positivo — escolhemos 1001 por convenção.
    private val CODIGO_CAPTURA_TELA = 1001

    // Referência ao WebView declarado no layout XML (activity_main.xml).
    // "lateinit" = será inicializado em onCreate(), não na declaração.
    // O compilador garante que vamos inicializá-lo antes de usar.
    private lateinit var webView: WebView

    // Referência ao gerenciador de projeção de tela (usado no mirroring).
    private lateinit var mediaProjectionManager: MediaProjectionManager

    // ============================================================
    // onCreate() — Chamado pelo Android quando o app é aberto
    // É o equivalente ao "main()" em apps Android
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        // Sempre chame super.onCreate() primeiro — configura o ciclo de vida da Activity
        super.onCreate(savedInstanceState)

        // Infla o layout XML e define como a tela será desenhada
        // R.layout.activity_main → arquivo: res/layout/activity_main.xml
        setContentView(R.layout.activity_main)

        // --------------------------------------------------------
        // DECISÃO CENTRAL: qual modo este APK usa?
        // --------------------------------------------------------
        // "desbloqueioAtivo" já foi definido pelo BuildConfig acima.
        // Aqui apenas roteamos para a função de configuração correta.

        if (desbloqueioAtivo) {
            // APK DESBLOQUEADO: mostra aviso e carrega YouTube no WebView
            // Este é o comportamento que o Fermata real implementa
            Toast.makeText(
                this,
                "⚠️ MODO DESBLOQUEADO\nYouTube carregado via WebView",
                Toast.LENGTH_LONG
            ).show()
            configurarYouTubeDesbloqueado()  // → PARTE 3 abaixo

        } else {
            // APK BLOQUEADO: mostra aviso e exibe a tela de bloqueio
            // Este é o comportamento PADRÃO e SEGURO do Android Auto
            Toast.makeText(
                this,
                "🔒 MODO BLOQUEADO\nComportamento padrão do Android Auto",
                Toast.LENGTH_LONG
            ).show()
            configurarYouTubeBloqueado()  // → PARTE 4 abaixo
        }

        // --------------------------------------------------------
        // BOTÃO 1: Verifica e exibe o status atual
        // --------------------------------------------------------
        // findViewById() localiza o elemento na tela pelo ID definido no XML
        val btnVerificarStatus = findViewById<Button>(R.id.btn_verificar_status)
        btnVerificarStatus.setOnClickListener {
            // Lê o valor atual da variável e formata uma mensagem descritiva
            val mensagem = if (desbloqueioAtivo)
                "STATUS: DESBLOQUEADO\n" +
                "BuildConfig.DESBLOQUEADO = true\n" +
                "YouTube rodando via WebView"
            else
                "STATUS: BLOQUEADO\n" +
                "BuildConfig.DESBLOQUEADO = false\n" +
                "Tela de bloqueio exibida"
            Toast.makeText(this, mensagem, Toast.LENGTH_LONG).show()
        }

        // --------------------------------------------------------
        // BOTÃO 2: Reverte o desbloqueio (demonstra a mitigação)
        // --------------------------------------------------------
        val btnReverterBloqueio = findViewById<Button>(R.id.btn_reverter)
        btnReverterBloqueio.setOnClickListener {
            if (desbloqueioAtivo) {
                // Só faz sentido reverter se estiver desbloqueado
                reverterDesbloqueio()  // → PARTE 5 abaixo
            } else {
                Toast.makeText(this, "Já está em modo bloqueado", Toast.LENGTH_SHORT).show()
            }
        }

        // --------------------------------------------------------
        // BOTÃO 3: Ativa o espelhamento de tela (segundo método de bypass)
        // --------------------------------------------------------
        val btnAtivarMirroring = findViewById<Button>(R.id.btn_mirror)
        btnAtivarMirroring.setOnClickListener {
            ativarEspelhamentoTela()  // → PARTE 6 abaixo
        }
    }

    // ============================================================
    // PARTE 3: YOUTUBE DESBLOQUEADO — o bypass central do Fermata
    // ============================================================

    private fun configurarYouTubeDesbloqueado() {
        // Encontra o WebView no layout pelo ID definido no activity_main.xml
        webView = findViewById(R.id.webview_youtube)

        // Configurações do WebView — cada uma tem implicação de segurança:
        webView.settings.apply {

            // VULNERABILIDADE #1: JavaScript habilitado
            // Necessário para o YouTube funcionar (reprodução, busca, etc.)
            // Porém: permite execução de código JS de qualquer site carregado.
            // Um site malicioso poderia executar JS no contexto deste app.
            // MITIGAÇÃO: validar URLs antes de carregar (feito no webViewClient abaixo)
            javaScriptEnabled = true

            // VULNERABILIDADE #2: popups automáticos
            // O player do YouTube abre janelas auxiliares (ex.: confirmações).
            // Porém: sites maliciosos poderiam abrir popups sem interação do usuário.
            // MITIGAÇÃO: limitar a domínios confiáveis no shouldOverrideUrlLoading
            javaScriptCanOpenWindowsAutomatically = true

            // Habilita zoom por gestos (pinça) — sem impacto de segurança relevante
            setSupportZoom(true)

            // VULNERABILIDADE #3: autoplay de mídia
            // Permite que vídeos comecem sem o usuário tocar na tela.
            // No contexto do carro, isso amplifica a distração do motorista.
            // MITIGAÇÃO: manter como "true" (exigir gesto) em apps de segurança
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mediaPlaybackRequiresUserGesture = false
            }
        }

        // WebViewClient: intercepta e controla toda a navegação dentro do WebView
        webView.webViewClient = object : WebViewClient() {

            // MITIGAÇÃO PARCIAL IMPLEMENTADA:
            // shouldOverrideUrlLoading() é chamado ANTES de cada navegação.
            // Aqui bloqueamos qualquer domínio que não seja o YouTube,
            // reduzindo o risco de o WebView ser redirecionado para sites maliciosos.
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                // Permite apenas URLs do YouTube (mobile e desktop)
                val dominiosPermitidos = listOf(
                    "https://m.youtube.com",
                    "https://www.youtube.com",
                    "https://youtube.com",
                    "https://youtu.be",
                    "https://accounts.google.com"  // Necessário para login
                )
                val urlPermitida = dominiosPermitidos.any { url.startsWith(it) }

                // false = WebView navega normalmente (URL permitida)
                // true  = WebView cancela a navegação (URL bloqueada)
                return !urlPermitida
            }
        }

        // WebChromeClient: necessário para suporte a:
        //   • Tela cheia de vídeo (fullscreen)
        //   • Alertas JavaScript (alert(), confirm())
        //   • Indicador de progresso de carregamento
        webView.webChromeClient = WebChromeClient()

        // *** MECANISMO CENTRAL DO BYPASS ***
        // O Android Auto mantém uma whitelist de apps aprovados.
        // Apps não aprovados são bloqueados no LAUNCHER do carro — antes de abrir.
        // Porém, o bloqueio não se aplica ao CONTEÚDO carregado dentro de um app.
        //
        // Ao carregar m.youtube.com aqui, o YouTube "escapa" do filtro:
        //   • O Android Auto vê: "App Fermata Demo" → PERMITIDO (está na whitelist)
        //   • O Android Auto não vê: o YouTube rodando dentro do WebView
        //
        // É equivalente a: "você não pode entrar com malas,
        // mas pode entrar com uma mochila que contenha malas dentro."
        webView.loadUrl("https://m.youtube.com")
    }

    // ============================================================
    // PARTE 4: YOUTUBE BLOQUEADO — comportamento padrão seguro
    // ============================================================

    private fun configurarYouTubeBloqueado() {
        webView = findViewById(R.id.webview_youtube)

        // HTML que explica o bloqueio e demonstra o que ocorreria no Android Auto real.
        // No mundo real, o Android Auto bloquearia o APP antes de qualquer código rodar.
        // Aqui simulamos esse bloqueio na UI para fins educacionais.
        val htmlBloqueado = """
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        background: #1a1a2e;
                        color: #e0e0e0;
                        font-family: sans-serif;
                        text-align: center;
                        padding: 30px 20px;
                    }
                    h2 { color: #e94560; }
                    .badge {
                        background: #e94560;
                        color: white;
                        padding: 6px 16px;
                        border-radius: 20px;
                        display: inline-block;
                        margin: 10px 0;
                        font-weight: bold;
                    }
                    ol { text-align: left; display: inline-block; }
                    li { margin: 8px 0; }
                    hr { border-color: #e94560; margin: 20px 0; }
                    small { color: #888; }
                </style>
            </head>
            <body>
                <div class="badge">BuildConfig.DESBLOQUEADO = false</div>
                <h2>🔒 CONTEÚDO BLOQUEADO</h2>
                <p>Este APK foi compilado no <b>MODO SEGURO</b>.<br>
                O Android Auto aplica este bloqueio por padrão<br>
                para evitar distração do motorista.</p>

                <hr>
                <h3>Como o Fermata real contorna isso:</h3>
                <ol>
                    <li>Usuário ativa o <b>Modo Desenvolvedor</b> no Android Auto</li>
                    <li>Ativa <b>"Unknown Sources"</b> (Fontes Desconhecidas)</li>
                    <li>Instala o Fermata como APK externo (sideload)</li>
                    <li>Fermata carrega <b>youtube.com em um WebView</b> interno</li>
                    <li>Android Auto vê o app (permitido), não o YouTube (bloqueado)</li>
                </ol>

                <hr>
                <h3>Como reverter (mitigação):</h3>
                <ol>
                    <li>Desinstalar o Fermata Auto</li>
                    <li>Desativar "Unknown Sources" no Modo Desenvolvedor</li>
                    <li>Desativar o Modo Desenvolvedor do Android Auto</li>
                    <li>Limpar o cache do Android Auto nas configurações</li>
                </ol>

                <hr>
                <small>APK Bloqueado — Demonstração Acadêmica — Segurança da Informação</small>
            </body>
            </html>
        """.trimIndent()

        // Carrega o HTML diretamente no WebView, sem fazer requisição à internet
        // Parâmetros:
        //   baseUrl = null       (sem URL base — conteúdo local)
        //   data    = htmlBloqueado (o HTML que escrevemos acima)
        //   mimeType = "text/html"  (tipo do conteúdo)
        //   encoding = "UTF-8"      (codificação de caracteres)
        //   historyUrl = null    (não entra no histórico de navegação)
        webView.loadDataWithBaseURL(null, htmlBloqueado, "text/html", "UTF-8", null)
    }

    // ============================================================
    // PARTE 5: REVERTER O DESBLOQUEIO — demonstração da mitigação
    // ============================================================

    private fun reverterDesbloqueio() {
        // PASSO 1: Muda a variável de controle em memória (RAM)
        // TRUE → FALSE: o app passa a se comportar como bloqueado nesta sessão
        // Nota: isso NÃO muda o APK — na próxima abertura, voltaria ao valor original
        // do BuildConfig. Aqui estamos simulando a mudança de configuração do sistema.
        desbloqueioAtivo = false

        // PASSO 2: Remove configurações persistidas em disco (SharedPreferences)
        // SharedPreferences é o armazenamento chave-valor local do Android
        // (equivalente ao localStorage do browser ou ao registry do Windows)
        val sharedPref = getSharedPreferences("fermata_demo", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            // Salva "false" para a chave "unknown_sources_ativado"
            putBoolean("unknown_sources_ativado", false)

            // apply() = salva de forma assíncrona (não trava a UI)
            // commit() seria síncrono — use apply() para operações simples
            apply()
        }

        // PASSO 3: Limpa dados do WebView (cache, histórico, cookies de sessão)
        limparConfiguracoesDesbloqueio()  // → método abaixo

        // PASSO 4: Substitui o conteúdo da tela pela tela de bloqueio
        configurarYouTubeBloqueado()

        // PASSO 5: Informa o usuário em detalhes o que foi feito
        Toast.makeText(
            this,
            "✅ REVERSÃO COMPLETA\n\n" +
            "• desbloqueioAtivo: true → false\n" +
            "• SharedPreferences: limpo\n" +
            "• Cache WebView: removido\n" +
            "• Histórico WebView: removido\n\n" +
            "No Android Auto real, também seria\n" +
            "necessário desativar 'Unknown Sources'.",
            Toast.LENGTH_LONG
        ).show()
    }

    // ============================================================
    // PARTE 5.1: LIMPAR DADOS DO WEBVIEW
    // Chamada por reverterDesbloqueio()
    // ============================================================

    private fun limparConfiguracoesDesbloqueio() {
        // Remove todos os dados armazenados em cache pelo WebView:
        //   • Arquivos HTML, CSS, JavaScript baixados do YouTube
        //   • Imagens em cache
        //   • Tokens de sessão armazenados em cache (parcialmente)
        // "true" = também limpa o cache em DISCO, não só na memória RAM
        webView.clearCache(true)

        // Remove o histórico de navegação do WebView
        // Efeito: os botões "Voltar" e "Avançar" ficam desabilitados
        webView.clearHistory()

        // EFEITO DE SEGURANÇA:
        // Após limpar o cache, o usuário perderia tokens de sessão do YouTube
        // armazenados em cache. Precisaria fazer login novamente se o
        // desbloqueio fosse reativado. Isso adiciona uma barreira extra.
        //
        // LIMITAÇÃO:
        // Cookies de sessão são separados do cache e precisariam ser
        // removidos via CookieManager.getInstance().removeAllCookies() para
        // remoção completa. Esta demo não implementa isso por simplicidade.
    }

    // ============================================================
    // PARTE 6: ESPELHAMENTO DE TELA — segundo método de bypass
    // ============================================================

    private fun ativarEspelhamentoTela() {
        // Verifica se o Android suporta a API de projeção de tela
        // A MediaProjection API foi introduzida no Android 5.0 (API 21 = Lollipop)
        // Como nosso minSdk é 26, esta verificação é tecnicamente redundante,
        // mas mantemos para deixar claro o requisito mínimo da funcionalidade
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            // Obtém o serviço do sistema responsável por captura de tela
            // getSystemService() devolve serviços nativos do Android por nome
            // O cast "as MediaProjectionManager" é necessário porque
            // getSystemService() devolve um tipo genérico (Any?)
            mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager

            // Cria a Intent que vai exibir o popup de autorização de captura
            // Este popup é OBRIGATÓRIO pelo Android — o app não pode suprimi-lo.
            // Texto do popup: "[App] vai começar a capturar tudo que aparece na tela"
            val intent = mediaProjectionManager.createScreenCaptureIntent()

            // Inicia a Activity do popup e aguarda a resposta do usuário.
            // Quando o usuário responder (permitir ou negar), o Android
            // chama onActivityResult() com o código CODIGO_CAPTURA_TELA = 1001
            startActivityForResult(intent, CODIGO_CAPTURA_TELA)

        } else {
            // Dispositivo com Android < 5.0 — sem suporte a mirroring via API
            Toast.makeText(
                this,
                "Espelhamento requer Android 5.0 (Lollipop) ou superior",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ============================================================
    // PARTE 7: RESULTADO DA AUTORIZAÇÃO DE MIRRORING
    // Chamado automaticamente pelo Android após o usuário responder ao popup
    // ============================================================

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Chama o método pai — necessário para que outros resultados sejam processados
        super.onActivityResult(requestCode, resultCode, data)

        // Verifica se este resultado veio da nossa solicitação de captura (código 1001)
        // O Android pode chamar onActivityResult() por outras razões — o código garante
        // que só processamos o resultado correto
        if (requestCode == CODIGO_CAPTURA_TELA) {

            if (resultCode == Activity.RESULT_OK) {
                // Usuário clicou em "Permitir" no popup de captura de tela
                // PONTO DE SEGURANÇA POSITIVO: o consentimento foi explícito

                Toast.makeText(
                    this,
                    "📱 ESPELHAMENTO AUTORIZADO\n\n" +
                    "O conteúdo da tela pode agora ser\n" +
                    "transmitido para o display do carro.\n\n" +
                    "Qualquer app aberto no celular apareceria\n" +
                    "no Android Auto — incluindo Netflix, WhatsApp, etc.\n\n" +
                    "[SEGURANÇA] Usuário autorizou explicitamente.",
                    Toast.LENGTH_LONG
                ).show()

                // IMPLEMENTAÇÃO REAL (comentada por ser extensa):
                // O código abaixo criaria o VirtualDisplay que transmite a tela.
                // Mantemos como comentário para fins educacionais:
                //
                // val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data!!)
                //
                // val metrics = resources.displayMetrics
                // val width = metrics.widthPixels
                // val height = metrics.heightPixels
                // val dpi = metrics.densityDpi
                //
                // val virtualDisplay = mediaProjection.createVirtualDisplay(
                //     "AndroidAutoMirror",              // Nome identificador
                //     width, height, dpi,               // Resolução e densidade
                //     DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                //     null,                             // Surface (null = mirror mode)
                //     null, null                        // Callbacks opcionais
                // )
                // O conteúdo da tela agora seria transmitido para o carro.

            } else {
                // Usuário clicou em "Negar" no popup — mirroring bloqueado
                // PONTO DE SEGURANÇA POSITIVO: sem consentimento = sem bypass

                Toast.makeText(
                    this,
                    "❌ ESPELHAMENTO NEGADO\n\n" +
                    "Usuário não autorizou a captura de tela.\n" +
                    "Android Auto permanece no modo seguro.\n\n" +
                    "[SEGURANÇA] Esta é a proteção que o\n" +
                    "Android mantém mesmo no Fermata:\n" +
                    "o mirroring SEMPRE requer consentimento.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
