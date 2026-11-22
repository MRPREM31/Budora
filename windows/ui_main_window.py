import os
from PySide6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QStackedWidget, QRadioButton, QButtonGroup, QCheckBox, QFrame,
    QScrollArea, QFileDialog, QGraphicsDropShadowEffect, QGraphicsOpacityEffect, QSizePolicy
)
from PySide6.QtCore import Qt, QRectF, QVariantAnimation, QEasingCurve, QTimer
from PySide6.QtGui import (
    QColor, QFont, QPainter, QPen, QBrush, QPainterPath, QPixmap
)
from models import BudoraState
from config import settings, THEMES, BATTERY_COLORS, get_battery_color

class StyledLogoAvatar(QWidget):
    """Corresponds to AppLogoManager.kt: renders the custom logo or default headphone in Round/Side-Round/Square shape"""
    def __init__(self, size: int = 42):
        super().__init__()
        self.avatar_size = size
        self.setFixedSize(size, size)

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform)

        s = float(self.avatar_size)
        shape_type = settings.get("logoShape", "round")

        path = QPainterPath()
        if shape_type == "round":
            path.addEllipse(0, 0, s, s)
        elif shape_type == "side_round":
            path.addRoundedRect(0, 0, s, s, s * 0.22, s * 0.22)
        else:
            path.addRoundedRect(0, 0, s, s, 4, 4)

        painter.setClipPath(path)

        logo_path = settings.get_custom_logo_path()
        if logo_path and os.path.exists(logo_path):
            pix = QPixmap(logo_path)
            if not pix.isNull():
                painter.drawPixmap(0, 0, int(s), int(s), pix.scaled(
                    int(s), int(s),
                    Qt.AspectRatioMode.KeepAspectRatioByExpanding,
                    Qt.TransformationMode.SmoothTransformation
                ))
            else:
                self._draw_default(painter, s)
        else:
            self._draw_default(painter, s)

        painter.setClipping(False)
        pen_border = QPen(QColor("#2B3040"), 1.5)
        painter.setPen(pen_border)
        painter.setBrush(Qt.BrushStyle.NoBrush)
        if shape_type == "round":
            painter.drawEllipse(0.75, 0.75, s - 1.5, s - 1.5)
        elif shape_type == "side_round":
            painter.drawRoundedRect(0.75, 0.75, s - 1.5, s - 1.5, s * 0.22, s * 0.22)
        else:
            painter.drawRoundedRect(0.75, 0.75, s - 1.5, s - 1.5, 4, 4)

    def _draw_default(self, painter, s):
        painter.fillRect(0, 0, int(s), int(s), QColor("#151722"))
        font = QFont("Segoe UI", int(s * 0.44))
        painter.setFont(font)
        painter.setPen(QColor("#B9C0DA"))
        painter.drawText(QRectF(0, 0, s, s), Qt.AlignmentFlag.AlignCenter, "🎧")

class AndroidBatteryRing(QWidget):
    """
    Circular gauge with dynamic Speedometer Rev-Up Animation (0% -> target%).
    """
    def __init__(self, diameter: int = 96, stroke: float = 6.5):
        super().__init__()
        self.diameter = diameter
        self.stroke = stroke
        self.setFixedSize(diameter, diameter)
        self.level = None
        self.animated_level = 0
        self.color = BATTERY_COLORS["unknown"]
        self.is_charging = False
        self.is_case = False
        self.is_lid_open = False

        self.anim = QVariantAnimation(self)
        self.anim.setDuration(800)
        self.anim.setEasingCurve(QEasingCurve.Type.OutCubic)
        self.anim.valueChanged.connect(self._on_anim_val)

    def _on_anim_val(self, val):
        self.animated_level = int(val)
        self.update()

    def animate_to_level(self, target_level: int):
        if target_level is None or target_level <= 0:
            self.animated_level = 0
            self.update()
            return
        self.anim.stop()
        self.anim.setStartValue(0)
        self.anim.setEndValue(min(100, max(0, target_level)))
        self.anim.start()

    def update_val(self, level, is_charging=False, is_case=False, is_lid_open=False, animate=False):
        self.level = level
        self.is_charging = is_charging
        self.is_case = is_case
        self.is_lid_open = is_lid_open
        self.color = get_battery_color(level)

        if animate and level is not None and level > 0:
            self.animate_to_level(level)
        else:
            self.animated_level = level if level is not None else 0
            self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)

        s = self.stroke
        r = QRectF(s / 2.0 + 2, s / 2.0 + 2, self.diameter - s - 4, self.diameter - s - 4)

        # 1. Background Track Ring
        pen_track = QPen(QColor("#1A1D28"), s)
        pen_track.setCapStyle(Qt.PenCapStyle.RoundCap)
        painter.setPen(pen_track)
        painter.drawEllipse(r)

        # 2. Foreground Progress Arc (uses animated_level for rev-up effect)
        curr_lvl = self.animated_level if self.anim.state() == QVariantAnimation.State.Running else (self.level or 0)
        if curr_lvl > 0:
            c = get_battery_color(curr_lvl)
            pen_arc = QPen(QColor(c), s)
            pen_arc.setCapStyle(Qt.PenCapStyle.RoundCap)
            painter.setPen(pen_arc)

            start_angle = 90 * 16
            span_angle = -int((min(100, max(0, curr_lvl)) / 100.0) * 360 * 16)
            painter.drawArc(r, start_angle, span_angle)

        painter.setPen(Qt.PenStyle.NoPen)
        cx, cy = self.diameter / 2.0, self.diameter / 2.0

        if self.is_case:
            brush_case = QBrush(QColor(self.color))
            painter.setBrush(brush_case)
            cw, ch = 22, 17
            painter.drawRoundedRect(QRectF(cx - cw/2, cy - ch/2 + 1, cw, ch), 4, 4)
            pen_gap = QPen(QColor("#08090F"), 1.8)
            painter.setPen(pen_gap)
            painter.drawLine(cx - cw/2, cy - 1, cx + cw/2, cy - 1)
        else:
            pen_glyph = QPen(QColor(self.color), 2.6)
            pen_glyph.setCapStyle(Qt.PenCapStyle.RoundCap)
            painter.setPen(pen_glyph)
            painter.drawArc(QRectF(cx - 11, cy - 14, 22, 18), 0 * 16, 180 * 16)
            brush_glyph = QBrush(QColor(self.color))
            painter.setBrush(brush_glyph)
            painter.setPen(Qt.PenStyle.NoPen)
            painter.drawRoundedRect(QRectF(cx - 13.5, cy - 4, 4.5, 11), 1.5, 1.5)
            painter.drawRoundedRect(QRectF(cx + 9, cy - 4, 4.5, 11), 1.5, 1.5)

        if self.is_charging:
            bx = self.diameter - 22
            by = self.diameter - 22
            painter.setBrush(QBrush(QColor("#08090F")))
            painter.drawEllipse(QRectF(bx - 2, by - 2, 22, 22))
            painter.setBrush(QBrush(QColor("#3DDC84")))
            painter.drawEllipse(QRectF(bx, by, 18, 18))
            painter.setPen(QColor("#08090F"))
            font_bolt = QFont("Segoe UI", 9, QFont.Weight.Bold)
            painter.setFont(font_bolt)
            painter.drawText(QRectF(bx, by, 18, 18), Qt.AlignmentFlag.AlignCenter, "⚡")

