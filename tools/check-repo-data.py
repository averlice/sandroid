#!/usr/bin/env python3
"""Reject tracked voice data and packaged binaries; local app assets remain allowed."""
from pathlib import PurePosixPath
import subprocess
import sys


DATA_DIRS = {
    "voicedata", "sapi4voices", "annavoice", "onecorevoice", "truvoicedata",
    "neuralvoices", "neuralsdk", "_installers",
}
DATA_SUFFIXES = {
    ".spd", ".sdf", ".lxa", ".dll", ".vce", ".exe", ".cab", ".msi",
    ".lex", ".dic", ".dat", ".apm", ".bep", ".nnm", ".tdat", ".tvdata",
    ".dylib", ".so", ".aar", ".apk", ".aab", ".ipa", ".wav", ".license",
    ".zip", ".7z", ".iso", ".wim",
}


def forbidden(name):
    path = PurePosixPath(name.lower())
    return (
        bool(DATA_DIRS.intersection(path.parts))
        or any(part.endswith(".app") for part in path.parts)
        or path.suffix in DATA_SUFFIXES
        or path.name in {"model.key", "tvdata.s", "sam_data.c"}
        or path.name.startswith("m1033dsk.")
        or str(path).startswith(("sapi4/data/", "sapi4/bundle/", "sapi4/ref/"))
    )


def main():
    names = subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0")
    rejected = sorted({name for name in names if name and forbidden(name)})
    if rejected:
        print("Voice data and packaged binaries must stay outside Git:", file=sys.stderr)
        print("\n".join(rejected), file=sys.stderr)
        return 1
    print("Repository check passed: no tracked voice data or packaged binaries.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
