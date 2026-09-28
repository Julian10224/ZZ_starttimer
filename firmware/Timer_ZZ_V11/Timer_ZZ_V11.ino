// Timer ZZ V11
//
// V10 (PWM-relais, nieuw matrixboard) met een robuuste opbouw voor wedstrijdgebruik:
//  - De timer (knoppen, tijd, hoorn, displays) draait in loop() op kern 1.
//    WiFi en de app draaien in een aparte taak op kern 0. Wat het netwerk ook doet
//    (vastlopen, trage telefoon, veel verkeer), de timer wacht er nooit op.
//  - Watchdog op de timer: loopt loop() meer dan 3 s vast, dan herstart de ESP.
//  - Na een herstart door een fout (crash, watchdog, spanningsdip) gaat een lopende
//    procedure verder waar hij was, met de juiste tijd. Gemiste hoornsignalen worden
//    alsnog gegeven.
//  - Geen flash-schrijfacties tijdens een lopende procedure.
//  - Geen String-opbouw in de lus (geen geheugenfragmentatie bij lang gebruik).
// Zie docs/V11_ROBUUSTHEID.md.

#include <TM1637Display.h>
#include <Arduino.h>
#include <Bounce2.h>

#define ARDUINOJSON_USE_LONG_LONG 1
#include <WiFi.h>
#include <WebSocketsServer.h>
#include <ArduinoJson.h>
#include <Preferences.h>
#include <sys/time.h>
#include "mbedtls/sha256.h"
#include "esp_timer.h"
#include "esp_random.h"
#include "esp_system.h"
#include "esp_task_wdt.h"

#define FW_VERSION "V11"
#define DEFAULT_SSID "ZZ-WedstrijdTimer"
#define DEFAULT_PASS "ZZstart2026"
#define DEFAULT_PIN  "1234"
#define WS_PORT 81
#define MAX_SCHED 20

#define TIMER_WDT_MS 3000          // V11: herstart als de timerlus zo lang vastzit

// === TM1637 Display Pin configuratie ===
#define CLK 18
#define DIO 19

// === Matrix board Pin configuratie ===
#define dataPin  33
#define clockPin 32
#define latchPin 26
#define enable_matrix 25

// === V10: enable van het matrixboard is actief-laag ===
#define MATRIX_ON   LOW    // LOW  = displays aan
#define MATRIX_OFF  HIGH   // HIGH = displays uit

// === V10: segmentmapping van het matrixboard (RJ45-D-4bits-kaart) ===
// Bitnummer (0-7) per segment. Bepaal deze met de sketch Timer_ZZ_Segmenttest.
#define SEG_BIT_A   5
#define SEG_BIT_B   6
#define SEG_BIT_C   2
#define SEG_BIT_D   1
#define SEG_BIT_E   0
#define SEG_BIT_F   4
#define SEG_BIT_G   7
#define SEG_BIT_DP  3
#define SEG_DP_AAN  1      // 1 = decimale punt altijd aan (zoals V3-V9), 0 = uit
#define SEG_ACTIEF_HOOG 1  // 1 = segment brandt bij bit = 1; 0 = omgekeerd (bit = 0 brandt)

// === Knoppen en relais pin configuratie ===
#define BUTTON_START 23
#define BUTTON_RESET 13
#define MANUAL_RELAY 14
#define RELAY_PIN 27

// === V9: PWM-relais (RC-servosignaal) op RELAY_PIN ===
#define RELAY_PWM_FREQ_HZ   50
#define RELAY_PWM_BITS      16
#define RELAY_PULSE_OFF_US  1000   // ca. 1 ms = relais uit
#define RELAY_PULSE_ON_US   2000   // ca. 2 ms = relais aan
#define horn_button 22
#define timer_switch 21

TM1637Display display(CLK, DIO);

// === Debounce objects ===
Bounce debouncedStart = Bounce();
Bounce debouncedReset = Bounce();
Bounce debouncedManual = Bounce();

// ===========================================================================
// Timerstatus: alleen gebruikt door de timertaak (loop, kern 1)
// ===========================================================================
bool startPressed = false;
bool resetPressed = false;

bool counting = false;
bool relayActive = false;
bool manualRelayActive = false;
bool ignoreResetUntilRelease = false;

int minutes = 5;
int seconds = 0;

bool triggeredRelay[6] = {false, false, false, false, false, false};
bool repeatMode = false;  // schakelaar open (HIGH) = herhalen, gesloten (LOW) = één keer

unsigned long relayStartMillis = 0;
unsigned long relayDuration = 500;

unsigned long startMillis = 0;   // millis() op het moment van START
long lastElapsedSec = 0;         // laatst verwerkte hele seconde sinds START
long currentCycle = 0;           // cyclusnummer in herhaalmodus (elke 300 s)
int64_t startSysUs = 0;          // V11: START-moment in systeemtijd (loopt door bij herstart)

bool sleepMode = false;
unsigned long resetPressStart = 0;
bool resetButtonHeld = false;
unsigned long lastActiveMillis = 0;

bool appHornActive = false;       // toeterknop in de app ingedrukt
unsigned long appHornUntil = 0;   // vervalt als de app niet blijft verversen

bool scheduledStartPending = false;
unsigned long scheduledStartMillis = 0;

// ===========================================================================
// V11: gedeeld tussen timertaak en netwerktaak (altijd via 'mux')
// ===========================================================================
portMUX_TYPE mux = portMUX_INITIALIZER_UNLOCKED;

struct Schedule {
  uint32_t id;
  int64_t pressEpoch;            // moment waarop START wordt uitgevoerd
  int64_t targetEpoch;           // door de gebruiker gekozen tijd
  uint8_t kind;                  // 0 = startschot (0:00) om, 1 = procedure start om
};
Schedule schedules[MAX_SCHED];   // mux
uint8_t schedCount = 0;          // mux
uint32_t nextSchedId = 1;        // mux
uint32_t schedVersion = 0;       // mux
bool schedDirty = false;         // mux: nog naar flash schrijven

