# FlockIt icon options, third sheet (25-36): more in the manner of 13 (card chick), 16 (done bird) and
# 22 (bar rooster) — a plain app / data shape that is also a bird: comb, beak and an eye, nothing else.
# Run:  python design/make_icon_options_3.py
import math, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "icons")
os.makedirs(OUT, exist_ok=True)

BLACK = "#000000"; WHITE = "#F2F2F0"; GREEN = "#8FE3BE"; GOLD = "#F2CF8A"; RED = "#E5534B"; BEAK = "#F0A23A"


def pol(cx, cy, r, deg):
    t = math.radians(deg)
    return cx + r * math.sin(t), cy - r * math.cos(t)


def arc(cx, cy, r, a0, a1):
    x0, y0 = pol(cx, cy, r, a0); x1, y1 = pol(cx, cy, r, a1)
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return f"M {x0:.1f} {y0:.1f} A {r} {r} 0 {large} 1 {x1:.1f} {y1:.1f}"


def comb(cx, cy, s=1.0, rot=0):
    """Three red lobes centred on (cx, cy)."""
    g = f'<circle cx="{cx - 12 * s}" cy="{cy + 5 * s}" r="{7.5 * s}" fill="{RED}"/><circle cx="{cx}" cy="{cy}" r="{9 * s}" fill="{RED}"/><circle cx="{cx + 12 * s}" cy="{cy + 5 * s}" r="{7.5 * s}" fill="{RED}"/>'
    return f'<g transform="rotate({rot} {cx} {cy})">{g}</g>' if rot else g


def ring_bird():
    hx, hy = pol(100, 104, 50, 20)          # head: the leading end of the ring
    tx, ty = math.cos(math.radians(20)), math.sin(math.radians(20))      # heading (clockwise at 20 deg)
    nx, ny = -ty, tx
    tip = (hx + tx * 27, hy + ty * 27); b1 = (hx + tx * 10 + nx * 9, hy + ty * 10 + ny * 9); b2 = (hx + tx * 10 - nx * 9, hy + ty * 10 - ny * 9)
    cx, cy = pol(100, 104, 66, 12)
    return (f'{comb(cx, cy, 0.8, 14)}'
            f'<path d="{arc(100, 104, 50, 62, 20)}" fill="none" stroke="{GREEN}" stroke-width="20" stroke-linecap="round"/>'
            f'<path d="M{tip[0]:.1f} {tip[1]:.1f} L{b1[0]:.1f} {b1[1]:.1f} L{b2[0]:.1f} {b2[1]:.1f}Z" fill="{BEAK}"/>'
            f'<circle cx="{hx - tx * 2:.1f}" cy="{hy - ty * 2:.1f}" r="4.3" fill="{BLACK}"/>')


EGG = "M100 34 C66 34 46 86 46 116 C46 148 70 168 100 168 C130 168 154 148 154 116 C154 86 134 34 100 34Z"

