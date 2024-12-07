package io.github.feivegian.music.services

import android.content.SharedPreferences
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.preference.PreferenceManager
import com.google.common.util.concurrent.ListenableFuture
import io.github.feivegian.music.App.Companion.asApp
import io.github.feivegian.music.BuildConfig

@UnstableApi
class PlaybackService : MediaSessionService(), MediaSession.Callback {
    private lateinit var preferences: SharedPreferences
    private var audioFocus: Boolean = true
    private var wakeLock: Boolean = false

    // TODO: Implement custom cache size
    private var cache: SimpleCache? = null
    private val maxCacheBytes: Long = 2147483648 // 2GB

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        audioFocus = preferences.getBoolean("playback_audio_focus", true)
        wakeLock = preferences.getBoolean("other_wake_lock", false)

        cache = SimpleCache(cacheDir,
            LeastRecentlyUsedCacheEvictor(maxCacheBytes),
            application.asApp().getDatabaseProvider())
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache!!)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(this))

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, audioFocus)
            .setWakeMode(if (wakeLock) C.WAKE_MODE_NETWORK else C.WAKE_MODE_NONE) // use network
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSourceFactory))
            .build()
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(this)
            .build()

        mediaSession?.player?.playWhenReady = true
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            cache?.release()
            release()

            cache = null
            mediaSession = null
        }

        super.onDestroy()
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        if (controller.packageName != BuildConfig.APPLICATION_ID) {
            return MediaSession.ConnectionResult.reject()
        }

        return super.onConnect(session, controller)
    }

    override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
        if (session.connectedControllers.isEmpty()) {
            stopSelf()
        }

        super.onDisconnected(session, controller)
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // do nothing
    }

    @UnstableApi
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return super.onPlaybackResumption(mediaSession, controller)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession
}