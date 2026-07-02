import math

with open("D:/Programes/ym2608-master/sinetab.coe") as f:
    lines = f.readlines()[2:]
vals = [int(x.strip().strip(',').strip(';'), 16) for x in lines if x.strip()]

for i in range(1, 10):
    x = (i + 0.5) / 1024.0 * (math.pi / 2)
    s = math.sin(x)
    log_val = -math.log2(s) * 256
    print(f"Index {i}: val={vals[i]}, log2={log_val:.2f}")
