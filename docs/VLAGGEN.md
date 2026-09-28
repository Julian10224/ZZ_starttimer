# Vlaggenpagina (app 1.3)

De app heeft een tweede pagina, **Vlaggen**, bereikbaar via de navigatiebalk onderaan (Timer · Vlaggen). Die toont de seinvlaggen van de startprocedure en telt hardop af in het Nederlands, op basis van de tijd van de timer zelf. Het gedrag is gebaseerd op de app *Start Timer* (Leonenko); vlaggen, geluiden en teksten zijn zelf gemaakt en niet overgenomen.

## Verloop volgens regel 26 (RvW)

| Tijd | Sein | Vlaggen | Stem |
|---|---|---|---|
| 10 s vóór 5:00 | – | – | "Tien." … "Eén." (na START in de app of vóór een geplande start) |
| 5:00 | Waarschuwingssein | Klassevlag op | "Waarschuwingssein. Klassevlag op. Nog vijf minuten." |
| 4:10 … 4:01 | – | – | "Tien." … "Eén." |
| 4:00 | Voorbereidingssein | Voorbereidingsvlag op (P, I, Z, U of zwart) | "Voorbereidingssein. P-vlag op. Nog vier minuten." |
| 3:00, 2:00 | – | – | "Nog drie minuten." / "Nog twee minuten." |
| 1:10 … 1:01 | – | – | "Tien." … "Eén." |
| 1:00 | Eén minuut | Voorbereidingsvlag neer | "Nog één minuut. P-vlag neer." |
| 0:50 … 0:20 | – | – | "Vijftig seconden." … "Twintig seconden." |
| 0:10 … 0:01 | – | – | "Tien." … "Eén." |
| 0:00 | Start | Klassevlag neer | "Start! Klassevlag neer." |

- **START in de app** (op beide pagina's) telt eerst 10 seconden af; daarna start de timer met het waarschuwingssein. De app plant die start op de timer zelf (zoals een geplande start), zodat de timer precies op tijd start, ook als de telefoon hapert of de verbinding wegvalt.
- **Geplande starts**: de stem zegt 30 seconden vooraf "Waarschuwingssein over dertig seconden", telt de laatste 10 seconden af en spreekt het waarschuwingssein uit. Dit geldt voor starts uit de lijst op de Timer-pagina en voor de hervatting na uitstel of algemene terugroep. De app moet daarvoor open zijn; de start zelf gebeurt altijd door de timer.
- De **fysieke startknop** op de timer start direct, zonder aftellen.
- **Herhalen** (schakelaar op de timer open): de start van de ene klasse is het waarschuwingssein van de volgende. De stem zegt dan "Start! Klassevlag Optimist neer. Waarschuwingssein klassevlag ILCA 7. Nog vijf minuten." De klassen stel je in via het tandwiel, gescheiden door komma's.
- De stem spreekt 0,25 s vóór de seconde, zodat het woord samenvalt met het omslaan van het display en de hoorn.
- De stem loopt door als je naar de Timer-pagina gaat. Hij gebruikt de Nederlandse stem van de telefoon; is die niet geïnstalleerd, dan meldt de app dat (Instellingen van Android → Tekst-naar-spraak).
- Opent de app midden in een procedure, dan zegt de stem pas iets bij het volgende moment; gemiste aankondigingen worden niet ingehaald.

## Seinen (onderbrekingen)

| Knop | Wanneer | Wat er gebeurt |
|---|---|---|
| **X · Individuele terugroep** | Tot 4 minuten na een start | X-vlag op, 1 geluidssein. De vlag gaat uiterlijk na 4 minuten vanzelf neer, of eerder met "X-vlag neer". |
| **1e · Algemene terugroep** | Tot 4 minuten na een start | Eerste vervangende op, 2 geluidsseinen. De timer stopt (reset). |
| **AP · Uitstel** | Tijdens de procedure of in rust | Uitstelwimpel op, 2 geluidsseinen. De timer stopt (reset). |
| **Wimpel / vlag neer** | Na uitstel of algemene terugroep | 1 geluidssein. De timer plant zelf het waarschuwingssein precies 1 minuut later (geplande start op de ESP, werkt ook als de app daarna dicht gaat). De stem telt af zoals hierboven. |

Elke onderbreking vraagt eerst om bevestiging. De geluidsseinen gaan via de hoorn van de timer (zelfde regels als de handmatige toeter). Na een algemene terugroep of uitstel begint de nieuwe procedure met dezelfde klasse als de onderbroken start.

## Instellingen (tandwiel op de vlaggenpagina)

- Voorbereidingsvlag: P, I, Z, U of zwarte vlag
- Klassen (voor herhalen)
- Aankondigingen: elke minuut · laatste minuut elke 10 s · 10 s aftellen vóór elk sein
- Stem testen

Stem aan/uit staat ook direct op de pagina.

## Wat niet is overgenomen

*Start Timer* kan veel meer (vlootstarts met verschillende procedures, radiografische hoorns, gedeelde races, teamstarts, zwarte-vlagregels per start). De vlaggenpagina volgt de ZZ-timer en doet dus alleen de procedure die de timer zelf draait: 5-4-1-0, eenmalig of herhalend.
