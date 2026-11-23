import sys
import time
import os
from PySide6.QtWidgets import QApplication
from PySide6.QtCore import QObject, Signal, Slot, Qt
from PySide6.QtGui import QIcon
from PySide6.QtNetwork import QLocalServer, QLocalSocket
from models import BudoraState
from scanner import BudoraScanner
from config import settings
from media_service import WindowsMediaService
from ui_popup import BudoraPopupWindow
from ui_tray import BudoraTrayIcon
from ui_main_window import BudoraMainWindow

class Bridge(QObject):
    state_received = Signal(BudoraState)

SOCKET_NAME = "BudoraApp_SingleInstance_IPC"

def main():
    app = QApplication(sys.argv)
    app.setQuitOnLastWindowClosed(False)

    # 1. Single-Instance Check
    # If Budora is already running, ping it to show its window and exit this duplicate process
    test_socket = QLocalSocket()
    test_socket.connectToServer(SOCKET_NAME)
    if test_socket.waitForConnected(400):
        test_socket.write(b"ACTIVATE\n")
        test_socket.waitForBytesWritten(1000)
        sys.exit(0)

    # 2. Setup Icon
    base_dir = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
    icon_path = os.path.join(base_dir, "icon.ico")
    if os.path.exists(icon_path):
        app.setWindowIcon(QIcon(icon_path))

    bridge = Bridge()

    # 3. Media Service for Windows (GSMTC)
    media_svc = WindowsMediaService()

    # 4. Create Full Main App Window
    main_win = BudoraMainWindow(
        on_trigger_popup=lambda: popup.show_animated(),
        on_media_toggle=media_svc.toggle_play_pause,
        on_media_next=media_svc.skip_next,
        on_media_prev=media_svc.skip_previous,
        on_media_refresh=media_svc.poll_immediate
    )
    media_svc.media_updated.connect(main_win.update_media)
    media_svc.start()

    def open_dashboard():
        main_win.open_page(0)
        main_win.setWindowState(main_win.windowState() & ~Qt.WindowState.WindowMinimized | Qt.WindowState.WindowActive)
        main_win.showNormal()
        main_win.show()
        main_win.raise_()
        main_win.activateWindow()
        try:
            import ctypes
            hwnd = int(main_win.winId())
            ctypes.windll.user32.ShowWindow(hwnd, 9)  # SW_RESTORE
            ctypes.windll.user32.SetForegroundWindow(hwnd)
        except Exception:
            pass

    def open_settings():
        main_win.open_page(1)
        main_win.show()
        main_win.raise_()
        main_win.activateWindow()

    # 5. Create Mini Popup Window
    popup = BudoraPopupWindow(on_open_full_app=open_dashboard)

    def toggle_popup():
        if popup.isVisible():
            popup.hide()
        else:
            popup.show_animated()

    def on_exit():
        media_svc.stop()
        scanner.stop()
        app.quit()

    # 5. Tray Icon
    tray = BudoraTrayIcon(
        on_open_dashboard=open_dashboard,
        on_open_settings=open_settings,
        on_toggle_popup=toggle_popup,
        on_exit=on_exit
    )
    tray.show()

    # 6. IPC Local Server for future single-instance launches
    local_server = QLocalServer()
    local_server.removeServer(SOCKET_NAME)
    local_server.listen(SOCKET_NAME)

    def on_new_connection():
        client = local_server.nextPendingConnection()
        if client:
            client.waitForReadyRead(500)
            msg = client.readAll().data().decode("utf-8", errors="ignore")
            if "ACTIVATE" in msg:
                open_dashboard()
            client.close()

    local_server.newConnection.connect(on_new_connection)

    prev_connected = False
    prev_lid_open = False
    last_popup_time = 0.0
    disconnected_since = time.time()

    @Slot(BudoraState)
    def handle_state(state: BudoraState):
        nonlocal prev_connected, prev_lid_open, last_popup_time, disconnected_since
        tray.update_state(state)
        popup.update_state(state)
        main_win.update_state(state)

        now = time.time()
        if not state.is_connected:
            if disconnected_since is None:
                disconnected_since = now
            prev_connected = False
        else:
            became_connected = (disconnected_since is not None and (now - disconnected_since >= 5.0))
            lid_just_opened = (not prev_lid_open and state.is_lid_open)

            if (became_connected or lid_just_opened) and (now - last_popup_time > 15.0):
                last_popup_time = now
                if settings.get("showConnectPopup", True):
                    popup.show_animated()

            disconnected_since = None
            prev_connected = True
            prev_lid_open = state.is_lid_open

    bridge.state_received.connect(handle_state)

    def on_scanner_state(state: BudoraState):
        bridge.state_received.emit(state)

    scanner = BudoraScanner(on_state_changed=on_scanner_state)
    scanner.start()

    # 8. OPEN DASHBOARD ON STARTUP
    # User expects the app window to appear immediately when running run.bat or Budora.exe
    open_dashboard()

    sys.exit(app.exec())

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        import traceback
        log_file = os.path.join(os.path.dirname(__file__), "error.log")
        with open(log_file, "a", encoding="utf-8") as f:
            f.write(f"\n--- Crash at {time.ctime()} ---\n")
            traceback.print_exc(file=f)
        raise
