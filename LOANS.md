# Android personal lending and borrowing

Implemented on `feat/personal-loans` against web/server commit
`49daf525c6511deddad86da0ee2ce6b05d9fcc81`. The web and iOS repositories were not edited.

## Behavior

- Accounts has an Accounts/Loans switch. Existing primary navigation and everyday Add entry types remain.
- Loans supports independent agreements, lending/borrowing and open/paid/all filters, person/purpose search,
  remaining principal, derived repayment status, and due/overdue dates. Archived accounts do not filter loans.
- Creation supports a new cash movement, an eligible existing entry, or an opening outstanding balance with
  no cash movement. The form explains the classification/cash effect.
- Details show original/tracked principal, repayments, balance, dates, notes, and chronological bank history.
  Repayments support partial/full amounts, different accounts, existing-entry links, and separate actual
  account amounts and loan-currency principal. No interest or instalment controls are added.
- Metadata PATCH sends only changed fields, including explicit null when clearing a due date.
- Explicit unlink requires an ordinary top-level category matching cash direction and explains the effect
  on income/spending. Funding cannot be unlinked while repayments remain. Opening agreements can only be
  removed without linked entries. Repayment deletion uses a separate confirmation and reconciliation warning.
- Activity and account histories mark and link actual movements once. Linked entries route to the loan flow;
  swipe duplication/deletion and generic Add duplication do not silently change linked principal.
- `loan_principal` joins the shared neutral/system category owner, including refund exclusion and local day
  totals. Reports and historical net worth remain server-owned; current receivables/personalDebt fields are
  modeled without adding today's balances to historical points.
- Overview shows separate all-date balances and dated obligations. Global search has a Loans shortcut and
  person/purpose matches from meta. New strings are generated from the pinned web catalogs in all 12 locales.
- Reads use the existing cache/error state and version invalidation. Loan writes never enter the transaction
  outbox. Forms use saveable primitive fields and a saveable frozen payload/UUID for uncertain write retries;
  OAuth refresh replays the same body. Definitive rejection keeps the fields editable. Acknowledged writes
  refresh shared state. A cached linked transaction waits for refresh before redirecting from generic detail.

## Verification

Passed locally:

- `./gradlew :app:assembleDebug test lint` with JDK 21 and the local Android SDK.
- 74 JVM tests, zero failures/errors/skips. Loan regressions cover neutral day totals/refund/eligibility,
  positive and bounded repayment principal, pinned cross-currency detail decoding, partial metadata PATCH,
  byte-identical OAuth replay, and conflict errors without automatic retry.
- `git diff --check` and regeneration of all 12 string catalogs. No formatter, ktlint, or detekt task is configured.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

Not verified:

- Emulator installation and interactive flows, theme/screenshot comparison, TalkBack, large fonts, and
  process-death restoration. T3 device discovery/open succeeded, but agent-device installation failed with
  `adb timed out after 10000ms`. Its suggested ADB restart failed with
  `could not install *smartsocket* listener: Address already in use`; device actions stopped there.
- Live OAuth/backend loan flows, server migration/deployment, and release builds/store delivery. Transport
  tests use synthetic fixtures and MockWebServer; they do not establish deployed-service availability.
- Saveable state follows Android's saved-state checkpoint; abrupt termination before a checkpoint is not
  durable draft storage. Uncertain submitted requests stay frozen for exact retry while that draft exists.

The originating web checks/REST suite/SSR/export checks are inherited evidence only. They were not rerun
here and are not Android runtime proof. No deployment, merge, push, or store release was performed.
