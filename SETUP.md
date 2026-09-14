# Twinster — Setup Guide

Twinster builds your Music Personality card from two supported sources: your phone's own local
music library (tag-based scanning plus on-device audio content detection — no account, no server,
no network call for the scan itself) and a built-in Demo mode for browsing the app instantly. There
is no online-streaming-account integration of any kind.

## 0. Just want to see the app right now? Tap "Try Demo"

On the connect screen, tap **Try Demo**. This loads a hardcoded, realistic music profile (top
artists/tracks, genre breakdown, decade split, obscurity score, mood, archetype) straight into the
same app state a real Local Library scan would fill — no setup, no network calls. Every screen
(cards, sharing, Taste Twin) is fully browsable immediately.

Taste Twin also works in demo mode: open **Taste Twin** and tap **Compare with Demo Friend** to see
a full compatibility result against a second synthetic profile, without needing a second device or
a real friend's link.

## 0.3. Local Library — scan your downloaded music, no account needed

Tap **Scan Local Library** on the connect screen to build a profile from audio files already on your
phone (downloaded MP3s, etc.) — no login, no network calls. The app asks for the
`READ_MEDIA_AUDIO` permission (or `READ_EXTERNAL_STORAGE` on Android 12 and below) with a short
explanation first; if you deny it, Twinster shows a message with a button straight to this app's
Android settings page instead of nagging you again. The scan uses `MediaStore.Audio.Media`, the
standard modern API for reading on-device media.

Most local files don't carry reliable genre tags, so on top of the file's own tag (and the
`MediaMetadataRetriever` fallback for missing tags), Twinster also runs real **on-device audio
content analysis** to detect genre directly from the music itself — not just trust whatever tag
happens to be in the file:

- A bundled copy of Google's **YAMNet** model (`assets/yamnet.tflite`, ~4MB, Apache-licensed, trained
  on AudioSet) runs locally via TensorFlow Lite's Task Library (`org.tensorflow:tensorflow-lite-task-audio`)
  — fully offline, no API key, no account, no network call, and no ongoing cost, unlike commercial
  audio-fingerprinting services.
- For each analyzed track, a ~12-second clip from roughly a third of the way into the file is decoded
  to 16kHz mono PCM (`MediaExtractor`/`MediaCodec`, see `AudioContentDecoder.kt`) and classified.
  YAMNet's 521 AudioSet output classes are filtered down to a clean genre-relevant allowlist (its
  "Music genre" branch — Rock music, Reggae, Techno, Jazz, etc. — plus a handful of unambiguous
  instrument classes like saxophone→jazz or steel guitar→country) via `AudioGenreTaxonomy.kt`;
  irrelevant AudioSet classes (Speech, Vehicle, Dog, ...) never surface as a "genre."
- This content-detected genre is **additive**: a file's own tag stays authoritative when present:
  content analysis fills the gap when a tag is missing, and also supplements tagged tracks with
  extra detected genre signal. Nothing from the existing tag-reading logic was removed.
- **Performance cap**: decoding + running inference per track is much slower than reading a tag, so
  content analysis runs on up to **150 tracks per scan** (tracks missing a genre tag are prioritized,
  since that's where it adds the most value; any remaining slots go to the most recently added tagged
  tracks). On a library larger than that, every track still gets the full tag-based scan (titles,
  artists, counts) — only the deeper audio analysis is sampled, and the Genre Breakdown card says so
  explicitly ("Includes on-device audio analysis of a 150-track sample...") rather than silently
  implying full coverage. The scan screen shows real progress ("Analyzing 42 of 150 tracks…") instead
  of a generic spinner while this runs. A corrupt file, unsupported codec, or decode failure just
  skips that one track's content analysis — it never aborts the whole scan.

If a track has embedded album art, that's used as its artwork; otherwise it falls back to the usual
music-note placeholder tile.

A completed scan lands on the exact same swipeable card pager (Top Picks, Genre, Decade, Obscurity,
Mood, Archetype) that Demo profiles use — not a separate stripped-down screen. The scan is also
cached on-device, so you don't have to rescan every time: a **My Library** button appears on the
connect screen (alongside Try Demo / Scan Local Library) whenever a previous scan exists, and
tapping it reopens that same card view instantly, with no rescan and no detour through the connect
flow. Running **Scan Local Library** again always overwrites the cached result with a fresh scan.

**Obscurity Score for Local Library**: local files carry no first-party popularity number of their
own, so this score is *estimated* rather than measured directly — from a Genius.com
mainstream-footprint signal (see step 1.5 below). If that signal isn't available (no Genius token
configured, or no matches found for these artists), the card honestly shows "not available" instead
of a fake number. Every Obscurity Score card also carries a small disclaimer — it's a fun,
illustrative estimate, not a precise ranking.

