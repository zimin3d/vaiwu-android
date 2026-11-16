#!/usr/bin/env python3
"""Slice the raw generated art into the sprite sizes the app ships.

``gen_art.py`` asks an image model for a large square PNG and writes it to
``tools/art/out/``. The app, the firmware previews and the web gallery all
expect 96x128 character sprites and 96x96 monsters, so this step turns the
raw generations into those files::

    python3 tools/art/slice_art.py            # slice everything found
    python3 tools/art/slice_art.py --list     # what would be built
    python3 tools/art/slice_art.py --dry-run  # slice, but write nothing

For every asset it

1. cuts the flat magenta key background (#FF00FF), keeping only the part that
   is connected to the image border (so a key-coloured pocket inside the
   character survives),
   and softens the anti-aliased ring that touches it,
2. crops to that content with a small margin,
3. scales it to fit the target box without distortion and centres it on a
   fully transparent canvas.

It then rebuilds the two contact sheets the gallery links to
(``sprites_preview.png`` and ``monsters_preview.png``) from the sliced
sprites, so the sheets cannot drift from the files next to them; a sheet is
skipped when any of its sprites is missing rather than half-drawn.

Only the standard library is used, so the tool and its tests run anywhere
Python 3.9+ does - see ``pngio.py`` for the PNG half of that promise.
"""

from __future__ import annotations

import argparse
import math
import sys
from collections import deque
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))

import art_prompts  # noqa: E402  (needs the path tweak above)
import pngio  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SRC = REPO_ROOT / "tools" / "art" / "out"
DEFAULT_DEST = REPO_ROOT / "external" / "vaiwu" / "firmware" / "assets"

#: Flat colour the generator paints behind every sprite (chroma key). Pure
#: magenta is far from any colour used in the art, so it never matches a
#: character pixel by accident.
KEY_COLOUR = (255, 0, 255)

#: Pixels whose RGB distance to the key is at or below this are background.
KEY_TOLERANCE = 60

#: Distance band above the tolerance that is faded instead of cut. The
#: anti-aliased edge of a character fades out, so no hard pink fringe remains
#: after down-scaling. 0 disables the feather.
FEATHER_WIDTH = 60

#: Breathing room around the content, as a fraction of its shorter side.
MARGIN_RATIO = 0.02

#: Alpha at or below this does not count as visible content.
ALPHA_CUTOFF = 8


class SliceError(RuntimeError):
    """Raised when an image cannot be sliced into a usable sprite."""


# --- Background removal ------------------------------------------------------


def _scan_key(
    rows: Sequence[bytearray], width: int, height: int, tolerance: int, band: int
) -> Tuple[bytearray, List[int]]:
    """Split pixels by their distance to :data:`KEY_COLOUR`.

    ``core`` marks every pixel within *tolerance*; the returned indices are the
    pixels inside the feather band just outside it, i.e. the ones a fade may
    touch.
    """
    key_r, key_g, key_b = KEY_COLOUR
    core_limit = tolerance * tolerance
    band_limit = (tolerance + band) * (tolerance + band)
    core = bytearray(width * height)
    candidates: List[int] = []
    for y in range(height):
        row = rows[y]
        base = y * width
        for x in range(width):
            offset = x * 4
            dr = row[offset] - key_r
            dg = row[offset + 1] - key_g
            db = row[offset + 2] - key_b
            distance = dr * dr + dg * dg + db * db
            if distance <= core_limit:
                core[base + x] = 1
            elif distance <= band_limit:
                candidates.append(base + x)
    return core, candidates


