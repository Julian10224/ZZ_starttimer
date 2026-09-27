# Testplan V8 + Android-app

Nodig: de timer met V8, een Android-telefoon met de app en een stopwatch of tweede telefoon met seconden. Noteer per test: geslaagd / niet geslaagd en een eventuele afwijking.

| # | Test | Uitvoering | Verwacht resultaat |
|---|---|---|---|
| 1 | Nauwkeurigheid 5 min | Verbind de app, start via de fysieke knop, laat de procedure 5 minuten lopen. Film of vergelijk het display en de app tegelijk. | ESP-display = app op elk moment (hooguit een fractie van een seconde verschil bij het omslaan). 0:00 valt 300 s na START (±0,1 s met stopwatch). |
| 2 | WiFi weg | Start een procedure, zet op de telefoon WiFi uit, wacht 30 s, zet het weer aan. | De timer loopt ongestoord door en de hoorn klinkt op tijd. De app toont **○ GEEN VERBINDING**, daarna automatisch **● VERBONDEN** met dezelfde tijd als het display. |
| 3 | App sluiten | Start een procedure en sluit de app (ook uit de recente apps). | De timer loopt door. |
| 4 | App opnieuw openen | Open de app tijdens test 3. | Na het splashscreen staat direct de juiste tijd in beeld. |
| 5 | Fysieke START | Druk op de startknop op de timer. | De app toont binnen 1 s "Procedure loopt" en de juiste tijd; TOETER ● AAN tijdens het signaal. |
| 6 | STOP via de app | Druk tijdens een procedure in de app op STOP / RESET. | Het fysieke display springt naar 5:00; de app ook. |
| 7 | LED-paneel fysiek | Houd reset 3 s vast in rust; druk daarna weer kort. | De app toont LED-PANEEL ○ UIT en daarna ● AAN. |
| 8 | LED-paneel via app | Druk in rust op "Zet uit" en daarna "Zet aan". | De displays gaan uit en aan en blijven aan. Tijdens een lopende procedure is "Zet uit" niet beschikbaar. |
| 8b | Geplande start vanuit slaap | Zet het LED-paneel uit en plan een procedure start 2 minuten vanaf nu. | Het paneel gaat op het geplande moment aan en de procedure start. |
| 9 | Geplande start (startschot) | Kies een startschot 7 minuten vanaf nu, bijv. 12:07:00. | Om 12:02:00 (telefoontijd) start de procedure met het 5:00-signaal; 0:00 valt om 12:07:00. |
| 9b | Geplande start (procedure) | Kies een procedure start 3 minuten vanaf nu en sluit de app. | START gebeurt exact op dat tijdstip, zonder app. Open de app daarna: de geplande start is uit de lijst verdwenen. |
| 9c | Gemiste geplande start | Plan een start, start 1 minuut ervoor handmatig een procedure. | De geplande start wordt overgeslagen; de app toont een melding. |
| 10 | Langdurig | Zet de schakelaar op herhalen, start, laat de app 1 uur open. | Na 1 uur toont de app nog steeds exact dezelfde tijd als het display, en de 12 cycli vallen elk precies 5 minuten na elkaar. |
| 11 | Modus | Zet de schakelaar om in rust. | De app toont de nieuwe modus binnen 1 s. |
| 12 | Handmatige hoorn | Druk de hoornknop in rust, tijdens 5:00–4:00 en na 4:00. | Rust en 5:00–4:00: hoorn klinkt en de app toont TOETER ● AAN. Na 4:00 (tot 0:00): geblokkeerd. |
| 12b | Toeter via app | Houd in de app **TOETER · INGEDRUKT HOUDEN** vast in rust, tijdens 5:00–4:00 en na 4:00. Zet daarna tijdens het vasthouden WiFi op de telefoon uit. | Zelfde als test 12. Bij WiFi uit stopt de toeter binnen 0,6 s. |
| 13 | PIN | Open Instellingen met een verkeerde PIN (5×), daarna met `1234`. | Na 5 fouten 60 s geblokkeerd; daarna opent `1234` de instellingen. |
| 14 | WiFi wijzigen | Wijzig in de instellingen SSID en wachtwoord (in rust). | De app meldt "WiFi-instellingen gewijzigd"; de timer toont het nieuwe netwerk. Na opnieuw verbinden werkt alles; na uit/aan zetten van de timer blijft het nieuwe netwerk. |
| 15 | Stroomonderbreking | Plan een start, zet de timer uit en weer aan, open de app. | De geplande start staat nog in de lijst; is het tijdstip intussen voorbij, dan volgt een melding "gemist". |