## 0.4. Removed: "Detect Now Playing" notification-listener feature

This app previously included a "Detect Now Playing" feature that used a
`NotificationListenerService` (with `BIND_NOTIFICATION_LISTENER_SERVICE`) and
`MediaSessionManager` to notice music playing in other apps. It has been removed entirely because
Google Play Protect flags `BIND_NOTIFICATION_LISTENER_SERVICE` as a high-risk permission strongly
associated with spyware/stalkerware, and hard-blocks sideloaded/signed APKs that declare it when
the app has no Play Store distribution history — regardless of proper release signing. It could be
reintroduced later once the app is properly distributed through Play Console and has an established
reputation there.

## 1. (Optional) Genius API token — powers Local Library obscurity estimates

Local Library obscurity scoring uses Genius.com as its "how mainstream is this artist" signal.
This is entirely optional — without it, the Obscurity Score card just honestly shows "not
available" for Local Library profiles.

1. Go to https://genius.com/api-clients and log in (a free Genius account is fine).
2. Click **New API Client**, fill in any app name/URL, and save.
3. Copy the **Client Access Token** shown on the client's page.
4. In `local.properties`, replace:
   ```
   GENIUS_ACCESS_TOKEN=YOUR_GENIUS_ACCESS_TOKEN_HERE
   ```
   with:
   ```
   GENIUS_ACCESS_TOKEN=<the client access token you copied>
   ```
5. Rebuild the app. If you skip this, Local Library obscurity just shows "not available" —
   nothing breaks or crashes without a Genius token.

## 2. (Optional) TheAudioDb API key — powers the Local Library internet genre fallback

For Local Library artists where neither file tags nor on-device audio analysis found a genre,
the app makes one last-resort lookup against TheAudioDb (theaudiodb.com), a free music metadata
database that explicitly permits commercial use (unlike MusicBrainz, which restricts its free API
to non-commercial use — not a fit since this app is ad-monetized).

This works out of the box with no setup: it defaults to TheAudioDb's own documented shared "123"
test key. That's fine for development and light use, but before any real public release, get your
own personal key for reliability/rate limits:

1. Go to https://www.theaudiodb.com/, log in, and support the project at their Patreon tier
   (~$8/month) to receive a personal API key by email.
2. In `local.properties`, add:
   ```
   THEAUDIODB_API_KEY=<your personal key>
   ```
3. Rebuild the app. If you skip this, the shared "123" key keeps working — just at its shared,
   lower-priority rate limit.

## 4. (Optional) Instagram Stories sharing

Sharing a card directly to Instagram Stories needs a Meta/Facebook App ID registered by you:

1. Go to https://developers.facebook.com/apps and create an app, note its **App ID**.
2. In `local.properties`, replace:
   ```
   FACEBOOK_APP_ID=YOUR_FACEBOOK_APP_ID_HERE
   ```
   with your real numeric App ID, then rebuild.

If you skip this, the app still works — sharing falls back to the normal Android share sheet, or shows "Instagram not available" if Instagram isn't installed.

## 5. (Optional) Sharper AI-written archetype/verdict lines

In the app's **Settings** screen you can paste your own Anthropic API key (get one at https://console.anthropic.com). This is stored encrypted on your device only and is never sent anywhere except directly to Anthropic's API when generating a line. Leave it blank and the app still works — it uses a built-in rule-based archetype instead.

## 6. Installing the debug APK on your Android phone

The built file is at:
```
app\build\outputs\apk\debug\app-debug.apk
```

To install it:

1. Copy `app-debug.apk` to your phone (USB cable, Google Drive, email to yourself, etc.).
2. On your phone, open the file from wherever you saved it (Files app / Downloads).
3. Android will ask to allow installing from this source the first time — allow it.
4. Tap **Install**.
5. Open **Twinster** from your app drawer.

No Play Store account or developer account is needed to sideload a debug APK this way.

## 7. Release signing — fixes the Play Protect "app blocked" screen

### Why this exists

Sideloading the plain debug APK (`app-debug.apk`) triggers Google Play Protect's hard
**"app blocked"** screen on many devices. This isn't a Twinster-specific bug — Play Protect is
much more aggressive about unsigned/debug-signed APKs that request sensitive permissions, and
this app asks for media/storage access to scan your local music library, which raises its risk score.
The real fix is a properly signed **release** build instead of a debug one. A release build is also
a hard requirement for ever uploading to Google Play Console in the future (not done yet — no
developer account exists — but the build is now ready for it).

To get there, the project now has:

