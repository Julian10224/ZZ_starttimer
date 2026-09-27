#include <TM1637Display.h>
#include <Arduino.h>
#include <Bounce2.h>

// === V8: WiFi / app ===
#define ARDUINOJSON_USE_LONG_LONG 1
#include <WiFi.h>
#include <WebSocketsServer.h>
#include <ArduinoJson.h>
#include <Preferences.h>
#include "mbedtls/sha256.h"
#include "esp_timer.h"
#include "esp_random.h"

#define FW_VERSION "V9"
#define DEFAULT_SSID "ZZ-WedstrijdTimer"
#define DEFAULT_PASS "ZZstart2026"
#define DEFAULT_PIN  "1234"
#define WS_PORT 81
#define MAX_SCHED 20

// === TM1637 Display Pin configuratie ===
#define CLK 18
#define DIO 19

// === Matrix board Pin configuratie ===
#define dataPin  33
#define clockPin 32
#define latchPin 26
#define enable_matrix 25

// === Knoppen en relais pin configuratie ===
#define BUTTON_START 23
#define BUTTON_RESET 13
#define MANUAL_RELAY 14
#define RELAY_PIN 27

// === V9: PWM-relais (RC-servosignaal) op RELAY_PIN ===
// Het relais wordt niet met HIGH/LOW geschakeld maar met een servopuls van 50 Hz.
// Schakelt het relais omgekeerd, wissel dan de twee pulsbreedtes om.
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

// === Status en timing ===
volatile bool startPressed = false;
volatile bool resetPressed = false;

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

// === V8: absolute tijdbasis ===
unsigned long startMillis = 0;   // millis() op het moment van START
long lastElapsedSec = 0;         // laatst verwerkte hele seconde sinds START
long currentCycle = 0;           // cyclusnummer in herhaalmodus (elke 300 s)

// === Slaapmodus variabelen ===
bool sleepMode = false;
unsigned long resetPressStart = 0;
bool resetButtonHeld = false;
unsigned long lastActiveMillis = 0;

// === V8: WiFi, instellingen en geplande starts ===
WebSocketsServer webSocket(WS_PORT);
Preferences prefs;

String apSsid = DEFAULT_SSID;
String apPass = DEFAULT_PASS;
uint8_t pinSalt[16];
uint8_t pinHash[32];
uint8_t failedPinAttempts = 0;
unsigned long pinLockUntil = 0;
bool pinLocked = false;

bool appHornActive = false;       // toeterknop in de app ingedrukt
unsigned long appHornUntil = 0;   // vervalt als de app niet blijft verversen

bool pendingWifiApply = false;
unsigned long wifiApplyAt = 0;

bool timeSynced = false;
int64_t epochOffset = 0;         // epoch-ms (telefoon) minus ESP-ms sinds boot

struct Schedule {
  uint32_t id;
  int64_t pressEpoch;            // moment waarop START wordt uitgevoerd
  int64_t targetEpoch;           // door de gebruiker gekozen tijd
  uint8_t kind;                  // 0 = startschot (0:00) om, 1 = procedure start om
};
Schedule schedules[MAX_SCHED];
uint8_t schedCount = 0;
uint32_t nextSchedId = 1;
uint32_t schedVersion = 0;

bool scheduledStartPending = false;
unsigned long scheduledStartMillis = 0;

bool forceBroadcast = true;
unsigned long lastBroadcast = 0;
String lastStatusKey = "";

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

// ---------------------------------------------------------------------------
// V8: tijdfuncties
// ---------------------------------------------------------------------------
int64_t espMs64() {                         // ms sinds boot, loopt niet over
  return esp_timer_get_time() / 1000;
}

int64_t epochNow() {
  return epochOffset + espMs64();
}

