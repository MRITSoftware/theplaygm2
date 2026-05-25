# Roteiro de Apresentação — Fermata Auto Demo
**Disciplina: Segurança da Informação**

---

## Estrutura sugerida (15–20 minutos)

---

### 1. Abertura (1–2 min)

**O que falar:**
> "O objetivo do trabalho é demonstrar como o app Fermata Auto contorna as restrições de segurança do Android Auto. O Android Auto bloqueia apps de vídeo enquanto o carro está em movimento — e vamos ver exatamente como esse bloqueio funciona, como ele é burlado, e como mitigar."

**Por que começar assim:** contextualiza o problema de segurança sem entrar em código ainda.

---

### 2. O que é o Android Auto e por que ele bloqueia apps? (2–3 min)

**O que falar:**
> "O Android Auto tem uma whitelist — uma lista de apps aprovados pelo Google que podem aparecer no display do carro. Apps de vídeo como o YouTube não estão nessa lista porque exibir vídeo para o motorista é perigoso. O bloqueio acontece no *launcher* do carro, antes de qualquer app abrir."

**Mostre no código** — `app/build.gradle`, a parte do `defaultConfig`:
> "O nosso app é compilado para Android 8.0 ou superior. Por padrão, nenhum app de terceiros consegue se registrar no Android Auto sem aprovação do Google."

---

### 3. Como o Fermata burla o bloqueio — o WebView (3–4 min)

**O que falar:**
> "O Fermata usa uma brecha simples: o Android Auto bloqueia *apps*, mas não bloqueia *conteúdo web dentro de apps*. O WebView é um navegador Chrome embutido dentro do app. Ao carregar `m.youtube.com` dentro do WebView, o YouTube roda dentro de um app 'neutro'."

**Mostre no código** — `MainActivity.kt`, função `configurarYouTubeDesbloqueado()`:
- Aponte a linha `webView.loadUrl("https://m.youtube.com")` — "é só isso"
- Aponte `javaScriptEnabled = true` — "necessário para o YouTube funcionar"
- Aponte `mediaPlaybackRequiresUserGesture = false` — "vídeo começa sozinho"

**Mostre o APK DESBLOQUEADO rodando** (ou screenshot):
> "O YouTube aparece normalmente dentro do app. O Android Auto vê o app permitido, não o YouTube."

---

### 4. Como o bloqueio é feito no nosso app — os dois flavors (2–3 min)

**O que falar:**
> "Para demonstrar os dois estados — bloqueado e desbloqueado — usamos os *product flavors* do Gradle. Um mesmo código gera dois APKs diferentes. A diferença está numa variável chamada `BuildConfig.DESBLOQUEADO`."

**Mostre no código** — `app/build.gradle`, seção `productFlavors`:
- Aponte `buildConfigField "boolean", "DESBLOQUEADO", "false"` no flavor `bloqueado`
- Aponte `buildConfigField "boolean", "DESBLOQUEADO", "true"` no flavor `desbloqueado`

**Mostre no código** — `MainActivity.kt`, início de `onCreate()`:
```kotlin
private var desbloqueioAtivo = BuildConfig.DESBLOQUEADO
```
> "Essa variável recebe o valor que o Gradle injetou em tempo de compilação. Cada APK já nasce com o valor definido."

**Mostre o APK BLOQUEADO rodando** (ou screenshot):
> "O APK bloqueado exibe a tela HTML explicando o bloqueio — simulando o comportamento padrão do Android Auto."

---

### 5. As vulnerabilidades encontradas (2–3 min)

**O que falar:**
> "Identificamos quatro vulnerabilidades principais neste mecanismo:"

| # | Vulnerabilidade | Onde no código |
|---|---|---|
| 1 | **JavaScript irrestrito** | `javaScriptEnabled = true` — qualquer site carregado pode executar JS |
| 2 | **Popups automáticos** | `javaScriptCanOpenWindowsAutomatically = true` |
| 3 | **Autoplay de mídia** | `mediaPlaybackRequiresUserGesture = false` — distração ao volante |
| 4 | **Unknown Sources** | Para instalar o Fermata real, o usuário precisa ativar Fontes Desconhecidas — isso expõe o dispositivo a malware |

