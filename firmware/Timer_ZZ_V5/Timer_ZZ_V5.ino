#include <TM1637Display.h>
#include <Arduino.h>
#include <Bounce2.h>

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
bool countingUp = false;
bool relayActive = false;
bool manualRelayActive = false;
bool ignoreResetUntilRelease = false;

int minutes = 5;
int seconds = 0;

bool triggeredRelay[6] = {false, false, false, false, false, false};

unsigned long previousMillis = 0;
unsigned long relayStartMillis = 0;

// === Slaapmodus variabelen ===
bool sleepMode = false;
unsigned long resetPressStart = 0;
bool resetButtonHeld = false;
unsigned long lastActiveMillis = 0;

void setup() {
  Serial.begin(115200);

  pinMode(BUTTON_START, INPUT_PULLUP);
  pinMode(BUTTON_RESET, INPUT_PULLUP);
  pinMode(MANUAL_RELAY, INPUT_PULLUP);
  pinMode(timer_switch, INPUT_PULLUP);
  pinMode(RELAY_PIN, OUTPUT);
  pinMode(horn_button, OUTPUT);
  pinMode(enable_matrix, OUTPUT);
  digitalWrite(RELAY_PIN, LOW);
  digitalWrite(enable_matrix, HIGH);

  // Bounce setup
  debouncedStart.attach(BUTTON_START);
  debouncedStart.interval(25);

  debouncedReset.attach(BUTTON_RESET);
  debouncedReset.interval(25);

  debouncedManual.attach(MANUAL_RELAY);
  debouncedManual.interval(25);

  countingUp = (digitalRead(timer_switch) == HIGH);
  minutes = countingUp ? 0 : 5;
  seconds = 0;

  display.setBrightness(0x0f);
  display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);

  pinMode(dataPin, OUTPUT);
  pinMode(clockPin, OUTPUT);
  pinMode(latchPin, OUTPUT);
  updateShiftRegisterDisplay(minutes, seconds);

  lastActiveMillis = millis();
}

