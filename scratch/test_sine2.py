with open("D:/Programes/ym2608-master/sinetab.coe") as f:
    lines = f.readlines()[2:]
vals = [int(x.strip().strip(',').strip(';'), 16) for x in lines if x.strip()]
print(f"Index 0: {vals[0]}")
print(f"Index 255: {vals[255]}")
print(f"Index 256: {vals[256]}")
print(f"Index 511: {vals[511]}")
print(f"Index 512: {vals[512]}")
print(f"Index 767: {vals[767]}")
print(f"Index 768: {vals[768]}")
print(f"Index 1023: {vals[1023]}")
