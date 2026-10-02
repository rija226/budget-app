# Budžet app: plan i pravila

> **Claude:** ovo je jedini izvor istine za projekat. Pročitaj ga cijelog prije bilo kakvog rada.
> Kad se neka odluka promijeni ili se završi faza, ažuriraj ovaj fajl u istom koraku (sekcija „Status").
> Odluke iz tabele „Odluke" ne mijenjaj bez pitanja vlasnika.

UI design source of truth: budzet-design/DESIGN.md (read before any UI work).

## Cilj

Lična Android aplikacija koja **sama** bilježi troškove iz notifikacija (Google Wallet + 2 banke), pokazuje gdje odlazi novac i pomaže da se uštedi: budžet po kategorijama, dnevni limit do plate i AI analiza na kraju perioda.

- **Korisnik:** samo vlasnik, jedan Android telefon.
- **Uspjeh:**
  - ≥90% kartičnih troškova se upiše bez ručnog unosa
  - vlasnik svaki dan vidi koliko smije da potroši
  - na kraju perioda dobije konkretne savjete gdje da uštedi
- **Komercijalizacija** je moguća kasnije. Ne smijemo je blokirati, ali ništa ne gradimo za nju unaprijed (bez naloga, servera, više korisnika).

## Odluke

| Tema            | Odluka                                                                                                                                                                                                         |
| --------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Platforma       | Samo Android. Kotlin + Jetpack Compose + Room (SQLite). Nema servera.                                                                                                                                          |
| Izvori podataka | `NotificationListenerService`: Google Wallet + notifikacije dvije banke (aplikacija ili SMS). **Nikad `READ_SMS`**: Play Store to ne dozvoljava, a SMS se ionako pojavi kao notifikacija aplikacije za poruke. |
| Valute          | Banke su u različitim valutama. **Glavna valuta je EUR.** Transakcija čuva originalni iznos i valutu, plus iznos u EUR po kursu iz podešavanja u trenutku upisa.                                               |
| Novac u kodu    | Uvijek `Long` u najmanjoj jedinici (centi/pare). Nikad `Double`/`Float` za iznose.                                                                                                                             |
| Period budžeta  | **Od plate do plate.** Novi period počinje kad stigne priliv označen kao plata.                                                                                                                                |
| Kategorije      | Predložene na startu (lista dolje). Vlasnik ih preimenuje, dodaje i briše.                                                                                                                                     |
| Gotovina        | Podizanje sa bankomata = trošak u kategoriji „Gotovina". Nema ručnog unosa gotovinskih troškova.                                                                                                               |
| Interni prenosi | Rijetki. Vlasnik ručno označi transakciju kao „Prenos" i ona se ne računa ni u troškove ni u prilive. Bez automatskog prepoznavanja.                                                                           |
| Početni ekran   | Glavni broj: „Danas smiješ da potrošiš X €" do sljedeće plate.                                                                                                                                                 |
| AI              | Claude API direktno iz aplikacije, sa ključem vlasnika. Koristi se za: (1) kategoriju nepoznatog trgovca, (2) automatsku analizu na kraju perioda.                                                             |
| Backup          | Android Auto Backup (Google Drive). Bez sopstvenog koda za backup.                                                                                                                                             |
| Grafikoni       | Obični Compose (trake preko `Box`/`Canvas`). Bez biblioteke za grafikone.                                                                                                                                      |

## Arhitektura

```
Notifikacija (Wallet / Banka A / Banka B)
  → Listener: propušta samo poznate izvore i odmah upisuje originalni tekst u raw_notifications
  → Parser (jedan po izvoru): iznos, valuta, trgovac, vrijeme, tip (trošak / priliv / podizanje)
       ne uspije → raw_notifications.status = FAILED i vidi se u „Za provjeru"
  → Dedup: isti iznos + ista valuta + DRUGI izvor + ±10 min → jedna transakcija
  → Kategorija: merchant_rules; ako pravila nema → AI prijedlog (nepotvrđen)
  → transactions
  → Budžet: provjera limita (80% / 100%) → lokalna notifikacija
```

- Obrada ide odmah u listeneru (coroutine), ne čeka otvaranje aplikacije.
- AI pozivi idu preko WorkManager-a sa uslovom „ima interneta", pa se sami ponove ako nema mreže.
- Originalna notifikacija se **uvijek** sačuva prije parsiranja. Ako se format poruke banke promijeni, popravimo parser i pustimo „Obradi ponovo", pa se ništa ne izgubi.

## Podaci (Room)

| Tabela              | Ključna polja                                                                                                                                                 |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `raw_notifications` | id, package, title, text, postedAt, status (NEW / PARSED / FAILED / IGNORED), transactionId?                                                                  |
| `transactions`      | id, amountMinor, currency, amountEurMinor?, type (EXPENSE / INCOME / TRANSFER), merchant, categoryId?, categoryConfirmed, isSalary, occurredAt, source, note? |
| `categories`        | id, name, limitEurMinor? (null = bez limita), isIncome                                                                                                        |
| `merchant_rules`    | merchantKey, categoryId                                                                                                                                       |
| `analyses`          | id, periodStart, periodEnd (epoch day, kraj isključen), budgetMinor, state (LOADING / READY / FAILED), json (AI odgovor), error, keyProblem, createdAt      |

- Baza je v2 (`AutoMigration` 1→2, sheme u `app/schemas`, kategorije se upisuju pri migraciji i pri prvom kreiranju). Svaka sljedeća promjena sheme: podigni verziju i dodaj `AutoMigration`, **nikad** destruktivnu migraciju (na telefonu su stvarni podaci).
- Podešavanja (budžet, pravilo za platu, API ključ, kursevi `rate_XXX`, poslata upozorenja) su u SharedPreferences „budzet" (`data/Settings.kt`).
- Valute se čuvaju kao ISO kod (`BAM`), a prikazuju kao `KM` (`displayCurrency`).

- Više notifikacija (Wallet + banka) može pokazivati na istu transakciju preko `raw_notifications.transactionId`.
- `amountEurMinor` je null kad za valutu nema kursa. Takva transakcija ide u „Za provjeru".
- `merchantKey` je normalizovan naziv trgovca: velika slova, bez brojeva, filijale i grada, bez razmaka na krajevima. Tako „MAXI 123 BEOGRAD" i „Maxi 456" padaju pod isto pravilo.

## Poslovna logika

**Dedup.** Spajaju se samo transakcije iz _različitih_ izvora, pa dvije kafe od 2,50 € preko Walleta ostaju dvije transakcije. Kod spajanja se trgovac uzima iz Walleta (čistiji naziv), a sve ostalo iz banke.

**Plata i period.**

- U podešavanjima postoji „pravilo za platu": izvor (banka) + minimalni iznos. Priliv koji ga ispuni dobija `isSalary = true` i započinje novi period.
- Na bilo kom prilivu postoji i ručna akcija „Označi kao platu".
- Prije prve plate, period počinje danom instalacije.
- Očekivani kraj perioda = datum posljednje plate + trajanje prethodnog perioda. Za prvi period: +1 mjesec.

**Budžet perioda** (odluka vlasnika 02.10.2026, zamjenjuje raniji „zbir limita"): vlasnik upiše jedan iznos za period (ekran Budžet, „Budžet po periodu"). Limiti po kategorijama su raspodjela tog iznosa i služe za upozorenja; ostatak („nije raspoređeno") pokriva kategorije bez limita.

**Dnevni limit** = `(budžet perioda − sva potrošnja u periodu do juče) / broj preostalih dana do očekivane plate, uključujući danas`; od toga se oduzima današnja potrošnja.

- Računa se svaki trošak osim prenosa, plate i ostalih prihoda. Transakcije bez kursa se ne računaju dok se ne riješe.
- Fiksne troškove (stanarina) vlasnik uračuna u budžet perioda.
- Ako je današnja potrošnja veća od dnevnog iznosa, hero prikazuje koliko je danas prekoračeno; razlika se raspoređuje na preostale dane.

**Upozorenja.** Za svaku kategoriju sa limitom stiže jedno upozorenje na 80% i jedno na 100% limita, jednom po periodu.

**Kurs.** Kursevi se unose ručno u podešavanjima (valuta → koliko jedinica za 1 EUR). KM je fiksan: 1,95583. U kodu stoji `// ponytail: ručni kurs; automatsko preuzimanje ako razlika postane primjetna`.

## Predložene kategorije

- **Troškovi:** Market · Restorani i kafići · Dostava · Gorivo i prevoz · Režije · Stanovanje · Zdravlje · Odjeća · Zabava · Pretplate · Putovanja · Gotovina · Ostalo
- **Prihodi:** Plata · Ostali prihodi

## Claude API

- Koristi se zvanični Java SDK `com.anthropic:anthropic-java`, koji radi i iz Kotlina. **Prije pisanja koda učitaj skill `claude-api`** i uzmi tačne nazive klasa odatle. Ne pogađaj ih.
- Model je `claude-opus-5-5`, sa uključenim server-side fallback-om (`fallbacks: "default"`). Uvijek provjeri `stop_reason` prije čitanja odgovora.
- **Kategorija nepoznatog trgovca:**
  - effort `low`, structured output `{ "category": ... }` (enum naziva kategorija)
  - rezultat je prijedlog (`categoryConfirmed = false`) dok ga vlasnik ne potvrdi ili ispravi
  - svaka ispravka pravi ili mijenja `merchant_rules`
- **Analiza perioda:**
  - pokreće je detekcija nove plate (WorkManager, uslov: internet)
  - šalje se **samo**: zbir po kategorijama, top 15 trgovaca sa iznosima, poređenje sa prethodnim periodom i limiti
  - **nikad** se ne šalje: sirovi tekst notifikacija, stanje računa, broj kartice
  - odgovor je na srpskom (ijekavica) i konkretan: gdje je potrošeno više nego inače, šta se ponavlja (pretplate) i 3 prijedloga za uštedu sa procjenom iznosa
  - rezultat se čuva u `analyses`, a stiže i notifikacija „Analiza perioda je spremna"
- API ključ se čuva u privatnim SharedPreferences. Za komercijalnu verziju ključ mora na server, ali to radimo tek tada.

## Ekrani

1. **Početna:** dnevni limit, broj dana do plate, potrošeno / budžet perioda, trake po kategorijama, posljednje transakcije, značka „Za provjeru (N)".
2. **Transakcije:** lista po danima. Izmjena: kategorija, trgovac, „Prenos", „Plata", brisanje. Dugme „+" otvara isti ekran za ručni unos.
3. **Izvještaji:** po kategorijama, top trgovci, poređenje sa prethodnim periodom.
4. **Budžet:** limiti po kategorijama, dodavanje i preimenovanje kategorija.
5. **Analize:** lista AI analiza po periodima.
6. **Za provjeru:** neprepoznate notifikacije, nepotvrđene AI kategorije, transakcije bez kursa.
7. **Podešavanja:** API ključ, kursevi, pravilo za platu, status dozvola, sirovi log notifikacija, „Obradi ponovo sve".

**Prvo pokretanje (uvod):**

- dozvola za čitanje notifikacija
- dozvola za slanje notifikacija (Android 13+)
- isključenje optimizacije baterije
- **Važno:** za aplikaciju instaliranu mimo Play Store-a, Android 13+ blokira čitanje notifikacija dok se u _Info o aplikaciji → ⋮ → Dozvoli ograničena podešavanja_ to ne odobri. Uvodni ekran mora to objasniti korak po korak.

## Faze

Svaka faza se završava aplikacijom koja se instalira na telefon i radi.

| #   | Faza                                                                                                                                                                               | Gotovo kad                                                                                                                                        |
| --- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| 0   | Projekat: Gradle wrapper, Compose, Room, prazan ekran                                                                                                                              | `gradlew installDebug` radi na telefonu                                                                                                           |
| 1   | Listener + `raw_notifications` + ekran sirovog loga + uvod za dozvole. **Privremeno bilježi notifikacije svih aplikacija**, da saznamo nazive paketa banaka i tačan format poruka. | Vlasnik nekoliko dana normalno koristi telefon i imamo stvarne primjere za Wallet, banku A i banku B: plaćanje karticom, online, bankomat, priliv |
| 2   | Parseri po izvoru (testovi na stvarnim primjerima iz faze 1), filter samo na poznate izvore, dedup, kursevi, `transactions`, brisanje logova drugih aplikacija                     | Stari log se ponovo obradi bez grešaka i testovi prolaze                                                                                          |
| 3   | Kategorije, `merchant_rules`, lista i izmjena transakcija, ručni unos, „Za provjeru"                                                                                               | Ispravka kategorije se pamti za tog trgovca                                                                                                       |
| 4   | Plata i periodi, limiti, dnevni limit na početnoj, upozorenja 80% / 100%                                                                                                           | Dnevni limit je tačan na testnim podacima                                                                                                         |
| 5   | Izvještaji                                                                                                                                                                         | Brojevi se slažu sa listom transakcija                                                                                                            |
| 6   | Claude: kategorija nepoznatog trgovca + analiza na kraju perioda                                                                                                                   | Nakon plate stigne notifikacija sa analizom                                                                                                       |
| 7   | Pouzdanost: rad nakon restarta telefona, baterija, provjera vraćanja iz Auto Backup-a                                                                                              | Sedmicu radi bez propuštenih troškova                                                                                                             |

## Status

- [x] Faza 0 — `installDebug` radi na emulatoru `budget`; na pravi telefon instalirano ručno preko `app-debug.apk` (01.10.2026), `installDebug` preko USB-a nije probano
- [ ] Faza 1 — kod gotov, na telefonu od 01.10.2026, skuplja primjere. Banke šalju tek od ~10.10.2026 (plata).
- [ ] Faza 2 — gotovo sve osim čitača po banci: `tag` u bazi (v2), filter na izvore (`ingest/Pipeline.kt`: Wallet paket + u debug buildu `com.android.shell` sa tagom), dedup Wallet + banka (±10 min, provjereno na emulatoru), kursevi, `transactions`, „Obradi ponovo sve". Privremeno radi **opšti čitač** `parseGeneric` (`ingest/Parser.kt`). **Ostaje poslije 10.10.:** upisati pakete obje banke u `BANK_PACKAGES`, napisati čitač po banci na stvarnim primjerima (fixture testovi), pa brisanje logova drugih aplikacija.
- [x] Faza 3 — kategorije, `merchant_rules` (ispravka se pamti), lista i izmjena, ručni unos (i iz neprepoznate notifikacije), „Za provjeru" (AI, bez kategorije, neprepoznato, bez kursa).
- [x] Faza 4 — periodi od plate do plate (`budget/Budget.kt`), ručna plata („Označi platu ručno"), pravilo za platu, budžet perioda, dnevni limit, upozorenja 80/100% (provjereno na emulatoru).
- [x] Faza 5 — izvještaji računaju iz transakcija (`ui/Report.kt` + test).
- [ ] Faza 6 — kod gotov (`ai/Ai.kt`: kategorija preko WorkManager-a, analiza poslije plate, notifikacija), **nije probano sa pravim API ključem**.
- [ ] Faza 7

**UI:** svi koraci iz `budzet-design/DESIGN.md` gotovi i povezani sa pravim podacima (`ui/Live.kt` pravi stanja ekrana iz Room-a i Settings-a; `ui/App.kt` je navigacija + akcije preko `data/Repo.kt`). `ui/Sample.kt` služi samo previewima. Stanje ekrana (tab, filteri) se čuva pri povratku iz detalja (`rememberSaveableStateHolder`). Notifikacija otvara ekran preko extra `route` (`Notify.EXTRA_ROUTE`). Previewi se bez Android Studija gledaju na emulatoru: `adb shell am start -n com.budget.app/androidx.compose.ui.tooling.PreviewActivity --es composable com.budget.app.ui.PocetnaKt.PocetnaNormalPreview` (tamno: `adb shell cmd uimode night yes`, font: `adb shell settings put system font_scale 2.0`, visina: `adb shell wm size 1080x5600`, pa `wm size reset`).

**Otvoreno** (saznaje se u fazi 1): nazivi paketa obje banke, da li banke šalju push ili SMS, da li Wallet notifikacija sadrži iznos.

## Kako raditi na projektu

- **Build bez Android Studija.** SDK je u `%LOCALAPPDATA%\Android\Sdk` (platforme 35/36, build-tools 35/36), Java 23, `adb` postoji.
  - Komande: `./gradlew assembleDebug`, `./gradlew installDebug`, `./gradlew test`.
- `minSdk 26`, `compileSdk`/`targetSdk 36`. Jedan modul `app`. `applicationId` = `com.budget.app`.
- Verzije su upisane direktno u `build.gradle.kts` (bez version catalog-a): Gradle 9.8.0, AGP 9.4.1 (ima ugrađen Kotlin, bez `kotlin-android` plugina), Kotlin 2.4.20, KSP 2.3.12, Room 2.8.5. **Compose BOM je namjerno 2026.06.01**: od 2026.08.00 Compose 1.12 traži `compileSdk 37`. Ne dizati BOM dok ne pređemo na SDK 37.
- **Emulator** `budget` (Pixel 8, API 36, WHPX radi): `%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd budget -gpu swiftshader_indirect` (bez `-gpu swiftshader_indirect` ekran emulatora zna da pocrni, a System UI javlja „isn't responding"). Kad se podigne, `./gradlew installDebug` ga koristi kao i telefon. Svojstvo za kraj dizanja je `sys.boot_completed` (sa donjom crtom).
- Gradle se pokreće sa `JAVA_HOME` = JDK 17 (već postavljen), ne sa Javom 23 sa PATH-a.
- **Paketi:** `data` (Room), `ingest` (listener, parseri, dedup), `budget` (periodi, limiti, dnevni limit), `ai` (Claude), `ui` (ekrani). Što manje fajlova.
- **Testovi:** JVM unit testovi za svu logiku koja dira novac ili parsiranje (parseri, dedup, periodi, dnevni limit, kurs). Primjeri notifikacija iz faze 1 su fixture-i, sa anonimizovanim brojevima kartica i stanjem.
- **Testiranje listenera bez plaćanja:** `adb shell "cmd notification post -S bigtext -t '<naslov>' <izvor> '<tekst>'"`. **Cijela komanda mora ići u jedne navodnike za `adb shell`**, inače Windows razbije naslov i tekst na više argumenata. Listener se na emulatoru uključuje sa `adb shell cmd notification allow_listener com.budget.app/com.budget.app.ingest.NotifListener`. U **debug** buildu notifikacije iz `com.android.shell` obrađuju se kao da dolaze iz izvora navedenog u tagu (`wallet` / `bankA` / `bankB`).
- **Stil:** najjednostavnije rješenje koje radi. Bez apstrakcija „za kasnije" i bez novih biblioteka ako par redova rješava stvar.
- **Ne dodavati:** server, naloge, iOS, sinhronizaciju, `READ_SMS`.
