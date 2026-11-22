from typing import Optional
from models import BudoraModel, BudoraState

class ProximityPayload:
    """
    Parser for Apple's Proximity Pairing BLE advertisements.
    Company ID: 0x004C (Apple)
    Message Type: 0x07 (Proximity Pairing)
    """
    APPLE_COMPANY_ID = 0x004C
    TYPE_PROXIMITY_PAIRING = 0x07
    MIN_LENGTH = 20

    @staticmethod
    def parse(data: bytes, rssi: int = 0) -> Optional[BudoraState]:
        if len(data) < ProximityPayload.MIN_LENGTH:
            return None
        if data[0] != ProximityPayload.TYPE_PROXIMITY_PAIRING:
            return None

        model = (data[3] << 8) | data[4]
        if not BudoraModel.is_known(model):
            return None

        status = data[5]
        pods_battery = data[6]
        flags_and_case = data[7]
        lid = data[8]

        # Bit 5: 1 if left pod is primary
        is_primary_left = ((status >> 5) & 0x01) == 1
        flipped = not is_primary_left

        if flipped:
            left_nibble = (pods_battery >> 4) & 0x0F
            right_nibble = pods_battery & 0x0F
        else:
            left_nibble = pods_battery & 0x0F
            right_nibble = (pods_battery >> 4) & 0x0F

        case_nibble = flags_and_case & 0x0F
        charge_flags = (flags_and_case >> 4) & 0x0F

        left_charge_bit = 0x02 if flipped else 0x01
        right_charge_bit = 0x01 if flipped else 0x02
        case_charge_bit = 0x04

        is_left_charging = (charge_flags & left_charge_bit) != 0
        is_right_charging = (charge_flags & right_charge_bit) != 0
        is_case_charging = (charge_flags & case_charge_bit) != 0

        # Lid bit is inverted: 0 means open
        is_lid_open = ((lid >> 3) & 0x01) == 0

        # Nibbles 0..10 represent 0%..100%. 0xF means unknown / disconnected.
        def decode_nibble(n: int) -> Optional[int]:
            if n > 10 or n == 0x0F:
                return None
            return min(100, max(0, n * 10))

        left_bat = decode_nibble(left_nibble)
        right_bat = decode_nibble(right_nibble)
        case_bat = decode_nibble(case_nibble)

        return BudoraState(
            model=model,
            left_battery=left_bat,
            right_battery=right_bat,
            case_battery=case_bat,
            is_left_charging=is_left_charging,
            is_right_charging=is_right_charging,
            is_case_charging=is_case_charging,
            is_lid_open=is_lid_open,
            device_name=BudoraModel.display_name(model),
            rssi=rssi
        )
