from collections import Counter

d = Counter()
d['dragon'] += 1

d = Counter('red green red blue red blue green'.split())

print(d.elements())
print(list(d.elements()))
print(d.keys(), d.values, d.items())
