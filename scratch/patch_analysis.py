"""
Compute exactly what changes are needed in each patch to compensate
for the engine fixes (ushr 22->19 and feedback -1).

Key insight: ushr 22 -> 19 only affects computeOpFree.
computeOpFree is used for operators that receive NO modulation input.
Those operators were playing at 1/8th frequency.

Which operators use computeOpFree per algorithm:
  alg 0: NONE (all chained: op0->op1->op2->op3)
  alg 1: op1 (op0 has fb, op1 is free, both feed into op2)
  alg 2: op1 (op0 has fb, op1 is free modulator of op2)
  alg 3: op2 (op0->op1, op2 is free, both feed into op3)
  alg 4: op2 (op0->op1 carrier, op2->op3 carrier)
  alg 5: NONE (op0 feeds op1,op2,op3 all via advanceOp)
  alg 6: op2, op3 (op0->op1, op2 free carrier, op3 free carrier)
  alg 7: op1, op2, op3 (all free carriers)
"""

# The patches and their algorithms
patches = {
    "ZunLead1": {"alg": 0, "fb": 2, "ops": [
        {"name": "op0", "mul": 1, "tl": 0},
        {"name": "op1", "mul": 1, "tl": 8},
        {"name": "op2", "mul": 1, "tl": 14},
        {"name": "op3", "mul": 1, "tl": 4},
    ]},
    "ZunBell1": {"alg": 2, "fb": 1, "ops": [
        {"name": "op0", "mul": 1, "tl": 0},
        {"name": "op1", "mul": 2, "tl": 4},  # FREE in alg2
        {"name": "op2", "mul": 1, "tl": 12},
        {"name": "op3", "mul": 1, "tl": 6},
    ]},
    "ZunBass1": {"alg": 1, "fb": 1, "ops": [
        {"name": "op0", "mul": 1, "tl": 0},
        {"name": "op1", "mul": 1, "tl": 16},  # FREE in alg1
        {"name": "op2", "mul": 1, "tl": 12},
        {"name": "op3", "mul": 1, "tl": 18},
    ]},
    "ZunPad1": {"alg": 4, "fb": 0, "ops": [
        {"name": "op0", "mul": 1, "tl": 6},
        {"name": "op1", "mul": 1, "tl": 10},
        {"name": "op2", "mul": 1, "tl": 8},   # FREE in alg4
        {"name": "op3", "mul": 1, "tl": 10},
    ]},
    "ZunChime1": {"alg": 7, "fb": 0, "ops": [
        {"name": "op0", "mul": 1, "tl": 6},
        {"name": "op1", "mul": 2, "tl": 8},   # FREE in alg7
        {"name": "op2", "mul": 3, "tl": 10},  # FREE in alg7
        {"name": "op3", "mul": 4, "tl": 12},  # FREE in alg7
    ]},
    "At54": {"alg": 0, "fb": 3, "ops": [
        {"name": "op0", "mul": 1, "tl": 0},
        {"name": "op1", "mul": 1, "tl": 6},
        {"name": "op2", "mul": 1, "tl": 12},
        {"name": "op3", "mul": 1, "tl": 8},
    ]},
    "At74": {"alg": 2, "fb": 2, "ops": [
        {"name": "op0", "mul": 1, "tl": 2},
        {"name": "op1", "mul": 2, "tl": 8},   # FREE in alg2
        {"name": "op2", "mul": 1, "tl": 14},
        {"name": "op3", "mul": 1, "tl": 10},
    ]},
    "At99": {"alg": 1, "fb": 2, "ops": [
        {"name": "op0", "mul": 1, "tl": 0},
        {"name": "op1", "mul": 1, "tl": 14},  # FREE in alg1
        {"name": "op2", "mul": 1, "tl": 10},
        {"name": "op3", "mul": 1, "tl": 16},
    ]},
    "At181": {"alg": 4, "fb": 1, "ops": [
        {"name": "op0", "mul": 1, "tl": 8},
        {"name": "op1", "mul": 1, "tl": 12},
        {"name": "op2", "mul": 1, "tl": 10},  # FREE in alg4
        {"name": "op3", "mul": 1, "tl": 12},
    ]},
}

# Which op indices use computeOpFree per algorithm
free_ops = {
    0: [],
    1: [1],
    2: [1],
    3: [2],
    4: [2],
    5: [],
    6: [2, 3],
    7: [1, 2, 3],
}

print("=" * 70)
print("PATCH ANALYSIS: Which patches are affected by ushr 22->19")
print("=" * 70)
print()

for name, patch in patches.items():
    alg = patch["alg"]
    fb = patch["fb"]
    affected = free_ops[alg]
    if affected:
        print(f"{name} (alg={alg}, fb={fb}): AFFECTED")
        for op_idx in affected:
            op = patch["ops"][op_idx]
            print(f"  {op['name']}: mul={op['mul']}, tl={op['tl']} -- was playing at 1/8th pitch")
            print(f"    With fix: now plays at correct pitch (8x higher than before)")
    else:
        print(f"{name} (alg={alg}, fb={fb}): NOT affected (no free ops)")
    print()

print("=" * 70)
print("FEEDBACK ANALYSIS: Which patches are affected by -1 fix")
print("=" * 70)
print()

fbtab = [31, 7, 6, 5, 4, 3, 2, 1]
for name, patch in patches.items():
    fb = patch["fb"]
    if fb > 0:
        old_net = 13 - fbtab[fb]
        new_net = 13 - (fbtab[fb] - 1)
        print(f"{name}: fb={fb}, old net shift=<<{old_net}, new net shift=<<{new_net} (2x STRONGER)")
    else:
        print(f"{name}: fb=0, NO FEEDBACK (unaffected)")

print()
print("=" * 70)
print("CONCLUSION")
print("=" * 70)
print()
print("The ushr 22->19 fix does NOT change modulation depth.")
print("It ONLY changes the PITCH of free-running operators.")
print("Free ops were 3 octaves too low. Now they play at correct pitch.")
print()
print("For CARRIER free ops (alg 7: all ops are carriers):")
print("  They were producing audio 3 octaves below the intended note.")
print("  Now they produce the correct note. This is purely a pitch fix.")
print("  NO patch adjustment needed for carriers.")
print()
print("For MODULATOR free ops (alg 1,2,3,4: modulators that are free):")
print("  They were modulating at 1/8th the intended frequency.")
print("  The modulation INDEX (depth) doesn't change, but the")
print("  modulation FREQUENCY does. The sidebands will be 8x further apart.")
print("  This changes timbre. The TL of these modulators may need adjustment.")
print()
print("The feedback -1 fix makes feedback 2x stronger for all patches with fb>0.")
print("Patches with high feedback may need fb reduced by 1 to compensate.")
