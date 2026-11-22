import asyncio
import threading
from typing import Callable, Optional
from bleak import BleakScanner
from proximity_payload import ProximityPayload
from fast_pair_payload import FastPairPayload
from tracker import BudoraTracker
from models import BudoraState

class BudoraScanner:
    def __init__(self, on_state_changed: Callable[[BudoraState], None]):
        self.on_state_changed = on_state_changed
        self.tracker = BudoraTracker()
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._loop: Optional[asyncio.AbstractEventLoop] = None

    def start(self):
        if self._running:
            return
        self._running = True
        self._thread = threading.Thread(target=self._run_loop, daemon=True)
        self._thread.start()

    def stop(self):
        self._running = False
        if self._loop and self._loop.is_running():
            self._loop.call_soon_threadsafe(self._loop.stop)

    def _run_loop(self):
        self._loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self._loop)
        try:
            self._loop.run_until_complete(self._scan_task())
        except Exception as e:
            print(f"Scanner exception: {e}")
        finally:
            self._loop.close()

    async def _scan_task(self):
        def detection_callback(device, adv_data):
            rssi = adv_data.rssi if adv_data.rssi is not None else -100
            addr = device.address or "Unknown"
            name = device.name or adv_data.local_name or "Earbuds"

            state = None
            # 1. Check Apple Proximity Pairing (0x004C)
            if adv_data.manufacturer_data:
                apple_data = adv_data.manufacturer_data.get(ProximityPayload.APPLE_COMPANY_ID)
                if apple_data:
                    state = ProximityPayload.parse(apple_data, rssi=rssi)

            # 2. Check Google Fast Pair (0xFE2C)
            if state is None and adv_data.service_data:
                for uuid, data in adv_data.service_data.items():
                    if "fe2c" in uuid.lower():
                        state = FastPairPayload.parse(data, rssi=rssi)
                        break

            if state is not None:
                state.device_name = name
                best = self.tracker.on_advertisement(addr, rssi, state)
                if best is not None and self.on_state_changed:
                    self.on_state_changed(best)

        scanner = BleakScanner(detection_callback=detection_callback)
        await scanner.start()
        print("Budora BLE scanner running...")

        # Immediate initial check on startup
        initial_state = self.tracker.check_expired()
        if initial_state is not None and self.on_state_changed:
            self.on_state_changed(initial_state)

        while self._running:
            await asyncio.sleep(1.0)
            expired_state = self.tracker.check_expired()
            if expired_state is not None and self.on_state_changed:
                self.on_state_changed(expired_state)
        await scanner.stop()
