# V9: PWM-relais (servosignaal)

V9 is V8 met één verschil: het hoornrelais op GPIO 27 wordt aangestuurd met een servopuls (50 Hz) in plaats van met `HIGH`/`LOW`. Gebruik V9 voor het bord met het PWM-relais (RC-schakelaar), en V8 voor het bord met een normaal relais. De app en het protocol zijn voor beide gelijk.

## Aansturing

| Stand | Puls | Duty (16 bit, 50 Hz) |
|---|---|---|
| Relais uit | ca. 1 ms | 3276 |
| Relais aan | ca. 2 ms | 6553 |

- Bij het opstarten zet de ESP het relais direct in de uit-stand (1 ms).
- De puls wordt alleen aangepast als de stand verandert; het PWM-signaal loopt daarna in hardware (LEDC) door, zonder belasting van de processor.
- Alle schakelmomenten, duur van de signalen (0,5 s / 1 s), de handmatige toeter en de toeterknop in de app werken hetzelfde als in V8.

## Instellen

Bovenaan de sketch:

```cpp
#define RELAY_PULSE_OFF_US  1000   // ca. 1 ms = relais uit
#define RELAY_PULSE_ON_US   2000   // ca. 2 ms = relais aan
```

Schakelt het relais precies omgekeerd (aan in rust, uit tijdens een signaal), wissel dan deze twee waarden om.

## Om rekening mee te houden

- Een RC-schakelaar reageert pas na de volgende puls (elke 20 ms) en filtert vaak een paar pulsen. Het hoornsignaal begint daardoor enkele tientallen milliseconden later dan bij een normaal relais. Dat is niet hoorbaar als afwijking en stapelt niet op, want de timer zelf blijft op absolute tijd lopen.
- Wat het relais doet zonder signaal (bijvoorbeeld tijdens het opstarten van de ESP, vóór `setup()`), hangt af van het module. Controleer bij het aanzetten of de hoorn niet kort klinkt.

## Wijzigingen ten opzichte van V8

- `RELAY_PIN` via `ledcAttach()` (50 Hz, 16 bit) in plaats van `pinMode(OUTPUT)`.
- `setRelayOutput(aan)` vervangt `digitalWrite(RELAY_PIN, ...)`.
- `FW_VERSION` is `"V9"`.

Gecompileerd met ESP32-core 3.3.12 (951 166 bytes, 72% flash).
