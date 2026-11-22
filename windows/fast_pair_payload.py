from typing import Optional
from models import BudoraModel, BudoraState

class FastPairPayload:
    """
    Parses Google Fast Pair battery advertisement (Service Data for 0xFE2C).
    Header: 0bLLLLTTTT (L = 1..3 components, T = 3 Show UI or 4 Hide UI)
    Component bytes: bit 7 = charging (1/0), bits 0..6 = battery level (0..100, 0x7F unknown)
    Ranks candidates by packet tail alignment (is_exact_end) to prevent salted bloom filter hash collisions.
    """
    FAST_PAIR_UUID_SHORT = "fe2c"

    @staticmethod
    def parse(data: bytes, rssi: int = 0) -> Optional[BudoraState]:
        if len(data) < 2:
            return None

        candidates = []

        for i in range(len(data) - 1):
            header = data[i]
            num_values = (header >> 4) & 0x0F
            ui_type = header & 0x0F

            if 1 <= num_values <= 3 and ui_type in (3, 4) and (i + num_values < len(data)):
                left_raw = data[i + 1]
                right_raw = data[i + 2] if num_values >= 2 else None
                case_raw = data[i + 3] if num_values >= 3 else None

                left_val = left_raw & 0x7F
                right_val = (right_raw & 0x7F) if right_raw is not None else None
                case_val = (case_raw & 0x7F) if case_raw is not None else None

                def is_valid(v: Optional[int]) -> bool:
                    return v is None or v == 0x7F or 0 <= v <= 100

                if not (is_valid(left_val) and is_valid(right_val) and is_valid(case_val)):
                    continue

                left_bat = left_val if 0 <= left_val <= 100 else None
                right_bat = right_val if right_val is not None and 0 <= right_val <= 100 else None
                case_bat = case_val if case_val is not None and 0 <= case_val <= 100 else None

                is_left_charging = bool(left_raw & 0x80)
                is_right_charging = bool(right_raw & 0x80) if right_raw is not None else False
                is_case_charging = bool(case_raw & 0x80) if case_raw is not None else False

                # Ignore uninitialized dummy frames reporting all zeroes with no charging
                has_real_reading = (
                    (left_bat is not None and left_bat > 0) or
                    (right_bat is not None and right_bat > 0) or
                    (case_bat is not None and case_bat > 0) or
                    is_left_charging or is_right_charging or is_case_charging
                )
                if not has_real_reading:
                    continue

                is_exact_end = (i + 1 + num_values == len(data))

                state = BudoraState(
                    model=BudoraModel.GOOGLE_FAST_PAIR,
                    left_battery=left_bat,
                    right_battery=right_bat,
                    case_battery=case_bat,
                    is_left_charging=is_left_charging,
                    is_right_charging=is_right_charging,
                    is_case_charging=is_case_charging,
                    is_lid_open=is_case_charging or (case_bat is not None and case_bat > 0),
                    device_name="Earbuds",
                    rssi=rssi
                )
                candidates.append((is_exact_end, ui_type == 3, i, state))

        if not candidates:
            return None

        # Priority Ranking:
        # 1. Matches exact end of packet (standard Fast Pair TLV tail)
        # 2. Type == 3 (Show UI) over Type == 4 (Hide UI)
        # 3. Later index in packet (past Model ID / Bloom filter)
        candidates.sort(key=lambda c: (c[0], c[1], c[2]), reverse=True)
        return candidates[0][3]
