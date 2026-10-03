# FlockIt icon options: twelve marks as SVG (design/icons/NN-name.svg) and one sheet (design/icon-options.html)
# to pick from. Run:  python design/make_icon_options.py   then open the HTML (or screenshot it to PNG).
import math, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "icons")
os.makedirs(OUT, exist_ok=True)

INK = "#0A0A0A"; WHITE = "#F5F3EE"; YOLK = "#F7C948"; YOLK_L = "#FCE38A"; YOLK_D = "#EFA91F"
BEAK = "#F28C28"; RED = "#E5484D"; GREEN = "#57D9A3"; CYAN = "#7FD8E8"; NAVY = "#0D1B2A"; TEAL = "#103B3A"; INDIGO = "#6F86E8"


def arc(cx, cy, r, a0, a1):
    """SVG arc path from clock angle a0 to a1 (degrees clockwise from the top)."""
    def pt(a):
        t = math.radians(a)
        return cx + r * math.sin(t), cy - r * math.cos(t)
    x0, y0 = pt(a0); x1, y1 = pt(a1)
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return f"M {x0:.1f} {y0:.1f} A {r} {r} 0 {large} 1 {x1:.1f} {y1:.1f}"


def spokes(cx, cy, r0, r1, n, col, w):
    out = []
    for i in range(n):
        t = 2 * math.pi * i / n
        out.append(f'<line x1="{cx + r0 * math.sin(t):.1f}" y1="{cy - r0 * math.cos(t):.1f}" x2="{cx + r1 * math.sin(t):.1f}" y2="{cy - r1 * math.cos(t):.1f}" stroke="{col}" stroke-width="{w}" stroke-linecap="round"/>')
    return "".join(out)


