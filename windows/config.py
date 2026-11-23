import json
import os
import shutil
import sys

if getattr(sys, "frozen", False):
    EXE_DIR = os.path.dirname(sys.executable)
    BUNDLE_DIR = getattr(sys, "_MEIPASS", EXE_DIR)
else:
    EXE_DIR = os.path.dirname(os.path.abspath(__file__))
    BUNDLE_DIR = EXE_DIR

CONFIG_PATH = os.path.join(EXE_DIR, "settings.json")
CUSTOM_LOGO_FILE = os.path.join(EXE_DIR, "custom_logo.png")
DEFAULT_LOGO_FILE = os.path.join(BUNDLE_DIR, "custom_logo.png")

# Exact palettes from Budora Android app (Palettes.kt)
THEMES = {
    "Default": {
        "primary": "#B9C0DA",
        "bg": "#08090F",
        "surface": "#10111A",
        "card": "#151722",
        "border": "#212435",
        "text": "#E6E7F2",
        "text_secondary": "#8D92A8"
    },
    "Ocean": {
        "primary": "#6FD0F5",
        "bg": "#060C12",
        "surface": "#0D1924",
        "card": "#122232",
        "border": "#1D3349",
        "text": "#E1F5FE",
        "text_secondary": "#80B6CF"
    },
    "Sunset": {
        "primary": "#FFB088",
        "bg": "#120907",
        "surface": "#1F110D",
        "card": "#291712",
        "border": "#3D241C",
        "text": "#FDEEE9",
        "text_secondary": "#BA8B7C"
    },
    "Forest": {
        "primary": "#8BD98B",
        "bg": "#070F08",
        "surface": "#0E1D10",
        "card": "#142917",
        "border": "#1E3B22",
        "text": "#E8F5E9",
        "text_secondary": "#87B28B"
    },
    "Grape": {
        "primary": "#B79CFF",
        "bg": "#0B0814",
        "surface": "#151024",
        "card": "#1C1630",
        "border": "#2E244D",
        "text": "#F3E8FF",
        "text_secondary": "#9E8EB8"
    }
}

BATTERY_COLORS = {
    "good": "#3DDC84",
    "moderate": "#FF9E0B",
    "low": "#EF4444",
    "unknown": "#565A6E"
}

def get_battery_color(level):
    if level is None:
        return BATTERY_COLORS["unknown"]
    if level > 40:
        return BATTERY_COLORS["good"]
    if level > 20:
        return BATTERY_COLORS["moderate"]
    return BATTERY_COLORS["low"]

DEFAULT_SETTINGS = {
    "colorTheme": "Default",
    "darkMode": "dark",
    "logoShape": "round",  # "round", "side_round", "square"
    "showConnectPopup": True,
    "popupPosition": "bottom",
    "autoDismissPopup": True,
    "popupTimeoutSeconds": 8,
    "resumeMusicOnConnect": False,
    "launchAtStartup": False,
    "lowBatteryThreshold": 20
}

class AppSettings:
    def __init__(self):
        self.data = dict(DEFAULT_SETTINGS)
        self.load()

    def load(self):
        if os.path.exists(CONFIG_PATH):
            try:
                with open(CONFIG_PATH, "r", encoding="utf-8") as f:
                    self.data.update(json.load(f))
            except Exception as e:
                print(f"Settings load error: {e}")

    def save(self):
        try:
            with open(CONFIG_PATH, "w", encoding="utf-8") as f:
                json.dump(self.data, f, indent=2)
        except Exception as e:
            print(f"Settings save error: {e}")

    def get(self, key, default=None):
        return self.data.get(key, default)

    def set(self, key, value):
        self.data[key] = value
        self.save()

    def get_theme_colors(self):
        theme_name = self.data.get("colorTheme", "Default")
        return THEMES.get(theme_name, THEMES["Default"])

    def has_custom_logo(self) -> bool:
        if os.path.exists(CUSTOM_LOGO_FILE) and os.path.getsize(CUSTOM_LOGO_FILE) > 0:
            return True
        return os.path.exists(DEFAULT_LOGO_FILE) and os.path.getsize(DEFAULT_LOGO_FILE) > 0

    def get_custom_logo_path(self):
        if os.path.exists(CUSTOM_LOGO_FILE) and os.path.getsize(CUSTOM_LOGO_FILE) > 0:
            return CUSTOM_LOGO_FILE
        if os.path.exists(DEFAULT_LOGO_FILE) and os.path.getsize(DEFAULT_LOGO_FILE) > 0:
            return DEFAULT_LOGO_FILE
        return None

    def save_custom_logo(self, source_path: str):
        try:
            shutil.copy2(source_path, CUSTOM_LOGO_FILE)
            self.save()
            return True
        except Exception as e:
            print(f"Error saving custom logo: {e}")
            return False

    def reset_custom_logo(self):
        if os.path.exists(CUSTOM_LOGO_FILE):
            try:
                os.remove(CUSTOM_LOGO_FILE)
            except Exception as e:
                print(f"Error resetting custom logo: {e}")
        self.save()

    def set_startup(self, enable: bool):
        self.set("launchAtStartup", enable)
        try:
            import winreg
            key = winreg.OpenKey(
                winreg.HKEY_CURRENT_USER,
                r"Software\Microsoft\Windows\CurrentVersion\Run",
                0,
                winreg.KEY_SET_VALUE
            )
            app_dir = os.path.dirname(os.path.abspath(__file__))
            exe_path = os.path.join(app_dir, "Budora.exe")
            bat_path = os.path.join(app_dir, "run.bat")
            target = exe_path if os.path.exists(exe_path) else f'"{bat_path}"'

            if enable:
                winreg.SetValueEx(key, "Budora", 0, winreg.REG_SZ, target)
            else:
                try:
                    winreg.DeleteValue(key, "Budora")
                except FileNotFoundError:
                    pass
            winreg.CloseKey(key)
        except Exception as e:
            print(f"Startup winreg error: {e}")

settings = AppSettings()
