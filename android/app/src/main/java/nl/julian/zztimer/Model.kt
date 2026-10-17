package nl.julian.zztimer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Eén geplande start zoals de ESP die bewaart. */
data class Schedule(
    val id: Long,
    val pressEpoch: Long,   // moment waarop de ESP op START drukt
    val targetEpoch: Long,  // tijd die de gebruiker koos
    val kind: Int,          // 0 = startschot (0:00) om, 1 = procedure start om
    val procS: Int = TimerMath.PROCEDURE_S,  // 300 = 5 minuten, 180 = 3 minuten doorstart
)

/** Status zoals de ESP die stuurt (zie docs/PROTOCOL.md). */
data class EspStatus(
    val firmware: String,
    val espMs: Long,
    val running: Boolean,
    val startMs: Long,
    val repeat: Boolean,
    val switchRepeat: Boolean,
    val procS: Int,         // lopende procedure, in rust de stand van de schakelaar (300 of 180)
    val switchProcS: Int,   // stand van de schakelaar: open = 300 (5 min), dicht = 180 (3 min)
    val minutes: Int,
    val seconds: Int,
    val ledPanel: Boolean,
    val horn: Boolean,
    val manualAllowed: Boolean,
    val timeSynced: Boolean,
    val ssid: String,
    val ip: String,
    val clients: Int,
    val uptimeS: Long,
    val schedules: List<Schedule>,
)

object TimerMath {
    const val PROCEDURE_S = 300   // 5 minuten doorstart
    const val SHORT_S = 180       // 3 minuten doorstart

    /** Alleen 300 en 180 zijn geldig; al het andere (oude firmware) telt als 5 minuten. */
    fun proc(p: Int): Int = if (p == SHORT_S) SHORT_S else PROCEDURE_S

    /** "5 min" of "3 min". */
    fun procLabel(p: Int): String = "${proc(p) / 60} min"

    /**
     * Resterende seconden, precies zoals stepSecond() in de firmware, met P = [procS]:
     * k = 0 -> P, k = P -> 0:00, k = P + 1 -> gestopt (één keer, oude firmware) of P - 1 (doorstart).
     * Geeft null terug als een eenmalige procedure is afgelopen.
     */
    fun remainingSeconds(elapsedMs: Long, repeat: Boolean, procS: Int = PROCEDURE_S): Int? {
        val p = proc(procS)
        if (elapsedMs < 1000) return p
        val k = elapsedMs / 1000
        if (!repeat && k > p) return null
        return (p - (((k - 1) % p) + 1)).toInt()
    }

    /** Zelfde formaat als het TM1637-display: MM:SS. */
    fun format(remaining: Int): String =
        String.format(Locale.ROOT, "%02d:%02d", remaining / 60, remaining % 60)

    fun raceText(status: EspStatus?, espNow: Long?): String {
        if (status == null) return "––:––"  // en-streepjes: breed genoeg naast de cijfers
        val p = proc(status.procS)
        if (!status.running || espNow == null) return format(p)
        val rem = remainingSeconds(espNow - status.startMs, status.repeat, p) ?: p
        return format(rem)
    }
}

object TimeFormat {
    private val nl: Locale = Locale.forLanguageTag("nl-NL")
    private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", nl)
    private val day: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", nl)

    fun time(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(clock)

    /** "vandaag", "morgen" of een datum. */
    fun dayLabel(epochMs: Long): String {
        val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when (date) {
            today -> "vandaag"
            today.plusDays(1) -> "morgen"
            else -> date.format(day)
        }
    }

    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.ROOT, "%d u %02d min", h, m)
        else String.format(Locale.ROOT, "%d min %02d s", m, s)
    }
}