class EarbudCard(QFrame):
    """Card container for each earbud with responsive scaling, in-case dimming, and rev-up animation"""
    def __init__(self, label: str):
        super().__init__()
        self.setObjectName("earbudCard")
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Preferred)
        self.opacity_effect = QGraphicsOpacityEffect(self)
        self.setGraphicsEffect(self.opacity_effect)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 12, 12, 14)
        layout.setSpacing(6)
        layout.setAlignment(Qt.AlignmentFlag.AlignCenter)

        self.ring = AndroidBatteryRing(diameter=96, stroke=6.5)
        self.ring.anim.valueChanged.connect(self._on_anim_step)
        layout.addWidget(self.ring, alignment=Qt.AlignmentFlag.AlignCenter)

        self.lbl_level = QLabel("--")
        self.lbl_level.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_level.setStyleSheet("font-size: 22px; font-weight: 800; color: #565A6E;")
        layout.addWidget(self.lbl_level, alignment=Qt.AlignmentFlag.AlignCenter)

        self.lbl_tag = QLabel(label.upper())
        self.lbl_tag.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_tag.setStyleSheet("font-size: 10px; font-weight: 700; color: #8D92A8; letter-spacing: 1.1px;")
        layout.addWidget(self.lbl_tag, alignment=Qt.AlignmentFlag.AlignCenter)

        self.badge_in_case = QLabel("⚡ DOCKED IN CASE")
        self.badge_in_case.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.badge_in_case.setStyleSheet("""
            background: #1C2333;
            color: #6FD0F5;
            font-size: 9px;
            font-weight: 700;
            padding: 2px 8px;
            border-radius: 6px;
            border: 1px solid #2B3A4F;
        """)
        self.badge_in_case.hide()
        layout.addWidget(self.badge_in_case, alignment=Qt.AlignmentFlag.AlignCenter)

    def _on_anim_step(self, val):
        c = get_battery_color(int(val))
        self.lbl_level.setText(f"{int(val)}%")
        self.lbl_level.setStyleSheet(f"font-size: 22px; font-weight: 800; color: {c};")

    def set_state(self, level, is_charging, is_in_case=False, animate=False):
        self.ring.update_val(level, is_charging=is_charging, is_case=False, animate=animate)
        if is_in_case:
            self.opacity_effect.setOpacity(0.40)
            self.badge_in_case.show()
        else:
            self.opacity_effect.setOpacity(1.0)
            self.badge_in_case.hide()

        if level is None:
            self.lbl_level.setText("--")
            self.lbl_level.setStyleSheet("font-size: 22px; font-weight: 800; color: #565A6E;")
        elif not animate:
            c = get_battery_color(level)
            self.lbl_level.setText(f"{level}%")
            self.lbl_level.setStyleSheet(f"font-size: 22px; font-weight: 800; color: {c};")

class HeadphoneCard(QFrame):
    """Unified prominent card for single headphones / neckbands with animated gauge"""
    def __init__(self):
        super().__init__()
        self.setObjectName("headphoneCard")
        self.setStyleSheet("""
            QFrame#headphoneCard {
                background: #10121C;
                border-radius: 18px;
                border: 1px solid #232738;
            }
        """)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 20, 18, 20)
        layout.setSpacing(8)
        layout.setAlignment(Qt.AlignmentFlag.AlignCenter)

        self.ring = AndroidBatteryRing(diameter=104, stroke=7.0)
        self.ring.anim.valueChanged.connect(self._on_anim_step)
        layout.addWidget(self.ring, alignment=Qt.AlignmentFlag.AlignCenter)

        self.lbl_level = QLabel("--")
        self.lbl_level.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_level.setStyleSheet("font-size: 26px; font-weight: 800; color: #565A6E;")
        layout.addWidget(self.lbl_level, alignment=Qt.AlignmentFlag.AlignCenter)

        self.lbl_title = QLabel("WIRELESS HEADPHONES / NECKBAND")
        self.lbl_title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_title.setStyleSheet("font-size: 11px; font-weight: 700; color: #8D92A8; letter-spacing: 1.2px;")
        layout.addWidget(self.lbl_title, alignment=Qt.AlignmentFlag.AlignCenter)

        self.lbl_status = QLabel("Connected & Active")
        self.lbl_status.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.lbl_status.setStyleSheet("font-size: 11px; font-weight: 600; color: #3DDC84;")
        layout.addWidget(self.lbl_status, alignment=Qt.AlignmentFlag.AlignCenter)

    def set_device_name(self, name: str):
        low = name.lower()
        if any(k in low for k in ["speaker", "box", "sound", "stone", "bar", "ws-", "boom", "party"]):
            self.lbl_title.setText("BLUETOOTH AUDIO SPEAKER")
        else:
            self.lbl_title.setText("WIRELESS HEADPHONES / NECKBAND")

    def _on_anim_step(self, val):
        c = get_battery_color(int(val))
        self.lbl_level.setText(f"{int(val)}%")
        self.lbl_level.setStyleSheet(f"font-size: 26px; font-weight: 800; color: {c};")

    def set_state(self, level, is_charging, is_connected=True, animate=False):
        self.ring.update_val(level, is_charging=is_charging, is_case=False, animate=animate)
        if not is_connected:
            self.lbl_level.setText("--")
            self.lbl_level.setStyleSheet("font-size: 26px; font-weight: 800; color: #565A6E;")
            self.lbl_status.setText("Disconnected")
            self.lbl_status.setStyleSheet("font-size: 11px; font-weight: 500; color: #565A6E;")
        elif level is None:
            self.lbl_level.setText("--")
            self.lbl_level.setStyleSheet("font-size: 26px; font-weight: 800; color: #3DDC84;")
            self.lbl_status.setText("Connected & Active")
            self.lbl_status.setStyleSheet("font-size: 11px; font-weight: 600; color: #3DDC84;")
        else:
            c = get_battery_color(level)
            bolt = " ⚡" if is_charging else ""
            if not animate:
                self.lbl_level.setText(f"{level}%{bolt}")
                self.lbl_level.setStyleSheet(f"font-size: 26px; font-weight: 800; color: {c};")
            status_txt = "Charging via Power" if is_charging else "Connected & Active"
            self.lbl_status.setText(status_txt)
            self.lbl_status.setStyleSheet(f"font-size: 11px; font-weight: 600; color: {c};")

