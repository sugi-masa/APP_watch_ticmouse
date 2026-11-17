#!/usr/bin/env python3
"""Render the TicMouse Design C face faithfully from MouseView.java geometry/colors."""
import math
from PIL import Image, ImageDraw, ImageFont

S = 3  # supersample
W, H = 1400, 1680
img = Image.new("RGB", (W * S, H * S), (0x0B, 0x0C, 0x0E))
d = ImageDraw.Draw(img, "RGBA")
CX, CY, R = 700 * S, 830 * S, 580 * S

FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
FONTB = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"

def font(sz, bold=False):
    return ImageFont.truetype(FONTB if bold else FONT, int(sz * S))

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def clock_pt(theta_deg, r):
    a = math.radians(theta_deg)
    return (CX + math.sin(a) * r, CY - math.cos(a) * r)

# ---- face radial gradient (center slightly above middle) ----
GT, GM, GB = (0x2B, 0x2F, 0x34), (0x17, 0x19, 0x1C), (0x0B, 0x0C, 0x0E)
gy = CY - R * 0.1
steps = 220
for i in range(steps, 0, -1):
    t = i / steps
    col = lerp(GT, GM, t / 0.55) if t < 0.55 else lerp(GM, GB, (t - 0.55) / 0.45)
    rr = R * 1.05 * t
    d.ellipse([CX - rr, gy - rr, CX + rr, gy + rr], fill=col)
# clip corners outside face circle
mask = Image.new("L", img.size, 0)
md = ImageDraw.Draw(mask)
md.ellipse([CX - R, CY - R, CX + R, CY + R], fill=255)
page = Image.new("RGB", img.size, (0x0B, 0x0C, 0x0E))
img = Image.composite(img, page, mask)
d = ImageDraw.Draw(img, "RGBA")

# hairlines
d.ellipse([CX - R * .81, CY - R * .81, CX + R * .81, CY + R * .81], outline=(255, 255, 255, 13), width=S)
d.ellipse([CX - R * .42, CY - R * .42, CX + R * .42, CY + R * .42], outline=(255, 255, 255, 13), width=S)
d.line([CX, CY - R, CX, CY + R], fill=(255, 255, 255, 9), width=S)
d.line([CX - R, CY, CX + R, CY], fill=(255, 255, 255, 9), width=S)

# tick ring
for i in range(60):
    a = math.radians(i * 6 - 90)
    major = i % 5 == 0
    r1, r0 = R * 0.972, R * (0.918 if major else 0.93)
    col = (0x6B, 0x72, 0x78) if major else (0x4A, 0x50, 0x56)
    d.line([CX + math.cos(a) * r0, CY + math.sin(a) * r0,
            CX + math.cos(a) * r1, CY + math.sin(a) * r1], fill=col, width=(3 if major else 2) * S)
# edge + 12 o'clock notch
d.ellipse([CX - R * .99, CY - R * .99, CX + R * .99, CY + R * .99], outline=(0x3A, 0x40, 0x46), width=int(R * 0.02))
d.rectangle([CX - R * .008, CY - R * .99, CX + R * .008, CY - R * .925], fill=(0x7F, 0xD4, 0xE8))