def remove_background(
    rows: Sequence[bytearray],
    width: int,
    height: int,
    tolerance: int = KEY_TOLERANCE,
    feather: int = FEATHER_WIDTH,
    cutoff: int = ALPHA_CUTOFF,
) -> bytearray:
    """Return the alpha plane with every key-coloured pixel cut out.

    The key colour is not used by any character, so a key-coloured gap enclosed
    by the outline (between a hand and the body, say) is background too. The
    feather fades the anti-aliased pixels next to the cut. The original alpha is
    kept for everything that is not background.
    """
    core, candidates = _scan_key(rows, width, height, tolerance, feather)
    background = core

    alpha = bytearray(width * height)
    for y, row in enumerate(rows):
        base = y * width
        alpha[base : base + width] = row[3::4]
    for index, is_background in enumerate(background):
        if is_background:
            alpha[index] = 0

    if feather <= 0:
        return alpha

    for index in candidates:
        if background[index]:
            continue
        y, x = divmod(index, width)
        touches = False
        for dy in (-1, 0, 1):
            neighbour_y = y + dy
            if neighbour_y < 0 or neighbour_y >= height:
                continue
            row_start = neighbour_y * width
            for dx in (-1, 0, 1):
                if dx == 0 and dy == 0:
                    continue
                neighbour_x = x + dx
                if neighbour_x < 0 or neighbour_x >= width:
                    continue
                if background[row_start + neighbour_x]:
                    touches = True
                    break
            if touches:
                break
        if not touches:
            continue
        offset = x * 4
        row = rows[y]
        dr = row[offset] - KEY_COLOUR[0]
        dg = row[offset + 1] - KEY_COLOUR[1]
        db = row[offset + 2] - KEY_COLOUR[2]
        distance = math.isqrt(dr * dr + dg * dg + db * db)
        value = (distance - tolerance) * 255 // feather
        value = 0 if value < 0 else min(255, value)
        alpha[index] = min(alpha[index], value)
    return alpha


def apply_alpha(rows: List[bytearray], width: int, alpha: bytearray) -> None:
    """Write *alpha* back into the RGBA rows in place."""
    for y, row in enumerate(rows):
        base = y * width
        row[3::4] = alpha[base : base + width]


# --- Cropping and scaling ----------------------------------------------------


def content_box(
    alpha: bytearray, width: int, height: int, cutoff: int = ALPHA_CUTOFF
) -> Optional[Tuple[int, int, int, int]]:
    """Bounding box ``(left, top, right, bottom)`` of visible pixels."""
    left, right = width, -1
    top, bottom = -1, -1
    for y in range(height):
        base = y * width
        first = -1
        for x in range(width):
            if alpha[base + x] > cutoff:
                first = x
                break
        if first < 0:
            continue
        if top < 0:
            top = y
        bottom = y
        if first < left:
            left = first
        for x in range(width - 1, -1, -1):
            if alpha[base + x] > cutoff:
                if x > right:
                    right = x
                break
    if right < 0:
        return None
    return left, top, right + 1, bottom + 1


def resize_rgba(
    rows: Sequence[bytearray], source_w: int, source_h: int, target_w: int, target_h: int
) -> List[bytearray]:
    """Box-filter *rows* down to ``target_w x target_h``.

    The filter works on premultiplied colour so transparent pixels do not drag
    black into the average, and it weights partial edge pixels by the area they
    actually cover, which keeps the result free of the banding a plain
    nearest-neighbour drop would produce.
    """
    x_edges = [index * source_w / target_w for index in range(target_w + 1)]
    y_edges = [index * source_h / target_h for index in range(target_h + 1)]
    output: List[bytearray] = []
    for dy in range(target_h):
        y0 = y_edges[dy]
        y1 = y_edges[dy + 1]
        start_y = int(y0)
        end_y = min(source_h, int(y1) + (0 if y1 == int(y1) else 1))
        target_row = bytearray(target_w * 4)
        for dx in range(target_w):
            x0 = x_edges[dx]
            x1 = x_edges[dx + 1]
            start_x = int(x0)
            end_x = min(source_w, int(x1) + (0 if x1 == int(x1) else 1))
            area = 0.0
            premultiplied = 0.0
            red = green = blue = 0.0
            for sy in range(start_y, end_y):
                weight_y = min(y1, sy + 1) - max(y0, sy)
                if weight_y <= 0.0:
                    continue
                source = rows[sy]
                for sx in range(start_x, end_x):
                    weight_x = min(x1, sx + 1) - max(x0, sx)
                    if weight_x <= 0.0:
                        continue
                    weight = weight_x * weight_y
                    area += weight
                    offset = sx * 4
                    alpha_value = source[offset + 3]
                    if alpha_value:
                        weighted = weight * alpha_value
                        premultiplied += weighted
                        red += source[offset] * weighted
                        green += source[offset + 1] * weighted
                        blue += source[offset + 2] * weighted
            if premultiplied > 0.0:
                target_row[dx * 4] = _clamp_byte(red / premultiplied)
                target_row[dx * 4 + 1] = _clamp_byte(green / premultiplied)
                target_row[dx * 4 + 2] = _clamp_byte(blue / premultiplied)
            if area > 0.0:
                target_row[dx * 4 + 3] = _clamp_byte(premultiplied / area)
        output.append(target_row)
    return output


