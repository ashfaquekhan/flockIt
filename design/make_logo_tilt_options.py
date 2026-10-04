# Logo sketches only (nothing in the app changes): the mark with the rooster bar leaning the other way (to the
# left) and the two dashes moved from the top to the bottom — the big one above the small one — so it reads as a
# chicken and an upside-down F. Several readings of that, numbered on from the earlier sheets.
# Run:  python design/make_logo_tilt_options.py
import math, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "icons")
os.makedirs(OUT, exist_ok=True)

BLACK = "#000000"; WHITE = "#F2F2F0"; RED = "#E5534B"; BEAK = "#F0A23A"


def comb(cx, cy, s=0.82):
    return (f'<circle cx="{cx - 12 * s}" cy="{cy + 5 * s}" r="{7.5 * s}" fill="{RED}"/><circle cx="{cx}" cy="{cy}" r="{9 * s}" fill="{RED}"/>'
            f'<circle cx="{cx + 12 * s}" cy="{cy + 5 * s}" r="{7.5 * s}" fill="{RED}"/>')


def rooster_bar():
    """The first bar: the stem with the comb, the beak and the eye (as in the logo now)."""
    return (comb(73, 50) + f'<rect x="60" y="56" width="26" height="104" rx="8" fill="{WHITE}"/>'
            f'<path d="M60 70 L42 77 L60 85Z" fill="{BEAK}"/><circle cx="72" cy="74" r="3.8" fill="{BLACK}"/>')


def mark(lean=12.0, level_arms=False, upper=(100, 60), lower=(136, 42), feet=False, mirror=False, shift=3.0):
    """
    lean: degrees the rooster bar's top leans to the LEFT (the logo now leans 12 to the right).
    upper / lower: (top y, length) of the dash above and the dash below it, both 24 thick, to the right of the bar.
    level_arms: the dashes stay level rectangles (only the rooster bar leans); otherwise they lean with it.
    """
    t = math.tan(math.radians(lean))
    skew = f'translate(100 100) skewX({lean}) translate(-100 -100)'
    def dash(y, w):
        if not level_arms:
            return f'<rect x="94" y="{y}" width="{w}" height="24" rx="8" fill="{WHITE}"/>'
        x = 94 + t * (y + 12 - 100)                      # follows the leaning bar's right edge
        return f'<rect x="{x:.1f}" y="{y}" width="{w}" height="24" rx="8" fill="{WHITE}"/>'
    dashes = dash(*upper) + dash(*lower)
    legs = ""
    if feet:
        y0 = lower[0] + 24
        legs = f'<path d="M104 {y0} v11 M122 {y0} v11" stroke="{BEAK}" stroke-width="6" stroke-linecap="round"/>'
    body = (f'<g transform="{skew}">{rooster_bar()}{"" if level_arms else dashes + legs}</g>' + (dashes if level_arms else ""))
    flip = 'translate(200 0) scale(-1 1)' if mirror else ''
    return f'<g transform="translate({shift} {-4 if feet else 0}) {flip}">{body}</g>'


OPTIONS = [
    ("tilt-left", "As described", "bar leans left · big dash over small, at the bottom", mark()),
    ("tilt-left-level", "Level dashes", "only the rooster bar leans; the dashes stay level", mark(level_arms=True)),
    ("tilt-left-strong", "Stronger lean", "18° instead of 12°", mark(lean=18, shift=1)),
    ("tilt-left-gentle", "Gentler lean", "8°", mark(lean=8, shift=5)),
    ("tilt-left-body", "Longer body", "big dash longer, small one shorter", mark(upper=(98, 70), lower=(136, 34), shift=0)),
    ("tilt-left-true-f", "Upside-down F", "long dash at the very bottom, short above it", mark(upper=(100, 42), lower=(136, 60))),
    ("tilt-left-feet", "With feet", "the same, on two feet (an extra)", mark(feet=True)),
    ("tilt-left-mirror", "Facing right", "the same, mirrored — in case left / right was meant the other way", mark(mirror=True, shift=-3)),
]

_n = [0]
def svg(body, size=200, shape="squircle"):
    _n[0] += 1; cid = f"t{_n[0]}"
    clip = '<rect width="200" height="200" rx="46"/>' if shape == "squircle" else '<circle cx="100" cy="100" r="100"/>'
    edge = '<rect x="1" y="1" width="198" height="198" rx="45" fill="none" stroke="#fff" stroke-opacity=".22" stroke-width="2"/>' if shape == "squircle" \
        else '<circle cx="100" cy="100" r="99" fill="none" stroke="#fff" stroke-opacity=".22" stroke-width="2"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="{size}" height="{size}">'
            f'<defs><clipPath id="{cid}">{clip}</clipPath></defs><g clip-path="url(#{cid})"><rect width="200" height="200" fill="{BLACK}"/>{body}</g>{edge}</svg>')


cells = []
for i, (slug, name, note, body) in enumerate(OPTIONS, 49):
    with open(os.path.join(OUT, f"{i:02d}-{slug}.svg"), "w", encoding="utf-8") as f:
        f.write(svg(body, 512))
    cells.append(f'''<div class="cell"><div class="big">{svg(body, 190)}</div>
      <div class="row"><span class="n">{i}</span><span class="name">{name}</span><span class="small">{svg(body, 44, "circle")}</span></div>
      <div class="note">{note}</div></div>''')

html = f'''<!doctype html><html><head><meta charset="utf-8"><title>FlockIt logo, leaning left</title><style>
 body{{margin:0;background:#1b1c1f;color:#f2f2f0;font-family:Segoe UI,Roboto,Arial,sans-serif}}
 .top{{display:flex;align-items:center;gap:26px;margin:22px 34px 6px}}
 h1{{font-size:26px;margin:0 0 4px;font-weight:650}} p{{margin:0;color:#a9acb3;font-size:15px;max-width:600px}}
 .picks{{display:flex;gap:14px;margin-left:auto;align-items:center}} .picks .lab{{color:#a9acb3;font-size:14px}}
 .pick{{display:flex;flex-direction:column;align-items:center;gap:4px}}
 .grid{{display:grid;grid-template-columns:repeat(4,1fr);gap:22px 20px;padding:10px 34px 30px}}
 .cell{{background:#26272b;border-radius:22px;padding:18px 18px 12px}}
 .big{{display:flex;justify-content:center}}
 .row{{display:flex;align-items:center;gap:10px;margin-top:12px}}
 .n{{font:700 22px/1 Consolas,monospace;color:#F0A23A;min-width:30px}} .pick .n{{font-size:16px;min-width:0}}
 .name{{flex:1;font-size:16px}} .small svg{{display:block}}
 .note{{font-size:13px;color:#a9acb3;margin-top:6px;min-height:34px}}
</style></head><body><div class="top"><div><h1>Logo sketches · leaning left, dashes at the bottom</h1>
<p>The rooster bar leans to the left instead of the right, and the two dashes move from the top to the bottom, the big one above the small one: a chicken, and an upside-down F. Sketches only — the app is unchanged.</p></div>
<div class="picks"><span class="lab">the logo now</span><div class="pick"><img src="logo.svg" width="92" height="92"><span class="n">45</span></div></div></div>
<div class="grid">{''.join(cells)}</div></body></html>'''
with open(os.path.join(HERE, "logo-tilt-options.html"), "w", encoding="utf-8") as f:
    f.write(html)
print("wrote", len(OPTIONS), "sketches")
