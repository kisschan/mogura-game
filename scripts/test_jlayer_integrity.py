"""Integrity checks use synthetic fixtures and never start Java or decode audio."""

from __future__ import annotations

import hashlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import compose_eat_recovery_sfx as composer


class JLayerIntegrityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory(
            prefix="test-jlayer-", dir=Path(__file__).resolve().parent
        )
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        self.jar = self.root / "libs/jlayer-1.0.1.jar"
        self.jar.parent.mkdir()
        self.original = b"synthetic public JAR fixture, never executed"
        self.jar.write_bytes(self.original)
        self.source = self.root / "source.mp3"
        self.destination = self.root / "decoded.wav"
        self.destination.write_bytes(b"synthetic decoded result")
        self.root_patch = patch.object(composer, "REPOSITORY_ROOT", self.root)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)

    def test_verified_fixture_can_reach_the_mocked_decoder(self) -> None:
        checksum = hashlib.sha256(self.original).hexdigest()
        with patch.object(composer, "JLAYER_SHA256", checksum), patch.object(
            composer.subprocess, "run", return_value=Mock(returncode=0, stderr="")
        ) as run:
            composer.decode_mp3(self.source, self.destination)

        run.assert_called_once()
        self.assertIn(str(self.jar), run.call_args.args[0])

    def test_modified_jar_stops_before_starting_java(self) -> None:
        checksum = hashlib.sha256(self.original).hexdigest()
        self.jar.write_bytes(self.original + b" changed")
        with patch.object(composer, "JLAYER_SHA256", checksum), patch.object(
            composer.subprocess, "run"
        ) as run:
            with self.assertRaisesRegex(ValueError, "SHA-256"):
                composer.decode_mp3(self.source, self.destination)
        run.assert_not_called()

    def test_missing_jar_stops_before_starting_java(self) -> None:
        self.jar.unlink()
        with patch.object(composer.subprocess, "run") as run:
            with self.assertRaises(FileNotFoundError):
                composer.decode_mp3(self.source, self.destination)
        run.assert_not_called()

    def test_linked_jar_is_rejected_without_reading_it_or_starting_java(self) -> None:
        with patch.object(Path, "is_symlink", return_value=True), patch.object(
            Path, "read_bytes"
        ) as read_bytes, patch.object(composer.subprocess, "run") as run:
            with self.assertRaisesRegex(ValueError, "link"):
                composer.decode_mp3(self.source, self.destination)
        read_bytes.assert_not_called()
        run.assert_not_called()


if __name__ == "__main__":
    unittest.main()
