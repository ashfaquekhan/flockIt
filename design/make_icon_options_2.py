# FlockIt icon options, second sheet (13-24): marks drawn in the app's own look — pure black, white outlines,
# colour only on the markers, in the app's value colours. Run:  python design/make_icon_options_2.py
import math, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "icons")
os.makedirs(OUT, exist_ok=True)

BLACK = "#000000"; WHITE = "#F2F2F0"
PRESENT = "#8FE3BE"; PROJECTED = "#F2CF8A"; IDEAL = "#A9CCF0"; COMMERCIAL = "#CDB6F7"; MIN = "#9CEBF2"; MAX = "#F7A6BF"
GOLD = "#F2CF8A"; RED = "#E5534B"; BEAK = "#F0A23A"


def pol(cx, cy, r, deg):
    t = math.radians(deg)
    return cx + r * math.sin(t), cy - r * math.cos(t)


def flock_round_pan():
    out = []
    for i in range(8):
        a = i * 45
        bx, by = pol(100, 100, 55, a); hx, hy = pol(100, 100, 37, a)
        out.append(f'<ellipse cx="{bx:.1f}" cy="{by:.1f}" rx="10" ry="15" fill="{GOLD}" transform="rotate({a} {bx:.1f} {by:.1f})"/>')
        out.append(f'<circle cx="{hx:.1f}" cy="{hy:.1f}" r="6.5" fill="{GOLD}"/>')
    out.append(f'<circle cx="100" cy="100" r="19" fill="{BLACK}" stroke="{WHITE}" stroke-width="6"/>')
    out.append(f'<circle cx="100" cy="100" r="9" fill="{WHITE}" opacity=".9"/>')
    return "".join(out)


def dot_grid_f():
    on = {(1, r) for r in range(6)} | {(c, 0) for c in range(1, 5)} | {(c, 3) for c in range(1, 4)}
    out = []
    for r in range(6):
        for c in range(6):
            x = 40 + c * 24; y = 40 + r * 24
            out.append(f'<circle cx="{x}" cy="{y}" r="8.5" fill="{GOLD}"/>' if (c, r) in on
                       else f'<circle cx="{x}" cy="{y}" r="5" fill="{WHITE}" opacity=".24"/>')
    return "".join(out)


def v_flock():
    pts = [(100, 56, WHITE), (79, 82, PRESENT), (121, 82, PROJECTED), (58, 108, IDEAL), (142, 108, COMMERCIAL), (37, 134, MIN), (163, 134, MAX)]
    return "".join(f'<path d="M{x - 10} {y + 7} L{x} {y - 7} L{x + 10} {y + 7}" fill="none" stroke="{c}" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>' for x, y, c in pts)


EGG = "M100 34 C66 34 46 86 46 116 C46 148 70 168 100 168 C130 168 154 148 154 116 C154 86 134 34 100 34Z"

