package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes
import com.example.timeboxvibe.engine.Lane
import com.example.timeboxvibe.engine.ToneSpec

object MmlSongBank {
    const val SENBONZAKURA_DEMO_KEY = "synth-mml-senbonzakura-demo"
    const val RIN_TO_SHITE_KEY = "synth-mml-rin-to-shite-saku-hana-no-gotoku"
    const val RIN_TO_SHITE_MML = RIN_TO_SHITE_MML_SOURCE

    const val SENBONZAKURA_DEMO_MML = """
#BPM 160.73
#BAR 4/4

A @54 v12 o5 l8
[e- f g- b- >e-< b- g- f |
 e- f g- b- >d-< b- g- f |
 e- f g- b- >e- f e- d-|
 c- d- e- g- b- g- f e- |
 e- f g- b- >e-< b- g- f |
 e- f g- b- >d-< b- g- f |
 c- d- e- g- b- g- f e- |
 d- f a- >d-< a- f d- c |
 b- g- f e- g- f e- d- |
 d- f a- >d-< a- f d- c |
 e- f g- b- >e-< b- g- f |
 e- f g- b- >d-< b- g- f |
 e- f g b- >e-< b- g f |
 g b- >d g< d b- g f |
 e- f g b- >e-< b- g f |
 g b- >d g< r r r r]2

B @74 v9 o4 l8
[b- g- f e- g- f e- d- |
 b- g- f e- a- g- f e- |
 b- >c- d- e-< b- g- f e- |
 g- f e- d- c- d- e- f |
 b- g- f e- g- f e- d- |
 b- g- f e- a- g- f e- |
 c- e- g- b- g- e- d- c- |
 d- f a- >d-< a- f d- c |
 b- g- f e- g- f e- d- |
 d- f a- >d-< a- f d- c |
 b- g- f e- g- f e- d- |
 b- g- f e- a- g- f e- |
 b- >e-< g b- >e-< b- g f |
 b- >d< g b- >d< b- g f |
 b- >e-< g b- >e-< b- g f |
 b- >d< g r r r r r]2

C @99 v12 o2 l4
[e- e- c- b- |
 e- e- a- b- |
 c- c- a- b- |
 e- b- e- r |
 e- e- c- b- |
 e- e- d- d |
 c- c- a- b- |
 d- d- d r |
 c- c- d- d- |
 e- e- e- r |
 c- c- d- d- |
 e- e- d- r |
 e- e- f f |
 g g g f |
 e- e- f f |
 g g r r]2

D @square v5 o3 l16
[e- b- >e-< b- e- b- >e-< b- c- g- >c-< g- b- f >b-< f |
 e- b- >e-< b- e- b- >d-< b- a- e- >a-< e- b- f >b-< f |
 c- g- >c-< g- c- g- >c-< g- a- e- >a-< e- b- f >b-< f |
 e- b- >e-< b- g- >e-< b- g- e- b- >e-< b- r16 r16 r16 r16 |
 e- b- >e-< b- e- b- >e-< b- c- g- >c-< g- b- f >b-< f |
 e- b- >e-< b- e- b- >d-< b- a- e- >a-< e- b- f >b-< f |
 c- g- >c-< g- c- g- >c-< g- a- e- >a-< e- b- f >b-< f |
 d- a- >d-< a- d a- >d< a- d f >a-< f d f >a-< f |
 c- g- >c-< g- c- g- >c-< g- d- a- >d-< a- d- a- >d-< a- |
 e- b- >e-< b- e- b- >e-< b- e- b- >e-< b- r16 r16 r16 r16 |
 c- g- >c-< g- c- g- >c-< g- d- a- >d-< a- d- a- >d-< a- |
 e- b- >e-< b- e- b- >d-< b- e- b- >d-< b- r16 r16 r16 r16 |
 e- b- >e-< b- f c >f< c g d >g< d f c >f< c |
 g d >g< d g d >g< d g d >g< d f c >f< c |
 e- b- >e-< b- f c >f< c g d >g< d f c >f< c |
 g d >g< d r16 r16 r16 r16 r16 r16 r16 r16 r16 r16 r16 r16]2

E @181 v10 o4 l8
[r b- r g- r e- r g- |
 r b- r g- r d- r f |
 r e- r g- r b- r d- |
 r c- r e- r g- r f |
 r b- r g- r e- r g- |
 r b- r g- r d- r f |
 r c- r e- r g- r d- |
 r d- r f r a- r c |
 r b- r g- r f r e- |
 r d- r f r a- r c |
 r b- r g- r e- r g- |
 r b- r g- r d- r f |
 r e- r f r g r f |
 r g r b- r d r f |
 r e- r f r g r f |
 r g r b- r d r r]2

R @drum v11 l8
[k h s h k h s h |
 k h s h k h s h |
 k h s h k h s h |
 k h s h k r s h |
 k h s h k h s h |
 k h s h k h s h |
 k h s h k h s h |
 k h s h k r s h |
 k h s h k h s h |
 k h s h k h s h |
 k h s h k h s h |
 k h s h k r s h |
 k h s h k h s h |
 k h s h k h s h |
 k h s h k h s h |
 k h s h k r s h]2
"""

    val senbonzakuraDemoResult: MmlCompileResult = MmlCompiler.compile(SENBONZAKURA_DEMO_MML)
    val rinToShiteResult: MmlCompileResult = MmlCompiler.compile(RIN_TO_SHITE_MML)

    fun getArrangement(key: String, volume: Float): ArrangementLanes? {
        val result = when (key) {
            SENBONZAKURA_DEMO_KEY -> senbonzakuraDemoResult
            RIN_TO_SHITE_KEY -> rinToShiteResult
            else -> return null
        }

        val success = result as? MmlCompileResult.Success
            ?: error("MML arrangement '$key' failed to compile: $result")

        if (volume == 1f) return success.arrangement

        val arrangement = success.arrangement
        val compiled = arrangement.compiledOpnaSong
        if (compiled != null) {
            return arrangement.copy(compiledOpnaSong = compiled.withPlaybackGain(volume))
        }
        return arrangement.copy(
            lead = scaleLane(arrangement.lead, volume),
            harmony = scaleLane(arrangement.harmony, volume),
            bass = scaleLane(arrangement.bass, volume),
            percussion = scaleLane(arrangement.percussion, volume),
            auxiliary = arrangement.auxiliary?.let { scaleLane(it, volume) },
            additional = arrangement.additional?.let { scaleLane(it, volume) }
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