> "A vulnerabilidade mais grave não é técnica: é social. O usuário precisa ir nas configurações avançadas e desativar uma proteção de segurança. Um atacante poderia criar um app falso e instruir a vítima a fazer o mesmo."

---

### 6. O segundo método — Espelhamento de Tela (1–2 min)

**O que falar:**
> "O Fermata tem um segundo modo: o espelhamento de tela. Neste modo, qualquer app do celular aparece no carro — Netflix, Instagram, jogos. Isso usa a API MediaProjection do Android."

**Mostre no código** — função `ativarEspelhamentoTela()`:
> "O Android obriga a exibir um popup de permissão. O app não consegue suprimir esse popup. Se o usuário negar, o mirroring falha. Essa é uma proteção que o Android mantém mesmo no Fermata."

**Demonstre o botão Mirror** — mostre o popup aparecendo.

---

### 7. Como reverter o desbloqueio (2 min)

**O que falar:**
> "A reversão é o inverso do desbloqueio. Nosso app demonstra os passos:"

**Mostre no código** — função `reverterDesbloqueio()`:
1. `desbloqueioAtivo = false` — muda a variável em memória
2. `sharedPref.edit().putBoolean("unknown_sources_ativado", false)` — limpa o disco
3. `webView.clearCache(true)` — remove cache (inclui tokens de sessão)
4. `webView.clearHistory()` — remove histórico de navegação
5. `configurarYouTubeBloqueado()` — aplica a tela de bloqueio

> "No Android Auto real, os passos adicionais seriam: desinstalar o Fermata, desativar Unknown Sources no Modo Desenvolvedor, e limpar o cache do Android Auto nas configurações do sistema."

**Demonstre o botão Reverter** no APK desbloqueado ao vivo.

---

### 8. Como mitigar (prevenção) (1–2 min)

**O que falar:**
> "Identificamos três camadas de mitigação:"

**Camada 1 — No WebView (código):**
> "Já implementamos no código uma mitigação parcial: o método `shouldOverrideUrlLoading` bloqueia qualquer domínio que não seja YouTube. Isso impede redirects maliciosos."

**Mostre no código** — `WebViewClient` override dentro de `configurarYouTubeDesbloqueado()`.

**Camada 2 — No sistema:**
- Nunca ativar Modo Desenvolvedor em dispositivos de uso cotidiano
- Nunca ativar Unknown Sources sem necessidade clara
- Manter o Android Auto atualizado

**Camada 3 — No Google (não implementada):**
> "O Google poderia bloquear isso filtrando o User-Agent do WebView ou detectando o padrão de tráfego, mas até hoje optou por não o fazer."

---

### 9. Como o build funciona — GitHub Actions (1 min)

**Mostre o arquivo** `.github/workflows/build.yml`:
> "Como não temos capacidade de compilar localmente, usamos GitHub Actions. O workflow instala o JDK, o Gradle e o Android SDK automaticamente numa máquina Linux gratuita do GitHub, e gera os dois APKs como artifacts para download."

**Mostre na interface do GitHub** a aba Actions com os artifacts disponíveis.

---

### 10. Conclusão (1 min)

**O que falar:**
> "O Fermata Auto demonstra que restrições de segurança implementadas apenas na camada de aplicação são insuficientes. O bloqueio do Android Auto opera no launcher, mas não no runtime dos apps. A correção real exigiria que o Android Auto isolasse o contexto de execução de apps de forma mais profunda — ou que o Google passasse a verificar o tráfego de rede gerado por apps em execução no carro."

---

## Dicas para a apresentação

- **Mostre os dois APKs lado a lado** se tiver dois celulares disponíveis
- **Se não tiver celular Android**, use prints de tela dos APKs funcionando
- **Foque no `app/build.gradle`** quando explicar os flavors — é onde a mágica acontece
- **Não precisa explicar cada linha do código** — foque nos conceitos: WebView bypass, BuildConfig, reversão
- **Prepare uma pergunta para o professor**: "Quais outras APIs do Android poderiam ser exploradas da mesma forma?"
