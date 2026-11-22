package com.budora.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.budora.data.NowPlayingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BudoraMediaService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var pollJob: Job? = null
    private var activeController: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        Log.d(TAG, "OnActiveSessionsChanged: count=${controllers?.size ?: 0}")
        updateFromControllers(controllers)
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            Log.d(TAG, "onPlaybackStateChanged: state=${state?.state}")
            updateFromController(activeController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            Log.d(TAG, "onMetadataChanged: title=${metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)}")
            updateFromController(activeController)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        NowPlayingRepository.setPermission(true)
        Log.d(TAG, "BudoraMediaService connected")

        try {
            val sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val componentName = ComponentName(this, BudoraMediaService::class.java)
            sessionManager?.addOnActiveSessionsChangedListener(sessionsListener, componentName)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding sessions listener", e)
        }

        refreshActiveSession()

        // 1.5s background polling to guarantee real-time updates across all Android ROMs
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(1500L)
                refreshActiveSession()
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        pollJob?.cancel()
        instance = null
        NowPlayingRepository.setPermission(false)
        try {
            val sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {}
        Log.d(TAG, "BudoraMediaService disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshActiveSession()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshActiveSession()
    }

    fun refreshActiveSession() {
        try {
            // 1. Try standard MediaSessionManager
            val sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val componentName = ComponentName(this, BudoraMediaService::class.java)
            val controllers = sessionManager?.getActiveSessions(componentName)

            if (!controllers.isNullOrEmpty()) {
                val hasActive = controllers.any { 
                    it.playbackState?.state == PlaybackState.STATE_PLAYING ||
                    !it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
                }
                if (hasActive) {
                    updateFromControllers(controllers)
                    return
                }
            }

            // 2. Fallback for ColorOS / Oppo / OEM ROMs: inspect activeNotifications
            val notifs = try { activeNotifications } catch (_: Exception) { null }
            if (notifs != null) {
                for (sbn in notifs) {
                    val pkg = sbn.packageName.lowercase()
                    val n = sbn.notification ?: continue
                    val extras = n.extras ?: continue

                    val isMediaPkg = pkg.contains("youtube") || pkg.contains("spotify") ||
                        pkg.contains("music") || pkg.contains("chrome") ||
                        pkg.contains("audio") || pkg.contains("saavn") || pkg.contains("podcast")

                    val token = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
                    }

                    if (token != null) {
                        val ctrl = MediaController(this, token)
                        if (ctrl.playbackState?.state == PlaybackState.STATE_PLAYING || isMediaPkg) {
                            updateFromControllers(listOf(ctrl))
                            return
                        }
                    }

                    if (isMediaPkg) {
                        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                        if (!title.isNullOrBlank()) {
                            val appSource = when {
                                pkg.contains("youtube") -> "YouTube"
                                pkg.contains("spotify") -> "Spotify"
                                pkg.contains("chrome") -> "Chrome"
                                pkg.contains("music") -> "Music"
                                pkg.contains("saavn") -> "JioSaavn"
                                else -> "Media"
                            }
                            Log.d(TAG, "Notification fallback: title='$title', artist='$text', pkg='$pkg'")
                            NowPlayingRepository.updateTrack(title, text, appSource, true)
                            return
                        }
                    }
                }
            }

            // If nothing found
            if (activeController != null) {
                activeController?.unregisterCallback(callback)
                activeController = null
            }
            NowPlayingRepository.updateTrack(null, null, null, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error in refreshActiveSession", e)
        }
    }

    private fun updateFromControllers(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            if (activeController != null) {
                activeController?.unregisterCallback(callback)
                activeController = null
            }
            NowPlayingRepository.updateTrack(null, null, null, false)
            return
        }

        val bestController = controllers.firstOrNull { 
            it.playbackState?.state == PlaybackState.STATE_PLAYING 
        } ?: controllers.firstOrNull { 
            !it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() 
        } ?: controllers.first()

        if (bestController != activeController) {
            activeController?.unregisterCallback(callback)
            activeController = bestController
            activeController?.registerCallback(callback)
        }

        updateFromController(activeController)
    }

    private fun updateFromController(controller: MediaController?) {
        if (controller == null) {
            NowPlayingRepository.updateTrack(null, null, null, false)
            return
        }
        val metadata = controller.metadata
        var title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        var artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) 
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)

        val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        val appPkg = controller.packageName ?: ""
        val appSource = when {
            appPkg.contains("youtube", ignoreCase = true) -> "YouTube"
            appPkg.contains("spotify", ignoreCase = true) -> "Spotify"
            appPkg.contains("chrome", ignoreCase = true) -> "Chrome"
            appPkg.contains("saavn", ignoreCase = true) -> "JioSaavn"
            appPkg.contains("apple", ignoreCase = true) -> "Apple Music"
            appPkg.contains("music", ignoreCase = true) -> "Music"
            else -> "Media"
        }

        if (title.isNullOrBlank()) {
            val extras = controller.extras
            title = extras?.getString("android.title") 
                ?: extras?.getString("title")
                ?: extras?.getCharSequence("android.title")?.toString()
        }
        if (artist.isNullOrBlank()) {
            val extras = controller.extras
            artist = extras?.getString("android.text") 
                ?: extras?.getString("artist")
                ?: extras?.getCharSequence("android.text")?.toString()
        }

        Log.d(TAG, "Track resolved: title='$title', artist='$artist', source='$appSource', playing=$isPlaying")
        NowPlayingRepository.updateTrack(title, artist, appSource, isPlaying)
    }

    companion object {
        private const val TAG = "BudoraMedia"
        var instance: BudoraMediaService? = null

        fun togglePlayPause() {
            val c = instance?.activeController ?: return
            val state = c.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) {
                c.transportControls.pause()
            } else {
                c.transportControls.play()
            }
        }

        fun skipNext() {
            instance?.activeController?.transportControls?.skipToNext()
        }

        fun skipPrevious() {
            instance?.activeController?.transportControls?.skipToPrevious()
        }
    }
}
