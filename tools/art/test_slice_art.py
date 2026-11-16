#!/usr/bin/env python3
"""Regression tests for the art slicing step and its PNG codec.

Run directly or through unittest discovery::

    python3 tools/art/test_slice_art.py
    python3 -m unittest discover -s tools/art -t tools/art

No third-party dependency and no network access: every fixture is built with
``pngio`` itself, so the suite also proves the codec round-trips.
"""

from __future__ import annotations

import io
import struct
import sys
import tempfile
import unittest
import zlib
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import art_prompts  # noqa: E402
import gen_art  # noqa: E402
import pngio  # noqa: E402
import slice_art  # noqa: E402

CHANNELS = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}


# --- fixtures ----------------------------------------------------------------


def chunk(ctype: bytes, body: bytes) -> bytes:
    crc = zlib.crc32(ctype + body) & 0xFFFFFFFF
    return struct.pack(">I", len(body)) + ctype + body + struct.pack(">I", crc)


def _filter_line(filter_type, line, previous, bpp):
    """Apply a PNG forward filter, the inverse of ``pngio._unfilter``."""
    if filter_type == 0:
        return bytearray(line)
    out = bytearray(len(line))
    for i, value in enumerate(line):
        a = line[i - bpp] if i >= bpp else 0
        b = previous[i]
        c = previous[i - bpp] if i >= bpp else 0
        if filter_type == 1:
            predictor = a
        elif filter_type == 2:
            predictor = b
        elif filter_type == 3:
            predictor = (a + b) >> 1
        else:
            p = a + b - c
            pa = abs(p - a)
            pb = abs(p - b)
            pc = abs(p - c)
            if pa <= pb and pa <= pc:
                predictor = a
            elif pb <= pc:
                predictor = b
            else:
                predictor = c
        out[i] = (value - predictor) & 0xFF
    return out


def build_png(
    width,
    height,
    color_type,
    samples,
    *,
    filter_type=0,
    palette=None,
    transparency=None,
    bit_depth=8,
    interlace=0,
    corrupt_crc=False,
):
    """Build a PNG from flat row-major samples for one colour type."""
    channels = CHANNELS[color_type]
    stride = width * channels
    raw = bytearray()
    previous = bytearray(stride)
    for y in range(height):
        line = bytearray(samples[y * stride : (y + 1) * stride])
        raw.append(filter_type)
        raw.extend(_filter_line(filter_type, line, previous, channels))
        previous = line
    header = struct.pack(">IIBBBBB", width, height, bit_depth, color_type, 0, 0, interlace)
    parts = [pngio.PNG_SIGNATURE, chunk(b"IHDR", header)]
    if palette is not None:
        parts.append(chunk(b"PLTE", palette))
    if transparency is not None:
        parts.append(chunk(b"tRNS", bytes(transparency)))
    idat = chunk(b"IDAT", zlib.compress(bytes(raw)))
    if corrupt_crc:
        idat = idat[:-4] + b"\x00\x00\x00\x00"
    parts.append(idat)
    parts.append(chunk(b"IEND", b""))
    return b"".join(parts)


KEY = slice_art.KEY_COLOUR


def solid(width, height, colour, alpha=255):
    """RGBA rows of a single colour."""
    pixel = bytes(colour) + bytes([alpha])
    return [bytearray(pixel * width) for _ in range(height)]


def box_image(width, height, background, shape, colour):
    """White canvas with an opaque rectangle at ``shape = (l, t, r, b)``."""
    left, top, right, bottom = shape
    rows = solid(width, height, background)
    for y in range(top, bottom):
        for x in range(left, right):
            offset = x * 4
            rows[y][offset : offset + 4] = bytes(colour) + b"\xff"
    return rows


# --- PNG codec ---------------------------------------------------------------


