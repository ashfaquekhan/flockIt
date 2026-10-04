# FlockIt font options: one sheet (design/font-options.html) showing the logo with each candidate typeface,
# on the app's own black-and-outline look. The fonts are loaded from Google Fonts by the browser only to draw
# this sheet; nothing is added to the app until one is chosen.  Run:  python design/make_font_options.py
import os

HERE = os.path.dirname(os.path.abspath(__file__))
LOGO = open(os.path.join(HERE, "logo.svg"), encoding="utf-8").read()
LOGO = LOGO[LOGO.index("<svg"):].replace('width="512" height="512"', 'width="64" height="64"')

# (letter, text family, numbers family, why)
OPTIONS = [
    ("A", "Rubik", "JetBrains Mono", "soft square corners, like the bars of the mark"),
    ("B", "Outfit", "JetBrains Mono", "clean geometric, round bowls"),
    ("C", "Nunito", "DM Mono", "fully rounded ends, friendly"),
    ("D", "Sora", "Space Mono", "wide and technical"),
    ("E", "Manrope", "IBM Plex Mono", "neutral, very readable small"),
    ("F", "Lexend", "Roboto Mono", "made for easy reading, wide letters"),
]
families = sorted({o[1] for o in OPTIONS} | {o[2] for o in OPTIONS})
link = "https://fonts.googleapis.com/css2?" + "&".join(
    "family=" + f.replace(" ", "+") + (":wght@400;500;700;800" if f in {o[1] for o in OPTIONS} else ":wght@400;500;700") for f in families) + "&display=block"

cells = []
for letter, text, mono, why in OPTIONS:
    cells.append(f'''<div class="cell" style="font-family:'{text}',sans-serif">
  <div class="head"><span class="n">{letter}</span><span class="fam">{text} <i>+ {mono} for numbers</i></span></div>
  <div class="brand">{LOGO}<span class="word">FlockIt</span></div>
  <div class="card">
    <div class="title">Today <span class="day">Day 11 · 30 Sep 2026</span></div>
    <div class="row"><span class="lab">Body weight</span><span class="val" style="font-family:'{mono}',monospace;color:#F2CF8A">370.5<small> g</small></span></div>
    <div class="refs" style="font-family:'{mono}',monospace"><span style="color:#CDB6F7">com 382.0</span> <span style="color:#A9CCF0">ideal 379.0</span></div>
    <div class="row"><span class="lab">Mortality till date</span><span class="val" style="font-family:'{mono}',monospace;color:#8FE3BE">1.92<small> %</small></span></div>
    <div class="tabs"><span class="on">Birds</span><span>Ventilation</span><span>Feed &amp; water</span></div>
    <div class="nav"><span class="on">Entry</span><span>Output</span><span>Stock</span><span>Tasks</span></div>
  </div>
  <div class="why">{why}</div>
</div>''')

html = f'''<!doctype html><html><head><meta charset="utf-8"><title>FlockIt font options</title>
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="{link}" rel="stylesheet"><style>
 body{{margin:0;background:#1b1c1f;color:#f2f2f0;font-family:Segoe UI,Arial,sans-serif}}
 h1{{font-size:26px;margin:24px 34px 2px;font-weight:650}} p.sub{{margin:0 34px 12px;color:#a9acb3;font-size:15px}}
 .grid{{display:grid;grid-template-columns:repeat(3,1fr);gap:20px;padding:8px 34px 30px}}
 .cell{{background:#000;border:1px solid #ffffff38;border-radius:22px;padding:16px 18px 14px}}
 .head{{display:flex;align-items:baseline;gap:10px;font-family:Segoe UI,Arial,sans-serif}}
 .n{{font:700 24px/1 Consolas,monospace;color:#F0A23A}} .fam{{font-size:15px;color:#f2f2f0}} .fam i{{color:#a9acb3;font-style:normal;font-size:13px}}
 .brand{{display:flex;align-items:center;gap:10px;margin:12px 0 10px}} .brand svg{{border-radius:14px}}
 .word{{font-size:38px;font-weight:800;letter-spacing:-.5px}}
 .card{{border:1px solid #ffffff47;border-radius:14px;padding:12px 14px}}
 .title{{font-size:16px;font-weight:700;display:flex;justify-content:space-between;align-items:baseline}} .day{{font-size:12px;font-weight:500;color:#ffffffa6}}
 .row{{display:flex;justify-content:space-between;align-items:baseline;margin-top:9px}} .lab{{font-size:15px;font-weight:700}}
 .val{{font-size:19px;font-weight:700}} .val small{{font-size:12px;color:#ffffff99;font-weight:500}}
 .refs{{text-align:right;font-size:12px}}
 .tabs,.nav{{display:flex;gap:6px;margin-top:11px}} .tabs span{{flex:1;text-align:center;border:1px solid #ffffff4d;border-radius:9px;padding:7px 2px;font-size:13px;font-weight:500;color:#ffffffb3}}
 .tabs .on{{border:2px solid #fff;color:#fff;font-weight:700;background:#ffffff1a}}
 .nav span{{flex:1;text-align:center;font-size:12px;color:#ffffff73;letter-spacing:.3px}} .nav .on{{color:#fff;font-weight:700}}
 .why{{margin-top:10px;font-size:13px;color:#a9acb3;font-family:Segoe UI,Arial,sans-serif}}
</style></head><body><h1>FlockIt font options</h1>
<p class="sub">The logo with six typefaces, on the app's own look. Each pairs a text face with a face for the numbers. Pick a letter.</p>
<div class="grid">{''.join(cells)}</div></body></html>'''
open(os.path.join(HERE, "font-options.html"), "w", encoding="utf-8").write(html)
print("wrote", len(OPTIONS), "options")
