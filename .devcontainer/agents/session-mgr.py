#!/usr/bin/env python3
"""Gerencia sessões dos agentes por thread do Slack (NEOTOOL_THREAD)."""
import json, os, sys

SESSION_DIR = "/tmp/bridge-sessions"

def get_session_file():
    thread = os.environ.get("NEOTOOL_THREAD", "default")
    os.makedirs(SESSION_DIR, exist_ok=True)
    return os.path.join(SESSION_DIR, f"{thread}.json")

def load(agent: str) -> str:
    try:
        data = json.load(open(get_session_file()))
        return data.get(f"{agent}_session", "")
    except Exception:
        return ""

def save(agent: str, session_id: str):
    f = get_session_file()
    try:
        data = json.load(open(f)) if os.path.exists(f) else {}
    except Exception:
        data = {}
    data[f"{agent}_session"] = session_id
    json.dump(data, open(f, "w"))

if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "load":
        print(load(sys.argv[2]))
    elif cmd == "save":
        save(sys.argv[2], sys.argv[3])
