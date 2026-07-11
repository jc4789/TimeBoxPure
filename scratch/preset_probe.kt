import com.example.timeboxvibe.engine.getDefaultPresets
import com.example.timeboxvibe.engine.core.TimerEngine

fun main() {
    val presets = getDefaultPresets("en")
    for (p in presets) {
        val n = p.normalized(true)
        val e = TimerEngine(n)
        println("${n.id} mode=${n.mode} seq=${n.sequence.toList()} t=${e.timeRemaining}/${e.totalDuration} big=${e.bigTimeRemaining}/${e.bigTotalDuration} dual=${e.isDual} stages=${e.sequenceLength} label=${e.currentStageLabel}")
        if (n.mode == "sequence" || n.mode == "classic") {
            // skip through all stages quickly via restore+tick
            var guard = 0
            while (guard < 20 && e.currentIndex < (n.sequence.size - 1).coerceAtLeast(0)) {
                e.restoreState(1, e.midTimeRemaining, e.bigTimeRemaining, e.currentIndex)
                e.start()
                e.tick()
                if (e.isRinging) e.dismissAlarm()
                guard++
            }
            println("  after advance index=${e.currentIndex} t=${e.timeRemaining} ringing=${e.isRinging}")
        }
    }
}
