package com.example.timeboxvibe.engine.audio.mml

import com.example.timeboxvibe.engine.ArrangementLanes

object MmlSongBank {
    const val SENBONZAKURA_DEMO_KEY = "synth-mml-senbonzakura-demo"
    const val LLS_LOGO_KEY = "synth-mml-lls-logo"
    const val LLS_LOGO_MML = LOGO_M86_MML_SOURCE

    const val BAD_APPLE_LLS_MML = """
#MML 2
#BPM 160.73
#PMDCLOCK 24
#BAR 4/4
#LFO 5
#EQ peak 180 -2.0 0.70
#EQ peak 850 1.5 0.65
#EQ peak 2400 -4.0 0.85

; 52 bars / 77.64 seconds. The discarded three-bar test intro stays removed.
; Authentic coordinated ST02.M86 lanes, source clocks 288..<5280 (three-bar cut).
; FM is retained at 64%; the two summed SSG lanes use 37% to fit clean-room mixer headroom.
A @74 V78 q0 o3 e-8 b-8 a-8 b-8 f+8 b-8 f8 b-8 e-8 b-8 a-8 b-8 f+8 b-8 f8 b-8 e-8 b-8 a-8 b-8 f+8 b-8 f8 b-8 e-8 b-8
    a-8 b-8 f+8 b-8 f8 b-8 e-8 b-8 a-8 b-8 f+8 b-8 f8 b-8 o2 b8 o3 f+8 f8 f+8 e-8 f+8 c+8 f+8 o2 b8 o3 f+8 f8 f+8 d8
    e-8 b-8 f8 e-8 b-8 a-8 b-8 f+8 b-8 f8 b-8 e-8 b-8 a-8 b-8 f+8 b-8 f8 b-8 o2 b8 o3 f+8 f8 f+8 e-8 f+8 c+8 f+8 o2
    b8 o3 f+8 f8 f+8 d8 e-8 b-8 f8 e-8 b-8 e-8 b-8 e-8 b-8 e-8 b-8 e-8 b-8 e-8 b-8 e-8 b-8 e-8 b-8 o2 b8 o3 f+8 o2
    b8 o3 f+8 o2 b8 o3 f+8 o2 b8 o3 f+8 c+8 a-8 c+8 a-8 d8 a-8 d8 a-8 e-8 b-8 e-8 b-8 e-8 b-8 e-8 b-8 e-8 b-8 e-8
    b-8 e-8 b-8 c+8 a-8 o2 b8 o3 f+8 o2 b8 o3 f+8 o2 b8 o3 f+8 o2 b8 o3 f+8 c+8 a-8 c+8 a-8 d8 a-8 d8 a-8 V81 o2 b1
    o3 c+1 e-1 e-4 e-4 e-4 e-4 V78 o2 b8 o3 f+8 o2 b8 o3 f+8 o2 b8 o3 f+8 o2 b8 o3 f+8 c+8 a-8 c+8 a-8 c+8 a-8 c+8
    a-8 e-8 b-8 e-8 b-8 e-8 b-8 e-8 b-8 e-8 b-8 e-8 b-8 e-8 b-8 c+8 a-8 o2 b8 o3 f+8 o2 b8 o3 f+8 o2 b8 o3 f+8 o2 b8
    o3 f+8 c+8 a-8 c+8 a-8 c+8 a-8 c+8 a-8 e-8 b-8 e-8 b-8 e-8 b-8 e-8 b-8 e-8 b-8 e-8 b-8 e-8 b-8 c+8 a-8 o2 b8 o3
    f+8 o2 b8 o3 f+8 o2 b8 o3 f+8 o2 b8 o3 f+8 c+8 a-8 c+8 a-8 c+8 a-8 c+8 a-8 d8 b-8 d8 b-8 d8 b-8 d8 b-8 d8 b-8 d8
    b-8 d8 b-8 d8 b-8 V81 q1 e-8 b-8 e-16 e-16 b-16 e-16 e-16 e-16 f16 f16 o4 c16 o3 f16 f16 o4 c16 o3 g8 o4 d8 o3
    g16 o4 d16 o3 g16 g16 g16 g16 o4 d16 o3 g16 f16 f16 o4 c16 o3 f16 e-8 b-8 e-16 e-16 b-16 e-16 e-16 e-16 f16 f16
    o4 c16 o3 f16 f16 o4 c16 o3 g16 g16 o4 d16 o3 g16 g16 o4 d16 o3 g16 g16 g16 g16 o4 d16 o3 g16 f16 f16 o4 c16 o3
    f16 e-8 b-8 e-16 e-16 b-16 e-16 e-16 e-16 f16 f16 o4 c16 o3 f16 f16 o4 c16 o3 g8 o4 d8 o3 g16 o4 d16 o3 g16 g16
    g16 g16 o4 d16 o3 g16 f16 f16 o4 c16 o3 f16 e-8 b-8 e-16 e-16 b-16 e-16 e-16 e-16 f16 f16 o4 c16 o3 f16 f16 o4
    c16 o3 g16 g16 o4 d16 o3 g16 g16 o4 d16 o3 g16 g16 g16 g16 o4 d16 o3 g16 f16 f16 o4 c16 o3 f16 e-8 b-8 e-16 e-16
    b-16 e-16 e-16 e-16 f16 f16 o4 c16 o3 f16 f16 o4 c16 o3 g8 o4 d8 o3 g16 o4 d16 o3 g16 g16 g16 g16 o4 d16 o3 g16
    f16 f16 o4 c16 o3 f16 e-8 b-8 e-16 e-16 b-16 e-16 e-16 e-16 f16 f16 o4 c16 o3 f16 f16 o4 c16 o3 g16 g16 o4 d16
    o3 g16 g16 o4 d16 o3 g16 g16 g16 g16 o4 d16 o3 g16 f16 f16 o4 c16 o3 f16 e-8 b-8 e-16 e-16 b-16 e-16 e-16 e-16
    f16 f16 o4 c16 o3 f16 f16 o4 c16 o3 g8 o4 d8 o3 g16 o4 d16 o3 g16 g16 g16 g16 o4 d16 o3 g16 f16 f16 o4 c16 o3
    f16 e-8 b-8 e-16 e-16 b-16 e-16 e-16 e-16 f16 f16 o4 c16 o3 f16 f16 o4 c16 o3 g16 g16 o4 d16 o3 g16 g16 o4 d16
    o3 g16 g16 g16 g16 o4 d16 o3 g16 g16 o4 d16 o3 g16 g16 r1

B r1. r1. @181 V71 q0 o2 b-2& b-1. f+1. f2 f+2& f+1. e-1. d2 @99 V78 o5 e-8 f8 f+8 a-8 b-4 e-8 o6 c+8 o5 b-4 e-4 b-8
    a-8 f+8 f8 e-8 f8 f+8 a-8 b-4 a-8 f+8 f8 o4 b-8 o5 f8 f+8 f8 e-8 d8 f8 e-8 f8 f+8 a-8 b-4 a-8 o6 c+8 e-4 e-4 f4
    f+4 e-8 f8 f+8 a-8 b-4 a-8 f+8 a-4 f4 f+4 a-4 V80 o5 e-2 e-8 f8 f+4 f4. o4 b-4. o5 f4 f4 f+8 e-4. c+4 c+4 e-8 o4
    b-4. o5 e-4 e-2 e-8 f8 f+4 f4. f+4. a-4 e-4. b-8 b-1 a-8 b-8 o6 c+4 e-2 e-8 f8 f+4 f4. o5 b-4. o6 f4 f4 f+8 e-4.
    c+4 c+4 e-8 o5 b-4. o6 f4 e-2 e-8 f8 f+8 a-8 f4. f+4. a-4 d4. o5 b-4. o6 b-4 a-4 f+4 f4 f+4 V81 o5 g8 d4 g4 o4
    b-8 o5 d8 f8 g8 d8 o4 b-8 o5 g4 g8 a8 b-8 g8 d4 g4 d8 g8 o6 c8 o5 b-2 a8 g8 d8 o4 b-8 o5 g8 d4 g4 o4 b-8 o5 d8
    f8 g8 d8 o4 b-8 o5 g4 g8 a8 b-8 g8 d4 g4 d8 g8 o6 c8 o5 b-2 a4 b-4 o6 g8 d4 g4 o5 b-8 o6 d8 f8 g8 d8 o5 b-8 o6
    g4 g8 a8 b-8 g8 d4 g4 d8 g8 o7 c8 o6 b-2 a8 g8 d8 o5 b-8 o6 g8 d4 g4 o5 b-8 o6 d8 f8 g8 d8 o5 b-8 o6 g4 g8 a8
    b-8 g8 d4 g4 d8 g8 a8 b-2. b-4 r1

C r1. r1. @181 V71 q0 o2 e-2& e-1. o1 b1. o2 d2 e-2& e-1. o1 b1. b-2 @99 V78 o4 e-8 f8 f+8 a-8 b-4 e-8 o5 c+8 o4 b-4
    e-4 b-8 a-8 f+8 f8 e-8 f8 f+8 a-8 b-4 a-8 f+8 f8 o3 b-8 o4 f8 f+8 f8 e-8 d8 f8 e-8 f8 f+8 a-8 b-4 a-8 o5 c+8 e-4
    e-4 f4 f+4 e-8 f8 f+8 a-8 b-4 a-8 f+8 a-4 f4 f+4 a-4 V80 o4 e-2 e-8 f8 f+4 f4. o3 b-4. o4 f4 f4 f+8 e-4. c+4 c+4
    e-8 o3 b-4. o4 e-4 e-2 e-8 f8 f+4 f4. f+4. a-4 e-4. b-8 b-1 a-8 b-8 o5 c+4 e-2 e-8 f8 f+4 f4. o4 b-4. o5 f4 f4
    f+8 e-4. c+4 c+4 e-8 o4 b-4. o5 f4 e-2 e-8 f8 f+8 a-8 f4. f+4. a-4 d4. o4 b-4. o5 b-4 a-4 f+4 f4 f+4 V78 o4 g8
    d4 g4 o3 b-8 o4 d8 f8 g8 d8 o3 b-8 o4 g4 g8 a8 b-8 g8 d4 g4 d8 g8 o5 c8 o4 b-2 a8 g8 d8 o3 b-8 o4 g8 d4 g4 o3
    b-8 o4 d8 f8 g8 d8 o3 b-8 o4 g4 g8 a8 b-8 g8 d4 g4 d8 g8 o5 c8 o4 b-2 a4 b-4 o5 g8 d4 g4 o4 b-8 o5 d8 f8 g8 d8
    o4 b-8 o5 g4 g8 a8 b-8 g8 d4 g4 d8 g8 o6 c8 o5 b-2 a8 g8 d8 o4 b-8 o5 g8 d4 g4 o4 b-8 o5 d8 f8 g8 d8 o4 b-8 o5
    g4 g8 a8 b-8 g8 d4 g4 d8 g8 a8 b-2. b-4 r1

D r8 r1 @99 V71 q2 o5 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 V73 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8
    r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 d8 r8 d8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8
    e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 d8 r8 d8 r8 V72 o4 e-8 r8 e-8 r8 e-8 r8 e-8 r8
    e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 d8 r8 d8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8
    e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 d8 r8 d8 @54 o3 b-16 b-16 o4 e-16 e-16
    e-16 e-16 o3 b-16 b-16 b-16 b-16 o4 e-16 e-16 e-16 e-16 e-16 e-16 o3 b-16 b-16 o4 e-16 e-16 e-16 e-16 o3 b-16
    b-16 b-16 b-16 o4 e-16 e-16 e-16 e-16 e-16 e-16 o3 b-16 b-16 o4 e-16 e-16 e-16 e-16 o3 b-16 b-16 b-16 b-16 o4
    e-16 e-16 e-16 e-16 e-16 e-16 o3 b-16 b-16 o4 e-16 e-16 e-16 e-16 o3 b-16 b-16 b-16 b-16 o4 e-16 e-16 e-16 e-16
    e-16 e-16 o3 b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16
    b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16
    f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16
    b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 b-16 b-16 o4 e-16 e-16 e-16 e-16 o3
    b-16 b-16 b-16 b-16 o4 e-16 e-16 e-16 e-16 e-16 e-16 o3 b-16 b-16 o4 e-16 e-16 e-16 e-16 o3 b-16 b-16 b-16 b-16
    o4 e-16 e-16 e-16 e-16 e-16 e-16 o3 b-16 b-16 o4 e-16 e-16 e-16 e-16 o3 b-16 b-16 b-16 b-16 o4 e-16 e-16 e-16
    e-16 e-16 e-16 o3 b-16 b-16 o4 e-16 e-16 e-16 e-16 o3 b-16 b-16 b-16 b-16 o4 e-16 e-16 e-16 e-16 e-16 e-16 o3
    b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16
    f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 b-16 o4 e-16 f16 o3 b-16 o4 d16 e-16 f16 b-16 d16
    e-16 f16 b-16 d16 e-16 f16 b-16 d16 e-16 f16 b-16 d16 e-16 f16 b-16 d16 e-16 f16 b-16 d16 e-16 f16 b-16 d16 e-16
    f16 b-16 @99 V76 b-16 o5 e-16 g16 g16 o4 b-16 o5 e-16 g16 g16 c16 f16 a16 a16 c16 f16 a16 a16 d16 g16 b-16 b-16
    d16 g16 b-16 b-16 d16 g16 b-16 b-16 c16 f16 a16 a16 o4 b-16 o5 e-16 g16 g16 o4 b-16 o5 e-16 g16 g16 c16 f16 a16
    a16 c16 f16 a16 a16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 c16 f16 a16 a16 o4 b-16 o5 e-16 g16
    g16 o4 b-16 o5 e-16 g16 g16 c16 f16 a16 a16 c16 f16 a16 a16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 d16 g16 b-16
    b-16 c16 f16 a16 a16 o4 b-16 o5 e-16 g16 g16 o4 b-16 o5 e-16 g16 g16 c16 f16 a16 a16 c16 f16 a16 a16 d16 g16
    b-16 b-16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 c16 f16 a16 a16 o4 b-16 o5 e-16 g16 g16 o4 b-16 o5 e-16 g16 g16
    c16 f16 a16 a16 c16 f16 a16 a16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 c16 f16 a16 a16 o4 b-16 o5
    e-16 g16 g16 o4 b-16 o5 e-16 g16 g16 c16 f16 a16 a16 c16 f16 a16 a16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 d16 g16
    b-16 b-16 c16 f16 a16 a16 o4 b-16 o5 e-16 g16 g16 o4 b-16 o5 e-16 g16 g16 c16 f16 a16 a16 c16 f16 a16 a16 d16
    g16 b-16 b-16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 c16 f16 a16 a16 o4 b-16 o5 e-16 g16 g16 o4 b-16 o5 e-16 g16
    g16 c16 f16 a16 a16 c16 f16 a16 a16 q0 o4 b-2. b-4 r1

E r8 r1 @99 V71 q2 o4 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 V73 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8
    r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8
    r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 V72 b-8 r8 b-8 r8 b-8 r8 b-8 r8
    b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8
    b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 @54 o3 f+16 f+16 b-16 b-16 b-16
    b-16 f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 a-16 a-16 b-16
    b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 f+16
    f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16
    f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 a-16 a-16 b-16 b-16 b-16
    b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16
    b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16
    f+16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 a-16 a-16 b-16 b-16 b-16 b-16 b-16
    b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16
    b-16 f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16
    b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 a-16 a-16 b-16 b-16 b-16 b-16 b-16 b-16 f+16
    f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16
    f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 @99 V73 o4 e-16 b-16 o5 e-16 e-16 o4 e-16 b-16 o5 e-16 e-16 o4 f16
    o5 c16 f16 f16 o4 f16 o5 c16 f16 f16 o4 g16 o5 d16 g16 g16 o4 g16 o5 d16 g16 g16 o4 g16 o5 d16 g16 g16 o4 f16 o5
    c16 f16 f16 o4 e-16 b-16 o5 e-16 e-16 o4 e-16 b-16 o5 e-16 e-16 o4 f16 o5 c16 f16 f16 o4 f16 o5 c16 f16 f16 o4
    g16 o5 d16 g16 g16 o4 g16 o5 d16 g16 g16 o4 g16 o5 d16 g16 g16 o4 f16 o5 c16 f16 f16 o4 e-16 b-16 o5 e-16 e-16
    o4 e-16 b-16 o5 e-16 e-16 o4 f16 o5 c16 f16 f16 o4 f16 o5 c16 f16 f16 o4 g16 o5 d16 g16 g16 o4 g16 o5 d16 g16
    g16 o4 g16 o5 d16 g16 g16 o4 f16 o5 c16 f16 f16 o4 e-16 b-16 o5 e-16 e-16 o4 e-16 b-16 o5 e-16 e-16 o4 f16 o5
    c16 f16 f16 o4 f16 o5 c16 f16 f16 o4 g16 o5 d16 g16 g16 o4 g16 o5 d16 g16 g16 o4 g16 o5 d16 g16 g16 o4 f16 o5
    c16 f16 f16 V76 e-16 b-16 o6 e-16 e-16 o5 e-16 b-16 o6 e-16 e-16 o5 f16 o6 c16 f16 f16 o5 f16 o6 c16 f16 f16 o5
    g16 o6 d16 g16 g16 o5 g16 o6 d16 g16 g16 o5 g16 o6 d16 g16 g16 o5 f16 o6 c16 f16 f16 o5 e-16 b-16 o6 e-16 e-16
    o5 e-16 b-16 o6 e-16 e-16 o5 f16 o6 c16 f16 f16 o5 f16 o6 c16 f16 f16 o5 g16 o6 d16 g16 g16 o5 g16 o6 d16 g16
    g16 o5 g16 o6 d16 g16 g16 o5 f16 o6 c16 f16 f16 o5 e-16 b-16 o6 e-16 e-16 o5 e-16 b-16 o6 e-16 e-16 o5 f16 o6
    c16 f16 f16 o5 f16 o6 c16 f16 f16 o5 g16 o6 d16 g16 g16 o5 g16 o6 d16 g16 g16 o5 g16 o6 d16 g16 g16 o5 f16 o6
    c16 f16 f16 o5 e-16 b-16 o6 e-16 e-16 o5 e-16 b-16 o6 e-16 e-16 o5 f16 o6 c16 f16 f16 o5 f16 o6 c16 f16 f16 q0
    o5 b-2. b-4 r1

G @lls_square EX0 E2,-1,24,1 r8 r1 V40 q0 o5 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 r8 e-8 o4 b-16 e-16 f16 f+16 b-16 e-16
    f16 f+16 b-16 e-16 f16 f+16 o5 c+16 e-16 o4 b-16 e-16 b-16 e-16 f16 f+16 b-16 e-16 f16 f+16 b-16 e-16 o5 c+16
    e-16 o4 b-16 a-16 f+16 f16 o3 b16 b16 o4 f+16 o3 b16 b16 o4 c+16 e-16 f+16 o3 b16 b16 o4 f+16 o3 b16 b16 o4 c+16
    e-16 f+16 d16 e-16 f16 o3 b-16 o4 d16 e-16 f16 o3 b-16 o4 d16 e-16 f16 o3 b-16 b-16 o4 a-16 f+16 f16 b-16 e-16
    f16 f+16 b-16 e-16 f16 f+16 b-16 e-16 f16 f+16 o5 c+16 e-16 o4 b-16 e-16 b-16 e-16 f16 f+16 b-16 e-16 f16 f+16
    b-16 e-16 o5 c+16 e-16 o4 b-16 a-16 f+16 f16 o3 b16 b16 o4 f+16 o3 b16 b16 o4 c+16 e-16 f+16 o3 b16 b16 o4 f+16
    o3 b16 b16 o4 c+16 e-16 f+16 d16 e-16 f16 o3 b-16 o4 d16 e-16 f16 o3 b-16 o4 d16 e-16 f16 o3 b-16 b-16 o4 a-16
    f+16 f16 V46 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 c+8 e-8
    f8 a-8 d8 e-8 f8 a-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8
    c+8 e-8 f8 a-8 d8 e-8 f8 a-8 V44 q2 b-16 b-16 o5 e-16 e-16 e-16 e-16 o4 b-16 b-16 b-16 b-16 o5 e-16 e-16 e-16
    e-16 e-16 e-16 o4 b-16 b-16 o5 e-16 e-16 e-16 e-16 o4 b-16 b-16 b-16 b-16 o5 e-16 e-16 e-16 e-16 e-16 e-16 o4
    b-16 b-16 o5 e-16 e-16 e-16 e-16 o4 b-16 b-16 b-16 b-16 o5 e-16 e-16 e-16 e-16 e-16 e-16 o4 b-16 b-16 o5 e-16
    e-16 e-16 e-16 o4 b-16 b-16 b-16 b-16 o5 e-16 e-16 e-16 e-16 e-16 e-16 o4 b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16
    f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16
    b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16
    f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16
    b-16 o5 e-16 f16 o4 b-16 b-16 b-16 o5 e-16 e-16 e-16 e-16 o4 b-16 b-16 b-16 b-16 o5 e-16 e-16 e-16 e-16 e-16
    e-16 o4 b-16 b-16 o5 e-16 e-16 e-16 e-16 o4 b-16 b-16 b-16 b-16 o5 e-16 e-16 e-16 e-16 e-16 e-16 o4 b-16 b-16 o5
    e-16 e-16 e-16 e-16 o4 b-16 b-16 b-16 b-16 o5 e-16 e-16 e-16 e-16 e-16 e-16 o4 b-16 b-16 o5 e-16 e-16 e-16 e-16
    o4 b-16 b-16 b-16 b-16 o5 e-16 e-16 e-16 e-16 e-16 e-16 o4 b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16
    b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16 b-16 o5 e-16
    f16 o4 b-16 b-16 o5 e-16 f16 o4 b-16 o5 d16 e-16 f16 b-16 d16 e-16 f16 b-16 d16 e-16 f16 b-16 d16 e-16 f16 b-16
    d16 e-16 f16 b-16 d16 e-16 f16 b-16 d16 e-16 f16 b-16 d16 e-16 f16 b-16 o3 b-16 o4 e-16 g16 g16 o3 b-16 o4 e-16
    g16 g16 c16 f16 a16 a16 c16 f16 a16 a16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 c16 f16 a16 a16 o3
    b-16 o4 e-16 g16 g16 o3 b-16 o4 e-16 g16 g16 c16 f16 a16 a16 c16 f16 a16 a16 d16 g16 b-16 b-16 d16 g16 b-16 b-16
    d16 g16 b-16 b-16 c16 f16 a16 a16 o3 b-16 o4 e-16 g16 g16 o3 b-16 o4 e-16 g16 g16 c16 f16 a16 a16 c16 f16 a16
    a16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 c16 f16 a16 a16 o3 b-16 o4 e-16 g16 g16 o3 b-16 o4
    e-16 g16 g16 c16 f16 a16 a16 c16 f16 a16 a16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 c16 f16 a16
    a16 o3 b-16 o4 e-16 g16 g16 o3 b-16 o4 e-16 g16 g16 c16 f16 a16 a16 c16 f16 a16 a16 d16 g16 b-16 b-16 d16 g16
    b-16 b-16 d16 g16 b-16 b-16 c16 f16 a16 a16 o3 b-16 o4 e-16 g16 g16 o3 b-16 o4 e-16 g16 g16 c16 f16 a16 a16 c16
    f16 a16 a16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 c16 f16 a16 a16 o3 b-16 o4 e-16 g16 g16 o3
    b-16 o4 e-16 g16 g16 c16 f16 a16 a16 c16 f16 a16 a16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 c16
    f16 a16 a16 o3 b-16 o4 e-16 g16 g16 o3 b-16 o4 e-16 g16 g16 c16 f16 a16 a16 c16 f16 a16 a16 d16 g16 b-16 b-16
    d16 g16 b-16 b-16 d16 g16 b-16 b-16 d16 g16 b-16 b-16 r1

H @lls_square EX0 E2,-1,24,1 r8 r1 V40 q0 o4 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r8 b-8 r16. b-16 e-16 f16 f+16 b-16 e-16
    f16 f+16 b-16 e-16 f16 f+16 o5 c+16 e-16 o4 b-16 e-16 b-16 e-16 f16 f+16 b-16 e-16 f16 f+16 b-16 e-16 o5 c+16
    e-16 o4 b-16 a-16 f+16 f16 o3 b16 b16 o4 f+16 o3 b16 b16 o4 c+16 e-16 f+16 o3 b16 b16 o4 f+16 o3 b16 b16 o4 c+16
    e-16 f+16 o3 b16 b16 o4 f+16 o3 b16 b16 o4 c+16 e-16 f+16 d16 e-16 f16 o3 b-16 b-16 o4 a-16 f+16 f16 b-16 e-16
    f16 f+16 b-16 e-16 f16 f+16 b-16 e-16 f16 f+16 o5 c+16 e-16 o4 b-16 e-16 b-16 e-16 f16 f+16 b-16 e-16 f16 f+16
    b-16 e-16 o5 c+16 e-16 o4 b-16 a-16 f+16 f16 o3 b16 b16 o4 f+16 o3 b16 b16 o4 c+16 e-16 f+16 o3 b16 b16 o4 f+16
    o3 b16 b16 o4 c+16 e-16 f+16 o3 b16 b16 o4 f+16 o3 b16 b16 o4 c+16 e-16 f+16 d16 e-16 f16 o3 b-16 b-16 o4 a-16
    f+32 r8. V44 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 c+8 e-8
    f8 a-8 d8 e-8 f8 a-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8 e-8 f8 f+8 b-8
    c+8 e-8 f8 a-8 d8 e-8 f16 q2 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16
    f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 a-16 a-16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16
    f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16 b-16
    b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16
    b-16 b-16 b-16 b-16 f+16 f+16 a-16 a-16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16
    f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16
    b-16 b-16 o5 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16
    b-16 b-16 f+16 f+16 a-16 a-16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16
    b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16
    f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16
    f+16 f+16 a-16 a-16 b-16 b-16 b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16 b-16
    b-16 b-16 b-16 b-16 f+16 f+16 b-16 b-16 b-16 b-16 f+16 f+16 f+16 f+16 b-16 b-16 b-16 b-16 b-16 b-16 V40 o3 e-16
    b-16 o4 e-16 e-16 o3 e-16 b-16 o4 e-16 e-16 o3 f16 o4 c16 f16 f16 o3 f16 o4 c16 f16 f16 o3 g16 o4 d16 g16 g16 o3
    g16 o4 d16 g16 g16 o3 g16 o4 d16 g16 g16 o3 f16 o4 c16 f16 f16 o3 e-16 b-16 o4 e-16 e-16 o3 e-16 b-16 o4 e-16
    e-16 o3 f16 o4 c16 f16 f16 o3 f16 o4 c16 f16 f16 o3 g16 o4 d16 g16 g16 o3 g16 o4 d16 g16 g16 o3 g16 o4 d16 g16
    g16 o3 f16 o4 c16 f16 f16 o3 e-16 b-16 o4 e-16 e-16 o3 e-16 b-16 o4 e-16 e-16 o3 f16 o4 c16 f16 f16 o3 f16 o4
    c16 f16 f16 o3 g16 o4 d16 g16 g16 o3 g16 o4 d16 g16 g16 o3 g16 o4 d16 g16 g16 o3 f16 o4 c16 f16 f16 o3 e-16 b-16
    o4 e-16 e-16 o3 e-16 b-16 o4 e-16 e-16 o3 f16 o4 c16 f16 f16 o3 f16 o4 c16 f16 f16 o3 g16 o4 d16 g16 g16 o3 g16
    o4 d16 g16 g16 o3 g16 o4 d16 g16 g16 o3 f16 o4 c16 f16 f16 V44 e-16 b-16 o5 e-16 e-16 o4 e-16 b-16 o5 e-16 e-16
    o4 f16 o5 c16 f16 f16 o4 f16 o5 c16 f16 f16 o4 g16 o5 d16 g16 g16 o4 g16 o5 d16 g16 g16 o4 g16 o5 d16 g16 g16 o4
    f16 o5 c16 f16 f16 o4 e-16 b-16 o5 e-16 e-16 o4 e-16 b-16 o5 e-16 e-16 o4 f16 o5 c16 f16 f16 o4 f16 o5 c16 f16
    f16 o4 g16 o5 d16 g16 g16 o4 g16 o5 d16 g16 g16 o4 g16 o5 d16 g16 g16 o4 f16 o5 c16 f16 f16 o4 e-16 b-16 o5 e-16
    e-16 o4 e-16 b-16 o5 e-16 e-16 o4 f16 o5 c16 f16 f16 o4 f16 o5 c16 f16 f16 o4 g16 o5 d16 g16 g16 o4 g16 o5 d16
    g16 g16 o4 g16 o5 d16 g16 g16 o4 f16 o5 c16 f16 f16 o4 e-16 b-16 o5 e-16 e-16 o4 e-16 b-16 o5 e-16 e-16 o4 f16
    o5 c16 f16 f16 o4 f16 o5 c16 f16 f16 o4 g16 o5 d16 g16 g16 o4 g16 o5 d16 g16 g16 o4 g16 o5 d16 g16 g16 o4 g16 o5
    d16 g16 g16 r1
R @drum V93 l8 p3
; Intro continuation - eight bars.
[k h s h k h s h |]8
; A is repetition 1; B is repetitions 2-3 plus the four-bar close.
[k h s h k h s h | k h s h k h s h |
 k h s h k h s h | k h s h k r s h |
 k h s h k h s h | k h s h k h s h |
 k h s h k h s h | k h s h k r s h |]3
k h s h k h s h | k h s h k h s h | k h s h k h s h | k h s h k r s h |
[k h s h k h s h | k h s h k h s h | k h s h k h s h | k h s h k r s h |]4
"""

    const val SENBONZAKURA_DEMO_MML = BAD_APPLE_LLS_MML

    val senbonzakuraDemoResult: MmlCompileResult = MmlCompiler.compile(BAD_APPLE_LLS_MML)
    val llsLogoResult: MmlCompileResult = MmlCompiler.compile(LLS_LOGO_MML, LogoSongPatchBank)

    fun getArrangement(key: String, volume: Float): ArrangementLanes? {
        val result = when (key) {
            SENBONZAKURA_DEMO_KEY -> senbonzakuraDemoResult
            LLS_LOGO_KEY -> llsLogoResult
            else -> return null
        }

        val success = result as? MmlCompileResult.Success
            ?: error("MML arrangement '$key' failed to compile: $result")

        if (volume == 1f) return success.arrangement

        val arrangement = success.arrangement
        val compiled = requireNotNull(arrangement.compiledOpnaSong) {
            "MML bank entry '$key' did not produce the unified event program"
        }
        return arrangement.copy(compiledOpnaSong = compiled.withPlaybackGain(volume))
    }
}
