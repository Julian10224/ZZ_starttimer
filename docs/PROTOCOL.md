# Communicatieprotocol ESP ↔ app

- **Transport:** WebSocket, `ws://192.168.4.1:81/` (IP instelbaar in de app).
- **Berichten:** JSON-tekstberichten.
- **Richting:** de ESP stuurt de status bij elke wijziging en minimaal elke seconde. De app stuurt commando's met een `id`; de ESP antwoordt met een `ack` met hetzelfde `id`.
- **Tijd:** alle tijden op de ESP zijn milliseconden sinds het opstarten van de ESP (`esp_ms`, loopt niet over). Tijden van geplande starts zijn Unix-epoch in milliseconden (telefoonklok).

## ESP → app

### `status`

```json
{
  "type": "status",
  "fw": "V7A",
  "esp_ms": 1234567,
  "running": true,
  "start_ms": 1200000,
  "repeat": false,
  "switch_repeat": false,
  "proc_s": 300,
  "switch_proc_s": 300,
  "min": 4,
  "sec": 25,
  "led_panel": true,
  "horn": false,
  "manual_allowed": false,
  "time_synced": true,
  "epoch_now": 1790000000000,
  "ssid": "ZZ-WedstrijdTimer",
  "ip": "192.168.4.1",
  "clients": 1,
  "uptime_s": 1234,
  "sched": [
    { "id": 3, "press": 1790001300000, "target": 1790001600000, "kind": 0, "proc_s": 300 }
  ]
}
```

| Veld | Betekenis |
|---|---|
| `running` | Procedure loopt |
| `start_ms` | ESP-tijd van het START-moment |
| `repeat` | Modus van de lopende procedure; in rust de stand van de schakelaar |
| `switch_repeat` | Huidige stand van de schakelaar (`true` = open = herhalen) |
| `proc_s` | Vanaf V7B: lengte van de procedure in seconden, `300` = 5 minuten doorstart, `180` = 3 minuten doorstart. Tijdens een procedure die van de lopende procedure, in rust de stand van de schakelaar. Ontbreekt bij oudere firmware (dan 300). `repeat` en `switch_repeat` zijn vanaf V7B altijd `true`. |
| `switch_proc_s` | Vanaf V7B: huidige stand van de schakelaar (open = `300`, gesloten = `180`) |
| `min`, `sec` | Wat de displays van de ESP op dit moment tonen |
| `led_panel` | `false` = slaapmodus (displays uit) |
| `horn` | Werkelijke stand van de relaisuitgang |
| `manual_allowed` | Handmatige hoornknop toegestaan |
| `time_synced` | ESP kent de telefoontijd (nodig voor geplande starts) |
| `sched[].press` | Moment waarop de ESP START uitvoert |
| `sched[].target` | Tijd die de gebruiker koos |
| `sched[].kind` | `0` = startschot om `target` (`press = target − proc_s × 1000`), `1` = procedure start om `target` |
| `sched[].proc_s` | Procedure van deze geplande start (`300` of `180`). De geplande start gebruikt deze procedure, niet de stand van de schakelaar. |

**Wedstrijdtijd in de app:** `k = floor((espNu − start_ms) / 1000)` met dezelfde regel als de firmware, met `P = proc_s` (300 of 180):
- `k = 0` → `P` (5:00 of 3:00);
- anders `rest = P − (((k − 1) mod P) + 1)`;
- bij één keer (alleen oudere firmware) en `k > P` → gestopt.

`espNu` = `elapsedRealtime()` van de telefoon + klokverschil (zie `sync`).

### `ack`

```json
{ "type": "ack", "id": 12, "ok": false, "error": "LED-paneel staat uit. Zet het eerst aan." }
```

Bij `sched_add` / `sched_edit` staat het `sched_id` in het antwoord.

### `sync`

```json
{ "type": "sync", "id": 7, "t": 1790000000000, "esp_ms": 1234567 }
```

### `event`

```json
{ "type": "event", "event": "sched_started", "target_epoch": 1790001600000, "reason": "" }
{ "type": "event", "event": "sched_missed",  "target_epoch": 1790001600000, "reason": "running" }
```

`reason`: `running` (er liep al een procedure) of `late` (tijdstip meer dan 2 s gemist).

## App → ESP

| Commando | Velden | Werking |
|---|---|---|
| `start` | – | Zelfde als de startknop |
| `reset` of `stop` | – | Zelfde als kort op reset drukken |
| `led` | `on` (bool) | LED-paneel aan/uit (slaapmodus) |
| `horn` | `on` (bool) | Handmatige toeter. De app herhaalt `on: true` elke 200 ms zolang de knop is ingedrukt; de ESP zet de toeter uit na `on: false`, na 600 ms zonder herhaling of als de verbinding wegvalt. Zelfde toegestane momenten als de fysieke knop. Geen `ack`. |
| `sync` | `t` (epoch-ms telefoon) | ESP antwoordt direct met `esp_ms` |
| `settime` | `offset_ms` | Verschil epoch − ESP-tijd, voor geplande starts |
| `sched_add` | `press_epoch`, `target_epoch`, `kind`, `proc_s` | Geplande start toevoegen. `proc_s` (300 of 180) is optioneel, standaard 300. |
| `sched_edit` | `sched_id`, `press_epoch`, `target_epoch`, `kind`, `proc_s` | Aanpassen |
| `sched_del` | `sched_id` | Verwijderen |
| `pin_check` | `pin` | Controleert de beheerders-PIN |
| `pin_set` | `pin`, `new_pin` | PIN wijzigen (4–8 cijfers) |
| `wifi_set` | `pin`, `ssid`, `pass` | WiFi wijzigen; niet tijdens een lopende procedure |

Voorbeeld:

```json
{ "cmd": "start", "id": 12 }
```

## Klok synchroniseren

1. De app stuurt `sync` met de telefoontijd `t0` en onthoudt ook `elapsedRealtime` (`r0`).
2. De ESP antwoordt met `esp_ms`. De app noteert `t1` en `r1`.
3. De rondreistijd is `r1 − r0`. Het klokverschil voor de weergave is `esp_ms − (r0 + r1) / 2`, en voor geplande starts `(t0 + t1) / 2 − esp_ms`.
4. Dit gebeurt vijf keer; de meting met de kortste rondreistijd telt. Het resultaat voor geplande starts gaat met `settime` naar de ESP.
5. Het geheel wordt bij elke verbinding herhaald, daarna elke 30 s, en direct als de ESP opnieuw is opgestart.
