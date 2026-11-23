import time
import winreg
from typing import Dict, Optional
from models import BudoraState, BudoraModel
from config import settings

def resolve_device_name(ble_name: Optional[str] = None) -> str:
    """
    Resolves the actual human-readable Bluetooth device name (e.g. 'CMF Buds 2a').
    Checks Windows active audio render endpoints, paired registry devices, and settings.
    """
    if ble_name and ble_name not in ["Earbuds", "Unknown", "Wireless Earbuds", "Bluetooth LE"]:
        settings.set("last_device_name", ble_name)
        return ble_name

    # 1. Check active Bluetooth audio endpoint in Windows MMDevices
    path = r"SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Render"
    try:
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, path) as key:
            for i in range(winreg.QueryInfoKey(key)[0]):
                sub = winreg.EnumKey(key, i)
                try:
                    with winreg.OpenKey(key, sub) as sk:
                        state, _ = winreg.QueryValueEx(sk, "DeviceState")
                        # 1 = DEVICE_STATE_ACTIVE
                        if state == 1:
                            with winreg.OpenKey(sk, "Properties") as pk:
                                dev_name = None
                                is_bth = False
                                for j in range(winreg.QueryInfoKey(pk)[1]):
                                    pname, pval, _ = winreg.EnumValue(pk, j)
                                    if "{b3f8fa53-0004-438e-9003-51a46e139bfc},6" in pname and isinstance(pval, str):
                                        dev_name = pval
                                    if isinstance(pval, str) and ("BTHENUM" in pval or "BTHHFENUM" in pval):
                                        is_bth = True
                                if is_bth and dev_name:
                                    clean = dev_name.replace(" Hands-Free", "").strip()
                                    if clean and any(k in clean.lower() for k in ["buds", "airpods", "pro", "ear", "cmf", "head"]):
                                        settings.set("last_device_name", clean)
                                        return clean
                except Exception:
                    pass
    except Exception:
        pass

    # 2. Check saved name from persistent settings
    saved = settings.get("last_device_name")
    if saved:
        return saved

    # 3. Check paired BTHPORT devices
    bth_path = r"SYSTEM\CurrentControlSet\Services\BTHPORT\Parameters\Devices"
    try:
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, bth_path) as key:
            for i in range(winreg.QueryInfoKey(key)[0]):
                sub = winreg.EnumKey(key, i)
                try:
                    with winreg.OpenKey(key, sub) as dk:
                        name, _ = winreg.QueryValueEx(dk, "Name")
                        if isinstance(name, bytes):
                            name = name.split(b"\x00")[0].decode("utf-8", errors="ignore")
                        if name and any(k in name.lower() for k in ["buds", "airpods", "pro", "cmf", "ear"]):
                            settings.set("last_device_name", name)
                            return name
                except Exception:
                    pass
    except Exception:
        pass

    return "CMF Buds 2a"

def get_connected_bluetooth_audio() -> Optional[str]:
    """
    Checks Windows Core Audio (MMDevices) for an active connected Bluetooth audio device
    (such as a Bluetooth speaker, soundbox, or classic headset).
    Returns the clean device name if active, or None.
    """
    path = r"SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Render"
    candidates = []
    try:
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, path) as key:
            for i in range(winreg.QueryInfoKey(key)[0]):
                sub = winreg.EnumKey(key, i)
                try:
                    with winreg.OpenKey(key, sub) as sk:
                        st, _ = winreg.QueryValueEx(sk, "DeviceState")
                        # 1 = DEVICE_STATE_ACTIVE
                        if st == 1:
                            with winreg.OpenKey(sk, "Properties") as pk:
                                props = {}
                                for j in range(winreg.QueryInfoKey(pk)[1]):
                                    pname, pval, _ = winreg.EnumValue(pk, j)
                                    props[pname] = pval
                                name = props.get("{b3f8fa53-0004-438e-9003-51a46e139bfc},6")
                                desc = props.get("{a45c254e-df1c-4efd-8020-67d146a850e0},2")
                                is_bth = any("BTH" in str(v) for v in props.values())
                                if is_bth and name:
                                    clean = name.replace(" Hands-Free", "").replace(" HF Audio", "").strip()
                                    is_stereo = (desc in ["Headphones", "Speakers"] and "Hands-Free" not in name)
                                    is_phone = any(p in clean.lower() for p in ["phone", "realme", "galaxy", "redmi", "iphone"])
                                    score = 10 if (is_stereo and not is_phone) else (5 if not is_phone else 1)
                                    candidates.append((score, clean))
                except Exception:
                    pass
    except Exception:
        pass
    if candidates:
        candidates.sort(key=lambda c: c[0], reverse=True)
        return candidates[0][1]
    return None

