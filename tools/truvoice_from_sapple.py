#!/usr/bin/env python3
"""Recover TruVoice's data image from the supported Sapple Mac app.

Usage: python3 tools/truvoice_from_sapple.py Sapple.app
Reads data and symbol offsets only; does not execute or modify the app.
The output is gitignored voice data, owned by its original rights holder.
This representation differs from upstream's generated assembly, so its text
will not match tools/truvoice_data.sha256. The recovered bytes are pinned below.
"""
import hashlib
from pathlib import Path
import struct
import subprocess
import sys


IMAGE_SHA256 = "c9a9eb0638678c079ab4e3ef013e036b22379be64ae8898f7ddd1c216efb2bb3"


def recover(path):
    data = path.read_bytes()
    if data[:4] != b"\xcf\xfa\xed\xfe":
        raise ValueError(f"Expected a thin 64-bit Mach-O: {path}")
    symbols = {}
    for line in subprocess.check_output(["nm", "-n", str(path)], text=True).splitlines():
        fields = line.split()
        if len(fields) == 3:
            symbols[fields[2]] = (int(fields[0], 16), fields[1])
    # In this supported build, the contiguous assembly image precedes tvtts.c's
    # first static variable. The digest check rejects other layouts or versions.
    start = symbols["_tv_data"][0]
    end = symbols["_g_ext"][0]
    offset = 32
    blob = None
    for _ in range(struct.unpack_from("<I", data, 16)[0]):
        command, size = struct.unpack_from("<II", data, offset)
        if command == 0x19:  # LC_SEGMENT_64
            vmaddr, _, fileoff, filesize = struct.unpack_from("<QQQQ", data, offset + 24)
            if vmaddr <= start < end <= vmaddr + filesize:
                blob = data[fileoff + start - vmaddr:fileoff + end - vmaddr]
        offset += size
    if blob is None or hashlib.sha256(blob).hexdigest() != IMAGE_SHA256:
        raise ValueError(f"Unsupported TruVoice data image: {path}")
    labels = {}
    for name, (address, kind) in symbols.items():
        if start <= address < end and kind == "D":
            if name not in ("tv_data", "_tv_data") and not name.startswith("_g_"):
                raise ValueError(f"Unexpected symbol in data image: {name}")
            labels.setdefault(address - start, set()).add(name)
    return blob, labels


def main():
    app = Path(sys.argv[1] if len(sys.argv) > 1 else "Sapple.app")
    image, labels = recover(app / "Contents/MacOS/Sapple")
    extension = app / "Contents/PlugIns/ClassicVoicesExtension.appex/Contents/MacOS/ClassicVoicesExtension"
    other_image, other_labels = recover(extension)
    if image != other_image or labels != other_labels:
        raise ValueError("App and extension TruVoice tables or symbols disagree")
    lines = ["/* Recovered from Sapple.app; original owners retain rights. */\n",
             "/* Data SHA-256: " + IMAGE_SHA256 + " */\n", "\t.data\n\t.balign 16\n"]
    boundaries = sorted(set(labels) | {len(image)})
    for index, at in enumerate(boundaries[:-1]):
        # Both spellings support Mach-O and ELF targets.
        names = labels[at] | {name[1:] for name in labels[at] if name.startswith("_")}
        for name in sorted(names):
            lines.append(f"\t.globl {name}\n{name}:\n")
        stop = boundaries[index + 1]
        for pos in range(at, stop, 24):
            lines.append("\t.byte " + ",".join(f"0x{b:02x}" for b in image[pos:min(pos + 24, stop)]) + "\n")
    output = Path(__file__).resolve().parent.parent / "TruVoiceData/tvdata.s"
    text = "".join(lines)
    if output.exists() and output.read_text() != text:
        raise ValueError(f"Refusing to replace existing tables: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(text)
    print(f"Recovered {len(image)} bytes, {len(labels)} symbol offsets into {output}")
    print(f"Verified matching data in app and extension: {IMAGE_SHA256}")


if __name__ == "__main__":
    main()