class CaseCard(QFrame):
    """Charging case card with Smart Case Memory and tachometer animation"""
    def __init__(self):
        super().__init__()
        self.setObjectName("caseCard")
        self.setFixedHeight(78)
        self._is_mem = False

        layout = QHBoxLayout(self)
        layout.setContentsMargins(14, 10, 16, 10)
        layout.setSpacing(14)

        self.ring = AndroidBatteryRing(diameter=48, stroke=4.5)
        self.ring.anim.valueChanged.connect(self._on_anim_step)
        layout.addWidget(self.ring)

        mid_col = QVBoxLayout()
        mid_col.setSpacing(3)
        mid_col.setAlignment(Qt.AlignmentFlag.AlignVCenter)

        tag_row = QHBoxLayout()
        tag_row.setSpacing(8)

        lbl_title = QLabel("CHARGING CASE")
        lbl_title.setStyleSheet("font-size: 11px; font-weight: 700; color: #8D92A8; letter-spacing: 1.2px;")
        tag_row.addWidget(lbl_title)

        self.badge_lid = QLabel("LID OPEN")
        self.badge_lid.setStyleSheet("""
            background: #1C2333;
            color: #6FD0F5;
            font-size: 9px;
            font-weight: bold;
            padding: 2px 6px;
            border-radius: 5px;
        """)
        self.badge_lid.hide()
        tag_row.addWidget(self.badge_lid)

        self.badge_charging = QLabel("CHARGING ⚡")
        self.badge_charging.setStyleSheet("""
            background: #172A1D;
            color: #3DDC84;
            font-size: 9px;
            font-weight: bold;
            padding: 2px 6px;
            border-radius: 5px;
        """)
        self.badge_charging.hide()
        tag_row.addWidget(self.badge_charging)

        mid_col.addLayout(tag_row)

        self.lbl_status = QLabel("Smart Case Memory • Ready")
        self.lbl_status.setSizePolicy(QSizePolicy.Policy.Ignored, QSizePolicy.Policy.Preferred)
        self.lbl_status.setStyleSheet("font-size: 11px; color: #8D92A8;")
        mid_col.addWidget(self.lbl_status)

        layout.addLayout(mid_col)
        layout.addStretch()

        self.lbl_level = QLabel("--")
        self.lbl_level.setStyleSheet("font-size: 22px; font-weight: 800; color: #565A6E;")
        layout.addWidget(self.lbl_level)

    def _on_anim_step(self, val):
        c = get_battery_color(int(val))
        prefix = "~" if self._is_mem else ""
        self.lbl_level.setText(f"{prefix}{int(val)}%")
        self.lbl_level.setStyleSheet(f"font-size: 22px; font-weight: 800; color: {c};")

    def set_state(self, level, is_charging, is_lid_open, is_memorized=False, animate=False, force_blank=False):
        self._is_mem = is_memorized
        if force_blank:
            effective_level = None
            self._is_mem = False
        else:
            effective_level = level
            if effective_level is None:
                saved = settings.get("last_case_battery", 95)
                if saved:
                    effective_level = saved
                    self._is_mem = True

        self.ring.update_val(effective_level, is_charging=is_charging and not force_blank, is_case=True, is_lid_open=is_lid_open and not force_blank, animate=animate)

        if is_lid_open and not force_blank:
            self.badge_lid.show()
        else:
            self.badge_lid.hide()

        if is_charging and not force_blank:
            self.badge_charging.show()
        else:
            self.badge_charging.hide()

        if effective_level is None or force_blank:
            self.lbl_level.setText("--")
            self.lbl_level.setStyleSheet("font-size: 22px; font-weight: 800; color: #565A6E;")
            self.lbl_status.setText("Case radio offline / disconnected")
        elif not animate:
            c = get_battery_color(effective_level)
            prefix = "~" if self._is_mem else ""
            self.lbl_level.setText(f"{prefix}{effective_level}%")
            self.lbl_level.setStyleSheet(f"font-size: 22px; font-weight: 800; color: {c};")

            if is_charging:
                self.lbl_status.setText("Charging via USB / wireless dock")
            elif self._is_mem:
                self.lbl_status.setText("Smart Case Memory • Last verified reading")
            else:
                self.lbl_status.setText("Live Bluetooth BLE reading")

