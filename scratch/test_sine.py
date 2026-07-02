with open("D:/Programes/ym2608-master/sinetab.coe") as f:
    lines = f.readlines()[2:]
vals = [int(x.strip().strip(',').strip(';'), 16) for x in lines if x.strip()]
print(f"Max: {max(vals)}, Min: {min(vals)}")
print(f"Midpoint (512): {vals[512]}")