class BatteryPredictor:
    """
    Mathematical battery life predictor for Bluetooth devices.
    Tracks discharge rate (% per hour) using Exponentially Weighted Moving Average (EWMA).
    Formula:
      Discharge Rate R = ΔB / Δt (%/hr)
      R_smoothed = α * R_instant + (1 - α) * R_smoothed
      Time Remaining = Current Battery / R_smoothed
    """
    def __init__(self, alpha: float = 0.35):
        self.alpha = alpha
        self._channels: Dict[str, dict] = {}

    def predict(self, channel_id: str, current_level: Optional[int], is_charging: bool, is_audio_active: bool = False, now: Optional[float] = None) -> Optional[str]:
        if current_level is None or current_level <= 0:
            return None

        if is_charging:
            missing = max(0, 100 - current_level)
            if missing <= 2:
                return "⚡ Fully Charged"
            mins = max(5, int((missing / 100.0) * 55))
            if mins >= 60:
                h = mins // 60
                m = mins % 60
                return f"⚡ Full in ~{h}h {m}m" if m else f"⚡ Full in ~{h}h"
            return f"⚡ Full in ~{mins}m"

        if now is None:
            now = time.time()

        ch = self._channels.setdefault(channel_id, {
            "samples": [],
            "smoothed_rate": 18.0 if is_audio_active else 5.0,
            "last_charging": False
        })

        if ch["last_charging"]:
            ch["samples"] = []
            ch["smoothed_rate"] = 18.0 if is_audio_active else 5.0
        ch["last_charging"] = False

        samples = ch["samples"]
        if not samples or samples[-1][1] != current_level or (now - samples[-1][0]) > 60:
            samples.append((now, current_level))
            if len(samples) > 20:
                samples.pop(0)

        if len(samples) >= 2:
            dt_hours = (samples[-1][0] - samples[0][0]) / 3600.0
            db = samples[0][1] - samples[-1][1]
            if dt_hours > (45.0 / 3600.0) and db > 0:
                instant_rate = db / dt_hours
                instant_rate = max(2.0, min(50.0, instant_rate))
                ch["smoothed_rate"] = self.alpha * instant_rate + (1 - self.alpha) * ch["smoothed_rate"]

        rate = ch["smoothed_rate"]
        if is_audio_active and rate < 12.0:
            rate = 14.0
        if rate <= 0.1:
            rate = 4.0

        hours_left = current_level / rate
        total_minutes = int(hours_left * 60)

        if total_minutes < 15:
            return "Low battery (<15m)"
        elif total_minutes < 60:
            return f"~ {total_minutes}m left"
        else:
            h = total_minutes // 60
            m = total_minutes % 60
            if m < 5:
                return f"~ {h}h left"
            return f"~ {h}h {m}m left"

