# Kosha for Android: build plan

The milestones from `HANDOFF.md` §9 broken into tasks, the decisions taken, and every gap or conflict found between the brief, the designs and the web code. When the brief and the web code disagree on money, the web code wins; each such case is listed under "Conflicts".

## Decisions

Defaults for `HANDOFF.md` §10, unless the owner says otherwise:

1. **App-only endpoints** (`/api/app`) for AI provider keys, API keys, export and deleting all transactions. Password, passkeys, Google, two-factor, signed-in devices and deleting the account open getkosha.app in a Custom Tab.
2. **Package id** `app.getkosha`.
3. **minSdk 28**, targetSdk latest stable.
4. **Play Store first.** F-Droid is open (the code is MIT); decide before M10.
5. **Wear OS** comes after M10 and is not planned here.

Decided while doing M0:

- **Every server knows the apps.** Migration `0009_app_clients.sql` registers `kosha-android` and `kosha-ios` on every Kosha server, self-hosted ones too. So the app never uses dynamic client registration: "Use your own server" checks `/.well-known/oauth-authorization-server`, then `/api/app/meta`. If `meta` is missing or its `version` is too old, the app says the server needs updating. This replaces §3.2's "register itself on first sign-in".
- **Token audience.** The app asks for `resource=<server>/api/v1`. `/api/v1` accepts tokens for `/api/v1` or `/mcp`. `/api/app` accepts only `/api/v1` tokens whose `azp` is one of the app clients.
- **Sign out** revokes the refresh token at `POST /api/auth/oauth2/revoke` (`token`, `token_type_hint=refresh_token`, `client_id`). No new endpoint was needed. An app token keeps working only while that user has a live refresh token for the client.
- **Demo.** The app opens authorize with `login_hint=demo&prompt=login`, and `/login` opens on the demo account. Demo tokens are read-only whatever they ask for.

## Milestones

Done means what `CLAUDE.md` and the brief say. Each screen matches its PNG in light and dark, and works in neutral mode and every accent. Its strings come from the message files, it has TalkBack labels, and it reads from the cache when offline. It is read-only for the demo and shows no trace of AI when AI is off. It holds up at 360dp and the largest font size.

### M0 · Server prep (web repo, branch `feat/mobile-app-api`): done, awaiting review

- [x] `/api/v1` accepts OAuth access tokens (same scopes, demo lock, rate limit). The OpenAPI document gets an `oauth` security scheme, and the 401 message names both methods.
- [x] `kosha-android` and `kosha-ios` public clients: PKCE, `skipConsent`, redirect `app.getkosha:/oauth/callback`. They are listed as `cachedTrustedClients`. Dynamic registration is unchanged.
- [x] Demo through authorize: `/login` honours `login_hint=demo`, and a demo browser session may sign in again.
- [x] `/api/app/*` (`src/server/app-api.server.ts`), outside MCP and OpenAPI:
  - `GET /meta`: the web's `getMeta` plus `demo` and `version`
  - `PATCH /preferences`: every PREFS key, the `ai_*` ones included
  - `GET /account`: sign-in methods and Delete & reset counts
  - `PATCH /profile`, and `GET/PUT/DELETE /avatar`
  - attachments: list per transaction and per recurring, upload, download, delete
  - import: `memory`, `read` (CSV, OFX and QFX parsed on the server, PDF through AI), `entries`, `lines`, `transfers`, `finish`, and the AI helpers `layout` and `tidy`
  - AI providers: list, test, save, set default, disconnect; `GET /ai/log`
  - `api-access`, `api-keys`, `connected-apps`
  - `export/transactions.csv` and `export/kosha.json`
  - `DELETE /transactions` (delete all)
