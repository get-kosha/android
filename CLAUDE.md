# Kosha for Android

Native Android app for Kosha, the personal finance tracker at https://getkosha.app. Kotlin and Jetpack Compose. Wear OS comes later in this repo.

Start with `HANDOFF.md`: it is the full brief (what to build, the server work, every screen, the design system, the build order). This file holds the rules that stay true while building.

## Sources of truth

- **Behaviour and money rules:** the web app at `../kosha` (`CLAUDE.md`, `PRODUCT.md`, `DESIGN.md`). The public API contract is `../kosha/src/lib/api-contract.ts`; the server is `../kosha/src/server/`.
- **Layout:** `design/screens/light/*.png` (2×), one per screen, named `<page>-<Board>.png`.
- **Exact values:** `design/source/k.css` (tokens, sizes, radii) and `design/source/<Board>.dc.html` (each screen's markup and copy).
- **Words:** `../kosha/messages/<locale>/*.json` and `../kosha/translations.md`. Strings are generated from them; never hard-code text.

## Rules

- Money is integer minor units with the sign as the direction. No floating point for money anywhere.
- Days (`YYYY-MM-DD`) and months (`YYYY-MM`) are in the user's time zone from settings, not the phone's.
- Neutral categories (corrections, transfers) are neither income nor spending. Transfers are created through the transfer operations.
- Take month figures and net worth from the server's reports; don't re-derive totals on the phone.
- Kosha draws its own inputs: no Material date, time or number pickers, no system keyboard for amounts.
- Every colour, size and type style comes from `KoshaTheme`; light, dark, neutral mode and all five accents must work.
- AI features render nothing when `meta.ai` says they are off.
- The demo account is read-only.
- Only new transactions queue offline, each with a client id so a retry is saved once.
- Tokens live in Keystore-backed storage. Refreshes are serialised; refresh tokens rotate.
- Never use real account data in tests or screenshots. Use the demo account.
- Touch targets are at least 48dp. Icon-only buttons have content descriptions.

## Checking your work

Before you finish a change, run `./gradlew test lint` (and ktlint/detekt once configured), and compare the screens you touched with their PNGs in light and dark.

## Commits

Conventional Commits (`type(scope): subject`, header 100 characters or fewer), the same as the web repo.
