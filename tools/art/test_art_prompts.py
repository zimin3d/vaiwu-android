#!/usr/bin/env python3
"""Regression tests for the art prompt tooling.

Run directly or through unittest discovery::

    python3 tools/art/test_art_prompts.py
    python3 -m unittest discover -s tools/art -t tools/art

No third-party dependency and no network access: the API layer is covered by
stubbing ``urllib.request.urlopen``.
"""

from __future__ import annotations

import base64
import io
import json
import re
import sys
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest import mock

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))

import art_prompts  # noqa: E402
import gen_art  # noqa: E402

GEN_SPRITES = REPO_ROOT / "external" / "vaiwu" / "firmware" / "tools" / "gen_sprites.py"

#: ``generate_image`` now insists on a real PNG, so fixtures carry the prefix.
PNG = gen_art.PNG_SIGNATURE


def firmware_list(name: str):
    """Read a ``NAME = ["a", "b"]`` list straight out of the firmware tool."""
    if not GEN_SPRITES.exists():
        raise unittest.SkipTest(f"{GEN_SPRITES} is not checked out")
    text = GEN_SPRITES.read_text(encoding="utf-8")
    match = re.search(rf"^{name}\s*=\s*\[([^\]]*)\]", text, re.MULTILINE)
    if not match:
        raise AssertionError(f"{name} not found in gen_sprites.py")
    return re.findall(r'"([^"]+)"', match.group(1))


class EmotionTests(unittest.TestCase):
    def test_every_emotion_has_a_modifier(self):
        self.assertEqual(set(art_prompts.EMOTIONS), set(art_prompts.EMOTION_MODIFIERS))
        self.assertEqual(len(art_prompts.EMOTIONS), 7)

    def test_emotion_order_matches_firmware(self):
        self.assertEqual(list(art_prompts.EMOTIONS), firmware_list("EMOTIONS"))

    def test_character_order_matches_firmware(self):
        self.assertEqual(list(art_prompts.CHARACTERS), firmware_list("CHARS"))

    def test_monster_order_matches_firmware(self):
        self.assertEqual(list(art_prompts.MONSTERS), firmware_list("MONSTERS"))


class PromptTests(unittest.TestCase):
    def test_prompts_are_single_line(self):
        for asset in art_prompts.build_assets():
            self.assertNotIn("\n", asset.prompt, asset.name)
            self.assertNotIn("  ", asset.prompt, asset.name)
            self.assertEqual(asset.prompt, asset.prompt.strip(), asset.name)

    def test_style_anchor_is_appended_to_every_prompt(self):
        for asset in art_prompts.build_assets():
            self.assertTrue(asset.prompt.endswith(art_prompts.STYLE_ANCHOR), asset.name)

    def test_emotion_modifier_is_present_in_its_own_assets_only(self):
        for asset in art_prompts.build_assets():
            if not asset.emotion:
                continue
            self.assertIn(art_prompts.EMOTION_MODIFIERS[asset.emotion], asset.prompt)
            for emotion, modifier in art_prompts.EMOTION_MODIFIERS.items():
                if emotion == asset.emotion:
                    continue
                self.assertNotIn(modifier, asset.prompt, f"{asset.name} leaked {emotion}")

    def test_no_placeholder_is_left_in_any_prompt(self):
        for asset in art_prompts.build_assets():
            self.assertNotIn("<EMOTION>", asset.prompt, asset.name)

    def test_character_prompts_share_one_style_anchor_byte_for_byte(self):
        prompts = [
            art_prompts.character_prompt(character, emotion)
            for character in art_prompts.CHARACTERS
            for emotion in art_prompts.EMOTIONS
        ]
        anchors = {prompt[-len(art_prompts.STYLE_ANCHOR):] for prompt in prompts}
        self.assertEqual(anchors, {art_prompts.STYLE_ANCHOR})

    def test_unknown_names_are_rejected(self):
        with self.assertRaises(ValueError):
            art_prompts.character_prompt("nobody", "idle")
        with self.assertRaises(ValueError):
            art_prompts.character_prompt("aoi", "ecstatic")
        with self.assertRaises(ValueError):
            art_prompts.monster_prompt("dragon")
        with self.assertRaises(ValueError):
            art_prompts.build_assets(characters=["nobody"])

    def test_negative_prompt_is_non_empty(self):
        self.assertTrue(art_prompts.NEGATIVE_PROMPT)
        self.assertNotIn("\n", art_prompts.NEGATIVE_PROMPT)


