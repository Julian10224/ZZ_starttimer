// Timer ZZ - Segmenttest voor het matrixboard (3 digits, RJ45-D-4bits-kaart)
//
// Doel: nagaan welke bit welk segment is en welk register welk digit is.
// Open de seriële monitor op 115200 baud; bij elke stap wordt gemeld wat er brandt.
// Het kleine TM1637-display toont het fase- en stapnummer, zodat het ook zonder laptop kan.
//
// Fase 1  Looplicht:      elk digit afzonderlijk, bit 0 t/m 7, daarna het volgende digit.
//                         -> laat zien welk register welk digit is.
// Fase 2  Zelfde segment: bit 0 t/m 7, steeds op alle 3 digits tegelijk.
//                         -> noteer per bit welk segment (a-g, dp) brandt.
// Fase 3  Cijfers:        0 t/m 9 op alle 3 digits met de mapping hieronder.
//                         -> controle van de ingevulde mapping.
// Fase 4  Alles aan / alles uit.
// Daarna begint de test opnieuw.
//
// Brandt bij fase 2 steeds alles BEHALVE één segment, dan is het board actief-laag:
// zet dan SEG_ACTIEF_HOOG op 0 (hier en in Timer_ZZ_V10).

#include <TM1637Display.h>

// === Pinnen (gelijk aan Timer_ZZ_V10) ===
#define CLK 18
#define DIO 19
#define dataPin  33
#define clockPin 32
#define latchPin 26
#define enable_matrix 25

#define MATRIX_ON   LOW    // enable actief-laag
#define MATRIX_OFF  HIGH

#define AANTAL_DIGITS 3
#define STAP_MS       700  // duur per stap

// === Mapping om te controleren in fase 3 (kopieer het resultaat naar Timer_ZZ_V10) ===
#define SEG_BIT_A   5
#define SEG_BIT_B   6
#define SEG_BIT_C   2
#define SEG_BIT_D   1
#define SEG_BIT_E   0
#define SEG_BIT_F   4
#define SEG_BIT_G   7
#define SEG_BIT_DP  3
#define SEG_ACTIEF_HOOG 1

TM1637Display display(CLK, DIO);

// Schrijft drie bytes naar het board. regs[0] wordt als eerste ingeschoven
// (in Timer_ZZ_V10 is dat de eenheden van de seconden).
void schrijf(const byte regs[AANTAL_DIGITS]) {
  digitalWrite(latchPin, LOW);
  for (int i = 0; i < AANTAL_DIGITS; i++) {
    byte v = SEG_ACTIEF_HOOG ? regs[i] : (byte)~regs[i];
    shiftOut(dataPin, clockPin, MSBFIRST, v);
  }
  digitalWrite(latchPin, HIGH);
}

void alle(byte v) {
  byte regs[AANTAL_DIGITS];
  for (int i = 0; i < AANTAL_DIGITS; i++) regs[i] = v;
  schrijf(regs);
}

// Fase en stap op het TM1637-display, bijv. "2-05" (fase 2, stap 5)
void toonStap(int fase, int stap) {
  uint8_t seg[4] = {
    display.encodeDigit(fase),
    0x40,                            // streepje
    display.encodeDigit((stap / 10) % 10),
    display.encodeDigit(stap % 10)
  };
  display.setSegments(seg);
}

byte cijfer(int n) {
  static const bool t[10][7] = {
    {1,1,1,1,1,1,0}, {0,1,1,0,0,0,0}, {1,1,0,1,1,0,1}, {1,1,1,1,0,0,1}, {0,1,1,0,0,1,1},
    {1,0,1,1,0,1,1}, {1,0,1,1,1,1,1}, {1,1,1,0,0,0,0}, {1,1,1,1,1,1,1}, {1,1,1,1,0,1,1}
  };
  const bool *s = t[n];
  return (s[0] << SEG_BIT_A) | (s[1] << SEG_BIT_B) | (s[2] << SEG_BIT_C) | (s[3] << SEG_BIT_D) |
         (s[4] << SEG_BIT_E) | (s[5] << SEG_BIT_F) | (s[6] << SEG_BIT_G);
}

void setup() {
  Serial.begin(115200);
  pinMode(dataPin, OUTPUT);
  pinMode(clockPin, OUTPUT);
  pinMode(latchPin, OUTPUT);
  pinMode(enable_matrix, OUTPUT);
  digitalWrite(enable_matrix, MATRIX_ON);
  display.setBrightness(0x0f);
  alle(0);
  delay(500);
  Serial.println();
  Serial.println("Timer ZZ segmenttest - 3 digits");
}

void loop() {
  // Fase 1: looplicht, per digit bit 0..7
  Serial.println("\n== Fase 1: looplicht per digit ==");
  for (int d = 0; d < AANTAL_DIGITS; d++) {
    for (int bit = 0; bit < 8; bit++) {
      byte regs[AANTAL_DIGITS] = {0};
      regs[d] = 1 << bit;
      schrijf(regs);
      toonStap(1, d * 10 + bit);
      Serial.printf("Register %d (%s ingeschoven), bit %d\n", d + 1,
                    d == 0 ? "eerst" : (d == AANTAL_DIGITS - 1 ? "laatst" : "als tweede"), bit);
      delay(STAP_MS);
    }
  }

  // Fase 2: zelfde bit op alle digits
  Serial.println("\n== Fase 2: zelfde segment op alle digits ==");
  Serial.println("Noteer per bit welk segment brandt (a=boven, b=rechtsboven, c=rechtsonder,");
  Serial.println("d=onder, e=linksonder, f=linksboven, g=midden, dp=punt).");
  for (int bit = 0; bit < 8; bit++) {
    alle(1 << bit);
    toonStap(2, bit);
    Serial.printf("Bit %d op alle digits\n", bit);
    delay(STAP_MS * 2);
  }

  // Fase 3: cijfers 0..9 met de ingevulde mapping
  Serial.println("\n== Fase 3: cijfers 0-9 met de ingevulde mapping ==");
  for (int n = 0; n <= 9; n++) {
    alle(cijfer(n));
    toonStap(3, n);
    Serial.printf("Cijfer %d\n", n);
    delay(STAP_MS);
  }

  // Fase 4: alles aan, alles uit
  Serial.println("\n== Fase 4: alles aan / alles uit ==");
  alle(0xFF);
  toonStap(4, 1);
  delay(STAP_MS * 2);
  alle(0x00);
  toonStap(4, 0);
  delay(STAP_MS);
}
