from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QGraphicsDropShadowEffect, QFrame
)
from PySide6.QtCore import Qt, QTimer, QRectF, QVariantAnimation, QEasingCurve
from PySide6.QtGui import QColor, QFont, QPainter, QPen, QBrush
from models import BudoraState
from config import settings, get_battery_color, BATTERY_COLORS
from ui_main_window import StyledLogoAvatar

class PopupBatteryRing(QWidget):
    """
    Speedometer rev-up circular gauge with crisp vector glyph inside the center.
    Eliminates awkward emoji baseline/overlap issues completely.
    """
    def __init__(self, size: int = 48, is_case: bool = False):
        super().__init__()
        self.setFixedSize(size, size)
        self.size_val = size
        self.is_case = is_case
        self.level = 0
        self.animated_level = 0
        self.color = BATTERY_COLORS["unknown"]
        self.is_charging = False
        self.is_dimmed = False

        self.anim = QVariantAnimation(self)
        self.anim.setDuration(750)
        self.anim.setEasingCurve(QEasingCurve.Type.OutCubic)
        self.anim.valueChanged.connect(self._on_anim_val)

    def _on_anim_val(self, val):
        self.animated_level = int(val)
        self.update()

    def animate_to_level(self, target: int):
        if target is None or target <= 0:
            self.animated_level = 0
            self.update()
            return
        self.anim.stop()
        self.anim.setStartValue(0)
        self.anim.setEndValue(min(100, max(0, target)))
        self.anim.start()

    def update_val(self, level, is_charging: bool, animate: bool = False, is_dimmed: bool = False):
        self.level = level if level is not None else 0
        self.is_charging = is_charging
        self.is_dimmed = is_dimmed
        self.color = get_battery_color(level)
        if animate and level is not None and level > 0:
            self.animate_to_level(level)
        else:
            self.animated_level = self.level
            self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)

        if self.is_dimmed:
            painter.setOpacity(0.40)
        else:
            painter.setOpacity(1.0)

        s = 4.5
        rect = QRectF(s / 2.0 + 2, s / 2.0 + 2, self.size_val - s - 4, self.size_val - s - 4)

        # 1. Background Track
        pen_bg = QPen(QColor("#1E212E"), s)
        pen_bg.setCapStyle(Qt.PenCapStyle.RoundCap)
        painter.setPen(pen_bg)
        painter.drawEllipse(rect)

        # 2. Progress Arc (rev-up value)
        curr_lvl = self.animated_level if self.anim.state() == QVariantAnimation.State.Running else self.level
        if curr_lvl > 0:
            c = get_battery_color(curr_lvl)
            pen_fg = QPen(QColor(c), s)
            pen_fg.setCapStyle(Qt.PenCapStyle.RoundCap)
            painter.setPen(pen_fg)

            start_angle = 90 * 16
            span_angle = -int((min(100, max(0, curr_lvl)) / 100.0) * 360 * 16)
            painter.drawArc(rect, start_angle, span_angle)

        # 3. Clean Center Vector Glyph
        cx, cy = self.size_val / 2.0, self.size_val / 2.0
        painter.setPen(Qt.PenStyle.NoPen)

        if self.is_case:
            # Case glyph
            brush_case = QBrush(QColor(self.color))
            painter.setBrush(brush_case)
            cw, ch = 20, 15
            painter.drawRoundedRect(QRectF(cx - cw/2, cy - ch/2 + 1, cw, ch), 4, 4)
            pen_gap = QPen(QColor("#08090F"), 1.5)
            painter.setPen(pen_gap)
            painter.drawLine(cx - cw/2, cy - 1, cx + cw/2, cy - 1)
        else:
            # Headphone glyph
            pen_glyph = QPen(QColor(self.color), 2.2)
            pen_glyph.setCapStyle(Qt.PenCapStyle.RoundCap)
            painter.setPen(pen_glyph)
            painter.drawArc(QRectF(cx - 10, cy - 12, 20, 16), 0 * 16, 180 * 16)
            brush_glyph = QBrush(QColor(self.color))
            painter.setBrush(brush_glyph)
            painter.setPen(Qt.PenStyle.NoPen)
            painter.drawRoundedRect(QRectF(cx - 12, cy - 4, 4.5, 9), 1.5, 1.5)
            painter.drawRoundedRect(QRectF(cx + 7.5, cy - 4, 4.5, 9), 1.5, 1.5)

        # 4. Charging Bolt Indicator
        if self.is_charging:
            bx = self.size_val - 18
            by = self.size_val - 18
            painter.setBrush(QBrush(QColor("#3DDC84")))
            painter.drawEllipse(QRectF(bx, by, 16, 16))
            painter.setPen(QColor("#08090F"))
            font_bolt = QFont("Segoe UI", 8, QFont.Weight.Bold)
            painter.setFont(font_bolt)
            painter.drawText(QRectF(bx, by, 16, 16), Qt.AlignmentFlag.AlignCenter, "⚡")