// Zelfde verloop als V7, maar berekend uit het aantal hele seconden sinds START.
// k = 0 -> 5:00, k = 300 -> 0:00, k = 301 -> stoppen (één keer) of 4:59 (herhalen).
void stepSecond(long k) {
  if (!repeatMode && k > 300) {
    counting = false;
    return;
  }

  long remaining;
  long cycle;
  if (k == 0) {
    remaining = 300;
    cycle = 0;
  } else {
    cycle = (k - 1) / 300;
    remaining = 300 - (((k - 1) % 300) + 1);
  }

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

// ---------------------------------------------------------------------------
// V8: slaapmodus vanuit de app (zelfde acties als de fysieke resetknop)
// ---------------------------------------------------------------------------
void enterSleepFromApp() {          // = reset 3 s vasthouden
  sleepMode = true;
  clearShiftRegisterDisplay();
  digitalWrite(enable_matrix, LOW);
  display.clear();
}

void exitSleepFromApp() {           // = reset indrukken in slaapmodus
  sleepMode = false;
  display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
  updateShiftRegisterDisplay(minutes, seconds);
  digitalWrite(enable_matrix, HIGH);
  lastActiveMillis = millis();
}

// ---------------------------------------------------------------------------
// V8: beheerders-PIN (gezouten SHA-256, niet als leesbare tekst opgeslagen)
// ---------------------------------------------------------------------------
void hashPin(const String &pin, uint8_t out[32]) {
  uint8_t buf[16 + 16];
  size_t n = pin.length() > 16 ? 16 : pin.length();
  memcpy(buf, pinSalt, 16);
  memcpy(buf + 16, pin.c_str(), n);
  mbedtls_sha256(buf, 16 + n, out, 0);
}

void setPin(const String &pin) {
  for (int i = 0; i < 16; i++) pinSalt[i] = (uint8_t)(esp_random() & 0xFF);
  hashPin(pin, pinHash);
  prefs.putBytes("salt", pinSalt, 16);
  prefs.putBytes("pinhash", pinHash, 32);
}

// Geeft "" terug bij een juiste PIN, anders een foutmelding.
String checkPin(const String &pin) {
  unsigned long now = millis();
  if (pinLocked) {
    if ((long)(now - pinLockUntil) < 0) {
      return "Te veel onjuiste pogingen. Probeer het over " + String((pinLockUntil - now) / 1000 + 1) + " s opnieuw.";
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
    return "";
  }
  failedPinAttempts++;
  if (failedPinAttempts >= 5) {
    pinLocked = true;
    pinLockUntil = now + 60000UL;
    return "Onjuiste PIN. Instellingen 60 s geblokkeerd.";
  }
  return "Onjuiste PIN.";
}

// ---------------------------------------------------------------------------
// V8: opslag van instellingen en geplande starts
// ---------------------------------------------------------------------------
void saveSchedules() {
  prefs.putUChar("schedn", schedCount);
  prefs.putUInt("schedid", nextSchedId);
  if (schedCount > 0) {
    prefs.putBytes("sched", schedules, sizeof(Schedule) * schedCount);
  } else {
    prefs.remove("sched");
  }
  schedVersion++;
}

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
  schedCount = prefs.getUChar("schedn", 0);
  if (schedCount > MAX_SCHED) schedCount = 0;
  nextSchedId = prefs.getUInt("schedid", 1);
  if (schedCount > 0 && prefs.getBytesLength("sched") == sizeof(Schedule) * schedCount) {
    prefs.getBytes("sched", schedules, sizeof(Schedule) * schedCount);
  } else {
    schedCount = 0;
  }
}

void removeSchedule(uint8_t index) {
  for (uint8_t i = index; i + 1 < schedCount; i++) schedules[i] = schedules[i + 1];
  schedCount--;
}

// ---------------------------------------------------------------------------
// V8: communicatie met de app (JSON over WebSocket)
// ---------------------------------------------------------------------------
void sendAck(uint8_t num, long id, bool ok, const String &error = "", long newId = -1) {
  JsonDocument doc;
  doc["type"] = "ack";
  doc["id"] = id;
  doc["ok"] = ok;
  if (!ok) doc["error"] = error;
  if (newId >= 0) doc["sched_id"] = newId;
  String out;
  serializeJson(doc, out);
  webSocket.sendTXT(num, out);
}

void broadcastEvent(const char *event, int64_t targetEpoch, const char *reason) {
  JsonDocument doc;
  doc["type"] = "event";
  doc["event"] = event;
  doc["target_epoch"] = targetEpoch;
  doc["reason"] = reason;
  String out;
  serializeJson(doc, out);
  webSocket.broadcastTXT(out);
}

String validateSchedule(int64_t pressEpoch, int64_t targetEpoch, uint8_t kind) {
  if (!timeSynced) return "Tijd van de ESP is nog niet gesynchroniseerd.";
  if (kind > 1) return "Onbekend type geplande start.";
  int64_t expectedPress = (kind == 0) ? targetEpoch - 300000LL : targetEpoch;
  if (pressEpoch != expectedPress) return "Starttijd klopt niet met het gekozen type.";
  if (pressEpoch < epochNow() + 2000) return "Dit tijdstip ligt in het verleden of te dichtbij.";
  return "";
}

void handleCommand(uint8_t num, uint8_t *payload, size_t length) {
  JsonDocument doc;
  if (deserializeJson(doc, payload, length)) return;

  const char *cmdC = doc["cmd"] | "";
  String cmd = cmdC;
  long id = doc["id"] | 0L;

  if (cmd == "start") {
    if (sleepMode) { sendAck(num, id, false, "LED-paneel staat uit. Zet het eerst aan."); return; }
    if (counting) { sendAck(num, id, false, "De procedure loopt al."); return; }
    startPressed = true;
    sendAck(num, id, true);

  } else if (cmd == "reset" || cmd == "stop") {
    resetPressed = true;
    sendAck(num, id, true);

  } else if (cmd == "horn") {
    // Handmatige toeter vanuit de app. De app herhaalt "on" zolang de knop is
    // ingedrukt; blijft dat uit (bijv. verbinding weg), dan gaat de toeter na 600 ms uit.
    bool on = doc["on"] | false;
    appHornActive = on;
    if (on) appHornUntil = millis() + 600;

  } else if (cmd == "led") {
    bool on = doc["on"] | true;
    if (on) {
      if (sleepMode) exitSleepFromApp();
      sendAck(num, id, true);
    } else {
      if (counting) { sendAck(num, id, false, "Niet mogelijk tijdens een lopende procedure."); return; }
      if (!sleepMode) enterSleepFromApp();
      sendAck(num, id, true);
    }
    forceBroadcast = true;

  } else if (cmd == "sync") {
    JsonDocument r;
    r["type"] = "sync";
    r["id"] = id;
    r["t"] = doc["t"] | 0LL;
    r["esp_ms"] = espMs64();
    String out;
    serializeJson(r, out);
    webSocket.sendTXT(num, out);

  } else if (cmd == "settime") {
    epochOffset = doc["offset_ms"] | 0LL;
    timeSynced = true;
    sendAck(num, id, true);
    forceBroadcast = true;

  } else if (cmd == "sched_add" || cmd == "sched_edit") {
    int64_t pressEpoch = doc["press_epoch"] | 0LL;
    int64_t targetEpoch = doc["target_epoch"] | 0LL;
    uint8_t kind = doc["kind"] | 0;
    String err = validateSchedule(pressEpoch, targetEpoch, kind);
    if (err.length()) { sendAck(num, id, false, err); return; }

    if (cmd == "sched_add") {
      if (schedCount >= MAX_SCHED) { sendAck(num, id, false, "Maximaal " + String(MAX_SCHED) + " geplande starts."); return; }
      Schedule s = { nextSchedId++, pressEpoch, targetEpoch, kind };
      schedules[schedCount++] = s;
      saveSchedules();
      sendAck(num, id, true, "", (long)s.id);
    } else {
      uint32_t sid = doc["sched_id"] | 0UL;
      for (uint8_t i = 0; i < schedCount; i++) {
        if (schedules[i].id == sid) {
          schedules[i].pressEpoch = pressEpoch;
          schedules[i].targetEpoch = targetEpoch;
          schedules[i].kind = kind;
          saveSchedules();
          sendAck(num, id, true, "", (long)sid);
          return;
        }
      }
      sendAck(num, id, false, "Geplande start niet gevonden.");
    }

  } else if (cmd == "sched_del") {
    uint32_t sid = doc["sched_id"] | 0UL;
    for (uint8_t i = 0; i < schedCount; i++) {
      if (schedules[i].id == sid) {
        removeSchedule(i);
        saveSchedules();
        sendAck(num, id, true);
        return;
      }
    }
    sendAck(num, id, false, "Geplande start niet gevonden.");

  } else if (cmd == "pin_check") {
    String err = checkPin(doc["pin"] | "");
    sendAck(num, id, err.length() == 0, err);

  } else if (cmd == "pin_set") {
    String err = checkPin(doc["pin"] | "");
    if (err.length()) { sendAck(num, id, false, err); return; }
    String newPin = doc["new_pin"] | "";
    bool digitsOnly = newPin.length() >= 4 && newPin.length() <= 8;
    for (unsigned int i = 0; i < newPin.length(); i++) if (!isDigit(newPin[i])) digitsOnly = false;
    if (!digitsOnly) { sendAck(num, id, false, "De PIN moet uit 4 tot 8 cijfers bestaan."); return; }
    setPin(newPin);
    sendAck(num, id, true);

  } else if (cmd == "wifi_set") {
    String err = checkPin(doc["pin"] | "");
    if (err.length()) { sendAck(num, id, false, err); return; }
    if (counting) { sendAck(num, id, false, "Niet mogelijk tijdens een lopende procedure."); return; }
    String ssid = doc["ssid"] | "";
    String pass = doc["pass"] | "";
    if (ssid.length() < 1 || ssid.length() > 32) { sendAck(num, id, false, "De netwerknaam moet 1 tot 32 tekens lang zijn."); return; }
    if (pass.length() < 8 || pass.length() > 63) { sendAck(num, id, false, "Het wachtwoord moet 8 tot 63 tekens lang zijn."); return; }
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
      appHornActive = false;
      forceBroadcast = true;
      break;
    case WStype_TEXT:
      handleCommand(num, payload, length);
      break;
    default:
      break;
  }
}

// Stuurt de status bij elke wijziging en minimaal elke seconde.
void broadcastStatus(bool relayOn, bool manualAllowed) {
  bool switchRepeat = (digitalRead(timer_switch) == HIGH);
  int clients = webSocket.connectedClients();

  String key = String(counting) + repeatMode + switchRepeat + minutes + ":" + seconds + sleepMode +
               relayOn + manualAllowed + timeSynced + "|" + schedVersion + "|" + clients;
  unsigned long now = millis();
  if (!forceBroadcast && key == lastStatusKey && now - lastBroadcast < 1000) return;
  forceBroadcast = false;
  lastStatusKey = key;
  lastBroadcast = now;
  if (clients == 0) return;

  int64_t nowMs = espMs64();
  JsonDocument doc;
  doc["type"] = "status";
  doc["fw"] = FW_VERSION;
  doc["esp_ms"] = nowMs;
  doc["running"] = counting;
  doc["start_ms"] = nowMs - (int64_t)(uint32_t)(millis() - startMillis);
  doc["repeat"] = counting ? repeatMode : switchRepeat;
  doc["switch_repeat"] = switchRepeat;
  doc["min"] = minutes;
  doc["sec"] = seconds;
  doc["led_panel"] = !sleepMode;
  doc["horn"] = relayOn;
  doc["manual_allowed"] = manualAllowed;
  doc["time_synced"] = timeSynced;
  doc["epoch_now"] = timeSynced ? epochNow() : 0;
  doc["ssid"] = apSsid;
  doc["ip"] = WiFi.softAPIP().toString();
  doc["clients"] = clients;
  doc["uptime_s"] = (long)(nowMs / 1000);
  JsonArray arr = doc["sched"].to<JsonArray>();
  for (uint8_t i = 0; i < schedCount; i++) {
    JsonObject o = arr.add<JsonObject>();
    o["id"] = schedules[i].id;
    o["press"] = schedules[i].pressEpoch;
    o["target"] = schedules[i].targetEpoch;
    o["kind"] = schedules[i].kind;
  }
  String out;
  serializeJson(doc, out);
  webSocket.broadcastTXT(out);
}

// Voert geplande starts uit zodra het tijdstip is bereikt.
void handleSchedules() {
  if (!timeSynced || schedCount == 0 || startPressed) return;
  int64_t now = epochNow();
  for (uint8_t i = 0; i < schedCount; i++) {
    if (schedules[i].pressEpoch <= now) {
      Schedule s = schedules[i];
      removeSchedule(i);
      saveSchedules();
      if (counting) {
        broadcastEvent("sched_missed", s.targetEpoch, "running");
      } else if (now - s.pressEpoch > 2000) {
        broadcastEvent("sched_missed", s.targetEpoch, "late");
      } else {
        if (sleepMode) exitSleepFromApp();
        scheduledStartMillis = (unsigned long)(uint32_t)(s.pressEpoch - epochOffset);
        scheduledStartPending = true;
        startPressed = true;
        broadcastEvent("sched_started", s.targetEpoch, "");
      }
      return;                           // één per doorgang
    }
  }
}

void setup() {
  Serial.begin(115200);

  pinMode(BUTTON_START, INPUT_PULLUP);
  pinMode(BUTTON_RESET, INPUT_PULLUP);
  pinMode(MANUAL_RELAY, INPUT_PULLUP);
  pinMode(timer_switch, INPUT_PULLUP);
  pinMode(horn_button, OUTPUT);
  pinMode(enable_matrix, OUTPUT);
  // V9: PWM-relais direct in de uit-stand zetten
  ledcAttach(RELAY_PIN, RELAY_PWM_FREQ_HZ, RELAY_PWM_BITS);
  setRelayOutput(false);
  digitalWrite(enable_matrix, HIGH);

  debouncedStart.attach(BUTTON_START);
  debouncedStart.interval(25);
  debouncedReset.attach(BUTTON_RESET);
  debouncedReset.interval(25);
  debouncedManual.attach(MANUAL_RELAY);
  debouncedManual.interval(25);

  minutes = 5;
  seconds = 0;

  display.setBrightness(0x0f);
  display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);

  pinMode(dataPin, OUTPUT);
  pinMode(clockPin, OUTPUT);
  pinMode(latchPin, OUTPUT);
  updateShiftRegisterDisplay(minutes, seconds);

  // === V8: WiFi access point en WebSocket-server ===
  loadSettings();
  WiFi.mode(WIFI_AP);
  WiFi.softAP(apSsid.c_str(), apPass.c_str());
  WiFi.setSleep(false);
  webSocket.begin();
  webSocket.onEvent(onWsEvent);
  webSocket.enableHeartbeat(5000, 3000, 2);
  Serial.printf("Timer ZZ %s - WiFi '%s' op %s:%d\n", FW_VERSION, apSsid.c_str(),
                WiFi.softAPIP().toString().c_str(), WS_PORT);

  lastActiveMillis = millis();
}

