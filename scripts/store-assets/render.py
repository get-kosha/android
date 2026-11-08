#!/usr/bin/env python3
"""Render the Play Store screenshots and feature graphic for every language.

Inputs:
  captures/<lang>/{add,activity,overview,recurring,accounts,overview-dark}.png
      Real screens from the demo account, made by capture.sh (git-ignored).
  fastlane/metadata/android/<locale>/captions.json
      Headlines and subs. "\\n" breaks a line, [text] is drawn in marigold.

Output, per locale:
  fastlane/metadata/android/<locale>/images/phoneScreenshots/1.png … 6.png (1080x1920)
  fastlane/metadata/android/<locale>/images/featureGraphic.png (1024x500)

The layout matches the Paper file "Kosha – Google Play listing". Needs Google Chrome,
ImageMagick and a network connection (fonts come from Google Fonts).

  python3 scripts/store-assets/render.py            # every language
  python3 scripts/store-assets/render.py de ja      # some languages
"""
import html, json, re, subprocess, sys, tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
META = ROOT / "fastlane/metadata/android"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

LOCALES = {
    "en": "en-US", "de": "de-DE", "es": "es-ES", "fr": "fr-FR", "pt": "pt-BR", "it": "it-IT",
    "nl": "nl-NL", "ru": "ru-RU", "ja": "ja-JP", "zh": "zh-CN", "ko": "ko-KR", "hi": "hi-IN",
}

# Headline and body fonts. Bricolage Grotesque and Geist cover Latin only.
LATIN = ("Bricolage Grotesque", 700, "Geist", -0.025, 1.04)
FONTS = {
    "ru": ("Onest", 700, "Onest", -0.025, 1.06),
    "ja": ("Noto Sans JP", 800, "Noto Sans JP", 0, 1.16),
    "zh": ("Noto Sans SC", 800, "Noto Sans SC", 0, 1.16),
    "ko": ("Noto Sans KR", 800, "Noto Sans KR", -0.01, 1.16),
    "hi": ("Noto Sans Devanagari", 700, "Noto Sans Devanagari", 0, 1.3),
}

PEACOCK, MARIGOLD, PAPER, INK = "#0F524E", "#F4A62A", "#F3F5F0", "#0B0B0C"


def rich(text):
    """Escape, turn [x] into accent spans and \\n into line breaks."""
    out = html.escape(text)
    out = re.sub(r"\[(.+?)\]", r'<span class="a">\1</span>', out, flags=re.S)
    return out.replace("\n", "<br>")


def page(lang, body, w, h):
    head, hw, text, track, lh = FONTS.get(lang, LATIN)
    families = {head, text}
    link = "&".join(
        f"family={f.replace(' ', '+')}:wght@400;{hw}" for f in sorted(families)
    )
    return f"""<!doctype html><html lang="{lang}"><head><meta charset="utf-8">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?{link}&display=block">
<style>
*{{box-sizing:border-box;margin:0;padding:0}}
html,body{{width:{w}px;height:{h}px;overflow:hidden}}
body{{position:relative;font-family:'{text}',sans-serif}}
.h{{font-family:'{head}',sans-serif;font-weight:{hw};letter-spacing:{track}em;line-height:{lh};color:{PAPER};white-space:nowrap}}
.s{{font-weight:400;color:rgba(243,245,240,.74);white-space:nowrap}}
.a{{color:{MARIGOLD}}}
.phone{{position:absolute;background:{INK};box-shadow:0 40px 80px rgba(0,0,0,.28)}}
.phone img{{display:block;object-fit:cover}}
</style></head><body>{body}
<script>
// Shrink any line that is wider than its box, so long translations still fit.
document.fonts.ready.then(() => {{
  for (const el of document.querySelectorAll('[data-fit]')) {{
    let size = parseFloat(getComputedStyle(el).fontSize);
    while (el.scrollWidth > el.clientWidth + 1 && size > 20) {{ size -= 1; el.style.fontSize = size + 'px'; }}
  }}
  document.title = 'ready';
}});
</script></body></html>"""


def shot_html(lang, shot, image, index):
    dark = shot["screen"] == "overview-dark"
    first = index == 0  # the Add screen: phone at the top, caption at the bottom
    bg = INK if dark else PEACOCK
    bezel = "#2A2A2E" if dark else INK
    caption = f"""<div style="position:absolute;left:90px;width:900px;{'bottom:96px' if first else 'top:150px'};
      display:flex;flex-direction:column;align-items:center;gap:28px;text-align:center">
      <div class="h" data-fit style="width:900px;font-size:92px">{rich(shot['headline'])}</div>
      <div class="s" data-fit style="width:900px;font-size:38px;line-height:52px">{rich(shot['sub'])}</div></div>"""
    top = -410 if first else 520
    phone = f"""<div class="phone" style="top:{top}px;left:120px;padding:14px;border-radius:76px;background:{bezel}">
      <img src="{image.as_uri()}" style="width:812px;height:1812px;border-radius:62px"></div>"""
    return page(lang, f'<div style="position:absolute;inset:0;background:{bg}"></div>{phone}{caption}', 1080, 1920)


def feature_html(lang, feature, image):
    logo = (HERE / "logo.svg").as_uri()
    body = f"""<div style="position:absolute;inset:0;background:{PEACOCK}"></div>
    <div class="phone" style="top:56px;left:640px;padding:9px;border-radius:48px;transform:rotate(-6deg)">
      <img src="{image.as_uri()}" style="width:300px;height:669px;border-radius:40px"></div>
    <div style="position:absolute;left:72px;top:0;bottom:0;display:flex;flex-direction:column;justify-content:center;gap:34px">
      <img src="{logo}" style="width:196px;height:60px;display:block">
      <div class="h" data-fit style="width:500px;font-size:56px">{rich(feature)}</div></div>"""
    return page(lang, body, 1024, 500)


def render(markup, out, w, h):
    out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False, dir=HERE) as f:
        f.write(markup)
        src = Path(f.name)
    try:
        subprocess.run(
            [CHROME, "--headless=new", "--hide-scrollbars", "--force-device-scale-factor=1",
             f"--window-size={w},{h}", "--virtual-time-budget=20000", "--allow-file-access-from-files",
             f"--screenshot={out}", src.as_uri()],
            check=True, capture_output=True,
        )
    finally:
        src.unlink()
    # Play wants 24-bit PNGs with no alpha channel.
    subprocess.run(["magick", str(out), "-alpha", "off", "-define", "png:color-type=2", str(out)], check=True)


def main(langs):
    for lang in langs:
        locale = LOCALES[lang]
        caps = HERE / "captures" / lang
        data = json.loads((META / locale / "captions.json").read_text())
        images = META / locale / "images"
        for i, shot in enumerate(data["shots"]):
            render(shot_html(lang, shot, caps / f"{shot['screen']}.png", i),
                   images / "phoneScreenshots" / f"{i + 1}.png", 1080, 1920)
        render(feature_html(lang, data["feature"], caps / "overview.png"),
               images / "featureGraphic.png", 1024, 500)
        print(f"{locale}: 6 screenshots and the feature graphic")


if __name__ == "__main__":
    main(sys.argv[1:] or list(LOCALES))
