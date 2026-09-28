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
4. Open de app. Onder de wedstrijdtijd verschijnt **● VERBONDEN**.

De app stuurt het verkeer altijd via het WiFi-netwerk van de timer, ook als Android mobiele data als standaardverbinding gebruikt.

## Gebruik

- **START / STOP-RESET:** STOP werkt hetzelfde als de resetknop (terug naar 5:00).
- **Modus:** de app toont de stand van de schakelaar op de timer; wijzigen kan alleen met die schakelaar.
- **LED-paneel:** aan/uit is hetzelfde als lang drukken op reset. Uitzetten kan niet tijdens een lopende procedure.
- **Toeter:** toont of het hoornrelais op dit moment aan staat. Met de knop **TOETER · INGEDRUKT HOUDEN** laat je de toeter klinken zolang je hem vasthoudt, op dezelfde momenten als de handmatige knop op de timer (in rust en tijdens 5:00–4:00). Valt de verbinding weg terwijl je hem ingedrukt houdt, dan stopt de toeter binnen 0,6 s.
- **Geplande starts:** kies een tijd en of dat het startschot (0:00) of het begin van de procedure is. De timer voert de start zelf uit, ook als de app dicht is.
- **Vlaggenpagina:** via de navigatiebalk onderaan. Toont de seinvlaggen volgens regel 26, telt hardop af in het Nederlands en heeft knoppen voor individuele terugroep, algemene terugroep en uitstel. Zie [VLAGGEN.md](VLAGGEN.md).
- Het scherm blijft aan zolang de app open is.

## Wat de app doet bij verbindingsverlies

- De timer werkt volledig zelfstandig en merkt niets van de app.
- De app blijft de wedstrijdtijd berekenen uit de laatst bekende starttijd en klok, en toont **○ GEEN VERBINDING**. De cijfers worden dan gedimd weergegeven, omdat iemand intussen op de timer op reset kan hebben gedrukt.
- Zodra de verbinding terug is, haalt de app direct de actuele status op en synchroniseert hij de klok opnieuw.
