#!/usr/bin/env python3
"""Generate four original, sample-free capture-failure sound candidates.

Only Python's standard library is used. The synthesis combines a short wooden
mallet transient with either tuned-bar notes or a playful elastic miss gesture.
Output is mono, 44.1 kHz, signed 16-bit PCM WAV.
"""

from __future__ import annotations

import argparse
import math
import random
import struct
import wave
from dataclasses import dataclass
from pathlib import Path


SAMPLE_RATE = 44_100
PCM_MAX = 32_767
XYLOPHONE_PARTIALS = (
    (1.000, 1.000, 1.00),
    (2.756, 0.150, 2.10),
    (5.404, 0.045, 3.40),
)


@dataclass(frozen=True)
class SoundDesign:
    filename: str
    duration_seconds: float
    pop_start: float
    pop_frequency: float
    pop_amplitude: float
    high_start: float
    high_frequency: float
    high_amplitude: float
    high_decay: float
    low_start: float
    low_frequency: float
    low_amplitude: float
    low_decay: float
    target_peak_dbfs: float
    noise_seed: int


DESIGNS = (
    SoundDesign(
        filename="capture_failure_pokon_drop.wav",
        duration_seconds=0.540,
        pop_start=0.018,
        pop_frequency=410.0,
        pop_amplitude=0.38,
        high_start=0.092,
        high_frequency=1_174.66,  # D6
        high_amplitude=0.43,
        high_decay=0.085,
        low_start=0.244,
        low_frequency=880.00,  # A5
        low_amplitude=0.50,
        low_decay=0.105,
        target_peak_dbfs=-4.0,
        noise_seed=104,
    ),
    SoundDesign(
        filename="capture_failure_wooden_oops.wav",
        duration_seconds=0.610,
        pop_start=0.024,
        pop_frequency=360.0,
        pop_amplitude=0.42,
        high_start=0.115,
        high_frequency=1_046.50,  # C6
        high_amplitude=0.42,
        high_decay=0.100,
        low_start=0.292,
        low_frequency=783.99,  # G5
        low_amplitude=0.50,
        low_decay=0.120,
        target_peak_dbfs=-4.5,
        noise_seed=205,
    ),
    SoundDesign(
        filename="capture_failure_bouncy_miss.wav",
        duration_seconds=0.480,
        pop_start=0.012,
        pop_frequency=470.0,
        pop_amplitude=0.34,
        high_start=0.070,
        high_frequency=1_318.51,  # E6
        high_amplitude=0.40,
        high_decay=0.070,
        low_start=0.202,
        low_frequency=987.77,  # B5
        low_amplitude=0.48,
        low_decay=0.092,
        target_peak_dbfs=-3.5,
        noise_seed=306,
    ),
)

PLAYFUL_MISS_FILENAME = "capture_failure_playful_miss.wav"
PLAYFUL_MISS_DURATION_SECONDS = 0.540
PLAYFUL_MISS_TARGET_PEAK_DBFS = -3.5


def add_tuned_bar(
    samples: list[float],
    *,
    start_seconds: float,
    frequency: float,
    amplitude: float,
    decay_seconds: float,
    bend: float = 0.018,
) -> None:
    """Add a softly struck tuned wooden bar with rapidly damped upper modes."""
    start = round(start_seconds * SAMPLE_RATE)
    attack_seconds = 0.0022
    bend_seconds = 0.030
    for index in range(start, len(samples)):
        time = (index - start) / SAMPLE_RATE
        attack = math.sin(min(1.0, time / attack_seconds) * math.pi / 2.0) ** 2
        value = 0.0
        for ratio, partial_gain, damping in XYLOPHONE_PARTIALS:
            # The brief downward micro-bend softens the synthetic onset.
            phase_time = time + bend * bend_seconds * (1.0 - math.exp(-time / bend_seconds))
            phase = 2.0 * math.pi * frequency * ratio * phase_time
            envelope = attack * math.exp(-time * damping / decay_seconds)
            value += partial_gain * envelope * math.sin(phase)
        samples[index] += amplitude * value