class AssetTests(unittest.TestCase):
    def test_default_set_is_seven_characters_by_seven_emotions_plus_monsters(self):
        assets = art_prompts.build_assets()
        characters = [asset for asset in assets if asset.kind == "character"]
        monsters = [asset for asset in assets if asset.kind == "monster"]
        self.assertEqual(len(characters), 7 * 7)
        self.assertEqual(len(monsters), 6)
        self.assertEqual(len(assets), 55)

    def test_filenames_follow_the_firmware_preview_convention(self):
        assets = art_prompts.build_assets()
        names = {asset.filename for asset in assets}
        self.assertIn("sprite_aoi_idle.png", names)
        self.assertIn("sprite_kolobok_dead.png", names)
        self.assertIn("monster_slime.png", names)
        self.assertEqual(len(names), len(assets))

    def test_targets_match_the_documented_sizes(self):
        self.assertEqual(
            (art_prompts.TARGETS["character"].width, art_prompts.TARGETS["character"].height),
            (96, 128),
        )
        self.assertEqual(art_prompts.TARGETS["character"].aspect, "3:4")
        self.assertEqual(
            (art_prompts.TARGETS["monster"].width, art_prompts.TARGETS["monster"].height),
            (96, 96),
        )
        self.assertEqual(art_prompts.TARGETS["monster"].aspect, "1:1")
        self.assertEqual(
            (art_prompts.TARGETS["background"].width, art_prompts.TARGETS["background"].height),
            (320, 140),
        )

    def test_api_sizes_match_the_documented_boxes(self):
        self.assertEqual(art_prompts.TARGETS["character"].api_size, "1024x1536")
        self.assertEqual(art_prompts.TARGETS["monster"].api_size, "1024x1024")
        self.assertEqual(art_prompts.TARGETS["background"].api_size, "1536x1024")

    def test_monster_api_size_is_exactly_square(self):
        width, height = (int(part) for part in art_prompts.TARGETS["monster"].api_size.split("x"))
        self.assertEqual(width, height)

    def test_character_api_size_uses_the_allowed_2_3_ratio(self):
        # The doc allows "3:4 (or 2:3)" for characters; no API offers 3:4.
        width, height = (int(part) for part in art_prompts.TARGETS["character"].api_size.split("x"))
        self.assertAlmostEqual(width / height, 2 / 3, places=6)

    def test_filters_select_a_subset(self):
        assets = art_prompts.build_assets(
            characters=["aoi"], emotions=["idle", "happy"], monsters=["slime"]
        )
        self.assertEqual(
            [asset.name for asset in assets],
            ["sprite_aoi_idle", "sprite_aoi_happy", "monster_slime"],
        )

    def test_empty_character_filter_still_means_all(self):
        # An explicit empty list is treated as "not given", matching the CLI.
        self.assertEqual(len(art_prompts.build_assets(characters=[])), 55)