ICONS = [
    ("card-chick", "Card chick", f'''
      <circle cx="88" cy="58" r="7.5" fill="{RED}"/><circle cx="100" cy="53" r="9" fill="{RED}"/><circle cx="112" cy="58" r="7.5" fill="{RED}"/>
      <rect x="52" y="60" width="96" height="92" rx="28" fill="{BLACK}" stroke="{WHITE}" stroke-width="8"/>
      <circle cx="82" cy="98" r="6" fill="{WHITE}"/><circle cx="118" cy="98" r="6" fill="{WHITE}"/>
      <path d="M90 110 H110 L100 124Z" fill="{GOLD}"/>
      <path d="M84 156 v12 M116 156 v12" stroke="{GOLD}" stroke-width="7" stroke-linecap="round"/>'''),

    ("f-cursor", "f + It cursor", f'''
      <path d="M74 152 V80 Q74 52 102 52 H110" fill="none" stroke="{WHITE}" stroke-width="18" stroke-linecap="round"/>
      <path d="M52 94 H104" stroke="{WHITE}" stroke-width="16" stroke-linecap="round"/>
      <g fill="{GOLD}"><rect x="134" y="50" width="14" height="104" rx="3"/><rect x="121" y="46" width="40" height="13" rx="4"/><rect x="121" y="145" width="40" height="13" rx="4"/></g>'''),

    ("dot-flock-f", "Flock of dots", dot_grid_f()),

    ("check-bird", "Done bird", f'''
      <path d="M48 106 L82 140 L142 66" fill="none" stroke="{PRESENT}" stroke-width="22" stroke-linecap="round" stroke-linejoin="round"/>
      <path d="M150 52 L172 54 L158 70Z" fill="{BEAK}"/>
      <circle cx="139" cy="69" r="4.5" fill="{BLACK}"/>'''),

    ("hen-lock", "f-lock-it", f'''
      <g transform="translate(0 4)">
      <circle cx="88" cy="42" r="7.5" fill="{RED}"/><circle cx="100" cy="37" r="9" fill="{RED}"/><circle cx="112" cy="42" r="7.5" fill="{RED}"/>
      <path d="M74 92 V72 A26 26 0 0 1 126 72 V92" fill="{BLACK}" stroke="{WHITE}" stroke-width="8"/>
      <path d="M129 64 L148 71 L129 79Z" fill="{BEAK}"/>
      <circle cx="109" cy="68" r="4.5" fill="{WHITE}"/>
      <rect x="56" y="90" width="88" height="68" rx="16" fill="{BLACK}" stroke="{WHITE}" stroke-width="8"/>
      <circle cx="100" cy="116" r="8.5" fill="{GOLD}"/><rect x="95.5" y="118" width="9" height="20" rx="3" fill="{GOLD}"/>
      </g>'''),

    ("viewfinder-chick", "Farm window", f'''
      <g fill="none" stroke="{WHITE}" stroke-width="8" stroke-linecap="round" stroke-linejoin="round">
      <path d="M44 74 V44 H74"/><path d="M126 44 H156 V74"/><path d="M156 126 V156 H126"/><path d="M74 156 H44 V126"/></g>
      <circle cx="94" cy="110" r="25" fill="{GOLD}"/><circle cx="113" cy="87" r="15" fill="{GOLD}"/>
      <path d="M126 83 L140 88 L126 94Z" fill="{BEAK}"/><circle cx="117" cy="84" r="3" fill="{BLACK}"/>'''),

    ("flock-round-pan", "Pan flock", flock_round_pan()),

    ("outline-hen", "Outline hen", f'''
      <g transform="translate(-4 -8)">
      <circle cx="128" cy="55" r="7" fill="{RED}"/><circle cx="138" cy="51" r="8" fill="{RED}"/><circle cx="148" cy="56" r="7" fill="{RED}"/>
      <path d="M50 70 C52 92 70 104 94 104 C110 104 118 92 118 76 A19 19 0 0 1 156 76 C156 118 142 150 102 150 C64 150 44 120 50 70Z" fill="{BLACK}" stroke="{WHITE}" stroke-width="7" stroke-linejoin="round"/>
      <path d="M159 70 L176 77 L159 84Z" fill="{BEAK}"/>
      <circle cx="142" cy="74" r="3.6" fill="{WHITE}"/>
      <path d="M92 153 v13 M112 153 v13" stroke="{WHITE}" stroke-width="6" stroke-linecap="round"/>
      </g>'''),

    ("egg-gauge", "Egg gauge", f'''
      <g transform="translate(100 100) scale(.92) translate(-100 -101)">
      <clipPath id="egg"><path d="{EGG}"/></clipPath>
      <rect x="40" y="96" width="120" height="80" fill="{GOLD}" clip-path="url(#egg)"/>
      <path d="{EGG}" fill="none" stroke="{WHITE}" stroke-width="8"/>
      <path d="M40 96 H160" stroke="{BLACK}" stroke-width="3" clip-path="url(#egg)"/>
      </g>'''),

    ("bar-rooster", "Bar rooster", f'''
      <g transform="translate(-4 2)">
      <rect x="48" y="118" width="26" height="40" rx="8" fill="{WHITE}"/>
      <rect x="84" y="92" width="26" height="66" rx="8" fill="{WHITE}"/>
      <circle cx="124" cy="58" r="6.5" fill="{RED}"/><circle cx="133" cy="54" r="7.5" fill="{RED}"/><circle cx="142" cy="58" r="6.5" fill="{RED}"/>
      <rect x="120" y="58" width="26" height="100" rx="8" fill="{WHITE}"/>
      <path d="M146 72 L164 79 L146 87Z" fill="{BEAK}"/>
      <circle cx="136" cy="76" r="3.8" fill="{BLACK}"/>
      </g>'''),

    ("range-chick", "In range", f'''
      <path d="M34 138 H166" stroke="{WHITE}" stroke-opacity=".3" stroke-width="10" stroke-linecap="round"/>
      <path d="M62 124 V152" stroke="{MIN}" stroke-width="7" stroke-linecap="round"/>
      <path d="M144 124 V152" stroke="{MAX}" stroke-width="7" stroke-linecap="round"/>
      <circle cx="98" cy="110" r="27" fill="{PRESENT}"/><circle cx="118" cy="85" r="16" fill="{PRESENT}"/>
      <path d="M132 80 L147 86 L132 92Z" fill="{BEAK}"/><circle cx="122" cy="82" r="3.2" fill="{BLACK}"/>'''),

    ("v-flock", "Colour flock", v_flock()),
]

