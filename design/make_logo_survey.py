# A survey picture: four of the leaning-left sketches and the logo the app has now, each with a number and nothing
# else. Nothing in the app changes.
# Run:  python design/make_logo_survey.py   (then render logo-survey.html to logo-survey.png)
import os

HERE = os.path.dirname(os.path.abspath(__file__))
BLACK = "#000000"

# shown as 1 … 5, in this order
PICKS = ["icons/49-tilt-left.svg", "icons/50-tilt-left-level.svg", "icons/53-tilt-left-body.svg",
         "icons/54-tilt-left-true-f.svg", "logo.svg"]


def body_of(path):
    """The drawing itself, without the file's own background and outline, so all five get the same tile."""
    s = open(os.path.join(HERE, path), encoding="utf-8").read()
    s = s.split(f'fill="{BLACK}"/>', 1)[1]
    if "clip-path" in open(os.path.join(HERE, path), encoding="utf-8").read():
        return s.rsplit("</g>", 1)[0]                   # a sketch: the clip group closes after the drawing
    return s.rsplit("</svg>", 1)[0]                     # the logo file


def tile(body, n, size=300):
    cid = f"s{n}"
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="{size}" height="{size}">'
            f'<defs><clipPath id="{cid}"><rect width="200" height="200" rx="46"/></clipPath></defs>'
            f'<g clip-path="url(#{cid})"><rect width="200" height="200" fill="{BLACK}"/>{body}</g>'
            f'<rect x="1" y="1" width="198" height="198" rx="45" fill="none" stroke="#fff" stroke-opacity=".22" stroke-width="2"/></svg>')


cells = [f'<div class="cell">{tile(body_of(p), i)}<div class="n">{i}</div></div>' for i, p in enumerate(PICKS, 1)]

html = f'''<!doctype html><html><head><meta charset="utf-8"><title>FlockIt logo survey</title><style>
 @font-face{{font-family:"JB";font-weight:700;src:url("../app/src/main/res/font/jetbrains_mono_bold.ttf")}}
 html,body{{margin:0;background:#1b1c1f}}
 body{{width:1080px;height:900px;overflow:hidden;display:flex;flex-direction:column;justify-content:center;gap:34px}}
 .row{{display:flex;justify-content:center;gap:40px}}
 .cell{{display:flex;flex-direction:column;align-items:center;gap:12px}}
 .cell svg{{display:block}}
 .n{{font:700 60px/1 "JB",Consolas,monospace;color:#F0A23A}}
</style></head><body><div class="row">{''.join(cells[:3])}</div><div class="row">{''.join(cells[3:])}</div></body></html>'''
with open(os.path.join(HERE, "logo-survey.html"), "w", encoding="utf-8") as f:
    f.write(html)
print("wrote logo-survey.html with", len(cells), "logos")
