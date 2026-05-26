package com.exemplo.fermata_demo

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat

/**
 * Serviço que registra o app como media player no Android Auto.
 *
 * O Android Auto só permite apps que implementam MediaBrowserServiceCompat
 * na sua tela de mídia. Ao declarar esse serviço, o GM2 Play aparece na
 * lista de players do carro — exatamente o que o Fermata original fazia.
 *
 * Bypass: o Android Auto verifica SE o app é um media player (✓),
 * mas não verifica O QUE o app carrega dentro do WebView (brecha).
 */
class AutoMediaService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(this, "GM2PlayAutoSession").apply {
            // Estado inicial: parado, mas pronto para receber comandos do carro
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 1f)
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP
                    )
                    .build()
            )
            isActive = true
        }

        // Token que autentica o serviço junto ao Android Auto
        sessionToken = mediaSession.sessionToken
    }

    /**
     * Android Auto chama esse método para obter a raiz do navegador de mídia.
     * Retornar um BrowserRoot válido = app aceito pelo sistema do carro.
     */
    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("gm2_root", null)
    }

    /**
     * Android Auto chama para listar filhos de uma pasta de mídia.
     * Lista vazia: o conteúdo real é exibido na MainActivity do celular.
     */
    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(emptyList())
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }
}