class NowPlayingCard(QFrame):
    """Modern Windows Fluent Media Card with high-contrast platform branding and balanced transport controls"""
    def __init__(self, on_toggle=None, on_next=None, on_prev=None):
        super().__init__()
        self.on_toggle = on_toggle
        self.on_next = on_next
        self.on_prev = on_prev
        self.setObjectName("cardContainer")

        layout = QHBoxLayout(self)
        layout.setContentsMargins(14, 12, 14, 12)
        layout.setSpacing(12)

        # 1. Left Platform Badge
        self.badge_app = QLabel("🎧")
        self.badge_app.setFixedSize(40, 40)
        self.badge_app.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.badge_app.setStyleSheet("""
            background: #181A26;
            border-radius: 10px;
            font-size: 18px;
            border: 1px solid #282C40;
        """)
        layout.addWidget(self.badge_app)

        # 2. Middle Metadata (strictly bounded so it never forces horizontal scrolling)
        col_meta = QVBoxLayout()
        col_meta.setSpacing(2)

        self.lbl_tag = QLabel("MEDIA PLAYER")
        self.lbl_tag.setStyleSheet("color: #6C7280; font-size: 9px; font-weight: 800; letter-spacing: 1.1px;")
        col_meta.addWidget(self.lbl_tag)

        self.lbl_title = QLabel("No active audio")
        self.lbl_title.setSizePolicy(QSizePolicy.Policy.Ignored, QSizePolicy.Policy.Preferred)
        self.lbl_title.setStyleSheet("font-size: 13px; font-weight: 700; color: #FFFFFF;")
        col_meta.addWidget(self.lbl_title)

        self.lbl_artist = QLabel("Play audio on YouTube, Spotify, or any media app")
        self.lbl_artist.setSizePolicy(QSizePolicy.Policy.Ignored, QSizePolicy.Policy.Preferred)
        self.lbl_artist.setStyleSheet("font-size: 10px; font-weight: 500; color: #8D92A8;")
        col_meta.addWidget(self.lbl_artist)

        layout.addLayout(col_meta, 1)

        # 3. Right Transport Controls
        controls = QHBoxLayout()
        controls.setSpacing(6)

        self.btn_prev = QPushButton("⏮")
        self.btn_prev.setFixedSize(34, 34)
        self.btn_prev.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_prev.setStyleSheet("""
            QPushButton {
                background: #181A28;
                color: #B9C0DA;
                font-size: 12px;
                border: 1px solid #262B3D;
                border-radius: 17px;
            }
            QPushButton:hover {
                background: #252838;
                color: #FFFFFF;
                border: 1px solid #3DDC84;
            }
        """)
        if on_prev:
            self.btn_prev.clicked.connect(on_prev)
        controls.addWidget(self.btn_prev)

        self.btn_play_pause = QPushButton("▶")
        self.btn_play_pause.setFixedSize(38, 38)
        self.btn_play_pause.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_play_pause.setStyleSheet("""
            QPushButton {
                background: #3DDC84;
                color: #08090F;
                font-size: 14px;
                font-weight: bold;
                border: none;
                border-radius: 19px;
            }
            QPushButton:hover {
                background: #4EF297;
            }
        """)
        if on_toggle:
            self.btn_play_pause.clicked.connect(on_toggle)
        controls.addWidget(self.btn_play_pause)

        self.btn_next = QPushButton("⏭")
        self.btn_next.setFixedSize(34, 34)
        self.btn_next.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_next.setStyleSheet("""
            QPushButton {
                background: #181A28;
                color: #B9C0DA;
                font-size: 12px;
                border: 1px solid #262B3D;
                border-radius: 17px;
            }
            QPushButton:hover {
                background: #252838;
                color: #FFFFFF;
                border: 1px solid #3DDC84;
            }
        """)
        if on_next:
            self.btn_next.clicked.connect(on_next)
        controls.addWidget(self.btn_next)

        layout.addLayout(controls)

    def update_media(self, data: dict):
        if not data.get("active", False):
            self.lbl_tag.setText("MEDIA PLAYER")
            self.lbl_tag.setStyleSheet("color: #6C7280; font-size: 9px; font-weight: 800; letter-spacing: 1.1px;")
            self.lbl_title.setText("No active audio")
            self.lbl_artist.setText("Play audio on YouTube, Spotify, or any media app")
            self.badge_app.setText("🎧")
            self.badge_app.setStyleSheet("""
                background: #181A26;
                color: #B9C0DA;
                border-radius: 10px;
                font-size: 18px;
                border: 1px solid #282C40;
            """)
            self.btn_play_pause.setText("▶")
            self.btn_play_pause.setEnabled(False)
            self.btn_prev.setEnabled(False)
            self.btn_next.setEnabled(False)
            return

        source = data.get("source", "Media")
        title = data.get("title", "Unknown Track")
        artist = data.get("artist", "")
        is_playing = data.get("is_playing", False)

        if "YouTube" in source:
            self.badge_app.setText("▶")
            self.badge_app.setStyleSheet("""
                background: #281215;
                color: #FF3B30;
                border-radius: 10px;
                font-size: 16px;
                font-weight: 900;
                border: 1px solid #5C1D24;
            """)
            self.lbl_tag.setText("● YOUTUBE")
            self.lbl_tag.setStyleSheet("color: #FF4D4D; font-size: 9px; font-weight: 800; letter-spacing: 1.1px;")
        elif "Spotify" in source:
            self.badge_app.setText("♫")
            self.badge_app.setStyleSheet("""
                background: #0E2416;
                color: #1DB954;
                border-radius: 10px;
                font-size: 17px;
                font-weight: bold;
                border: 1px solid #1E5C2F;
            """)
            self.lbl_tag.setText("● SPOTIFY")
            self.lbl_tag.setStyleSheet("color: #1DB954; font-size: 9px; font-weight: 800; letter-spacing: 1.1px;")
        elif "Chrome" in source or "Edge" in source:
            self.badge_app.setText("🌐")
            self.badge_app.setStyleSheet("""
                background: #121D2C;
                color: #60A5FA;
                border-radius: 10px;
                font-size: 16px;
                border: 1px solid #1E385B;
            """)
            self.lbl_tag.setText(f"● {source.upper()}")
            self.lbl_tag.setStyleSheet("color: #60A5FA; font-size: 9px; font-weight: 800; letter-spacing: 1.1px;")
        else:
            self.badge_app.setText("🎵")
            self.badge_app.setStyleSheet("""
                background: #181A26;
                color: #A78BFA;
                border-radius: 10px;
                font-size: 16px;
                border: 1px solid #332B54;
            """)
            self.lbl_tag.setText(f"● {source.upper()}")
            self.lbl_tag.setStyleSheet("color: #A78BFA; font-size: 9px; font-weight: 800; letter-spacing: 1.1px;")

        # Elide long titles cleanly to prevent expanding window horizontally
        if len(title) > 36:
            title = title[:34] + "…"
        self.lbl_title.setText(title)

        if len(artist) > 36:
            artist = artist[:34] + "…"
        self.lbl_artist.setText(artist)

        self.btn_play_pause.setEnabled(True)
        self.btn_prev.setEnabled(data.get("can_prev", True))
        self.btn_next.setEnabled(data.get("can_next", True))
        self.btn_play_pause.setText("⏸" if is_playing else "▶")

