from dataclasses import dataclass
from typing import Optional

class BudoraModel:
    AIRPODS_GEN_1 = 0x0220
    AIRPODS_GEN_2 = 0x0F20
    AIRPODS_PRO = 0x0E20
    AIRPODS_GEN_3 = 0x1320
    AIRPODS_PRO_2 = 0x1420
    AIRPODS_MAX = 0x0A20
    GENERIC_BLUETOOTH = 0xFFFF
    GOOGLE_FAST_PAIR = 0xFE2C

    NAMES = {
        AIRPODS_GEN_1: "AirPods (1st gen)",
        AIRPODS_GEN_2: "AirPods (2nd gen)",
        AIRPODS_PRO: "AirPods Pro (1st gen)",
        AIRPODS_GEN_3: "AirPods (3rd gen)",
        AIRPODS_PRO_2: "AirPods Pro (2nd gen)",
        AIRPODS_MAX: "AirPods Max",
        GOOGLE_FAST_PAIR: "Fast Pair Earbuds",
        GENERIC_BLUETOOTH: "Wireless Earbuds"
    }

    @classmethod
    def is_known(cls, model: int) -> bool:
        if model in cls.NAMES:
            return True
        return (model & 0x00FF) == 0x0020

    @classmethod
    def display_name(cls, model: int) -> str:
        if model in cls.NAMES:
            return cls.NAMES[model]
        if (model & 0x00FF) == 0x0020:
            return f"AirPods (0x{model:04X})"
        return "Wireless Earbuds"

@dataclass
class BudoraState:
    model: int
    left_battery: Optional[int]
    right_battery: Optional[int]
    case_battery: Optional[int]
    is_left_charging: bool
    is_right_charging: bool
    is_case_charging: bool
    is_lid_open: bool
    case_is_memorized: bool = False
    device_name: str = "Earbuds"
    rssi: int = 0
    is_connected: bool = True
    is_single_device: bool = False
    is_left_in_case: bool = False
    is_right_in_case: bool = False
    left_time_remaining: Optional[str] = None
    right_time_remaining: Optional[str] = None
    case_time_remaining: Optional[str] = None
    single_time_remaining: Optional[str] = None

    @property
    def model_name(self) -> str:
        return BudoraModel.display_name(self.model)

    def summary(self) -> str:
        if self.is_single_device:
            lvl = self.left_battery or self.right_battery
            c = "⚡" if (self.is_left_charging or self.is_right_charging) else ""
            return f"Headphones: {lvl}%{c}" if lvl is not None else "Headphones: Connected"
        parts = []
        if self.left_battery is not None:
            c = "⚡" if self.is_left_charging else ""
            dock = " (Docked)" if self.is_left_in_case else ""
            parts.append(f"L: {self.left_battery}%{c}{dock}")
        if self.right_battery is not None:
            c = "⚡" if self.is_right_charging else ""
            dock = " (Docked)" if self.is_right_in_case else ""
            parts.append(f"R: {self.right_battery}%{c}{dock}")
        if self.case_battery is not None:
            c = "⚡" if self.is_case_charging else ""
            m = "~" if self.case_is_memorized else ""
            parts.append(f"Case: {m}{self.case_battery}%{c}")
        return " | ".join(parts) if parts else "Searching..."
