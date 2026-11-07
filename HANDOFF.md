# Kosha mobile: handoff for building the native apps

Written 7 Nov 2026, at the end of the design phase. This is the brief for building Kosha's phone apps natively: **Jetpack Compose on Android first**, then **SwiftUI on iOS**. It covers what to build, what the server has to provide, how every screen behaves and the order to build in.

| | Where |
|---|---|
| Android repo (Android, Wear OS later) | `/Users/amruth/Projects/kosha-android` |
| iOS repo (iOS, iPadOS, watchOS later) | `/Users/amruth/Projects/kosha-ios` |
| Web app and server (source of truth) | `/Users/amruth/Projects/kosha` (read its `CLAUDE.md`, `PRODUCT.md`, `DESIGN.md`, `README.md`) |
| Design, as pictures | `design/screens/light/*.png` in the Android repo: 87 phone screens at 2× plus the inputs kit. They ship as `kosha-mobile-design.zip`; unzip it at the repo root if `design/` is missing |
| Design, as source | `design/source/*.dc.html` (markup and copy of each screen) and `design/source/k.css` (every token and component style), in the same zip |
| Design canvas (owner only) | https://claude.ai/artifact/CMwUHh6hVasBLg9ZZVhzfa (Design artifact "Kosha for iOS and Android", 88 screens, 10 pages) |

The PNGs are the spec for layout. `k.css` is the spec for numbers: colours, sizes, radii. When a PNG and this file disagree, this file wins. When either disagrees with the web repo about money behaviour, the web repo wins.

---

## 1. What Kosha is

Kosha (कोश, "treasury") is a personal finance tracker for people with money in more than one place and more than one currency. Tagline: **"Jot down what you spent. Know where it went."** The web app runs at https://getkosha.app on one Cloudflare Worker with a D1 database. It is open source (MIT, github.com/amruthpillai/kosha), and people can run their own server.

The phone app has two jobs:

1. **Quick entry.** Writing a payment down must take seconds: amount first, guesses from history fill the rest.
2. **A glance at the month.** How this month compares with a usual one, what is due, where the money stands.

Product principles, from `PRODUCT.md`, apply to every screen:

- Entry must take seconds; guesses from history fill the rest.
- Totals must be right before they are pretty: neutral categories, transfers and account types are respected everywhere.
- Plain words. Say what a number means.
- Everything in the app is also reachable from the API.

What changed from the web on purpose (decided during design):

- **Sign-in happens on the web.** The app never draws a password, passkey or two-factor form. It signs in through the system browser (OAuth 2.1 with PKCE).
- **Kosha draws its own inputs.** Keypad, date, time, account, category, currency, time zone and list pickers are Kosha components in bottom sheets. The system keyboard only opens to type words (payee, note, names, search, server address, keys).
- **A bit more colour than the web.** Each account keeps its chart colour as a small square wherever it appears, and selections take the user's accent. Settings › Appearance › Account colours off gives the web's ink-only look.
- **Phone-only additions:** swipe rows to delete or duplicate, camera for receipts and attachments, the system share sheet for exports, zips and API keys, haptic ticks on the keypad and on save, Face ID or fingerprint app lock.

---

## 2. Data rules the client must respect

These come from the web repo's `CLAUDE.md`. Breaking one gives wrong totals without any error.

- **Amounts are integer minor units (cents), and the sign is the direction.** Negative is money out. €12.50 is `-1250` for an expense. Never use floating point for money; parse keypad input straight into minor units.
- **Minor units follow the currency.** EUR and INR have 2 decimals, JPY and KRW have 0. The keypad's decimal key and the number of decimals follow the account's currency.
- **Days and months are strings in the user's time zone**: `YYYY-MM-DD` and `YYYY-MM`. The time zone comes from settings, not the phone. Use `java.time` / `Calendar` with that zone, never the device default.
- **Category `0` (balance corrections) and `transfer` are neutral**: not income, not spending. `transfer_fee` counts as spending.
- **A transfer is two linked rows**, one per account, each in its own currency. Create and edit transfers through the transfer operations, never as two transactions.
- **Net worth depends on account type.** Bank and cash count their balance, credit cards count what is owed as debt, investments count money in plus the latest gain. Take net worth and month figures from the report operations rather than summing on the phone.
- **Subscriptions are schedules, not rows.** Upcoming payments are computed by the server. A transaction row exists only once a payment is paid, edited or skipped.
- **Exchange rates are units per 1 USD**, refreshed at most once a day by the server.
- **Tags** are lowercase without `#`. **Events** are optional date ranges; a new entry dated inside an event joins it.
- **Reconciled entries** have `reconciledTo`/`reconciled_id`. Changing their amount, account or day un-reconciles them, so warn first ("Reconciled. Changing it means checking it again.").
- **The demo account is read-only.** The server refuses writes; the app shows the demo banner and disables write actions.
- **AI is optional and per feature.** A feature that is off leaves no trace in the UI: no sparkle, no button, no empty card.

---

## 3. Server work first (milestone 0, in the web repo)

The app talks to the public REST API at `/api/v1`, which already covers most of what the screens need (see §5). A few things have to change or be added on the server before the app can work. Do these in `/Users/amruth/Projects/kosha`, following its `CLAUDE.md` (migrations, messages in every locale, `npm test`, `typecheck`, `lint`, `check`, `knip`).

### 3.1 Let the REST API accept OAuth access tokens

Today `handleRest` in `src/server/api.server.ts` only accepts API keys (`Bearer kosha_…`), while `handleMcp` accepts API keys **or** OAuth access tokens through `fromAccessToken`. Make REST accept both the same way, with the same scope rules (`kosha:read`, `kosha:write`) and the same demo lock. Access tokens are JWS signed by Better Auth with issuer `<origin>/api/auth` and audience `<origin>/mcp` (`MCP_RESOURCE`); either accept that audience on REST too or add `<origin>/api/v1` as a second resource. Update the OpenAPI security schemes and the 401 message.

### 3.2 First-party OAuth clients for the apps

Kosha is already an OAuth 2.1 authorization server (Better Auth `mcp` plugin in `src/server/auth.server.ts`: authorization code + refresh token, PKCE, dynamic client registration, `/login` and `/consent` pages). For the apps:

