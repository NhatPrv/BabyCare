path = r"D:\MyDATA\SEMESTER6\Chuyende\Final\ml\data\external\nhanes\raw\19992000_DEMO_19992000.XPT"
with open(path,'rb') as f:
    head = f.read(200)
print(head[:200])
print('---HEX---')
print(head[:200].hex())