def add_wooden_pop(
    samples: list[float],
    *,
    start_seconds: float,
    frequency: float,
    amplitude: float,
    seed: int,
) -> None:
    """Add a compact 'pokon' transient without square waves or buzzer timbre."""
    start = round(start_seconds * SAMPLE_RATE)
    length = round(0.075 * SAMPLE_RATE)
    rng = random.Random(seed)
    previous_noise = 0.0
    for offset in range(min(length, len(samples) - start)):
        time = offset / SAMPLE_RATE
        attack = math.sin(min(1.0, time / 0.0014) * math.pi / 2.0) ** 2
        body = (
            math.sin(2.0 * math.pi * frequency * time) * math.exp(-time / 0.021)
            + 0.24
            * math.sin(2.0 * math.pi * frequency * 2.63 * time + 0.35)
            * math.exp(-time / 0.012)
        )
        noise = rng.uniform(-1.0, 1.0)
        high_pass_noise = noise - previous_noise
        previous_noise = noise
        click = 0.055 * high_pass_noise * math.exp(-time / 0.006)
        samples[start + offset] += amplitude * attack * (body + click)


def add_band_limited_swoosh(
    samples: list[float],
    *,
    start_seconds: float,
    duration_seconds: float,
    amplitude: float,
    seed: int,
) -> None:
    """Add a soft 500 Hz--3 kHz air gesture for the initial whiff."""
    start = round(start_seconds * SAMPLE_RATE)
    length = round(duration_seconds * SAMPLE_RATE)
    rng = random.Random(seed)
    low_cut_coefficient = 1.0 - math.exp(-2.0 * math.pi * 500.0 / SAMPLE_RATE)
    high_cut_coefficient = 1.0 - math.exp(-2.0 * math.pi * 3_000.0 / SAMPLE_RATE)
    low_1 = low_2 = high_1 = high_2 = 0.0

    for offset in range(min(length, len(samples) - start)):
        position = offset / max(1, length - 1)
        noise = rng.uniform(-1.0, 1.0)

        # The difference between two cascaded low-pass filters forms a gentle
        # band-pass without the sharp resonance associated with alert sounds.
        high_1 += high_cut_coefficient * (noise - high_1)
        high_2 += high_cut_coefficient * (high_1 - high_2)
        low_1 += low_cut_coefficient * (noise - low_1)
        low_2 += low_cut_coefficient * (low_1 - low_2)
        band_limited_noise = high_2 - low_2

        envelope = math.sin(math.pi * position) ** 1.35
        forward_motion = 0.72 + 0.28 * position
        samples[start + offset] += (
            amplitude * envelope * forward_motion * band_limited_noise
        )


def add_elastic_glide(
    samples: list[float],
    *,
    start_seconds: float,
    duration_seconds: float,
    amplitude: float,
) -> None:
    """Add one continuous, damped pitch gesture instead of two alert-like notes."""
    start = round(start_seconds * SAMPLE_RATE)
    length = round(duration_seconds * SAMPLE_RATE)
    phase = 0.0
    for offset in range(min(length, len(samples) - start)):
        time = offset / SAMPLE_RATE
        position = offset / max(1, length - 1)
        attack = math.sin(min(1.0, time / 0.006) * math.pi / 2.0) ** 2
        release = math.cos(position * math.pi / 2.0) ** 1.3

        # The pitch falls from about 1.28 kHz, overshoots gently, then settles
        # near 760 Hz. Integrating the phase keeps the motion continuous.
        frequency = 760.0 + 520.0 * math.exp(-time / 0.070) * math.cos(
            2.0 * math.pi * 5.2 * time
        )
        phase += 2.0 * math.pi * frequency / SAMPLE_RATE
        body = math.sin(phase) + 0.15 * math.sin(2.02 * phase + 0.32)
        samples[start + offset] += amplitude * attack * release * body


def add_small_hop(
    samples: list[float],
    *,
    start_seconds: float,
    duration_seconds: float,
    amplitude: float,
) -> None:
    """Add a quiet up-and-down chirp that reads as a small physical bounce."""
    start = round(start_seconds * SAMPLE_RATE)
    length = round(duration_seconds * SAMPLE_RATE)
    phase = 0.0
    for offset in range(min(length, len(samples) - start)):
        position = offset / max(1, length - 1)
        envelope = math.sin(math.pi * position) ** 1.5
        frequency = 790.0 + 285.0 * math.sin(math.pi * position) - 35.0 * position
        phase += 2.0 * math.pi * frequency / SAMPLE_RATE
        tone = math.sin(phase) + 0.10 * math.sin(2.18 * phase + 0.2)
        samples[start + offset] += amplitude * envelope * tone


def remove_dc(samples: list[float]) -> list[float]:
    """Apply a gentle first-order DC blocker."""
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
    """Force a zero start/end and a smooth 55 ms tail to prevent clicks."""
    fade_in_samples = round(0.003 * SAMPLE_RATE)
    fade_out_samples = min(round(0.055 * SAMPLE_RATE), len(samples))
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