- [x] `create_transactions` takes an optional `id` (UUID), so a retried entry is saved once.
- [x] Fixed: the CSRF check refused `/api/auth/oauth2/token`, `revoke`, `introspect` and `register` for any client without an `Origin` header, which means every native app. Those endpoints never read cookies, so they are now exempt.
- [x] Checked live against `npm run dev`: PKCE sign-in as demo with no consent screen, token exchange, REST read, demo writes refused (messages in German with `Accept-Language: de`), meta, export, refresh rotation, revoke, and the token refused after revoke. A retried `create_transactions` with the same `id` saved one row.
- [ ] Owner: review, merge and deploy. Deploying runs migration 0009 remotely.
- [x] Added in M1: the `app` message area (`messages/<locale>/app.json`, all 12 languages) for strings only the phone apps show.

### M1 · Skeleton and design system: done, awaiting review

- [x] `git init`. Gradle 9.6.0 wrapper, AGP 9.2.1 (the newest Android Studio supports; 9.4.1 also builds), built-in Kotlin 2.4.21, Compose BOM 2026.09.00, JDK 21. Version catalog in `gradle/libs.versions.toml`.
- [x] Modules: `app`, `core:model` (pure Kotlin, JVM unit tests), `core:network`, `core:data` (empty until M2 and M3), `core:design`.
- [x] `KoshaTheme` from `k.css`:
  - light and dark tokens, five accents, series s1 to s6, neutral mode (`selection` is ink, account squares hide)
  - type scale, Geist and Geist Mono, number font (mono or proportional with tabular figures)
  - density (compact rows stay at 48dp for touch targets)
  - the app language for money and dates; status and navigation bar icons follow the theme