void loop() {
  unsigned long currentMillis = millis();

  webSocket.loop();                     // V8: app-commando's verwerken

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
          digitalWrite(enable_matrix, LOW);
          display.clear();
          ignoreResetUntilRelease = true;
          resetButtonHeld = false;
        } else if (sleepMode && heldDuration >= 100) {
          sleepMode = false;
          display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
          updateShiftRegisterDisplay(minutes, seconds);
          digitalWrite(enable_matrix, HIGH);
          lastActiveMillis = currentMillis;
          ignoreResetUntilRelease = true;
          resetButtonHeld = false;
        }
      } else {
        resetButtonHeld = false;
      }
    }
  }

  handleSchedules();                    // V8: geplande starts

  // V8: met teken vergelijken. Een app-commando (in webSocket.loop()) kan
  // lastActiveMillis na currentMillis zetten; zonder teken loopt de aftrekking over
  // en valt het net aangezette paneel direct weer in slaap.
  if (!counting && !sleepMode && ((long)(currentMillis - lastActiveMillis) >= 360000L)) {
    sleepMode = true;
    digitalWrite(enable_matrix, LOW);
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
        digitalWrite(enable_matrix, HIGH);
        display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
        updateShiftRegisterDisplay(minutes, seconds);
      }

      // V8: absolute starttijd (bij een geplande start: het exacte geplande moment)
      startMillis = scheduledStartPending ? scheduledStartMillis : currentMillis;
      lastElapsedSec = 0;
      currentCycle = 0;
      lastActiveMillis = currentMillis;
      forceBroadcast = true;
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
      digitalWrite(enable_matrix, HIGH);
      display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
      updateShiftRegisterDisplay(minutes, seconds);
    }

    lastActiveMillis = currentMillis;
    forceBroadcast = true;
  }

  // === V8: timer op basis van de verstreken tijd sinds START ===
  // Elke gemiste seconde wordt ingehaald (inclusief hoornsignalen), dus een
  // trage loop-doorgang kan de timer nooit laten achterlopen.
  if (counting) {
    unsigned long nowMs = millis();
    if ((long)(nowMs - startMillis) >= 0) {
      long elapsedSec = (long)((nowMs - startMillis) / 1000UL);
      bool stepped = false;
      while (counting && lastElapsedSec < elapsedSec) {
        lastElapsedSec++;
        stepSecond(lastElapsedSec);
        stepped = true;
      }

      if (stepped) {
        if (!sleepMode) {
          digitalWrite(enable_matrix, HIGH);
          display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
          updateShiftRegisterDisplay(minutes, seconds);
        }
        lastActiveMillis = currentMillis;
      }
    }
  }

  // V8: millis() i.p.v. currentMillis. In V2-V7 kon relayStartMillis (later in
  // dezelfde doorgang gezet) 1 ms groter zijn dan currentMillis; de aftrekking
  // liep dan over en het hoornsignaal werd direct weer uitgezet.
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

  // V8: toeterknop in de app telt als de handmatige toeterknop (zelfde toegestane momenten)
  bool relayShouldBeOn = (((manualRelayActive || appHornActive) && (!counting || inAllowedManualRange)) || relayActive);

  if (!counting || inAllowedManualRange) {
    digitalWrite(horn_button, LOW);
  } else {
    digitalWrite(horn_button, HIGH);
  }

  setRelayOutput(relayShouldBeOn);     // V9: servopuls i.p.v. HIGH/LOW

  // === V8: status naar de app en eventueel nieuwe WiFi-instellingen toepassen ===
  broadcastStatus(relayShouldBeOn, !counting || inAllowedManualRange);

  if (pendingWifiApply && (long)(millis() - wifiApplyAt) >= 0) {
    pendingWifiApply = false;
    WiFi.softAP(apSsid.c_str(), apPass.c_str());
  }
}

