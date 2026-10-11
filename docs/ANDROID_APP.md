# Android-app: installeren, bouwen en verbinden

## Standaardwaarden

| Instelling | Standaard |
|---|---|
| WiFi-netwerk van de timer | `ZZ-WedstrijdTimer` |
| WiFi-wachtwoord | `ZZstart2026` |
| IP-adres van de timer | `192.168.4.1` |
| Beheerders-PIN | `1234` |

Wijzig het WiFi-wachtwoord en de PIN na de eerste keer via **Instellingen** (tandwiel rechtsboven).

## De app installeren (APK)

1. Open op GitHub het tabblad **Actions** → workflow **Android app** → de laatste geslaagde run.
2. Download onder **Artifacts** het bestand `ZZ-Wedstrijd-Timer-apk` (een zip met de APK). Is er een release gemaakt, dan staat de APK ook onder **Releases**.
3. Zet de APK op de telefoon en open hem. Sta "installeren uit onbekende bronnen" toe als Android daarom vraagt.
4. Een nieuwere versie installeer je gewoon over de oude heen; de APK is altijd met dezelfde sleutel ondertekend.

Minimaal Android 8.0.

## Zelf bouwen

**Met Android Studio:**
1. Open de map `android/` met **File → Open**.
2. Laat Gradle synchroniseren en kies **Run**.

**Via de opdrachtregel (JDK 17 en Android SDK nodig):**

```bash
cd android
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

**Release op GitHub:** Actions → **Android app** → **Run workflow** → vink "GitHub-release maken" aan.

## Telefoon verbinden met de timer

1. Zet de timer aan (firmware V8).
2. Open op de telefoon de WiFi-instellingen en kies `ZZ-WedstrijdTimer`.
3. Meldt Android "Dit netwerk heeft geen internettoegang", kies dan **Verbonden blijven** (of "Toch verbinden").
4. Open de app. Boven de wedstrijdtijd verschijnt **● Verbonden met de timer**.

De app stuurt het verkeer altijd via het WiFi-netwerk van de timer, ook als Android mobiele data als standaardverbinding gebruikt.

## Gebruik

- **START / STOP-RESET:** STOP werkt hetzelfde als de resetknop (terug naar 5:00).
- **Modus:** **5 min doorstart** (schakelaar open) of **3 min doorstart** (schakelaar gesloten). De app toont de stand van de schakelaar op de timer; wijzigen kan alleen met die schakelaar.
- **LED-paneel:** aan/uit is hetzelfde als lang drukken op reset. Uitzetten kan niet tijdens een lopende procedure.
- **Toeter:** toont of het hoornrelais op dit moment aan staat. Met de knop **Toeter** (ingedrukt houden) laat je de toeter klinken zolang je hem vasthoudt, op dezelfde momenten als de handmatige knop op de timer (in rust en in de eerste minuut: 5:00–4:00, of 3:00–2:00 bij 3 minuten). Valt de verbinding weg terwijl je hem ingedrukt houdt, dan stopt de toeter binnen 0,6 s.
- **Geplande starts:** kies een tijd, 5 of 3 minuten doorstart (standaard de stand van de schakelaar) en of dat het startschot (0:00) of het begin van de procedure is. De timer voert de start zelf uit, ook als de app dicht is.
- **Vlaggenpagina:** via de navigatiebalk onderaan. Toont de seinvlaggen volgens regel 26, telt hardop af in het Nederlands en heeft knoppen voor individuele terugroep, algemene terugroep en uitstel. Zie [VLAGGEN.md](VLAGGEN.md).
- Het scherm blijft aan zolang de app open is.

## Wat de app doet bij verbindingsverlies

- De timer werkt volledig zelfstandig en merkt niets van de app.
- De app blijft de wedstrijdtijd berekenen uit de laatst bekende starttijd en klok, en toont **● Geen verbinding**. De cijfers worden dan gedimd weergegeven, omdat iemand intussen op de timer op reset kan hebben gedrukt.
- Zodra de verbinding terug is, haalt de app direct de actuele status op en synchroniseert hij de klok opnieuw.

## Vormgeving (vanaf app 1.9)

App 1.9 heeft een nieuwe, rustige vormgeving; de werking is gelijk aan 1.8.

- **Licht en donker:** de app volgt de instelling van de telefoon. Licht: witte groepen op een lichtgrijze achtergrond. Donker: donkergrijze groepen op zwart.
- **Eén accentkleur** (blauw) voor knoppen en actieve standen. Groen betekent verbonden of vlag op, oranje toeter of aandacht, rood stop, fouten of geen verbinding.
- **Grote paginatitels** (Timer, Vlaggen, Instellingen) en gegroepeerde lijsten met een korte uitleg eronder.
- **Tijden** staan in cijfers met gelijke breedte, zodat ze niet verspringen.
- **10 s aftellen / Direct** en **5 / 3 min doorstart** (bij een geplande start) zijn segmentknoppen.
- Liggend zijn de titel en de tabbalk compacter, zodat er meer ruimte is voor de tijd.
- Staat op de telefoon "Animaties verwijderen" aan, dan laat de app de kleine animaties weg.