- **getkosha.app:** register two static public clients, `kosha-android` and `kosha-ios` (no secret, PKCE required), marked trusted so the consent screen is skipped (check the Better Auth oauth-provider docs for the trusted-client / skip-consent option). Redirect URIs: `app.getkosha:/oauth/callback` (custom scheme, RFC 8252). Optionally also a claimed HTTPS link (`https://getkosha.app/app/oauth/callback` with `assetlinks.json` and `apple-app-site-association`).
- **Your own server:** the app registers itself on first sign-in through dynamic client registration (already allowed), with the same custom-scheme redirect, and stores the `client_id` per server. Those sign-ins show the consent screen, which is right for a server the app doesn't know.
- Scopes the app asks for: `openid profile email offline_access kosha:read kosha:write`.
- Refresh tokens rotate and a reused one counts as replay (`refreshTokenReuseInterval: 0`). The app must serialise refreshes and store the new refresh token before using the new access token.
- **Demo:** "Try the demo" must end in a demo token too. Check how the landing page signs people into the demo today (`demoAvailable()` in `auth.server.ts`) and make the authorize flow support it (for example a `login_hint=demo` that `/login` honours). The demo token is read-only whatever scope it asks for.

### 3.3 App-only endpoints

`CLAUDE.md` says auth, AI keys, export and Delete & reset stay out of the public API on purpose. Keep it that way: add these as **app-only** endpoints (for example under `/api/app/*`), excluded from MCP and the public OpenAPI document, and accepted only from the first-party app clients' access tokens. Each mirrors a server function the web already has in `src/lib/api.ts`.

| Endpoint (suggested) | For screens | Mirrors on the web |
|---|---|---|
| `GET /api/app/meta` | everything (feature flags) | `getMeta()`: AI features on/off (`meta.ai`), attachments available, avatars available, demo, server version |
| attachments: upload, list, download, delete | TxDetail, AttachSheet, NoteSheet, PaymentSheet, PayslipsSheet, ReceiptCamera | `src/server/attachments.server.ts`, `/api/attachments/$id` |
| profile: name, avatar upload/remove | Profile, Welcome | `setAvatar`, `updateUser` |
| import: parse statements, finish import | ImportChoose → ImportAccounts → ImportPayees → ImportDone | `src/routes/_app/import.tsx`, `finishImport`, `statement.ts` |
| AI providers: list, add, replace key, choose model, test, set default, disconnect | AiSettings, AiProvider | `src/server/ai.server.ts` |
| AI visibility and per-feature switches (if not already `PREFS` in `update_settings`) | AiPrivacy, AiSettings | `PREFS` in `core.ts` |
| AI request log, last 30 days | AiLog | `ai_log` table |
| API keys: list, create, delete; connected apps: list, disconnect | ApiMcp | `listApiKeys`, `createApiKey`, `deleteApiKey`, `listConnectedApps`, `disconnectApp` |
| export: transactions CSV, everything JSON (bearer auth) | DataExport | `/api/export/transactions.csv`, `/api/export/kosha.json` |
| delete all transactions | DeleteReset | the web's Delete & reset |
| sign out: revoke this app's refresh token | AccountMenu, Profile | Better Auth revoke |

These stay on the web and the app opens them in the browser, then returns (the designs already say so):

- Password, passkeys, Google, two-factor, signed-in devices (Security screen, "These open getkosha.app and bring you back").
- Delete account ("Opens getkosha.app, where you confirm with your email and password in three steps").
- `/consent` for third-party apps.

### 3.4 Smaller server checks

- **Idempotent creates.** The web's offline queue sends a client-chosen id (`newId`) so a retried entry is saved once (`src/lib/offline.ts`). Make sure `create_transactions` accepts a client id the same way; add it if not.
- **Rate limit** is 120 requests a minute per user across REST and MCP. Batch writes, cache reads, and never poll.
- **Locale.** The public API answers in English. App-only endpoints and AI replies should follow the app's language (send `Accept-Language`, and have the server honour it the way `start.ts` honours the `kosha-locale` cookie).

---

## 4. Architecture

### 4.1 Shape

A thin client over the REST API with a local cache, so the app opens instantly and works offline.

