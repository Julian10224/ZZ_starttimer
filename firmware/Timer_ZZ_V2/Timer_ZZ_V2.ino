#include <TM1637Display.h>
#include <Arduino.h>

// Pin configuratie
#define CLK 18
#define DIO 19
#define BUTTON_START 23
#define BUTTON_RESET 13
#define MANUAL_RELAY 14
#define RELAY_PIN 27

TM1637Display display(CLK, DIO);

// Status en timing
volatile bool startPressed = false;
volatile bool resetPressed = false;

bool counting = false;
bool countingUp = false;
bool blinking = false;
bool relayActive = false;

int minutes = 5;
int seconds = 0;

bool triggeredRelay[6] = {false, false, false, false, false, false};

unsigned long previousMillis = 0;
unsigned long relayStartMillis = 0;
unsigned long blinkStartMillis = 0;
unsigned long lastBlinkToggle = 0;
bool displayOn = true;

// Interrupt handlers
void IRAM_ATTR onStartPress() {
  startPressed = true;
}

void IRAM_ATTR onResetPress() {
  resetPressed = true;
}

void setup() {
  pinMode(BUTTON_START, INPUT_PULLUP);
  pinMode(BUTTON_RESET, INPUT_PULLUP);
  pinMode(MANUAL_RELAY, INPUT_PULLUP);
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, LOW);

  attachInterrupt(digitalPinToInterrupt(BUTTON_START), onStartPress, FALLING);
  attachInterrupt(digitalPinToInterrupt(BUTTON_RESET), onResetPress, FALLING);

  display.setBrightness(0x0f);
  display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
}

void loop() {
  unsigned long currentMillis = millis();

  // Startknop
  if (startPressed) {
    startPressed = false;
    if (!counting) {
      counting = true;
      countingUp = false;
      minutes = 5;
      seconds = 0;
      activateRelayNonBlocking();
      triggeredRelay[5] = true;
      for (int i = 0; i < 6; i++) triggeredRelay[i] = false;
    }
  }

  // Resetknop
  if (resetPressed) {
    resetPressed = false;
    counting = false;
    blinking = false;
    countingUp = false;
    relayActive = false;
    minutes = 5;
    seconds = 0;
    displayOn = true;
    for (int i = 0; i < 6; i++) triggeredRelay[i] = false;
    display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
  }

  // Tijd bijwerken elke seconde
  if (counting && currentMillis - previousMillis >= 1000) {
    previousMillis = currentMillis;

    if (!countingUp) {
      // Aftellen
      if (seconds == 0) {
        if (minutes > 0) {
          minutes--;
          seconds = 59;
        } else {
          // 0:00 bereikt
          seconds = 0;
          countingUp = true;
          blinking = true;
          blinkStartMillis = currentMillis;
          lastBlinkToggle = currentMillis;
        }
      } else {
        seconds--;
      }

      // Relay activeren bij 5, 4, 1, 0 minuten (alleen tijdens aftellen)
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
      // Optellen vanaf 0:00
      seconds++;
      if (seconds >= 60) {
        seconds = 0;
        minutes++;
      }

      // Stop bij 4:00
      if (minutes == 4 && seconds == 0) {
        counting = false;
      }
    }

    if (!blinking) {
      display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
    }
  }

  // Knipperen bij 0:00 voor 10 seconden
  if (blinking) {
    if (currentMillis - blinkStartMillis >= 3000) {
      blinking = false;
      displayOn = true;
      display.showNumberDecEx(minutes * 100 + seconds, 0b01000000, true);
    } else if (currentMillis - lastBlinkToggle >= 500) {
      lastBlinkToggle = currentMillis;
      displayOn = !displayOn;
      if (displayOn) {
        display.showNumberDecEx(0 * 100 + 0, 0b01000000, true);
      } else {
        display.clear();
      }
    }
  }

  // Relay automatisch uitschakelen na 2 seconden
  if (relayActive && currentMillis - relayStartMillis >= 2000) {
    relayActive = false;
  }

  // Relay sturen (handmatig of automatisch)
  if(countingUp == true || counting == false) {
     bool manualRelayActive = (digitalRead(MANUAL_RELAY) == LOW);
  }
 
  bool relayShouldBeOn = manualRelayActive || relayActive;
  digitalWrite(RELAY_PIN, relayShouldBeOn ? HIGH : LOW);
}

// Relay activatie met timer
void activateRelayNonBlocking() {
  relayActive = true;
  relayStartMillis = millis();
}