- [x] Phosphor regular and fill fonts, drawn by name through `res/raw/phosphor.txt` (`scripts/phosphor-codes.py`). `categoryIconName` ports `category-icon.tsx`.
- [x] Strings: `scripts/sync-strings.py` builds `strings.xml` in 12 languages from the web repo's messages (1,910 strings and plurals). Five import messages with two plural selectors can't be Android plurals; M7 handles them.
- [x] Keypad logic in `core:model` (`Keypad`, `Money`, `EntryKind`) with unit tests: expression rules, evaluation, ×100 minor units for every currency, locale grouping and currency affixes, the true minus.
- [x] Components in `core:design`, all on the debug Inputs kit:
  - amount display and 4×4 keypad (haptic tick, hold backspace to clear)
  - tile row, category grid (picked, guess, AI guess)
  - month grid (the language's first weekday, dots, today outlined), time wheel (24-hour or AM/PM, TalkBack adjustable), quick-pick chips
  - account list, searchable list picker, sheet (Material only for drag, insets and back), Undo toast (5 s)
  - segmented, switch, radio, tick, chips, stepper, buttons, fields
- [x] The debug build has a second launcher entry, "Inputs kit", with switches for theme, colour mode, accent and number font. Checked on the Pixel 10 Pro emulator in light, dark and neutral.
- [x] Tab bar shell (Overview, Activity, raised Add, Recurring, Accounts), edge to edge, the SplashScreen API with the K-coin, and an adaptive and themed launcher icon.
- [x] `./gradlew test lint` passes.
- Not yet: ktlint and detekt (Android lint runs). Hilt and Navigation come in M2 with the first real screens, and each `feature:*` module comes with its milestone.

### M2 · Auth and session

- Launch (SplashScreen API), SignIn, OwnServer (live discovery check), Finishing, SignInError.
- Browser sign-in: Custom Tabs, our own PKCE, state check, redirect `app.getkosha:/oauth/callback`, scopes `openid profile email offline_access kosha:read kosha:write`, `resource=<server>/api/v1`.
- Token store: Tink AEAD with a Keystore key and DataStore. One refresh at a time behind a mutex, and the new refresh token is stored before the new access token is used.
- Demo: `login_hint=demo&prompt=login`, the demo banner, write actions disabled.
- Sign out: revoke, clear cache and outbox (warn when entries are waiting), back to SignIn.
- App lock: `BiometricPrompt` with `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`, Lock again after, `FLAG_SECURE` while locked, LockScreen.

### M3 · Data layer

- Typed client for `/api/v1` and `/api/app`. Errors are `{ error: { code, message, issues } }`, and `issues[].path` maps to fields. `Accept-Language` follows the app's language.
- Room cache: accounts, categories, payees, recent transactions per month, recurring, settings, month overview and net worth. Reads go cache-first with a background refresh, and nothing polls (120 requests a minute).
- Outbox: new transactions only, each with a client `id`, sent in order by WorkManager when online. Rejected entries are dropped and reported. Lists show "waiting" rows.
- Global state: selected month, display currency, `meta`, settings. Day and month maths use the user's time zone from settings (`java.time`).
- Loading and Offline states.

### M4 · Add

- Every `02-add-*` board: keypad, tiles, payee field with `search_payees` guesses (`count` and `averageAmount` give "14 times" and "usually €28"), the category Next flow, account, date, time and note sheets, income, transfer with the fee sheet, Add another, Undo toast (5 s), offline save, haptics.
- App shortcuts: Add expense, Add income, Scan receipt.

### M5 · Overview and Activity

- Overview cards, pace chart, Needs attention, Coming up, Where it went, Net worth. Month, currency and search sheets with quick add (port `quick-add.ts` and its tests from `core.test.ts`). Account menu.
- Activity with filters, swipe to delete and duplicate, tag and event view, transaction and transfer detail, delete confirm, attachments.

### M6 · Recurring and Accounts

- Recurring list, detail, form, payment sheet, documents (zip and share).
- Accounts with the net worth chart, account and investment detail, account form, Fix balance, gains sheet, exchanges.

### M7 · Reconcile and Import

- Reconcile start, tick and done (`check_statement`, `reconcile_account`, `undo_reconciliation`).
- Import choose, accounts, payees, done (`/api/app/import/*`). A share target for CSV, OFX, QFX and PDF.
- Decide then whether `matchLines`, `pairTransfers` and `payeeFor` (pure, in `statement.ts`) are ported to Kotlin or move to a server endpoint. Prefer the endpoint, so there is one implementation.

### M8 · Settings and onboarding

- Every `08-settings-*` screen. Browser hand-off for Security and Delete account.
- Welcome flow (`/welcome` logic: shown when there are no accounts), FirstOverview, empty states.

### M9 · AI

- Describe it, receipt camera (CameraX), filled-by-AI state, recap card, Ask Kosha, AI settings, provider, privacy, log, and AI hints in reconcile and import. Nothing shows when `meta.ai` says a feature is off.

### M10 · Polish and release

- Dark and neutral pass on every screen, hide amounts, density, number font, animations (system reduce-motion counts as Reduced), every language, TalkBack, large fonts, the Glance quick-add widget, Play internal testing.

## Gaps: server work still needed (before the milestone named)

Found by comparing the designs with `api-contract.ts`. Each item needs a small change to the public API or `/api/app`. Do each one in the web repo at the start of the milestone that needs it.

| Needed for | Gap | Source today |
|---|---|---|
| M4 DateSheet, TimeSheet | Transactions have only `day`; no time to read or write. The web's own form sends `time`. | `saveInput.time` in `finance.server.ts`; not in the public `Transaction` |
| M4 DateSheet | Dots on days with entries: there is no cheap "days with entries for a month" | would need paging `search_transactions` |
| M4, M5 Note tile "1 file" | Attachment count per transaction | the server counts `files` per row (`finance.server.ts`), but `Transaction` leaves it out |
| M5 Overview pace chart | Day-by-day `usual[]` and `current[]` curves; `get_month_overview` gives single numbers | `finance.dashboard().pace` |
| M6 Accounts chart | Net worth per account per month (1Y, 2Y, All); `get_net_worth.history` is totals only | `accountsOverview` |
| M6 RecurringDetail | Payment history, paid so far, count, average; `payments` has only overdue plus the next six, and `search_transactions` has no `recurringId` filter | web's `sub.history` |
| M5 TagView | Spending by category for a tag or event; `totals` is per currency | — |
| M6 AccountDetail | "Last entry today"; In / Out / Moved where Moved counts both directions (`totals.transferred` counts outgoing only) | `accountsOverview` |
| M8 Categories | Balance correction usage count; `list_categories` leaves out category `0` | — |
| M8 Appearance | "Account colours" is not a PREF. Add `account_colours` to `PREFS` (synced), or keep it on the phone. Default: add it to PREFS. | — |

## Gaps the app fills from existing operations

These follow the web's own formulas. Port each formula and cite its source.

- **Overview:**
  - Saved so far = income − spending.
  - Projected = spent + max(0, usual whole month − usual by now) (`index.tsx`).
  - Still to pay = `upcoming` up to month end, converted.
  - Pills: "fixed" is `recurringOnly`; "new" when there is no usual or usual × 10 < total.
- **Needs attention:**
  - overdue `upcoming` with a `recurringId` (take `dueDay` from `list_recurring` for Mark paid)
  - card owed = `creditLimit − balance`
  - category above usual (thresholds in `index.tsx`)
  - investment `gainDay` older than 60 days
- **MonthSheet:** one `get_month_overview` for December of the year gives 12 months of `history`.
- **Exchanges and the fee sheet's cost against market:** port `exchange()` from `core.ts`.
- **Coming up horizon:** `upcoming_horizon` is applied on the client; the server always returns 30 days.

## Conflicts (the web wins)

1. **Minor units.** The brief says JPY and KRW have 0 decimals. The web treats every currency as ×100: `fmt` divides by 100, and `parseAmount` and quick add multiply by 100. The app does the same: ¥1,200 is `120000`. The keypad's decimal key may still hide for zero-decimal currencies as a display choice, but storage stays ×100. Confirm with the owner.
2. **Keypad digit cap.** The web's one-number `pressKey` caps 8 digits in total, decimals included. The prototype and the brief cap each number's whole part at 8, plus the currency's decimals. The keypad with operators is new to the app, so it follows the prototype; the largest amount is 99,999,999.99, well inside a `Long`.
3. **Fix balance.** Not `create_transactions` with category 0: use `update_account { balance }`, which books the correction, or `reconcile_account { correctDifference }`.
4. **Transfer amount.** `sent` includes the fee. The amount typed on Add Transfer is what left the From account, fee included (as `phone-add.tsx` does).
5. **"Never sum on the phone."** The web converts the server's per-currency totals with `convert()` for Still to pay, Activity, Exchanges and Committed per month. The app may convert server totals at the server's rates. It never adds up rows itself for month figures or net worth.
6. **Category icons.** The `icon` field is an emoji or `ph:<name>`, not only an emoji.
7. **Variable recurring.** `create_recurring` describes `variable` as transfers only, but the designs show variable bills (Stadtwerke). Check the server's behaviour before M6.

## Other notes

- **Security.** Skipping consent for a public client on a custom URL scheme is what RFC 8252 §8.6 advises against. Another app on the same phone could register `app.getkosha:` and start a sign-in as `kosha-android`, and a signed-in browser would hand it a code with no screen in between. PKCE doesn't stop this, because that app makes its own verifier. Fix it in M2: add a claimed HTTPS redirect (`https://getkosha.app/app/oauth/callback`, served `assetlinks.json` with the release key's fingerprint), make it the app's redirect, then drop the custom scheme from the skip-consent client.
- **Strings.** About 630 of roughly 1,200 text nodes on the boards already match English messages. Roughly 200 to 250 are new and go in a new `app` area, such as "Start with the amount", "Kosha is locked", "Its receipt is deleted with it." and "Signing out clears what this device saved." Write them in every locale with the glossary in `translations.md`.
- **Tokens.** The design's colours match the web's `styles.css` exactly. The phone adds `sel`, `glass`, `scrim` and its own shadows and radii (card 16, button and field 14, sheet 24).
- **quick-add.ts port.** Its exports are `parseQuickAdd(q, wallets, categories, locale)`, `needsAi` and `QuickParse`. It depends on `cleanTag` and the locale. It uses Unicode script classes and NFKC: use `Normalizer` and `UNICODE_CHARACTER_CLASS`, and `BigDecimal` instead of `parseFloat`. Port its asserts from `core.test.ts`.
