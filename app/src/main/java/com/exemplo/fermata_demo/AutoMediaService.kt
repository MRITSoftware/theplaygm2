package com.exemplo.fermata_demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat

class AutoMediaService : MediaBrowserServiceCompat() {

    private lateinit var session: MediaSessionCompat

    companion object {
        // Lambdas preenchidas pela MainActivity — controlam o ExoPlayer via volante/carro
        var onPlay:  (() -> Unit)? = null
        var onPause: (() -> Unit)? = null
        var onStop:  (() -> Unit)? = null

        private var sessionRef: MediaSessionCompat? = null

        // Capa gerada uma vez e reutilizada em todas as atualizações
        val capaPadrao: Bitmap by lazy { criarCapaGM2() }

        fun atualizarNowPlaying(titulo: String, artista: String = "GM2 Play") {
            sessionRef?.apply {
                setMetadata(
                    MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titulo)
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artista)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "GM2 Play")
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, capaPadrao)
                        .build()
                )
                setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
                        .setActions(
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_STOP  or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE
                        )
                        .build()
                )
            }
        }

        fun atualizarPausado() {
            sessionRef?.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 1f)
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_STOP  or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                    .build()
            )
        }

        fun atualizarParado() {
            sessionRef?.apply {
                setMetadata(
                    MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "GM2 Play")
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Selecione uma mídia para reproduzir")
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, capaPadrao)
                        .build()
                )
                setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 1f)
                        .setActions(PlaybackStateCompat.ACTION_PLAY)
                        .build()
                )
            }
        }

        // Capa 512×512 com identidade visual do GM2 Play para a tela do carro
        private fun criarCapaGM2(): Bitmap {
            val size = 512
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.BLACK)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                typeface  = Typeface.DEFAULT_BOLD
            }

            // Anel azul externo
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 18f
            paint.color = Color.parseColor("#1e7fd4")
            canvas.drawCircle(size / 2f, size / 2f, 230f, paint)

            // Triângulo play
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#cccccc")
            val path = android.graphics.Path().apply {
                moveTo(size / 2f - 80f, size / 2f - 110f)
                lineTo(size / 2f + 110f, size / 2f)
                lineTo(size / 2f - 80f, size / 2f + 110f)
                close()
            }
            canvas.drawPath(path, paint)

            // Texto "GM" em branco
            paint.textSize = 120f
            paint.color = Color.WHITE
            canvas.drawText("GM", size / 2f - 60f, size / 2f + 175f, paint)

            // Texto "2" em azul
            paint.color = Color.parseColor("#1e7fd4")
            canvas.drawText("2", size / 2f + 100f, size / 2f + 175f, paint)

            return bmp
        }
    }

    override fun onCreate() {
        super.onCreate()

        session = MediaSessionCompat(this, "GM2PlayAutoSession").apply {

            // Callback: comandos do volante/painel do carro
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay()  { onPlay?.invoke()  }
                override fun onPause() { onPause?.invoke() }
                override fun onStop()  { onStop?.invoke()  }
            })

            // Estado e metadata iniciais
            setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "GM2 Play")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Selecione uma mídia para reproduzir")
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, capaPadrao)
                    .build()
            )
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE, 0L, 1f)
                    .setActions(PlaybackStateCompat.ACTION_PLAY)
                    .build()
            )

            isActive = true
        }

        sessionToken = session.sessionToken
        sessionRef   = session
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot = BrowserRoot("gm2_root", null)

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) = result.sendResult(emptyList())

    override fun onDestroy() {
        // Limpa referências para evitar vazamento de memória
        sessionRef = null
        onPlay  = null
        onPause = null
        onStop  = null
        session.release()
        super.onDestroy()
    }
}