class PngCodecTests(unittest.TestCase):
    def test_rgba_round_trip_is_exact(self):
        rows = [
            bytearray([10, 20, 30, 40, 50, 60, 70, 80]),
            bytearray([1, 2, 3, 4, 5, 6, 7, 8]),
        ]
        width, height, decoded = pngio.decode_png(pngio.encode_png(2, 2, rows))
        self.assertEqual((width, height), (2, 2))
        self.assertEqual([list(row) for row in decoded], [list(row) for row in rows])

    def test_rgb_is_decoded_with_full_alpha(self):
        samples = [10, 20, 30, 40, 50, 60]
        data = build_png(2, 1, 2, samples)
        width, height, rows = pngio.decode_png(data)
        self.assertEqual((width, height), (2, 1))
        self.assertEqual(list(rows[0]), [10, 20, 30, 255, 40, 50, 60, 255])

    def test_palette_is_expanded_and_trespected(self):
        palette = bytes([255, 0, 0, 0, 255, 0])
        data = build_png(2, 1, 3, [0, 1], palette=palette, transparency=bytes([128]))
        width, height, rows = pngio.decode_png(data)
        self.assertEqual(list(rows[0]), [255, 0, 0, 128, 0, 255, 0, 255])

    def test_gray_and_gray_alpha_are_expanded(self):
        gray = pngio.decode_png(build_png(2, 1, 0, [7, 9]))
        self.assertEqual(list(gray[2][0]), [7, 7, 7, 255, 9, 9, 9, 255])
        with_alpha = pngio.decode_png(build_png(2, 1, 4, [7, 128, 9, 3]))
        self.assertEqual(list(with_alpha[2][0]), [7, 7, 7, 128, 9, 9, 9, 3])

    def test_every_scanline_filter_decodes_back(self):
        width, height = 5, 4
        samples = bytearray()
        for y in range(height):
            for x in range(width):
                samples.extend(
                    [(x * 37 + y * 11) % 256, (x * 13 + y * 71) % 256, (x + y * 3) % 256]
                )
        expected = pngio.decode_png(build_png(width, height, 2, samples, filter_type=0))
        for filter_type in (1, 2, 3, 4):
            with self.subTest(filter=filter_type):
                decoded = pngio.decode_png(
                    build_png(width, height, 2, samples, filter_type=filter_type)
                )
                self.assertEqual(
                    [list(row) for row in decoded[2]], [list(row) for row in expected[2]]
                )

    def test_write_png_round_trips_through_read(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "sprite.png"
            rows = solid(3, 2, (200, 100, 50), alpha=64)
            pngio.write_png(path, 3, 2, rows)
            width, height, decoded = pngio.read_png(path)
            self.assertEqual((width, height), (3, 2))
            self.assertEqual([list(row) for row in decoded], [list(row) for row in rows])

    def test_non_png_is_rejected(self):
        with self.assertRaises(pngio.PngError):
            pngio.decode_png(b"not a png at all, honest")

    def test_truncated_chunk_is_rejected(self):
        data = build_png(1, 1, 2, [1, 2, 3])
        with self.assertRaises(pngio.PngError):
            pngio.decode_png(data[:20])

    def test_bad_crc_is_rejected(self):
        data = build_png(1, 1, 2, [1, 2, 3], corrupt_crc=True)
        with self.assertRaises(pngio.PngError):
            pngio.decode_png(data)

    def test_interlaced_png_is_rejected(self):
        data = build_png(1, 1, 2, [1, 2, 3], interlace=1)
        with self.assertRaisesRegex(pngio.PngError, "interlaced"):
            pngio.decode_png(data)

    def test_sixteen_bit_png_is_rejected(self):
        data = build_png(1, 1, 2, [1, 2, 3], bit_depth=16)
        with self.assertRaisesRegex(pngio.PngError, "bit depth"):
            pngio.decode_png(data)

    def test_encode_validates_the_row_length(self):
        with self.assertRaises(pngio.PngError):
            pngio.encode_png(2, 1, [bytearray(7)])

    def test_encode_validates_the_canvas_size(self):
        with self.assertRaises(pngio.PngError):
            pngio.encode_png(0, 4, [])

    def test_encode_validates_the_row_count(self):
        # A short row list used to produce a PNG whose IHDR promises more
        # scanlines than IDAT holds, which decoders reject later.
        with self.assertRaisesRegex(pngio.PngError, "expected 2 rows, got 1"):
            pngio.encode_png(2, 2, [bytearray(8)])


# --- background removal ------------------------------------------------------


class BackgroundTests(unittest.TestCase):
    WIDTH = HEIGHT = 64
    KEY = slice_art.KEY_COLOUR

    def test_border_background_is_transparent(self):
        rows = box_image(self.WIDTH, self.HEIGHT, KEY, (20, 20, 44, 44), (0, 0, 255))
        alpha = slice_art.remove_background(rows, self.WIDTH, self.HEIGHT)
        self.assertEqual(alpha[0], 0)
        self.assertEqual(alpha[self.WIDTH * self.HEIGHT - 1], 0)
        # A pixel well inside the shape is untouched.
        self.assertEqual(alpha[32 * self.WIDTH + 32], 255)

    def test_white_enclosed_by_the_shape_stays_opaque(self):
        rows = box_image(self.WIDTH, self.HEIGHT, KEY, (16, 16, 48, 48), (0, 0, 255))
        # A white highlight in the middle of the blue square: white is not
        # the key, so it survives as opaque white.
        for y in range(28, 36):
            for x in range(28, 36):
                offset = x * 4
                rows[y][offset : offset + 4] = b"\xff\xff\xff\xff"
        alpha = slice_art.remove_background(rows, self.WIDTH, self.HEIGHT)
        self.assertEqual(alpha[32 * self.WIDTH + 32], 255)

    def test_white_is_not_background_any_more(self):
        # Socks, eye highlights and white outfit pieces must survive the cut.
        rows = solid(self.WIDTH, self.HEIGHT, (255, 255, 255))
        alpha = slice_art.remove_background(rows, self.WIDTH, self.HEIGHT)
        self.assertEqual(set(alpha), {255})

    def test_fringe_between_tolerance_and_band_is_faded_not_cut(self):
        rows = solid(self.WIDTH, self.HEIGHT, KEY)
        # Colour roughly 98 away from the key: outside the tolerance (60) but
        # inside the feather band (60 + 60), so it fades.
        for y in range(30, 34):
            for x in range(30, 34):
                offset = x * 4
                rows[y][offset : offset + 4] = bytes([200, 60, 200, 255])
        alpha = slice_art.remove_background(rows, self.WIDTH, self.HEIGHT)
        feathered = alpha[30 * self.WIDTH + 30]
        self.assertGreater(feathered, 0)
        self.assertLess(feathered, 255)

    def test_key_coloured_pixels_inside_tolerance_are_cut(self):
        rows = solid(self.WIDTH, self.HEIGHT, KEY)
        alpha = slice_art.remove_background(rows, self.WIDTH, self.HEIGHT)
        self.assertEqual(set(alpha), {0})

    def test_tolerance_controls_how_close_a_colour_must_be(self):
        rows = solid(self.WIDTH, self.HEIGHT, (250, 10, 250))
        loose = slice_art.remove_background(rows, self.WIDTH, self.HEIGHT, tolerance=60)
        strict = slice_art.remove_background(rows, self.WIDTH, self.HEIGHT, tolerance=2)
        self.assertEqual(set(loose), {0})
        self.assertEqual(set(strict), {255})

    def test_original_alpha_is_preserved_for_survivors(self):
        rows = solid(self.WIDTH, self.HEIGHT, KEY)
        offset = 32 * 4
        rows[32][offset : offset + 4] = bytes([10, 20, 30, 64])
        alpha = slice_art.remove_background(rows, self.WIDTH, self.HEIGHT)
        self.assertEqual(alpha[32 * self.WIDTH + 32], 64)
        self.assertEqual(alpha[0], 0)

    def test_feather_can_be_disabled(self):
        rows = solid(self.WIDTH, self.HEIGHT, KEY)
        for y in range(30, 34):
            for x in range(30, 34):
                offset = x * 4
                rows[y][offset : offset + 4] = bytes([200, 60, 200, 255])
        alpha = slice_art.remove_background(rows, self.WIDTH, self.HEIGHT, feather=0)
        self.assertEqual(alpha[31 * self.WIDTH + 31], 255)

    def test_enclosed_key_pixels_are_cut_too(self):
        # A key-coloured gap inside an outline (between a hand and the body)
        # is background as well: no character uses the key colour.
        rows = box_image(self.WIDTH, self.HEIGHT, KEY, (10, 10, 54, 54), (0, 0, 255))
        for y in range(24, 40):
            for x in range(24, 40):
                offset = x * 4
                rows[y][offset : offset + 4] = bytes(KEY) + b"\xff"
        alpha = slice_art.remove_background(rows, self.WIDTH, self.HEIGHT)
        self.assertEqual(alpha[32 * self.WIDTH + 32], 0)
        self.assertEqual(alpha[32 * self.WIDTH + 20], 255)

    def test_pastel_pink_is_not_treated_as_key(self):
        # Pastel pink (hair, ribbons, blush) is far from the magenta key.
        rows = solid(self.WIDTH, self.HEIGHT, (255, 182, 193))
        alpha = slice_art.remove_background(rows, self.WIDTH, self.HEIGHT)
        self.assertEqual(set(alpha), {255})


class ContentBoxTests(unittest.TestCase):
    def test_box_matches_the_visible_pixels(self):
        alpha = bytearray(10 * 8)
        for y in range(2, 6):
            for x in range(3, 7):
                alpha[y * 10 + x] = 255
        self.assertEqual(slice_art.content_box(alpha, 10, 8), (3, 2, 7, 6))

    def test_box_ignores_faint_noise(self):
        alpha = bytearray(10 * 8)
        alpha[4 * 10 + 5] = 4  # below the cutoff
        self.assertIsNone(slice_art.content_box(alpha, 10, 8))

    def test_no_content_is_reported_as_none(self):
        self.assertIsNone(slice_art.content_box(bytearray(20), 5, 4))


# --- scaling -----------------------------------------------------------------


class ResizeTests(unittest.TestCase):
    def test_solid_colour_survives_downscaling(self):
        rows = solid(8, 8, (10, 200, 30))
        out = slice_art.resize_rgba(rows, 8, 8, 2, 2)
        self.assertEqual(len(out), 2)
        for row in out:
            self.assertEqual(len(row), 8)
            self.assertEqual(list(row), [10, 200, 30, 255] * 2)

    def test_alpha_is_averaged_not_multiplied(self):
        rows = [bytearray([255, 0, 0, 255, 0, 0, 0, 0])]
        out = slice_art.resize_rgba(rows, 2, 1, 1, 1)
        red, green, blue, alpha = out[0]
        self.assertEqual((red, green, blue), (255, 0, 0))
        self.assertIn(alpha, (127, 128))

    def test_fully_transparent_input_stays_transparent(self):
        out = slice_art.resize_rgba(solid(6, 6, (9, 9, 9), alpha=0), 6, 6, 3, 3)
        for row in out:
            for index in range(3, len(row), 4):
                self.assertEqual(row[index], 0)

    def test_fit_into_centres_without_distortion(self):
        # A tall thin shape must fill the height of a 96x128 box, not the width.
        rows = solid(40, 80, (0, 0, 0), alpha=0)
        alpha = bytearray(40 * 80)
        for y in range(80):
            for x in range(16, 24):
                alpha[y * 40 + x] = 255
                offset = x * 4
                rows[y][offset : offset + 4] = b"\xff\x00\x00\xff"
        slice_art.apply_alpha(rows, 40, alpha)
        cropped = [row[16 * 4 : 24 * 4] for row in rows]
        out = slice_art.fit_into(cropped, 8, 80, (96, 128))
        self.assertEqual(len(out), 128)
        self.assertEqual(len(out[0]), 96 * 4)
        self.assertEqual(out[0][3], 0)  # top-left corner is empty
        self.assertEqual(out[127][96 * 4 - 1], 0)  # bottom-right corner too
        occupied = [
            x
            for y in range(128)
            for x in range(96)
            if out[y][x * 4 + 3] > 128
        ]
        self.assertTrue(occupied)
        self.assertLess(max(occupied) - min(occupied) + 1, 96)


# --- end to end --------------------------------------------------------------


class ConvertTests(unittest.TestCase):
    def test_convert_produces_the_target_box_with_transparent_corners(self):
        rows = box_image(100, 100, KEY, (30, 10, 70, 90), (0, 0, 255))
        out = slice_art.convert_rgba(rows, 100, 100, (96, 128))
        self.assertEqual(len(out), 128)
        self.assertEqual(len(out[0]), 96 * 4)
        self.assertEqual(out[0][3], 0)
        self.assertEqual(out[127][96 * 4 - 1], 0)
        centre = out[64][48 * 4 : 48 * 4 + 4]
        self.assertEqual(centre[3], 255)
        self.assertGreater(centre[2], 200)

    def test_convert_reports_an_empty_image(self):
        rows = solid(20, 20, KEY)
        with self.assertRaises(slice_art.SliceError):
            slice_art.convert_rgba(rows, 20, 20, (96, 128))

    def test_convert_keeps_monsters_square(self):
        rows = box_image(100, 100, KEY, (20, 20, 80, 80), (0, 200, 0))
        out = slice_art.convert_rgba(rows, 100, 100, (96, 96))
        self.assertEqual((len(out), len(out[0]) // 4), (96, 96))


# --- discovery and CLI -------------------------------------------------------


class PipelineWiringTests(unittest.TestCase):
    def test_generator_output_is_the_slicer_input(self):
        # A plain `gen_art.py` run must leave its raw files where
        # `slice_art.py` looks for them, and never in the shipped asset
        # folder, which holds the sliced 96x128 / 96x96 sprites.
        self.assertEqual(gen_art.DEFAULT_OUT_DIR, slice_art.DEFAULT_SRC)
        self.assertNotEqual(gen_art.DEFAULT_OUT_DIR, slice_art.DEFAULT_DEST)

    def test_slicer_source_is_the_git_ignored_raw_folder(self):
        gitignore = (HERE.parents[1] / ".gitignore").read_text(encoding="utf-8")
        self.assertIn("tools/art/out", gitignore)


class PreviewTests(unittest.TestCase):
    def test_compose_sheet_sizes_and_places_sprites(self):
        sprite = solid(4, 6, (255, 0, 0))
        rows, width, height = slice_art.compose_sheet(
            [(sprite, 4, 6)] * 3, 2, (4, 6), pad=1
        )
        self.assertEqual((width, height), (22, 30))
        self.assertEqual(list(rows[0][:4]), [24, 18, 43, 255])  # pad corner
        self.assertEqual(list(rows[2][8:12]), [255, 0, 0, 255])  # first sprite
        # The fourth cell was never filled, so it stays background.
        self.assertEqual(list(rows[16][48:52]), [24, 18, 43, 255])

    def test_write_previews_rebuilds_both_sheets_at_the_right_size(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp)
            # One pixel per asset is enough: the sheet uses the cell size.
            for name in slice_art.expected_names():
                pngio.write_png(dest / f"{name}.png", 1, 1, solid(1, 1, (10, 20, 30)))
            written, skipped = slice_art.write_previews(dest)
            self.assertEqual(written, ["sprites_preview.png", "monsters_preview.png"])
            self.assertEqual(skipped, [])
            self.assertEqual(
                pngio.read_png(dest / "sprites_preview.png")[:2], (1472, 1920)
            )
            self.assertEqual(
                pngio.read_png(dest / "monsters_preview.png")[:2], (1264, 224)
            )

    def test_write_previews_skips_a_sheet_with_missing_sprites(self):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp)
            pngio.write_png(dest / "monster_slime.png", 1, 1, solid(1, 1, (1, 2, 3)))
            written, skipped = slice_art.write_previews(dest)
            self.assertEqual(written, [])
            self.assertEqual(len(skipped), 2)
            self.assertFalse((dest / "sprites_preview.png").exists())
            self.assertFalse((dest / "monsters_preview.png").exists())


class DiscoveryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.src = Path(self.tmp.name)

    def _write(self, name):
        pngio.write_png(self.src / f"{name}.png", 1, 1, solid(1, 1, (1, 2, 3)))

    def test_assets_are_mapped_to_their_target_sizes(self):
        self._write("sprite_aoi_idle")
        self._write("monster_slime")
        (self.src / "notes.txt").write_text("ignored")
        found = slice_art.discover(self.src)
        self.assertEqual(sorted(found), ["monster_slime", "sprite_aoi_idle"])
        self.assertEqual(found["sprite_aoi_idle"][0].target.width, 96)
        self.assertEqual(found["sprite_aoi_idle"][0].target.height, 128)
        self.assertEqual(found["monster_slime"][0].target.width, 96)
        self.assertEqual(found["monster_slime"][0].target.height, 96)

    def test_subfolders_are_searched(self):
        nested = self.src / "aoi"
        nested.mkdir()
        pngio.write_png(nested / "sprite_aoi_happy.png", 1, 1, solid(1, 1, (1, 2, 3)))
        self.assertEqual(list(slice_art.discover(self.src)), ["sprite_aoi_happy"])

    def test_unknown_names_are_rejected(self):
        with self.assertRaises(ValueError):
            slice_art.discover(self.src, ["sprite_nobody_idle"])

    def test_expected_set_is_seven_characters_plus_monsters(self):
        names = slice_art.expected_names()
        self.assertEqual(len(names), 7 * 7 + 6)
        self.assertIn("sprite_aoi_idle", names)
        self.assertIn("monster_golem", names)


class CliTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.src = self.root / "src"
        self.dest = self.root / "dest"
        self.src.mkdir()
        pngio.write_png(
            self.src / "sprite_aoi_idle.png",
            40,
            40,
            box_image(40, 40, KEY, (10, 5, 30, 35), (0, 0, 255)),
        )
        pngio.write_png(
            self.src / "monster_slime.png",
            40,
            40,
            box_image(40, 40, KEY, (8, 8, 32, 32), (0, 200, 0)),
        )

    def _run(self, *args):
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = slice_art.main([*args, "--src", str(self.src), "--dest", str(self.dest)])
        return code, out.getvalue(), err.getvalue()

    def test_list_needs_no_dest_and_writes_nothing(self):
        code, out, _ = self._run("--list")
        self.assertEqual(code, 0)
        self.assertIn("sprite_aoi_idle.png", out)
        self.assertIn("monster_slime.png", out)
        self.assertIn("2 assets", out)
        self.assertFalse(self.dest.exists())

    def test_run_writes_the_target_sizes(self):
        code, out, _ = self._run()
        self.assertEqual(code, 0)
        self.assertIn("converted 2", out)
        width, height, _ = pngio.read_png(self.dest / "sprite_aoi_idle.png")
        self.assertEqual((width, height), (96, 128))
        width, height, _ = pngio.read_png(self.dest / "monster_slime.png")
        self.assertEqual((width, height), (96, 96))

    def test_written_sprite_has_a_transparent_background(self):
        self.assertEqual(self._run()[0], 0)
        _, _, rows = pngio.read_png(self.dest / "sprite_aoi_idle.png")
        self.assertEqual(rows[0][3], 0)
        self.assertEqual(rows[127][96 * 4 - 1], 0)

    def test_dry_run_writes_nothing(self):
        code, out, _ = self._run("--dry-run")
        self.assertEqual(code, 0)
        self.assertIn("sliced 2", out)
        self.assertFalse(self.dest.exists())

    def test_partial_run_reports_the_missing_contact_sheets(self):
        _, _, err = self._run()
        self.assertIn("not rebuilding sprites_preview.png", err)
        _, _, err_off = self._run("--no-previews")
        self.assertNotIn("not rebuilding", err_off)

    def test_assets_filters_the_run(self):
        code, out, _ = self._run("--assets", "monster_slime")
        self.assertEqual(code, 0)
        self.assertTrue((self.dest / "monster_slime.png").exists())
        self.assertFalse((self.dest / "sprite_aoi_idle.png").exists())

    def test_empty_source_fails_loudly(self):
        empty = self.root / "empty"
        empty.mkdir()
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = slice_art.main(["--src", str(empty), "--dest", str(self.dest)])
        self.assertEqual(code, 2)
        self.assertIn("no generated PNGs", err.getvalue())

    def test_unknown_asset_fails_loudly(self):
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = slice_art.main(
                ["--src", str(self.src), "--dest", str(self.dest), "--assets", "sprite_nope"]
            )
        self.assertEqual(code, 2)
        self.assertIn("unknown asset", err.getvalue())


if __name__ == "__main__":
    unittest.main()
