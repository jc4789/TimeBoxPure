with open("D:/Programes/ym2608-master/sinetab.coe") as f:
    lines = f.readlines()[2:]
vals = [int(x.strip().strip(',').strip(';'), 16) for x in lines if x.strip()]

for i in range(10):
    print(f"Index {i}: {vals[i]}")

for i in range(250, 260):
    print(f"Index {i}: {vals[i]}")

