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
)

/** Status zoals de ESP die stuurt (zie docs/PROTOCOL.md). */
data class EspStatus(
    val firmware: String,
    val espMs: Long,
    val running: Boolean,
    val startMs: Long,
    val repeat: Boolean,
    val switchRepeat: Boolean,
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
    const val PROCEDURE_S = 300

    /**
     * Resterende seconden, precies zoals stepSecond() in de firmware:
     * k = 0 -> 5:00, k = 300 -> 0:00, k = 301 -> gestopt (één keer) of 4:59 (herhalen).
     * Geeft null terug als een eenmalige procedure is afgelopen.
     */
    fun remainingSeconds(elapsedMs: Long, repeat: Boolean): Int? {
        if (elapsedMs < 1000) return PROCEDURE_S
        val k = elapsedMs / 1000
        if (!repeat && k > PROCEDURE_S) return null
        return (PROCEDURE_S - (((k - 1) % PROCEDURE_S) + 1)).toInt()
    }

    /** Zelfde formaat als het TM1637-display: MM:SS. */
    fun format(remaining: Int): String =
        String.format(Locale.ROOT, "%02d:%02d", remaining / 60, remaining % 60)

    fun raceText(status: EspStatus?, espNow: Long?): String {
        if (status == null) return "--:--"
        if (!status.running || espNow == null) return format(PROCEDURE_S)
        val rem = remainingSeconds(espNow - status.startMs, status.repeat) ?: PROCEDURE_S
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
