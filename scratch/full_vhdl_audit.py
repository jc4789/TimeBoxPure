"""
Complete VHDL-vs-Kotlin audit for the YM2608 FM synthesis pipeline.
Traces the EXACT signal path from phase -> sine -> envelope -> output -> modulation
in both the VHDL reference and the Kotlin engine, and identifies every discrepancy.
"""
import math

print("=" * 70)
print("STAGE 1: SINE TABLE ENCODING")
print("=" * 70)

# Read the actual VHDL sinetab.coe
with open("D:/Programes/ym2608-master/sinetab.coe") as f:
    lines = f.readlines()[2:]
vhdl_sine = [int(x.strip().strip(',').strip(';'), 16) for x in lines if x.strip()]

print(f"VHDL sinetab has {len(vhdl_sine)} entries")
print(f"Range: {min(vhdl_sine)} to {max(vhdl_sine)}")
print()

# VHDL operator.vhd line 105: sine_s := signed(sine);
# sine is 10 bits (0..1023). When cast to signed(9 downto 0):
#   values 0-511   -> positive (0 to 511)
#   values 512-1023 -> negative (-512 to -1) in two's complement
print("VHDL sine is cast to signed(9 downto 0):")
print("  Index 0   -> unsigned={}, signed={}".format(vhdl_sine[0], vhdl_sine[0] if vhdl_sine[0] < 512 else vhdl_sine[0] - 1024))
print("  Index 255 -> unsigned={}, signed={}".format(vhdl_sine[255], vhdl_sine[255] if vhdl_sine[255] < 512 else vhdl_sine[255] - 1024))
print("  Index 256 -> unsigned={}, signed={}".format(vhdl_sine[256], vhdl_sine[256] if vhdl_sine[256] < 512 else vhdl_sine[256] - 1024))
print("  Index 511 -> unsigned={}, signed={}".format(vhdl_sine[511], vhdl_sine[511] if vhdl_sine[511] < 512 else vhdl_sine[511] - 1024))
print("  Index 512 -> unsigned={}, signed={}".format(vhdl_sine[512], vhdl_sine[512] if vhdl_sine[512] < 512 else vhdl_sine[512] - 1024))
print("  Index 767 -> unsigned={}, signed={}".format(vhdl_sine[767], vhdl_sine[767] if vhdl_sine[767] < 512 else vhdl_sine[767] - 1024))
print("  Index 768 -> unsigned={}, signed={}".format(vhdl_sine[768], vhdl_sine[768] if vhdl_sine[768] < 512 else vhdl_sine[768] - 1024))
print("  Index 1023-> unsigned={}, signed={}".format(vhdl_sine[1023], vhdl_sine[1023] if vhdl_sine[1023] < 512 else vhdl_sine[1023] - 1024))

# Convert to signed like VHDL does
vhdl_sine_signed = []
for v in vhdl_sine:
    if v >= 512:
        vhdl_sine_signed.append(v - 1024)
    else:
        vhdl_sine_signed.append(v)

print(f"\nVHDL sine_s range: {min(vhdl_sine_signed)} to {max(vhdl_sine_signed)}")

# Now Kotlin AudioSinLut generates:
# sin((i + 0.5)/1024 * 2*PI) * 256, with rounding and ROM bias
# Range: approx -256 to +256
kotlin_sine = []
SINE_AMP = 256.0
TWO_PI = 6.283185307179586
for i in range(1024):
    phase = (i + 0.5) / 1024.0
    scaled = math.sin(phase * TWO_PI) * SINE_AMP
    if scaled >= 0:
        v = int(scaled + 0.5)
    else:
        v = int(scaled - 0.5)
    if i == 511:
        v = 0
    if i == 1023:
        v = -1
    if v < 0 and v % 2 == 0:
        v -= 2
    kotlin_sine.append(v)

print(f"\nKotlin sine range: {min(kotlin_sine)} to {max(kotlin_sine)}")