bool timeSynced = false;         // mux
int64_t epochOffset = 0;         // mux: epoch-ms (telefoon) minus ESP-ms sinds opstart

// Momentopname van de timer voor de app (timertaak schrijft, netwerktaak leest)
struct Snapshot {
  bool counting;
  bool repeat;
  bool sleep;
  bool relayOn;
  bool manualAllowed;
  int8_t minutes;
  int8_t seconds;
  uint32_t startMillis;
};
Snapshot snap = {};              // mux
uint32_t snapSeq = 0;            // mux: telt op bij elke wijziging

// Commando's van de app naar de timer
enum CmdType : uint8_t { CMD_START, CMD_RESET, CMD_LED, CMD_HORN };
struct Cmd { CmdType type; bool on; };
QueueHandle_t cmdQueue;

// Meldingen van de timer naar de app
enum EvtType : uint8_t { EVT_SCHED_STARTED, EVT_SCHED_MISSED_RUNNING, EVT_SCHED_MISSED_LATE };
struct Evt { EvtType type; int64_t target; };
QueueHandle_t evtQueue;

// Herstelinformatie na een herstart
esp_reset_reason_t resetReason;
bool stateRestored = false;
uint8_t restoreCounter = 0;       // herstarts kort na elkaar; na 60 s stabiel weer 0
#define MAX_RESTORES 3

// ===========================================================================
// V11: herstel na een herstart (RTC-geheugen blijft behouden bij een softwareherstart)
// ===========================================================================
#define HERSTEL_MAGIC 0x5A5A1011UL
struct Herstel {
  uint32_t magic;
  uint8_t counting;
  uint8_t repeat;
  uint8_t sleep;
  uint8_t timeSynced;
  uint8_t restores;               // herstarts kort na elkaar (beveiliging tegen een herstartlus)
  uint8_t trig[6];
  int32_t lastElapsedSec;
  int32_t currentCycle;
  int64_t startSysUs;
  int64_t lastSaveSysUs;
  int64_t epochMinusSysMs;
  uint32_t check;
};
RTC_NOINIT_ATTR Herstel herstel;

int64_t sysUs() {                // systeemtijd; loopt via de RTC-timer door bij een herstart
  struct timeval tv;
  gettimeofday(&tv, NULL);
  return (int64_t)tv.tv_sec * 1000000LL + tv.tv_usec;
}

uint32_t herstelCheck(const Herstel &h) {
  const uint8_t *p = (const uint8_t *)&h;
  uint32_t x = 2166136261UL;     // FNV-1a
  for (size_t i = 0; i < offsetof(Herstel, check); i++) { x ^= p[i]; x *= 16777619UL; }
  return x;
}

// ===========================================================================
// Hulpfuncties
// ===========================================================================
Preferences prefs;
WebSocketsServer webSocket(WS_PORT);

// Netwerktaak
String apSsid = DEFAULT_SSID;
String apPass = DEFAULT_PASS;
uint8_t pinSalt[16];
uint8_t pinHash[32];
uint8_t failedPinAttempts = 0;
unsigned long pinLockUntil = 0;
bool pinLocked = false;
bool pendingWifiApply = false;
unsigned long wifiApplyAt = 0;
bool forceBroadcast = true;
unsigned long lastBroadcast = 0;
uint32_t lastSentSnapSeq = 0xFFFFFFFF;
uint32_t lastSentSchedVersion = 0xFFFFFFFF;
bool lastSentSynced = false;
bool lastSentSwitch = false;
int lastSentClients = -1;

void updateShiftRegisterDisplay(int minutes, int seconds);
void clearShiftRegisterDisplay();

// V9: zet de servopuls voor aan of uit (alleen bij een wijziging).
int8_t relayOutputState = -1;
uint32_t pulseToDuty(uint32_t us) {
  uint32_t periodUs = 1000000UL / RELAY_PWM_FREQ_HZ;
  return (us * ((1UL << RELAY_PWM_BITS) - 1)) / periodUs;
}
void setRelayOutput(bool on) {
  if (relayOutputState == (on ? 1 : 0)) return;
  relayOutputState = on ? 1 : 0;
  ledcWrite(RELAY_PIN, pulseToDuty(on ? RELAY_PULSE_ON_US : RELAY_PULSE_OFF_US));
}

void activateRelayNonBlocking(unsigned long duration = 500) {
  relayActive = true;
  relayStartMillis = millis();
  relayDuration = duration;
}

int64_t espMs64() {                         // ms sinds opstart, loopt niet over
  return esp_timer_get_time() / 1000;
}

// Resterende seconden voor k hele seconden na START (zelfde regel als V8-V10).
long remainingForK(long k) {
  if (k == 0) return 300;
  return 300 - (((k - 1) % 300) + 1);
}

// k = 0 -> 5:00, k = 300 -> 0:00, k = 301 -> stoppen (één keer) of 4:59 (herhalen).
void stepSecond(long k) {
  if (!repeatMode && k > 300) {
    counting = false;
    return;
  }

  long remaining = remainingForK(k);
  long cycle = (k == 0) ? 0 : (k - 1) / 300;

  if (cycle != currentCycle) {
    currentCycle = cycle;
    for (int i = 0; i < 6; i++) triggeredRelay[i] = false;
  }

  minutes = remaining / 60;
  seconds = remaining % 60;

  if (seconds == 0 && (
      (minutes == 5 && !triggeredRelay[5]) ||
      (minutes == 4 && !triggeredRelay[4]) ||
      (minutes == 1 && !triggeredRelay[1]) ||
      (minutes == 0 && !triggeredRelay[0])
    )) {
    unsigned long duration = (minutes == 1 || minutes == 0) ? 1000 : 500;
    activateRelayNonBlocking(duration);
    triggeredRelay[minutes] = true;
  }
}

