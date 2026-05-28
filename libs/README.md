# Local JVM Libraries

## jlayer-1.0.1.jar

- Purpose: MP3 playback inside the Swing/JVM app without launching an external media player.
- Source: Maven Central, `javazoom:jlayer:1.0.1`
- SHA-256: `850508C837454A1B06017C32A36876FAE516DE1E89A829F725FEE1E6DCC52000`

Android builds should provide their own `BackgroundMusicPlayer` implementation backed by `android.media.MediaPlayer` instead of using this desktop playback adapter.
