# Kosha for Android

The native Android app for [Kosha](https://getkosha.app), the personal finance tracker. Jot down what you spent, and see how the month compares with a usual one. It signs in to getkosha.app or your own Kosha server, and is written in Kotlin and Jetpack Compose.

The server, the money rules and every string live in the web repo, [get-kosha/web](https://github.com/get-kosha/web). The iOS app is [get-kosha/ios](https://github.com/get-kosha/ios). It was built from the same brief and behaves the same.

## What it does

- **Every screen of the web app, made for a phone:** Overview, Activity, Add, Recurring, Accounts, Reconcile, Import, Ask Kosha, Settings and the welcome flow.
- **Add in a few taps.** The amount comes first on Kosha's own calculator keypad. Then pick the category, date and account, add a note and files, and set Repeat to make it a recurring payment. Transfers show the provider's fee against the market rate.
- **Offline.** Screens open from what the app saved last time. New entries wait on the phone and are sent when the connection returns. Each entry has its own id, so a retry saves it only once.
- **Sign-in through the browser.** OAuth with PKCE in an Auth Tab or Custom Tab, on the server's HTTPS app callback. Passwords, passkeys, Google and two-factor stay on the website. Tokens are kept in Keystore-backed storage.
- **App lock** with fingerprint, face or the screen lock, and the app hidden in recents.
- **On the home screen:** a quick-add widget and app shortcuts.
- **Statements from other apps.** Share one or more CSV, OFX, QFX or PDF statements to Kosha to reconcile or import them.
- **Optional AI**, with the key you connect in Settings › AI: Describe it, the receipt camera, auto-categorise, the monthly recap and Ask Kosha. When AI is off, these don't appear.
- **12 languages**, with a per-app language setting. Light and dark, five accents, a neutral mode and a choice of number font.
- **The demo account** is read-only, as on the web.

## Build and run

You need Android Studio, or JDK 21 and the Android SDK. The app supports Android 9 (API 28) and later.

```sh
./gradlew :app:installDebug    # build and install on a connected device or emulator
./gradlew test lint            # unit tests and Android lint; run before every change
```

To use a Kosha server on your computer, run `npm run dev` in the web repo, then `adb reverse tcp:3000 tcp:3000`. In the app, choose **Use your own server** and enter `http://localhost:3000`. Plain HTTP works only for `localhost`, `127.0.0.1` and the emulator's `10.0.2.2`. Sign in as the demo account, `demo@kosha.local` with the password `kosha-demo-2026`. Never use real account data in tests or screenshots.

The debug build adds a second launcher entry, **Inputs kit**. It shows every control in `core:design`, with switches for theme, accent and number font.

### Strings

Every string comes from the web repo's `messages/<locale>/*.json`. Never hard-code text. After changing messages there, rebuild `strings.xml` in all 12 languages:

```sh
python3 scripts/sync-strings.py ../kosha-web
```

Add strings that only the phone apps show to `messages/<locale>/app.json` (or an `app-<feature>.json`) in the web repo, in every locale.

### Screenshot tour

`TourTest` signs in to a dev server as the demo account. It opens every main screen in light and dark and saves a screenshot of each:

```sh
adb reverse tcp:3000 tcp:3000
./gradlew :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
adb pull /sdcard/Android/data/app.getkosha/files/tour
```

### Designs

Each screen has a design board. They ship as `kosha-mobile-design.zip`: unzip it at the repo root to get `design/` (git-ignored), which holds:

- `design/screens/light/*.png`: one picture per screen
- `design/source/k.css`: every token and size
- `design/source/*.dc.html`: each screen's markup

The build doesn't need them. They are for comparing your work.

## Project layout

| Module | Contents |
| --- | --- |
| `app` | Activity, navigation, tab bar, app lock, the widget and shortcuts |
| `core:model` | Pure Kotlin: API models, money, the keypad, quick add and the statement helpers, with JVM unit tests |
| `core:network` | The `/api/v1` and `/api/app` client, sign-in, token storage and refresh |
| `core:data` | Cache-first loads, the offline outbox (WorkManager) and preferences |
| `core:design` | `KoshaTheme`, Geist fonts, Phosphor icons, generated strings and every input component |
| `core:ui` | The header, banners, and the month and currency shared across screens |
| `feature:*` | Screens: `auth`, `add`, `overview`, `activity`, `recurring`, `accounts`, `statements`, `settings`, `welcome` |

`CLAUDE.md` lists the rules the code depends on. `HANDOFF.md` is the full brief: every screen, the server work and the design system. `PLAN.md` records the decisions, the conflicts with the web code (the web wins), and what is still open.

## Release

The release build is minified with R8. It is signed with the Play upload key from `keystore.properties` (git-ignored) when that file exists:

```properties
storeFile=…
storePassword=…
keyAlias=…
keyPassword=…
```

```sh
./gradlew :app:bundleRelease    # app/build/outputs/bundle/release/app-release.aab
```

Every push to `main` runs `.github/workflows/play-internal.yml`. It runs the tests and lint, builds and signs the bundle, and uploads it to Play's internal testing track. It needs these repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `PLAY_SERVICE_ACCOUNT_JSON`

The store listing is in `fastlane/metadata/android/<locale>/`: text, screenshots and feature graphics in 12 languages. `fastlane/PLAY_CONSOLE.md` is the runbook for setting it up in Play Console.

For sign-in to open the app straight from getkosha.app, the server must list the app's signing certificates in `ANDROID_CERT_SHA256`. Use the upload key and Play app signing key fingerprints, as Play Console shows them under App signing. Without them, sign-in still works, but the browser shows an **Open Kosha** button first.

## Commits

[Conventional Commits](https://www.conventionalcommits.org) (`type(scope): subject`, header 100 characters or fewer), the same as the web repo.
