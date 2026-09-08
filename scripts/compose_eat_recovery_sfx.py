#!/usr/bin/env python3
"""Build the game's eating cue from two locally downloaded Sound Effect Lab MP3s.

Download "食べ物をパクッ" (suck1.mp3) and "キラッ1" (kira1.mp3) from
https://soundeffect-lab.info/sound/anime/ into build/sfx-sources first.
The sources have their own license, not CC0: see assets/audio/sfx/README.md.
Do not distribute the source downloads or this mix as standalone sound assets.

Python's standard library performs the mix. The project's existing JLayer JAR
and Java decode MP3s; no additional packages or network access are needed.
Both production copies are written as mono, 44.1 kHz, 16-bit PCM WAV.
"""

from __future__ import annotations

import argparse
import hashlib
import math
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

from generate_eat_recovery_sfx import PCM_MAX, SAMPLE_RATE, metrics, normalize, write_wav


REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
DURATION_SECONDS = 0.800
BITE_START_SECONDS = 0.018
# The shared animation's first icon arrives at (0.12 + 0.44) * 800 ms.
CHIME_START_SECONDS = 0.448
BITE_PEAK_DBFS = -4.0
CHIME_PEAK_DBFS = -10.0
SOURCE_NAMES = ("suck1.mp3", "kira1.mp3")
DEFAULT_DESTINATIONS = (
    REPOSITORY_ROOT / "assets/audio/sfx/eat_recovery.wav",
    REPOSITORY_ROOT / "androidApp/src/main/res/raw/eat_recovery.wav",
)


def read_mono_pcm(path: Path) -> list[float]:
    with wave.open(str(path), "rb") as source:
        channels = source.getnchannels()
        if (
            source.getframerate() != SAMPLE_RATE
            or source.getsampwidth() != 2
            or source.getcomptype() != "NONE"
            or channels not in (1, 2)
        ):
            raise ValueError(f"Expected mono/stereo 44.1 kHz 16-bit PCM: {path}")
        payload = source.readframes(source.getnframes())
    if not payload or len(payload) % (2 * channels):
        raise ValueError(f"Missing or incomplete audio frames: {path}")
    pcm = struct.unpack(f"<{len(payload) // 2}h", payload)
    return [
        sum(pcm[index:index + channels]) / (channels * 32768.0)
        for index in range(0, len(pcm), channels)
    ]


def trim_silence(samples: list[float]) -> list[float]:
    """Remove MP3 padding and silent tails, keeping 3 ms before the attack."""
    if any(not math.isfinite(sample) for sample in samples):
        raise ValueError("Source contains non-finite audio samples")
    active = [index for index, sample in enumerate(samples) if abs(sample) >= 0.001]
    if not active:
        raise ValueError("Source audio is silent")
    padding = round(0.003 * SAMPLE_RATE)
    return samples[max(0, active[0] - padding):min(len(samples), active[-1] + padding + 1)]


def prepare_clip(
    samples: list[float], *, max_seconds: float, peak_dbfs: float, fade_out_seconds: float
) -> list[float]:
    clip = trim_silence(samples)[:round(max_seconds * SAMPLE_RATE)]
    if len(clip) < 2:
        raise ValueError("Source audio is too short")
    fade_in = min(round(0.002 * SAMPLE_RATE), len(clip))
    fade_out = min(round(fade_out_seconds * SAMPLE_RATE), len(clip))
    for index in range(fade_in):
        clip[index] *= index / max(1, fade_in - 1)
    for offset in range(fade_out):
        remaining = 1.0 - offset / max(1, fade_out - 1)
        clip[len(clip) - fade_out + offset] *= remaining * remaining
    normalize(clip, peak_dbfs)
    return clip


def render(bite_samples: list[float], chime_samples: list[float]) -> list[int]:
    """One composite cue: an immediate bite, then a quieter arrival sparkle."""
    bite = prepare_clip(
        bite_samples,
        max_seconds=0.280,
        peak_dbfs=BITE_PEAK_DBFS,
        fade_out_seconds=0.018,
    )
    chime = prepare_clip(
        chime_samples,
        max_seconds=DURATION_SECONDS - CHIME_START_SECONDS,
        peak_dbfs=CHIME_PEAK_DBFS,
        fade_out_seconds=0.080,
    )
    mix = [0.0] * round(DURATION_SECONDS * SAMPLE_RATE)
    for clip, start_seconds in ((bite, BITE_START_SECONDS), (chime, CHIME_START_SECONDS)):
        start = round(start_seconds * SAMPLE_RATE)
        for offset, sample in enumerate(clip[:len(mix) - start]):
            mix[start + offset] += sample
    if max(map(abs, mix)) >= 1.0:
        raise ValueError("Mix would clip; reduce the component gains")
    return [round(sample * PCM_MAX) for sample in mix]


def decode_mp3(source: Path, destination: Path) -> None:
    jar = REPOSITORY_ROOT / "libs/jlayer-1.0.1.jar"
    result = subprocess.run(
        ["java", "-cp", str(jar), "javazoom.jl.converter.jlc", "-p", str(destination), str(source)],
        capture_output=True,
        text=True,
        check=False,
        timeout=30,
    )
    if result.returncode != 0 or not destination.is_file():
        raise RuntimeError(f"JLayer could not decode {source.name}: {result.stderr.strip()}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=REPOSITORY_ROOT / "build/sfx-sources",
        help="Directory containing the two official source MP3s (not redistributed).",
    )
    parser.add_argument(
        "--production-file",
        type=Path,
        action="append",
        help="Exact WAV destination; repeat for multiple copies. Defaults to both game assets.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    sources = [args.source_dir.resolve() / name for name in SOURCE_NAMES]
    for source in sources:
        if not source.is_file():
            raise FileNotFoundError(f"Download {source.name} from the official asset page into {source.parent}")
    with tempfile.TemporaryDirectory(prefix="mogura-eat-sfx-") as directory:
        decoded = []
        for source in sources:
            destination = Path(directory) / f"{source.stem}.wav"
            decode_mp3(source, destination)
            decoded.append(read_mono_pcm(destination))
        pcm = render(*decoded)
    for destination in args.production_file or DEFAULT_DESTINATIONS:
        write_wav(destination, pcm)
        peak_dbfs, rms_dbfs = metrics(pcm)
        print(f"{destination}: {len(pcm) / SAMPLE_RATE:.3f}s, peak={peak_dbfs:.2f} dBFS, rms={rms_dbfs:.2f} dBFS")
    for source in sources:
        print(f"Source SHA-256 {source.name}: {hashlib.sha256(source.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