print("\n*** CRITICAL DISCREPANCY ***")
print(f"VHDL peak positive: {max(vhdl_sine_signed)}")
print(f"Kotlin peak positive: {max(kotlin_sine)}")
print(f"VHDL peak negative: {min(vhdl_sine_signed)}")
print(f"Kotlin peak negative: {min(kotlin_sine)}")
print(f"Ratio: VHDL is {max(vhdl_sine_signed) / max(kotlin_sine):.4f}x Kotlin")

print()
print("=" * 70)
print("STAGE 2: ENVELOPE OUTPUT")
print("=" * 70)

# VHDL: envelope is unsigned(15 downto 0)
# From fm_channel.vhd line 851:
#   envelope = cltab[eglevel] * gaintab[totalLevel]
# cltab is 8-bit unsigned (0..255), gaintab is 8-bit unsigned (0..255)
# So envelope max = 255 * 255 = 65025
# envelope port is unsigned(15 downto 0), range 0..65535
env_max = 255 * 255
print(f"VHDL envelope max (cltab[0] * gaintab[0]) = {env_max}")
print(f"VHDL envelope port width: 16 bits unsigned, max 65535")

# Kotlin envNextRaw for OPN_RATE mode:
# cltab[eglevel] * gaintab[tl]
# Same tables, same multiplication -> same max 65025
print(f"\nKotlin envNextRaw OPN_RATE max = cltab[0] * gaintab[0] = {env_max}")

# Kotlin envNextRaw for LEGACY mode:
# (envFloat * outputLevel * 65535f).toInt()
# envFloat is 0..1, outputLevel is 0..1
# So this is also 0..65535
print(f"Kotlin envNextRaw LEGACY max = 65535")

print()
print("=" * 70)
print("STAGE 3: OPERATOR OUTPUT (sine * envelope)")
print("=" * 70)

# VHDL operator.vhd:
#   sine_s  := signed(sine);              -- 10-bit signed, range -512 to +511 (but actual max ~256)
#   env_s   := signed('0' & envelope);    -- 17-bit signed (0 prepended), range 0..65535
#   sample  := sine_s * env_s;            -- 27-bit signed product
#   output  := sample(25 downto 8);       -- 18-bit signed, = (sine_s * env_s) >> 8

# Max VHDL output:
vhdl_max_output = max(vhdl_sine_signed) * env_max
print(f"VHDL max product (sine_s * env_s): {max(vhdl_sine_signed)} * {env_max} = {vhdl_max_output}")
print(f"VHDL max output (product >> 8): {vhdl_max_output >> 8}")
print(f"VHDL output width: signed(17 downto 0), range: -{1<<17} to {(1<<17)-1}")

# Kotlin Fm4OpVoice.kt line 360:
#   val out = (sineRaw * envRaw) shr 8
# sineRaw is from AudioSinLut.sample10BitInt, range ~-258 to +256
# envRaw is from envNextRaw, range 0..65025
kotlin_max_product = max(kotlin_sine) * env_max
print(f"\nKotlin max product (sineRaw * envRaw): {max(kotlin_sine)} * {env_max} = {kotlin_max_product}")
print(f"Kotlin max output (product >> 8): {kotlin_max_product >> 8}")

print(f"\n*** OPERATOR OUTPUT RATIO: VHDL/Kotlin = {(vhdl_max_output >> 8) / (kotlin_max_product >> 8):.4f}x ***")

print()
print("=" * 70)
print("STAGE 4: PHASE MODULATION")
print("=" * 70)

# VHDL operator.vhd line 96:
#   in1 := signed( input(16 downto 0) & "000000000000000");  -- input << 15 (but only 17 bits of input)
#   in2 := signed('0' & phase);
#   theta := in1 + in2;
# VHDL operator.vhd line 121:
#   theta_sine <= theta(28 downto 19);  -- 10-bit sine address

# So the FULL sine cycle is from theta bit 28 wrapping, meaning 2^29 = 536870912
# Modulation input is left-shifted by 15

