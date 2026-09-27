# Technische analyse en wijzigingen V8

## 1. Hoe V7 werkt (uitgangspunt)

- ESP32 met TM1637-display (`MM:SS`), twee grote schuifregister-displays (`M.SS`), hoornrelais, knoppen START / RESET / handmatige hoorn en een modusschakelaar.
- Procedure: START → 5:00 aftellen, hoornsignalen op 5:00 (0,5 s), 4:00 (0,5 s), 1:00 (1 s) en 0:00 (1 s).
- Modusschakelaar, ingelezen bij START: open (`HIGH`) = herhalen (na 0:00 verder vanaf 4:59), gesloten (`LOW`) = één keer.
- Reset: kort indrukken = terug naar 5:00; 3 s vasthouden in rust = slaapmodus (alle displays uit, hier "LED-paneel uit"); indrukken in slaapmodus = weer aan. Automatische slaap na 6 minuten zonder activiteit.
- Handmatige hoorn: in rust en tijdens 5:00–4:00.
- Geen WiFi, geen klok met absolute tijd (geen RTC).

## 2. Controle op tijdnauwkeurigheid

| Onderdeel | V7 | Oordeel |
|---|---|---|
| `delay()` | Niet gebruikt | Goed |
| Seconde-tik | `previousMillis += 1000`, gekoppeld aan het startmoment | Loopt niet weg; een vertraagde tik wordt ingehaald |
| `seconds--` | Telt het aantal verwerkte tikken, maar de tikken zelf hangen aan `millis()` | Geen tijdverlies, maar de spec vraagt expliciet een absolute berekening |
| Interrupts | Niet (Bounce2 in `loop()`) | Goed |
| Display-verversing | TM1637 bit-bang, enkele ms per keer | Vertraagt alleen de weergave, niet de tijd |
| Relais-timeout | `currentMillis - relayStartMillis` | **Fout**, zie hieronder |
| Kristal ESP32 | Enkele tientallen ppm (schatting) | Orde 10 ms per 5 minuten |

### Gevonden fout in V2–V7: hoornsignaal kan wegvallen

`currentMillis` wordt aan het begin van `loop()` gelezen. `activateRelayNonBlocking()` zet later in dezelfde doorgang `relayStartMillis = millis()`. Valt daartussen een milliseconde-grens, dan is `relayStartMillis` één groter dan `currentMillis`. De aftrekking `currentMillis - relayStartMillis` loopt dan over naar 4 294 967 295, is dus groter dan de relaisduur, en het relais wordt in dezelfde doorgang weer uitgezet. Het hoornsignaal klinkt dan niet.

De kans per signaal is klein, want de code tussen het begin van de loop en de activering duurt maar enkele tientallen microseconden, maar niet nul. Hij geldt voor elk automatisch signaal: start, 4:00, 1:00 en 0:00. In V7 is deze fout niet aangepast; in V8 wel (zie 3.2).

## 3. Wijzigingen in V8

Alle bestaande pinnen, displays, knoppen, modi, hoornmomenten en de slaapmodus zijn ongewijzigd. Nieuwe code is in de sketch gemarkeerd met `V8`.

### 3.1 Timer op absolute tijd

- Bij START wordt `startMillis` vastgelegd. Elke loop-doorgang berekent de firmware `k = (millis() - startMillis) / 1000`, het aantal hele seconden sinds START.
- `stepSecond(k)` rekent de weergave uit `k` uit: `k = 0` → 5:00, `k = 300` → 0:00, `k = 301` → stoppen (één keer) of 4:59 (herhalen, elke 300 s een nieuwe cyclus).
- Is de ESP even bezig geweest, dan worden alle gemiste seconden direct ingehaald, inclusief hoornsignalen. De weergave springt meteen naar de juiste tijd.
- **Waarom:** V7 liep niet achter, maar de app heeft een vaste starttijd nodig om exact dezelfde tijd te kunnen tonen. Bovendien vraagt de specificatie om een absolute tijdreferentie.
- **Controle:** een simulatie van V7 en V8 naast elkaar over 1000 seconden geeft een identieke weergave en identieke hoornmomenten, zowel eenmalig als in herhaalmodus.

### 3.2 Relais-timeout

- `currentMillis - relayStartMillis` is vervangen door `millis() - relayStartMillis`, zodat de aftrekking niet meer kan overlopen (zie 2).

### 3.2b Automatische slaap na een app-commando

