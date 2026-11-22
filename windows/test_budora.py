import sys
import os
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from proximity_payload import ProximityPayload
from fast_pair_payload import FastPairPayload
from tracker import BudoraTracker
from models import BudoraModel, BudoraState

class TestBudoraWindows(unittest.TestCase):
    def test_proximity_apple_gen2_fixture(self):
        # Hex frame from TECHNICAL.md:
        # 07 19 01 0F20 01 A9 8F 01 00 04 C0 D1 B9 25 94 25 F3 48 1F 56 89 E3 4E 40 39 EF
        hex_data = "0719010F2001A98F010004C0D1B9259425F3481F5689E34E4039EF"
        payload = bytes.fromhex(hex_data)
        
        state = ProximityPayload.parse(payload, rssi=-45)
        self.assertIsNotNone(state)
        self.assertEqual(state.model, BudoraModel.AIRPODS_GEN_2)
        # Status 0x01: right pod primary, so flipped = true
        # podsBattery = 0xA9 -> left is high nibble (10 -> 100%), right is low nibble (9 -> 90%)
        self.assertEqual(state.left_battery, 100)
        self.assertEqual(state.right_battery, 90)
        # Case nibble is 0xF -> unknown (None)
        self.assertIsNone(state.case_battery)
        self.assertEqual(state.rssi, -45)

    def test_fast_pair_payload(self):
        # Fast Pair 0xFE2C frame:
        # Header: 0x33 (3 components, type 3 Show UI)
        # Left: 85 (0x55)
        # Right: 90 (0x5A)
        # Case: 75 with charging bit 0x80 -> 0xCB
        payload = bytes([0x33, 85, 90, 75 | 0x80])
        state = FastPairPayload.parse(payload, rssi=-50)
        self.assertIsNotNone(state)
        self.assertEqual(state.left_battery, 85)
        self.assertEqual(state.right_battery, 90)
        self.assertEqual(state.case_battery, 75)
        self.assertFalse(state.is_left_charging)
        self.assertFalse(state.is_right_charging)
        self.assertTrue(state.is_case_charging)

    def test_tracker_smart_case_memory(self):
        tracker = BudoraTracker()
        
        # 1. First advertisement with case battery reported
        s1 = BudoraState(
            model=BudoraModel.AIRPODS_GEN_2,
            left_battery=100,
            right_battery=100,
            case_battery=80,
            is_left_charging=False,
            is_right_charging=False,
            is_case_charging=False,
            is_lid_open=True
        )
        res1 = tracker.on_advertisement("AA:BB:CC:DD:EE:FF", -50, s1)
        self.assertEqual(res1.case_battery, 80)
        self.assertFalse(res1.case_is_memorized)

        # 2. Later advertisement when pods are out of case (case reports None)
        s2 = BudoraState(
            model=BudoraModel.AIRPODS_GEN_2,
            left_battery=90,
            right_battery=90,
            case_battery=None,
            is_left_charging=False,
            is_right_charging=False,
            is_case_charging=False,
            is_lid_open=False
        )
        res2 = tracker.on_advertisement("AA:BB:CC:DD:EE:FF", -52, s2)
        # Smart Case Memory must restore the last seen 80%
        self.assertEqual(res2.case_battery, 80)
        self.assertTrue(res2.case_is_memorized)

    def test_primary_priority_over_connected_audio_fallback(self):
        from unittest.mock import patch
        tracker = BudoraTracker()

        # Case 1: When no primary earbuds are advertising, fallback to connected Bluetooth audio (e.g. WS-036)
        with patch("tracker.get_connected_bluetooth_audio", return_value="WS-036"):
            state = tracker.check_expired(now=100.0)
            self.assertIsNotNone(state)
            self.assertEqual(state.device_name, "WS-036")
            self.assertTrue(state.is_connected)
            self.assertTrue(state.is_single_device)

        # Case 2: The moment primary earbuds (Fast Pair) advertise, they MUST override the fallback
        primary_earbuds = BudoraState(
            model=BudoraModel.GOOGLE_FAST_PAIR,
            left_battery=80,
            right_battery=75,
            case_battery=95,
            is_left_charging=False,
            is_right_charging=False,
            is_case_charging=False,
            is_lid_open=True,
            device_name="CMF Buds 2a"
        )
        selected = tracker.on_advertisement("11:22:33:44:55:66", -45, primary_earbuds, now=100.0)
        self.assertIsNotNone(selected)
        self.assertEqual(selected.device_name, "CMF Buds 2a")
        self.assertFalse(selected.is_single_device)
        self.assertEqual(selected.left_battery, 80)

        # Case 3: While primary earbuds are in active window, check_expired() continues to return primary earbuds
        with patch("tracker.get_connected_bluetooth_audio", return_value="WS-036"):
            state = tracker.check_expired(now=102.0)
            self.assertIsNotNone(state)
            self.assertEqual(state.device_name, "CMF Buds 2a")
            self.assertEqual(state.left_battery, 80)

        # Case 4: Once primary earbuds expire (after 8s TTL), it falls back to WS-036
        with patch("tracker.get_connected_bluetooth_audio", return_value="WS-036"):
            state = tracker.check_expired(now=115.0)
            self.assertIsNotNone(state)
            self.assertEqual(state.device_name, "WS-036")
            self.assertTrue(state.is_single_device)

        # Case 5: When WS-036 is turned off / disconnected, it emits Disconnected
        with patch("tracker.get_connected_bluetooth_audio", return_value=None):
            state = tracker.check_expired(now=116.0)
            self.assertIsNotNone(state)
            self.assertFalse(state.is_connected)

if __name__ == "__main__":
    unittest.main()