# VHDL max modulation input (from op output) is the 18-bit operator output
vhdl_max_op_out = vhdl_max_output >> 8
# in1 uses input(16 downto 0), which is the lower 17 bits of the 32-bit signed input
# then <<15
vhdl_modulation_word = (vhdl_max_op_out & 0x1FFFF) << 15
print(f"VHDL max op output: {vhdl_max_op_out}")
print(f"VHDL modulation word (input(16..0) << 15): {vhdl_modulation_word}")
print(f"VHDL phase cycle: 2^29 = {1 << 29}")
print(f"VHDL modulation in cycles: {vhdl_modulation_word / (1 << 29):.4f}")

# Kotlin Fm4OpVoice.kt line 383:
#   val modulation = phaseMod shl 15
#   val phaseAddr = ((op.phase + modulation) ushr 19) and 1023
# But wait - computeOpFree uses ushr 22!
kotlin_max_op_out = kotlin_max_product >> 8
kotlin_modulation_word = kotlin_max_op_out << 15

print(f"\nKotlin max op output: {kotlin_max_op_out}")
print(f"Kotlin modulation word (phaseMod << 15): {kotlin_modulation_word}")

# advanceOp uses ushr 19 to extract 10-bit index
# BUT computeOpFree uses ushr 22!
print(f"\nKotlin advanceOp: (phase + mod) ushr 19, cycle = 2^29 = {1 << 29}")
print(f"Kotlin computeOpFree: phase ushr 22, cycle = 2^32 = {1 << 32}")

print(f"\n*** BUG: computeOpFree uses ushr 22 while advanceOp uses ushr 19! ***")
print(f"*** This means free ops run 8x slower in frequency! ***")
print(f"*** calcPhaseStep generates steps for a 2^29 cycle, ***")
print(f"*** but computeOpFree reads them as if cycle is 2^32 ***")

# Let's compute actual frequencies
# calcPhaseStep: step = (freq / sampleRate) * 536870912.0
# With advanceOp (ushr 19): index advances by step/2^19 per sample
#   Full cycle = 2^29 / step iterations -> freq = step * sampleRate / 2^29 = correct
# With computeOpFree (ushr 22): index advances by step/2^22 per sample
#   Full cycle = 2^32 / step iterations -> freq = step * sampleRate / 2^32 = freq / 8 !

test_freq = 440.0
sample_rate = 48000.0
phase_step = int(test_freq / sample_rate * 536870912.0)
actual_freq_advanceOp = phase_step * sample_rate / (1 << 29)
actual_freq_computeOpFree = phase_step * sample_rate / (1 << 32)

print(f"\nExample: target freq = {test_freq} Hz")
print(f"  phaseStep = {phase_step}")
print(f"  advanceOp freq = {actual_freq_advanceOp:.2f} Hz (correct)")
print(f"  computeOpFree freq = {actual_freq_computeOpFree:.2f} Hz (8x too slow!)")

print()
print("=" * 70)
print("STAGE 5: FINAL OUTPUT SCALING")
print("=" * 70)

# Kotlin Fm4OpVoice.kt line 341:
#   return (sum.toFloat() * p.totalLevel) / 131072f
# sum is the Int operator output
# p.totalLevel is Float (0..1)
# Divider is 131072 = 2^17

# VHDL: fm_channel.vhd outputs the sum of carriers as signed(17 downto 0)
# That's directly the 18-bit operator output(s) summed
# No additional division by 131072

# For algorithm 0 (serial chain), the carrier is op3
# VHDL max carrier output: 65025 (env max for the carrier)
# But wait, the output has already been >> 8 in operator.vhd
# So VHDL carrier output range is roughly +-65025 at the operator port

# The question is whether 131072 is the right normalizer
print(f"Kotlin divider: 131072 = 2^17")
print(f"VHDL op output range: signed(17 downto 0) = -{1<<17} to {(1<<17)-1} = +-{(1<<17)-1}")
print(f"Max single carrier output: {vhdl_max_op_out}")
print(f"Normalized by 131072: {vhdl_max_op_out / 131072.0:.6f}")
print(f"This would be ~50% of full scale for a single carrier")

