# V11: robuust voor wedstrijdgebruik

V11 is V10 (PWM-relais, nieuw matrixboard) met een opbouw die ervoor zorgt dat de timer nooit stopt of hapert door WiFi of de app. De bediening, de hoornmomenten en het protocol met de app zijn ongewijzigd.

## Gevonden risico in V8–V10

In V8–V10 draaide de WiFi-code (`webSocket.loop()` en het versturen van de status) in dezelfde `loop()` als de timer. De WebSocket-bibliotheek wacht bij het versturen tot **5 seconden** (`WEBSOCKETS_TCP_TIMEOUT`) als een telefoon niet meer reageert, bijvoorbeeld omdat hij uit bereik loopt zonder de verbinding netjes te sluiten. Gedurende die tijd:

- stond het display stil;
- kwam een hoornsignaal tot 5 s te laat;
- kon de toeter tot 5 s blijven klinken, omdat het uitzetten ook moest wachten.

Daarna klopte de tijd weer (absolute tijdbasis), maar tijdens een wedstrijd is dit niet acceptabel.

## Wat V11 anders doet

| Maatregel | Wat het voorkomt |
|---|---|
| **Netwerk in een eigen taak op kern 0**; de timer (knoppen, tijd, hoorn, displays) blijft in `loop()` op kern 1. App-commando's gaan via een wachtrij; de timer kijkt er alleen naar, wacht er nooit op. | WiFi of de app kan de timer niet meer vertragen of laten vastlopen. |
| **Watchdog alleen op de timerlus** (3 s). Het netwerk valt er bewust niet onder. | Een vastgelopen timer herstart automatisch. Een vastgelopen netwerk laat de ESP niet herstarten; de timer loopt dan gewoon door, alleen de app werkt niet. |
| **Herstel na een herstart door een fout** (crash, watchdog, spanningsdip). De toestand (procedure loopt, modus, hoornsignalen, starttijd) staat in RTC-geheugen, dat een herstart overleeft. De starttijd is vastgelegd in de systeemklok, die via de RTC-timer doorloopt tijdens de herstart. | Na een herstart gaat de procedure verder met de juiste tijd. Een hoornsignaal dat tijdens de herstart had moeten klinken, wordt alsnog gegeven (enkele tienden van een seconde laat). |
| **Beveiliging tegen een herstartlus**: meer dan 3 herstarts binnen een minuut → schoon beginnen. | Als de opgeslagen toestand zelf de fout veroorzaakt, blijft de ESP niet eindeloos herstarten. |
| **Geen flash-schrijfacties tijdens een procedure**. Wijzigingen in geplande starts worden pas opgeslagen als de timer stilstaat; PIN en WiFi wijzigen kan dan niet. | Een flash-schrijfactie legt beide kernen kort stil (tientallen ms). |
| **Geen `String`-opbouw in de lus**; status en antwoorden gaan via vaste buffers. | Geheugenfragmentatie bij urenlang gebruik. |
| **Hoorn eerst uit** als allereerste stap na het opstarten. | Hoorn die blijft klinken na een herstart. |
| **Klok voor geplande starts blijft behouden** na een herstart door een fout. | Geplande starts werken na een crash zonder dat de app opnieuw hoeft te verbinden. |

Na het in- en uitschakelen van de stroom begint de timer altijd schoon (zoals voorheen). Ook na de resetknop op het ESP32-bord (EN) wordt niets hersteld, omdat dat een bewuste actie is.

## Status in de app

Het statusbericht bevat drie extra velden (de app negeert ze nog):

| Veld | Betekenis |
|---|---|
| `reset_reason` | Laatste herstart: `aan`, `resetknop`, `software`, `crash`, `watchdog`, `spanningsdip` |
| `restored` | `true` als de toestand na een herstart is hersteld |
| `heap_free` | Vrij werkgeheugen in bytes (moet over uren stabiel blijven) |

## Geen seriële uitvoer

V11 gebruikt de seriële poort niet: geen `Serial.begin()`, geen meldingen en geen testcommando's. Er gaat dus geen processortijd naar. De reden van de laatste herstart is zichtbaar via de app-status (`reset_reason`, `restored`).

## Grenzen

- **Herstart duurt kort:** tijdens de herstart (ongeveer 0,5–1 s) staan de displays even stil en wordt er geen PWM-signaal naar het relais gestuurd. Wat het RC-relais dan doet (laatste stand vasthouden of uit), hangt af van het module. Controleer dat met `test-crash` terwijl de toeter klinkt.
- **Tijd tijdens de herstart:** die wordt gemeten met de interne RTC-klok van de ESP32. Die is minder nauwkeurig dan het kristal, maar over een herstart van ongeveer een seconde is de afwijking hooguit enkele tientallen milliseconden.
- **Stroomonderbreking:** software kan een stroomonderbreking niet opvangen. Gebruik een stabiele voeding en ontkoppel de hoorn goed van de ESP-voeding, zodat de hoorn geen spanningsdip op de ESP geeft.
- **Niet op hardware getest:** V11 is gecompileerd (937 122 bytes, 71% flash) maar nog niet op hardware getest. Het herstel na een crash is zonder testhaak niet bewust op te wekken; test vóór een wedstrijd in elk geval een volledige procedure met de app aan en uit, en laat de telefoon tijdens een procedure buiten bereik lopen.