ICONS = [
    ("card-chick-side", "Side chick", f'''
      <g transform="translate(-8 0)">
      {comb(118, 53)}
      <rect x="56" y="60" width="92" height="92" rx="28" fill="{BLACK}" stroke="{WHITE}" stroke-width="8"/>
      <path d="M152 92 L174 101 L152 110Z" fill="{BEAK}"/>
      <circle cx="122" cy="94" r="6" fill="{WHITE}"/>
      <path d="M76 116 q16 16 34 0" fill="none" stroke="{WHITE}" stroke-width="6" stroke-linecap="round"/>
      <path d="M88 156 v12 M116 156 v12" stroke="{GOLD}" stroke-width="7" stroke-linecap="round"/>
      </g>'''),

    ("card-chick-solid", "Solid chick", f'''
      {comb(100, 53)}
      <rect x="50" y="58" width="100" height="96" rx="30" fill="{WHITE}"/>
      <circle cx="82" cy="98" r="6.5" fill="{BLACK}"/><circle cx="118" cy="98" r="6.5" fill="{BLACK}"/>
      <path d="M90 110 H110 L100 124Z" fill="{BEAK}"/>
      <path d="M84 156 v12 M116 156 v12" stroke="{BEAK}" stroke-width="7" stroke-linecap="round"/>'''),

    ("check-chick", "Check chick", f'''
      {comb(100, 53)}
      <rect x="52" y="60" width="96" height="92" rx="28" fill="{BLACK}" stroke="{WHITE}" stroke-width="8"/>
      <circle cx="80" cy="94" r="6" fill="{WHITE}"/><circle cx="120" cy="94" r="6" fill="{WHITE}"/>
      <path d="M84 116 L96 128 L118 104" fill="none" stroke="{GREEN}" stroke-width="9" stroke-linecap="round" stroke-linejoin="round"/>
      <path d="M84 156 v12 M116 156 v12" stroke="{GOLD}" stroke-width="7" stroke-linecap="round"/>'''),

    ("done-bird-outline", "Outline tick", f'''
      <g transform="translate(-4 4)">
      {comb(129, 53, 0.8, -40)}
      <path d="M48 106 L82 140 L142 66" fill="none" stroke="{WHITE}" stroke-width="26" stroke-linecap="round" stroke-linejoin="round"/>
      <path d="M48 106 L82 140 L142 66" fill="none" stroke="{BLACK}" stroke-width="11" stroke-linecap="round" stroke-linejoin="round"/>
      <path d="M154 50 L177 53 L161 71Z" fill="{BEAK}"/>
      <circle cx="140" cy="68" r="4" fill="{WHITE}"/>
      </g>'''),

    ("trend-bird", "Trend bird", f'''
      <path d="M38 48 V160 H166" fill="none" stroke="{WHITE}" stroke-opacity=".32" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>
      {comb(133, 56, 0.72, -42)}
      <path d="M56 138 L84 110 L104 126 L144 74" fill="none" stroke="{GREEN}" stroke-width="19" stroke-linecap="round" stroke-linejoin="round"/>
      <path d="M153 60 L174 62 L160 78Z" fill="{BEAK}"/>
      <circle cx="142" cy="77" r="4.2" fill="{BLACK}"/>'''),

    ("ring-bird", "Ring bird", ring_bird()),

    ("bar-rooster-outline", "Outline bars", f'''
      <g transform="translate(-4 2)">
      <rect x="48" y="118" width="26" height="40" rx="8" fill="{BLACK}" stroke="{WHITE}" stroke-width="7"/>
      <rect x="84" y="92" width="26" height="66" rx="8" fill="{BLACK}" stroke="{WHITE}" stroke-width="7"/>
      {comb(133, 52, 0.82)}
      <rect x="120" y="58" width="26" height="100" rx="8" fill="{BLACK}" stroke="{WHITE}" stroke-width="7"/>
      <path d="M150 72 L168 79 L150 87Z" fill="{BEAK}"/>
      <circle cx="134" cy="78" r="3.8" fill="{WHITE}"/>
      </g>'''),

    ("f-rooster", "F rooster", f'''
      <g transform="translate(4 0)">
      {comb(73, 50, 0.82)}
      <rect x="60" y="56" width="26" height="104" rx="8" fill="{WHITE}"/>
      <rect x="94" y="60" width="60" height="24" rx="8" fill="{WHITE}"/>
      <rect x="94" y="100" width="42" height="24" rx="8" fill="{WHITE}"/>
      <path d="M60 70 L42 77 L60 85Z" fill="{BEAK}"/>
      <circle cx="71" cy="74" r="3.8" fill="{BLACK}"/>
      </g>'''),

    ("bars-card", "Bars in a card", f'''
      <g transform="translate(-8 0)">
      {comb(118, 53)}
      <rect x="56" y="60" width="92" height="92" rx="28" fill="{BLACK}" stroke="{WHITE}" stroke-width="8"/>
      <path d="M152 84 L174 93 L152 102Z" fill="{BEAK}"/>
      <circle cx="124" cy="86" r="5.5" fill="{WHITE}"/>
      <g fill="{GREEN}"><rect x="74" y="120" width="13" height="18" rx="5"/><rect x="93" y="108" width="13" height="30" rx="5"/><rect x="112" y="104" width="13" height="34" rx="5"/></g>
      <path d="M88 156 v12 M116 156 v12" stroke="{GOLD}" stroke-width="7" stroke-linecap="round"/>
      </g>'''),

    ("toggle-chick", "Switch chick", f'''
      <g transform="translate(-6 0)">
      {comb(125, 68, 0.82)}
      <rect x="42" y="76" width="112" height="56" rx="28" fill="{BLACK}" stroke="{WHITE}" stroke-width="8"/>
      <circle cx="126" cy="104" r="17" fill="{GOLD}"/>
      <circle cx="131" cy="99" r="3.6" fill="{BLACK}"/>
      <path d="M158 96 L177 104 L158 112Z" fill="{BEAK}"/>
      <path d="M80 136 v12 M104 136 v12" stroke="{GOLD}" stroke-width="7" stroke-linecap="round"/>
      </g>'''),

    ("magnifier-chick", "Lens chick", f'''
      <g transform="translate(2 4)">
      {comb(90, 44)}
      <path d="M118 118 L148 150" stroke="{WHITE}" stroke-width="13" stroke-linecap="round"/>
      <circle cx="90" cy="88" r="38" fill="{BLACK}" stroke="{WHITE}" stroke-width="8"/>
      <path d="M132 79 L153 88 L132 97Z" fill="{BEAK}"/>
      <circle cx="104" cy="80" r="5.5" fill="{WHITE}"/>
      </g>'''),

    ("egg-chick", "Egg chick", f'''
      {comb(100, 42, 0.8)}
      <g transform="translate(100 100) scale(.8) translate(-100 -98)"><path d="{EGG}" fill="{BLACK}" stroke="{WHITE}" stroke-width="10"/></g>
      <circle cx="85" cy="106" r="5.5" fill="{WHITE}"/><circle cx="115" cy="106" r="5.5" fill="{WHITE}"/>
      <path d="M91 117 H109 L100 130Z" fill="{GOLD}"/>
      <path d="M88 160 v11 M112 160 v11" stroke="{GOLD}" stroke-width="7" stroke-linecap="round"/>'''),
]