# For algorithms with multiple carriers (like alg 7 = all 4):
# sum = 4 * max_output = 4 * 65025
max_4_carrier = 4 * vhdl_max_op_out
print(f"\n4-carrier sum: {max_4_carrier}")
print(f"Normalized by 131072: {max_4_carrier / 131072.0:.6f}")
print(f"This would clip at ~2x! Need proper carrier count normalization")

print()
print("=" * 70)
print("STAGE 6: FEEDBACK MATH")
print("=" * 70)

# VHDL fm_channel.vhd fbtab:
fbtab = [31, 7, 6, 5, 4, 3, 2, 1]
print("VHDL fbtab:", fbtab)

# VHDL operator_fb.vhd:
#   input = fb_out + fb_out2  (sign-extended sum of two previous outputs)
#   input is 19 bits signed
#   input_s = signed(input & "0000000000000")  -- << 13
#   in_shifted = shift_right(input_s, fb-1)    -- if fb > 0
#   (if fb == 0, in_shifted is just input << 13, but then checked: if fb==30, skip)
#   Actually: fb port is 5 bits, and fb=0 means no feedback (fbtab[0]=31)
# Wait, let me re-read...

# fm_channel.vhd line 460: fbtab(0) = 31, fbtab(7) = 1
# The fb register value (from user) is 0..7
# VHDL feeds fbtab[fb_value] as the 'fb' port to operator_fb
# operator_fb.vhd line 122-126:
#   if fb == 0: ci.fb := 0  (but fb port value is fbtab[x], so fb==0 never happens from fbtab)
#   else: ci.fb := fb - 1
# So for user fb=0: fbtab[0]=31, operator_fb gets fb=31, fb-1=30
#   Line 145: if fb==30 -> theta := phase_s (NO feedback)
# For user fb=7: fbtab[7]=1, operator_fb gets fb=1, fb-1=0
#   input << 13, shift_right by 0 = input << 13

print("\nFeedback strength per user setting:")
for user_fb in range(8):
    fb_val = fbtab[user_fb]
    if fb_val == 31:
        print(f"  fb={user_fb}: fbtab={fb_val}, fb-1=30 -> NO FEEDBACK (phase only)")
    else:
        fb_minus_1 = fb_val - 1
        # input << 13, then >> (fb-1)
        net_shift = 13 - fb_minus_1
        print(f"  fb={user_fb}: fbtab={fb_val}, fb-1={fb_minus_1}, net shift = <<13 >> {fb_minus_1} = <<{net_shift}")

# Now Kotlin Fm4OpVoice.kt line 344-354:
#   fbtab = [31, 7, 6, 5, 4, 3, 2, 1]  (matches VHDL)
#   fbOut = op0Feedback1 + op0Feedback2
#   inputS = fbOut shl 13
#   result = inputS shr fbtab[feedback]
print("\nKotlin feedback:")
for user_fb in range(8):
    fb_val = fbtab[user_fb]
    # inputS = fbOut << 13, then >> fbtab[fb]
    net_shift = 13 - fb_val
    print(f"  fb={user_fb}: fbOut << 13 >> {fb_val} = net shift <<{net_shift}")

print("\n*** FEEDBACK DISCREPANCY ***")
print("VHDL: shift_right(input<<13, fbtab[fb]-1)")
print("Kotlin: (fbOut << 13) shr fbtab[fb]")
print("Kotlin is shifting by fbtab[fb], VHDL shifts by fbtab[fb]-1")
print("This means Kotlin feedback is 2x WEAKER than VHDL for every setting!")

for user_fb in range(1, 8):
    fb_val = fbtab[user_fb]
    vhdl_net = 13 - (fb_val - 1)
    kotlin_net = 13 - fb_val
    print(f"  fb={user_fb}: VHDL net=<<{vhdl_net}, Kotlin net=<<{kotlin_net}, ratio={2**(vhdl_net-kotlin_net)}x")