ICONS = [
    ("chick-face", "Chick face", INK, f'''
      <path d="M100 58 C91 46 95 35 104 33 C102 41 107 45 113 41 C113 50 107 56 100 58Z" fill="{YOLK}"/>
      <circle cx="100" cy="106" r="52" fill="{YOLK}"/>
      <circle cx="69" cy="117" r="8" fill="#F5A25D" opacity=".6"/><circle cx="131" cy="117" r="8" fill="#F5A25D" opacity=".6"/>
      <circle cx="80" cy="99" r="7" fill="#141414"/><circle cx="82.6" cy="96.4" r="2.4" fill="#fff"/>
      <circle cx="120" cy="99" r="7" fill="#141414"/><circle cx="122.6" cy="96.4" r="2.4" fill="#fff"/>
      <path d="M88 110 Q100 104 112 110 L100 127 Z" fill="{BEAK}"/>'''),

    ("hen", "Hen", INK, f'''
      <g transform="translate(-4 -8)">
      <circle cx="128" cy="56" r="7" fill="{RED}"/><circle cx="138" cy="52" r="8" fill="{RED}"/><circle cx="148" cy="57" r="7" fill="{RED}"/>
      <path d="M50 70 C52 92 70 104 94 104 C110 104 118 92 118 76 A19 19 0 0 1 156 76 C156 118 142 150 102 150 C64 150 44 120 50 70Z" fill="{WHITE}"/>
      <path d="M155 70 L173 77 L155 84Z" fill="{BEAK}"/>
      <path d="M149 87 q8 2 6 13 q-9 0 -9 -10z" fill="{RED}"/>
      <circle cx="142" cy="73" r="3.4" fill="{INK}"/>
      <path d="M72 106 C76 134 112 138 124 112 C110 122 88 120 72 106Z" fill="#D8D4CA"/>
      <path d="M92 150 v15 M112 150 v15 M84 166 h16 M104 166 h16" stroke="{BEAK}" stroke-width="5" stroke-linecap="round"/>
      </g>'''),

    ("egg-growth", "Egg growth", INK, f'''
      <g transform="translate(100 100) scale(.92) translate(-100 -101)">
      <path d="M100 34 C66 34 46 86 46 116 C46 148 70 168 100 168 C130 168 154 148 154 116 C154 86 134 34 100 34Z" fill="none" stroke="{WHITE}" stroke-width="8"/>
      <path d="M68 130 L88 114 L104 124 L132 88" fill="none" stroke="{GREEN}" stroke-width="9" stroke-linecap="round" stroke-linejoin="round"/>
      <circle cx="132" cy="88" r="8.5" fill="{GREEN}"/>
      </g>'''),

    ("shed-fan", "Shed and fan", NAVY, f'''
      <g transform="translate(0 -3)">
      <path d="M38 150 V98 L100 58 L162 98 V150Z" fill="none" stroke="{WHITE}" stroke-width="8" stroke-linejoin="round"/>
      <circle cx="100" cy="116" r="26" fill="none" stroke="{CYAN}" stroke-width="5"/>
      <g fill="{CYAN}"><ellipse cx="100" cy="103" rx="7" ry="12"/><ellipse cx="100" cy="103" rx="7" ry="12" transform="rotate(120 100 116)"/><ellipse cx="100" cy="103" rx="7" ry="12" transform="rotate(240 100 116)"/></g>
      <circle cx="100" cy="116" r="5" fill="{NAVY}" stroke="{CYAN}" stroke-width="3"/>
      </g>'''),

    ("feeder-pan", "Feeder pan", INK, f'''
      <circle cx="100" cy="100" r="42" fill="{YOLK}"/>
      {spokes(100, 100, 18, 56, 10, WHITE, 5)}
      <circle cx="100" cy="100" r="59" fill="none" stroke="{WHITE}" stroke-width="8"/>
      <circle cx="100" cy="100" r="15" fill="{INK}" stroke="{WHITE}" stroke-width="6"/>'''),

    ("f-feeder-line", "F feeder line", INK, f'''
      <g transform="translate(-2 0)">
      <rect x="58" y="42" width="17" height="118" rx="8.5" fill="{WHITE}"/>
      <rect x="58" y="42" width="94" height="17" rx="8.5" fill="{WHITE}"/>
      <rect x="58" y="94" width="64" height="17" rx="8.5" fill="{WHITE}"/>
      <g stroke="{WHITE}" stroke-width="4"><line x1="102" y1="59" x2="102" y2="70"/><line x1="136" y1="59" x2="136" y2="70"/><line x1="104" y1="111" x2="104" y2="122"/></g>
      <g fill="{YOLK}"><path d="M89 70 h26 l-5 11 h-16z"/><path d="M123 70 h26 l-5 11 h-16z"/><path d="M91 122 h26 l-5 11 h-16z"/></g>
      </g>'''),

    ("growing-flock", "Growing flock", INK, f'''
      <g transform="translate(100 100) scale(.9) translate(-106 -114)">
      <circle cx="46" cy="133" r="15" fill="{YOLK_L}"/><path d="M59 128 L69 133 L59 138Z" fill="{BEAK}"/><circle cx="51" cy="129" r="2.4" fill="{INK}"/>
      <circle cx="86" cy="126" r="22" fill="{YOLK}"/><path d="M106 119 L119 126 L106 133Z" fill="{BEAK}"/><circle cx="94" cy="119" r="3" fill="{INK}"/>
      <circle cx="140" cy="117" r="31" fill="{YOLK_D}"/><path d="M168 108 L185 117 L168 126Z" fill="{BEAK}"/><circle cx="152" cy="106" r="3.8" fill="{INK}"/>
      <path d="M28 150 H182" stroke="{WHITE}" stroke-width="5" stroke-linecap="round" opacity=".85"/>
      </g>'''),

    ("chart-hen", "Chart hen", INK, f'''
      <g transform="translate(-2 -4)">
      <g fill="{GREEN}"><rect x="36" y="62" width="16" height="78" rx="8"/><rect x="58" y="82" width="16" height="58" rx="8"/><rect x="80" y="100" width="16" height="40" rx="8"/></g>
      <circle cx="135" cy="66" r="6" fill="{RED}"/><circle cx="144" cy="63" r="7" fill="{RED}"/><circle cx="153" cy="67" r="6" fill="{RED}"/>
      <circle cx="124" cy="120" r="31" fill="{WHITE}"/>
      <path d="M126 104 L126 84 A17 17 0 0 1 160 84 L156 112Z" fill="{WHITE}"/>
      <path d="M158 78 L174 84 L158 91Z" fill="{BEAK}"/>
      <circle cx="147" cy="81" r="3.2" fill="{INK}"/>
      <path d="M114 151 v13 M132 151 v13 M107 165 h14 M125 165 h14" stroke="{BEAK}" stroke-width="5" stroke-linecap="round"/>
      </g>'''),

    ("hatchling", "Hatchling", TEAL, f'''
      <g transform="translate(0 -2)">
      <path d="M72 60 C80 40 112 34 130 52 L119 60 L109 50 L99 62 L87 54Z" fill="{WHITE}" transform="rotate(-10 100 50)"/>
      <circle cx="100" cy="100" r="36" fill="{YOLK}"/>
      <circle cx="88" cy="94" r="4.6" fill="{INK}"/><circle cx="112" cy="94" r="4.6" fill="{INK}"/>
      <path d="M92 104 L108 104 L100 116Z" fill="{BEAK}"/>
      <path d="M48 114 L62 126 L76 112 L90 126 L100 114 L110 126 L124 112 L138 126 L152 114 C152 148 130 166 100 166 C70 166 48 148 48 114Z" fill="{WHITE}"/>
      </g>'''),

    ("rooster", "Rooster", YOLK, f'''
      <g transform="translate(-6 -6)">
      <circle cx="94" cy="62" r="10" fill="{RED}"/><circle cx="111" cy="55" r="12" fill="{RED}"/><circle cx="128" cy="63" r="10" fill="{RED}"/>
      <path d="M84 104 C80 128 74 148 70 166 H140 C136 148 140 124 142 104Z" fill="#FFFFFF"/>
      <circle cx="113" cy="97" r="30" fill="#FFFFFF"/>
      <path d="M139 88 L166 99 L139 109Z" fill="#D9641A"/>
      <ellipse cx="134" cy="124" rx="8" ry="13" fill="{RED}"/>
      <circle cx="121" cy="90" r="4.8" fill="{INK}"/>
      </g>'''),

    ("footprint", "Footprint", INK, f'''
      <g stroke="{YOLK}" stroke-width="13" stroke-linecap="round" fill="none" transform="translate(0 -2)">
      <path d="M100 118 L100 48"/><path d="M100 118 L60 68"/><path d="M100 118 L140 68"/><path d="M100 118 L100 158"/>
      </g>'''),

    ("day-dial", "Day dial", INK, f'''
      <circle cx="100" cy="100" r="60" fill="none" stroke="#E9D9A8" stroke-opacity=".38" stroke-width="12"/>
      <path d="{arc(100, 100, 60, 345, 75)}" fill="none" stroke="{INDIGO}" stroke-width="12"/>
      <path d="{arc(100, 100, 60, 210, 270)}" fill="none" stroke="{BEAK}" stroke-width="12"/>
      <circle cx="94" cy="112" r="24" fill="{YOLK}"/>
      <circle cx="111" cy="88" r="15" fill="{YOLK}"/>
      <path d="M124 84 L137 89 L124 94Z" fill="{BEAK}"/>
      <circle cx="115" cy="85" r="2.8" fill="{INK}"/>
      <path d="M78 106 C80 126 102 128 110 112 C100 118 88 116 78 106Z" fill="{YOLK_D}"/>'''),
]


