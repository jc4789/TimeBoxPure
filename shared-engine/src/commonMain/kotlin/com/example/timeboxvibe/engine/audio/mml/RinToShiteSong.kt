package com.example.timeboxvibe.engine.audio.mml

/**
 * Short PC-98 arrangement of 凛として咲く花の如く.
 *
 * The supplied score's final A-major section (measures 67..76) is used as a
 * self-contained ten-bar finale. Three FM parts voice the written harmony while
 * dedicated lead and piano-arpeggio parts keep the texture moving.
 */
internal const val RIN_TO_SHITE_MML_SOURCE: String = """
#MML 2
#BPM 136
#BAR 4/4

; Two-beat piano figures. Together they preserve the score's rolling left hand.
#MACRO arp_d o3 d16 a >d f+ a f+ d <a
#MACRO arp_cs o3 c+16 g+ >c+ e g+ e c+ <g+
#MACRO arp_bm o2 b16 >f+ b >d f+ d <b f+
#MACRO arp_e o3 e16 b >e g+ b g+ e <b
#MACRO arp_a o2 a16 >e a >c+ e c+ <a e
#MACRO arp_fs o2 f+16 >c+ f+ a >c+< a f+ c+

; Score-derived upper line for measures 67..76.
A @brass V72 Q8 p3 l8
o5 a8 b a g+ e4 f+8 d |
o5 c+4 d8 e f+4 e8 d |
o5 f+4 g+8 a b4 e8 f+ |
o5 e4 e8 g+ a4 b8 a |
o5 b8 >c+ d e d4 c+8< b |
o5 a4 b8 a g+4 e8 f+ |
o5 a2 r4 e16 f+16 g+8 |
o5 a2 r4 f+16 g+16 a8 |
o5 a8 b >c+16 d16 e8 d c+< b a |
o5 g+16 f+16 e4 >c+8 e2 |

; Sustained block harmony supports the rolling piano without replacing the removed
; drum track with another constant pulse.
B @strings V62 Q8 p2 l2
o4 d c+ | b e | a e | f+ d | a e | d e | a1 | e1 | a e | a1 |

; Continuous midrange arpeggios carry the score's piano motion.
D @piano V64 Q8 P1 p1 l16
${'$'}arp_d ${'$'}arp_cs | ${'$'}arp_bm ${'$'}arp_e | ${'$'}arp_a ${'$'}arp_e | ${'$'}arp_fs ${'$'}arp_d |
${'$'}arp_a ${'$'}arp_e | ${'$'}arp_d ${'$'}arp_e | ${'$'}arp_a ${'$'}arp_a | ${'$'}arp_e ${'$'}arp_e |
${'$'}arp_a ${'$'}arp_e | ${'$'}arp_a ${'$'}arp_a |

; Root, third, and fifth share one sustained ensemble envelope so they fuse as
; harmony while the P1 piano part supplies the written motion.
E @strings V48 Q8 p2 l2
o4 f+ e | d g+ | c+ g+ | a f+ | c+ g+ | f+ g+ | c+1 | g+1 | c+ g+ | c+1 |

F @strings V54 Q8 p1 l2
o4 a g+ | f+ b | e b | c+ a | e b | a b | e1 | b1 | e b | e1 |
"""