_n = [0]
def svg(body, size=200, shape="squircle"):
    _n[0] += 1; cid = f"k{_n[0]}"
    clip = '<rect width="200" height="200" rx="46"/>' if shape == "squircle" else '<circle cx="100" cy="100" r="100"/>'
    edge = '<rect x="1" y="1" width="198" height="198" rx="45" fill="none" stroke="#fff" stroke-opacity=".22" stroke-width="2"/>' if shape == "squircle" \
        else '<circle cx="100" cy="100" r="99" fill="none" stroke="#fff" stroke-opacity=".22" stroke-width="2"/>'
    body = body.replace('id="egg"', f'id="egg{cid}"').replace('url(#egg)', f'url(#egg{cid})')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="{size}" height="{size}">'
            f'<defs><clipPath id="{cid}">{clip}</clipPath></defs><g clip-path="url(#{cid})"><rect width="200" height="200" fill="{BLACK}"/>{body}</g>{edge}</svg>')


cells = []
for i, (slug, name, body) in enumerate(ICONS, 13):
    with open(os.path.join(OUT, f"{i:02d}-{slug}.svg"), "w", encoding="utf-8") as f:
        f.write(svg(body, 512))
    cells.append(f'''<div class="cell"><div class="big">{svg(body, 190)}</div>
      <div class="row"><span class="n">{i}</span><span class="name">{name}</span><span class="small">{svg(body, 44, "circle")}</span></div></div>''')

html = f'''<!doctype html><html><head><meta charset="utf-8"><title>FlockIt icon options 2</title><style>
 body{{margin:0;background:#1b1c1f;color:#f2f2f0;font-family:Segoe UI,Roboto,Arial,sans-serif}}
 h1{{font-size:26px;margin:26px 34px 2px;font-weight:650}} p{{margin:0 34px 14px;color:#a9acb3;font-size:15px}}
 .grid{{display:grid;grid-template-columns:repeat(4,1fr);gap:22px 20px;padding:10px 34px 30px}}
 .cell{{background:#26272b;border-radius:22px;padding:18px 18px 12px}}
 .big{{display:flex;justify-content:center}}
 .row{{display:flex;align-items:center;gap:10px;margin-top:12px}}
 .n{{font:700 22px/1 Consolas,monospace;color:#F2CF8A;min-width:30px}} .name{{flex:1;font-size:16px}} .small svg{{display:block}}
</style></head><body><h1>FlockIt icon options · sheet 2</h1><p>In the app's own look: pure black, white outlines, colour only on the markers. Numbers carry on from the first sheet.</p>
<div class="grid">{''.join(cells)}</div></body></html>'''
with open(os.path.join(HERE, "icon-options-2.html"), "w", encoding="utf-8") as f:
    f.write(html)
print("wrote", len(ICONS), "icons")
