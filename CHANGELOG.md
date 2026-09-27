# Changelog

Alle wijzigingen per versie. De versies staan in `firmware/Timer_ZZ_Vx/` en als git-tags `v1` t/m `v7`.

---

## V7 — herhaalmodus en tweede groot display

- **Optelmodus vervallen** (`countingUp` verwijderd). De timer telt altijd af vanaf 5:00; ook de beginstand in rust is altijd 5:00.
- **Nieuwe betekenis modusschakelaar** via `repeatMode`, ingelezen bij start:
  - `HIGH` (open) → herhalen: na 0:00 direct verder vanaf 4:59, `triggeredRelay[]` wordt gewist, elke 5 minuten een nieuwe cyclus tot reset;
  - `LOW` (dicht) → één keer: stoppen na 0:00.
  - Het commentaar in de code zegt het omgekeerde; de beschrijving hierboven volgt de code.
- **Signaalcontrole verplaatst** uit de seconde-tik naar een apart blok dat elke loop-doorgang draait zolang de timer loopt. Signaalduur ongewijzigd (0,5 s / 1 s).
- `0:00` blijft bij de eenmalige modus 1 s zichtbaar, omdat de timer pas bij de volgende tik stopt.
- **Tweede groot display:** `updateShiftRegisterDisplay()` schuift de drie cijfers twee keer in (zes registers); `clearShiftRegisterDisplay()` wist zes registers.
- Handmatige hoorn toegestaan in rust en tijdens 5:00–4:00 van elke cyclus.
- Pinout gelijk aan V5/V6.

## V6 — instelbare relaisduur

- `activateRelayNonBlocking()` krijgt een parameter `duration` (standaard 500 ms); nieuwe variabele `relayDuration`.
- Signaalduur:
  - start / overgang optellen → aftellen: 0,5 s
  - 4:00: 0,5 s
  - 1:00 en 0:00: 1 s
- De relais-timeout gebruikt `relayDuration` in plaats van vast 2000 ms.
- `activateRelayNonBlocking()` staat nu vóór `setup()`, zodat de standaardwaarde van de parameter bekend is waar de functie wordt aangeroepen.
- Overige logica gelijk aan V5.

## V5 — reset direct, handmatige hoorn in eerste minuut

- **Pinwissel:** `horn_button` 21 → 22, `timer_switch` 22 → 21.
- **Reset reageert direct bij indrukken** (V4: pas bij loslaten). Vasthouden voor slaapmodus werkt nog steeds.
- **Handmatige hoorn ook toegestaan tijdens het aftellen tussen 5:00 en 4:00** (`inAllowedManualRange`). Tijdens optellen en na 4:00 is hij geblokkeerd.
- `horn_button` is `LOW` wanneer de handmatige hoorn is toegestaan (rust of 5:00–4:00), anders `HIGH`.
- Automatische slaap na 360000 ms (6 minuten; commentaar zegt nog 5).
- Relaisduur nog vast 2 s.

## V4 — debouncing, slaapmodus, nieuwe optel-modus

- **`Bounce2`-debouncing** (25 ms) voor start, reset en handmatige hoorn; interrupts vervallen.
- **Slaapmodus**:
  - reset 3 s vasthouden in rust → displays uit, enable matrix `LOW`;
  - reset indrukken (≥ 100 ms) in slaapmodus → wakker;
  - automatisch na 300000 ms (5 minuten) inactiviteit.
- Korte reset wordt uitgevoerd bij **loslaten** van de knop (zodat vasthouden niet eerst reset).
- **Nieuwe betekenis modusschakelaar** `timer_switch`:
  - `LOW` → start direct met aftellen vanaf 5:00;
  - `HIGH` → eerst optellen van 0:00 tot 4:00, daarna automatisch naar 5:00 met hoornsignaal en verder aftellen.
- Aftellen stopt bij 0:00 (geen optellen meer na 0:00).
- In rust volgt de beginstand live de modusschakelaar.
- **Driftvrije tijdbasis:** `previousMillis += 1000` in plaats van `previousMillis = currentMillis`.
- Handmatige hoorn alleen in rust.
- `horn_button` omgekeerd t.o.v. V3: `LOW` in rust, `HIGH` tijdens lopen.
- Enable-pin hernoemd naar `enable_matrix`; schuifregisterfuncties naar onderen verplaatst.

## V3 — groot display en modusschakelaar

- **Schuifregister-display** ("matrix board") op pinnen 33/32/26 met enable op 25. Drie cijfers `M.SS`.
- `mapSegments()` vertaalt segmenten a–g en dp naar de bedrading van het board; tabel `digits[10]`.
- Beide displays worden tegelijk bijgewerkt.
- **Modusschakelaar** `timer_switch` (pin 22): bepaalt of het optellen na 0:00 stopt bij 4:00 (`HIGH`) of bij 5:00 (`LOW`).
- **Statusuitgang** `horn_button` (pin 21): `HIGH` wanneer de handmatige hoorn is toegestaan (rust of optellen), anders `LOW`.
- Knipperen bij 0:00 vervangen door kort vast `0:00` tonen (drempel 1500 ms; omdat dit alleen bij de seconde-tik wordt gecontroleerd, staat `0:00` er in de praktijk ca. 2 s en wordt `0:01` overgeslagen).
- Start zet `previousMillis` opnieuw, zodat de eerste seconde een volle seconde is.
- Seriële debug-uitvoer (115200 baud) bij elke relaisactivering.

## V2 — nieuwe pinout, optellen na 0:00

- **Nieuwe pinout:** start 23, reset 13, handmatige hoorn 14, relais 27.
- Na 0:00 **optellen** tot 4:00, daarna stopt de timer.
- Bij 0:00 knippert `00:00` gedurende 3 s (om de 500 ms aan/uit).
- Start tijdens een lopende procedure wordt genegeerd.
- Relaissignaal ook gecontroleerd op 5 minuten.
- Relais niet meer direct geschakeld in `activateRelayNonBlocking()`; de uitgang wordt aan het eind van `loop()` bepaald uit automatisch + handmatig signaal.
- Handmatige hoorn bedoeld alleen in rust of tijdens optellen.
- Bekend: compileert niet (bereik van `manualRelayActive`).

## V1 — basisversie

- ESP32 met TM1637-display (CLK 18, DIO 19).
- Start (34) en reset (35) via interrupts op dalende flank.
- Aftellen 5:00 → 0:00, daarna stoppen.
- Relais (16) 2 s aan bij start, 4:00, 1:00 en 0:00, niet-blokkerend via `millis()`.
- Bekend: compileert niet (lege `#define manual_relay`); GPIO 34/35 hebben geen interne pull-ups.