void enterSleep() {                 // = reset 3 s vasthouden
  sleepMode = true;
  clearShiftRegisterDisplay();
  digitalWrite(enable_matrix, MATRIX_OFF);
  display.clear();
}

void exitSleep() {                  // = reset indrukken in slaapmodus
  sleepMode = false;
  display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
  updateShiftRegisterDisplay(minutes, seconds);
  digitalWrite(enable_matrix, MATRIX_ON);
  lastActiveMillis = millis();
}

void removeScheduleLocked(uint8_t index) {  // alleen aanroepen binnen mux
  for (uint8_t i = index; i + 1 < schedCount; i++) schedules[i] = schedules[i + 1];
  schedCount--;
}

// ===========================================================================
// V11: herstel opslaan en terugzetten (timertaak)
// ===========================================================================
void saveHerstel() {
  Herstel h;
  memset(&h, 0, sizeof(h));
  h.magic = HERSTEL_MAGIC;
  h.counting = counting;
  h.repeat = repeatMode;
  h.sleep = sleepMode;
  h.restores = (millis() < 60000UL) ? restoreCounter : 0;
  for (int i = 0; i < 6; i++) h.trig[i] = triggeredRelay[i];
  h.lastElapsedSec = lastElapsedSec;
  h.currentCycle = currentCycle;
  h.startSysUs = startSysUs;
  int64_t nowSys = sysUs();
  h.lastSaveSysUs = nowSys;
  portENTER_CRITICAL(&mux);
  h.timeSynced = timeSynced;
  int64_t off = epochOffset;
  portEXIT_CRITICAL(&mux);
  h.epochMinusSysMs = off + espMs64() - nowSys / 1000;
  h.check = herstelCheck(h);
  herstel = h;
}

bool restoreHerstel() {
  resetReason = esp_reset_reason();
  bool fout = resetReason == ESP_RST_PANIC || resetReason == ESP_RST_INT_WDT ||
              resetReason == ESP_RST_TASK_WDT || resetReason == ESP_RST_WDT ||
              resetReason == ESP_RST_BROWNOUT || resetReason == ESP_RST_SW;
  Herstel h = herstel;
  herstel.magic = 0;                         // eenmalig gebruiken
  if (!fout || h.magic != HERSTEL_MAGIC || h.check != herstelCheck(h)) return false;
  // Meer dan MAX_RESTORES herstarts binnen een minuut na elkaar: waarschijnlijk veroorzaakt
  // de toestand zelf de fout. Dan schoon beginnen in plaats van eindeloos herstarten.
  if (h.restores >= MAX_RESTORES) return false;
  restoreCounter = h.restores + 1;

  int64_t nowSys = sysUs();
  int64_t gap = nowSys - h.lastSaveSysUs;
  if (gap < 0 || gap > 10000000LL) return false;   // tijd onbetrouwbaar of te lang weg

  if (h.timeSynced) {
    portENTER_CRITICAL(&mux);
    epochOffset = h.epochMinusSysMs + nowSys / 1000 - espMs64();
    timeSynced = true;
    portEXIT_CRITICAL(&mux);
  }

  sleepMode = h.sleep;
  if (h.counting) {
    int64_t elapsedUs = nowSys - h.startSysUs;
    if (elapsedUs < 0 || elapsedUs > 7LL * 24 * 3600 * 1000000LL) return false;
    counting = true;
    repeatMode = h.repeat;
    startSysUs = h.startSysUs;
    startMillis = millis() - (uint32_t)(elapsedUs / 1000);
    lastElapsedSec = h.lastElapsedSec;
    currentCycle = h.currentCycle;
    for (int i = 0; i < 6; i++) triggeredRelay[i] = h.trig[i];
    long rem = remainingForK(lastElapsedSec);
    minutes = rem / 60;
    seconds = rem % 60;
    sleepMode = false;                       // tijdens een procedure altijd zichtbaar
  }
  return true;
}

// ===========================================================================
// V8: beheerders-PIN (gezouten SHA-256) - netwerktaak
// ===========================================================================
void hashPin(const char *pin, uint8_t out[32]) {
  uint8_t buf[16 + 16];
  size_t n = strnlen(pin, 16);
  memcpy(buf, pinSalt, 16);
  memcpy(buf + 16, pin, n);
  mbedtls_sha256(buf, 16 + n, out, 0);
}

void setPin(const char *pin) {
  for (int i = 0; i < 16; i++) pinSalt[i] = (uint8_t)(esp_random() & 0xFF);
  hashPin(pin, pinHash);
  prefs.putBytes("salt", pinSalt, 16);
  prefs.putBytes("pinhash", pinHash, 32);
}

// Geeft NULL terug bij een juiste PIN, anders een foutmelding (buf).
const char *checkPin(const char *pin, char *buf, size_t len) {
  unsigned long now = millis();
  if (pinLocked) {
    if ((long)(now - pinLockUntil) < 0) {
      snprintf(buf, len, "Te veel onjuiste pogingen. Probeer het over %lu s opnieuw.",
               (unsigned long)((pinLockUntil - now) / 1000 + 1));
      return buf;
    }
    pinLocked = false;
    failedPinAttempts = 0;
  }
  uint8_t h[32];
  hashPin(pin, h);
  uint8_t diff = 0;
  for (int i = 0; i < 32; i++) diff |= (uint8_t)(h[i] ^ pinHash[i]);
  if (diff == 0) {
    failedPinAttempts = 0;
    return NULL;
  }
  failedPinAttempts++;
  if (failedPinAttempts >= 5) {
    pinLocked = true;
    pinLockUntil = now + 60000UL;
    return "Onjuiste PIN. Instellingen 60 s geblokkeerd.";
  }
  return "Onjuiste PIN.";
}

