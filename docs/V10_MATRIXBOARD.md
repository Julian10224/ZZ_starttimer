# V10: nieuw matrixboard (3 digits, RJ45-D-4bits-kaart)

V10 is V9 (met PWM-relais) met drie wijzigingen voor het nieuwe displayboard. Voor de rest (timer, app, hoorn, geplande starts) is V10 gelijk aan V9.

| Wijziging | V9 | V10 |
|---|---|---|
| Enable matrixboard (GPIO 25) | `HIGH` = aan, `LOW` = uit | `LOW` = aan, `HIGH` = uit (`MATRIX_ON` / `MATRIX_OFF`) |
| Aantal digits in de keten | 6 (twee displays) | 3 |
| Segmentmapping | vast in de code | instelbaar met `SEG_BIT_A` … `SEG_BIT_DP` bovenaan de sketch |

Extra instellingen bovenaan de sketch:

- `SEG_DP_AAN`: decimale punt altijd aan (`1`, zoals V3–V9) of uit (`0`).
- `SEG_ACTIEF_HOOG`: `1` als een segment brandt bij bit = 1, `0` als het board omgekeerd werkt.

## Mapping bepalen

Voor de "RJ45-D-4bits"-kaart heb ik geen datasheet of mapping kunnen vinden. De standaardwaarden in V10 zijn daarom die van V3–V9. Bepaal de juiste mapping met de sketch **`firmware/Timer_ZZ_Segmenttest`**:

1. Upload `Timer_ZZ_Segmenttest` en open de seriële monitor (115200 baud). Het kleine TM1637-display toont ook fase en stap, bijvoorbeeld `2-05`.
2. **Fase 1, looplicht:** per register bit 0 t/m 7. Hier zie je welk register welk digit is. In V10 wordt de eenheden-seconde als eerste ingeschoven, de minuut als laatste.
3. **Fase 2, zelfde segment:** bit 0 t/m 7 op alle 3 digits tegelijk. Noteer per bit welk segment brandt:

   | Bit | Segment |
   |---|---|
   | 0 | |
   | 1 | |
   | 2 | |
   | 3 | |
   | 4 | |
   | 5 | |
   | 6 | |
   | 7 | |

   Segmenten: a = boven, b = rechtsboven, c = rechtsonder, d = onder, e = linksonder, f = linksboven, g = midden, dp = punt.

4. Vul de bitnummers in bij `SEG_BIT_A` … `SEG_BIT_DP`, in de testsketch én in `Timer_ZZ_V10`.
5. **Fase 3, cijfers:** 0 t/m 9 met de ingevulde mapping. Kloppen alle cijfers, dan is de mapping goed.
6. **Fase 4:** alles aan, dan alles uit.

Brandt in fase 2 steeds alles **behalve** één segment, dan werkt het board omgekeerd. Zet dan `SEG_ACTIEF_HOOG` op `0`.

Staan de digits in de verkeerde volgorde (bijvoorbeeld de minuut rechts), pas dan in `updateShiftRegisterDisplay()` de volgorde van de drie `shiftOut`-regels aan.

Beide sketches compileren met ESP32-core 3.3.12: V10 gebruikt 951 110 bytes (72%), de testsketch 273 872 bytes (20%).