def fit_into(
    rows: Sequence[bytearray], width: int, height: int, target: Tuple[int, int]
) -> List[bytearray]:
    """Scale *rows* to fit *target* and centre it on a transparent canvas."""
    target_w, target_h = target
    scale = min(target_w / width, target_h / height)
    scaled_w = max(1, min(target_w, int(round(width * scale))))
    scaled_h = max(1, min(target_h, int(round(height * scale))))
    scaled = resize_rgba(rows, width, height, scaled_w, scaled_h)
    offset_x = (target_w - scaled_w) // 2
    offset_y = (target_h - scaled_h) // 2
    canvas = [bytearray(target_w * 4) for _ in range(target_h)]
    for y in range(scaled_h):
        row = canvas[offset_y + y]
        row[offset_x * 4 : (offset_x + scaled_w) * 4] = scaled[y]
    return canvas


def convert_rgba(
    rows: List[bytearray],
    width: int,
    height: int,
    target: Tuple[int, int],
    *,
    tolerance: int = KEY_TOLERANCE,
    feather: int = FEATHER_WIDTH,
    margin_ratio: float = MARGIN_RATIO,
    cutoff: int = ALPHA_CUTOFF,
) -> List[bytearray]:
    """Cut the background out of *rows* and slice them into *target*."""
    alpha = remove_background(rows, width, height, tolerance, feather, cutoff)
    apply_alpha(rows, width, alpha)
    box = content_box(alpha, width, height, cutoff)
    if box is None:
        raise SliceError("image has no visible content after background removal")
    left, top, right, bottom = box
    margin = int(round(margin_ratio * min(right - left, bottom - top)))
    if margin < 0:
        margin = 0
    left = max(0, left - margin)
    top = max(0, top - margin)
    right = min(width, right + margin)
    bottom = min(height, bottom + margin)
    cropped = [row[left * 4 : right * 4] for row in rows[top:bottom]]
    return fit_into(cropped, right - left, bottom - top, target)


def _clamp_byte(value: float) -> int:
    rounded = int(value + 0.5)
    if rounded < 0:
        return 0
    if rounded > 255:
        return 255
    return rounded


# --- Contact sheets ----------------------------------------------------------

#: Must match ``kBgColor`` in VaiwuUi.cpp and the sheets ``gen_sprites.py``
#: writes, so the gallery contact sheets keep looking the same.
PREVIEW_BACKGROUND = (24, 18, 43)
PREVIEW_PAD = 8
PREVIEW_SCALE = 2

SPRITES_PREVIEW = "sprites_preview.png"
MONSTERS_PREVIEW = "monsters_preview.png"


def _paste_sprite(
    canvas: List[bytearray],
    sprite: Sequence[bytearray],
    sprite_width: int,
    sprite_height: int,
    offset_x: int,
    offset_y: int,
) -> None:
    """Alpha-composite one sprite onto an opaque sheet."""
    for y in range(sprite_height):
        source = sprite[y]
        target = canvas[offset_y + y]
        for x in range(sprite_width):
            index = x * 4
            alpha_value = source[index + 3]
            if alpha_value == 0:
                continue
            out = (offset_x + x) * 4
            if alpha_value == 255:
                target[out : out + 3] = source[index : index + 3]
            else:
                inverse = 255 - alpha_value
                for channel in range(3):
                    target[out + channel] = (
                        source[index + channel] * alpha_value
                        + target[out + channel] * inverse
                    ) // 255
            target[out + 3] = 255


