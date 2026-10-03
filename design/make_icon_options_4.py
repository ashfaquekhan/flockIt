# FlockIt icon options, fourth sheet (37-48): variations on 32, the F rooster — the letter F built from
# bars, its stem a rooster (comb, beak, eye). Run:  python design/make_icon_options_4.py
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "icons")
os.makedirs(OUT, exist_ok=True)

BLACK = "#000000"; WHITE = "#F2F2F0"; GREEN = "#8FE3BE"; GOLD = "#F2CF8A"; RED = "#E5534B"; BEAK = "#F0A23A"


def comb(cx, cy, s=1.0):
    return (f'<circle cx="{cx - 12 * s}" cy="{cy + 5 * s}" r="{7.5 * s}" fill="{RED}"/><circle cx="{cx}" cy="{cy}" r="{9 * s}" fill="{RED}"/>'
            f'<circle cx="{cx + 12 * s}" cy="{cy + 5 * s}" r="{7.5 * s}" fill="{RED}"/>')


def f_rooster(stem=WHITE, arm1=WHITE, arm2=WHITE, rx=8, outline=False, feet=False, face=True):
    """The F of 32: stem x 60-86, arms to the right, rooster head on the stem looking left."""
    def bar(x, y, w, h, col, r):
        if outline:
            return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{BLACK}" stroke="{col}" stroke-width="7"/>'
        return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{col}"/>'
    out = [comb(73, 50, 0.82), bar(60, 56, 26, 104, stem, rx), bar(94, 60, 60, 24, arm1, min(rx, 12)), bar(94, 100, 42, 24, arm2, min(rx, 12))]
    if face:
        out.append(f'<path d="M{56 if outline else 60} 70 L{38 if outline else 42} 77 L{56 if outline else 60} 85Z" fill="{BEAK}"/>')
        out.append(f'<circle cx="72" cy="74" r="3.8" fill="{WHITE if outline else BLACK}"/>')
    if feet:
        out.append(f'<path d="M67 162 v11 M79 162 v11" stroke="{GOLD}" stroke-width="6" stroke-linecap="round"/>')
    return "".join(out)


def at(body, dx=0, dy=0, s=1.0, extra=""):
    return f'<g transform="translate({100 + dx} {100 + dy}) {extra} scale({s}) translate(-100 -100)">{body}</g>'


ICONS = [
    ("f-rooster-round", "Round, feet", at(f_rooster(rx=13, feet=True), 4, -6)),

    ("f-rooster-outline", "Outline", at(f_rooster(outline=True), 4, 0)),

    ("f-rooster-green", "Green arms", at(f_rooster(arm1=GREEN, arm2=GREEN), 4, 0)),

    ("f-rooster-solid", "One piece", at(
        comb(76, 48, 0.82) +
        f'<path d="M64 60 H150 V80 H86 V100 H132 V120 H86 V156 H64Z" fill="{WHITE}" stroke="{WHITE}" stroke-width="8" stroke-linejoin="round"/>'
        f'<path d="M60 70 L40 78 L60 87Z" fill="{BEAK}"/><circle cx="75" cy="76" r="4" fill="{BLACK}"/>', 4, 0)),

    ("f-beak", "Beak arm", at(
        comb(73, 50, 0.82) +
        f'<rect x="60" y="56" width="26" height="104" rx="8" fill="{WHITE}"/>'
        f'<path d="M94 60 H138 L158 72 L138 84 H94 Q90 84 90 80 V64 Q90 60 94 60Z" fill="{BEAK}"/>'
        f'<rect x="94" y="100" width="36" height="24" rx="12" fill="{RED}"/>'
        f'<circle cx="74" cy="74" r="3.8" fill="{BLACK}"/>', -4, 0)),

    ("f-lower-bird", "Small f bird", at(
        comb(112, 38, 0.78) +
        f'<path d="M84 158 V88 Q84 56 116 56 H122" fill="none" stroke="{WHITE}" stroke-width="24" stroke-linecap="round"/>'
        f'<path d="M58 102 H114" stroke="{WHITE}" stroke-width="20" stroke-linecap="round"/>'
        f'<path d="M134 47 L154 56 L134 65Z" fill="{BEAK}"/><circle cx="120" cy="53" r="3.8" fill="{BLACK}"/>'
        f'<path d="M78 172 v9 M90 172 v9" stroke="{GOLD}" stroke-width="6" stroke-linecap="round"/>', -4, -6)),

    ("f-rooster-card", "In a card",
     f'<rect x="38" y="38" width="124" height="124" rx="36" fill="none" stroke="{WHITE}" stroke-width="7"/>' + at(f_rooster(), 4, 2, 0.7)),

    ("f-rooster-i", "F + cursor", at(
        at(f_rooster(), -22, 0) +
        f'<g fill="{GOLD}"><rect x="152" y="58" width="13" height="102" rx="3"/><rect x="140" y="54" width="37" height="12" rx="4"/><rect x="140" y="152" width="37" height="12" rx="4"/></g>', 0, 0, 0.88)),

    ("f-rooster-lean", "Leaning", at(f_rooster(), 2, 0, 1.0, "skewX(-12)")),

    ("f-card-chick", "F chick",
     comb(100, 42) +
     f'<rect x="46" y="48" width="108" height="104" rx="32" fill="{WHITE}"/>'
     f'<path d="M46 86 L26 95 L46 104Z" fill="{BEAK}"/><circle cx="66" cy="84" r="5.5" fill="{BLACK}"/>'
     f'<g fill="{BLACK}"><rect x="86" y="70" width="18" height="64" rx="6"/><rect x="110" y="72" width="34" height="17" rx="6"/><rect x="110" y="96" width="24" height="17" rx="6"/></g>'
     f'<path d="M82 154 v12 M114 154 v12" stroke="{BEAK}" stroke-width="7" stroke-linecap="round"/>'),

    ("bars-f-rooster", "Bars into F", at(
        f'<rect x="20" y="126" width="20" height="34" rx="7" fill="{GREEN}"/><rect x="42" y="100" width="20" height="60" rx="7" fill="{GREEN}"/>' +
        at(f_rooster(), 18, 0), 0, 0, 0.9)),

    ("f-rooster-ring", "In a ring",
     f'<circle cx="100" cy="100" r="68" fill="none" stroke="{WHITE}" stroke-width="7"/>' + at(f_rooster(), 3, 2, 0.68)),
]

