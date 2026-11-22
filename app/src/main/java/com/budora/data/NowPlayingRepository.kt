package com.budora.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlayingTrack(
    val title: String,
    val artist: String,
    val appSource: String,
    val isPlaying: Boolean,
)

object NowPlayingRepository {
    private val _track = MutableStateFlow<NowPlayingTrack?>(null)
    val track: StateFlow<NowPlayingTrack?> = _track.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    fun updateTrack(title: String?, artist: String?, appSource: String?, isPlaying: Boolean) {
        if (title.isNullOrBlank()) {
            _track.value = null
        } else {
            _track.value = NowPlayingTrack(
                title = title.trim(),
                artist = (artist ?: appSource ?: "Media").trim(),
                appSource = (appSource ?: "Media").trim(),
                isPlaying = isPlaying,
            )
        }
    }

    fun setPermission(granted: Boolean) {
        _hasPermission.value = granted
    }
}
