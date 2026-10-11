package nl.julian.zztimer

/**
 * Vlaggenprocedure volgens regel 26 van de Regels voor Wedstrijdzeilen (RvW):
 *   5:00  waarschuwingssein  – klassevlag op
 *   4:00  voorbereidingssein – P, I, Z, U of zwarte vlag op
 *   1:00  één minuut         – voorbereidingsvlag neer
 *   0:00  start              – klassevlag neer
 * De tijd komt altijd van de ESP (zelfde berekening als de firmware).
 */

enum class PrepFlag(val label: String, val spoken: String) {
    P("P-vlag", "P-vlag"),
    I("I-vlag", "I-vlag"),
    Z("Z-vlag", "Z-vlag"),
    U("U-vlag", "U-vlag"),
    BLACK("Zwarte vlag", "zwarte vlag"),
}

/** Onderbreking die de wedstrijdleiding heeft gegeven. */
enum class Interrupt { NONE, INDIVIDUAL_RECALL, GENERAL_RECALL, POSTPONED }

enum class Phase(val label: String) {
    IDLE("Gereed"),
    WARNING("Waarschuwingssein"),
    PREPARATORY("Voorbereidingssein"),
    LAST_MINUTE("Laatste minuut"),
    STARTED("Gestart"),
    POSTPONED("Uitstel"),
    GENERAL_RECALL("Algemene terugroep"),
    RESUMING("Waarschuwingssein volgt"),
    NO_CONNECTION("Geen verbinding"),
}

/** Achtergrondkleur van de klassevlag. */
enum class ClassColor(val label: String, val argb: Long, val textArgb: Long) {
    WHITE("Wit", 0xFFFFFFFF, 0xFF0000FF),
    YELLOW("Geel", 0xFFFFFF00, 0xFF000000),
    RED("Rood", 0xFFFF0000, 0xFFFFFFFF),
    BLUE("Blauw", 0xFF0000FF, 0xFFFFFFFF),
    GREEN("Groen", 0xFF008000, 0xFFFFFFFF),
    ORANGE("Oranje", 0xFFFF8C00, 0xFF000000),
}

/** Welke vlaggen er te zien zijn. */
enum class FlagKind { CLASS, P, I, Z, U, BLACK, X, FIRST_SUBSTITUTE, AP }

fun PrepFlag.kind(): FlagKind = when (this) {
    PrepFlag.P -> FlagKind.P
    PrepFlag.I -> FlagKind.I
    PrepFlag.Z -> FlagKind.Z
    PrepFlag.U -> FlagKind.U
    PrepFlag.BLACK -> FlagKind.BLACK
}

data class FlagSettings(
    val prep: PrepFlag = PrepFlag.P,
    val classes: List<String> = listOf("Klasse"),
    val voice: Boolean = true,
    val everyMinute: Boolean = true,
    val every10s: Boolean = true,
    val lastTen: Boolean = true,
    val countdownStart: Boolean = true,   // START: eerst 10 s aftellen (true) of direct (false)
    val classColor: ClassColor = ClassColor.WHITE,
) {
    /** Klasse voor een start; bij herhalen loopt de lijst rond. */
    fun className(index: Int): String {
        val list = classes.ifEmpty { listOf("Klasse") }
        return list[((index % list.size) + list.size) % list.size]
    }
}

/** Een vlag die straks op of neer gaat. */
data class UpcomingFlag(val flag: ShownFlag, val up: Boolean, val inSeconds: Int) {
    val text: String get() = "${flag.label} ${if (up) "op" else "neer"}"
}

data class ShownFlag(val kind: FlagKind, val label: String, val classColor: ClassColor = ClassColor.WHITE)

data class FlagState(
    val phase: Phase = Phase.NO_CONNECTION,
    val timeText: String = "--:--",
    val startNumber: Int = 0,           // 1, 2, 3 ... bij herhalen
    val className: String = "",
    val flags: List<ShownFlag> = emptyList(),
    val nextText: String? = null,       // bijv. "P-vlag op over 0:23"
    val timelineStep: Int = -1,         // 0 = waarschuwing … 3 = start
    val interrupt: Interrupt = Interrupt.NONE,
    val canStart: Boolean = false,
    val canIndividualRecall: Boolean = false,
    val canGeneralRecall: Boolean = false,
    val canPostpone: Boolean = false,
    val canResume: Boolean = false,
    val resuming: Boolean = false,
    val running: Boolean = false,
    val local: Boolean = false,          // klok niet verbonden: telefoon telt zelf
    val banner: String? = null,          // instructie, bijv. "DRUK NU OP START VAN DE KLOK"
    val bannerUrgent: Boolean = false,
    val upcoming: List<UpcomingFlag> = emptyList(),  // volgende en daarna
)

object FlagLogic {
    const val PREP_UP = 240
    const val PREP_DOWN = 60
    const val X_MAX_MS = 240_000L        // X-vlag uiterlijk 4 minuten (RvW 29.1)

    /** Resterende seconden bij k hele seconden na START (zelfde regel als de firmware). */
    fun remainingForK(k: Long): Int = if (k <= 0) 300 else (300 - (((k - 1) % 300) + 1)).toInt()

    /** Index van de start (0, 1, 2 …) waar seconde k bij hoort. */
    fun cycleForK(k: Long): Int = if (k <= 0) 0 else ((k - 1) / 300).toInt()

    fun mmss(seconds: Int): String = TimerMath.format(seconds.coerceAtLeast(0))

    fun words(n: Int): String = when (n) {
        1 -> "één"; 2 -> "twee"; 3 -> "drie"; 4 -> "vier"; 5 -> "vijf"
        6 -> "zes"; 7 -> "zeven"; 8 -> "acht"; 9 -> "negen"; 10 -> "tien"
        20 -> "twintig"; 30 -> "dertig"; 40 -> "veertig"; 50 -> "vijftig"
        else -> n.toString()
    }