class PromptFileTests(unittest.TestCase):
    def test_prompt_files_are_written_for_every_asset(self):
        assets = art_prompts.build_assets()
        with tempfile.TemporaryDirectory() as directory:
            out_dir = Path(directory)
            gen_art.write_prompt_files(assets, out_dir)

            payload = json.loads((out_dir / "prompts.json").read_text(encoding="utf-8"))
            self.assertEqual(len(payload["assets"]), 55)
            self.assertEqual(payload["style_anchor"], art_prompts.STYLE_ANCHOR)
            self.assertEqual(payload["negative_prompt"], art_prompts.NEGATIVE_PROMPT)

            markdown = (out_dir / "prompts.md").read_text(encoding="utf-8")
            for asset in assets:
                self.assertIn(asset.prompt, markdown)

            aoi = (out_dir / "characters" / "aoi.txt").read_text(encoding="utf-8")
            for emotion in art_prompts.EMOTIONS:
                self.assertIn(art_prompts.EMOTION_MODIFIERS[emotion], aoi)

            slime = (out_dir / "monsters" / "slime.txt").read_text(encoding="utf-8")
            self.assertIn(art_prompts.MONSTERS["slime"], slime)

    def test_subset_writes_only_the_selected_files(self):
        assets = art_prompts.build_assets(
            characters=["rin"], emotions=["sad"], monsters=["slime"]
        )
        with tempfile.TemporaryDirectory() as directory:
            out_dir = Path(directory)
            gen_art.write_prompt_files(assets, out_dir)
            self.assertTrue((out_dir / "characters" / "rin.txt").exists())
            self.assertFalse((out_dir / "characters" / "aoi.txt").exists())
            self.assertTrue((out_dir / "monsters" / "slime.txt").exists())
            self.assertFalse((out_dir / "monsters" / "golem.txt").exists())


class _HttpStub(unittest.TestCase):
    """Shared helpers for stubbing the images API."""

    def http_error(self, status: int, message: str = "boom") -> urllib.error.HTTPError:
        # The real class doubles as a response object; a BytesIO body keeps it
        # close to what urlopen produces on an HTTP error.
        return urllib.error.HTTPError(
            "https://example.test/v1/images/generations",
            status,
            "Error",
            {},
            io.BytesIO(json.dumps({"error": {"message": message}}).encode()),
        )

    def always(self, status: int, message: str = "boom"):
        """A side_effect that fails every attempt with a new error."""

        def factory(*args, **kwargs):
            raise self.http_error(status, message)

        return factory


class FormatTests(unittest.TestCase):
    """The bytes handed to the caller must really be a PNG."""

    WEBP = b"RIFF\x10\x00\x00\x00WEBPVP8L" + b"\x0f" + b"payload"

    def test_sniff_format_names_the_common_formats(self):
        cases = [
            (PNG + b"pixels", "PNG"),
            (self.WEBP, "WebP"),
            (b"\xff\xd8\xff\xe0rest", "JPEG"),
            (b"GIF89arest", "GIF"),
            (b"BMrest", "BMP"),
            (b"<!DOCTYPE html><html></html>", "HTML"),
            (b"\x00\x01\x02\x03", "unknown"),
        ]
        for payload, expected in cases:
            with self.subTest(expected=expected):
                self.assertEqual(gen_art.sniff_format(payload), expected)

    def test_require_png_passes_a_png_through(self):
        image = PNG + b"pixels"
        self.assertEqual(gen_art.require_png(image), image)

    def test_require_png_rejects_everything_else_without_retrying(self):
        for payload in (self.WEBP, b"\xff\xd8\xff", b"GIF89a", b"not an image", b""):
            with self.subTest(payload=payload[:8]):
                with self.assertRaises(gen_art.ArtApiError) as caught:
                    gen_art.require_png(payload)
                self.assertFalse(caught.exception.transient)


