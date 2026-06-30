package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.Lane
import com.example.timeboxvibe.engine.ToneSpec

object MmlSongBank {
    const val SENBONZAKURA_DEMO_KEY = "synth-mml-senbonzakura-demo"

    const val SENBONZAKURA_DEMO_MML = """
#BPM 160.73
#BAR 4/4

A @54 v13 o5 l8
[e- f g- b- >e-< b- g- f |
 e- f g- b- >d-< b- g- f |
 e- f g- b- >e- f e- d- |
 c- d- e- g- b- g- f e-]2

B @74 v10 o5 l8
[b- g- f e- g- f e- d- |
 b- g- f e- a- g- f e- |
 b- >c- d- e-< b- g- f e- |
 g- f e- d- c- d- e- f]2

C @99 v12 o2 l4
[e- e- c- b- |
 e- e- a- b- |
 c- c- a- b- |
 e- b- e- r]2

D @square v8 o4 l16
[e- b- >e-< b- e- b- >e-< b- c- g- >c-< g- b- f >b-< f |
 e- b- >e-< b- e- b- >d-< b- a- e- >a-< e- b- f >b-< f |
 c- g- >c-< g- c- g- >c-< g- a- e- >a-< e- b- f >b-< f |
 e- b- >e-< b- g- >e-< b- g- e- b- >e-< b- r16 r16 r16 r16]2

R @drum v12 l8
[k h s h k k s h |
 k h s h k h s h |
 k h s h k k s h |
 k h s h k r s h]2
"""

    val senbonzakuraDemoResult: MmlCompileResult = MmlCompiler.compile(SENBONZAKURA_DEMO_MML)

    fun getArrangement(key: String, volume: Float): ArrangementLanes? {
        if (key != SENBONZAKURA_DEMO_KEY) return null
        val success = senbonzakuraDemoResult as? MmlCompileResult.Success ?: return null
        if (volume == 1f) return success.arrangement
        val arrangement = success.arrangement
        return arrangement.copy(
            lead = scaleLane(arrangement.lead, volume),
            harmony = scaleLane(arrangement.harmony, volume),
            bass = scaleLane(arrangement.bass, volume),
            percussion = scaleLane(arrangement.percussion, volume),
            auxiliary = arrangement.auxiliary?.let { scaleLane(it, volume) }
        )
    }

    private fun scaleLane(lane: Lane, volume: Float): Lane {
        val notes = ArrayList<ToneSpec>(lane.notes.size)
        var i = 0
        while (i < lane.notes.size) {
            val note = lane.notes[i]
            notes.add(note.copy(volume = note.volume * volume))
            i++
        }
        return lane.copy(notes = notes)
    }
}