_n = [0]
def svg(body, size=200, shape="squircle"):
    _n[0] += 1; cid = f"m{_n[0]}"
    clip = '<rect width="200" height="200" rx="46"/>' if shape == "squircle" else '<circle cx="100" cy="100" r="100"/>'
    edge = '<rect x="1" y="1" width="198" height="198" rx="45" fill="none" stroke="#fff" stroke-opacity=".22" stroke-width="2"/>' if shape == "squircle" \
        else '<circle cx="100" cy="100" r="99" fill="none" stroke="#fff" stroke-opacity=".22" stroke-width="2"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="{size}" height="{size}">'
            f'<defs><clipPath id="{cid}">{clip}</clipPath></defs><g clip-path="url(#{cid})"><rect width="200" height="200" fill="{BLACK}"/>{body}</g>{edge}</svg>')


cells = []
for i, (slug, name, body) in enumerate(ICONS, 25):
    with open(os.path.join(OUT, f"{i:02d}-{slug}.svg"), "w", encoding="utf-8") as f:
        f.write(svg(body, 512))
    cells.append(f'''<div class="cell"><div class="big">{svg(body, 190)}</div>
      <div class="row"><span class="n">{i}</span><span class="name">{name}</span><span class="small">{svg(body, 44, "circle")}</span></div></div>''')

picks = "".join(f'<div class="pick"><img src="icons/{f}" width="92" height="92"><span class="n">{n}</span></div>'
                for n, f in [(13, "13-card-chick.svg"), (16, "16-check-bird.svg"), (22, "22-bar-rooster.svg")])

html = f'''<!doctype html><html><head><meta charset="utf-8"><title>FlockIt icon options 3</title><style>
 body{{margin:0;background:#1b1c1f;color:#f2f2f0;font-family:Segoe UI,Roboto,Arial,sans-serif}}
 .top{{display:flex;align-items:center;gap:26px;margin:22px 34px 6px}}
 h1{{font-size:26px;margin:0 0 4px;font-weight:650}} p{{margin:0;color:#a9acb3;font-size:15px;max-width:520px}}
 .picks{{display:flex;gap:16px;margin-left:auto;align-items:center}} .picks .lab{{color:#a9acb3;font-size:14px}}
 .pick{{display:flex;flex-direction:column;align-items:center;gap:4px}}
 .grid{{display:grid;grid-template-columns:repeat(4,1fr);gap:22px 20px;padding:10px 34px 30px}}
 .cell{{background:#26272b;border-radius:22px;padding:18px 18px 12px}}
 .big{{display:flex;justify-content:center}}
 .row{{display:flex;align-items:center;gap:10px;margin-top:12px}}
 .n{{font:700 22px/1 Consolas,monospace;color:#F2CF8A;min-width:30px}} .pick .n{{font-size:16px;min-width:0}}
 .name{{flex:1;font-size:16px}} .small svg{{display:block}}
</style></head><body><div class="top"><div><h1>FlockIt icon options · sheet 3</h1>
<p>More like your picks: a plain app or data shape that is also a bird — a comb, a beak and an eye.</p></div>
<div class="picks"><span class="lab">your picks</span>{picks}</div></div>
<div class="grid">{''.join(cells)}</div></body></html>'''
with open(os.path.join(HERE, "icon-options-3.html"), "w", encoding="utf-8") as f:
    f.write(html)
print("wrote", len(ICONS), "icons")
