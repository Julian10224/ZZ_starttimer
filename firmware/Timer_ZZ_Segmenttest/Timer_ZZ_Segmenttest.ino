// Timer ZZ - Segmenttest voor het matrixboard (3 digits, RJ45-D-4bits-kaart)
//
// Doel: nagaan welke bit welk segment is.
// Het kleine TM1637-display toont fase en stap, bijv. "2-05" = fase 2, bit 5.
// Geen seriële uitvoer.
//
// Fase 1  Looplicht:      van links naar rechts (minuten, tientallen, eenheden),
//                         per digit bit 0 t/m 7. TM1637: "1-db" = digit d (1 = links), bit b.
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
#define SEG_BIT_A   1
#define SEG_BIT_B   2
#define SEG_BIT_C   6
#define SEG_BIT_D   5
#define SEG_BIT_E   4
#define SEG_BIT_F   0
#define SEG_BIT_G   3
#define SEG_BIT_DP  7
#define SEG_ACTIEF_HOOG 1

TM1637Display display(CLK, DIO);

// Schrijft drie bytes naar het board. regs[0] wordt als eerste ingeschoven en komt in
// het verste register: het rechter digit (eenheden seconden), net als in de timer.
// regs[AANTAL_DIGITS - 1] wordt als laatste ingeschoven: het linker digit (minuten).
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
  pinMode(dataPin, OUTPUT);
  pinMode(clockPin, OUTPUT);
  pinMode(latchPin, OUTPUT);
  pinMode(enable_matrix, OUTPUT);
  digitalWrite(enable_matrix, MATRIX_ON);
  display.setBrightness(0x0f);
  alle(0);
  delay(500);
}

void loop() {
  // Fase 1: looplicht van links naar rechts, per digit bit 0..7
  for (int pos = 0; pos < AANTAL_DIGITS; pos++) {       // pos 0 = links (minuten)
    int reg = AANTAL_DIGITS - 1 - pos;                   // links = laatst ingeschoven
    for (int bit = 0; bit < 8; bit++) {
      byte regs[AANTAL_DIGITS] = {0};
      regs[reg] = 1 << bit;
      schrijf(regs);
      toonStap(1, (pos + 1) * 10 + bit);
      delay(STAP_MS);
    }
  }

  // Fase 2: zelfde bit op alle digits
  // Noteer per bit welk segment brandt (a=boven, b=rechtsboven, c=rechtsonder,
  // d=onder, e=linksonder, f=linksboven, g=midden, dp=punt).
  for (int bit = 0; bit < 8; bit++) {
    alle(1 << bit);
    toonStap(2, bit);
    delay(STAP_MS * 2);
  }

  // Fase 3: cijfers 0..9 met de ingevulde mapping
  for (int n = 0; n <= 9; n++) {
    alle(cijfer(n));
    toonStap(3, n);
    delay(STAP_MS);
  }

  // Fase 4: alles aan, alles uit
  alle(0xFF);
  toonStap(4, 1);
  delay(STAP_MS * 2);
  alle(0x00);
  toonStap(4, 0);
  delay(STAP_MS);
}
