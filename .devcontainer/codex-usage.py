#!/usr/bin/env python3
"""Retorna info básica do Codex disponível sem TUI."""
import subprocess, shutil, sys

if not shutil.which("codex"):
    print("codex não encontrado no PATH")
    sys.exit(0)

try:
    ver = subprocess.run(["codex", "--version"], capture_output=True, text=True, timeout=5)
    version = ver.stdout.strip() or ver.stderr.strip() or "?"
except Exception:
    version = "?"

print(f"Versão: {version}")
print("Limites: https://chatgpt.com/codex/settings/usage")