// ===========================================================================
// Opslag (netwerktaak)
// ===========================================================================
void loadSettings() {
  prefs.begin("zztimer", false);
  apSsid = prefs.getString("ssid", DEFAULT_SSID);
  apPass = prefs.getString("pass", DEFAULT_PASS);
  if (prefs.getBytesLength("pinhash") == 32 && prefs.getBytesLength("salt") == 16) {
    prefs.getBytes("salt", pinSalt, 16);
    prefs.getBytes("pinhash", pinHash, 32);
  } else {
    setPin(DEFAULT_PIN);
  }
  uint8_t n = prefs.getUChar("schedn", 0);
  if (n > MAX_SCHED) n = 0;
  uint32_t nextId = prefs.getUInt("schedid", 1);
  static Schedule tmp[MAX_SCHED];
  if (n > 0 && prefs.getBytesLength("sched") == sizeof(Schedule) * n) {
    prefs.getBytes("sched", tmp, sizeof(Schedule) * n);
  } else {
    n = 0;
  }
  portENTER_CRITICAL(&mux);
  memcpy(schedules, tmp, sizeof(Schedule) * n);
  schedCount = n;
  nextSchedId = nextId;
  schedVersion++;
  portEXIT_CRITICAL(&mux);
}

// Schrijft geplande starts naar flash. Een flash-schrijfactie legt beide kernen
// kort stil; daarom nooit tijdens een lopende procedure.
void saveSchedulesIfIdle(bool timerRunning) {
  static Schedule tmp[MAX_SCHED];
  uint8_t n;
  uint32_t nextId;
  portENTER_CRITICAL(&mux);
  if (!schedDirty || timerRunning) {
    portEXIT_CRITICAL(&mux);
    return;
  }
  n = schedCount;
  nextId = nextSchedId;
  memcpy(tmp, schedules, sizeof(Schedule) * n);
  schedDirty = false;
  portEXIT_CRITICAL(&mux);

  prefs.putUChar("schedn", n);
  prefs.putUInt("schedid", nextId);
  if (n > 0) prefs.putBytes("sched", tmp, sizeof(Schedule) * n);
  else prefs.remove("sched");
}

// ===========================================================================
// Communicatie met de app (netwerktaak)
// ===========================================================================
Snapshot readSnapshot(uint32_t *seq = NULL) {
  portENTER_CRITICAL(&mux);
  Snapshot s = snap;
  if (seq) *seq = snapSeq;
  portEXIT_CRITICAL(&mux);
  return s;
}

bool pushCmd(CmdType type, bool on = false) {
  Cmd c = { type, on };
  return xQueueSend(cmdQueue, &c, 0) == pdTRUE;
}

void sendAck(uint8_t num, long id, bool ok, const char *error = NULL, long newId = -1) {
  JsonDocument doc;
  doc["type"] = "ack";
  doc["id"] = id;
  doc["ok"] = ok;
  if (!ok) doc["error"] = error ? error : "Opdracht mislukt.";
  if (newId >= 0) doc["sched_id"] = newId;
  char out[256];
  size_t n = serializeJson(doc, out, sizeof(out));
  webSocket.sendTXT(num, out, n);
}

void broadcastEvent(const Evt &e) {
  JsonDocument doc;
  doc["type"] = "event";
  doc["event"] = e.type == EVT_SCHED_STARTED ? "sched_started" : "sched_missed";
  doc["target_epoch"] = e.target;
  doc["reason"] = e.type == EVT_SCHED_MISSED_RUNNING ? "running" :
                  (e.type == EVT_SCHED_MISSED_LATE ? "late" : "");
  char out[192];
  size_t n = serializeJson(doc, out, sizeof(out));
  webSocket.broadcastTXT(out, n);
}

const char *validateSchedule(int64_t pressEpoch, int64_t targetEpoch, uint8_t kind) {
  portENTER_CRITICAL(&mux);
  bool synced = timeSynced;
  int64_t now = epochOffset + espMs64();
  portEXIT_CRITICAL(&mux);
  if (!synced) return "Tijd van de ESP is nog niet gesynchroniseerd.";
  if (kind > 1) return "Onbekend type geplande start.";
  int64_t expectedPress = (kind == 0) ? targetEpoch - 300000LL : targetEpoch;
  if (pressEpoch != expectedPress) return "Starttijd klopt niet met het gekozen type.";
  if (pressEpoch < now + 2000) return "Dit tijdstip ligt in het verleden of te dichtbij.";
  return NULL;
}