class ApiTests(_HttpStub):
    def setUp(self):
        self.asset = art_prompts.build_assets(characters=["aoi"], emotions=["idle"])[0]
        self.config = gen_art.ApiConfig(api_key="test-key", base_url="https://example.test")

    def test_endpoint_is_openai_compatible(self):
        self.assertEqual(
            self.config.endpoint, "https://example.test/v1/images/generations"
        )

    def test_base_url_may_include_the_v1_suffix(self):
        # Providers document their base URL either way; both must work.
        for base_url in (
            "https://example.test",
            "https://example.test/",
            "https://example.test/v1",
            "https://example.test/v1/",
        ):
            with self.subTest(base_url=base_url):
                config = gen_art.ApiConfig(base_url=base_url)
                self.assertEqual(
                    config.endpoint, "https://example.test/v1/images/generations"
                )

    def test_base_url_may_keep_a_path_prefix(self):
        config = gen_art.ApiConfig(base_url="https://example.test/openai/v1")
        self.assertEqual(
            config.endpoint, "https://example.test/openai/v1/images/generations"
        )

    def test_size_override_wins_over_the_target(self):
        self.assertEqual(self.config.size_for(self.asset), "1024x1536")
        overridden = gen_art.ApiConfig(size_override="512x512")
        self.assertEqual(overridden.size_for(self.asset), "512x512")

    def test_base64_response_is_decoded(self):
        payload = PNG + b"hello"
        body = json.dumps(
            {"data": [{"b64_json": base64.b64encode(payload).decode()}]}
        ).encode()
        with mock.patch("urllib.request.urlopen", return_value=_Response(body)) as urlopen:
            image = gen_art.generate_image(self.config, self.asset.prompt, "1024x1536")
        self.assertEqual(image, payload)
        request = urlopen.call_args.args[0]
        payload = json.loads(request.data.decode())
        self.assertEqual(payload["model"], gen_art.DEFAULT_MODEL)
        self.assertEqual(payload["size"], "1024x1536")
        self.assertEqual(payload["n"], 1)
        self.assertEqual(request.get_header("Authorization"), "Bearer test-key")

    def test_url_response_is_downloaded(self):
        payload = PNG + b"image-bytes"
        body = json.dumps({"data": [{"url": "https://cdn.test/a.png"}]}).encode()
        with mock.patch(
            "urllib.request.urlopen", side_effect=[_Response(body), _Response(payload)]
        ):
            image = gen_art.generate_image(self.config, self.asset.prompt, "1024x1536")
        self.assertEqual(image, payload)

    def test_response_format_is_retried_without_on_rejection(self):
        payload = PNG + b"hi"
        rejection = self.http_error(400, "unknown parameter: response_format")
        success = _Response(
            json.dumps({"data": [{"b64_json": base64.b64encode(payload).decode()}]}).encode()
        )
        with mock.patch("urllib.request.urlopen", side_effect=[rejection, success]) as urlopen:
            image = gen_art.generate_image(self.config, self.asset.prompt, "1024x1536")
        self.assertEqual(image, payload)
        second_payload = json.loads(urlopen.call_args.args[0].data.decode())
        self.assertNotIn("response_format", second_payload)

    def test_unrelated_http_error_is_not_retried(self):
        error = self.http_error(401, "invalid api key")
        with mock.patch("urllib.request.urlopen", side_effect=error) as urlopen:
            with self.assertRaises(gen_art.ArtApiError) as caught:
                gen_art.generate_image(self.config, self.asset.prompt, "1024x1536")
        self.assertIn("invalid api key", str(caught.exception))
        self.assertEqual(urlopen.call_count, 1)

    def test_empty_response_is_an_error(self):
        with mock.patch("urllib.request.urlopen", return_value=_Response(b"{}")):
            with self.assertRaises(gen_art.ArtApiError):
                gen_art.generate_image(self.config, self.asset.prompt, "1024x1536")

    def test_html_response_explains_the_base_url(self):
        # Some relays answer every path on the apex domain with their web UI.
        html = _Response(
            b"<!DOCTYPE html>\n<html><head><title>app</title></head></html>",
            content_type="text/html; charset=utf-8",
        )
        with mock.patch("urllib.request.urlopen", return_value=html):
            with self.assertRaises(gen_art.ArtApiError) as caught:
                gen_art.generate_image(self.config, self.asset.prompt, "1024x1536")
        message = str(caught.exception)
        self.assertIn("HTML", message)
        self.assertIn("check the base URL", message)

    def test_json_response_without_a_content_type_still_parses(self):
        payload = PNG + b"hi"
        body = json.dumps(
            {"data": [{"b64_json": base64.b64encode(payload).decode()}]}
        ).encode()
        with mock.patch(
            "urllib.request.urlopen", return_value=_Response(body, content_type="")
        ):
            image = gen_art.generate_image(self.config, self.asset.prompt, "1024x1536")
        self.assertEqual(image, payload)

    def test_server_error_is_retried_then_succeeds(self):
        payload = PNG + b"hi"
        success = _Response(
            json.dumps({"data": [{"b64_json": base64.b64encode(payload).decode()}]}).encode()
        )
        with mock.patch(
            "urllib.request.urlopen", side_effect=[self.http_error(503), success]
        ) as urlopen, mock.patch("gen_art.time.sleep") as sleep:
            image = gen_art.generate_image(self.config, self.asset.prompt, "1024x1536")
        self.assertEqual(image, payload)
        self.assertEqual(urlopen.call_count, 2)
        sleep.assert_called_once()

    def test_webp_payload_is_rejected_and_not_retried(self):
        # Regression: a relay answered one request with a WebP and the bytes
        # were written to a .png name, breaking the slicer later on.
        body = json.dumps(
            {"data": [{"b64_json": base64.b64encode(FormatTests.WEBP).decode()}]}
        ).encode()
        with mock.patch("urllib.request.urlopen", return_value=_Response(body)) as urlopen, \
                mock.patch("gen_art.time.sleep") as sleep:
            with self.assertRaises(gen_art.ArtApiError) as caught:
                gen_art.generate_image(self.config, self.asset.prompt, "1024x1536")
        self.assertIn("WebP", str(caught.exception))
        self.assertFalse(caught.exception.transient)
        self.assertEqual(urlopen.call_count, 1)
        sleep.assert_not_called()

    def test_retries_are_capped(self):
        with mock.patch(
            "urllib.request.urlopen", side_effect=self.always(503)
        ) as urlopen, mock.patch("gen_art.time.sleep"):
            with self.assertRaises(gen_art.ArtApiError) as caught:
                gen_art.generate_image(self.config, self.asset.prompt, "1024x1536")
        self.assertEqual(urlopen.call_count, self.config.retries + 1)
        self.assertTrue(caught.exception.transient)

    def test_rate_limit_is_retried(self):
        with mock.patch(
            "urllib.request.urlopen", side_effect=self.always(429, "slow down")
        ) as urlopen, mock.patch("gen_art.time.sleep"):
            with self.assertRaises(gen_art.ArtApiError):
                gen_art.generate_image(self.config, self.asset.prompt, "1024x1536")
        self.assertEqual(urlopen.call_count, self.config.retries + 1)

    def test_rejected_key_is_not_retried(self):
        with mock.patch(
            "urllib.request.urlopen", side_effect=self.always(401, "invalid api key")
        ) as urlopen, mock.patch("gen_art.time.sleep") as sleep:
            with self.assertRaises(gen_art.ArtApiError) as caught:
                gen_art.generate_image(self.config, self.asset.prompt, "1024x1536")
        self.assertEqual(urlopen.call_count, 1)
        sleep.assert_not_called()
        self.assertFalse(caught.exception.transient)

    def test_wrong_base_url_is_not_retried(self):
        html = _Response(b"<!DOCTYPE html><html></html>", content_type="text/html")
        with mock.patch("urllib.request.urlopen", return_value=html) as urlopen, mock.patch(
            "gen_art.time.sleep"
        ):
            with self.assertRaises(gen_art.ArtApiError):
                gen_art.generate_image(self.config, self.asset.prompt, "1024x1536")
        self.assertEqual(urlopen.call_count, 1)

    def test_no_retries_means_a_single_attempt(self):
        config = gen_art.ApiConfig(api_key="test-key", retries=0)
        with mock.patch(
            "urllib.request.urlopen", side_effect=self.always(500)
        ) as urlopen, mock.patch("gen_art.time.sleep") as sleep:
            with self.assertRaises(gen_art.ArtApiError):
                gen_art.generate_image(config, self.asset.prompt, "1024x1536")
        self.assertEqual(urlopen.call_count, 1)
        sleep.assert_not_called()