// Segment mapping
byte mapSegments(bool a, bool b, bool c, bool d, bool e, bool f, bool g, bool dp = true) {
  return (a << 5) | (b << 6) | (c << 2) | (d << 1) | (e << 0) | (f << 4) | (g << 7) | (dp << 3);
}

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

void updateShiftRegisterDisplay(int minutes, int seconds) {
  int m = minutes % 10;
  int s1 = seconds / 10;
  int s2 = seconds % 10;
  digitalWrite(latchPin, LOW);
  shiftOut(dataPin, clockPin, MSBFIRST, digits[s2]);
  shiftOut(dataPin, clockPin, MSBFIRST, digits[s1]);
  shiftOut(dataPin, clockPin, MSBFIRST, digits[m]);
  shiftOut(dataPin, clockPin, MSBFIRST, digits[s2]);
  shiftOut(dataPin, clockPin, MSBFIRST, digits[s1]);
  shiftOut(dataPin, clockPin, MSBFIRST, digits[m]);
  digitalWrite(latchPin, HIGH);
}

void clearShiftRegisterDisplay() {
  digitalWrite(latchPin, LOW);
  shiftOut(dataPin, clockPin, MSBFIRST, 0);
  shiftOut(dataPin, clockPin, MSBFIRST, 0);
  shiftOut(dataPin, clockPin, MSBFIRST, 0);
  shiftOut(dataPin, clockPin, MSBFIRST, 0);
  shiftOut(dataPin, clockPin, MSBFIRST, 0);
  shiftOut(dataPin, clockPin, MSBFIRST, 0);
  digitalWrite(latchPin, HIGH);
}