void handleCommand(uint8_t num, uint8_t *payload, size_t length) {
  JsonDocument doc;
  if (deserializeJson(doc, payload, length)) return;

  const char *cmd = doc["cmd"] | "";
  long id = doc["id"] | 0L;
  Snapshot s = readSnapshot();
  char errBuf[96];

  if (!strcmp(cmd, "start")) {
    if (s.sleep) { sendAck(num, id, false, "LED-paneel staat uit. Zet het eerst aan."); return; }
    if (s.counting) { sendAck(num, id, false, "De procedure loopt al."); return; }
    sendAck(num, id, pushCmd(CMD_START), "Timer bezet, probeer opnieuw.");

  } else if (!strcmp(cmd, "reset") || !strcmp(cmd, "stop")) {
    sendAck(num, id, pushCmd(CMD_RESET), "Timer bezet, probeer opnieuw.");

  } else if (!strcmp(cmd, "horn")) {
    // De app herhaalt "on" zolang de knop is ingedrukt; blijft dat uit, dan gaat
    // de toeter na 600 ms vanzelf uit (in de timertaak).
    pushCmd(CMD_HORN, doc["on"] | false);

  } else if (!strcmp(cmd, "led")) {
    bool on = doc["on"] | true;
    if (!on && s.counting) { sendAck(num, id, false, "Niet mogelijk tijdens een lopende procedure."); return; }
    sendAck(num, id, pushCmd(CMD_LED, on), "Timer bezet, probeer opnieuw.");
    forceBroadcast = true;

  } else if (!strcmp(cmd, "sync")) {
    JsonDocument r;
    r["type"] = "sync";
    r["id"] = id;
    r["t"] = doc["t"] | 0LL;
    r["esp_ms"] = espMs64();
    char out[128];
    size_t n = serializeJson(r, out, sizeof(out));
    webSocket.sendTXT(num, out, n);

  } else if (!strcmp(cmd, "settime")) {
    int64_t off = doc["offset_ms"] | 0LL;
    portENTER_CRITICAL(&mux);
    epochOffset = off;
    timeSynced = true;
    portEXIT_CRITICAL(&mux);
    sendAck(num, id, true);
    forceBroadcast = true;

  } else if (!strcmp(cmd, "sched_add") || !strcmp(cmd, "sched_edit")) {
    int64_t pressEpoch = doc["press_epoch"] | 0LL;
    int64_t targetEpoch = doc["target_epoch"] | 0LL;
    uint8_t kind = doc["kind"] | 0;
    const char *err = validateSchedule(pressEpoch, targetEpoch, kind);
    if (err) { sendAck(num, id, false, err); return; }

    if (!strcmp(cmd, "sched_add")) {
      long newId = -1;
      portENTER_CRITICAL(&mux);
      if (schedCount < MAX_SCHED) {
        Schedule sc = { nextSchedId++, pressEpoch, targetEpoch, kind };
        schedules[schedCount++] = sc;
        schedVersion++;
        schedDirty = true;
        newId = sc.id;
      }
      portEXIT_CRITICAL(&mux);
      if (newId < 0) {
        snprintf(errBuf, sizeof(errBuf), "Maximaal %d geplande starts.", MAX_SCHED);
        sendAck(num, id, false, errBuf);
      } else {
        sendAck(num, id, true, NULL, newId);
      }
    } else {
      uint32_t sid = doc["sched_id"] | 0UL;
      bool found = false;
      portENTER_CRITICAL(&mux);
      for (uint8_t i = 0; i < schedCount; i++) {
        if (schedules[i].id == sid) {
          schedules[i].pressEpoch = pressEpoch;
          schedules[i].targetEpoch = targetEpoch;
          schedules[i].kind = kind;
          schedVersion++;
          schedDirty = true;
          found = true;
          break;
        }
      }
      portEXIT_CRITICAL(&mux);
      if (found) sendAck(num, id, true, NULL, (long)sid);
      else sendAck(num, id, false, "Geplande start niet gevonden.");
    }

  } else if (!strcmp(cmd, "sched_del")) {
    uint32_t sid = doc["sched_id"] | 0UL;
    bool found = false;
    portENTER_CRITICAL(&mux);
    for (uint8_t i = 0; i < schedCount; i++) {
      if (schedules[i].id == sid) {
        removeScheduleLocked(i);
        schedVersion++;
        schedDirty = true;
        found = true;
        break;
      }
    }
    portEXIT_CRITICAL(&mux);
    if (found) sendAck(num, id, true);
    else sendAck(num, id, false, "Geplande start niet gevonden.");

  } else if (!strcmp(cmd, "pin_check")) {
    const char *err = checkPin(doc["pin"] | "", errBuf, sizeof(errBuf));
    sendAck(num, id, err == NULL, err);

  } else if (!strcmp(cmd, "pin_set")) {
    const char *err = checkPin(doc["pin"] | "", errBuf, sizeof(errBuf));
    if (err) { sendAck(num, id, false, err); return; }
    if (s.counting) { sendAck(num, id, false, "Niet mogelijk tijdens een lopende procedure."); return; }
    const char *newPin = doc["new_pin"] | "";
    size_t len = strlen(newPin);
    bool digitsOnly = len >= 4 && len <= 8;
    for (size_t i = 0; i < len; i++) if (!isDigit(newPin[i])) digitsOnly = false;
    if (!digitsOnly) { sendAck(num, id, false, "De PIN moet uit 4 tot 8 cijfers bestaan."); return; }
    setPin(newPin);
    sendAck(num, id, true);

  } else if (!strcmp(cmd, "wifi_set")) {
    const char *err = checkPin(doc["pin"] | "", errBuf, sizeof(errBuf));
    if (err) { sendAck(num, id, false, err); return; }
    if (s.counting) { sendAck(num, id, false, "Niet mogelijk tijdens een lopende procedure."); return; }
    const char *ssid = doc["ssid"] | "";
    const char *pass = doc["pass"] | "";
    size_t ls = strlen(ssid), lp = strlen(pass);
    if (ls < 1 || ls > 32) { sendAck(num, id, false, "De netwerknaam moet 1 tot 32 tekens lang zijn."); return; }
    if (lp < 8 || lp > 63) { sendAck(num, id, false, "Het wachtwoord moet 8 tot 63 tekens lang zijn."); return; }
    apSsid = ssid;
    apPass = pass;
    prefs.putString("ssid", apSsid);
    prefs.putString("pass", apPass);
    sendAck(num, id, true);
    pendingWifiApply = true;
    wifiApplyAt = millis() + 700;       // antwoord eerst laten aankomen

  } else {
    sendAck(num, id, false, "Onbekend commando.");
  }
}

void onWsEvent(uint8_t num, WStype_t type, uint8_t *payload, size_t length) {
  switch (type) {
    case WStype_CONNECTED:
      forceBroadcast = true;
      break;
    case WStype_DISCONNECTED:
      pushCmd(CMD_HORN, false);
      forceBroadcast = true;
      break;
    case WStype_TEXT:
      handleCommand(num, payload, length);
      break;
    default:
      break;
  }
}

const char *resetReasonText(esp_reset_reason_t r) {
  switch (r) {
    case ESP_RST_POWERON:  return "aan";
    case ESP_RST_EXT:      return "resetknop";
    case ESP_RST_SW:       return "software";
    case ESP_RST_PANIC:    return "crash";
    case ESP_RST_INT_WDT:
    case ESP_RST_TASK_WDT:
    case ESP_RST_WDT:      return "watchdog";
    case ESP_RST_BROWNOUT: return "spanningsdip";
    default:               return "onbekend";
  }
}