class ComponentCard(QFrame):
    def __init__(self, title: str, is_case: bool = False):
        super().__init__()
        self.is_case = is_case
        self.title_base = title
        self.setObjectName("compCard")
        self.setFixedHeight(132)
        self.setStyleSheet("""
            QFrame#compCard {
                background: #12141F;
                border-radius: 14px;
                border: 1px solid #232738;
            }
        """)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 10, 8, 10)
        layout.setSpacing(4)
        layout.setAlignment(Qt.AlignmentFlag.AlignCenter)

        self.ring = PopupBatteryRing(size=48, is_case=is_case)
        layout.addWidget(self.ring, alignment=Qt.AlignmentFlag.AlignCenter)

        self.lbl_text = QLabel(title)
        self.lbl_text.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_text.setStyleSheet("color: #8D92A8; font-size: 11px; font-weight: 700; letter-spacing: 0.5px; margin-top: 4px;")
        layout.addWidget(self.lbl_text)

        self.lbl_pct = QLabel("--")
        self.lbl_pct.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_pct.setStyleSheet("color: #565A6E; font-size: 15px; font-weight: 800; margin-top: 1px;")
        layout.addWidget(self.lbl_pct)

        self.lbl_time = QLabel("")
        self.lbl_time.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_time.setStyleSheet("color: #8D92A8; font-size: 9px; font-weight: 600;")
        self.lbl_time.hide()
        layout.addWidget(self.lbl_time)

    def set_state(self, level, is_charging, is_memorized=False, is_in_case=False, animate=False, force_blank=False, time_remaining=None):
        effective_level = level
        if not force_blank and effective_level is None and self.is_case:
            saved = settings.get("last_case_battery", 95)
            if saved:
                effective_level = saved
                is_memorized = True

        if time_remaining and effective_level is not None and effective_level > 0:
            self.lbl_time.setText(time_remaining)
            self.lbl_time.show()
        else:
            self.lbl_time.setText("")
            self.lbl_time.hide()

        if is_in_case:
            self.setStyleSheet("""
                QFrame#compCard {
                    background: #0D0F18;
                    border-radius: 14px;
                    border: 1px solid #1C2030;
                }
            """)
            self.lbl_text.setText(f"{self.title_base} (In Case)")
            self.lbl_text.setStyleSheet("color: #6FD0F5; font-size: 10px; font-weight: 700; margin-top: 4px;")
        else:
            self.setStyleSheet("""
                QFrame#compCard {
                    background: #12141F;
                    border-radius: 14px;
                    border: 1px solid #232738;
                }
            """)
            self.lbl_text.setText(self.title_base)
            self.lbl_text.setStyleSheet("color: #8D92A8; font-size: 11px; font-weight: 700; letter-spacing: 0.5px; margin-top: 4px;")

        if effective_level is None or force_blank:
            self.lbl_pct.setText("--")
            self.lbl_pct.setStyleSheet("color: #565A6E; font-size: 16px; font-weight: 800; margin-top: 2px;")
            self.ring.update_val(0, False, animate=False, is_dimmed=is_in_case)
        else:
            bolt = " ⚡" if is_charging else ""
            tilde = "~" if is_memorized else ""
            c = get_battery_color(effective_level)
            self.lbl_pct.setText(f"{tilde}{effective_level}%{bolt}")
            self.lbl_pct.setStyleSheet(f"color: {c}; font-size: 16px; font-weight: 800; margin-top: 2px;")
            self.ring.update_val(effective_level, is_charging, animate=animate, is_dimmed=is_in_case)

