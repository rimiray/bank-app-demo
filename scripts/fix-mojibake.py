#!/usr/bin/env python3
"""Find and fix common UTF-8 mojibake sequences in text files."""

from __future__ import annotations

import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SKIP_DIRS = {
    ".git",
    "node_modules",
    ".gradle",
    "build",
    ".railway",
    "design-review",
}
EXTS = {
    ".md",
    ".tsx",
    ".ts",
    ".kt",
    ".yml",
    ".yaml",
    ".txt",
    ".properties",
    ".kts",
    ".css",
    ".html",
    ".xml",
    ".json",
    ".ps1",
    ".py",
    ".sh",
    ".example",
}

# bad -> good. Use escapes so this script file itself stays ASCII-clean.
REPLACEMENTS: list[tuple[str, str]] = [
    # Double-encoded UTF-8
    ("\u00c3\u00a2\u00c2\u0080\u00c2\u0094", "\u2014"),  # —
    ("\u00c3\u00a2\u00c2\u0080\u00c2\u0093", "\u2013"),  # –
    ("\u00c3\u00a2\u00c2\u0080\u00c2\u0099", "'"),
    ("\u00c3\u00a2\u00c2\u0080\u00c2\u0098", "'"),
    ("\u00c3\u00a2\u00c2\u0080\u00c2\u009c", '"'),
    ("\u00c3\u00a2\u00c2\u0080\u00c2\u009d", '"'),
    ("\u00c3\u00a2\u00c2\u0086\u00c2\u0092", "\u2192"),  # →
    ("\u00c3\u00a2\u00c2\u0086\u00c2\u0090", "\u2190"),  # ←
    ("\u00c3\u0082\u00c2\u00b7", "\u00b7"),  # ·
    ("\u00c3\u00a2\u00c2\u0080\u00c2\u00a6", "\u2026"),  # …
    # Single-level mojibake (UTF-8 read as cp1252 / latin-1)
    ("\u00e2\u0080\u0094", "\u2014"),
    ("\u00e2\u0080\u0093", "\u2013"),
    ("\u00e2\u0080\u0099", "'"),
    ("\u00e2\u0080\u0098", "'"),
    ("\u00e2\u0080\u009c", '"'),
    ("\u00e2\u0080\u009d", '"'),
    ("\u00e2\u0086\u0092", "\u2192"),
    ("\u00e2\u0080\u00a6", "\u2026"),
    ("\u00c2\u00b7", "\u00b7"),
]


def should_scan(path: Path) -> bool:
    return path.suffix.lower() in EXTS or path.name in {".env.example", "AGENTS.md"}


def main() -> None:
    fixed: list[str] = []
    remaining: list[str] = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS and not d.startswith(".")]
        for name in filenames:
            path = Path(dirpath) / name
            if not should_scan(path):
                continue
            raw = path.read_bytes()
            try:
                text = raw.decode("utf-8")
            except UnicodeDecodeError:
                continue
            original = text
            for bad, good in REPLACEMENTS:
                text = text.replace(bad, good)
            if text != original:
                path.write_bytes(text.encode("utf-8"))
                fixed.append(str(path.relative_to(ROOT)))
            # Flag leftover double-encoding markers
            if "\u00c3\u00a2" in text or "\u00e2\u0080" in text:
                remaining.append(str(path.relative_to(ROOT)))

    print("FIXED:")
    for f in fixed or ["(none)"]:
        print(" ", f)
    print("REMAINING_SUSPECT:")
    for f in remaining or ["(none)"]:
        print(" ", f)


if __name__ == "__main__":
    main()
