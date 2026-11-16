"""Builds shared_prefs/radio.xml for the screenshot emulator from AutoTuneRadio-stations.txt.
Usage: python3 make_prefs.py <stations.txt> <out.xml> key=value ...  (values: true/false → boolean, digits → int)"""
import csv, io, json, sys
from xml.sax.saxutils import escape

GROUPS = [("news", ["news", "ειδήσ", "ενημέρ", "talk"]), ("sports", ["sport", "σπορ", "αθλητ"]),
          ("rock", ["rock", "ροκ", "metal"]), ("pop", ["pop", "hits"]), ("dance", ["dance", "club"]),
          ("rnb", ["rnb", "r&b", "hip hop", "rap"]), ("chill", ["chill", "lounge", "smooth", "deep", "relax"]),
          ("greek", ["ελληνικ", "λαϊκ"]), ("jazz", ["jazz", "blues"]), ("classical", ["classic", "κλασικ"])]

def guess(name):
    n = name.lower()
    return next((k for k, words in GROUPS if any(w in n for w in words)), "")

src, out, *pairs = sys.argv[1:]
stations, seen = [], set()
for line in open(src, encoding="utf-8-sig"):
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    row = next(csv.reader(io.StringIO(line)))
    if len(row) < 2 or not row[1].startswith("http") or row[1] in seen:
        continue
    seen.add(row[1])
    stations.append({"id": f"s{len(stations) + 1}", "name": row[0], "url": row[1],
                     "logo": row[2] if len(row) > 2 else "", "group": row[3] if len(row) > 3 else guess(row[0])})

values = {"stations": json.dumps(stations, ensure_ascii=False)}
for p in pairs:
    k, v = p.split("=", 1)
    values[k] = v
with open(out, "w", encoding="utf-8") as f:
    f.write("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n")
    for k, v in values.items():
        if v in ("true", "false"):
            f.write(f'    <boolean name="{k}" value="{v}" />\n')
        elif v.lstrip("-").isdigit() and k not in ("last", "chosen"):
            f.write(f'    <int name="{k}" value="{v}" />\n')
        else:
            f.write(f'    <string name="{k}">{escape(v)}</string>\n')
    f.write("</map>\n")
print(f"{len(stations)} stations, groups: {sorted({s['group'] for s in stations if s['group']})}")
