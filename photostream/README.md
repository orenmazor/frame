# PhotoStream

Tiny Android photo-frame app for Android 6+.

It reads photos recursively from:

```text
/storage/emulated/0/Pictures/Frame
```

Supported file extensions: `jpg`, `jpeg`, `png`, `webp`, `bmp`, `gif`.

Behavior:

- fullscreen immersive display
- keeps screen on
- shuffled slideshow
- 10 second slide interval
- crossfade transition
- center-crop scaling
- runtime storage permission request for Android 6+

Build/install from the repo root:

```sh
task build-photostream
task install-photostream
task run-photostream
```

Or with Gradle directly:

```sh
cd photostream
./gradlew :app:assembleDebug
cd ..
adb install -r photostream/app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.earendilworks.photostream 1
```
