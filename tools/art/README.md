# Vaiwu art generation

Tooling for regenerating the character and monster art with an external,
OpenAI-compatible image model, and for slicing the raw generations into the
sprite sizes the app ships.

The prompts mirror `firmware/docs/ART_PROMPTS.md` in the
[vaiwu](https://github.com/zimin3d/vaiwu) firmware repository
(`external/vaiwu`). `art_prompts.py` is the machine-readable twin of that
document; change one and change the other.

## Files

| file | role |
| --- | --- |
| `art_prompts.py` | style anchor, negative prompt, emotion modifiers, character and monster base prompts, target sizes |
| `gen_art.py` | CLI: writes the prompt files and calls the images API |
| `slice_art.py` | CLI: keys out the white background, crops, fits to the target size, rebuilds the contact sheets |
| `pngio.py` | minimal PNG codec (stdlib only) used by the slicer |
| `test_art_prompts.py` | regression tests for prompts and the API layer |
| `test_slice_art.py` | regression tests for the slicer and the PNG codec |
| `prompts/` | generated prompt files (checked in) |

## Pipeline

```sh
export VAIWU_ART_API_KEY=sk-...
python3 tools/art/gen_art.py     # 1. raw 1254x1254 PNGs -> tools/art/out/
python3 tools/art/slice_art.py   # 2. 96x128 / 96x96 transparent -> external/vaiwu/firmware/assets/
```

`tools/art/out/` is git-ignored: the raw generations are large, keep the
white studio background and are not what the app loads. `slice_art.py`
produces the files that ship:

1. flood-fills the flat white background from the image border, so white
   parts of the drawing (socks, blouse, kimono) stay opaque while the studio
   backdrop becomes transparent, and feathers the anti-aliased ring,
2. crops to the remaining content with a small margin,
3. scales it into the target box without distortion and centres it on a
   transparent canvas,
4. rebuilds `sprites_preview.png` and `monsters_preview.png` from the sliced
   sprites, so the gallery contact sheets cannot drift from the files next to
   them (a sheet is skipped with a note when a sprite is missing).

Both steps are stdlib-only, so neither the tools nor the tests need Pillow or
any other dependency in CI.

## Prompt set

Every character asset is

```
<character base prompt>, <emotion modifier>, <style anchor>
```

and every monster asset is `<monster base prompt>, <style anchor>`, so the whole
set shares one style and only the emotion tag changes between sprites.

| class | count | firmware size | aspect | API size |
| --- | --- | --- | --- | --- |
| character sprites | 7 x 7 = 49 | 96x128 | 3:4 | 1024x1536 (2:3) |
| battle monsters | 6 | 96x96 | 1:1 | 1024x1024 |

No API offers 3:4 or 16:7 exactly, so `--size` picks the closest supported box;
the crop to the firmware size happens on conversion, not here.

## Usage

By default the tool writes the prompt files **and** generates the raw PNGs
into `tools/art/out/`, skipping files that already exist:

```sh
export VAIWU_ART_API_KEY=sk-...
python3 tools/art/gen_art.py
```

Common variations:

```sh
python3 tools/art/gen_art.py --list                 # what would be built
python3 tools/art/gen_art.py --no-generate          # prompt files only, no network
python3 tools/art/gen_art.py --assets sprite_aoi_idle   # exactly one image
python3 tools/art/gen_art.py --characters aoi       # one character (existing files are skipped)
python3 tools/art/gen_art.py --characters aoi --emotions idle --monsters slime
python3 tools/art/gen_art.py --force                # overwrite existing PNGs
python3 tools/art/gen_art.py --size 1024x1024       # override the API box
```

`--characters`, `--emotions` and `--monsters` are independent filters; an
omitted filter means "all". Because existing files are skipped, regenerating
one character does not re-request the rest of the set.

`--assets` selects exact asset names instead, which is the way to pay for a
single image, and to retry the files listed after a failed run. It cannot be
combined with the other three selectors.

The tool exits non-zero if any asset fails, and prints the failing file names.
The payload must be a real PNG: a provider that answers with WebP, JPEG or
anything else is reported as a non-transient failure naming the format, and
nothing is written to disk.

### Slicing

```sh
python3 tools/art/slice_art.py              # everything found under tools/art/out/
python3 tools/art/slice_art.py --list       # what would be built
python3 tools/art/slice_art.py --dry-run    # slice, but write nothing
python3 tools/art/slice_art.py --assets monster_shroom   # only these assets
```

| flag | default | meaning |
| --- | --- | --- |
| `--src` | `tools/art/out` | raw generations, searched recursively |
| `--dest` | `external/vaiwu/firmware/assets` | where the sliced PNGs go |
| `--assets` | *(all)* | subset of asset names |
| `--white-threshold` | `248` | per-channel level at or above which a pixel is background |
| `--feather` | `8` | levels below the threshold that are faded instead of cut; `0` disables |
| `--margin` | `0.02` | margin around the content as a fraction of its shorter side |
| `--no-previews` | off | skip rebuilding the contact sheets |
| `--dry-run` | off | write nothing |

It exits non-zero if any asset fails, and reports which. The contact sheets
are only rebuilt on a run without failures.

## Configuration

Flags win over environment variables.

| flag | environment | default |
| --- | --- | --- |
| `--base-url` | `VAIWU_ART_BASE_URL`, `OPENAI_BASE_URL` | `https://api.openai.com` |
| `--api-key` | `VAIWU_ART_API_KEY`, `OPENAI_API_KEY` | *(none)* |
| `--model` | `VAIWU_ART_MODEL`, `OPENAI_IMAGE_MODEL` | `gpt-image-1` |
| `--response-format` | - | `b64_json` |
| `--retries` | - | `2` (extra attempts after a transient failure) |
| `--retry-delay` | - | `2` seconds before the first retry, then doubling |
| `--out-dir` | - | `tools/art/out` (raw generations) |
| `--prompts-out` | - | `tools/art/prompts` |

The request is `POST <base-url>/v1/images/generations` with
`{"model", "prompt", "n": 1, "size"}`. Both `b64_json` and `url` responses are
understood and must decode to a PNG. If the server rejects `response_format`
(as `gpt-image-1` does), the tool retries once without that field.

### Connecting a provider

Any host that implements `POST /v1/images/generations` works. Take the base URL
and the model name from the provider's documentation; the base URL may be given
with or without a trailing `/v1`.

| provider | `--base-url` | `--model` |
| --- | --- | --- |
| OpenAI | `https://api.openai.com` | `gpt-image-1` or `dall-e-3` |
| self-hosted gateway (LiteLLM, one-api, ...) | the gateway root | whatever it exposes |
| any other OpenAI-compatible host | its API root | its image model |

Keep the key out of the repository with a local `.env` (already git-ignored):

```sh
cat > .env <<'EOF'
VAIWU_ART_API_KEY=sk-...
VAIWU_ART_BASE_URL=https://api.openai.com
VAIWU_ART_MODEL=gpt-image-1
EOF
set -a; . ./.env; set +a
python3 tools/art/gen_art.py
```

Check the wiring before spending anything:

```sh
python3 tools/art/gen_art.py --list                       # offline, no key
python3 tools/art/gen_art.py --no-generate                # no network
python3 tools/art/gen_art.py --assets sprite_aoi_idle     # exactly one request
```

Requests go through `urllib`, so `HTTPS_PROXY` / `https_proxy` are honoured.
A failed request prints the HTTP status and the provider's own message, for
example `401: invalid api key` or `404: ...` when the base URL is wrong.

Rate limits, server errors and network failures are retried a couple of times
with a growing delay, because shared relays flap. A rejected key or a wrong
base URL is not retried. Use `--retries 0` to fail fast.

Some relays answer every path with their web UI, so a wrong base URL shows up
as `200` plus HTML rather than a `404`. The tool detects that and says so
instead of reporting a JSON parse error.

The tool always sends `Authorization: Bearer <key>`. A provider that wants a
different header needs a small change in `post_json`.

## Generated prompt files

```sh
python3 tools/art/gen_art.py --no-generate
```

writes:

- `prompts/prompts.json` - every asset with its prompt, target size and aspect
- `prompts/prompts.md` - the same, formatted for reading
- `prompts/characters/<character>.txt` - all 7 emotions of one character
- `prompts/monsters/<monster>.txt` - one monster

These files are committed, so the prompt set is reviewable without running the
tool.

## Tests

```sh
python3 tools/art/test_art_prompts.py
python3 tools/art/test_slice_art.py
python3 -m unittest discover -s tools/art -t tools/art
```

`test_art_prompts.py` covers prompt composition (style anchor on every asset,
exactly one emotion modifier per sprite, no leftover `<EMOTION>` placeholder),
the target sizes, the prompt-file writer, and the API layer with `urlopen`
stubbed out — including the rejection of non-PNG payloads. It also asserts
that the character, emotion and monster order matches `gen_sprites.py` in the
firmware submodule, because the firmware indexes its sprite table by that
order. That check is skipped when the submodule is not checked out.

`test_slice_art.py` covers the PNG codec (every colour type and scanline
filter, CRC and truncation errors), background removal (border flood fill,
enclosed whites kept, feathering), cropping, scaling, the contact sheets and
the CLI. Every fixture is built with `pngio`, so the suite also proves the
codec round-trips.

## Known gap

`gen_sprites.py` draws the firmware C arrays procedurally and does not read
PNGs, so the sliced files in `firmware/assets/` are previews; emitting
`sprites.c` from the generated art is still to be written, and `ART_PROMPTS.md`
describes that as the intended follow-up.