// Stuurt de status bij elke wijziging en minimaal elke seconde.
void broadcastStatus() {
  uint32_t seq;
  Snapshot s = readSnapshot(&seq);
  bool switchRepeat = (digitalRead(timer_switch) == HIGH);
  int clients = webSocket.connectedClients();

  portENTER_CRITICAL(&mux);
  uint32_t sv = schedVersion;
  bool synced = timeSynced;
  portEXIT_CRITICAL(&mux);

  unsigned long now = millis();
  bool changed = seq != lastSentSnapSeq || sv != lastSentSchedVersion || synced != lastSentSynced ||
                 switchRepeat != lastSentSwitch || clients != lastSentClients;
  if (!forceBroadcast && !changed && now - lastBroadcast < 1000) return;
  forceBroadcast = false;
  lastSentSnapSeq = seq;
  lastSentSchedVersion = sv;
  lastSentSynced = synced;
  lastSentSwitch = switchRepeat;
  lastSentClients = clients;
  lastBroadcast = now;
  if (clients == 0) return;

  static Schedule tmp[MAX_SCHED];
  portENTER_CRITICAL(&mux);
  uint8_t n = schedCount;
  memcpy(tmp, schedules, sizeof(Schedule) * n);
  int64_t off = epochOffset;
  portEXIT_CRITICAL(&mux);

  int64_t nowMs = espMs64();
  JsonDocument doc;
  doc["type"] = "status";
  doc["fw"] = FW_VERSION;
  doc["esp_ms"] = nowMs;
  doc["running"] = s.counting;
  doc["start_ms"] = nowMs - (int64_t)(uint32_t)(millis() - s.startMillis);
  doc["repeat"] = s.counting ? s.repeat : switchRepeat;
  doc["switch_repeat"] = switchRepeat;
  doc["min"] = s.minutes;
  doc["sec"] = s.seconds;
  doc["led_panel"] = !s.sleep;
  doc["horn"] = s.relayOn;
  doc["manual_allowed"] = s.manualAllowed;
  doc["time_synced"] = synced;
  doc["epoch_now"] = synced ? off + nowMs : 0;
  doc["ssid"] = apSsid;
  doc["ip"] = WiFi.softAPIP().toString();
  doc["clients"] = clients;
  doc["uptime_s"] = (long)(nowMs / 1000);
  doc["reset_reason"] = resetReasonText(resetReason);
  doc["restored"] = stateRestored;
  doc["heap_free"] = ESP.getFreeHeap();
  JsonArray arr = doc["sched"].to<JsonArray>();
  for (uint8_t i = 0; i < n; i++) {
    JsonObject o = arr.add<JsonObject>();
    o["id"] = tmp[i].id;
    o["press"] = tmp[i].pressEpoch;
    o["target"] = tmp[i].targetEpoch;
    o["kind"] = tmp[i].kind;
  }
  static char out[3072];
  size_t len = serializeJson(doc, out, sizeof(out));
  webSocket.broadcastTXT(out, len);
}

// V11: netwerktaak op kern 0. De timer wacht nooit op deze taak.
void netTask(void *) {
  loadSettings();
  WiFi.mode(WIFI_AP);
  WiFi.softAP(apSsid.c_str(), apPass.c_str());
  WiFi.setSleep(false);
  webSocket.begin();
  webSocket.onEvent(onWsEvent);
  webSocket.enableHeartbeat(5000, 3000, 2);
  Serial.printf("Timer ZZ %s - WiFi '%s' op %s:%d\n", FW_VERSION, apSsid.c_str(),
                WiFi.softAPIP().toString().c_str(), WS_PORT);

  for (;;) {
    webSocket.loop();

    Evt e;
    while (xQueueReceive(evtQueue, &e, 0) == pdTRUE) broadcastEvent(e);

    broadcastStatus();
    saveSchedulesIfIdle(readSnapshot().counting);

    if (pendingWifiApply && (long)(millis() - wifiApplyAt) >= 0) {
      pendingWifiApply = false;
      WiFi.softAP(apSsid.c_str(), apPass.c_str());
    }
    vTaskDelay(pdMS_TO_TICKS(2));
  }
}

// ===========================================================================
// Geplande starts uitvoeren (timertaak)
// ===========================================================================
void handleSchedules() {
  if (startPressed) return;
  bool due = false;
  Schedule s;
  int64_t now = 0, off = 0;
  portENTER_CRITICAL(&mux);
  if (timeSynced && schedCount > 0) {
    off = epochOffset;
    now = off + espMs64();
    for (uint8_t i = 0; i < schedCount; i++) {
      if (schedules[i].pressEpoch <= now) {
        s = schedules[i];
        removeScheduleLocked(i);
        schedVersion++;
        schedDirty = true;
        due = true;
        break;                           // één per doorgang
      }
    }
  }
  portEXIT_CRITICAL(&mux);
  if (!due) return;

  Evt e = { EVT_SCHED_STARTED, s.targetEpoch };
  if (counting) {
    e.type = EVT_SCHED_MISSED_RUNNING;
  } else if (now - s.pressEpoch > 2000) {
    e.type = EVT_SCHED_MISSED_LATE;
  } else {
    if (sleepMode) exitSleep();
    scheduledStartMillis = (unsigned long)(uint32_t)(s.pressEpoch - off);
    scheduledStartPending = true;
    startPressed = true;
  }
  xQueueSend(evtQueue, &e, 0);
}

// Verwerkt commando's van de app (timertaak)
void handleAppCommands() {
  Cmd c;
  while (xQueueReceive(cmdQueue, &c, 0) == pdTRUE) {
    switch (c.type) {
      case CMD_START:
        startPressed = true;
        break;
      case CMD_RESET:
        resetPressed = true;
        break;
      case CMD_LED:
        if (c.on) {
          if (sleepMode) exitSleep();
        } else if (!counting && !sleepMode) {
          enterSleep();
        }
        break;
      case CMD_HORN:
        appHornActive = c.on;
        if (c.on) appHornUntil = millis() + 600;
        break;
    }
  }
}