- **Network:** typed client for `/api/v1` and `/api/app`. Generate models from `/api/v1/openapi.json` or hand-write them from `src/lib/api-contract.ts` (the contract, with schemas and examples, is the source of truth; every operation there is one REST endpoint and one MCP tool). Errors come as `{ error: { code, message, issues? } }`; show `message` (it is written for people) and map `issues[].path` (`transactions.3.accountId`) to fields.
- **Cache:** a local database holding the last answers per month and per account (accounts, categories, payees, recent transactions, recurring, settings, month overview, net worth). Screens read the cache first and refresh in the background ("Showing what this phone saved, while Kosha checks for anything new").
- **Outbox:** like the web, only **new transactions** queue offline. Each gets a client id, is shown in lists with a "waiting" mark, and is sent in order when the connection returns. Edits, transfers and deletes need a connection; show the offline state instead. A rejected entry is dropped and reported.
- **Session:** base URL (getkosha.app or the user's own server), client id, access and refresh tokens in secure storage. One refresh at a time.
- **Global state:** the selected month (`YYYY-MM`) and display currency, shared by Overview, Activity and Recurring, plus `meta` (feature flags) and settings.
- **Pure logic in one place**, with unit tests: the keypad expression rules (§7.1), quick-add parsing (port `src/lib/quick-add.ts`), amount formatting, day and month maths in the user's time zone, category guessing display.

### 4.2 Android (first)

- Kotlin 2.x, Jetpack Compose (latest stable BOM), single activity, edge to edge, predictive back, SplashScreen API.
- minSdk 28, targetSdk latest. Package `app.getkosha` (confirm with the owner).
- Material 3 underneath only for plumbing; every colour, shape and type style comes from a `KoshaTheme` built from the tokens in §6. Kosha draws its own controls; do not use Material date or time pickers.
- Libraries: Navigation Compose (type-safe routes), Hilt, Ktor or Retrofit + OkHttp with kotlinx.serialization, Room (cache and outbox), DataStore, WorkManager (send the outbox when back online), AppAuth-Android or `androidx.browser` Custom Tabs with your own PKCE, `androidx.biometric`, CameraX, Coil, Glance (home-screen quick-add widget, later), Vico or Compose Canvas for charts.
- Tokens in EncryptedSharedPreferences / Tink-backed DataStore with a Keystore key.
- Fonts: Geist and Geist Mono (variable, OFL) bundled; Phosphor icons (regular and fill) bundled as a font or vector drawables; Bricolage Grotesque only for the wordmark (or ship the wordmark as a vector).
- Modules: `app`, `core:model`, `core:network`, `core:data` (repositories, cache, outbox), `core:design` (tokens, components, the inputs kit), `feature:*` (add, overview, activity, recurring, accounts, ask, settings, auth). `wear` later.
- App shortcuts: Add expense, Add income, Scan receipt. Share target: statements (CSV, OFX, QFX, PDF) open Import.

### 4.3 iOS (after Android)

- Swift 6, SwiftUI, iOS 17+ (Observation). A `KoshaKit` Swift package (Model, API, Store, Design) shared by the iOS app, widgets and later the watch app.
- `ASWebAuthenticationSession` with `prefersEphemeralWebBrowserSession = false`, so the web session also serves the "opens getkosha.app" settings pages. Keychain for tokens. `LocalAuthentication` for the lock. SwiftData or GRDB for cache and outbox. Swift Charts. AVFoundation or `UIImagePickerController` for the receipt camera.
- WidgetKit and App Intents (Add expense, Siri and Shortcuts). iPad gets a sidebar with the four tabs and a detail column, like the web's desktop layout; the phone screens are the content.
- watchOS later: a quick-add keypad and "spent so far" complication.

---

## 5. Screens and the data behind them

Navigation: a tab bar with **Overview, Activity, [＋ Add], Recurring, Accounts**. Add is the raised centre button. The header on main tabs has the month name (opens the month picker), the display currency switch, the Ask sparkle (when AI is on) and the avatar (opens the account menu → Settings). Swiping Overview, Activity or Recurring sideways moves one month.

API operation names below are the public operations (`/api/v1`, see `src/lib/api-contract.ts`): `list_accounts`, `create_account`, `update_account`, `delete_account`, `search_transactions`, `create_transactions`, `update_transactions`, `delete_transactions`, `list_tags`, `create_transfers`, `update_transfer`, `list_currency_exchanges`, `list_events`, `create_event`, `update_event`, `delete_event`, `list_categories`, `create_category`, `update_category`, `delete_category`, `merge_category`, `list_payee_rules`, `save_payee_rules`, `delete_payee_rules`, `search_payees`, `list_recurring`, `create_recurring`, `update_recurring`, `delete_recurring`, `update_recurring_payment`, `dismiss_recurring_suggestion`, `list_gains`, `record_gains`, `delete_gain`, `get_month_overview`, `get_net_worth`, `get_settings`, `update_settings`, `refresh_exchange_rates`, `check_statement`, `reconcile_account`, `undo_reconciliation`, `read_transaction` (AI), `ask_kosha` (AI), `recap_month` (AI). Check the contract for exact shapes before building each screen. `app:` marks the app-only endpoints from §3.3.

PNG names are `design/screens/light/<page>-<Board>.png`.

### 5.1 Sign-in & setup (`01-signin-*`)

| Board | What it is | Data |
|---|---|---|
| Launch | Brand splash: peacock ground, the K-coin symbol and wordmark. Shown while the session and cache load. | — |
| SignIn | Brand panel with tagline. **Continue to sign in** (opens the browser), **Try the demo**, "Sign in or create an account on getkosha.app with your password, a passkey or Google. Kosha brings you back here.", **Use your own server**. | OAuth authorize (discover endpoints from `/.well-known/oauth-authorization-server`) |
| OwnServer | Server address field with live check ("Found Kosha at kosha.home.example"), Continue, "Use getkosha.app instead". Error state: "Enter a full URL, like https://kosha.example.com". | discovery doc, dynamic client registration |
| Finishing | After the redirect: "Signed in on getkosha.app", "Loading your accounts", "Keeping a copy on this phone for offline", Cancel. | token exchange, `get_settings`, `app: meta`, `list_accounts`, `list_categories` |
| SignInError | SignIn again with "Sign-in didn't finish. The browser closed before Kosha heard back from getkosha.app. Nothing was changed." and Try again. | — |
| Welcome | First run, step 1 of 2: name, main currency, time zone ("You can change all three later in Settings"). | `update_settings`, `app: profile` |
| WelcomeAccounts | Step 2: account cards prefilled (bank, card with owed and limit, investment with put in and value today), add or remove, live net worth, "Skip for now", "Add 3 accounts". | `create_account`, `record_gains` |
| WelcomeDone | "You're all set", net worth, **Import statements** or **Skip for now** (import is always available later from Accounts and Settings › Import & export). | — |
| FirstOverview | Overview on day one: €0.00 "Tap + to add your first entry.", a Fill in your history card with Import, net worth with the new accounts ("The chart starts after your first month."). | `get_month_overview`, `list_accounts` |

Welcome runs when the account has no accounts yet (match the web's `/welcome` logic).

### 5.2 Add (`02-add-*`): build this first and best

The Add screen opens on the amount, with the keypad visible and no keyboard.

Layout top to bottom: close ×, segmented **Expense · Income · Transfer**, AI sparkle (Describe it), the amount (expression line above it when it has an operator, backspace on the right, a note below such as the conversion hint), the **tile row**, the payee field with "usually €28", the 4×4 keypad, then **Add another** (secondary) and the primary button.

| Board | What it is |
|---|---|
| AddEmpty | Fresh state: "€0 · Start with the amount", tiles show the default account, an empty dashed Category tile, Today, Note. Primary says **Next** (disabled until there is an amount). |
| AddPayee | Typing a payee (system keyboard): suggestions with category, account, times used and usual amount ("Dosaking Indisches Restaurant · Dining · Visa Card · 14 times · usually €28.00"), then Go to and "Search activity for …". Picking one fills the guesses. |
| AddReady | Amount `24.07 + 5.50 = −€29.57`, Visa Card, Dining (guessed), Today, payee with "usually €28". Primary **Add expense**. |
| AddIncome | Income: "+€3,240.00 · Money in shows with a plus" in the positive colour, income categories, payee with its usual amount. |
| AddSaved | After saving: toast "Added −€29.57 · Dosaking Indisches Restaurant" with **Undo**, form reset for the next one. Account and date stay. |
| AccountSheet | Account picker: Bank & cash, Credit cards, Investments, each with colour square, type, currency and balance (cards show available credit). Recent first; archived accounts left out. |
| DateSheet | Date and time: quick picks (Today, Yesterday, Start of month), month grid with dots on days that already have entries, future days allowed, "Time 13:05" row. |
| TimeSheet | iOS-style wheel for hours and minutes, Now, 24-hour or AM/PM from the language. |
| NoteSheet | Note, tags (type a tag then space; "Used before" chips), event (picked by date automatically, or choose, or New event), files with Attach. |
| CategorySheet | "Pick a category", reached through **Next**: "Späti am Eck is new, so Kosha can't guess one. Pick it and the expense is added." Search, Most used, All, New. Picking a tile here **saves immediately**. |
| AddTransfer | Transfer: amount, "Not counted as spending. The fee is.", tiles Date and Note only, From and To account rows with a swap button, a "Fee and rate" row. Primary **Add transfer**. |
| FeeSheet | Fee (in the From currency), Received (in the To currency), Via (provider), rate, and the cost against the market rate ("It costs €4.76 (0.95%) against the market rate"). |
| DescribeSheet | AI: "What did you spend or receive?" one sentence ("Lunch with Sam at Nando's, 18.50 yesterday"), Fill in, or Scan a receipt. Uses the system keyboard. |
| ReceiptCamera | AI: full-screen camera with receipt frame, choose a photo, shutter. |
| AddFilledAI | Result: "Read from your receipt. Check it before adding. The photo is attached." AI-guessed tiles carry a small sparkle corner. |

Behaviour:

- Account defaults to the last one used on this phone (Settings › General › Default account), date to today, kind to Expense (General › Add opens as).
- Typing a payee or picking a suggestion fills **guesses** for category and account from payee rules and history (`search_payees`); a guess shows in the secondary ink colour until confirmed by tapping. With AI auto-categorise on, a new payee can get an AI guess (sparkle corner).
- **Next flow:** if there is an amount and no category is known, the primary button says **Next** and opens the category sheet; picking a tile saves. With a category known (picked or guessed), the primary says **Add expense / Add income** (never the amount).
- **Add another** saves and keeps the form open for the next entry, with account and date kept (AddSaved). The primary button saves and closes Add, back to where the user was, with the same toast.
- After every save: success haptic, toast with Undo for 5 seconds (Undo deletes the saved entry, or removes it from the outbox).
- Transfers in two currencies ask for the received amount in the fee sheet; the server stores both legs.
- When the account's currency differs from the main currency, the note under the amount shows "≈ €16.49 at today's rate".
- Offline: the save goes to the outbox ("Saved offline €4.20, Bonanza Coffee" with Undo).
- Data: `create_transactions` (with client id), `create_transfers`, `search_payees`, `list_tags`, `list_events`, `create_event`, `read_transaction` (AI), `app: attachments`.

### 5.3 Overview (`03-overview-*`)

| Board | What it is | Data |
|---|---|---|
| Overview | **Spent so far** card (hero amount, "Day 7 of 30", "€58 under your usual pace", pace chart: dashed usual month vs this month to today, "Usual month €2,480 · Projected €2,420"). Two small cards: **Saved so far** (with % of income) and **Still to pay** (bills before month end). **Monthly recap** card (AI, written on the 2nd, with Hide). **Needs attention** (overdue bill → Mark paid, card owed → Pay, category above usual → Review, stale investment value → Update). **Coming up** list (date, name, amount; transfers greyed) with "All in Recurring →". **Where it went** (top categories with bar and a pill: fixed, −8%, +24%, new; "3 others"; "All activity →"). **Net worth** (hero, change this month and in 12 months, "Accounts →"). | `get_month_overview`, `get_net_worth`, `list_recurring`, `recap_month` (AI) |
| MonthSheet | Month grid for the year with each month's spending; "Swipe Overview, Activity or Recurring sideways to move one month." | `get_month_overview` per month (or a lighter summary) |
| CurrencySheet | "Show totals in": your currencies with rate and the converted net worth; "Converted at today's rates."; link to Settings › Currencies. The header pill steps through the currencies in the user's order. | `get_settings`, `get_net_worth` |
| AccountMenu | Avatar, name, email; Theme (Light, Dark, System); Hide amounts; Settings; Sign-in & security (with a badge when steps are missing); Categories; AI; Language; Sign out ("Signing out clears what this device saved."). | `get_settings`, `update_settings` |
| SearchSheet | Search and quick add from one field: "12.50 rewe yesterday" → **Quick add** row ("Add −€12.50 · REWE · Groceries · N26 Main · Yesterday"), Payees, Go to, "Search activity for …", "Ask …" (AI). | port `quick-add.ts`; `search_payees`, `create_transactions`, `search_transactions` |
| GainsSheet | Update an investment: current value or gain (segmented), keypad, as-of date, note; shows gain and return. | `record_gains` |

### 5.4 Activity (`04-activity-*`)

| Board | What it is | Data |
|---|---|---|
| Activity | Month header, search field, segmented All · Out · In · Transfers, summary (Out, In, Net, and "€200.00 moved between accounts, not counted"), entries grouped by day with day totals. Rows: category icon tile, payee, "category · account · #tags", amount. Swipe left to delete, right to duplicate. Waiting (outbox) rows marked "waiting". | `search_transactions` |
| FiltersSheet | Show (All, Out, In, Transfers), Account chips, Category, Amount (at least / at most, in each entry's own currency), Tag, Event, Show balance corrections, "Show 4 transactions". | `search_transactions` filters |
| TagView | One event or tag across all dates: name, date range, count, total, by category, entries ("added to the event by hand" where it applies). | `search_transactions` (tag/event filter covers every date) |
| TxDetail | Transaction, autosaved as you edit: amount (tap to edit on the keypad), payee, category, account, date and time, type (Expense, Income, Transfer), tags, note, event, attachments ("from the receipt scan"), "At this payee: 14 payments · usually €27.60" (opens that payee's entries), Make this recurring…, Duplicate, Delete. Reconciled entries warn before changes. | `update_transactions`, `delete_transactions`, `app: attachments` |
| TransferDetail | From, To, sent, received, fee, rate and cost vs market, provider, note; "Reconciled. Changing it means checking it again." | `update_transfer`, `delete_transactions` |
| DeleteConfirm | "Delete this transaction?" with payee, amount, date; "Its receipt is deleted with it." Delete transaction (danger) / Cancel. | `delete_transactions` |
| AttachSheet | Take a photo, Choose from photos, Choose a file (PDF or image); "Up to 10 MB each."; upload progress row. | `app: attachments` |

### 5.5 Recurring (`05-recurring-*`)

| Board | What it is | Data |
|---|---|---|
| Recurring | Header with `+` (new recurring). **Committed per month** card (bills and subscriptions split, per year with % of income, "Moved to savings … not counted"). **Looks recurring** suggestion (Make recurring / Not recurring). Lists: Bills, Subscriptions, Income, Transfers, sorted by cost per month, each with "Every month · next 20 Nov", overdue in warning colour, yearly ones show the monthly equivalent. Ended (3), collapsed. | `list_recurring`, `create_recurring`, `dismiss_recurring_suggestion` |
| RecurringDetail | Next payment (date, "Kosha asks for the amount that day" for variable ones, Enter amount / Skip this one), Paid so far, Per month, Payments list (paid, skipped, changed for this payment only, attached bills), Documents by month, account, category, amount, ends; End this bill / Delete. | `list_recurring`, `update_recurring`, `update_recurring_payment`, `delete_recurring` |
| RecurringForm | New recurring: Bill · Subscription · Income · Transfer, name, usual amount (keypad), "Amount changes every time", account, category, repeats every N day/week/month/year (stepper + segmented), first payment, ends, "Kosha will record payments from 10 Aug until today: 3 payments.", Record payments automatically. | `create_recurring` |
| PaymentSheet | Enter this payment: amount on the keypad ("Usually about €84.00"), account, date, Attach bill, Reset to regular, Record payment. | `update_recurring_payment`, `app: attachments` |
| PayslipsSheet | Documents by month for a recurring income or bill: tick months, "Share 3 as zip" (share sheet). | `app: attachments` |

### 5.6 Accounts (`06-accounts-*`)

| Board | What it is | Data |
|---|---|---|
| Accounts | Net worth with 1Y · 2Y · All and a stacked area chart by account (account colours; card owed below zero), currency note ("INR shown at 94.60 = €1"). Groups: Bank & cash (foreign ones show ≈ main currency), Credit cards (available, owed, % of limit used), Investments (value, gain, "Value is 41 days old", Update gains). "Archived (1) · still in net worth". Links: Currency exchange, Reconcile an account, Import statements. Header `+` for a new account. | `list_accounts`, `get_net_worth` |
| AccountDetail | Balance, In / Out / Moved this month, "Reconciled to 30 Sep · Does this match your bank?" with Reconcile and Fix balance, Recent entries, Archive. | `list_accounts`, `search_transactions`, `update_account` |
| InvestmentDetail | Value, invested so far, unrealized gains with %, Update gains, gains history, recent transfers in. | `list_gains`, `record_gains`, `delete_gain` |
| AccountForm | New account: name, type (Bank, Cash, Credit card, Investment), currency (list picker), credit limit and currently owed for cards, opening balance otherwise; "Net worth counts what you owe as debt." | `create_account`, `update_account` |
| FixBalance | "Enter the balance your bank shows", keypad, "Adds a balance correction of −€14.20." | `create_transactions` with category 0 (check the contract for the correction shape) |
| Exchanges | Currency exchanges by year: route, amounts, rate vs market, fee, cost ("Negative means you beat it"). | `list_currency_exchanges` |
| ReconcileStart | Pick an account (last reconciled date), Quick check ("My bank shows the same" / Fix balance), With a statement (date, closing balance, Tick entries off, or Import the statement instead). | `reconcile_account`, `check_statement` |
| ReconcileTick | Statement vs ticked vs difference, AI hints for finding the difference, "On the statement, not in Kosha" (add them), "Close, but not the same amount" (Use €4.50 / Not the same), tick list, "After 31 Oct (9)", Finish with a correction. | `check_statement`, `create_transactions` (with `bankRef`), `update_transactions`, `reconcile_account` |
| ReconcileDone | Matches; past reconciliations with Undo on the latest. | `undo_reconciliation` |
| ImportChoose | Choose files (CSV, OFX, QFX, PDF; many banks and months at once), share-to-Kosha hint, per-file reading progress (PDFs read by AI). | `app: import` |
| ImportAccounts | Each statement's target: existing account (columns as last time, closing balance matches) or a new account; AI-worked-out column mapping to check; PDF closing-balance mismatch warning. | `app: import` |
| ImportPayees | Payees to check (bank text → payee name, category), transfers between accounts found (Not a transfer / Leave out), "Import 192 transactions". | `app: import` |
| ImportDone | Results per account (matches statement / check against the PDF), payments that look recurring. | `app: import` |

### 5.7 Ask Kosha (`07-ask-*`, AI)

| Board | What it is | Data |
|---|---|---|
| AskEmpty | "Ask anything about your money", suggested questions, input, and the privacy line **"Kosha sends only what you share with Anthropic."** where the provider is the user's default provider and "what you share" is underlined and opens Settings › AI › What AI can see. | — |
| AskWorking | The question and the look-ups in progress. | `ask_kosha` |
| AskAnswer | Answer with figures, breakdown, the entries behind it, "How Kosha found this · 2 look-ups" with exactly what was sent, model used, Ask a follow-up, "Check important figures in Activity." | `ask_kosha` |

### 5.8 Settings (`08-settings-*`)

Settings is a searchable list grouped as You, Preferences, Money, Intelligence, Data. Every row saves itself; there is no Save button ("Changes save as you go"). Footer: "Kosha 1.0 (12) · getkosha.app".

| Board | What it is | Data |
|---|---|---|
| Settings | The list: Profile, Sign-in & security (badge for missing steps), General, Appearance, Currencies, Categories, Events, Payee rules, Recurring, AI (Beta), Import & export, API & MCP, Delete & reset. | `get_settings` |
| Profile | Avatar (upload or initials in the accent), name, email (read-only), Sign out. | `app: profile` |
| Security | Progress (password, passkey, backup codes). **On this phone:** Unlock with Face ID / fingerprint, Lock again after (Instantly, 1, 5, 15 min, 1 hr). **Opens getkosha.app:** password, passkeys, Google, two-factor, signed-in devices. | local; browser hand-off |
| General | Language, time zone (list picker), Round amounts on Overview, Default account (Last used), Add opens as (Expense / Income), Suggest a category. | `update_settings` |
| Appearance | Live preview; Theme (Light, Dark, System); Accent colour (Teal, Blue, Amber, Rose, Graphite); Density (Comfortable, Compact); Account colours; Number font (Monospace, Proportional); Hide amounts; Animations (On, Reduced, Off). | `update_settings` for synced ones; local for phone-only |
| ListPicker | One searchable radio list for time zones, currencies, languages and "Move to…". | — |
| Currencies | Your currencies, hold and drag to reorder; **the one on top is the default** (no Make default button); rates with Refresh and last update. | `update_settings`, `refresh_exchange_rates` |
| Categories | Spending and Income tabs, usage counts, Kosha's own categories (Transfer fees, Balance correction), Archived. | `list_categories` |
| CategoryEdit | Name, inside (parent), type, icon (search), subcategories, Merge into…, Archive. | `update_category`, `merge_category`, `delete_category` |
| Events | Events with dates and counts; edit dates, see transactions, delete. | `list_events`, `create_event`, `update_event`, `delete_event` |
| Rules | Payee rules: Learn from my edits, new rule (payee contains / is exactly → category), search, list with use counts and learned/yours. | `list_payee_rules`, `save_payee_rules`, `delete_payee_rules` |
| RecurringDefaults | Record payments automatically (default for new bills); "Coming up" shows until month end / next 30 days. | `update_settings` |
| AiSettings | Use AI features (master switch), provider (default, model), Add a provider, per-feature switches (Auto-categorise, Describe it, Receipt scanning, Monthly recap, Ask Kosha, Statements), What AI can see, Request log. | `app: AI providers`, `update_settings` |
| AiProvider | Provider detail: status, API key (masked, Replace; "Stored encrypted on the server. Kosha never shows it again."), model, Test connection, other providers, Disconnect. | `app: AI providers` |
| AiPrivacy | What AI can see: Amounts, Payee names (else a stable code), Exact dates (else month), Notes (off by default), Account names; a live example of one transaction as sent; what each feature sends. | `update_settings` / `app` |
| AiLog | Request log for 30 days: feature, time, provider, model, tokens; expanding shows exactly what was sent and returned. | `app: AI log` |
| DataExport | Import statements; export Transactions (CSV) and Everything (JSON) through the share sheet. | `app: export` |
| ApiMcp | MCP server URL with Copy; connected apps (scope, date, Disconnect); API keys (New key shown once with Copy, read-only or read & write); API reference link. | `app: API keys` |
| DeleteReset | Delete all transactions (type DELETE to confirm, shows the count and what's kept); Delete account opens getkosha.app. | `app: delete`; browser hand-off |

### 5.9 States & dark mode (`09-states-*`)

| Board | What it is |
|---|---|
| Offline | Banner "You're offline. 3 entries are saved on this phone. Kosha sends them as soon as you're back online, then updates your totals." Waiting rows, toast "Saved offline … Undo". |
| HiddenAmounts | Every money figure blurred; touch and hold to peek. Toggle in the account menu and Appearance. |
| DemoAccount | Banner "Demo account. It holds sample data, and changes are turned off." with Create your own account. |
| EmptyActivity | "No transactions this month. Entries you add show up here. It takes a few seconds: amount, payee, done." Add a transaction / Or import a statement. |
| Loading | Skeletons with "Showing what this phone saved, while Kosha checks for anything new." |
| LockScreen | "Kosha is locked. Locked after a minute away." Unlock with Face ID (fingerprint or face unlock on Android), Use passcode. |
| DarkOverview, DarkAdd, DarkDate, DarkActivity | Dark theme references. Every screen must work in dark. |

---

## 6. Design system

The look is the web app's phone layout (neutral grounds, white cards with hairlines, ink buttons, Geist with Geist Mono figures, Phosphor icons, true minus signs) with a little more colour. All values below come from `design/source/k.css`; sizes are CSS px, use them as dp/pt 1:1.

### 6.1 Colour tokens

| Token | Light | Dark | Use |
|---|---|---|---|
| bg | `#F6F6F4` | `#0B0B0C` | screen ground |
| surface | `#FFFFFF` | `#131315` | cards, sheets, fields |
| soft | `#F0F0ED` | `#1C1C1F` | icon tiles, keypad operator keys, tracks, skeletons |
| sel | `#EBEBE7` | `#232327` | selected row, switch off, avatar ground |
| line | `#E4E4E0` | `#27272B` | hairlines (1px) |
| ink | `#111113` | `#F3F3F4` | text, primary button ground |
| ink2 | `#46464D` | `#BDBDC4` | secondary text, icons, guesses |
| ink3 | `#6B6B73` | `#90909A` | tertiary text, captions, cents |
| on-ink | `#F6F6F4` | `#0B0B0C` | text on ink |
| pos | `#0F766E` | `#2DD4BF` | income, under pace, matches |
| warn | `#B45309` | `#F59E0B` | overdue, above usual, checks needed |
| bad | `#B42318` | `#F87171` | delete, errors, differences |
| brand (accent) | `#0F766E` | `#2DD4BF` | switches, selection, focus, AI sparkle |
| on-brand | `#FFFFFF` | `#0B0B0C` | text on accent |
| bar / bar2 | `#1C1C1F` / `#CFCFCA` | `#ECECEE` / `#3B3B41` | chart bars, tracks |
| scrim | `rgba(17,17,19,.34)` | `rgba(0,0,0,.55)` | behind sheets |
| glass | `rgba(255,255,255,.94)` | `rgba(19,19,21,.94)` | tab bar and sticky footers (blur 12) |

Accents replace `brand`: Teal (default, above), Blue `#2563EB` / `#60A5FA`, Amber `#B45309` / `#FBBF24`, Rose `#BE123C` / `#FB7185`, Graphite `#27272A` / `#E4E4E7`.

Account (series) colours, assigned in account order and shown as 8px rounded squares and in the net worth chart: s1 `#2A78D6` / `#3987E5`, s2 `#E87BA4` / `#D55181`, s3 `#EDA100` / `#C98500`, s4 `#6250D6` / `#9085E9`, s5 `#EB6834` / `#D95926`, s6 `#1BAF7A` / `#199E70`.

**Neutral mode** (Account colours off): selections use ink instead of the accent and account squares are hidden (the account tile shows a wallet icon instead).

**Brand colours** appear only on Launch, the SignIn panel and the app icon: peacock `#0F524E`, marigold `#F4A62A`, paper `#F3F5F0`. Wordmark: lowercase "kosha", Bricolage Grotesque Bold. The symbol is a K whose arm is a coin (source files in the web repo's `design/brand/`).

Shadows: pop `0 8 24 -6 rgba(17,17,19,.18)` + 1px ring at 6%; sheet `0 -12 40 -12 rgba(17,17,19,.28)`.

### 6.2 Type

- Sans: **Geist**. Numbers: **Geist Mono** by default (Appearance › Number font can switch figures to proportional Geist with tabular numerals).
- Hero (big money): Geist 40/600, letter-spacing −0.035em, tabular, cents in ink3 (`€1,312` + `.40`).
- Page title 34/600 (−0.03em). Title 22/600 (−0.02em). Head 15/500. Body 15/1.35. Small 13. Extra small 12. Caption 12/500 uppercase, +0.06em, ink3.
- Add amount: Geist Mono 60, dropping to 46 when longer than 8 characters.
- Negative amounts use the true minus sign `−` (U+2212). Currency symbol, grouping and decimals follow the user's language (`€1,312.40`, `1.312,40 €`). Income shows `+€3,240.00` in pos. Transfers and neutral amounts show in ink2 without a sign.
- Support font scaling; let rows grow rather than truncate money.

### 6.3 Components and sizes

- Card: surface, 1px line, radius 16, padding 16. Tight card: no padding, rows inside with hairlines between.
- List row: min height 52, padding 10/16, gap 12. Settings row: min height 56, title + description.
- Icon tile: 36×36, radius 10, soft ground, ink2 icon (category icons).
- Buttons: primary 52 high, radius 14, ink ground, on-ink text 16/500; medium 44 (radius 12); small 36 (radius 8). Secondary: surface with line. Soft: soft ground. Danger: bad text on a 10% bad tint. Disabled: 40% opacity.
- Chip 40 high, pill radius, line border; small chip 32. Pill (status) 22 high, soft ground, 12px text.
- Segmented: soft track radius 12, 3px inset, segments 38 high radius 9, the selected one on surface with a light shadow.
- Field: min height 48, radius 14, line border; focused 2px ink border with an accent caret; error bad border and message.
- Switch 46×28 (knob 22), on = accent. Radio 22 (dot 12 in accent). Tick 22, radius 7, accent fill with a check.
- Sheet: radius 24 on top, grab handle 36×5, padding 8/16/34, title row with a 36 close button; sizes to content, drag down to close, scrim behind.
- Toast: top, 16 from the edges, min height 52, radius 14, ink ground, Undo button inside.
- Tab bar: 88 high including the home indicator area, glass with blur, 5 columns; centre Add button 56×52, radius 18, ink ground, plus icon 26.
- Header: page title left, then currency pill (36 high), icon buttons (44), avatar (36).
- Tile row (Add): 4 equal tiles, gap 6, 58 high, radius 14, 1px line, icon 18 over a 12.5px label.
- Keypad: 4 columns; digit keys 60 high, Geist Mono 28; operator column on soft, radius 12, 54 high, Geist 26 in ink2; the active operator highlighted.
- Calendar: 38 day cells, today outlined, selected filled with accent, 4px dot under days with entries.
- Charts: thin lines (2px), dashed comparisons, area fills at 6% ink; bar radius 4 on top; account colours in the net worth chart.
- Touch targets at least 44×44 (48dp on Android).

### 6.4 Icons

Phosphor, regular weight for UI, fill for selected tabs and the AI sparkle (`sparkle-fill` in the accent). Category icons: the server stores an emoji per category; the web maps each to a Phosphor icon in `src/components/category-icon.tsx`. Port that map.

### 6.5 Motion and feedback

- Sheets slide up; screens slide; numbers roll when they change; charts draw in. Appearance › Animations: On, Reduced (fades only), Off. Respect the system's reduce-motion setting as Reduced.
- Haptics: a light tick on each keypad press, a success haptic on save, a warning haptic on errors.

---

## 7. Custom components in detail

Build these once in `core:design` (and later `KoshaKit/Design`) and show them all on a debug "Inputs kit" screen that mirrors `design/screens/light/00-start-InputsKit.png`.

### 7.1 Amount keypad

Keys, row by row: `1 2 3 ÷` / `4 5 6 ×` / `7 8 9 −` / `. 0 00 +`. Backspace sits next to the amount; holding it clears. The same keypad is used everywhere an amount is entered (Add, transfer fee sheet, Fix balance, Update gains, Enter this payment, recurring amount, transaction edit, filters' amount).

Port this logic as a pure function with unit tests (from the working prototype in `design/source/AddPrototype.dc.html`):

- The input is a string expression such as `24.07+5.5`. The current segment is the text after the last operator.
- Digit: append, unless the segment's integer part already has 8 digits, or the decimals are full (2 for most currencies, the currency's minor units in general). A leading `0` is replaced by the next digit.
- `00`: like two zeros, ignored when the segment is empty or `0`. `0` on an empty segment types `0`.
- `.`: only once per segment; on an empty segment types `0.`. Hidden for zero-decimal currencies.
- Operator: ignored on an empty input; replaces a trailing operator; drops a trailing `.` first.
- Evaluate with × and ÷ before + and −, ignoring a trailing operator or dot, then round to the currency's minor units. Division by zero is invalid: the amount shows `…` and save is disabled.
- Display: with an operator, show the expression above in small type (`24.07 + 5.50`, with `×`, `÷`, `−`) and the result as the amount. Without, show the typed number grouped (`1,204.5`). The kind sets the sign: `−` for expense, `+` for income, none for transfer.
- Save is enabled when the result is above zero. Convert to integer minor units with the sign of the kind.
- Accessibility labels: "Divide", "Multiply", "Subtract", "Add", "Decimal point", "Double zero", "Delete, hold to clear".

### 7.2 Tile row

Four tiles above the payee: **Account** (colour square or wallet icon + name), **Category** (icon + name), **Date** (Today, Yesterday, Wed 5 Nov), **Note** (or "1 file" when there is only a file). States: empty (dashed border, ink3), guess (label in ink2; a sparkle corner when AI made the guess), picked. In Transfer mode only Date and Note show, in 2 columns, with the From/To rows below.

### 7.3 Category picker

Bottom sheet: search, "Most used" (5 per row, by use), "All", New. Opens from the Category tile ("Category · Most used first") or from Next ("Pick a category" with the reason). Picked: accent outline. Guess: dashed, with a sparkle when AI made it. After Next, a tap saves the entry.

### 7.4 Date sheet and time wheel

Date: quick picks (Today, Yesterday, a recent weekday, Start of month), month grid starting on the language's first weekday, dots on days with entries, future days allowed (planned payments), a Time row. Time: two wheels (hours, minutes; AM/PM wheel when the language uses 12-hour time) in the style of the iOS picker, with a highlighted band, fading rows, flick, drag or tap a row, and Now.

### 7.5 Account picker, list picker, payee field

- Account picker: grouped by type, colour square, name, type and currency, balance (cards: available of limit), recent first, archived hidden.
- List picker: search on top, radio list. Used for time zone, currency, language and "Move to…".
- Payee field: the only text field on Add. Suggestions from `search_payees` show times used and the usual amount; picking fills guesses.

### 7.6 Sheets, toasts, confirmations

- One sheet pattern for every picker; the keypad stays under the thumb when a sheet doesn't need it.
- Toasts carry Undo for five seconds.
- Destructive actions confirm in a sheet with a danger button (DeleteConfirm), or by typing DELETE for bulk deletes.

---

## 8. Behaviours across the app

- **AI optional.** Read `meta.ai` (per feature). With AI off: no sparkle in Add or the header, no Describe it, no receipt scanning, no recap card, no Ask, no AI hints in reconcile, no AI badges. AI calls spend the user's tokens: never call them speculatively.
- **AI privacy line.** Wherever AI is offered for free text (Ask, Describe it), show "Kosha sends only what you share with <provider>", with "what you share" underlined and linking to What AI can see.
- **App lock.** Settings › Sign-in & security › On this phone: Unlock with Face ID (iOS) or fingerprint / face unlock (Android, `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`), and Lock again after Instantly, 1, 5, 15 minutes or 1 hour in the background. Hide the app's content in the app switcher while locked. Phone-only setting.
- **Hide amounts.** Blur every money figure; touch and hold to peek. Synced preference.
- **Offline.** Cache-first reads; the outbox for new transactions; an offline banner with the waiting count; sending when the connection returns; rejected entries reported in a toast.
- **Demo account.** Banner on main screens; write buttons disabled or answered with "Changes are turned off in the demo".
- **Sign out.** Revoke the refresh token, clear the cache and the outbox (warn if entries are waiting), return to SignIn.
- **Month and currency.** One selected month and display currency across Overview, Activity and Recurring; swipe sideways to change month; the currency pill steps through the user's currencies in their order.
- **Language.** Use the web's messages as the source for every string: `messages/<locale>/*.json` (areas: accounts, add, ai, auth, common, import, overview, reconcile, settings, settings-sections, transactions, welcome, …) and the glossary in `translations.md`. Write a script that converts them to Android `strings.xml` (with plurals) and an iOS String Catalog, and add app-only strings to the web's message files under a new `app` area so translations stay in one place. The app's language follows the `language` setting, else the phone.
- **Accessibility.** Content descriptions on icon buttons, segmented controls announce selection, money read as words by the screen reader ("minus 29 euros 57"), font scaling without clipping, 44/48 touch targets, reduce motion.
- **Privacy.** No analytics or crash reporting that sends financial data. Never use real account data in tests or screenshots; use the demo account (`demo@kosha.local`, see the web repo's `CLAUDE.md`).

---

## 9. Build order

Android first. Each milestone ends with the app running on a device and the relevant screens matching their PNGs in light and dark.

| # | Milestone | Done when |
|---|---|---|
| M0 | **Server prep** (web repo, §3) | REST accepts access tokens; app clients registered; demo flow; app-only endpoints for meta, attachments, profile, AI providers, AI log, API keys, export, delete, import; idempotent creates; all web checks pass. |
| M1 | **Skeleton and design system** | Android project with modules, KoshaTheme (tokens, accents, neutral mode, dark), fonts, icons, the Inputs kit debug screen with every component, tab bar shell with placeholder tabs. |
| M2 | **Auth and session** | SignIn, browser sign-in with PKCE, Finishing, SignInError, OwnServer, demo, token storage and refresh, sign out, Launch, app lock and LockScreen. |
| M3 | **Data layer** | API client for `/api/v1` and `/api/app`, cache, outbox with WorkManager, settings and meta, Loading and Offline states. |
| M4 | **Add** | Every Add board: keypad logic with tests, tiles, payee suggestions and guesses, category Next flow, account/date/time/note sheets, income, transfer with fee sheet, Add another, Undo toast, offline save, haptics. App shortcuts. |
| M5 | **Overview and Activity** | Overview with all cards and charts, month and currency sheets, account menu, search and quick add, Activity with filters, swipe actions, tag/event view, transaction and transfer detail, delete, attachments. |
| M6 | **Recurring and Accounts** | Recurring list, detail, form, payment sheet, documents; Accounts with net worth chart, account and investment detail, account form, fix balance, gains, exchanges. |
| M7 | **Reconcile and Import** | Reconcile start, tick, done; Import choose, accounts, payees, done; share target for statements. |
| M8 | **Settings and onboarding** | All settings screens (browser hand-off for the web-only ones), Welcome flow, Empty states. |
| M9 | **AI** | Describe it, receipt camera, filled-by-AI state, recap card, Ask Kosha, AI settings, provider, privacy, log, AI hints in reconcile and import; all hidden when off. |
| M10 | **Polish and release** | Dark mode and neutral mode pass on every screen, hide amounts, density, number font, animations, every language, TalkBack, large fonts, widget, Play internal testing. |
| — | **iOS** | Repeat M1–M10 in SwiftUI with `KoshaKit`, then the iPad layout. |
| — | **Watch** | Wear OS tile and watchOS app: quick add and spent this month. |

Per screen, done means: matches its PNG in light and dark, works in neutral mode and every accent, every string comes from the message files, screen reader labels, reads from the cache when offline, read-only for the demo, the AI-off variant shows no trace of AI, and it holds up at 360dp width and the largest font size.

---

## 10. Decisions to confirm with the owner

1. **App-only endpoints** (§3.3) for AI provider keys, API keys, export and Delete all transactions, versus opening those pages on the web as well. The designs assume native screens for these and the web for password, passkeys, two-factor, devices and account deletion.
2. **Package id and bundle id**: `app.getkosha` suggested for both.
3. **Minimum versions**: Android 9 (API 28) and iOS 17 suggested.
4. **Distribution**: Play Store and App Store; whether to also publish on F-Droid, since the code is MIT.
5. **Wear OS and watchOS scope** for the first watch release.
