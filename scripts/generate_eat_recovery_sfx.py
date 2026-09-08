#!/usr/bin/env python3
"""Generate the original, sample-free eat-and-recover alternative.

The sound is intentionally dry and playful rather than realistic: a soft bite,
two muted chew pulses, a rounded swallow, and a small wooden recovery chime.
Only Python's standard library is used. Output is mono, 44.1 kHz, signed
16-bit PCM WAV. The production effect is now rebuilt by
compose_eat_recovery_sfx.py from the licensed Sound Effect Lab samples.
"""

from __future__ import annotations

import argparse
import math
import random
import struct
import wave
from pathlib import Path


SAMPLE_RATE = 44_100
PCM_MAX = 32_767
DURATION_SECONDS = 0.720
TARGET_PEAK_DBFS = -4.0


def add_soft_bite(samples: list[float], *, start_seconds: float, seed: int) -> None:
    """Add a compact cork-like bite with a short paper-dry crunch."""
    start = round(start_seconds * SAMPLE_RATE)
    length = round(0.105 * SAMPLE_RATE)
    rng = random.Random(seed)
    smoothed_noise = 0.0
    phase = 0.0
    for offset in range(min(length, len(samples) - start)):
        time = offset / SAMPLE_RATE
        position = offset / max(1, length - 1)
        attack = math.sin(min(1.0, time / 0.002) * math.pi / 2.0) ** 2
        release = (1.0 - position) ** 2.3
        frequency = 520.0 - 110.0 * position
        phase += 2.0 * math.pi * frequency / SAMPLE_RATE
        body = math.sin(phase) + 0.18 * math.sin(2.45 * phase + 0.4)
        noise = rng.uniform(-1.0, 1.0)
        smoothed_noise += 0.22 * (noise - smoothed_noise)
        crunch = (noise - smoothed_noise) * math.exp(-time / 0.027)
        samples[start + offset] += attack * release * (0.38 * body + 0.19 * crunch)


def add_chew_pulse(
    samples: list[float],
    *,
    start_seconds: float,
    frequency: float,
    amplitude: float,
) -> None:
    """Add one short, rounded and deliberately non-wet chew pulse."""
    start = round(start_seconds * SAMPLE_RATE)
    length = round(0.090 * SAMPLE_RATE)
    phase = 0.0
    for offset in range(min(length, len(samples) - start)):
        time = offset / SAMPLE_RATE
        position = offset / max(1, length - 1)
        attack = math.sin(min(1.0, time / 0.004) * math.pi / 2.0) ** 2
        release = (1.0 - position) ** 2.6
        phase += 2.0 * math.pi * (frequency * (1.0 - 0.12 * position)) / SAMPLE_RATE
        body = math.sin(phase) + 0.12 * math.sin(2.02 * phase + 0.25)
        samples[start + offset] += amplitude * attack * release * body


def add_swallow(samples: list[float], *, start_seconds: float) -> None:
    """Add a soft downward pitch gesture that reads as a cartoon swallow."""
    start = round(start_seconds * SAMPLE_RATE)
    length = round(0.150 * SAMPLE_RATE)
    phase = 0.0
    for offset in range(min(length, len(samples) - start)):
        position = offset / max(1, length - 1)
        envelope = math.sin(math.pi * position) ** 1.8
        frequency = 310.0 - 145.0 * position
        phase += 2.0 * math.pi * frequency / SAMPLE_RATE
        samples[start + offset] += 0.22 * envelope * (
            math.sin(phase) + 0.08 * math.sin(2.0 * phase)
        )