class CliTests(_HttpStub):
    """The CLI is exercised end to end with the network stubbed out."""

    @staticmethod
    def run_cli(argv):
        stdout, stderr = io.StringIO(), io.StringIO()
        with mock.patch("sys.stdout", stdout), mock.patch("sys.stderr", stderr):
            code = gen_art.main(argv)
        return code, stdout.getvalue(), stderr.getvalue()

    @staticmethod
    def image_response(payload: bytes):
        body = json.dumps(
            {"data": [{"b64_json": base64.b64encode(payload).decode()}]}
        ).encode()
        return _Response(body)

    def test_list_needs_no_api_key(self):
        code, stdout, _ = self.run_cli(
            ["--list", "--characters", "aoi", "--emotions", "idle", "--monsters", "slime"]
        )
        self.assertEqual(code, 0)
        self.assertIn("sprite_aoi_idle.png", stdout)
        self.assertIn("2 assets", stdout)

    def test_assets_selects_exactly_one_asset(self):
        with tempfile.TemporaryDirectory() as directory:
            out_dir = Path(directory) / "assets"
            with mock.patch(
                "urllib.request.urlopen", return_value=self.image_response(PNG + b"new")
            ) as urlopen:
                code, _, _ = self.run_cli(
                    [
                        "--api-key",
                        "test-key",
                        "--assets",
                        "sprite_aoi_idle",
                        "--out-dir",
                        str(out_dir),
                        "--prompts-out",
                        str(Path(directory) / "prompts"),
                    ]
                )
            self.assertEqual(code, 0)
            self.assertEqual(urlopen.call_count, 1)
            self.assertEqual(
                sorted(p.name for p in out_dir.iterdir()), ["sprite_aoi_idle.png"]
            )

    def test_assets_rejects_unknown_names(self):
        _, _, stderr = self.run_cli(["--assets", "sprite_nobody_idle", "--no-generate"])
        self.assertIn("unknown asset(s): sprite_nobody_idle", stderr)

    def test_assets_cannot_be_combined_with_the_other_selectors(self):
        _, _, stderr = self.run_cli(
            ["--assets", "sprite_aoi_idle", "--characters", "aoi", "--no-generate"]
        )
        self.assertIn("cannot be combined", stderr)
        self.assertIn("--characters", stderr)

    def test_no_generate_writes_prompts_and_skips_the_network(self):
        with tempfile.TemporaryDirectory() as directory:
            out_dir = Path(directory) / "prompts"
            with mock.patch("urllib.request.urlopen") as urlopen:
                code, _, _ = self.run_cli(
                    ["--no-generate", "--prompts-out", str(out_dir), "--characters", "aoi"]
                )
            self.assertEqual(code, 0)
            urlopen.assert_not_called()
            self.assertTrue((out_dir / "prompts.json").exists())

    def test_missing_api_key_fails_loudly(self):
        with tempfile.TemporaryDirectory() as directory:
            with mock.patch.dict("os.environ", {}, clear=True), mock.patch(
                "urllib.request.urlopen"
            ) as urlopen:
                code, _, stderr = self.run_cli(
                    [
                        "--prompts-out",
                        str(Path(directory) / "prompts"),
                        "--out-dir",
                        str(Path(directory) / "assets"),
                        "--characters",
                        "aoi",
                    ]
                )
            self.assertEqual(code, 2)
            urlopen.assert_not_called()
            self.assertIn("no API key", stderr)

    def test_existing_files_are_skipped_unless_forced(self):
        with tempfile.TemporaryDirectory() as directory:
            out_dir = Path(directory) / "assets"
            out_dir.mkdir()
            (out_dir / "sprite_aoi_idle.png").write_bytes(b"old")
            (out_dir / "monster_slime.png").write_bytes(b"olds")
            argv = [
                "--api-key",
                "test-key",
                "--out-dir",
                str(out_dir),
                "--prompts-out",
                str(Path(directory) / "prompts"),
                "--characters",
                "aoi",
                "--emotions",
                "idle",
                "--monsters",
                "slime",
            ]
            with mock.patch("urllib.request.urlopen") as urlopen:
                code, stdout, _ = self.run_cli(argv)
            self.assertEqual(code, 0)
            urlopen.assert_not_called()
            self.assertEqual((out_dir / "sprite_aoi_idle.png").read_bytes(), b"old")
            self.assertEqual((out_dir / "monster_slime.png").read_bytes(), b"olds")
            self.assertIn("skipping", stdout)

    def test_force_regenerates_existing_files(self):
        with tempfile.TemporaryDirectory() as directory:
            out_dir = Path(directory) / "assets"
            out_dir.mkdir()
            (out_dir / "sprite_aoi_idle.png").write_bytes(b"old")
            argv = [
                "--api-key",
                "test-key",
                "--force",
                "--out-dir",
                str(out_dir),
                "--prompts-out",
                str(Path(directory) / "prompts"),
                "--characters",
                "aoi",
                "--emotions",
                "idle",
                "--monsters",
                "slime",
            ]
            with mock.patch(
                "urllib.request.urlopen", return_value=self.image_response(PNG + b"new")
            ):
                code, _, _ = self.run_cli(argv)
            self.assertEqual(code, 0)
            self.assertEqual(
                (out_dir / "sprite_aoi_idle.png").read_bytes(), PNG + b"new"
            )

    def test_generation_writes_the_png(self):
        with tempfile.TemporaryDirectory() as directory:
            out_dir = Path(directory) / "assets"
            with mock.patch(
                "urllib.request.urlopen", return_value=self.image_response(PNG + b"new")
            ):
                code, _, _ = self.run_cli(
                    [
                        "--api-key",
                        "test-key",
                        "--out-dir",
                        str(out_dir),
                        "--prompts-out",
                        str(Path(directory) / "prompts"),
                        "--characters",
                        "aoi",
                        "--emotions",
                        "idle",
                        "--monsters",
                        "slime",
                    ]
                )
            self.assertEqual(code, 0)
            self.assertEqual(
                (out_dir / "sprite_aoi_idle.png").read_bytes(), PNG + b"new"
            )
            self.assertEqual(
                (out_dir / "monster_slime.png").read_bytes(), PNG + b"new"
            )

    def test_api_failure_returns_non_zero(self):
        with tempfile.TemporaryDirectory() as directory:
            with mock.patch(
                "urllib.request.urlopen", side_effect=self.always(500)
            ), mock.patch("gen_art.time.sleep"):
                code, _, stderr = self.run_cli(
                    [
                        "--api-key",
                        "test-key",
                        "--out-dir",
                        str(Path(directory) / "assets"),
                        "--prompts-out",
                        str(Path(directory) / "prompts"),
                        "--characters",
                        "aoi",
                        "--emotions",
                        "idle",
                        "--monsters",
                        "slime",
                    ]
                )
            self.assertEqual(code, 1)
            self.assertIn("sprite_aoi_idle.png", stderr)
            self.assertIn("monster_slime.png", stderr)

    def test_non_png_payload_is_reported_and_never_written(self):
        # Regression: a WebP from the relay used to be written to a .png name.
        with tempfile.TemporaryDirectory() as directory:
            out_dir = Path(directory) / "assets"
            with mock.patch(
                "urllib.request.urlopen",
                return_value=self.image_response(FormatTests.WEBP),
            ) as urlopen, mock.patch("gen_art.time.sleep") as sleep:
                code, _, stderr = self.run_cli(
                    [
                        "--api-key",
                        "test-key",
                        "--assets",
                        "sprite_aoi_idle",
                        "--out-dir",
                        str(out_dir),
                        "--prompts-out",
                        str(Path(directory) / "prompts"),
                    ]
                )
            self.assertEqual(code, 1)
            self.assertIn("WebP", stderr)
            self.assertFalse((out_dir / "sprite_aoi_idle.png").exists())
            self.assertEqual(urlopen.call_count, 1)  # not transient, no retry
            sleep.assert_not_called()


class _Response:
    """Minimal stand-in for the urlopen context manager."""

    def __init__(self, body: bytes, content_type: str = "application/json"):
        self._body = body
        self.headers = {"Content-Type": content_type}

    def read(self) -> bytes:
        return self._body

    def __enter__(self):
        return self

    def __exit__(self, *exc_info):
        return False


if __name__ == "__main__":
    unittest.main(verbosity=2)
