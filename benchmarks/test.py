import numpy as np
import pandas as pd

data = pd.read_csv('./200 apps/200 benchmarks.csv')

df = data.copy()

print(data.columns)


i = 0
no = 1
while i < 1000:
    for j in range(5):
        df.iloc[i + j].at['appid'] = str(no)
    
    no += 1
    i += 5

print(df.tail())
df.to_csv('./new.csv')