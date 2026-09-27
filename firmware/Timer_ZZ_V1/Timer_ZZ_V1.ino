#include <TM1637Display.h>

#include <Arduino.h>

// Pin configuratie
#define CLK 18
#define DIO 19
#define BUTTON_START 34
#define BUTTON_RESET 35
#define manual_relay
#define RELAY_PIN 16

TM1637Display display(CLK, DIO);

// Variabelen voor tijd
volatile bool startPressed = false;
volatile bool resetPressed = false;
volatile bool manual_relay = false;

bool countingDown = false;
int minutes = 5;
int seconds = 0;
int relayDuration = 2000;

// Houd bij of relais al is geactiveerd voor deze tijdstippen
bool relayTriggered[6] = {false, false, false, false, false, false};

unsigned long previousMillis = 0;
unsigned long interval = 1000;

void IRAM_ATTR onStartPress() {
  startPressed = true;
}

void IRAM_ATTR onResetPress() {
  resetPressed = true;
}

void setup() {
  pinMode(BUTTON_START, INPUT_PULLUP);
  pinMode(BUTTON_RESET, INPUT_PULLUP);
  pinMode(manual_relay, INPUT_PULLUP);
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, LOW);

  attachInterrupt(digitalPinToInterrupt(BUTTON_START), onStartPress, FALLING);
  attachInterrupt(digitalPinToInterrupt(BUTTON_RESET), onResetPress, FALLING);
  attachInterrupt(digitalPinToInterrupt(manual_relay), onStartPress, FALLING);

  display.setBrightness(0x0f);
  display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
}

bool relayActive = false;
unsigned long relayStartMillis = 0;


void loop() {
  // Afhandeling van interrupts
  if (startPressed) {
    startPressed = false;
    countingDown = true;
    activateRelayNonBlocking();
    relayTriggered[5] = true;
  }

  if (resetPressed) {
    resetPressed = false;
    countingDown = false;
    minutes = 5;
    seconds = 0;
    for (int i = 0; i < 6; i++) relayTriggered[i] = false;
    display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
  }

  unsigned long currentMillis = millis();

  // Niet-blokkerende relay-deactivatie
  if (relayActive && currentMillis - relayStartMillis >= relayDuration) {
    digitalWrite(RELAY_PIN, LOW);
    relayActive = false;
  }

  // Countdown-timer
  if (countingDown && currentMillis - previousMillis >= interval) {
    previousMillis = currentMillis;

    if (seconds == 0) {
      if (minutes > 0) {
        minutes--;
        seconds = 59;
      } else {
        seconds = 0;
        countingDown = false;
      }
    } else {
      seconds--;
    }

    display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);

    // Relay activatie op exacte tijdstippen
    if ((seconds == 0) && (
        (minutes == 4 && !relayTriggered[4]) ||
        (minutes == 1 && !relayTriggered[1]) ||
        (minutes == 0 && !relayTriggered[0])
      )) {
        activateRelayNonBlocking();
        relayTriggered[minutes] = true;
    }
  }
}

void activateRelayNonBlocking() {
  digitalWrite(RELAY_PIN, HIGH);
  relayStartMillis = millis();
  relayActive = true;
}

