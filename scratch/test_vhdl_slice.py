def vhdl_slice(input_val):
    # input is 18-bit signed
    # simulate taking bits 16 downto 0
    bits_17 = input_val & 0x3FFFF
    # take bits 16..0
    bits_17_to_0 = bits_17 & 0x1FFFF
    
    # concatenate with 15 zeros
    in1_unsigned = (bits_17_to_0 << 15) & 0xFFFFFFFF
    
    # cast to 32-bit signed
    if in1_unsigned & 0x80000000:
        in1_signed = in1_unsigned - 0x100000000
    else:
        in1_signed = in1_unsigned
        
    return in1_signed

val_pos = 64770
val_neg = -64770

print(f"Kotlin val_pos << 15: {val_pos << 15}")
print(f"VHDL val_pos: {vhdl_slice(val_pos)}")

print(f"Kotlin val_neg << 15: {val_neg << 15}")
print(f"VHDL val_neg: {vhdl_slice(val_neg)}")