void loop() {
  unsigned long currentMillis = millis();

  // Update debounce states
  debouncedStart.update();
  debouncedReset.update();
  debouncedManual.update();

  // Start knop detectie
  if (debouncedStart.fell()) {
    startPressed = true;
  }

  // Reset knop detectie en houd logica
  bool resetButtonState = (debouncedReset.read() == LOW);

  if (ignoreResetUntilRelease && !resetButtonState) {
    // Knop is losgelaten, we mogen weer reageren op nieuwe input
    ignoreResetUntilRelease = false;
  }

  if (!ignoreResetUntilRelease) {
    if (resetButtonState && !resetButtonHeld) {
      // Knop net ingedrukt → direct reset triggeren
      resetButtonHeld = true;
      resetPressStart = currentMillis;

      if (!sleepMode) {
        resetPressed = true;  // Reset direct bij indrukken
      }
    }

    if (resetButtonHeld) {
      unsigned long heldDuration = currentMillis - resetPressStart;

      if (resetButtonState) {
        if (!sleepMode && !counting && heldDuration >= 3000) {
          // Zet in slaapmodus
          sleepMode = true;
          clearShiftRegisterDisplay();
          digitalWrite(enable_matrix, LOW);
          display.clear();

          // Wacht op loslaten knop
          ignoreResetUntilRelease = true;
          resetButtonHeld = false;

        } else if (sleepMode && heldDuration >= 100) {
          // Haal uit slaapmodus
          sleepMode = false;
          display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
          updateShiftRegisterDisplay(minutes, seconds);
          digitalWrite(enable_matrix, HIGH);
          lastActiveMillis = currentMillis;

          // Wacht op loslaten knop
          ignoreResetUntilRelease = true;
          resetButtonHeld = false;
        }
      } else {
        // Knop losgelaten, reset de flags
        resetButtonHeld = false;
      }
    }
  }

  // === Automatische slaap na 5 minuten inactiviteit ===
  if (!counting && !sleepMode && (currentMillis - lastActiveMillis >= 360000)) {
    sleepMode = true;
    digitalWrite(enable_matrix, LOW);
    clearShiftRegisterDisplay();
    display.clear();
  }

  // === START knop logica ===
  if (startPressed) {
    startPressed = false;
    if (!sleepMode && !counting) {
      counting = true;
      countingUp = (digitalRead(timer_switch) == HIGH);
      minutes = countingUp ? 0 : 5;
      seconds = 0;
      for (int i = 0; i < 6; i++) triggeredRelay[i] = false;

      if (!countingUp) {
        activateRelayNonBlocking();
        triggeredRelay[5] = true;
      }

      if (!sleepMode) {
        digitalWrite(enable_matrix, HIGH);
        display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
        updateShiftRegisterDisplay(minutes, seconds);
      }

      previousMillis = currentMillis;
      lastActiveMillis = currentMillis;
    }
  }

  // === RESET knop logica ===
  if (resetPressed) {
    resetPressed = false;
    counting = false;
    relayActive = false;
    countingUp = (digitalRead(timer_switch) == HIGH);
    minutes = countingUp ? 0 : 5;
    seconds = 0;
    for (int i = 0; i < 6; i++) triggeredRelay[i] = false;

    if (!sleepMode) {
      digitalWrite(enable_matrix, HIGH);
      display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
      updateShiftRegisterDisplay(minutes, seconds);
    }

    lastActiveMillis = currentMillis;
  }

  // === Timer update ===
  if (counting && currentMillis - previousMillis >= 1000) {
    previousMillis += 1000;

    if (countingUp) {
      seconds++;
      if (seconds >= 60) {
        seconds = 0;
        minutes++;
      }
      if (minutes == 4 && seconds == 0) {
        minutes = 5;
        seconds = 0;
        countingUp = false;
        activateRelayNonBlocking();
        triggeredRelay[5] = true;
      }
    } else {
      if (seconds == 0) {
        if (minutes > 0) {
          minutes--;
          seconds = 59;
        } else {
          seconds = 0;
          counting = false;
        }
      } else {
        seconds--;
      }

      if (seconds == 0 && (
            (minutes == 5 && !triggeredRelay[5]) ||
            (minutes == 4 && !triggeredRelay[4]) ||
            (minutes == 1 && !triggeredRelay[1]) ||
            (minutes == 0 && !triggeredRelay[0])
          )) {
        activateRelayNonBlocking();
        triggeredRelay[minutes] = true;
      }
    }

    if (!sleepMode){
        digitalWrite(enable_matrix, HIGH);
        display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
        updateShiftRegisterDisplay(minutes, seconds);
    }

    lastActiveMillis = currentMillis;
  }

  if ((currentMillis - relayStartMillis >= 2000)) {
    relayActive = false;
  }

  if (!counting && !sleepMode && !manualRelayActive) {
    countingUp = (digitalRead(timer_switch) == HIGH);
    minutes = countingUp ? 0 : 5;
    seconds = 0;
    display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
    updateShiftRegisterDisplay(minutes, seconds);
  }


manualRelayActive = (debouncedManual.read() == LOW);

// Check of we tussen 5:00 en 4:00 zitten tijdens aftellen
bool inAllowedManualRange = counting && !countingUp &&
                            ((minutes == 5) || (minutes == 4 && seconds == 0) || (minutes == 4 && seconds > 0));

bool relayShouldBeOn = ((manualRelayActive && (!counting || inAllowedManualRange)) || relayActive);


  if (!counting ||inAllowedManualRange) {
    digitalWrite(horn_button, LOW);
  } else {
    digitalWrite(horn_button, HIGH);
  }

  digitalWrite(RELAY_PIN, relayShouldBeOn ? HIGH : LOW);
}

void activateRelayNonBlocking() {
  relayActive = true;
  relayStartMillis = millis();
}

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
  digitalWrite(latchPin, HIGH);
}

void clearShiftRegisterDisplay() {
  digitalWrite(latchPin, LOW);
  shiftOut(dataPin, clockPin, MSBFIRST, 0);
  shiftOut(dataPin, clockPin, MSBFIRST, 0);
  shiftOut(dataPin, clockPin, MSBFIRST, 0);
  digitalWrite(latchPin, HIGH);
}
