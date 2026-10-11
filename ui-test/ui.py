#!/usr/bin/env python3
"""Hulpmiddel voor de emulator: tikken op tekst (via uiautomator, met terugvaltoets op coördinaten)."""
import re, subprocess, sys, time, xml.etree.ElementTree as ET

def sh(*a, check=False):
    return subprocess.run(["adb", "shell", *a], capture_output=True, text=True, timeout=40)

def dump():
    r = sh("uiautomator", "dump", "/sdcard/ui.xml")
    if "dumped" not in r.stdout:
        return None
    out = subprocess.run(["adb", "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True, text=True).stdout
    try:
        return ET.fromstring(out)
    except Exception:
        return None

def find(root, text):
    best = None
    for n in root.iter("node"):
        t = (n.get("text") or "") + "|" + (n.get("content-desc") or "")
        if text in t:
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                best = ((x1 + x2) // 2, (y1 + y2) // 2)
                break
    return best

def tap(text, fx=None, fy=None):
    root = dump()
    pos = find(root, text) if root is not None else None
    if pos is None and fx is not None:
        pos = (fx, fy)
        print(f"tik '{text}': terugval {pos}")
    elif pos is None:
        print(f"tik '{text}': niet gevonden")
        return False
    else:
        print(f"tik '{text}': {pos}")
    sh("input", "tap", str(pos[0]), str(pos[1]))
    return True

def scroll_to(text, tries=6):
    for _ in range(tries):
        root = dump()
        if root is not None and find(root, text):
            return True
        if root is None:
            print("scrollto: dump mislukt")
        else:
            print("scrollto: zichtbaar:", [((n.get("text") or "") + "/" + (n.get("content-desc") or ""))[:40] for n in root.iter("node") if n.get("text") or n.get("content-desc")][:30])
        sh("input", "swipe", "540", "1700", "540", "900", "500")
        time.sleep(0.8)
    return False

if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "tap":
        fx = int(sys.argv[3]) if len(sys.argv) > 3 else None
        fy = int(sys.argv[4]) if len(sys.argv) > 4 else None
        sys.exit(0 if tap(sys.argv[2], fx, fy) else 1)
    if cmd == "scrollto":
        sys.exit(0 if scroll_to(sys.argv[2]) else 1)
    if cmd == "dumpok":
        sys.exit(0 if dump() is not None else 1)