def quantize(samples: list[float]) -> list[int]:
    return [
        max(-PCM_MAX - 1, min(PCM_MAX, round(sample * PCM_MAX)))
        for sample in samples
    ]


def metrics(pcm: list[int]) -> tuple[float, float]:
    peak = max(abs(sample) for sample in pcm) / PCM_MAX
    rms = math.sqrt(sum((sample / PCM_MAX) ** 2 for sample in pcm) / len(pcm))
    peak_dbfs = 20.0 * math.log10(peak) if peak else float("-inf")
    rms_dbfs = 20.0 * math.log10(rms) if rms else float("-inf")
    return peak_dbfs, rms_dbfs


def render(design: SoundDesign) -> list[int]:
    frame_count = round(design.duration_seconds * SAMPLE_RATE)
    samples = [0.0] * frame_count
    add_wooden_pop(
        samples,
        start_seconds=design.pop_start,
        frequency=design.pop_frequency,
        amplitude=design.pop_amplitude,
        seed=design.noise_seed,
    )
    add_tuned_bar(
        samples,
        start_seconds=design.high_start,
        frequency=design.high_frequency,
        amplitude=design.high_amplitude,
        decay_seconds=design.high_decay,
    )
    add_tuned_bar(
        samples,
        start_seconds=design.low_start,
        frequency=design.low_frequency,
        amplitude=design.low_amplitude,
        decay_seconds=design.low_decay,
        bend=0.012,
    )
    samples = remove_dc(samples)
    apply_edge_fades(samples)
    normalize(samples, design.target_peak_dbfs)
    return quantize(samples)


def render_playful_miss() -> list[int]:
    """Render a whiff, wooden pop, elastic recoil, and one tiny bounce."""
    frame_count = round(PLAYFUL_MISS_DURATION_SECONDS * SAMPLE_RATE)
    samples = [0.0] * frame_count
    add_band_limited_swoosh(
        samples,
        start_seconds=0.004,
        duration_seconds=0.086,
        amplitude=0.31,
        seed=407,
    )
    add_wooden_pop(
        samples,
        start_seconds=0.108,
        frequency=640.0,
        amplitude=0.45,
        seed=408,
    )
    add_elastic_glide(
        samples,
        start_seconds=0.168,
        duration_seconds=0.198,
        amplitude=0.39,
    )
    add_small_hop(
        samples,
        start_seconds=0.378,
        duration_seconds=0.094,
        amplitude=0.145,
    )
    samples = remove_dc(samples)
    apply_edge_fades(samples)
    normalize(samples, PLAYFUL_MISS_TARGET_PEAK_DBFS)
    return quantize(samples)


def write_wav(path: Path, pcm: list[int]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = struct.pack(f"<{len(pcm)}h", *pcm)
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(payload)


def parse_args() -> argparse.Namespace:
    repository_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=repository_root / "build" / "generated-sfx-review",
        help="Directory for all four review variants.",
    )
    parser.add_argument(
        "--production-file",
        type=Path,
        help=(
            "Write only the selected playful-miss design to this exact path "
            "as capture_failure.wav."
        ),
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.production_file is not None:
        pcm = render_playful_miss()
        write_wav(args.production_file, pcm)
        peak_dbfs, rms_dbfs = metrics(pcm)
        print(
            f"{args.production_file}: {len(pcm) / SAMPLE_RATE:.3f}s, "
            f"peak={peak_dbfs:.2f} dBFS, rms={rms_dbfs:.2f} dBFS"
        )
        return

    for design in DESIGNS:
        pcm = render(design)
        destination = args.output_dir / design.filename
        write_wav(destination, pcm)
        peak_dbfs, rms_dbfs = metrics(pcm)
        print(
            f"{destination}: {len(pcm) / SAMPLE_RATE:.3f}s, "
            f"peak={peak_dbfs:.2f} dBFS, rms={rms_dbfs:.2f} dBFS"
        )

    playful_pcm = render_playful_miss()
    playful_destination = args.output_dir / PLAYFUL_MISS_FILENAME
    write_wav(playful_destination, playful_pcm)
    peak_dbfs, rms_dbfs = metrics(playful_pcm)
    print(
        f"{playful_destination}: {len(playful_pcm) / SAMPLE_RATE:.3f}s, "
        f"peak={peak_dbfs:.2f} dBFS, rms={rms_dbfs:.2f} dBFS"
    )


if __name__ == "__main__":
    main()
