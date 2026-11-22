import asyncio
import threading
import time
from typing import Optional, Dict, Any, Callable
from PySide6.QtCore import QObject, Signal

try:
    from winrt.windows.media.control import (
        GlobalSystemMediaTransportControlsSessionManager as MediaManager,
        GlobalSystemMediaTransportControlsSessionPlaybackStatus as PlaybackStatus
    )
    WINRT_AVAILABLE = True
except Exception:
    WINRT_AVAILABLE = False

def clean_source_name(app_id: str) -> str:
    if not app_id:
        return "Media"
    app_lower = app_id.lower()
    if "chrome" in app_lower:
        return "YouTube • Chrome"
    elif "msedge" in app_lower or "edge" in app_lower:
        return "YouTube • Edge"
    elif "firefox" in app_lower:
        return "Firefox"
    elif "spotify" in app_lower:
        return "Spotify"
    elif "brave" in app_lower:
        return "YouTube • Brave"
    elif "vlc" in app_lower:
        return "VLC"
    elif "music" in app_lower:
        return "Apple Music"
    elif "netflix" in app_lower:
        return "Netflix"
    # Fallback to base name without .exe
    base = app_id.split("\\")[-1].replace(".exe", "").capitalize()
    return base

class WindowsMediaService(QObject):
    """
    Background worker that monitors Windows Global System Media Transport Controls (GSMTC).
    Pulls live track title, artist, source application (YouTube, Spotify, etc.), and playback state.
    """
    media_updated = Signal(dict)

    def __init__(self):
        super().__init__()
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._current_session = None
        self._loop: Optional[asyncio.AbstractEventLoop] = None

    def start(self):
        if not WINRT_AVAILABLE or self._running:
            return
        self._running = True
        self._thread = threading.Thread(target=self._run_loop, daemon=True)
        self._thread.start()

    def stop(self):
        self._running = False

    def _run_loop(self):
        self._loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self._loop)
        self._loop.run_until_complete(self._poll_media())

    async def _poll_media_once(self):
        try:
            mgr = await MediaManager.request_async()
            cur = mgr.get_current_session()
            self._current_session = cur
            if cur:
                props = await cur.try_get_media_properties_async()
                info = cur.get_playback_info()
                status_val = info.playback_status.value if hasattr(info.playback_status, 'value') else int(info.playback_status)
                is_playing = (status_val == 4)
                source = clean_source_name(cur.source_app_user_model_id)
                title = props.title.strip() if props and props.title else ""
                artist = props.artist.strip() if props and props.artist else ""
                data = {
                    "active": bool(title),
                    "title": title or "No Track Title",
                    "artist": artist or source,
                    "source": source,
                    "is_playing": is_playing,
                    "can_play_pause": info.controls.is_play_pause_toggle_enabled if info and info.controls else True,
                    "can_next": info.controls.is_next_enabled if info and info.controls else True,
                    "can_prev": info.controls.is_previous_enabled if info and info.controls else True,
                }
                self.media_updated.emit(data)
            else:
                self.media_updated.emit({"active": False})
        except Exception:
            pass

    def poll_immediate(self):
        if self._loop and self._loop.is_running():
            asyncio.run_coroutine_threadsafe(self._poll_media_once(), self._loop)

    async def _poll_media(self):
        while self._running:
            try:
                mgr = await MediaManager.request_async()
                cur = mgr.get_current_session()
                self._current_session = cur

                if cur:
                    props = await cur.try_get_media_properties_async()
                    info = cur.get_playback_info()

                    status_val = info.playback_status.value if hasattr(info.playback_status, 'value') else int(info.playback_status)
                    is_playing = (status_val == 4) # 4 = Playing, 5 = Paused

                    source = clean_source_name(cur.source_app_user_model_id)
                    title = props.title.strip() if props and props.title else ""
                    artist = props.artist.strip() if props and props.artist else ""

                    data = {
                        "active": bool(title),
                        "title": title or "No Track Title",
                        "artist": artist or source,
                        "source": source,
                        "is_playing": is_playing,
                        "can_play_pause": info.controls.is_play_pause_toggle_enabled if info and info.controls else True,
                        "can_next": info.controls.is_next_enabled if info and info.controls else True,
                        "can_prev": info.controls.is_previous_enabled if info and info.controls else True,
                    }
                    self.media_updated.emit(data)
                else:
                    self.media_updated.emit({"active": False})
            except Exception:
                pass

            await asyncio.sleep(1.2)

    def toggle_play_pause(self):
        if not self._current_session or not self._loop:
            return
        async def _act():
            try:
                await self._current_session.try_toggle_play_pause_async()
            except Exception:
                pass
        asyncio.run_coroutine_threadsafe(_act(), self._loop)

    def skip_next(self):
        if not self._current_session or not self._loop:
            return
        async def _act():
            try:
                await self._current_session.try_skip_next_async()
            except Exception:
                pass
        asyncio.run_coroutine_threadsafe(_act(), self._loop)

    def skip_previous(self):
        if not self._current_session or not self._loop:
            return
        async def _act():
            try:
                await self._current_session.try_skip_previous_async()
            except Exception:
                pass
        asyncio.run_coroutine_threadsafe(_act(), self._loop)
