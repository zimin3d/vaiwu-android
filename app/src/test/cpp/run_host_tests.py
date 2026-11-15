#!/usr/bin/env python3
import shlex
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
GAME_DIR = ROOT / "external/vaiwu/firmware/components/game"
SOURCES = [
    "Assistant.cpp",
    "Characters.cpp",
    "I18n.cpp",
    "OfflinePhrases.cpp",
    "Rpg.cpp",
    "Shop.cpp",
    "SoundSynth.cpp",
    "VaiwuPet.cpp",
]


def run(command):
    print("+", shlex.join(str(part) for part in command), flush=True)
    subprocess.run(command, cwd=ROOT, check=True)


def main():
    with tempfile.TemporaryDirectory(prefix="vaiwu-android-native-tests-") as output_dir:
        test_binary = Path(output_dir) / "test_android_native"
        run([
            "g++",
            "-std=c++20",
            "-Wall",
            "-Wextra",
            "-Werror",
            "-O1",
            f"-I{GAME_DIR / 'include'}",
            "-Iapp/src/main/cpp",
            *(str(GAME_DIR / "src" / source) for source in SOURCES),
            "app/src/test/cpp/test_android_native.cpp",
            "-o",
            str(test_binary),
        ])
        run([test_binary])


if __name__ == "__main__":
    main()
