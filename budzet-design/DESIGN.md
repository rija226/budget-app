# Budžet: design handoff for implementation

Personal Android budgeting app. Single user, one phone, no accounts, no server.
Stack: Kotlin, Jetpack Compose, Material 3. All UI copy is Serbian (ijekavica, Latin script).

This folder is the source of truth for the UI. Read this file first, then the artboards.

## Folder contents

| Path | What it is |
|---|---|
| `DESIGN.md` | This spec: rules, screen map, component map, implementation order |
| `theme/*.kt` | Ready-to-use Compose theme. Copy into `ui/theme` and `ui/format`, fix the package name |
| `design/*.dc.html` | One file per artboard. HTML with inline styles plus a `<script>` block holding the sample data and state logic. Read them as a precise layout spec; they are not meant to run outside the design tool |

### How to read a `.dc.html` artboard

- Markup inside `<x-dc>` is the layout. Inline `style` values map directly to Compose (px = dp, font-size px = sp).
- `{{c.someRole}}` is a Material 3 color role, e.g. `{{c.primaryContainer}}` = `MaterialTheme.colorScheme.primaryContainer`; `ok*` and `warning*` roles come from `LocalStatus`.
- `<sc-for list="{{items}}">` = a list (`LazyColumn` items / `forEach`). `<sc-if value="{{flag}}">` = conditional UI / state branch.
- The `<script>` block at the bottom contains the sample data and the `variant` logic (which states exist and what changes between them). The `variant` enum in `data-props` lists every state of that screen.
- Icons are Material Symbols Rounded names written as text (e.g. `receipt_long`, `auto_awesome`). Use `Icons.Rounded.*` / material-icons-extended, same names in PascalCase.
- Files ending in `Tamno` (dark) or with a state suffix are thin wrappers that render a base file with `theme="dark"` and/or `variant="..."`. Implement the base file once; dark comes from the theme.

## Global rules (must hold everywhere)

1. **Money**: store as `Long` cents. Format with `theme/Money.kt`: `1.500,00 €`, `12,50 €`, comma decimals, dot thousands, non-breaking space before the currency. Use tabular figures (already in the type scale).
2. **Dates**: `dd.MM.` (`30.09.`), time `HH:mm`. Day group headers: `Danas`, `Juče`, then `dd.MM.`.
3. **Limit status** (see `limitStatus()` in `StatusColors.kt`), never color alone, always icon + text:
   - Ok, under 80%: pill `okContainer` + `check_circle` + `72%`; bar color `primary` (not green); note `U redu · ostalo 70,00 €` in `onSurfaceVariant`.
   - Warning, 80 to 99%: pill `warningContainer` + `warning` icon; bar `warning`; note `Blizu limita · ostalo 4,00 €` in `warning`.
   - Over, 100% and more: pill `errorContainer` + `error` icon; bar `error` (capped at full width); note `Preko limita za 12,00 €`, or `Limit dostignut` when exactly 100%.
   - No limit: pill `surfaceContainerHighest` + `all_inclusive`; note `Bez limita`.
4. **AI-suggested, unconfirmed category**: transparent chip with 1dp **dashed** `tertiary` border, `tertiary` text, filled `auto_awesome` 14dp icon. Confirmed category: `surfaceContainerHighest` chip, `onSurfaceVariant` text. Dashed border needs a custom `Modifier.drawBehind` with `PathEffect.dashPathEffect`.
5. **Foreign currency rows**: primary amount in original currency (`25,00 KM`), secondary line `≈ 12,78 €` in `bodySmall`/`onSurfaceVariant`. BAM uses the fixed rate 1 € = 1,95583 KM.
6. **Tone**: calm, factual. No confetti, no exclamation marks, no "pažnja". Overspending shown with red + icon + plain text only. Spending increases in reports use neutral arrows, not red.
7. **One-handed use**: primary actions in the lower half (FAB, bottom action bar on detail, extended FAB on Budžet, sheet for category picker).
8. **Long labels and large font**: no fixed heights on text containers, names wrap (never truncate category names), amounts `softWrap = false`. See `PocetnaVelikiFont.dc.html` for 200% font: rows stack vertically. Bottom nav labels use `CappedNavLabel` (max 1.3x, ellipsis).
9. **Touch targets**: min 48dp. Icon buttons 48x48.
10. **Elevation**: tonal surfaces only (cards = `surfaceContainerLow`, nav = `surfaceContainer`, dialog = `surfaceContainerHigh`). Only FABs have a shadow.

## Navigation

`NavigationBar` with 4 destinations: **Početna** (`home`), **Transakcije** (`receipt_long`), **Izvještaji** (`bar_chart`), **Budžet** (`account_balance_wallet`). Selected icon is filled.

- **Analize** is the second tab (`PrimaryTabRow`: `Pregled` | `Analize`) inside Izvještaji.
- **Za provjeru** opens from the home chip `Za provjeru (N)` (hidden when N = 0) and from the notification.
- **Podešavanja** opens from the gear icon in the Početna top bar. **Sirovi log** opens from Podešavanja.
- **Uvod** (onboarding) is shown on first launch until notification access is granted.

## Screen map

