#!/usr/bin/env python3
"""Nagebootste ESP voor schermtests: WebSocket op poort 81, protocol volgens docs/PROTOCOL.md.
Scenario uit /tmp/esp_scenario.json: {"running": bool, "elapsed_s": int, "proc_s": 300|180,
"led": bool, "horn": bool}. Bij elke wijziging van het bestand wordt de starttijd opnieuw gezet."""
import asyncio, json, os, sys, time
import websockets

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 81
SCEN = "/tmp/esp_scenario.json"
T0 = time.monotonic()
BOOT = 1_000_000          # ESP "draait al" ~17 minuten
state = {"running": True, "elapsed_s": 100, "proc_s": 300, "led": True, "horn": False, "start_ms": 0, "mtime": None}


def esp_ms():
    return int((time.monotonic() - T0) * 1000) + BOOT


state["start_ms"] = esp_ms() - state["elapsed_s"] * 1000


def load():
    try:
        m = os.path.getmtime(SCEN)
    except OSError:
        return
    if m == state["mtime"]:
        return
    state["mtime"] = m
    try:
        d = json.load(open(SCEN))
    except Exception:
        return
    state.update({k: d[k] for k in ("running", "elapsed_s", "proc_s", "led", "horn") if k in d})
    state["start_ms"] = esp_ms() - int(state["elapsed_s"]) * 1000


def status():
    load()
    now = esp_ms()
    p = state["proc_s"]
    if state["running"]:
        k = (now - state["start_ms"]) // 1000
        rem = p if k <= 0 else p - (((k - 1) % p) + 1)
    else:
        rem = p
    epoch = int(time.time() * 1000)
    base = (epoch // 60000 + 45) * 60000
    sched = [
        {"id": 3, "press": base - 300000, "target": base, "kind": 0, "proc_s": 300},
        {"id": 4, "press": base + 3600000, "target": base + 3600000, "kind": 1, "proc_s": 180},
    ]
    first_min = p // 60
    m = rem // 60
    manual = (not state["running"]) or m in (first_min, first_min - 1)
    return {
        "type": "status", "fw": "V7B", "esp_ms": now, "running": state["running"],
        "start_ms": state["start_ms"] if state["running"] else now, "repeat": True, "switch_repeat": True,
        "proc_s": p, "switch_proc_s": p, "min": m, "sec": rem % 60,
        "led_panel": state["led"], "horn": state["horn"], "manual_allowed": manual,
        "time_synced": True, "epoch_now": epoch, "ssid": "ZZ-WedstrijdTimer", "ip": "192.168.4.1",
        "clients": 1, "uptime_s": now // 1000, "reset_reason": "aan", "restored": False,
        "heap_free": 180000, "sched": sched,
    }


async def handler(ws):
    async def sender():
        while True:
            await ws.send(json.dumps(status()))
            await asyncio.sleep(0.5)
    task = asyncio.create_task(sender())
    try:
        async for msg in ws:
            try:
                d = json.loads(msg)
            except Exception:
                continue
            cmd, i = d.get("cmd", ""), d.get("id", 0)
            if cmd == "sync":
                await ws.send(json.dumps({"type": "sync", "id": i, "t": d.get("t", 0), "esp_ms": esp_ms()}))
            elif cmd == "horn":
                pass
            else:
                await ws.send(json.dumps({"type": "ack", "id": i, "ok": True}))
    finally:
        task.cancel()


async def main():
    async with websockets.serve(handler, "0.0.0.0", PORT):
        print("nep-ESP luistert op", PORT, flush=True)
        await asyncio.Future()

asyncio.run(main())