print()
print("=" * 70)
print("STAGE 7: computeOpFree vs advanceOp PHASE SHIFT MISMATCH")
print("=" * 70)

# calcPhaseStep generates steps for 2^29 cycle
# computeOp0: (phase + modulation) ushr 19  -> 2^29 cycle (correct!)
# advanceOp:  (phase + modulation) ushr 19  -> 2^29 cycle (correct!)
# computeOpFree: phase ushr 22              -> 2^32 cycle (WRONG!)

print("calcPhaseStep: step = freq * 536870912 / sampleRate")
print("computeOp0:    phaseAddr = (phase + mod) ushr 19  -> cycle = 2^29 OK")
print("advanceOp:     phaseAddr = (phase + mod) ushr 19  -> cycle = 2^29 OK")  
print("computeOpFree: phaseAddr = phase ushr 22          -> cycle = 2^32 WRONG")
print()
print("computeOpFree operators play at 1/8th the correct frequency!")
print("In algorithm 4: ops 0,2 are carriers via computeOpFree paths")
print("  op0 goes through computeOp0 (correct ushr 19)")
print("  op2 goes through computeOpFree (WRONG ushr 22)")
print("  op1 gets modulated by op0 via advanceOp (correct)")
print("  op3 gets modulated by op2 via advanceOp (correct base, but op2 was wrong)")

print()
print("=" * 70)
print("SUMMARY OF ALL BUGS FOUND")
print("=" * 70)
print()
print("BUG 1: SINE TABLE AMPLITUDE MISMATCH")
print(f"  VHDL sine peak: {max(vhdl_sine_signed)} (when cast to signed)")
print(f"  Kotlin sine peak: {max(kotlin_sine)}")
print(f"  VHDL sinetab stores unsigned 10-bit values 0-1023")
print(f"  When cast to signed(9 downto 0), positive half goes up to {max(vhdl_sine_signed)}")
print(f"  Kotlin generates sin() * 256, so peak is only {max(kotlin_sine)}")
print(f"  This is NOT a bug if the sine table is normalized differently")
print(f"  But it means the operator output is proportionally different")
print()
print("BUG 2: computeOpFree PHASE SHIFT (ushr 22 should be ushr 19)")
print("  This makes all free-running operators play at 1/8th frequency")
print("  Affects algorithms where ops don't receive modulation input")
print()
print("BUG 3: FEEDBACK OFF BY ONE (shr fbtab[fb] should be shr (fbtab[fb]-1))")
print("  Kotlin feedback is exactly 2x weaker than VHDL for all fb settings")
print()

# Now let's verify: does the sine amplitude difference matter?
# The VHDL sine goes up to 256 in the positive half (index 255,256).
# Our Kotlin sine also goes up to 256.
# They match for positive! Let me check negative:
print("Checking if VHDL and Kotlin sine tables actually match when VHDL is signed:")
mismatches = 0
for i in range(1024):
    vhdl_val = vhdl_sine_signed[i]
    kotlin_val = kotlin_sine[i]
    if vhdl_val != kotlin_val:
        mismatches += 1
        if mismatches <= 10:
            print(f"  MISMATCH at index {i}: VHDL={vhdl_val}, Kotlin={kotlin_val}")
if mismatches > 10:
    print(f"  ... and {mismatches - 10} more mismatches")
print(f"Total mismatches: {mismatches} / 1024")

if mismatches > 0:
    # Check the pattern of mismatches
    print("\nSample of mismatches:")
    for i in [0, 1, 2, 3, 4, 5, 255, 256, 511, 512, 513, 767, 768, 1023]:
        print(f"  [{i:4d}] VHDL={vhdl_sine_signed[i]:+5d}  Kotlin={kotlin_sine[i]:+5d}  diff={vhdl_sine_signed[i]-kotlin_sine[i]:+3d}")