// V11: testcommando's via de USB-seriële monitor (115200 baud), om het herstel te testen:
//   test-crash  -> forceert een crash (herstart, procedure gaat verder)
//   test-hang   -> laat de timerlus vastlopen (watchdog herstart na 3 s)
void handleSerialTest() {
  static char buf[16];
  static uint8_t len = 0;
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n' || c == '\r') {
      buf[len] = 0;
      if (!strcmp(buf, "test-crash")) {
        Serial.println("Test: crash");
        Serial.flush();
        abort();
      } else if (!strcmp(buf, "test-hang")) {
        Serial.println("Test: timer loopt vast, watchdog herstart binnen 3 s");
        Serial.flush();
        for (;;) {}
      }
      len = 0;
    } else if (len < sizeof(buf) - 1) {
      buf[len++] = c;
    }
  }
}

void publishSnapshot(bool relayOn, bool manualAllowed) {
  Snapshot s;
  memset(&s, 0, sizeof(s));
  s.counting = counting;
  s.repeat = repeatMode;
  s.sleep = sleepMode;
  s.relayOn = relayOn;
  s.manualAllowed = manualAllowed;
  s.minutes = minutes;
  s.seconds = seconds;
  s.startMillis = startMillis;
  portENTER_CRITICAL(&mux);
  if (memcmp(&s, &snap, sizeof(s)) != 0) {
    snap = s;
    snapSeq++;
  }
  portEXIT_CRITICAL(&mux);
}

// ===========================================================================
// setup / loop (timertaak, kern 1)
// ===========================================================================
void setup() {
  // Hoorn eerst uit, vóór alles
  ledcAttach(RELAY_PIN, RELAY_PWM_FREQ_HZ, RELAY_PWM_BITS);
  setRelayOutput(false);

  Serial.begin(115200);

  pinMode(BUTTON_START, INPUT_PULLUP);
  pinMode(BUTTON_RESET, INPUT_PULLUP);
  pinMode(MANUAL_RELAY, INPUT_PULLUP);
  pinMode(timer_switch, INPUT_PULLUP);
  pinMode(horn_button, OUTPUT);
  pinMode(enable_matrix, OUTPUT);
  digitalWrite(enable_matrix, MATRIX_ON);

  debouncedStart.attach(BUTTON_START);
  debouncedStart.interval(25);
  debouncedReset.attach(BUTTON_RESET);
  debouncedReset.interval(25);
  debouncedManual.attach(MANUAL_RELAY);
  debouncedManual.interval(25);

  minutes = 5;
  seconds = 0;

  // V11: lopende procedure terugzetten na een herstart door een fout
  stateRestored = restoreHerstel();

  pinMode(dataPin, OUTPUT);
  pinMode(clockPin, OUTPUT);
  pinMode(latchPin, OUTPUT);
  display.setBrightness(0x0f);
  if (sleepMode) {
    enterSleep();
  } else {
    display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
    updateShiftRegisterDisplay(minutes, seconds);
  }
  Serial.printf("Timer ZZ %s - herstart: %s%s\n", FW_VERSION, resetReasonText(resetReason),
                stateRestored ? (counting ? ", procedure hersteld" : ", status hersteld") : "");

  // V11: watchdog alleen op de timerlus; het netwerk kan de ESP niet laten herstarten
  esp_task_wdt_config_t wdt = { TIMER_WDT_MS, 0, true };
  esp_task_wdt_reconfigure(&wdt);
  disableCore0WDT();
  enableLoopWDT();

  cmdQueue = xQueueCreate(16, sizeof(Cmd));
  evtQueue = xQueueCreate(8, sizeof(Evt));
  xTaskCreatePinnedToCore(netTask, "net", 8192, NULL, 1, NULL, 0);

  lastActiveMillis = millis();
}

