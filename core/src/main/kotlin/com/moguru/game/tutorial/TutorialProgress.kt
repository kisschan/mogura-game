package com.moguru.game.tutorial

/** What is remembered for one lesson. Practice boards themselves are never saved. */
data class TutorialLessonRecord(
    /** Scenario version the record was written for. */
    val version: Int,
    val completed: Boolean = false,
    /** Chapter to start from next time, or null when there is nothing to resume. */
    val resumeChapter: Int? = null,
)

/**
 * Learning progress, kept apart from the match save. [current] names the lesson that was being
 * practised when progress was last written, so a restarted app can offer to continue it.
 */
data class TutorialProgress(
    val lessons: Map<TutorialLessonId, TutorialLessonRecord> = emptyMap(),
    val current: TutorialLessonId? = null,
) {
    /** Completion is kept even when the scenario has changed since. */
    fun isCompleted(lesson: TutorialLessonId): Boolean = lessons[lesson]?.completed == true

    /** The chapter to resume [scenario] from, only if it was recorded for this scenario version. */
    fun resumeChapter(scenario: TutorialScenario): Int? =
        lessons[scenario.lesson]
            ?.takeIf { it.version == scenario.version }
            ?.resumeChapter
            ?.takeIf { it in 0 until scenario.chapterCount }

    /** The interrupted lesson to offer after a restart, if it can still be resumed. */
    fun interrupted(): TutorialScenario? =
        current?.let(TutorialScenarios::of)?.takeIf { resumeChapter(it) != null }

    /** [scenario] is being practised from the start of [chapter]. */
    fun started(scenario: TutorialScenario, chapter: Int): TutorialProgress {
        require(chapter in 0 until scenario.chapterCount)
        val record = TutorialLessonRecord(scenario.version, isCompleted(scenario.lesson), chapter)
        return copy(lessons = lessons + (scenario.lesson to record), current = scenario.lesson)
    }

    fun completed(scenario: TutorialScenario): TutorialProgress {
        val record = TutorialLessonRecord(scenario.version, completed = true, resumeChapter = null)
        return copy(lessons = lessons + (scenario.lesson to record), current = null)
    }

    /** The learner left the practice. The resume chapter stays for the lesson list. */
    fun left(): TutorialProgress = copy(current = null)
}

/** The progress file names an unknown format, probably from a newer app version. */
class UnsupportedTutorialProgressException(val format: Int) :
    IllegalArgumentException("Unsupported tutorial progress format $format")

/**
 * A small line format, independent of the match save:
 * ```
 * moguru-tutorial-progress 1
 * current BASIC
 * lesson BASIC 1 0 3
 * ```
 * A lesson line holds the scenario version, 1 for completed, and the resume chapter or `-`.
 * Lessons this app does not know are skipped, so a newer file still opens.
 */
object TutorialProgressCodec {
    const val FORMAT = 1
    private const val HEADER = "moguru-tutorial-progress"

    fun encode(progress: TutorialProgress): String = buildString {
        append("$HEADER $FORMAT\n")
        progress.current?.let { append("current ${it.name}\n") }
        progress.lessons.entries.sortedBy { it.key.ordinal }.forEach { (lesson, record) ->
            val completed = if (record.completed) 1 else 0
            append("lesson ${lesson.name} ${record.version} $completed ${record.resumeChapter ?: "-"}\n")
        }
    }

    /** Throws [IllegalArgumentException] for damaged text and [UnsupportedTutorialProgressException] for another format. */
    fun decode(text: String): TutorialProgress {
        val lines = text.lines().filter { it.isNotBlank() }
        val header = lines.firstOrNull()?.trim()?.split(' ')
        require(header != null && header.size == 2 && header[0] == HEADER) { "Not tutorial progress" }
        val format = requireNotNull(header[1].toIntOrNull()) { "Bad tutorial progress format" }
        if (format != FORMAT) throw UnsupportedTutorialProgressException(format)
        var current: TutorialLessonId? = null
        var currentSeen = false
        val lessons = mutableMapOf<TutorialLessonId, TutorialLessonRecord>()
        val names = mutableSetOf<String>()
        lines.drop(1).forEach { line ->
            val parts = line.trim().split(' ')
            when (parts.firstOrNull()) {
                "current" -> {
                    require(parts.size == 2 && !currentSeen) { "Bad current lesson" }
                    currentSeen = true
                    current = lessonOrNull(parts[1])
                }
                "lesson" -> {
                    require(parts.size == 5 && names.add(parts[1])) { "Bad lesson line" }
                    val version = requireNotNull(parts[2].toIntOrNull()?.takeIf { it >= 1 }) { "Bad version" }
                    val completed = when (parts[3]) {
                        "1" -> true
                        "0" -> false
                        else -> throw IllegalArgumentException("Bad completion")
                    }
                    val resume = if (parts[4] == "-") {
                        null
                    } else {
                        requireNotNull(parts[4].toIntOrNull()?.takeIf { it >= 0 }) { "Bad chapter" }
                    }
                    lessonOrNull(parts[1])?.let { lessons[it] = TutorialLessonRecord(version, completed, resume) }
                }
                else -> throw IllegalArgumentException("Unknown tutorial progress line")
            }
        }
        return TutorialProgress(lessons, current)
    }

    private fun lessonOrNull(name: String): TutorialLessonId? = TutorialLessonId.entries.firstOrNull { it.name == name }
}