def add_recovery_chime(
    samples: list[float],
    *,
    start_seconds: float,
    frequency: float,
    amplitude: float,
) -> None:
    """Add a softly struck wooden-bar recovery confirmation."""
    start = round(start_seconds * SAMPLE_RATE)
    attack_seconds = 0.0025
    for index in range(start, len(samples)):
        time = (index - start) / SAMPLE_RATE
        attack = math.sin(min(1.0, time / attack_seconds) * math.pi / 2.0) ** 2
        value = (
            math.sin(2.0 * math.pi * frequency * time) * math.exp(-time / 0.155)
            + 0.13
            * math.sin(2.0 * math.pi * frequency * 2.756 * time + 0.2)
            * math.exp(-time / 0.070)
            + 0.035
            * math.sin(2.0 * math.pi * frequency * 5.404 * time + 0.4)
            * math.exp(-time / 0.042)
        )
        samples[index] += amplitude * attack * value


def remove_dc(samples: list[float]) -> list[float]:
    output: list[float] = []
    previous_input = 0.0
    previous_output = 0.0
    for sample in samples:
        filtered = sample - previous_input + 0.995 * previous_output
        output.append(filtered)
        previous_input = sample
        previous_output = filtered
    return output


def apply_edge_fades(samples: list[float]) -> None:
    fade_in_samples = round(0.003 * SAMPLE_RATE)
    fade_out_samples = min(round(0.060 * SAMPLE_RATE), len(samples))
    for index in range(fade_in_samples):
        position = index / max(1, fade_in_samples - 1)
        samples[index] *= position * position
    for offset in range(fade_out_samples):
        index = len(samples) - fade_out_samples + offset
        remaining = 1.0 - offset / max(1, fade_out_samples - 1)
        samples[index] *= remaining * remaining
    samples[0] = 0.0
    samples[-1] = 0.0


def normalize(samples: list[float], target_dbfs: float) -> None:
    target_peak = 10.0 ** (target_dbfs / 20.0)
    current_peak = max(abs(sample) for sample in samples)
    if current_peak == 0.0:
        raise ValueError("Cannot normalize silent output")
    scale = target_peak / current_peak
    for index, sample in enumerate(samples):
        samples[index] = sample * scale


def render() -> list[int]:
    samples = [0.0] * round(DURATION_SECONDS * SAMPLE_RATE)
    add_soft_bite(samples, start_seconds=0.018, seed=821)
    add_chew_pulse(samples, start_seconds=0.135, frequency=430.0, amplitude=0.24)
    add_chew_pulse(samples, start_seconds=0.245, frequency=380.0, amplitude=0.22)
    add_swallow(samples, start_seconds=0.330)
    add_recovery_chime(samples, start_seconds=0.455, frequency=783.99, amplitude=0.30)
    add_recovery_chime(samples, start_seconds=0.515, frequency=987.77, amplitude=0.23)
    samples = remove_dc(samples)
    apply_edge_fades(samples)
    normalize(samples, TARGET_PEAK_DBFS)
    return [
        max(-PCM_MAX - 1, min(PCM_MAX, round(sample * PCM_MAX)))
        for sample in samples
    ]


def write_wav(path: Path, pcm: list[int]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = struct.pack(f"<{len(pcm)}h", *pcm)
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(payload)


def metrics(pcm: list[int]) -> tuple[float, float]:
    peak = max(abs(sample) for sample in pcm) / PCM_MAX
    rms = math.sqrt(sum((sample / PCM_MAX) ** 2 for sample in pcm) / len(pcm))
    return 20.0 * math.log10(peak), 20.0 * math.log10(rms)


def parse_args() -> argparse.Namespace:
    repository_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--production-file",
        type=Path,
        default=repository_root / "build" / "sfx-variants" / "eat_recovery_synthesized.wav",
        help="Exact alternative WAV destination; production assets are not overwritten by default.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    pcm = render()
    write_wav(args.production_file, pcm)
    peak_dbfs, rms_dbfs = metrics(pcm)
    print(
        f"{args.production_file}: {len(pcm) / SAMPLE_RATE:.3f}s, "
        f"peak={peak_dbfs:.2f} dBFS, rms={rms_dbfs:.2f} dBFS"
    )


if __name__ == "__main__":
    main()