void loop() {
  unsigned long currentMillis = millis();

  handleAppCommands();                  // V11: app-commando's uit de wachtrij
  handleSerialTest();

  debouncedStart.update();
  debouncedReset.update();
  debouncedManual.update();

  if (debouncedStart.fell()) {
    startPressed = true;
  }

  bool resetButtonState = (debouncedReset.read() == LOW);

  if (ignoreResetUntilRelease && !resetButtonState) {
    ignoreResetUntilRelease = false;
  }

  if (!ignoreResetUntilRelease) {
    if (resetButtonState && !resetButtonHeld) {
      resetButtonHeld = true;
      resetPressStart = currentMillis;

      if (!sleepMode) {
        resetPressed = true;
      }
    }

    if (resetButtonHeld) {
      unsigned long heldDuration = currentMillis - resetPressStart;

      if (resetButtonState) {
        if (!sleepMode && !counting && heldDuration >= 3000) {
          sleepMode = true;
          clearShiftRegisterDisplay();
          digitalWrite(enable_matrix, MATRIX_OFF);
          display.clear();
          ignoreResetUntilRelease = true;
          resetButtonHeld = false;
        } else if (sleepMode && heldDuration >= 100) {
          sleepMode = false;
          display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
          updateShiftRegisterDisplay(minutes, seconds);
          digitalWrite(enable_matrix, MATRIX_ON);
          lastActiveMillis = currentMillis;
          ignoreResetUntilRelease = true;
          resetButtonHeld = false;
        }
      } else {
        resetButtonHeld = false;
      }
    }
  }

  handleSchedules();                    // geplande starts

  if (!counting && !sleepMode && ((long)(currentMillis - lastActiveMillis) >= 360000L)) {
    sleepMode = true;
    digitalWrite(enable_matrix, MATRIX_OFF);
    clearShiftRegisterDisplay();
    display.clear();
  }

  if (startPressed) {
    startPressed = false;
    if (!sleepMode && !counting) {
      counting = true;
      minutes = 5;
      seconds = 0;
      for (int i = 0; i < 6; i++) triggeredRelay[i] = false;

      repeatMode = (digitalRead(timer_switch) == HIGH);

      activateRelayNonBlocking();
      triggeredRelay[5] = true;

      if (!sleepMode) {
        digitalWrite(enable_matrix, MATRIX_ON);
        display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
        updateShiftRegisterDisplay(minutes, seconds);
      }

      startMillis = scheduledStartPending ? scheduledStartMillis : currentMillis;
      startSysUs = sysUs() - (int64_t)(uint32_t)(millis() - startMillis) * 1000LL;
      lastElapsedSec = 0;
      currentCycle = 0;
      lastActiveMillis = currentMillis;
    }
    scheduledStartPending = false;
  }

  if (resetPressed) {
    resetPressed = false;
    counting = false;
    relayActive = false;
    minutes = 5;
    seconds = 0;
    for (int i = 0; i < 6; i++) triggeredRelay[i] = false;

    if (!sleepMode) {
      digitalWrite(enable_matrix, MATRIX_ON);
      display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
      updateShiftRegisterDisplay(minutes, seconds);
    }

    lastActiveMillis = currentMillis;
  }

  // Timer op basis van de verstreken tijd sinds START. Gemiste seconden worden
  // ingehaald (inclusief hoornsignalen), dus een trage doorgang kan de timer nooit
  // laten achterlopen.
  if (counting) {
    unsigned long nowMs = millis();
    if ((long)(nowMs - startMillis) >= 0) {
      long elapsedSec = (long)((nowMs - startMillis) / 1000UL);
      // V11: meer dan 10 s achter (alleen na een uitzonderlijke onderbreking):
      // direct naar de juiste tijd, zonder een reeks late hoornsignalen.
      if (elapsedSec - lastElapsedSec > 10) lastElapsedSec = elapsedSec - 1;
      bool stepped = false;
      while (counting && lastElapsedSec < elapsedSec) {
        lastElapsedSec++;
        stepSecond(lastElapsedSec);
        stepped = true;
      }

      if (stepped) {
        if (!sleepMode) {
          digitalWrite(enable_matrix, MATRIX_ON);
          display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
          updateShiftRegisterDisplay(minutes, seconds);
        }
        lastActiveMillis = currentMillis;
      }
    }
  }

  if (relayActive && (millis() - relayStartMillis >= relayDuration)) {
    relayActive = false;
  }

  if (!counting && !sleepMode && !manualRelayActive) {
    minutes = 5;
    seconds = 0;
    display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
    updateShiftRegisterDisplay(minutes, seconds);
  }

  manualRelayActive = (debouncedManual.read() == LOW);

  bool inAllowedManualRange = counting &&
                              ((minutes == 5) || (minutes == 4 && seconds == 0) || (minutes == 4 && seconds > 0));

  if (appHornActive && (long)(millis() - appHornUntil) >= 0) appHornActive = false;

  bool relayShouldBeOn = (((manualRelayActive || appHornActive) && (!counting || inAllowedManualRange)) || relayActive);

  if (!counting || inAllowedManualRange) {
    digitalWrite(horn_button, LOW);
  } else {
    digitalWrite(horn_button, HIGH);
  }

  setRelayOutput(relayShouldBeOn);

  // V11: status voor de app en herstelinformatie bijwerken (geen netwerk hier)
  publishSnapshot(relayShouldBeOn, !counting || inAllowedManualRange);
  saveHerstel();
}

// === V10: segmentmapping en 3 digits ===
byte mapSegments(bool a, bool b, bool c, bool d, bool e, bool f, bool g, bool dp = SEG_DP_AAN) {
  byte v = (a << SEG_BIT_A) | (b << SEG_BIT_B) | (c << SEG_BIT_C) | (d << SEG_BIT_D) |
           (e << SEG_BIT_E) | (f << SEG_BIT_F) | (g << SEG_BIT_G) | (dp << SEG_BIT_DP);
  return SEG_ACTIEF_HOOG ? v : (byte)~v;
}

// Welke segmenten samen een cijfer vormen (a-g). Dit is voor elk 7-segmentdisplay
// gelijk; mapSegments() zet het om naar de bitposities van dit board.
const byte digits[10] = {
  mapSegments(1,1,1,1,1,1,0), // 0
  mapSegments(0,1,1,0,0,0,0), // 1
  mapSegments(1,1,0,1,1,0,1), // 2
  mapSegments(1,1,1,1,0,0,1), // 3
  mapSegments(0,1,1,0,0,1,1), // 4
  mapSegments(1,0,1,1,0,1,1), // 5
  mapSegments(1,0,1,1,1,1,1), // 6
  mapSegments(1,1,1,0,0,0,0), // 7
  mapSegments(1,1,1,1,1,1,1), // 8
  mapSegments(1,1,1,1,0,1,1)  // 9
};

// Drie registers in serie. De eerst ingeschoven byte komt in het verst gelegen register.
void updateShiftRegisterDisplay(int minutes, int seconds) {
  int m = minutes % 10;
  int s1 = seconds / 10;
  int s2 = seconds % 10;
  digitalWrite(latchPin, LOW);
  shiftOut(dataPin, clockPin, MSBFIRST, digits[s2]);
  shiftOut(dataPin, clockPin, MSBFIRST, digits[s1]);
  shiftOut(dataPin, clockPin, MSBFIRST, digits[m]);
  digitalWrite(latchPin, HIGH);
}

void clearShiftRegisterDisplay() {
  byte leeg = SEG_ACTIEF_HOOG ? 0x00 : 0xFF;
  digitalWrite(latchPin, LOW);
  shiftOut(dataPin, clockPin, MSBFIRST, leeg);
  shiftOut(dataPin, clockPin, MSBFIRST, leeg);
  shiftOut(dataPin, clockPin, MSBFIRST, leeg);
  digitalWrite(latchPin, HIGH);
}
