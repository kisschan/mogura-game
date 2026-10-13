package com.moguru.game.android

import android.os.Handler
import android.os.Looper

/** Pause before every CPU operation so each one can be followed on screen. */
internal const val CPU_STEP_DELAY_MILLIS = 450L

/** How long a CPU capture shows the revealed card, and then the spinning dice. */
internal const val CPU_CAPTURE_STAGE_MILLIS = 600L

fun interface CpuStepCancellation {
    fun cancel()
}

/** One-shot timer for CPU steps. The task runs later on the main thread, never inside [schedule]. */
fun interface CpuStepScheduler {
    fun schedule(delayMillis: Long, task: () -> Unit): CpuStepCancellation
}

/** Deterministic default for previews and JVM tests: a step runs only when [advanceBy] reaches it. */
class ManualCpuStepScheduler : CpuStepScheduler {
    private class Reservation(val dueAtMillis: Long, val task: () -> Unit)

    private val reservations = mutableListOf<Reservation>()
    private var nowMillis = 0L

    val pendingCount: Int get() = reservations.size

    override fun schedule(delayMillis: Long, task: () -> Unit): CpuStepCancellation {
        val reservation = Reservation(nowMillis + delayMillis, task)
        reservations.add(reservation)
        return CpuStepCancellation { reservations.remove(reservation) }
    }

    /** Runs each step that falls due within [millis], including ones reserved by earlier steps. */
    fun advanceBy(millis: Long) {
        val until = nowMillis + millis
        while (true) {
            val due = reservations.filter { it.dueAtMillis <= until }.minByOrNull { it.dueAtMillis } ?: break
            reservations.remove(due)
            nowMillis = due.dueAtMillis
            due.task()
        }
        nowMillis = until
    }
}

internal class MainHandlerCpuStepScheduler : CpuStepScheduler {
    private val main = Handler(Looper.getMainLooper())

    override fun schedule(delayMillis: Long, task: () -> Unit): CpuStepCancellation {
        val runnable = Runnable(task)
        main.postDelayed(runnable, delayMillis)
        return CpuStepCancellation { main.removeCallbacks(runnable) }
    }
}
