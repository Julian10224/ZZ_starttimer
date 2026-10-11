# V7A: V7 met de app-functionaliteit

V7A is V7 (twee grote displays, normaal relais, herhaalmodus) met dezelfde app-functionaliteit als op de branch `tonnie`. De Android-app *ZZ Wedstrijd Timer* werkt ongewijzigd met V7A: het protocol is gelijk (zie [PROTOCOL.md](PROTOCOL.md)).

## Wat gelijk is aan V7

| Onderdeel | V7 / V7A |
|---|---|
| Pinnen | Gelijk aan V5–V7 |
| Hoornrelais (GPIO 27) | Normaal relais: `HIGH` = aan, `LOW` = uit |
| Grote displays | Twee displays, 6 schuifregisters in de keten, mapping van V3–V7, decimale punt aan |
| Enable displays (GPIO 25) | `HIGH` = aan, `LOW` = uit |
| Lampje toeterknop (GPIO 22) | `LOW` = handmatige toeter toegestaan, `HIGH` = geblokkeerd |
| Procedure | START → 5:00, hoorn op 5:00 en 4:00 (0,5 s), 1:00 en 0:00 (1 s) |
| Modusschakelaar | Open = herhalen, gesloten = één keer (ingelezen bij START) |
| Reset / slaapmodus | Kort = terug naar 5:00, 3 s vasthouden in rust = displays uit, automatisch na 6 min |
| Handmatige toeter | In rust en tijdens 5:00–4:00 |

## Wat er bij komt (uit de branch `tonnie`)

- **WiFi access point en WebSocket** voor de app: standaard netwerk `ZZ-WedstrijdTimer`, wachtwoord `ZZstart2026`, IP `192.168.4.1`, poort 81.
- **Bediening vanuit de app:** START, STOP/RESET, LED-paneel aan/uit en de toeterknop, met dezelfde regels als de fysieke knoppen.
- **Geplande starts** (maximaal 20), opgeslagen op de ESP en zelfstandig uitgevoerd.
- **Beheerders-PIN** (standaard `1234`, opgeslagen als gezouten SHA-256) voor de instellingen, WiFi en PIN wijzigen.
- **Timer op absolute tijd** (`startMillis`), zodat de app exact dezelfde tijd toont en gemiste seconden worden ingehaald.
- **Relais-timeout-fout van V2–V7 opgelost:** een automatisch hoornsignaal kon in zeldzame gevallen wegvallen.
- **Robuuste opbouw** (zoals V11/V12): netwerk in een eigen taak op kern 0, watchdog op de timerlus (3 s), herstel van een lopende procedure na een crash, geen flash-schrijfacties tijdens een procedure.

De code voor de app, de geplande starts en de robuustheid is overgenomen uit V12. Alleen de hardware-specifieke delen (PWM-relais, 3-digit matrixboard met actief-lage enable, andere segmentmapping, omgekeerd lampje) zijn teruggezet naar V7.

## Firmwareversie

De app toont de firmwareversie `V7A`. De naam V8 is niet gebruikt, omdat de tag `v8` al bestaat voor V8 op de branch `tonnie`.

## Bibliotheken

Naast TM1637 1.2.0 en Bounce2: **WebSockets** (Markus Sattler, 2.7.2) en **ArduinoJson** (7.4.3). `WiFi`, `Preferences` en `mbedtls` zitten in de ESP32-core (3.3.12).

## Testen

V7A is nog niet op hardware getest. Volg [TESTPLAN.md](TESTPLAN.md) en controleer in elk geval:

1. Beide grote displays tonen dezelfde, juiste tijd.
2. De hoorn klinkt op 5:00, 4:00, 1:00 en 0:00, en niet bij het aanzetten.
3. Het lampje in de toeterknop brandt zoals bij V7.
4. Een volledige procedure met de app aan en uit, en met de telefoon buiten bereik tijdens de procedure.
