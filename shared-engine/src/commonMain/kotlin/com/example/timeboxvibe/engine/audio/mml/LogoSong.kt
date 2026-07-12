package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.audio.opna.EgMode
import com.example.timeboxvibe.engine.audio.opna.FmPatch
import com.example.timeboxvibe.engine.audio.opna.OperatorSpec

/**
 * Compact source-owned patch bank for LOGO.M86.
 *
 * Oracle entry SHA-256: 1e572f2677129bdc16bc79323c2e8369ca1c958d9c2685e3c48e21e74c2e66f7
 * Archive SHA-256: ca787b8ff66f7b3f10c97b3ecc77cd466772767e3e9e8cf5a0c71dd612b1c8d7
 */
internal object LogoSongPatchBank {
    val At79 = FmPatch(
        algorithm = 4,
        feedback = 7,
        op0 = OperatorSpec(2, 3, 30, ar = 15, dr = 0, sr = 0, sl = 0, rr = 5, ks = 3, egMode = EgMode.OPN_RATE),
        op1 = OperatorSpec(2, 7, 31, ar = 15, dr = 0, sr = 0, sl = 0, rr = 3, ks = 3, egMode = EgMode.OPN_RATE),
        op2 = OperatorSpec(4, 3, 0, ar = 13, dr = 2, sr = 2, sl = 13, rr = 4, egMode = EgMode.OPN_RATE),
        op3 = OperatorSpec(4, 7, 0, ar = 12, dr = 2, sr = 6, sl = 13, rr = 5, egMode = EgMode.OPN_RATE)
    )
}

internal const val LOGO_M86_MML_SOURCE: String = """
#MML 2
#BPM 80
#PMDCLOCK 24
#BAR 33/16

; Clean-room transcription of LOGO.M86. The 198-clock one-shot length is
; represented as one 33/16 compiler bar so every preserved lane ends exactly.
#MACRO logo_low o2 l32 g+ a+ b >d+ <g+ a+ b g+
#MACRO logo_high o3 l32 d+ g+ a+ >d+ <b a+ g+ a+

A @logo79 V95 MX1 M0,15,1,20 MW6 *6 o2 d+1 *0 V106 e1 r16 |
B @logo79 V95 MX1 M0,15,1,20 MW6 *6 o1 g+1 *0 V106 o2 g+1 r16 |

G @square EX0 E2,-2,4,1 v3 MX1 M0,15,1,20 MW6 *6
  [${'$'}logo_low]4 *0 v11 [${'$'}logo_high]3
  T70 o4 d+32 g+ a+ >d+ T45 <b32 a+ g+ a+ r16 |

H @square EX0 E2,-2,4,1 v3 D-1 MX1 M0,15,1,20 MW6 *6
  [${'$'}logo_low]4 *0 v11 [${'$'}logo_high]3
  o4 d+32 g+ a+ >d+ <b a+ g+ a+ r16 |

I @square EX0 E2,-2,4,1 v2 r16 MX1 M0,15,1,20 MW6 *6
  [${'$'}logo_low]4 *0 v10 [${'$'}logo_high]3
  T60 o4 d+32 g+ a+ >d+ T30 <b32 a+ T18 g+ a+ |
"""
