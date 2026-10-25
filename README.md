# twitch-lite-for-android-tv

A lightweight Twitch client for Android TV boxes (developed on an X96). It was made to
reduce stutters, show BTTV / 7TV / FFZ emotes in chat, and keep the UI simple enough to use with a remote.

## Features

- Browse top live streams, followed channels and search (D-pad friendly grid UI)
- HLS playback with ExoPlayer (Media3); the master playlist is rewritten to pick the best quality
- Ad segments are stripped from HLS playlists using the Streamlink approach (`stitched-ad` date ranges)
- Read-only live chat over Twitch IRC WebSocket (anonymous `justinfan` login)
- Emotes from BTTV, FrankerFaceZ and 7TV (global and per-channel)
- Optional sign-in through Twitch Device Code flow (enter the code on your phone) to see followed channels
- DNS pinning for emote CDNs, for networks where the ISP DNS returns a sinkhole address

## Requirements

- Android 5.0+ (minSdk 21), landscape; touchscreen and Leanback are optional
- To build: JDK 17 and the Android SDK (platform 34)

## Build

```bash
# point Gradle at your Android SDK once
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. Install it on a device with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## CI

`.github/workflows/android.yml` runs on every push to `main` and on pull requests: lint, debug
and release (unsigned) builds, with the APKs uploaded as workflow artifacts. Pushing a tag
such as `v0.3.1` also attaches the APKs to a GitHub release.

## Project layout

| Path | Purpose |
| --- | --- |
| `app/src/main/java/app/litetwitch/tv/` | Activities, GQL client, HLS rewriting, chat, emotes, auth |
| `app/src/main/res/` | Layouts, colours, strings, icon |

Key classes: `Gql` (Twitch GraphQL), `HlsRewrite` + `RewriteDataSource` (playlist selection and ad removal),
`ChatManager` (IRC chat), `Emotes` (BTTV/FFZ/7TV), `DeviceAuth` (device-code login), `NetDns` (pinned DNS).

## Notes

- The app uses Twitch's public web client id and an unofficial GQL endpoint; it is not affiliated with
  Twitch and may break if Twitch changes these.
- Emote handling is ported from [bttv-android](https://github.com/night/betterttv) (MIT).

## License

MIT, see [LICENSE](LICENSE).