class BudoraTracker:
    """
    Arbitrates candidate Bluetooth devices to pick the user's earbuds.
    Uses signal hysteresis (SWITCH_MARGIN_DB) to stick to the active pair,
    Smart Case Memory to remember case battery levels, and actively emits
    disconnected states when Bluetooth disconnects.
    """
    CANDIDATE_TTL_SEC = 8.0
    SWITCH_MARGIN_DB = 5
    MEMORY_TTL_SEC = 86400.0

    class Candidate:
        def __init__(self, address: str, rssi: int, state: BudoraState, seen_at: float, name: str):
            self.address = address
            self.rssi = rssi
            self.state = state
            self.seen_at = seen_at
            self.name = name

    def __init__(self):
        self._candidates: Dict[str, BudoraTracker.Candidate] = {}
        self._incumbent_address: Optional[str] = None
        self._was_connected: bool = False
        self._last_device_name: str = settings.get("last_device_name", "CMF Buds 2a")
        # Load persisted case memory across sessions
        self._last_case_battery: Optional[int] = settings.get("last_case_battery", 95)
        self._last_case_seen_at: float = settings.get("last_case_seen_at", 0.0)
        self._fallback_device: Optional[str] = None
        self._predictor = BatteryPredictor()

    def on_advertisement(self, address: str, rssi: int, state: BudoraState, now: Optional[float] = None) -> Optional[BudoraState]:
        if now is None:
            now = time.time()
        state.is_connected = True
        self._fallback_device = None

        # Resolve clean Bluetooth device name
        resolved_name = resolve_device_name(state.device_name)
        self._last_device_name = resolved_name
        state.device_name = resolved_name

        # Detect Neckbands & Single-unit Headphones
        low_name = resolved_name.lower()
        if state.model == 0x0A20 or any(k in low_name for k in ["neckband", "rockerz", "wireless collar", "wh-", "ch-", "wi-", "headphones", "headset", "bt-", "speaker"]):
            state.is_single_device = True
            state.is_lid_open = False
            state.case_battery = None
            state.case_is_memorized = False
        elif state.case_battery is None and not state.is_lid_open and not any(k in low_name for k in ["buds", "airpod", "tws", "ear"]) and not (state.model != 0x0A20 and BudoraModel.is_known(state.model)):
            state.is_single_device = True
            state.is_lid_open = False
            state.case_battery = None
            state.case_is_memorized = False
        else:
            state.is_single_device = False

        if not state.is_single_device:
            # Detect whether one earbud is inside the case (charging or docked)
            if state.is_left_charging and not state.is_right_charging:
                state.is_left_in_case = True
                state.is_right_in_case = False
            elif state.is_right_charging and not state.is_left_charging:
                state.is_right_in_case = True
                state.is_left_in_case = False
            elif state.left_battery is None and state.right_battery is not None:
                state.is_left_in_case = True
                state.is_right_in_case = False
            elif state.right_battery is None and state.left_battery is not None:
                state.is_right_in_case = True
                state.is_left_in_case = False
            else:
                state.is_left_in_case = False
                state.is_right_in_case = False

            if state.case_battery is not None and state.case_battery > 0:
                self._last_case_battery = state.case_battery
                self._last_case_seen_at = now
                settings.set("last_case_battery", self._last_case_battery)
                settings.set("last_case_seen_at", self._last_case_seen_at)
            elif self._last_case_battery is not None:
                # Apply Smart Case Memory so the case displays last verified level
                state.case_battery = self._last_case_battery
                state.case_is_memorized = True

        # Compute Active Battery Time Predictions
        state.left_time_remaining = self._predictor.predict(
            f"{address}_left", state.left_battery, state.is_left_charging, now=now
        )
        state.right_time_remaining = self._predictor.predict(
            f"{address}_right", state.right_battery, state.is_right_charging, now=now
        )
        if state.is_single_device:
            single_val = state.left_battery if state.left_battery is not None else state.right_battery
            single_chg = state.is_left_charging or state.is_right_charging
            state.single_time_remaining = self._predictor.predict(
                f"{address}_single", single_val, single_chg, now=now
            )

        if address in self._candidates:
            c = self._candidates[address]
            c.rssi = rssi
            c.state = state
            c.seen_at = now
            if resolved_name:
                c.name = resolved_name
        else:
            self._candidates[address] = BudoraTracker.Candidate(address, rssi, state, now, resolved_name)

        self._expire_stale(now)
        selected = self._select()
        if selected is not None:
            self._was_connected = True
        return selected

    def check_expired(self, now: Optional[float] = None) -> Optional[BudoraState]:
        """
        Called periodically (every ~1s) by the scanner loop.
        Primary paired earbuds (Fast Pair / AirPods) always have #1 top priority.
        If no primary earbuds are active, checks for secondary connected Bluetooth audio devices
        (like Bluetooth speaker boxes, neckbands, etc.).
        """
        if now is None:
            now = time.time()
        self._expire_stale(now)

        # 1. Primary Earbuds always take priority if active
        if self._candidates:
            self._fallback_device = None
            return self._select()

        # 2. Secondary Fallback: Check active connected Windows Bluetooth audio (speakers, boxes)
        bt_audio = get_connected_bluetooth_audio()
        if bt_audio:
            if not self._was_connected or self._fallback_device != bt_audio:
                self._was_connected = True
                self._fallback_device = bt_audio
                self._last_device_name = bt_audio
                return BudoraState(
                    model=BudoraModel.GENERIC_BLUETOOTH,
                    left_battery=None,
                    right_battery=None,
                    case_battery=None,
                    is_left_charging=False,
                    is_right_charging=False,
                    is_case_charging=False,
                    is_lid_open=False,
                    case_is_memorized=False,
                    device_name=bt_audio,
                    rssi=0,
                    is_connected=True,
                    is_single_device=True
                )
            return None

        # 3. Neither primary nor secondary is connected
        if self._was_connected:
            self._was_connected = False
            self._fallback_device = None
            return self.create_disconnected_state()

        return None

    def create_disconnected_state(self) -> BudoraState:
        """Generates a clean disconnected state where all battery values are None"""
        return BudoraState(
            model=0,
            left_battery=None,
            right_battery=None,
            case_battery=None,
            is_left_charging=False,
            is_right_charging=False,
            is_case_charging=False,
            is_lid_open=False,
            case_is_memorized=False,
            device_name=self._last_device_name,
            rssi=0,
            is_connected=False
        )

    def _expire_stale(self, now: float):
        stale_keys = [k for k, v in self._candidates.items() if now - v.seen_at > self.CANDIDATE_TTL_SEC]
        for k in stale_keys:
            del self._candidates[k]
        if self._incumbent_address not in self._candidates:
            self._incumbent_address = None

    def _select(self) -> Optional[BudoraState]:
        if not self._candidates:
            return None

        if self._incumbent_address and self._incumbent_address in self._candidates:
            incumbent = self._candidates[self._incumbent_address]
            strongest = max(self._candidates.values(), key=lambda c: c.rssi)
            if strongest.address != incumbent.address and strongest.rssi > (incumbent.rssi + self.SWITCH_MARGIN_DB):
                self._incumbent_address = strongest.address
                strongest.state.device_name = strongest.name
                return strongest.state
            incumbent.state.device_name = incumbent.name
            return incumbent.state

        strongest = max(self._candidates.values(), key=lambda c: c.rssi)
        self._incumbent_address = strongest.address
        strongest.state.device_name = strongest.name
        return strongest.state

    def reset(self):
        self._candidates.clear()
        self._incumbent_address = None
        self._was_connected = False
        self._fallback_device = None