    fun cap(t: String): String = t.replaceFirstChar { it.uppercase() }

    /** Aankondiging 10 s vóór het waarschuwingssein. */
    fun warningIn10(className: String): String = "${cap(classSpoken(className))} op over tien seconden."

    /**
     * Vlaggen die nog op of neer gaan, in volgorde (voor "Volgende" en "Daarna").
     * [rem] = resterende seconden van de lopende procedure.
     */
    fun upcomingRunning(rem: Int, cls: String, nextCls: String?, s: FlagSettings): List<UpcomingFlag> {
        val prep = ShownFlag(s.prep.kind(), s.prep.label)
        val c = ShownFlag(FlagKind.CLASS, cls, s.classColor)
        val list = mutableListOf<UpcomingFlag>()
        if (rem > PREP_UP) list += UpcomingFlag(prep, true, rem - PREP_UP)
        if (rem > PREP_DOWN) list += UpcomingFlag(prep, false, rem - PREP_DOWN)
        if (rem > 0) list += UpcomingFlag(c, false, rem)
        if (nextCls != null && rem > 0) list += UpcomingFlag(ShownFlag(FlagKind.CLASS, nextCls, s.classColor), true, rem)
        return list
    }

    /** Vlaggen vóór een start die eraan komt (over [inSeconds] seconden). */
    fun upcomingPending(inSeconds: Int, cls: String, s: FlagSettings): List<UpcomingFlag> = listOf(
        UpcomingFlag(ShownFlag(FlagKind.CLASS, cls, s.classColor), true, inSeconds),
        UpcomingFlag(ShownFlag(s.prep.kind(), s.prep.label), true, inSeconds + 60),
    )

    /** Eén woord uit het aftellen: "Tien." … "Eén." */
    fun countWord(n: Int): String = words(n).replaceFirstChar { it.uppercase() } + "."

    /** Tekst bij het waarschuwingssein (5:00). */
    fun warningText(className: String): String =
        "Waarschuwingssein. ${classSpoken(className).replaceFirstChar { it.uppercase() }} op. Nog vijf minuten."

    fun classSpoken(name: String): String =
        if (name.equals("Klasse", ignoreCase = true)) "klassevlag" else "klassevlag $name"

    /**
     * Tekst die bij seconde k uitgesproken wordt, of null. De instellingen bepalen welke
     * tussentijden er genoemd worden.
     */
    fun announcement(k: Long, repeat: Boolean, s: FlagSettings, classOffset: Int): String? {
        if (!repeat && k > 300) return null
        val rem = remainingForK(k)
        val cycle = cycleForK(k)
        val cls = s.className(classOffset + cycle)
        return when {
            k == 0L -> warningText(cls)
            rem == 0 -> buildString {
                append("Start! ${classSpoken(cls).replaceFirstChar { it.uppercase() }} neer.")
                if (repeat) append(" Waarschuwingssein ${classSpoken(s.className(classOffset + cycle + 1))}. Nog vijf minuten.")
            }
            rem == PREP_UP -> "Voorbereidingssein. ${s.prep.spoken.replaceFirstChar { it.uppercase() }} op. Nog vier minuten."
            rem == PREP_DOWN -> "Nog één minuut. ${s.prep.spoken.replaceFirstChar { it.uppercase() }} neer."
            // 10 s vóór elk sein: welke vlag, daarna 5 … 1
            rem == PREP_UP + 10 && s.lastTen -> "${cap(s.prep.spoken)} op over tien seconden."
            rem in (PREP_UP + 1)..(PREP_UP + 5) && s.lastTen -> countWord(rem - PREP_UP)
            rem == PREP_DOWN + 10 && s.lastTen -> "${cap(s.prep.spoken)} neer over tien seconden."
            rem in (PREP_DOWN + 1)..(PREP_DOWN + 5) && s.lastTen -> countWord(rem - PREP_DOWN)
            rem == 10 && s.lastTen -> buildString {
                append("Start over tien seconden. ${cap(classSpoken(cls))} neer")
                if (repeat) append(", ${classSpoken(s.className(classOffset + cycle + 1))} op")
                append(".")
            }
            rem in 6..9 && s.lastTen -> null
            (rem == 180 || rem == 120) && s.everyMinute -> "Nog ${words(rem / 60)} minuten."
            rem in listOf(50, 40, 30, 20) && s.every10s -> "${words(rem).replaceFirstChar { it.uppercase() }} seconden."
            rem in 1..5 && s.lastTen -> countWord(rem)
            rem == 10 && s.every10s -> "Tien seconden."
            else -> null
        }
    }

    /** Vlaggen en volgende gebeurtenis tijdens een lopende procedure. */
    fun runningFlags(rem: Int, className: String, s: FlagSettings): Triple<List<ShownFlag>, String?, Int> {
        val flags = mutableListOf<ShownFlag>()
        if (rem > 0) flags += ShownFlag(FlagKind.CLASS, className, s.classColor)
        if (rem in (PREP_DOWN + 1)..PREP_UP) flags += ShownFlag(s.prep.kind(), s.prep.label)
        val next = when {
            rem > PREP_UP -> "${s.prep.label} op over ${mmss(rem - PREP_UP)}"
            rem > PREP_DOWN -> "${s.prep.label} neer over ${mmss(rem - PREP_DOWN)}"
            rem > 0 -> "Start over ${mmss(rem)}"
            else -> null
        }
        val step = when {
            rem > PREP_UP -> 0
            rem > PREP_DOWN -> 1
            rem > 0 -> 2
            else -> 3
        }
        return Triple(flags, next, step)
    }
}