def annular(r_out, r_in, start_canvas, sweep, fill):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    ld.pieslice([CX - r_out, CY - r_out, CX + r_out, CY + r_out], start_canvas, start_canvas + sweep, fill=fill + (255,))
    ld.ellipse([CX - r_in, CY - r_in, CX + r_in, CY + r_in], fill=(0, 0, 0, 0))
    # punch the inner circle via mask
    m = Image.new("L", img.size, 0)
    mk = ImageDraw.Draw(m)
    mk.pieslice([CX - r_out, CY - r_out, CX + r_out, CY + r_out], start_canvas, start_canvas + sweep, fill=255)
    mk.ellipse([CX - r_in, CY - r_in, CX + r_in, CY + r_in], fill=0)
    img.paste(Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB"), (0, 0), m)
    return ImageDraw.Draw(img, "RGBA")

# ---- click sectors (canvas angles: right -80..20, bottom 40..140, left 160..260) ----
btnIn, btnOut = R * 0.685, R * 0.90
d = annular(btnOut, btnIn, -80, 100, (0x29, 0x33, 0x3E))   # right
d = annular(btnOut, btnIn, 40, 100, (0x21, 0x24, 0x28))    # bottom
d = annular(btnOut, btnIn, 160, 100, (0x29, 0x35, 0x2E))   # left
for st in (-80, 40, 160):
    d.arc([CX - btnOut, CY - btnOut, CX + btnOut, CY + btnOut], st + 4, st + 96, fill=(255, 255, 255, 46), width=int(R * 0.008))
    d.arc([CX - btnIn, CY - btnIn, CX + btnIn, CY + btnIn], st, st + 100, fill=(0x66, 0x6E, 0x75), width=int(R * 0.01))

def arc_label(clock_mid, main, micro, color):
    x, y = clock_pt(clock_mid, (btnIn + btnOut) / 2)
    tile = Image.new("RGBA", (int(R * 0.62), int(R * 0.30)), (0, 0, 0, 0))
    td = ImageDraw.Draw(tile)
    f1, f2 = font(R * 0.115 / S, True), font(R * 0.045 / S)
    td.text((tile.width / 2, tile.height / 2 - R * 0.01), main, font=f1, fill=color + (255,), anchor="mm")
    if micro:
        td.text((tile.width / 2, tile.height / 2 + R * 0.075), micro, font=f2, fill=(0x7C, 0x84, 0x8B, 255), anchor="mm")
    tile = tile.rotate(-clock_mid, expand=True, resample=Image.BICUBIC)
    img.paste(tile, (int(x - tile.width / 2), int(y - tile.height / 2)), tile)

base = img
arc_label(300, "L", "", (0xDC, 0xEB, 0xE0))
arc_label(60, "R", "", (0xDF, 0xE9, 0xF2))
d = ImageDraw.Draw(img, "RGBA")

# ---- hub ----
ringOut, padIn, padOut = R * 0.65, R * 0.365, R * 0.58
d.ellipse([CX - ringOut + R * .008, CY - ringOut + R * .018, CX + ringOut + R * .008, CY + ringOut + R * .018], fill=(0, 0, 0, 110))
d.ellipse([CX - ringOut, CY - ringOut, CX + ringOut, CY + ringOut], fill=(0x14, 0x15, 0x18))
d.ellipse([CX - ringOut, CY - ringOut, CX + ringOut, CY + ringOut], outline=(0x7D, 0x85, 0x8D), width=int(R * 0.013))
for s_ in range(4):
    start = s_ * 90 - 90 - 35
    d = annular(padOut, padIn, start, 70, (0x25, 0x28, 0x2C))
    d.arc([CX - padOut, CY - padOut, CX + padOut, CY + padOut], start, start + 70, fill=(0x66, 0x6E, 0x75), width=int(R * 0.008))
    d.arc([CX - padIn, CY - padIn, CX + padIn, CY + padIn], start, start + 70, fill=(0x66, 0x6E, 0x75), width=int(R * 0.008))
# chevrons (apex outward at r=0.52R)
for s_ in range(4):
    th = s_ * 90
    # local frame: outward = clock direction
    def lp_(along, side):
        a = math.radians(th)
        ox, oy = math.sin(a), -math.cos(a)      # outward unit
        sx_, sy_ = math.cos(a), math.sin(a)     # sideways unit
        return (CX + ox * along + sx_ * side, CY + oy * along + sy_ * side)
    apex = lp_(R * 0.52, 0)
    lft = lp_(R * 0.52 - R * 0.068, -R * 0.047)
    rgt = lp_(R * 0.52 - R * 0.068, R * 0.047)
    d.line([apex, lft], fill=(0xD5, 0xDB, 0xE0), width=int(R * 0.011))
    d.line([apex, rgt], fill=(0xD5, 0xDB, 0xE0), width=int(R * 0.011))

# ---- knob (lever) ----
knobR = ringOut * 0.43
kx, ky = CX, CY
d.ellipse([kx - knobR - R * .014 + R * .012, ky - knobR - R * .014 + R * .02,
           kx + knobR + R * .014 + R * .012, ky + knobR + R * .014 + R * .02], fill=(0, 0, 0, 120))
knob = Image.new("RGBA", (int(knobR * 2), int(knobR * 2)), (0, 0, 0, 0))
kd = ImageDraw.Draw(knob)
for i in range(120, 0, -1):
    t = i / 120
    col = lerp((0x43, 0x48, 0x4E), (0x2A, 0x2D, 0x31), t / 0.6) if t < 0.6 else lerp((0x2A, 0x2D, 0x31), (0x1D, 0x1F, 0x22), (t - 0.6) / 0.4)
    rr = knobR * t
    kd.ellipse([knobR - rr - knobR * 0.3 * (1 - t), knobR - rr - knobR * 0.3 * (1 - t), knobR + rr - knobR * 0.3 * (1 - t), knobR + rr - knobR * 0.3 * (1 - t)], fill=col + (255,))
img.paste(knob, (int(kx - knobR), int(ky - knobR)), knob)
d = ImageDraw.Draw(img, "RGBA")
# knurled dashed ring
seg = 26
for i in range(seg):
    a0 = math.radians(i * 360 / seg)
    a1 = math.radians(i * 360 / seg + 5)
    d.arc([kx - knobR - R * .016, ky - knobR - R * .016, kx + knobR + R * .016, ky + knobR + R * .016],
          math.degrees(a0), math.degrees(a1), fill=(0x4A, 0x50, 0x56), width=int(R * 0.032))
d.ellipse([kx - knobR, ky - knobR, kx + knobR, ky + knobR], outline=(0x87, 0x8F, 0x96), width=int(R * 0.008))
d.ellipse([kx - R * .047, ky - R * .047, kx + R * .047, ky + R * .047], outline=(0x7F, 0xD4, 0xE8), width=int(R * 0.009))
d.ellipse([kx - R * .015, ky - R * .015, kx + R * .015, ky + R * .015], fill=(0x7F, 0xD4, 0xE8))
f = font(R * 0.046 / S)
d.text((kx, ky + R * 0.175), "L E V E R", font=f, fill=(0x9A, 0xA3, 0xAB), anchor="mm")

# ---- bottom info ----
f = font(R * 0.056 / S)
d.text((CX, CY + R * 0.815), "左クリック", font=f, fill=(0xD5, 0xDB, 0xE0), anchor="mm")
f2 = font(R * 0.05 / S)
for th, label, col, dot in ((207, "PHONE LINK", (0xD5, 0xDB, 0xE0), (0x8F, 0xE3, 0xA8)),
                            (153, "SCROLL", (0x9A, 0xA3, 0xAB), None)):
    x, y = clock_pt(th, R * 0.79)
    tw = d.textlength(label, font=f2)
    if dot:
        lx, ly = x - tw / 2 - R * .052, y - R * .018
        d.ellipse([lx - R * .037, ly - R * .037, lx + R * .037, ly + R * .037], fill=dot + (36,))
        d.ellipse([lx - R * .026, ly - R * .026, lx + R * .026, ly + R * .026], fill=dot + (78,))
        d.ellipse([lx - R * .027, ly - R * .027, lx + R * .027, ly + R * .027], outline=dot + (150,), width=int(R * 0.006))
        d.ellipse([lx - R * .013, ly - R * .013, lx + R * .013, ly + R * .013], fill=dot)
    d.text((x, y), label, font=f2, fill=col, anchor="mm")
f3 = font(R * 0.042 / S)
d.text((CX, CY + R * 0.955), "SYS.UI-03", font=f3, fill=(0x7C, 0x84, 0x8B), anchor="mm")

# ---- cursor crosshair (top) ----
curX, curY = CX, CY - R * 0.79
lr = R * 0.075
d.ellipse([curX - R * .045, curY - R * .045, curX + R * .045, curY + R * .045], outline=(0xD5, 0xDB, 0xE0), width=2 * S)
d.line([curX - lr, curY, curX - R * .02, curY], fill=(0xD5, 0xDB, 0xE0), width=2 * S)
d.line([curX + R * .02, curY, curX + lr, curY], fill=(0xD5, 0xDB, 0xE0), width=2 * S)
d.line([curX, curY - lr, curX, curY - R * .02], fill=(0xD5, 0xDB, 0xE0), width=2 * S)
d.line([curX, curY + R * .02, curX, curY + lr], fill=(0xD5, 0xDB, 0xE0), width=2 * S)
d.ellipse([curX - R * .014, curY - R * .014, curX + R * .014, curY + R * .014], fill=(0x7F, 0xD4, 0xE8))

img = img.resize((W, H), Image.LANCZOS)
d = ImageDraw.Draw(img)
d.text((W / 2, 92), "TicMouse — デザインC（ハイブリッド）", font=ImageFont.truetype(FONTB, 54), fill=(0xE8, 0xED, 0xF2), anchor="mm")
d.text((W / 2, H - 70), "中央レバー＋十字リング／上部：L・Rクリック／下部：状態表示 — 実装 0.5.1 の画面を再現",
       font=ImageFont.truetype(FONT, 30), fill=(0x9A, 0xA3, 0xAB), anchor="mm")

import os
out = os.path.expanduser("~/workspace/goals/ticmouse-wear-os-mouse-app/files/ticmouse-design-c.png")
img.save(out)
print("saved", out, img.size)