_n = [0]
def svg(body, size=200, shape="squircle"):
    _n[0] += 1; cid = f"q{_n[0]}"
    clip = '<rect width="200" height="200" rx="46"/>' if shape == "squircle" else '<circle cx="100" cy="100" r="100"/>'
    edge = '<rect x="1" y="1" width="198" height="198" rx="45" fill="none" stroke="#fff" stroke-opacity=".22" stroke-width="2"/>' if shape == "squircle" \
        else '<circle cx="100" cy="100" r="99" fill="none" stroke="#fff" stroke-opacity=".22" stroke-width="2"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="{size}" height="{size}">'
            f'<defs><clipPath id="{cid}">{clip}</clipPath></defs><g clip-path="url(#{cid})"><rect width="200" height="200" fill="{BLACK}"/>{body}</g>{edge}</svg>')


cells = []
for i, (slug, name, body) in enumerate(ICONS, 37):
    with open(os.path.join(OUT, f"{i:02d}-{slug}.svg"), "w", encoding="utf-8") as f:
        f.write(svg(body, 512))
    cells.append(f'''<div class="cell"><div class="big">{svg(body, 190)}</div>
      <div class="row"><span class="n">{i}</span><span class="name">{name}</span><span class="small">{svg(body, 44, "circle")}</span></div></div>''')

html = f'''<!doctype html><html><head><meta charset="utf-8"><title>FlockIt icon options 4</title><style>
 body{{margin:0;background:#1b1c1f;color:#f2f2f0;font-family:Segoe UI,Roboto,Arial,sans-serif}}
 .top{{display:flex;align-items:center;gap:26px;margin:22px 34px 6px}}
 h1{{font-size:26px;margin:0 0 4px;font-weight:650}} p{{margin:0;color:#a9acb3;font-size:15px;max-width:560px}}
 .picks{{display:flex;gap:14px;margin-left:auto;align-items:center}} .picks .lab{{color:#a9acb3;font-size:14px}}
 .pick{{display:flex;flex-direction:column;align-items:center;gap:4px}}
 .grid{{display:grid;grid-template-columns:repeat(4,1fr);gap:22px 20px;padding:10px 34px 30px}}
 .cell{{background:#26272b;border-radius:22px;padding:18px 18px 12px}}
 .big{{display:flex;justify-content:center}}
 .row{{display:flex;align-items:center;gap:10px;margin-top:12px}}
 .n{{font:700 22px/1 Consolas,monospace;color:#F2CF8A;min-width:30px}} .pick .n{{font-size:16px;min-width:0}}
 .name{{flex:1;font-size:16px}} .small svg{{display:block}}
</style></head><body><div class="top"><div><h1>FlockIt icon options · sheet 4</h1>
<p>Twelve takes on 32, the F rooster: the letter F built from bars, with a comb, a beak and an eye.</p></div>
<div class="picks"><span class="lab">your pick</span><div class="pick"><img src="icons/32-f-rooster.svg" width="92" height="92"><span class="n">32</span></div></div></div>
<div class="grid">{''.join(cells)}</div></body></html>'''
with open(os.path.join(HERE, "icon-options-4.html"), "w", encoding="utf-8") as f:
    f.write(html)
print("wrote", len(ICONS), "icons")