def _nearest_x2(rows: Sequence[bytearray], width: int, height: int) -> List[bytearray]:
    """Double both dimensions with nearest-neighbour, as the old sheets do."""
    output: List[bytearray] = []
    for row in rows:
        wide = bytearray(width * PREVIEW_SCALE * 4)
        for x in range(width):
            pixel = row[x * 4 : x * 4 + 4]
            start = x * PREVIEW_SCALE * 4
            wide[start : start + 4] = pixel
            wide[start + 4 : start + 8] = pixel
        output.append(wide)
        output.append(bytearray(wide))
    return output


def compose_sheet(
    sprites: Sequence[Tuple[Sequence[bytearray], int, int]],
    columns: int,
    cell: Tuple[int, int],
    pad: int = PREVIEW_PAD,
    background: Tuple[int, int, int] = PREVIEW_BACKGROUND,
) -> Tuple[List[bytearray], int, int]:
    """Lay *sprites* out on an opaque contact sheet, already scaled up."""
    cell_width, cell_height = cell
    rows_count = (len(sprites) + columns - 1) // columns
    width = pad + columns * (cell_width + pad)
    height = pad + rows_count * (cell_height + pad)
    pixel = bytes(background) + b"\xff"
    canvas = [bytearray(pixel * width) for _ in range(height)]
    for index, (sprite_rows, sprite_width, sprite_height) in enumerate(sprites):
        row, column = divmod(index, columns)
        _paste_sprite(
            canvas,
            sprite_rows,
            sprite_width,
            sprite_height,
            pad + column * (cell_width + pad),
            pad + row * (cell_height + pad),
        )
    scaled = _nearest_x2(canvas, width, height)
    return scaled, width * PREVIEW_SCALE, height * PREVIEW_SCALE


def write_previews(dest: Path) -> Tuple[List[str], List[str]]:
    """Rebuild the contact sheets in *dest* from the sliced sprites.

    Returns ``(written, skipped)``; a sheet whose sprites are not all present
    is skipped rather than half-drawn, so a partial run cannot publish a
    misleading contact sheet.
    """
    plans = [
        (
            SPRITES_PREVIEW,
            [
                f"sprite_{character}_{emotion}"
                for character in art_prompts.CHARACTERS
                for emotion in art_prompts.EMOTIONS
            ],
            len(art_prompts.EMOTIONS),
            (art_prompts.TARGETS["character"].width, art_prompts.TARGETS["character"].height),
        ),
        (
            MONSTERS_PREVIEW,
            [f"monster_{monster}" for monster in art_prompts.MONSTERS],
            len(art_prompts.MONSTERS),
            (art_prompts.TARGETS["monster"].width, art_prompts.TARGETS["monster"].height),
        ),
    ]
    written: List[str] = []
    skipped: List[str] = []
    for filename, names, columns, cell in plans:
        sprites = []
        missing = 0
        for name in names:
            path = dest / f"{name}.png"
            if not path.exists():
                missing += 1
                continue
            sprite_width, sprite_height, sprite_rows = pngio.read_png(path)
            sprites.append((sprite_rows, sprite_width, sprite_height))
        if missing:
            skipped.append(f"{filename} ({missing} missing)")
            continue
        rows, width, height = compose_sheet(sprites, columns, cell)
        pngio.write_png(dest / filename, width, height, rows)
        written.append(filename)
    return written, skipped


# --- Asset discovery ---------------------------------------------------------


def expected_names() -> List[str]:
    return [asset.name for asset in art_prompts.build_assets()]


def discover(src: Path, names: Optional[Sequence[str]] = None) -> Dict[str, Tuple]:
    """Map asset name to ``(asset, path)`` for every generated PNG under *src*."""
    known = {asset.name: asset for asset in art_prompts.build_assets()}
    if names:
        unknown = [name for name in names if name not in known]
        if unknown:
            raise ValueError(
                "unknown asset(s): " + ", ".join(unknown) + " - see --list"
            )
    wanted = set(names) if names else None
    found: Dict[str, Tuple] = {}
    for path in sorted(Path(src).rglob("*.png")):
        asset = known.get(path.stem)
        if asset is None:
            continue
        if wanted is not None and asset.name not in wanted:
            continue
        found[asset.name] = (asset, path)
    return found