- App-commando's worden verwerkt in `webSocket.loop()`, ná het lezen van `currentMillis`. Zet zo'n commando `lastActiveMillis = millis()`, dan kan die 1 ms of meer groter zijn dan `currentMillis`. De slaapcheck `currentMillis - lastActiveMillis >= 360000` liep dan over en zette het net via de app aangezette LED-paneel direct weer uit. Om dezelfde reden kon een geplande start vanuit de slaapmodus mislukken.
- Opgelost door met teken te vergelijken: `(long)(currentMillis - lastActiveMillis) >= 360000`. Deze fout zat alleen in de eerste versie van V8, niet in V7.

### 3.3 WiFi access point en WebSocket

- De ESP start een eigen WiFi-netwerk (standaard `ZZ-WedstrijdTimer`, wachtwoord `ZZstart2026`, IP `192.168.4.1`). Er is geen router nodig.
- De WebSocket-server op poort 81 gebruikt JSON (zie [PROTOCOL.md](PROTOCOL.md)).
- De ESP stuurt de status bij elke wijziging en minimaal elke seconde. Daardoor ziet de app ook fysieke bediening direct.
- WiFi draait op de tweede processorkern van de ESP32. Omdat de tijd uit `millis()` wordt berekend, kan WiFi-verkeer de timer niet vertragen.

### 3.4 Bediening vanuit de app

| App | Doet op de ESP |
|---|---|
| START | Zelfde als de startknop. Geweigerd met melding als het LED-paneel uit staat of de procedure al loopt. |
| STOP / RESET | Zelfde als kort op reset drukken (STOP = reset). |
| LED-paneel uit | Zelfde als reset 3 s vasthouden; alleen als de timer niet loopt (net als fysiek). |
| LED-paneel aan | Zelfde als reset indrukken in slaapmodus. |
| Modus | Alleen weergave. De fysieke schakelaar bepaalt de modus. |
| Toeter (ingedrukt houden) | Zelfde als de handmatige toeterknop, met dezelfde toegestane momenten. Veiligheid: de app moet het commando elke 200 ms herhalen, anders gaat de toeter na 600 ms uit; ook uit bij verbroken verbinding. |

### 3.5 Geplande starts

- De ESP heeft geen klok met datum en tijd. Bij elke verbinding (en daarna elke 30 s) meet de app vijf keer de rondreistijd en stuurt het verschil tussen de telefoonklok en de ESP-klok (`settime`). De snelste meting telt, zodat de onzekerheid op het lokale netwerk maar enkele milliseconden is.
- Een geplande start wordt op de ESP opgeslagen (flash, maximaal 20) en daar zelfstandig uitgevoerd; de app hoeft niet open te zijn.
- Per geplande start kies je:
  - **Startschot om T:** START om T − 5:00, zodat 0:00 precies op T valt;
  - **Procedure start om T:** START om T.
- De starttijd wordt exact het geplande moment (`startMillis` = gepland tijdstip), ook als de loop een paar ms later reageert.
- Staat het LED-paneel uit, dan zet een geplande start het eerst aan.
- Loopt er al een procedure, of is het moment meer dan 2 s gemist (bijvoorbeeld door een stroomonderbreking), dan wordt de geplande start overgeslagen en krijgt de app een melding.
- **Beperking:** na een stroomonderbreking kent de ESP de tijd niet meer. De geplande starts blijven bewaard, maar worden pas weer uitgevoerd nadat de app één keer heeft verbonden.

### 3.6 Instellingen en beveiliging

- De SSID en het wachtwoord zijn te wijzigen vanuit de app. Ze worden opgeslagen in het flashgeheugen (`Preferences`) en direct toegepast zonder herstart; de timer loopt door. Tijdens een lopende procedure is wijzigen geweigerd.
- **Beheerders-PIN (standaard `1234`):** nodig voor de instellingen en voor het wijzigen van WiFi of PIN. De controle gebeurt op de ESP, dus je kunt hem niet omzeilen door de app aan te passen.
- **Opslag van de PIN:** de ESP bewaart hem als gezouten SHA-256-hash, niet als leesbare tekst. De app bewaart de PIN niet; hij staat alleen in het geheugen zolang de instellingen open zijn.
- **Pogingen:** na 5 onjuiste pogingen worden de instellingen 60 s geblokkeerd.
- **Wachtwoord:** het WiFi-wachtwoord wordt niet naar de app teruggestuurd; bij wijzigen vul je een nieuw wachtwoord in.

### 3.7 Nieuwe bibliotheken

| Bibliotheek | Versie | Waarvoor |
|---|---|---|
| WebSockets (Markus Sattler) | 2.7.2 | WebSocket-server |
| ArduinoJson | 7.4.3 | JSON |

`WiFi`, `Preferences` en `mbedtls` (SHA-256) zitten in de ESP32-core.

V8 is gecompileerd met ESP32-core 3.3.12: 941 991 bytes flash (71%) en 49 304 bytes RAM (15%). Op hardware is het nog niet getest.