- A real release keystore (`keystore/twinster-release.jks`) generated with `keytool`
  (RSA 2048, alias `twinster`, valid ~30 years — well past Play's Oct 22 2033 minimum).
- `keystore.properties` at the project root holding the keystore path and passwords, loaded by
  `app/build.gradle.kts` at build time. **This file is not committed to git** (see `.gitignore`) —
  it contains real secrets.
- A `signingConfigs { release { ... } }` block wired to `buildTypes.release`, plus
  `isMinifyEnabled = true` and `isShrinkResources = true` (R8 code shrinking/obfuscation and
  unused-resource removal), with matching ProGuard/R8 keep rules in `app/proguard-rules.pro` for
  Retrofit, kotlinx.serialization, androidx.security-crypto (Tink), Coil, and ZXing so shrinking
  doesn't break anything at runtime. If `keystore.properties` is ever missing (e.g. a fresh
  checkout before generating a keystore), the release build type simply skips attaching a signing
  config rather than failing — `assembleDebug` is unaffected either way.

### ⚠️ CRITICAL — back up the keystore now, before you lose it

```
keystore/twinster-release.jks
keystore.properties
```

**These two files are the only thing that can ever sign an update to this app once it's installed
by real users or published to Google Play.** This is not a Twinster-specific limitation — it's a
hard, unrecoverable constraint of how Android and the Play Store work: every future version of the
app must be signed with the *same* key as the first one, or Android will refuse to install it as
an "update" (it would be treated as a completely different app, wiping existing users' data). If
this keystore or its passwords are lost, **there is no recovery path** — not from Google, not from
anyone. The app's identity would be permanently dead for anyone who already has it installed.

**Copy both files to at least one other safe location right now** — a password manager (as file
attachments, or note the passwords in a secure note plus the file separately), encrypted cloud
storage, an external drive, or all of the above. Treat this like a wallet's private key, not like a
regular project file.

The keystore's passwords are recorded wherever this setup session's output was saved (they are
intentionally **not** duplicated again in this document, since `SETUP.md` is far more likely to be
casually shared, screenshotted, or committed to a public repo than a password manager entry is).
`keystore.properties` in the project root holds the working copy Gradle actually reads.

## 8. Pulling this project onto another machine (from GitHub)

This repo intentionally does **not** contain `local.properties` or the release keystore/
`keystore.properties` — both are gitignored because they hold real secrets and signing material.
After cloning on a new machine, `assembleDebug` will run (it works without either file), but to get
a fully working build with obscurity/genre lookups and a release build, you'll need to bring three
things over yourself, none of them via git:

1. **`local.properties`** — create this file at the project root with:
   ```
   sdk.dir=<path to that machine's Android SDK>
   GENIUS_ACCESS_TOKEN=<your token from genius.com/api-clients>
   THEAUDIODB_API_KEY=<your key, or the shared test key "123" for quick testing>
   FACEBOOK_APP_ID=<optional, only for Instagram Stories attribution>
   ```
   Reuse the same `GENIUS_ACCESS_TOKEN`/`THEAUDIODB_API_KEY` values already in use elsewhere — these
   are per-account API credentials, not per-machine, so there's nothing new to generate.
2. **The release keystore** — copy `keystore/twinster-release.jks` and `keystore.properties` from
   wherever you backed them up (see §7 above) into the same paths in the new checkout. Skip this if
   you only need a debug build on this machine.
3. **The Android SDK/JDK toolchain itself** — this isn't part of the repo at all; install Android
   Studio (or just the command-line SDK tools) and a JDK 17 on the new machine the normal way, same
   as any fresh Android dev setup.

Once those are in place, `./gradlew assembleDebug` (or `assembleRelease` once the keystore is copied
over) works exactly the same as on the original machine.

### Building and installing the release APK

```
.\gradlew.bat assembleRelease
```

produces a signed, minified, shrunk APK at:

```
app\build\outputs\apk\release\app-release.apk
```

Install it exactly the same way as the old debug APK (copy to phone, open it, allow install from
this source, tap Install). The hard Play Protect **block** seen with the debug build should no
longer occur. You may still see the normal, much milder "Play Protect doesn't recognize this
developer" or "install unknown apps" style warnings/friction — that's expected and unavoidable for
*any* app installed outside the Play Store (Play builds its trust reputation from install volume
over time), and is a completely different, non-blocking thing from the hard block this fixes.

### The `.aab` — not for sideloading

```
.\gradlew.bat bundleRelease
```

produces a signed Android App Bundle at:

```
app\build\outputs\bundle\release\app-release.aab
```

This `.aab` file is the format Google Play Console requires for upload whenever a Play developer
account eventually exists — it will **not** install directly on a device via sideloading (Android
has no installer for `.aab`, only Play's own backend does). It's built and signed now so it's ready
to go the moment there's an account to upload it to; no action needed on it until then.
