#!/usr/bin/env python3
"""Minimal PNG codec built on the standard library.

The slicing step has to decode the large ``gpt-image-1`` PNGs and encode the
small RGBA sprites. ``tools/art`` deliberately has no third-party dependency
so the test suite runs on a bare Python in CI, and pulling Pillow into the
workflow just to crop 55 files is not worth the dependency, so this module
covers the subset of PNG the pipeline actually meets:

* decode: 8-bit, non-interlaced gray / RGB / palette / gray+alpha / RGBA,
* encode: 8-bit RGBA.

Anything else raises :class:`PngError` naming what was unsupported instead of
quietly producing garbage.
"""

from __future__ import annotations

import struct
import zlib
from pathlib import Path
from typing import List, Optional, Tuple

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"

#: Samples per pixel for each PNG colour type.
_CHANNELS = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}


class PngError(ValueError):
    """Raised when a file is not a PNG this codec can handle."""


# --- Encoding ----------------------------------------------------------------


def write_png(path, width: int, height: int, rows) -> None:
    """Write RGBA *rows* to *path* as an 8-bit RGBA PNG."""
    Path(path).write_bytes(encode_png(width, height, rows))


def encode_png(width: int, height: int, rows) -> bytes:
    """Encode RGBA *rows* (``height`` rows of ``width * 4`` bytes).

    Filter type 0 (none) is used for every scanline: the outputs are tiny and
    a fixed filter keeps the bytes reproducible for the regression tests.
    """
    if width <= 0 or height <= 0:
        raise PngError(f"bad image size {width}x{height}")
    rows = list(rows)
    if len(rows) != height:
        raise PngError(f"expected {height} rows, got {len(rows)}")
    raw = bytearray()
    for index, row in enumerate(rows):
        expected = width * 4
        if len(row) != expected:
            raise PngError(
                f"row {index} has {len(row)} bytes, expected {expected} for RGBA"
            )
        raw.append(0)
        raw.extend(row)
    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (
        PNG_SIGNATURE
        + _chunk(b"IHDR", header)
        + _chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + _chunk(b"IEND", b"")
    )


def _chunk(ctype: bytes, body: bytes) -> bytes:
    crc = zlib.crc32(ctype + body) & 0xFFFFFFFF
    return struct.pack(">I", len(body)) + ctype + body + struct.pack(">I", crc)


# --- Decoding ----------------------------------------------------------------


def read_png(path) -> Tuple[int, int, List[bytearray]]:
    """Decode *path* into ``(width, height, rows)`` with RGBA rows."""
    return decode_png(Path(path).read_bytes())


def decode_png(data: bytes) -> Tuple[int, int, List[bytearray]]:
    """Decode PNG *data* into ``(width, height, rows)`` with RGBA rows."""
    if data[:8] != PNG_SIGNATURE:
        raise PngError("not a PNG file (bad signature)")

    pos = 8
    header: Optional[Tuple] = None
    palette: Optional[bytes] = None
    transparency: Optional[bytes] = None
    idat = bytearray()
    while pos + 12 <= len(data):
        length, ctype = struct.unpack(">I4s", data[pos : pos + 8])
        body_start = pos + 8
        body_end = body_start + length
        if body_end + 4 > len(data):
            raise PngError("truncated PNG chunk")
        body = data[body_start:body_end]
        crc_bytes = data[body_end : body_end + 4]
        if zlib.crc32(ctype + body) & 0xFFFFFFFF != struct.unpack(">I", crc_bytes)[0]:
            raise PngError(f"bad CRC in {ctype.decode('latin1', 'replace')} chunk")
        pos = body_end + 4
        if ctype == b"IHDR":
            header = struct.unpack(">IIBBBBB", body)
        elif ctype == b"PLTE":
            palette = body
        elif ctype == b"tRNS":
            transparency = body
        elif ctype == b"IDAT":
            idat.extend(body)
        elif ctype == b"IEND":
            break

    if header is None:
        raise PngError("PNG has no IHDR chunk")
    width, height, bit_depth, color_type, compression, filter_method, interlace = header
    if compression != 0 or filter_method != 0:
        raise PngError(f"unsupported compression/filter method {compression}/{filter_method}")
    if interlace != 0:
        raise PngError("interlaced PNGs are not supported")
    if bit_depth != 8:
        raise PngError(f"unsupported bit depth {bit_depth} (only 8 is handled)")
    if color_type not in _CHANNELS:
        raise PngError(f"unsupported colour type {color_type}")
    channels = _CHANNELS[color_type]
    if color_type == 3 and palette is None:
        raise PngError("palettised PNG without a PLTE chunk")

    raw = zlib.decompress(bytes(idat)) if idat else b""
    stride = width * channels
    if len(raw) < (stride + 1) * height:
        raise PngError("PNG pixel data is shorter than the header promises")

    scanlines = _unfilter(raw, width, height, channels)
    return width, height, _to_rgba(
        scanlines, width, color_type, channels, palette, transparency
    )


def _unfilter(raw: bytes, width: int, height: int, channels: int) -> List[bytearray]:
    stride = width * channels
    rows: List[bytearray] = []
    previous = bytearray(stride)
    pos = 0
    for _ in range(height):
        filter_type = raw[pos]
        pos += 1
        line = bytearray(raw[pos : pos + stride])
        pos += stride
        if filter_type == 0:
            pass
        elif filter_type == 1:
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif filter_type == 2:
            for i in range(stride):
                line[i] = (line[i] + previous[i]) & 0xFF
        elif filter_type == 3:
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + previous[i]) >> 1)) & 0xFF
        elif filter_type == 4:
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = previous[i]
                c = previous[i - channels] if i >= channels else 0
                p = a + b - c
                pa = p - a if p >= a else a - p
                pb = p - b if p >= b else b - p
                pc = p - c if p >= c else c - p
                if pa <= pb and pa <= pc:
                    predictor = a
                elif pb <= pc:
                    predictor = b
                else:
                    predictor = c
                line[i] = (line[i] + predictor) & 0xFF
        else:
            raise PngError(f"unsupported scanline filter {filter_type}")
        rows.append(line)
        previous = line
    return rows


def _to_rgba(
    scanlines: List[bytearray],
    width: int,
    color_type: int,
    channels: int,
    palette: Optional[bytes],
    transparency: Optional[bytes],
) -> List[bytearray]:
    # Strided slicing turns the per-pixel copy into a handful of C-level
    # operations; only the palettised path still needs a Python loop.
    alpha = b"\xff" * width
    rows: List[bytearray] = []
    for line in scanlines:
        out = bytearray(width * 4)
        if color_type == 6:
            out[:] = line
        elif color_type == 2:
            out[0::4] = line[0::3]
            out[1::4] = line[1::3]
            out[2::4] = line[2::3]
            out[3::4] = alpha
        elif color_type == 0:
            out[0::4] = line
            out[1::4] = line
            out[2::4] = line
            out[3::4] = alpha
        elif color_type == 4:
            gray = line[0::2]
            out[0::4] = gray
            out[1::4] = gray
            out[2::4] = gray
            out[3::4] = line[1::2]
        else:  # palette
            for x in range(width):
                index = line[x]
                offset = x * 4
                base = index * 3
                out[offset] = palette[base]
                out[offset + 1] = palette[base + 1]
                out[offset + 2] = palette[base + 2]
                out[offset + 3] = (
                    transparency[index]
                    if transparency is not None and index < len(transparency)
                    else 255
                )
        rows.append(out)
    return rows