# --- CLI ---------------------------------------------------------------------


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Slice the raw generated art into the app sprite sizes.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--src",
        type=Path,
        default=DEFAULT_SRC,
        help="folder holding the raw generated PNGs, searched recursively",
    )
    parser.add_argument(
        "--dest",
        type=Path,
        default=DEFAULT_DEST,
        help="folder for the sliced PNGs",
    )
    parser.add_argument(
        "--assets",
        nargs="+",
        metavar="NAME",
        help="only these asset names, e.g. sprite_aoi_idle monster_slime",
    )
    parser.add_argument(
        "--key-tolerance",
        type=int,
        default=KEY_TOLERANCE,
        help="RGB distance to the key colour at or below which a pixel counts as background",
    )
    parser.add_argument(
        "--feather",
        type=int,
        default=FEATHER_WIDTH,
        help="RGB distance band above the key tolerance that is faded instead of cut; 0 disables",
    )
    parser.add_argument(
        "--margin",
        type=float,
        default=MARGIN_RATIO,
        help="margin around the content as a fraction of its shorter side",
    )
    parser.add_argument(
        "--no-previews",
        action="store_true",
        help="do not rebuild sprites_preview.png / monsters_preview.png",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="slice everything but write no files",
    )
    parser.add_argument(
        "--list",
        action="store_true",
        help="print what would be sliced and exit",
    )
    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = build_parser().parse_args(argv)

    try:
        found = discover(args.src, args.assets)
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    if args.list:
        for name in sorted(found):
            asset, path = found[name]
            target = asset.target
            print(f"{asset.filename:<32} {path} -> {target.width}x{target.height}")
        print(f"{len(found)} assets")
        return 0

    if not found:
        print(f"error: no generated PNGs found under {args.src}", file=sys.stderr)
        print("       run tools/art/gen_art.py first, or pass --src.", file=sys.stderr)
        return 2

    missing = [name for name in expected_names() if name not in found]
    if missing and not args.assets:
        print(
            f"note: {len(missing)} of {len(expected_names())} assets are missing "
            f"under {args.src}: " + ", ".join(missing),
            file=sys.stderr,
        )

    if not args.dry_run:
        args.dest.mkdir(parents=True, exist_ok=True)

    names = sorted(found)
    failures: List[str] = []
    converted = 0
    for index, name in enumerate(names, start=1):
        asset, path = found[name]
        target = asset.target
        try:
            width, height, rows = pngio.read_png(path)
            sliced = convert_rgba(
                rows,
                width,
                height,
                (target.width, target.height),
                tolerance=args.key_tolerance,
                feather=args.feather,
                margin_ratio=args.margin,
            )
        except (pngio.PngError, SliceError) as error:
            failures.append(name)
            print(f"[{index}/{len(names)}] {asset.filename}: FAILED ({error})",
                  file=sys.stderr)
            continue
        if not args.dry_run:
            pngio.write_png(args.dest / asset.filename, target.width, target.height, sliced)
        converted += 1
        suffix = "" if args.dry_run else f" -> {args.dest / asset.filename}"
        print(
            f"[{index}/{len(names)}] {asset.filename}: {width}x{height} -> "
            f"{target.width}x{target.height}{suffix}"
        )

    verb = "sliced" if args.dry_run else "converted"
    print(
        f"{verb} {converted}, failed {len(failures)}"
        + ("" if args.dry_run else f" into {args.dest}")
    )
    if failures:
        print("failed assets: " + ", ".join(failures), file=sys.stderr)
        return 1

    if not args.dry_run and not args.no_previews:
        written, skipped = write_previews(args.dest)
        for filename in written:
            print(f"preview {filename}")
        for note in skipped:
            print(f"note: not rebuilding {note}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
