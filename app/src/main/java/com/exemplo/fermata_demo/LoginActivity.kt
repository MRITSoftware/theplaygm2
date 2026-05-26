package com.exemplo.fermata_demo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Se já tem sessão salva, vai direto para o app
        if (temSessaoAtiva()) {
            abrirApp()
            return
        }

        setContentView(R.layout.activity_login)

        val etEmail    = findViewById<EditText>(R.id.et_email)
        val etPassword = findViewById<EditText>(R.id.et_password)
        val btnLogin   = findViewById<Button>(R.id.btn_login)
        val progress   = findViewById<ProgressBar>(R.id.progress_login)
        val tvError    = findViewById<TextView>(R.id.tv_error)

        val tentarLogin = {
            val email = etEmail.text.toString().trim()
            val senha = etPassword.text.toString()

            if (email.isEmpty() || senha.isEmpty()) {
                tvError.text = "Preencha e-mail e senha"
                tvError.visibility = View.VISIBLE
            } else {
                btnLogin.isEnabled = false
                progress.visibility = View.VISIBLE
                tvError.visibility = View.GONE

                lifecycleScope.launch {
                    val resultado = withContext(Dispatchers.IO) { autenticar(email, senha) }
                    progress.visibility = View.GONE
                    btnLogin.isEnabled = true

                    resultado.fold(
                        onSuccess = { token ->
                            salvarSessao(token, email)
                            registrarAcesso(token)
                            abrirApp()
                        },
                        onFailure = {
                            tvError.text = "E-mail ou senha incorretos. Verifique com o professor."
                            tvError.visibility = View.VISIBLE
                        }
                    )
                }
            }
        }

        btnLogin.setOnClickListener { tentarLogin() }

        // Permite logar pressionando "Done" no teclado
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { tentarLogin(); true } else false
        }
    }

    private fun temSessaoAtiva(): Boolean {
        return getSharedPreferences("gm2_auth", Context.MODE_PRIVATE)
            .getString("access_token", null) != null
    }

    private fun salvarSessao(token: String, email: String) {
        getSharedPreferences("gm2_auth", Context.MODE_PRIVATE).edit()
            .putString("access_token", token)
            .putString("email", email)
            .apply()
    }

    private fun abrirApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // Autentica via Supabase REST — nenhuma dependência extra necessária
    private fun autenticar(email: String, senha: String): Result<String> {
        // Modo demonstração: enquanto o Supabase não está configurado, aceita admin/admin
        if (SupabaseConfig.URL.contains("SEU-PROJETO")) {
            return if (email == "admin" && senha == "admin")
                Result.success("demo-token-local")
            else
                Result.failure(Exception("Modo demo ativo. Use: admin / admin"))
        }
        return autenticarSupabase(email, senha)
    }

    private fun autenticarSupabase(email: String, senha: String): Result<String> = try {
        val url = URL("${SupabaseConfig.URL}/auth/v1/token?grant_type=password")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
        }

        val body = JSONObject()
            .put("email", email)
            .put("password", senha)
            .toString()

        conn.outputStream.use { it.write(body.toByteArray()) }

        if (conn.responseCode == 200) {
            val resposta = conn.inputStream.bufferedReader().readText()
            val token = JSONObject(resposta).getString("access_token")
            Result.success(token)
        } else {
            Result.failure(Exception("HTTP ${conn.responseCode}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // Registra acesso na tabela "acessos" do Supabase (cria a tabela no painel do Supabase)
    private fun registrarAcesso(token: String) {
        if (token == "demo-token-local") return
        registrarAcessoSupabase(token)
    }

    private fun registrarAcessoSupabase(token: String) = try {
        val email = getSharedPreferences("gm2_auth", Context.MODE_PRIVATE)
            .getString("email", "") ?: ""

        val url = URL("${SupabaseConfig.URL}/rest/v1/acessos")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Prefer", "return=minimal")
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
        }

        val body = JSONObject()
            .put("email", email)
            .put("plataforma", "Android")
            .toString()

        conn.outputStream.use { it.write(body.toByteArray()) }
        conn.responseCode // dispara a requisição
    } catch (_: Exception) {
        // Falha silenciosa — não impede o acesso ao app
    }
}
