from PySide6.QtWidgets import QSystemTrayIcon, QMenu
from PySide6.QtGui import QIcon, QPixmap, QPainter, QColor, QPen, QBrush, QFont
from PySide6.QtCore import Qt
from models import BudoraState

def create_tray_icon(accent_color: str = "#3DDC84") -> QIcon:
    pixmap = QPixmap(64, 64)
    pixmap.fill(Qt.GlobalColor.transparent)

    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)

    pen = QPen(QColor(accent_color), 5)
    pen.setCapStyle(Qt.PenCapStyle.RoundCap)
    painter.setPen(pen)

    painter.drawArc(12, 10, 40, 40, 0 * 16, 180 * 16)

    brush = QBrush(QColor(accent_color))
    painter.setBrush(brush)
    painter.setPen(Qt.PenStyle.NoPen)
    painter.drawRoundedRect(8, 28, 10, 22, 4, 4)
    painter.drawRoundedRect(46, 28, 10, 22, 4, 4)

    painter.end()
    return QIcon(pixmap)

class BudoraTrayIcon(QSystemTrayIcon):
    def __init__(self, on_open_dashboard, on_open_settings, on_toggle_popup, on_exit):
        super().__init__()
        self.on_open_dashboard = on_open_dashboard
        self.on_open_settings = on_open_settings
        self.on_toggle_popup = on_toggle_popup
        self.on_exit = on_exit

        self.setIcon(create_tray_icon("#3DDC84"))
        self.setToolTip("Budora - Earbuds Battery Monitor")

        self._init_menu()
        self.activated.connect(self._on_activated)

    def _init_menu(self):
        menu = QMenu()
        menu.setStyleSheet("""
            QMenu {
                background-color: #1E1E24;
                color: #FFFFFF;
                border: 1px solid #333842;
                border-radius: 8px;
                padding: 4px;
            }
            QMenu::item {
                padding: 6px 18px;
                border-radius: 4px;
            }
            QMenu::item:selected {
                background-color: #2F333D;
            }
        """)

        # Open Dashboard (Bold default)
        self.action_dash = menu.addAction("📊  Open Budora Dashboard")
        font = self.action_dash.font()
        font.setBold(True)
        self.action_dash.setFont(font)
        self.action_dash.triggered.connect(self.on_open_dashboard)

        self.action_settings = menu.addAction("⚙️  Settings")
        self.action_settings.triggered.connect(self.on_open_settings)

        self.action_show = menu.addAction("🪟  Show Mini Popup")
        self.action_show.triggered.connect(self.on_toggle_popup)

        menu.addSeparator()

        self.action_status = menu.addAction("Status: Scanning...")
        self.action_status.setEnabled(False)

        menu.addSeparator()

        self.action_exit = menu.addAction("Exit Budora")
        self.action_exit.triggered.connect(self.on_exit)

        self.setContextMenu(menu)

    def _on_activated(self, reason):
        if reason in (QSystemTrayIcon.ActivationReason.Trigger, QSystemTrayIcon.ActivationReason.DoubleClick):
            self.on_open_dashboard()

    def update_state(self, state: BudoraState):
        if not state.is_connected:
            self.setIcon(create_tray_icon("#565A6E"))
            self.setToolTip(f"Budora: {state.device_name} (Disconnected)")
            self.action_status.setText(f"{state.device_name}: Disconnected")
        else:
            self.setIcon(create_tray_icon("#3DDC84"))
            summary = state.summary()
            self.setToolTip(f"Budora: {state.device_name}\n{summary}")
            self.action_status.setText(f"{state.device_name}: {summary}")
