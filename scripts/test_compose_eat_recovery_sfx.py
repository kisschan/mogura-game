"""Synthetic-only regression tests for the licensed-source SFX composer."""

from __future__ import annotations

import math
import struct
import tempfile
import unittest
import wave
from pathlib import Path

import compose_eat_recovery_sfx as composer


class ComposeEatRecoverySfxTest(unittest.TestCase):
    def setUp(self) -> None:
        temporary_root = composer.REPOSITORY_ROOT / "build"
        temporary_root.mkdir(parents=True, exist_ok=True)
        self.temporary_directory = tempfile.TemporaryDirectory(
            prefix="test-eat-compose-", dir=temporary_root
        )
        self.addCleanup(self.temporary_directory.cleanup)
        self.directory = Path(self.temporary_directory.name)

    def write_pcm(
        self,
        name: str,
        samples: list[int],
        *,
        channels: int = 1,
        sample_width: int = 2,
        sample_rate: int = composer.SAMPLE_RATE,
    ) -> Path:
        path = self.directory / name
        payload = (
            struct.pack(f"<{len(samples)}h", *samples)
            if sample_width == 2
            else bytes(samples)
        )
        with wave.open(str(path), "wb") as output:
            output.setnchannels(channels)
            output.setsampwidth(sample_width)
            output.setframerate(sample_rate)
            output.writeframes(payload)
        return path

    def test_render_has_exact_duration_onsets_and_silent_gap(self) -> None:
        source = [0.4] * composer.SAMPLE_RATE
        pcm = composer.render(source, source)
        bite_start = round(0.018 * composer.SAMPLE_RATE)
        bite_end = bite_start + round(0.280 * composer.SAMPLE_RATE)
        chime_start = round(0.448 * composer.SAMPLE_RATE)

        self.assertEqual(35_280, len(pcm))
        self.assertTrue(all(isinstance(sample, int) for sample in pcm))
        self.assertTrue(all(sample == 0 for sample in pcm[:bite_start]))
        self.assertEqual(0, pcm[bite_start], "the bite's first sample is faded in")
        self.assertTrue(any(pcm[bite_start:bite_end]))
        self.assertTrue(all(sample == 0 for sample in pcm[bite_end:chime_start]))
        self.assertGreater(chime_start - bite_end, round(0.140 * composer.SAMPLE_RATE))
        self.assertEqual(0, pcm[chime_start], "the chime must not jump at its onset")
        self.assertTrue(any(pcm[chime_start:]))
        self.assertEqual([0.4] * composer.SAMPLE_RATE, source, "render must not mutate source PCM")

    def test_chime_peak_is_six_db_quieter_than_bite(self) -> None:
        pcm = composer.render([0.05] * composer.SAMPLE_RATE, [0.9] * composer.SAMPLE_RATE)
        chime_start = round(composer.CHIME_START_SECONDS * composer.SAMPLE_RATE)
        bite_peak = max(map(abs, pcm[:chime_start]))
        chime_peak = max(map(abs, pcm[chime_start:]))
        bite_dbfs = 20 * math.log10(bite_peak / composer.PCM_MAX)
        chime_dbfs = 20 * math.log10(chime_peak / composer.PCM_MAX)

        self.assertAlmostEqual(-4.0, bite_dbfs, delta=0.001)
        self.assertAlmostEqual(-10.0, chime_dbfs, delta=0.001)
        self.assertAlmostEqual(-6.0, chime_dbfs - bite_dbfs, delta=0.001)
        self.assertLess(max(map(abs, pcm)), composer.PCM_MAX)

    def test_trim_removes_source_padding_but_retains_three_ms_at_each_edge(self) -> None:
        padding = round(0.003 * composer.SAMPLE_RATE)
        body = [0.5, -0.25, 0.001] * 80
        samples = [0.0] * 1000 + body + [0.0] * 800

        trimmed = composer.trim_silence(samples)

        self.assertEqual([0.0] * padding + body + [0.0] * padding, trimmed)
        self.assertEqual(1000 + len(body) + 800, len(samples))

    def test_clip_endpoint_fades_are_zero_and_bounded_before_pcm_conversion(self) -> None:
        for peak_dbfs, fade_out_seconds in ((-4.0, 0.018), (-10.0, 0.080)):
            with self.subTest(peak_dbfs=peak_dbfs):
                clip = composer.prepare_clip(
                    [0.9] * composer.SAMPLE_RATE,
                    max_seconds=0.280,
                    peak_dbfs=peak_dbfs,
                    fade_out_seconds=fade_out_seconds,
                )
                expected_peak = 10 ** (peak_dbfs / 20)
                fade_in_samples = round(0.002 * composer.SAMPLE_RATE)
                self.assertEqual(round(0.280 * composer.SAMPLE_RATE), len(clip))
                self.assertEqual(0.0, clip[0])
                self.assertEqual(0.0, clip[-1])
                self.assertAlmostEqual(expected_peak, max(clip), places=12)
                self.assertLessEqual(abs(clip[1] - clip[0]), expected_peak / (fade_in_samples - 1) + 1e-12)
                self.assertLess(abs(clip[-1] - clip[-2]), expected_peak * 0.001)
        pcm = composer.render([0.9] * composer.SAMPLE_RATE, [0.9] * composer.SAMPLE_RATE)
        self.assertEqual(0, pcm[0])
        self.assertEqual(0, pcm[-1])
        self.assertTrue(all(-32768 < sample < 32767 for sample in pcm))

    def test_read_mono_pcm_preserves_supported_signed_16_bit_samples(self) -> None:
        path = self.write_pcm("mono.wav", [-32768, -16384, 0, 16384, 32767])
        self.assertEqual(
            [-1.0, -0.5, 0.0, 0.5, 32767 / 32768],
            composer.read_mono_pcm(path),
        )

    def test_read_mono_pcm_downmixes_stereo_by_averaging_each_frame(self) -> None:
        path = self.write_pcm(
            "stereo.wav", [16384, 0, -16384, -16384, 32767, -32768], channels=2
        )
        self.assertEqual([0.25, -0.5, -1 / 65536], composer.read_mono_pcm(path))

    def test_silent_or_phase_cancelled_input_is_rejected_before_rendering(self) -> None:
        paths = (
            self.write_pcm("silent.wav", [0] * 512),
            self.write_pcm("cancelled.wav", [16384, -16384] * 256, channels=2),
        )
        for path in paths:
            with self.subTest(path=path.name):
                silent = composer.read_mono_pcm(path)
                self.assertTrue(all(sample == 0.0 for sample in silent))
                with self.assertRaisesRegex(ValueError, "silent"):
                    composer.trim_silence(silent)
                with self.assertRaisesRegex(ValueError, "silent"):
                    composer.render(silent, [0.4] * 512)
                with self.assertRaisesRegex(ValueError, "silent"):
                    composer.render([0.4] * 512, silent)

    def test_reader_rejects_unsupported_pcm_formats_and_empty_audio(self) -> None:
        cases = (
            ("rate.wav", [1000] * 10, {"sample_rate": 48000}),
            ("width.wav", [128] * 10, {"sample_width": 1}),
            ("channels.wav", [1000] * 12, {"channels": 3}),
            ("empty.wav", [], {}),
        )
        for name, samples, options in cases:
            with self.subTest(name=name):
                path = self.write_pcm(name, samples, **options)
                with self.assertRaises(ValueError):
                    composer.read_mono_pcm(path)

    def test_trim_rejects_non_finite_or_too_short_sources(self) -> None:
        for value in (math.nan, math.inf, -math.inf):
            with self.subTest(value=value):
                with self.assertRaisesRegex(ValueError, "non-finite"):
                    composer.trim_silence([0.25, value, 0.25])
        with self.assertRaisesRegex(ValueError, "too short"):
            composer.prepare_clip([0.25], max_seconds=0.280, peak_dbfs=-4.0, fade_out_seconds=0.018)


if __name__ == "__main__":
    unittest.main()