class BudoraPopupWindow(QWidget):
    def __init__(self, on_open_full_app=None):
        super().__init__()
        self.on_open_full_app = on_open_full_app
        self.setWindowFlags(
            Qt.WindowType.FramelessWindowHint |
            Qt.WindowType.WindowStaysOnTopHint |
            Qt.WindowType.Tool
        )
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        self.setFixedSize(430, 230)

        self.hide_timer = QTimer(self)
        self.hide_timer.setSingleShot(True)
        self.hide_timer.timeout.connect(self.hide)

        self._init_ui()

    def _init_ui(self):
        root_layout = QVBoxLayout(self)
        root_layout.setContentsMargins(8, 8, 8, 8)

        self.container = QFrame(self)
        self.container.setObjectName("container")
        self.container.setStyleSheet("""
            QFrame#container {
                background: #08090F;
                border: 1.5px solid #232738;
                border-radius: 18px;
            }
        """)

        shadow = QGraphicsDropShadowEffect(self)
        shadow.setBlurRadius(24)
        shadow.setColor(QColor(0, 0, 0, 210))
        shadow.setOffset(0, 6)
        self.container.setGraphicsEffect(shadow)

        layout = QVBoxLayout(self.container)
        layout.setContentsMargins(16, 12, 16, 14)
        layout.setSpacing(10)

        # 1. Header (Avatar, Title, Status, Open App Button, Close Button)
        hdr = QHBoxLayout()
        hdr.setSpacing(10)

        self.avatar = StyledLogoAvatar(size=36)
        hdr.addWidget(self.avatar)

        title_col = QVBoxLayout()
        title_col.setSpacing(2)

        self.lbl_name = QLabel("Budora")
        self.lbl_name.setStyleSheet("color: #FFFFFF; font-size: 15px; font-weight: 800;")
        title_col.addWidget(self.lbl_name)

        status_box = QHBoxLayout()
        status_box.setSpacing(5)
        self.dot = QLabel("●")
        self.dot.setStyleSheet("color: #3DDC84; font-size: 10px;")
        self.lbl_status = QLabel("Connected & Active")
        self.lbl_status.setStyleSheet("color: #3DDC84; font-size: 11px; font-weight: 600;")
        status_box.addWidget(self.dot)
        status_box.addWidget(self.lbl_status)
        status_box.addStretch()
        title_col.addLayout(status_box)

        hdr.addLayout(title_col)
        hdr.addStretch()

        self.btn_app = QPushButton("Open App →")
        self.btn_app.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_app.setStyleSheet("""
            QPushButton {
                background: #151722;
                color: #B9C0DA;
                font-size: 11px;
                font-weight: 600;
                border: 1px solid #282C40;
                border-radius: 8px;
                padding: 6px 12px;
            }
            QPushButton:hover {
                background: #252835;
                color: #3DDC84;
                border: 1px solid #3DDC84;
            }
        """)
        self.btn_app.clicked.connect(self._handle_open_full)
        hdr.addWidget(self.btn_app)

        self.btn_close = QPushButton("✕")
        self.btn_close.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_close.setFixedSize(28, 28)
        self.btn_close.setStyleSheet("""
            QPushButton {
                background: transparent;
                color: #8D92A8;
                font-size: 13px;
                font-weight: bold;
                border: none;
                border-radius: 14px;
            }
            QPushButton:hover {
                background: #1C2030;
                color: #FFFFFF;
            }
        """)
        self.btn_close.clicked.connect(self.hide)
        hdr.addWidget(self.btn_close)

        layout.addLayout(hdr)

        # 2. Vector Battery Cards (Left Pod, Case, Right Pod OR Single Headphone)
        cards_layout = QHBoxLayout()
        cards_layout.setSpacing(8)

        self.card_left = ComponentCard("Left Pod", is_case=False)
        self.card_case = ComponentCard("Case", is_case=True)
        self.card_right = ComponentCard("Right Pod", is_case=False)
        self.card_headphone = ComponentCard("Headphones", is_case=False)
        self.card_headphone.setMaximumWidth(200)
        self.card_headphone.hide()

        cards_layout.addWidget(self.card_left, 1)
        cards_layout.addWidget(self.card_case, 1)
        cards_layout.addWidget(self.card_right, 1)
        cards_layout.addWidget(self.card_headphone, 1)

        layout.addLayout(cards_layout)
        root_layout.addWidget(self.container)

    def _handle_open_full(self):
        self.hide()
        if self.on_open_full_app:
            self.on_open_full_app()

    def update_state(self, state: BudoraState):
        name = state.device_name or "CMF Buds 2a"
        self.lbl_name.setText(name)
        self.avatar.update()

        if not state.is_connected or (not state.is_single_device and state.left_battery is None and state.right_battery is None):
            self.lbl_status.setText("Disconnected")
            self.lbl_status.setStyleSheet("color: #565A6E; font-size: 11px; font-weight: 500;")
            self.dot.setStyleSheet("color: #565A6E; font-size: 10px;")
            if state.is_single_device:
                self.card_left.hide()
                self.card_case.hide()
                self.card_right.hide()
                self.card_headphone.show()
                self.card_headphone.set_state(None, False, force_blank=True)
            else:
                self.card_headphone.hide()
                self.card_left.show()
                self.card_case.show()
                self.card_right.show()
                self.card_left.set_state(None, False, force_blank=True)
                self.card_right.set_state(None, False, force_blank=True)
                self.card_case.set_state(None, False, force_blank=True)
            return

        rssi_str = f" • {state.rssi} dBm" if state.rssi != 0 else ""
        if state.is_lid_open or state.is_single_device or (state.left_battery is not None or state.right_battery is not None):
            self.lbl_status.setText(f"Connected & Active{rssi_str}")
            self.lbl_status.setStyleSheet("color: #3DDC84; font-size: 11px; font-weight: 600;")
            self.dot.setStyleSheet("color: #3DDC84; font-size: 10px;")
        else:
            self.lbl_status.setText(f"In Range{rssi_str}")
            self.lbl_status.setStyleSheet("color: #8D92A8; font-size: 11px; font-weight: 500;")
            self.dot.setStyleSheet("color: #8D92A8; font-size: 10px;")

        if state.is_single_device:
            self.card_left.hide()
            self.card_case.hide()
            self.card_right.hide()
            self.card_headphone.show()
            low = (state.device_name or "").lower()
            if any(k in low for k in ["speaker", "box", "sound", "stone", "bar", "ws-", "boom"]):
                self.card_headphone.title_base = "Speaker"
            else:
                self.card_headphone.title_base = "Headphones"
            lvl = state.left_battery or state.right_battery
            chg = state.is_left_charging or state.is_right_charging
            self.card_headphone.set_state(
                lvl, chg,
                force_blank=(lvl is None and not state.is_connected),
                time_remaining=state.single_time_remaining
            )
            if state.is_connected and lvl is None:
                self.card_headphone.lbl_text.setText(self.card_headphone.title_base)
                self.card_headphone.lbl_pct.setText("--")
                self.card_headphone.lbl_pct.setStyleSheet("color: #3DDC84; font-size: 16px; font-weight: 800; margin-top: 2px;")
        else:
            self.card_headphone.hide()
            self.card_left.show()
            self.card_case.show()
            self.card_right.show()
            self.card_left.set_state(
                state.left_battery, state.is_left_charging,
                is_in_case=state.is_left_in_case,
                time_remaining=state.left_time_remaining
            )
            self.card_right.set_state(
                state.right_battery, state.is_right_charging,
                is_in_case=state.is_right_in_case,
                time_remaining=state.right_time_remaining
            )

            effective_case = state.case_battery
            is_mem = state.case_is_memorized
            if effective_case is None:
                saved = settings.get("last_case_battery", 95)
                if saved:
                    effective_case = saved
                    is_mem = True

            self.card_case.set_state(
                effective_case,
                state.is_case_charging,
                is_memorized=is_mem
            )

    def show_animated(self):
        from PySide6.QtGui import QGuiApplication
        screen = QGuiApplication.primaryScreen().availableGeometry()
        margin = 16
        x = screen.x() + screen.width() - self.width() - margin
        y = screen.y() + screen.height() - self.height() - margin
        self.move(x, y)

        # Trigger rev-up speedometer animation on show
        self.card_left.ring.animate_to_level(self.card_left.ring.level)
        self.card_case.ring.animate_to_level(self.card_case.ring.level)
        self.card_right.ring.animate_to_level(self.card_right.ring.level)
        self.card_headphone.ring.animate_to_level(self.card_headphone.ring.level)

        self.show()
        self.raise_()
        self.activateWindow()

        timeout_sec = settings.get("popupTimeoutSeconds", 8)
        if timeout_sec > 0:
            self.hide_timer.setInterval(timeout_sec * 1000)
            self.hide_timer.start()
        else:
            self.hide_timer.stop()
