#include <TM1637Display.h>
#include <Arduino.h>

// === TM1637 Display Pin configuratie ===
#define CLK 18
#define DIO 19

// === Matrix board Pin configuratie ===
#define dataPin  33
#define clockPin 32
#define latchPin 26
#define enable 25

// === Knoppen en relais pin configuratie ===
#define BUTTON_START 23
#define BUTTON_RESET 13
#define MANUAL_RELAY 14
#define RELAY_PIN 27
#define horn_button 21
#define timer_switch 22

#define DP_ALWAYS_ON 1  // Zet alle decimal points permanent aan

TM1637Display display(CLK, DIO);
byte mapSegments(bool a, bool b, bool c, bool d, bool e, bool f, bool g, bool dp = true) {
  // Nieuwe bitvolgorde:
  // a = bit 5
  // b = bit 6
  // c = bit 2
  // d = bit 1
  // e = bit 0
  // f = bit 4
  // g = bit 7
  // dp = bit 3
  return
    (a  << 5) |  // segment a → bit 5
    (b  << 6) |  // segment b → bit 6
    (c  << 2) |  // segment c → bit 2
    (d  << 1) |  // segment d → bit 1
    (e  << 0) |  // segment e → bit 0
    (f  << 4) |  // segment f → bit 4
    (g  << 7) |  // segment g → bit 7
    (dp << 3);   // decimal point → bit 3
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
  shiftOut(dataPin, clockPin, MSBFIRST, digits[s2]);   // 1e (links)
  shiftOut(dataPin, clockPin, MSBFIRST, digits[s1]);  // 2e (midden)
  shiftOut(dataPin, clockPin, MSBFIRST, digits[m]);  // 3e (rechts)
  digitalWrite(latchPin, HIGH);
}

void clearShiftRegisterDisplay() {
  digitalWrite(latchPin, LOW);
  shiftOut(dataPin, clockPin, MSBFIRST, 0);
  shiftOut(dataPin, clockPin, MSBFIRST, 0);
  shiftOut(dataPin, clockPin, MSBFIRST, 0);
  digitalWrite(latchPin, HIGH);
}

// === Status en timing ===
volatile bool startPressed = false;
volatile bool resetPressed = false;

bool counting = false;
bool countingUp = false;
bool zero = false;
bool relayActive = false;
bool manualRelayActive = false;

int minutes = 5;
int seconds = 0;

bool triggeredRelay[6] = {false, false, false, false, false, false};

unsigned long previousMillis = 0;
unsigned long relayStartMillis = 0;
unsigned long zeroStartMillis = 0;

// === Interrupt handlers ===
void IRAM_ATTR onStartPress() {
  startPressed = true;
}

void IRAM_ATTR onResetPress() {
  resetPressed = true;
}

void setup() {
  Serial.begin(115200);

  pinMode(BUTTON_START, INPUT_PULLUP);
  pinMode(BUTTON_RESET, INPUT_PULLUP);
  pinMode(MANUAL_RELAY, INPUT_PULLUP);
  pinMode(timer_switch, INPUT_PULLUP);
  pinMode(RELAY_PIN, OUTPUT);
  pinMode(horn_button, OUTPUT);
  pinMode(enable, OUTPUT);
  digitalWrite(RELAY_PIN, LOW);
  digitalWrite(enable, HIGH);

  attachInterrupt(digitalPinToInterrupt(BUTTON_START), onStartPress, FALLING);
  attachInterrupt(digitalPinToInterrupt(BUTTON_RESET), onResetPress, FALLING);

  display.setBrightness(0x0f);
  display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);

  pinMode(dataPin, OUTPUT);
  pinMode(clockPin, OUTPUT);
  pinMode(latchPin, OUTPUT);
  updateShiftRegisterDisplay(minutes, seconds);
}

void loop() {
  unsigned long currentMillis = millis();

  if (startPressed) {
    startPressed = false;
    if (!counting) {
      counting = true;
      countingUp = false;
      minutes = 5;
      seconds = 0;
      display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
      updateShiftRegisterDisplay(minutes, seconds);
      for (int i = 0; i < 6; i++) triggeredRelay[i] = false;
      activateRelayNonBlocking();
      previousMillis = millis();
      currentMillis = millis();
      triggeredRelay[5] = true;
    }
  }

  if (resetPressed) {
    resetPressed = false;
    counting = false;
    zero = false;
    countingUp = false;
    relayActive = false;
    minutes = 5;
    seconds = 0;
    for (int i = 0; i < 6; i++) triggeredRelay[i] = false;
    display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
    updateShiftRegisterDisplay(minutes, seconds);
  }

  if (counting && currentMillis - previousMillis >= 1000) {
    previousMillis = currentMillis;

    if (!countingUp) {
      if (seconds == 0) {
        if (minutes > 0) {
          minutes--;
          seconds = 59;
        } else {
          seconds = 0;
          countingUp = true;
          zero = true;
          zeroStartMillis = currentMillis;
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
    } else {
      seconds++;
      if (seconds >= 60) {
        seconds = 0;
        minutes++;
      }

      if (digitalRead(timer_switch) == LOW) {
        if (minutes == 5 && seconds == 0) {
          counting = false;
        }
      } else {
        if (minutes == 4 && seconds == 0) {
          counting = false;
        }
      }
    }

    if (zero) {
      if ((currentMillis - zeroStartMillis) >= 1500) {
        zero = false;
        display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
        updateShiftRegisterDisplay(minutes, seconds);
      } else {
        display.showNumberDecEx(0, 0b01000000, true);
        updateShiftRegisterDisplay(0, 0);
      }
    } else {
      display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
      updateShiftRegisterDisplay(minutes, seconds);
    }
  }

  if ((currentMillis - relayStartMillis >= 2000)) {
    relayActive = false;
  }

  if (countingUp || !counting) {
    digitalWrite(horn_button, HIGH);
    manualRelayActive = (digitalRead(MANUAL_RELAY) == LOW);
  } else {
    digitalWrite(horn_button, LOW);
  }

  bool relayShouldBeOn = manualRelayActive || relayActive;
  digitalWrite(RELAY_PIN, relayShouldBeOn ? HIGH : LOW);
}

// === Functies voor relais ===
void activateRelayNonBlocking() {
  relayActive = true;
  relayStartMillis = millis();
  Serial.println("activateRelayNonBlocking(): relayActive op TRUE gezet");
}

// === Functies voor shift-register display ===