class BudoraMainWindow(QMainWindow):
    def __init__(self, on_trigger_popup, on_media_toggle=None, on_media_next=None, on_media_prev=None, on_media_refresh=None):
        super().__init__()
        self.on_trigger_popup = on_trigger_popup
        self.on_media_toggle = on_media_toggle
        self.on_media_next = on_media_next
        self.on_media_prev = on_media_prev
        self.on_media_refresh = on_media_refresh
        self._latest_state = None

        self.setWindowTitle("Budora")
        self.resize(700, 600)
        self.setMinimumSize(380, 420)  # Fits small screens without horizontal scrollbar

        self._apply_theme()
        self._init_ui()

    def _apply_theme(self):
        pal = settings.get_theme_colors()
        self.setStyleSheet(f"""
            QMainWindow {{
                background-color: {pal['bg']};
            }}
            QWidget {{
                color: {pal['text']};
                font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, sans-serif;
            }}
            QScrollArea {{
                border: none;
                background: transparent;
            }}
            QFrame#earbudCard, QFrame#caseCard, QFrame#cardContainer {{
                background-color: {pal['surface']};
                border-radius: 16px;
                border: 1px solid {pal['border']};
            }}
            QFrame#settingRow {{
                background-color: {pal['card']};
                border-radius: 14px;
                border: 1px solid {pal['border']};
                padding: 14px 16px;
            }}
            QFrame#capsuleFrame {{
                background-color: {pal['surface']};
                border-radius: 20px;
                border: 1px solid {pal['border']};
            }}
        """)

    def _init_ui(self):
        root = QWidget()
        self.setCentralWidget(root)
        main_layout = QVBoxLayout(root)
        main_layout.setContentsMargins(0, 0, 0, 0)
        main_layout.setSpacing(0)

        self.pages = QStackedWidget()
        self.dashboard_page = self._create_dashboard_page()
        self.settings_page = self._create_settings_page()

        self.pages.addWidget(self.dashboard_page)
        self.pages.addWidget(self.settings_page)

        main_layout.addWidget(self.pages)

    def _create_dashboard_page(self) -> QWidget:
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        scroll.setStyleSheet("background: transparent; border: none;")
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        scroll.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)

        page = QWidget()
        layout = QVBoxLayout(page)
        layout.setContentsMargins(18, 16, 18, 20)
        layout.setSpacing(14)

        # 1. Header with customizable Avatar Logo + Live Status + Refresh + Settings
        header = QHBoxLayout()

        self.avatar_logo = StyledLogoAvatar(size=42)
        header.addWidget(self.avatar_logo)
        header.addSpacing(10)

        col_title = QVBoxLayout()
        col_title.setSpacing(2)
        lbl_title = QLabel("Budora")
        lbl_title.setStyleSheet("font-size: 19px; font-weight: 800; color: #FFFFFF;")
        col_title.addWidget(lbl_title)

        status_row = QHBoxLayout()
        status_row.setSpacing(6)
        self.dot = QLabel("●")
        self.dot.setStyleSheet("color: #3DDC84; font-size: 11px;")
        self.lbl_status = QLabel("Searching for earbuds...")
        self.lbl_status.setStyleSheet("color: #8D92A8; font-size: 12px; font-weight: 500;")
        status_row.addWidget(self.dot)
        status_row.addWidget(self.lbl_status)
        status_row.addStretch()
        col_title.addLayout(status_row)
        header.addLayout(col_title)

        header.addStretch()

        # REFRESH BUTTON with Speedometer Needle Rev-Up Trigger
        self.btn_refresh = QPushButton("🔄 Refresh")
        self.btn_refresh.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_refresh.setStyleSheet("""
            QPushButton {
                background: #151722;
                color: #B9C0DA;
                font-size: 12px;
                font-weight: 600;
                border-radius: 18px;
                border: 1px solid #282C40;
                padding: 6px 14px;
            }
            QPushButton:hover {
                background: #252835;
                color: #3DDC84;
                border: 1px solid #3DDC84;
            }
        """)
        self.btn_refresh.clicked.connect(self.trigger_refresh)
        header.addWidget(self.btn_refresh)
        header.addSpacing(8)

        # Settings Gear Button
        self.btn_gear = QPushButton("⚙️")
        self.btn_gear.setFixedSize(36, 36)
        self.btn_gear.setCursor(Qt.CursorShape.PointingHandCursor)
        self.btn_gear.setStyleSheet("""
            QPushButton {
                background: #151722;
                color: #B9C0DA;
                font-size: 15px;
                border-radius: 18px;
                border: 1px solid #2B3040;
            }
            QPushButton:hover {
                background: #252835;
                color: #FFFFFF;
                border: 1px solid #3DDC84;
            }
        """)
        self.btn_gear.clicked.connect(lambda: self.pages.setCurrentIndex(1))
        header.addWidget(self.btn_gear)

        layout.addLayout(header)

        # 2. Device Capsule
        dev_capsule = QHBoxLayout()
        dev_capsule.setAlignment(Qt.AlignmentFlag.AlignCenter)

        self.capsule_frame = QFrame()
        self.capsule_frame.setObjectName("capsuleFrame")
        self.capsule_frame.setStyleSheet("""
            QFrame#capsuleFrame {
                background-color: #12131C;
                border-radius: 18px;
                border: 1px solid #212435;
                padding: 4px 12px;
            }
        """)
        capsule_layout = QHBoxLayout(self.capsule_frame)
        capsule_layout.setContentsMargins(10, 5, 10, 5)
        capsule_layout.setSpacing(8)

        self.lbl_dev_name = QLabel("Earbuds")
        self.lbl_dev_name.setStyleSheet("font-size: 13px; font-weight: 700; color: #FFFFFF;")
        capsule_layout.addWidget(self.lbl_dev_name)

        self.lbl_rssi_tag = QLabel("Bluetooth LE")
        self.lbl_rssi_tag.setStyleSheet("""
            background: #1C2030;
            color: #3DDC84;
            font-size: 11px;
            font-weight: 600;
            padding: 2px 8px;
            border-radius: 10px;
        """)
        capsule_layout.addWidget(self.lbl_rssi_tag)

        dev_capsule.addWidget(self.capsule_frame)
        layout.addLayout(dev_capsule)

        # 3. Two Earbuds Cards (Left & Right) with responsive stretch and safe spacing
        self.buds_widget = QWidget()
        buds_row = QHBoxLayout(self.buds_widget)
        buds_row.setContentsMargins(0, 0, 0, 0)
        buds_row.setSpacing(12)
        buds_row.setAlignment(Qt.AlignmentFlag.AlignCenter)

        self.card_left = EarbudCard("Left Earbud")
        self.card_right = EarbudCard("Right Earbud")

        buds_row.addWidget(self.card_left, 1)
        buds_row.addWidget(self.card_right, 1)
        layout.addWidget(self.buds_widget)

        # 3b. Headphone / Neckband Card (for Single Bluetooth Audio Devices)
        self.card_headphone = HeadphoneCard()
        self.card_headphone.hide()
        layout.addWidget(self.card_headphone)

        # 4. Wide Case Battery Card
        self.card_case = CaseCard()
        layout.addWidget(self.card_case)

        # 5. Now Playing Media Card
        self.card_media = NowPlayingCard(
            on_toggle=self.on_media_toggle,
            on_next=self.on_media_next,
            on_prev=self.on_media_prev
        )
        layout.addWidget(self.card_media)

        # 6. Bottom Quick Actions
        bot_bar = QHBoxLayout()
        btn_preview_pop = QPushButton("👁️ Show Connect Popup")
        btn_preview_pop.setCursor(Qt.CursorShape.PointingHandCursor)
        btn_preview_pop.setStyleSheet("""
            QPushButton {
                background: #151722;
                color: #8D92A8;
                border: 1px solid #212435;
                border-radius: 8px;
                padding: 7px 14px;
                font-size: 12px;
                font-weight: 500;
            }
            QPushButton:hover {
                background: #252835;
                color: #FFFFFF;
                border: 1px solid #3DDC84;
            }
        """)
        btn_preview_pop.clicked.connect(self.on_trigger_popup)
        bot_bar.addWidget(btn_preview_pop)

        bot_bar.addStretch()

        btn_bt_settings = QPushButton("🔊 Windows Sound Settings")
        btn_bt_settings.setCursor(Qt.CursorShape.PointingHandCursor)
        btn_bt_settings.setStyleSheet("""
            QPushButton {
                background: #151722;
                color: #8D92A8;
                border: 1px solid #212435;
                border-radius: 8px;
                padding: 7px 14px;
                font-size: 12px;
                font-weight: 500;
            }
            QPushButton:hover {
                background: #252835;
                color: #FFFFFF;
            }
        """)
        btn_bt_settings.clicked.connect(lambda: os.system("start ms-settings:sound"))
        bot_bar.addWidget(btn_bt_settings)

        layout.addLayout(bot_bar)

        scroll.setWidget(page)
        return scroll

    def trigger_refresh(self):
        """Animates gauges like a speedometer revving up and pings background listeners"""
        if self._latest_state and self._latest_state.is_single_device:
            val = self._latest_state.left_battery or self._latest_state.right_battery or 75
            self.card_headphone.set_state(val, self._latest_state.is_left_charging or self._latest_state.is_right_charging, animate=True)
        else:
            left_val = self._latest_state.left_battery if self._latest_state else self.card_left.ring.level
            right_val = self._latest_state.right_battery if self._latest_state else self.card_right.ring.level
            case_val = self._latest_state.case_battery if self._latest_state else self.card_case.ring.level

            if left_val is None: left_val = 35
            if right_val is None: right_val = 35
            if case_val is None: case_val = settings.get("last_case_battery", 95)

            self.card_left.set_state(left_val, self._latest_state.is_left_charging if self._latest_state else False, is_in_case=self._latest_state.is_left_in_case if self._latest_state else False, animate=True)
            self.card_right.set_state(right_val, self._latest_state.is_right_charging if self._latest_state else False, is_in_case=self._latest_state.is_right_in_case if self._latest_state else False, animate=True)
            self.card_case.set_state(
                case_val,
                self._latest_state.is_case_charging if self._latest_state else False,
                self._latest_state.is_lid_open if self._latest_state else False,
                True,
                animate=True
            )

        if self.on_media_refresh:
            self.on_media_refresh()

    def _create_settings_page(self) -> QWidget:
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        scroll.setStyleSheet("background: transparent; border: none;")
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        scroll.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)

        content = QWidget()
        layout = QVBoxLayout(content)
        layout.setContentsMargins(24, 18, 24, 24)
        layout.setSpacing(18)

        # Header with back button
        hdr = QHBoxLayout()
        btn_back = QPushButton("← Back to Dashboard")
        btn_back.setCursor(Qt.CursorShape.PointingHandCursor)
        btn_back.setStyleSheet("""
            QPushButton {
                background: #151722;
                color: #B9C0DA;
                font-size: 12px;
                font-weight: 600;
                border-radius: 8px;
                border: 1px solid #282C40;
                padding: 6px 14px;
            }
            QPushButton:hover {
                background: #252835;
                color: #FFFFFF;
                border: 1px solid #3DDC84;
            }
        """)
        btn_back.clicked.connect(lambda: self.pages.setCurrentIndex(0))
        hdr.addWidget(btn_back)
        hdr.addStretch()

        lbl_settings_title = QLabel("Settings & Preferences")
        lbl_settings_title.setStyleSheet("font-size: 16px; font-weight: 700; color: #FFFFFF;")
        hdr.addWidget(lbl_settings_title)
        layout.addLayout(hdr)

        # SECTION 1: Logo & Avatar Customization
        sec_logo = QLabel("🎨  APP LOGO & SHAPE")
        sec_logo.setStyleSheet("font-size: 11px; font-weight: 700; color: #3DDC84; letter-spacing: 1.5px;")
        layout.addWidget(sec_logo)

        logo_frame = QFrame()
        logo_frame.setObjectName("settingRow")
        logo_box = QVBoxLayout(logo_frame)
        logo_box.setSpacing(14)

        logo_row = QHBoxLayout()
        logo_row.setSpacing(20)

        self.settings_avatar_preview = StyledLogoAvatar(size=64)
        logo_row.addWidget(self.settings_avatar_preview)

        btn_col = QVBoxLayout()
        btn_col.setSpacing(8)

        lbl_shape_info = QLabel("Choose Logo Silhouette Shape:")
        lbl_shape_info.setStyleSheet("color: #FFFFFF; font-size: 12px; font-weight: 600;")
        btn_col.addWidget(lbl_shape_info)

        shape_row = QHBoxLayout()
        shape_row.setSpacing(10)
        cur_shape = settings.get("logoShape", "round")

        for s_key, s_name in [("round", "Round"), ("side_round", "Side Round"), ("square", "Square")]:
            btn_s = QPushButton(s_name)
            btn_s.setCursor(Qt.CursorShape.PointingHandCursor)
            is_active = (s_key == cur_shape)
            btn_s.setStyleSheet(f"""
                QPushButton {{
                    background: {'#1D2335' if is_active else '#151722'};
                    color: {'#3DDC84' if is_active else '#8D92A8'};
                    border: 1.5px solid {'#3DDC84' if is_active else '#212435'};
                    border-radius: 8px;
                    padding: 5px 12px;
                    font-size: 11px;
                    font-weight: 600;
                }}
                QPushButton:hover {{
                    border: 1.5px solid #3DDC84;
                    color: #FFFFFF;
                }}
            """)
            btn_s.clicked.connect(lambda checked=False, k=s_key: self._set_logo_shape(k))
            shape_row.addWidget(btn_s)

        shape_row.addStretch()
        btn_col.addLayout(shape_row)

        action_row = QHBoxLayout()
        action_row.setSpacing(10)

        btn_choose = QPushButton("📁 Choose Custom Image...")
        btn_choose.setCursor(Qt.CursorShape.PointingHandCursor)
        btn_choose.setStyleSheet("""
            QPushButton {
                background: #1C2333;
                color: #6FD0F5;
                font-size: 11px;
                font-weight: 600;
                border: 1px solid #2A3C54;
                border-radius: 6px;
                padding: 6px 14px;
            }
            QPushButton:hover {
                background: #233147;
                color: #FFFFFF;
            }
        """)
        btn_choose.clicked.connect(self._pick_logo_image)
        action_row.addWidget(btn_choose)

        btn_reset = QPushButton("↺ Reset to Default")
        btn_reset.setCursor(Qt.CursorShape.PointingHandCursor)
        btn_reset.setStyleSheet("""
            QPushButton {
                background: #151722;
                color: #8D92A8;
                font-size: 11px;
                font-weight: 500;
                border: 1px solid #212435;
                border-radius: 6px;
                padding: 6px 12px;
            }
            QPushButton:hover {
                background: #252835;
                color: #FFFFFF;
            }
        """)
        btn_reset.clicked.connect(self._reset_logo)
        action_row.addWidget(btn_reset)
        action_row.addStretch()
        btn_col.addLayout(action_row)

        logo_row.addLayout(btn_col)
        logo_box.addLayout(logo_row)
        layout.addWidget(logo_frame)

        # SECTION 2: Themes & Colors (Professional Theme Cards with Color Swatches)
        sec_theme = QLabel("🎨  PALETTE & THEME")
        sec_theme.setStyleSheet("font-size: 11px; font-weight: 700; color: #3DDC84; letter-spacing: 1.5px;")
        layout.addWidget(sec_theme)

        theme_frame = QFrame()
        theme_frame.setObjectName("settingRow")
        theme_box = QVBoxLayout(theme_frame)
        theme_box.setSpacing(12)

        lbl_theme_desc = QLabel("Select an authentic dark design palette:")
        lbl_theme_desc.setStyleSheet("color: #8D92A8; font-size: 12px; font-weight: 500;")
        theme_box.addWidget(lbl_theme_desc)

        theme_grid = QHBoxLayout()
        theme_grid.setSpacing(10)
        cur_theme = settings.get("colorTheme", "default")

        for key, tdata in THEMES.items():
            is_cur = (key.lower() == cur_theme.lower())
            accent = tdata["primary"]

            tbtn = QPushButton(f"●  {key}")
            tbtn.setFixedHeight(38)
            tbtn.setCursor(Qt.CursorShape.PointingHandCursor)
            tbtn.setStyleSheet(f"""
                QPushButton {{
                    background: {'#1D2335' if is_cur else '#151722'};
                    color: {accent if is_cur else '#B9C0DA'};
                    border: {2 if is_cur else 1}px solid {accent if is_cur else '#212435'};
                    border-radius: 10px;
                    font-size: 12px;
                    font-weight: 700;
                    padding: 5px 12px;
                    text-align: center;
                }}
                QPushButton:hover {{
                    border: 2px solid {accent};
                    color: {accent};
                    background: #202435;
                }}
            """)
            tbtn.clicked.connect(lambda checked=False, k=key: self._set_theme(k))
            theme_grid.addWidget(tbtn)

        theme_box.addLayout(theme_grid)
        layout.addWidget(theme_frame)

        # SECTION 3: Connect Popup Drawer (Restored & Enhanced)
        sec_pop = QLabel("🪟  CONNECT POPUP DRAWER")
        sec_pop.setStyleSheet("font-size: 11px; font-weight: 700; color: #3DDC84; letter-spacing: 1.5px;")
        layout.addWidget(sec_pop)

        pop_frame = QFrame()
        pop_frame.setObjectName("settingRow")
        pop_box = QVBoxLayout(pop_frame)
        pop_box.setSpacing(12)

        chk_pop = QCheckBox("Show connect popup on Bluetooth connection")
        chk_pop.setChecked(settings.get("showConnectPopup", False))
        chk_pop.setStyleSheet("color: #FFFFFF; font-size: 12px; font-weight: 600;")
        chk_pop.toggled.connect(lambda v: settings.set("showConnectPopup", v))
        pop_box.addWidget(chk_pop)

        chk_dismiss = QCheckBox("Auto-dismiss popup automatically")
        chk_dismiss.setChecked(settings.get("autoDismissPopup", True))
        chk_dismiss.setStyleSheet("color: #FFFFFF; font-size: 12px; font-weight: 600;")
        chk_dismiss.toggled.connect(lambda v: settings.set("autoDismissPopup", v))
        pop_box.addWidget(chk_dismiss)

        lbl_dur = QLabel("Popup Display Timeout (Hide After):")
        lbl_dur.setStyleSheet("font-size: 11px; font-weight: 600; color: #8D92A8; margin-top: 4px;")
        pop_box.addWidget(lbl_dur)

        dur_row = QHBoxLayout()
        dur_row.setSpacing(14)
        d_group = QButtonGroup(content)
        durs = [(3, "3 seconds"), (8, "8 seconds"), (15, "15 seconds"), (0, "Keep Open")]
        cur_d = settings.get("popupTimeoutSeconds", 8)

        for sec, txt in durs:
            rb = QRadioButton(txt)
            rb.setStyleSheet("color: #FFFFFF; font-size: 12px; font-weight: 500;")
            rb.setChecked(cur_d == sec)
            d_group.addButton(rb)
            rb.toggled.connect(lambda c, s=sec: settings.set("popupTimeoutSeconds", s) if c else None)
            dur_row.addWidget(rb)

        dur_row.addStretch()
        pop_box.addLayout(dur_row)
        layout.addWidget(pop_frame)

        # SECTION 4: Startup & Background Service (Restored)
        sec_sys = QLabel("🚀  STARTUP & BACKGROUND SERVICE")
        sec_sys.setStyleSheet("font-size: 11px; font-weight: 700; color: #3DDC84; letter-spacing: 1.5px;")
        layout.addWidget(sec_sys)

        sys_frame = QFrame()
        sys_frame.setObjectName("settingRow")
        sys_box = QVBoxLayout(sys_frame)

        chk_boot = QCheckBox("Launch Budora silently in System Tray when Windows boots")
        chk_boot.setChecked(settings.get("launchAtStartup", False))
        chk_boot.setStyleSheet("font-size: 12px; font-weight: 600; color: #FFFFFF;")
        chk_boot.toggled.connect(lambda v: settings.set_startup(v))
        sys_box.addWidget(chk_boot)
        layout.addWidget(sys_frame)

        # SECTION 5: Version & Author Footer (Restored)
        sec_about = QLabel("ℹ️  ABOUT & CREDITS")
        sec_about.setStyleSheet("font-size: 11px; font-weight: 700; color: #3DDC84; letter-spacing: 1.5px;")
        layout.addWidget(sec_about)

        abt_frame = QFrame()
        abt_frame.setObjectName("settingRow")
        abt_box = QVBoxLayout(abt_frame)
        abt_box.setSpacing(6)

        lbl_app_ver = QLabel("Budora for Windows • Version 1.0.0")
        lbl_app_ver.setStyleSheet("font-size: 13px; font-weight: 700; color: #FFFFFF;")
        abt_box.addWidget(lbl_app_ver)

        lbl_dev = QLabel("Crafted with care by Prem Prasad Pradhan — a Zenemoo Software")
        lbl_dev.setStyleSheet("font-size: 12px; color: #3DDC84; font-weight: 500;")
        abt_box.addWidget(lbl_dev)
        layout.addWidget(abt_frame)

        layout.addStretch()

        scroll.setWidget(content)
        return scroll

    def _pick_logo_image(self):
        file_path, _ = QFileDialog.getOpenFileName(
            self, "Choose Custom App Logo", "",
            "Images (*.png *.jpg *.jpeg *.webp *.bmp)"
        )
        if file_path:
            settings.save_custom_logo(file_path)
            self.settings_avatar_preview.update()
            self.avatar_logo.update()

    def _reset_logo(self):
        settings.clear_custom_logo()
        self.settings_avatar_preview.update()
        self.avatar_logo.update()

    def _set_logo_shape(self, shape_key: str):
        settings.set("logoShape", shape_key)
        self.settings_avatar_preview.update()
        self.avatar_logo.update()

    def _set_theme(self, theme_key: str):
        settings.set("colorTheme", theme_key)
        self._apply_theme()
        idx = self.pages.currentIndex()
        self.pages.removeWidget(self.settings_page)
        self.settings_page = self._create_settings_page()
        self.pages.addWidget(self.settings_page)
        self.pages.setCurrentIndex(idx)

    def open_page(self, index: int):
        self.pages.setCurrentIndex(index)

    def update_state(self, state: BudoraState):
        self._latest_state = state
        name = state.device_name or "CMF Buds 2a"
        self.lbl_dev_name.setText(name)

        if not state.is_connected or (not state.is_single_device and state.left_battery is None and state.right_battery is None):
            self.lbl_status.setText("Disconnected")
            self.dot.setStyleSheet("color: #565A6E; font-size: 11px;")
            self.lbl_rssi_tag.setText("Disconnected")
            self.lbl_rssi_tag.setStyleSheet("""
                background: #181A26;
                color: #565A6E;
                font-size: 11px;
                font-weight: 600;
                padding: 2px 8px;
                border-radius: 10px;
            """)
            if state.is_single_device:
                self.buds_widget.hide()
                self.card_case.hide()
                self.card_headphone.show()
                self.card_headphone.set_state(None, False)
            else:
                self.card_headphone.hide()
                self.buds_widget.show()
                self.card_case.show()
                self.card_left.set_state(None, False)
                self.card_right.set_state(None, False)
                self.card_case.set_state(None, False, False, is_memorized=False, force_blank=True)
            return

        status_text = "Connected & Active" if (state.is_lid_open or state.is_single_device) else "In Range"
        rssi_str = f"{state.rssi} dBm" if state.rssi != 0 else "Active"
        self.lbl_rssi_tag.setText(rssi_str)
        self.lbl_rssi_tag.setStyleSheet("""
            background: #1C2030;
            color: #3DDC84;
            font-size: 11px;
            font-weight: 600;
            padding: 2px 8px;
            border-radius: 10px;
        """)
        self.lbl_status.setText(status_text)
        self.dot.setStyleSheet("color: #3DDC84; font-size: 11px;")

        if state.is_single_device:
            self.buds_widget.hide()
            self.card_case.hide()
            self.card_headphone.show()
            self.card_headphone.set_device_name(state.device_name)
            lvl = state.left_battery or state.right_battery
            chg = state.is_left_charging or state.is_right_charging
            self.card_headphone.set_state(lvl, chg, is_connected=state.is_connected)
        else:
            self.card_headphone.hide()
            self.buds_widget.show()
            self.card_case.show()
            self.card_left.set_state(state.left_battery, state.is_left_charging, is_in_case=state.is_left_in_case)
            self.card_right.set_state(state.right_battery, state.is_right_charging, is_in_case=state.is_right_in_case)
            self.card_case.set_state(state.case_battery, state.is_case_charging, state.is_lid_open, state.case_is_memorized)

    def update_media(self, data: dict):
        self.card_media.update_media(data)

    def closeEvent(self, event):
        event.ignore()
        self.hide()