_n = [0]
def svg(bg, body, size=200, shape="squircle"):
    _n[0] += 1; cid = f"c{_n[0]}"
    clip = '<rect width="200" height="200" rx="46"/>' if shape == "squircle" else '<circle cx="100" cy="100" r="100"/>'
    edge = '<rect x="1" y="1" width="198" height="198" rx="45" fill="none" stroke="#fff" stroke-opacity=".16" stroke-width="2"/>' if shape == "squircle" \
        else '<circle cx="100" cy="100" r="99" fill="none" stroke="#fff" stroke-opacity=".16" stroke-width="2"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="{size}" height="{size}">'
            f'<defs><clipPath id="{cid}">{clip}</clipPath></defs><g clip-path="url(#{cid})"><rect width="200" height="200" fill="{bg}"/>{body}</g>{edge}</svg>')


cells = []
for i, (slug, name, bg, body) in enumerate(ICONS, 1):
    with open(os.path.join(OUT, f"{i:02d}-{slug}.svg"), "w", encoding="utf-8") as f:
        f.write(svg(bg, body, 512))
    cells.append(f'''<div class="cell"><div class="big">{svg(bg, body, 190)}</div>
      <div class="row"><span class="n">{i}</span><span class="name">{name}</span><span class="small">{svg(bg, body, 44, "circle")}</span></div></div>''')

html = f'''<!doctype html><html><head><meta charset="utf-8"><title>FlockIt icon options</title><style>
 body{{margin:0;background:#1b1c1f;color:#f2f2f0;font-family:Segoe UI,Roboto,Arial,sans-serif}}
 h1{{font-size:26px;margin:26px 34px 2px;font-weight:650}} p{{margin:0 34px 14px;color:#a9acb3;font-size:15px}}
 .grid{{display:grid;grid-template-columns:repeat(4,1fr);gap:22px 20px;padding:10px 34px 30px}}
 .cell{{background:#26272b;border-radius:22px;padding:18px 18px 12px}}
 .big{{display:flex;justify-content:center}}
 .row{{display:flex;align-items:center;gap:10px;margin-top:12px}}
 .n{{font:700 22px/1 Consolas,monospace;color:#F7C948;min-width:30px}} .name{{flex:1;font-size:16px}} .small svg{{display:block}}
</style></head><body><h1>FlockIt icon options</h1><p>Pick a number. The small round one shows it at home-screen size. The loading animation will be the chosen mark, moving.</p>
<div class="grid">{''.join(cells)}</div></body></html>'''
with open(os.path.join(HERE, "icon-options.html"), "w", encoding="utf-8") as f:
    f.write(html)
print("wrote", len(ICONS), "icons")
