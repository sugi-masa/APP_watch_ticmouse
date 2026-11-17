#!/usr/bin/env python3
"""Preview render of the ic_launcher vectors (watch + phone) at 480px."""
import math, os
from PIL import Image, ImageDraw

S = 4
N = 480

def wedge(base, cx, cy, r_out, r_in, th0, th1, fill):
    # annular wedge via pieslice + punch on a layer
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    a0 = th0 - 90  # clock -> PIL canvas degrees (0 = 3 o'clock, CW)
    ld.pieslice([cx - r_out, cy - r_out, cx + r_out, cy + r_out], a0, a0 + (th1 - th0), fill=fill + (255,))
    m = Image.new("L", base.size, 0)
    mk = ImageDraw.Draw(m)
    mk.pieslice([cx - r_out, cy - r_out, cx + r_out, cy + r_out], a0, a0 + (th1 - th0), fill=255)
    mk.ellipse([cx - r_in, cy - r_in, cx + r_in, cy + r_in], fill=0)
    base.paste(Image.alpha_composite(base.convert("RGBA"), layer).convert("RGB"), (0, 0), m)

def icon(phone=False):
    img = Image.new("RGB", (N * S, N * S), (0x10, 0x12, 0x15))
    d = ImageDraw.Draw(img, "RGBA")
    c = N * S / 2
    u = N * S / 108.0
    wedge(img, c, c, 43 * u, 33 * u, 250, 350, (0x2F, 0x3D, 0x34))
    wedge(img, c, c, 43 * u, 33 * u, 10, 110, (0x2F, 0x3D, 0x4A))
    d = ImageDraw.Draw(img, "RGBA")
    d.ellipse([c - 30 * u, c - 30 * u, c + 30 * u, c + 30 * u], fill=(0x14, 0x15, 0x18), outline=(0x7D, 0x85, 0x8D), width=int(2 * u))
    d.ellipse([c - 17 * u, c - 17 * u, c + 17 * u, c + 17 * u], fill=(0x2A, 0x2D, 0x31), outline=(0x87, 0x8F, 0x96), width=int(1.5 * u))
    d.ellipse([c - 5.5 * u, c - 5.5 * u, c + 5.5 * u, c + 5.5 * u], outline=(0x7F, 0xD4, 0xE8), width=int(2.5 * u))
    d.ellipse([c - 2 * u, c - 2 * u, c + 2 * u, c + 2 * u], fill=(0x7F, 0xD4, 0xE8))
    if phone:
        bx, by = 82 * u, 82 * u
        d.ellipse([bx - 10.5 * u, by - 10.5 * u, bx + 10.5 * u, by + 10.5 * u], fill=(0x14, 0x15, 0x18), outline=(255, 255, 255), width=int(3 * u))
        d.ellipse([bx - 3.2 * u, by - 3.2 * u, bx + 3.2 * u, by + 3.2 * u], fill=(0x4F, 0xC3, 0xF7))
    # circular mask like a launcher icon
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, N * S - 1, N * S - 1], fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out.resize((N, N), Image.LANCZOS)

canvas = Image.new("RGBA", (N * 2 + 60, N + 40), (0x0B, 0x0C, 0x0E, 255))
canvas.paste(icon(False), (20, 20), icon(False))
canvas.paste(icon(True), (N + 40, 20), icon(True))
out = os.path.expanduser("~/workspace/goals/ticmouse-wear-os-mouse-app/files/ticmouse-icons.png")
canvas.convert("RGB").save(out)
print("saved", out)