| Screen | Base artboard | States (`variant`) / wrappers |
|---|---|---|
| Uvod / dozvole | `Uvod.dc.html` | `start`, `blocked` (`UvodBlokirano`), `done` (`UvodGotovo`) |
| Početna | `Pocetna.dc.html` | `normal`, `over` (`PocetnaPrekoraceno`), `empty` = no salary yet (`PocetnaPrazno`), `paused` = notification access lost (`PocetnaPauza`); 200% font reference `PocetnaVelikiFont` |
| Transakcije | `Transakcije.dc.html` | `normal`, `empty` (`TransakcijePrazno`) |
| Transaction detail | `Transakcija.dc.html` | `normal`, `picker` = category bottom sheet (`TransakcijaKategorija`) |
| Izvještaji | `Izvjestaji.dc.html` | single state |
| Analize list | `Analize.dc.html` | shows ready, in-progress and failed cards |
| Analysis detail | `Analiza.dc.html` | `ready`, `loading` (`AnalizaPriprema`), `failed` (`AnalizaGreska`) |
| Budžet | `Budzet.dc.html` | `normal` (one limit field focused), `delete` = confirm dialog (`BudzetBrisanje`) |
| Za provjeru | `ZaProvjeru.dc.html` | `normal` (3 sections), `empty` (`ZaProvjeruPrazno`) |
| Podešavanja | `Podesavanja.dc.html` | single state |
| Sirovi log | `SiroviLog.dc.html` | single state |
| System notifications | `Notifikacije.dc.html` | limit 80%, limit exceeded, analysis ready; channel rules |
| Components | `Komponente.dc.html` | all component states in one board |
| Design tokens | `Main.dc.html`, `ComposeTema.dc.html` | reference only, already in `theme/` |

## Component map (build these first, in `ui/components`)

| Component | Compose base | States |
|---|---|---|
| `HeroCard` | `Card(shape = extraLarge)` | normal (`primaryContainer`, label + `displayLarge` amount + `event` icon "još N dana do plate"), over (`surfaceContainerHigh`, `error` text + filled `error` icon, "Prekoračeno za X"), empty ("Čeka se prva plata" + 2 actions) |
| `CategoryLimitRow` | `Column` in a `surfaceContainerLow` card | avatar 40dp `secondaryContainer`, name (wraps), "X od Y", `StatusPill`, 8dp bar (`Box` + `fillMaxWidth(fraction)`, track `surfaceContainerHighest`), status note |
| `StatusPill` | `Surface(shape = small)` | ok / warning / over / limit reached / no limit |
| `TransactionRow` | `ListItem`, min 72dp | EUR, foreign currency, AI unconfirmed, transfer (`sync_alt`, "van budžeta"), salary (`work`, `+` amount in `ok`), no exchange rate |
| `CategoryLabel` | `Surface` 24dp, shape small | confirmed, AI suggested (dashed), transfer, salary |
| `PermissionStepCard` | `Card` `surfaceContainerLow` | todo (outlined number), done (`okContainer` check), blocked (1dp `error` border, `errorContainer` block icon) |
| `ReviewChip` | `AssistChip`-like, `secondaryContainer` | `Za provjeru (N)`, hidden at 0 |
| `BudzetNavigationBar` | `NavigationBar` | 4 items, `CappedNavLabel` |

## Behavior notes

- **Hero amount**: the remaining daily allowance until the next salary. Over state shows the absolute overspend for today; the text explains the difference is spread over the remaining days. Calculate everything; do not copy sample numbers (the sample data in the artboards is not internally consistent).
- **Period**: from one detected salary to the next. Salary rule = source + minimum amount (Podešavanja). "Označi kao platu" on a transaction starts a new period.
- **Transfer** ("Prenos") transactions are excluded from spending.
- **Za provjeru** has 3 sections: unrecognized notifications (`Dodaj kao transakciju` / `Zanemari`), unconfirmed AI categories (`Potvrdi` / `Promijeni`), transactions without an exchange rate (`Unesi kurs za XXX` / `Unesi iznos u €`). Transactions without a rate are not counted in the budget until resolved.
- **Analysis**: generated after salary arrives; states ready / loading ("Analiza se priprema…") / failed (with reason and "Pokušaj ponovo"). Body = headings + paragraphs + 3 tip cards, each with an estimated monthly saving.
- **Notifications**: channel "Upozorenja o limitu" (DEFAULT importance, at most one per category per threshold 80% / 100% per period), channel "Analize" (LOW). Small icon monochrome wallet.
- **Uvod**: step 1 (notification access) is required for "Nastavi"; steps 2 and 3 are recommended. The blocked state shows the help card with the "Dozvoli ograničena podešavanja" steps and a button that opens App info.

## Out of scope

Login, accounts, sync, multi-user, social, iOS, SMS access, marketing onboarding slides.

## Suggested implementation order

1. Theme: copy `theme/*.kt`, add Manrope (Google Fonts provider or bundled TTF), wrap the app in `BudzetTheme`.
2. Components from the component map, each with `@Preview` in light and dark, plus one preview at `fontScale = 2f`.
3. Navigation scaffold with the 4-tab `NavigationBar` and routes for all screens.
4. Screens with fake data, matching every `variant` state listed above (one preview per state).
5. Only then wire real data (notification listener, Room, period logic, AI analysis).
