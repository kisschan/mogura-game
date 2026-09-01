# Sound Effects

The UI sounds come from Kenney UI Audio:

- Source: https://kenney.nl/assets/ui-audio
- License: CC0 1.0 Universal
- Attribution: not required

The capture-failure sound is procedurally synthesized for this project and
contains no third-party samples. Its complete source is the generator in
`scripts/generate_capture_failure_sfx.py`.

## Files

- `button_press.ogg`: `Audio/click1.ogg`, used for button presses
- `tile_rotate.ogg`: `Audio/switch1.ogg`, used for tile rotation
- `capture_failure.wav`: original sample-free synthesis, used when a capture fails

The same files are copied into `androidApp/src/main/res/raw/` for Android playback.

## Rebuilding the generated sound

The production WAV is the `playful_miss` design: a light whiff, wooden pop,
elastic recoil, and a tiny bounce. Rebuild both identical copies with:

```powershell
python scripts/generate_capture_failure_sfx.py --production-file assets/audio/sfx/capture_failure.wav
python scripts/generate_capture_failure_sfx.py --production-file androidApp/src/main/res/raw/capture_failure.wav
```
