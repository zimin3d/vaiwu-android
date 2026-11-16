"""Prompt definitions for the Vaiwu character and monster art set.

This is the machine-readable twin of ``firmware/docs/ART_PROMPTS.md`` in the
vaiwu firmware repository (``external/vaiwu``). Keep the two in sync when the
art direction changes: the style anchor and the emotion modifiers are shared by
every asset so that a set produced by one image model looks coherent.

Every character prompt is built as::

    <character base prompt>, <emotion modifier>, <style anchor>

and every monster prompt as::

    <monster base prompt>, <style anchor>

The module intentionally has no third-party imports so that ``gen_art.py`` and
the test suite can use it without any setup.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Optional, Sequence, Tuple

# --- Shared prompt fragments -------------------------------------------------

#: Appended to every prompt so the whole set shares one art style.
STYLE_ANCHOR = (
    "full body character sprite, front view, standing pose, centered "
    "composition, flat solid bright magenta background (#FF00FF) with no gradient "
    "and no shadow on it, detailed anime illustration, "
    "soft cel shading, large expressive eyes, pastel color palette, clean "
    "lineart, high quality"
)

#: Negative prompt for generators that support one.
NEGATIVE_PROMPT = (
    "text, watermark, logo, scenery background, multiple characters, cropped "
    "body, extra limbs, blurry, photo, 3d render"
)

# --- Emotions ----------------------------------------------------------------

#: Order matches vaiwu::game::Emotion and the firmware sprite table.
EMOTIONS: Tuple[str, ...] = ("idle", "happy", "sad", "angry", "sleepy", "sick", "dead")

#: One modifier per emotion, swapped while everything else stays fixed.
EMOTION_MODIFIERS: Dict[str, str] = {
    "idle": "gentle calm smile, relaxed pose, arms at sides",
    "happy": "joyful laughing, closed ^ ^ eyes, open mouth smile, small sparkles around",
    "sad": "sad drooping eyebrows, watery teary eyes, single tear drop, small frown",
    "angry": "angry furrowed brows, pouting frown, red anger vein mark on head",
    "sleepy": 'sleepy closed eyes, small yawn, "Zzz" letters floating above head',
    "sick": "dizzy spiral eyes, pale sickly greenish face, big sweat drop on temple",
    "dead": "knocked out, X X eyes, whole character in grey monochrome, flat mouth",
}

# --- Characters --------------------------------------------------------------

#: Order matches gen_sprites.py CHARS (the firmware sprite table order).
CHARACTERS: Dict[str, str] = {
    "aoi": (
        "adult anime woman, 30+ years old, mature face, long straight pastel blue hair reaching the waist "
        "with a shiny highlight band, jagged fringe, light blue eyes, soft "
        "blush, light blue sundress with thin shoulder straps, fitted bodice "
        "and flared skirt with a darker blue hem band, white socks, dark "
        "mary-jane shoes"
    ),
    "hina": (
        "adult anime woman, 30+ years old, mature face, warm peachy-orange high twintails tied with small "
        "cream bows, jagged fringe, amber eyes, soft blush, oversized cream "
        "hoodie with a kangaroo pocket, drawstrings and ribbed cuffs and hem, "
        "white socks, dark mary-jane shoes"
    ),
    "rin": (
        "adult anime woman, 30+ years old, mature face, pastel pink bob haircut curled inward with a small "
        "white triangular hairpin, pink eyes, soft blush, japanese sailor "
        "school uniform: white-pink blouse with a dark purple sailor collar "
        "with white stripes, pink neckerchief with a knot, pleated purple "
        "skirt, white socks, dark mary-jane shoes"
    ),
    "yuki": (
        "adult anime woman, 30+ years old, mature face, long lavender hime-cut hair with blunt straight "
        "bangs, blunt side locks and a small white flower hair clip, violet "
        "eyes, soft blush, pale lavender kimono with wide hanging sleeves, "
        "wrapped collar and a purple obi belt with a front knot, white socks, "
        "dark shoes"
    ),
    "akane": (
        "adult anime fox woman, 30+ years old, mature face, caramel orange hair with an ahoge, large fox "
        "ears with dark tips and cream fluff inside, big fluffy fox tail with "
        "a cream tip, amber eyes, soft blush, light orange hoodie with a paw "
        "print on the kangaroo pocket and drawstrings, white socks, dark shoes"
    ),
    "unicorn": (
        "adult anime unicorn woman, 30+ years old, mature face, long flowing wavy lilac mane with lighter "
        "pastel streaks, golden spiral horn on the forehead with a sparkle, "
        "violet eyes, soft blush, pale lilac sundress with shoulder straps and "
        "a flared skirt, white socks, dark shoes"
    ),
    "kolobok": (
        "cute round golden bread bun creature (russian kolobok fairy tale), "
        "shiny baked crust with a glossy highlight and small crust cracks, "
        "stubby little feet, simple kawaii face with big eyes and rosy cheeks"
    ),
}

# --- Monsters ----------------------------------------------------------------

#: Order matches gen_sprites.py MONSTERS (vaiwu::game::MonsterKind).
MONSTERS: Dict[str, str] = {
    "slime": (
        "cute green slime monster, gooey translucent dome with a lighter inner "
        "core and trapped bubbles, big glossy highlight, happy-evil grin, kawaii"
    ),
    "bat": (
        "cute purple bat monster, round body, spread membrane wings with "
        "visible bone struts, big glowing yellow eyes, small white fangs, kawaii"
    ),
    "shroom": (
        "grumpy mushroom monster, red cap with cream spots and a rim light, "
        "beige stem body, angry eyebrows and a small frown, kawaii"
    ),
    "fox": (
        "sly orange fox head monster, big ears with dark tips, cream muzzle, "
        "half-lidded amber eyes, fluffy curled tail with cream tip, kawaii"
    ),
    "ghost": (
        "cute white sheet ghost, wavy hem, floating wisps, hollow dark eyes "
        "and a wailing mouth, faint inner glow, kawaii"
    ),
    "golem": (
        "small stone golem monster, blocky grey rock body and head with "
        "bevelled edges, glowing yellow eyes, cracks and green moss patches, "
        "kawaii"
    ),
}

# --- Targets -----------------------------------------------------------------


@dataclass(frozen=True)
class AssetTarget:
    """Final firmware size plus the closest API size for the same aspect."""

    kind: str
    width: int
    height: int
    aspect: str
    #: Long side 1024-1536 px, matching the ART_PROMPTS.md "generate large" tip.
    api_size: str


#: Mirrors the "Target sizes" table in firmware/docs/ART_PROMPTS.md.
#:
#: No image API offers 3:4 or 16:7 exactly, so ``api_size`` picks the closest
#: supported box: 1024x1536 is 2:3, which the doc explicitly allows for
#: characters ("3:4 (or 2:3)"), and 1536x1024 is the widest box available for
#: the 16:7 backdrop. The final crop to the firmware size happens on
#: conversion, not here.
TARGETS: Dict[str, AssetTarget] = {
    "character": AssetTarget("character", 96, 128, "3:4", "1024x1536"),
    "monster": AssetTarget("monster", 96, 96, "1:1", "1024x1024"),
    "background": AssetTarget("background", 320, 140, "16:7", "1536x1024"),
}


# --- Prompt building ---------------------------------------------------------


def normalize(text: str) -> str:
    """Collapse the wrapped source text into a single-line prompt fragment."""
    return " ".join(text.split())


def compose(*fragments: str) -> str:
    """Join non-empty fragments with ", " into one single-line prompt."""
    parts = [normalize(fragment) for fragment in fragments if fragment]
    return ", ".join(part for part in parts if part)


def character_prompt(character: str, emotion: str) -> str:
    """Full prompt for one character/emotion pair."""
    if character not in CHARACTERS:
        raise ValueError(f"unknown character: {character!r}")
    if emotion not in EMOTION_MODIFIERS:
        raise ValueError(f"unknown emotion: {emotion!r}")
    return compose(CHARACTERS[character], EMOTION_MODIFIERS[emotion], STYLE_ANCHOR)


def monster_prompt(monster: str) -> str:
    """Full prompt for one battle monster."""
    if monster not in MONSTERS:
        raise ValueError(f"unknown monster: {monster!r}")
    return compose(MONSTERS[monster], STYLE_ANCHOR)


# --- Asset list --------------------------------------------------------------


@dataclass(frozen=True)
class Asset:
    """One image to generate, addressed by its firmware preview file name."""

    name: str
    kind: str
    prompt: str
    character: Optional[str] = None
    emotion: Optional[str] = None
    monster: Optional[str] = None

    @property
    def filename(self) -> str:
        return f"{self.name}.png"

    @property
    def target(self) -> AssetTarget:
        return TARGETS[self.kind]


def build_assets(
    characters: Optional[Sequence[str]] = None,
    emotions: Optional[Sequence[str]] = None,
    monsters: Optional[Sequence[str]] = None,
) -> List[Asset]:
    """Build the asset list in firmware table order.

    ``None`` for a selector means "all", so the default call returns the whole
    set: 7 characters x 7 emotions plus 6 monsters.
    """
    selected_characters = tuple(characters) if characters else tuple(CHARACTERS)
    selected_emotions = tuple(emotions) if emotions else EMOTIONS
    selected_monsters = tuple(monsters) if monsters else tuple(MONSTERS)

    for character in selected_characters:
        if character not in CHARACTERS:
            raise ValueError(f"unknown character: {character!r}")
    for emotion in selected_emotions:
        if emotion not in EMOTION_MODIFIERS:
            raise ValueError(f"unknown emotion: {emotion!r}")
    for monster in selected_monsters:
        if monster not in MONSTERS:
            raise ValueError(f"unknown monster: {monster!r}")

    assets: List[Asset] = []
    for character in selected_characters:
        for emotion in selected_emotions:
            assets.append(
                Asset(
                    name=f"sprite_{character}_{emotion}",
                    kind="character",
                    prompt=character_prompt(character, emotion),
                    character=character,
                    emotion=emotion,
                )
            )
    for monster in selected_monsters:
        assets.append(
            Asset(
                name=f"monster_{monster}",
                kind="monster",
                prompt=monster_prompt(monster),
                monster=monster,
            )
        )
    return assets
