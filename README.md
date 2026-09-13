# Twinster — Find Your Musical Twin

A native Android app that builds your "Music Personality" straight from the audio files already on your phone — no account, no server, nothing uploaded.

## What it does

- **Scan Local Library** — reads your downloaded songs via Android's MediaStore, using file tags plus on-device audio content analysis (Google's YAMNet model, running fully offline) to fill in genre data tags don't have, with a free internet lookup (TheAudioDB) as a last-resort fallback for genre-less artists.
- **Try Demo** — see the full app instantly with synthetic sample data, no files or permissions required.
- **Music Personality cards** — swipeable cards for Top Picks (songs/artists/genres/playlists), Genre Breakdown, Decade Split, Obscurity Score (blended from Genius mainstream-footprint data), Mood Profile, and an auto-generated Archetype.
- **Taste Twin** — compare taste with a friend with zero backend: your profile compresses into a QR code / deep link, their device decodes and compares it locally.
- **Sharing** — export your card to Instagram Stories, the generic Android share sheet, or copy a short text summary.
- **BYOK** — optionally connect your own Anthropic/OpenAI/Gemini key in Settings for sharper, funnier archetype and Taste Twin verdict writing; falls back to a solid rule-based version otherwise.

## Stack

Kotlin + Jetpack Compose, dark glassmorphic UI with a per-profile animated color wash, Retrofit for Genius/TheAudioDB, on-device TensorFlow Lite (YAMNet) for audio genre detection running in an isolated process, kotlinx.serialization, DataStore for local persistence.

## Building

```
./gradlew assembleRelease
```

Requires a `local.properties` with:
```
sdk.dir=<path to your Android SDK>
GENIUS_ACCESS_TOKEN=<free token from genius.com/api-clients>
FACEBOOK_APP_ID=<optional, only needed for Instagram Stories attribution>
```

and a `keystore.properties` + release keystore for a signed build (see `SETUP.md` for full setup steps). Neither file is committed — both are gitignored since they hold secrets/signing material.

## Non-goals

No searching or viewing anyone else's Spotify/streaming data, no Instagram login integration, no auto-posting without the user tapping Share themselves. See `SETUP.md` for the full list of what's deliberately not built and why.
