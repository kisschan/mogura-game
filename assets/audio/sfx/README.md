# Sound Effects

The UI sounds come from Kenney UI Audio:

- Source: https://kenney.nl/assets/ui-audio
- License: CC0 1.0 Universal
- Attribution: not required

The capture-failure sound is original, sample-free synthesis. The production
eat-recovery sound is composed from third-party samples from 効果音ラボ
(Sound Effect Lab); it is not CC0 or sample-free. A separate original synthesized
eat-recovery alternative is also retained.

## Files

- `button_press.ogg`: `Audio/click1.ogg`, used for button presses
- `tile_rotate.ogg`: `Audio/switch1.ogg`, used for tile rotation
- `capture_failure.wav`: original sample-free synthesis, used when a capture fails
- `eat_recovery.wav`: edited 効果音ラボ samples, used when food restores health

The same files are copied into `androidApp/src/main/res/raw/` for Android playback.

## Production eat-recovery sources and terms

- Provider: 効果音ラボ (Sound Effect Lab)
- Source / preview page: https://soundeffect-lab.info/sound/anime/
- Samples: 「食べ物をパクッ」 (`suck1.mp3`) and 「キラッ1」 (`kira1.mp3`)
- Terms: https://soundeffect-lab.info/agreement/
- Terms checked: 2026-09-03

The provider permits free commercial use and editing. Attribution, a link, and
usage reporting are optional. The samples remain under the provider's terms,
not the Kenney CC0 license above.

Standalone redistribution of the original or edited sound files is prohibited,
as are sampler-style apps and standalone audio showcases. The terms expressly
permit integration as application operation sounds, including when the audio
files are exposed. This project's production WAV is used only as an integrated
gameplay sound effect. Do not offer the samples or composed WAV as a standalone
sound library or audio demonstration. Do not hotlink the provider's audio files.

The downloaded originals are kept locally in the ignored `build/sfx-sources/`
directory and are not redistributed. Obtain them from the provider's source
page under its current terms before rebuilding.

## Rebuilding the production sounds

The capture-failure WAV is the `playful_miss` design: a light whiff, wooden pop,
elastic recoil, and a tiny bounce. Rebuild both identical copies with:

```powershell
python scripts/generate_capture_failure_sfx.py --production-file assets/audio/sfx/capture_failure.wav
python scripts/generate_capture_failure_sfx.py --production-file androidApp/src/main/res/raw/capture_failure.wav
```

For eat recovery, place the two source files at
`build/sfx-sources/suck1.mp3` and `build/sfx-sources/kira1.mp3`. The composer uses
Java with the existing `libs/jlayer-1.0.1.jar` to decode the MP3 files, followed by
Python's standard library for composition. Rebuild both identical production
copies with:

```powershell
python scripts/compose_eat_recovery_sfx.py
```

By default, this writes `assets/audio/sfx/eat_recovery.wav` and
`androidApp/src/main/res/raw/eat_recovery.wav`.

The production cue is 800 ms, mono, 44.1 kHz, 16-bit PCM:

- Bite: starts at 18 ms, peak normalized to -4 dBFS.
- Sparkle: starts at 448 ms (the first recovery icon's arrival), peak normalized
  to -10 dBFS so it stays quieter than the bite.
- MP3 padding and leading silence are removed. The sparkle is shortened to fit
  the animation and faded out over its final 80 ms to avoid an abrupt cutoff.
- Both platforms receive byte-identical files; the combined peak is -4 dBFS.

Original MP3 SHA-256 checksums for this build:

- `suck1.mp3`: `c7e09291b5685121896ef070b3525d117af45f668fd0e96aaafc48fcb1b3ad2e`
- `kira1.mp3`: `a3683b1268e2081e3837311194b1eefbe533a12a204aef9c47df90ed1575211c`

## Original synthesized eat-recovery alternative

`scripts/generate_eat_recovery_sfx.py` contains the original sample-free design.
It is an alternative, not the production eat-recovery rebuild command:

```powershell
python scripts/generate_eat_recovery_sfx.py
```

Its default output is `build/sfx-variants/eat_recovery_synthesized.wav`; it does
not replace the production files by default.
