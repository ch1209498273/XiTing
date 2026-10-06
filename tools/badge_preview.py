#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把 Badges.kt 里的手绘路径原样翻译成 SVG，用于「不拿用户手机当调试器」的形体验证。
Canvas 的 moveTo/lineTo/cubicTo 与 SVG 的 M/L/C 语义一致，坐标也一致（y 向下）。

控制点全部照抄 Badges.kt，未做任何美化调整 —— 美化之后的图不是 App 里会显示的图。
四个区块：大图 / 实际尺寸(15dp) × 已点亮 / 未解锁。
"""
import os

GOLD_DEEP = "#FFA000"
GOLD_PALE = "#FFF3C4"
DISC_LOCKED = "#F2F3F5"
RING_LOCKED = "#D5D9DF"
INK_LOCKED = "#BFC4CC"

FLAME_TOP = "#FF7043"
FLAME_BOT = "#D84315"
FLAME_INNER = "#FFD54F"
SAND = "#E65100"
FRAME = "#6D4C41"
CAP = "#5D4037"
ARROW = "#37474F"

# PetView 实测：RADIUS_RATIO=0.042，视图高 180dp、density 3 → 徽章直径 ≈ 15dp ≈ 45px
ACTUAL_BADGE_PX = 45


def hourglass(cx, cy, s, filled):
    w, hh = s * 0.62, s * 0.86
    out = [f'<path d="M{cx-w},{cy-hh} L{cx+w},{cy-hh} L{cx},{cy} Z '
           f'M{cx-w},{cy+hh} L{cx+w},{cy+hh} L{cx},{cy} Z" fill="none" '
           f'stroke="{FRAME if filled else INK_LOCKED}" stroke-width="{s*0.13}"/>']
    if filled:
        up = f"M{cx-w*0.52},{cy-hh*0.34} L{cx+w*0.52},{cy-hh*0.34} L{cx},{cy} Z"
        dn = (f"M{cx-w*0.56},{cy+hh*0.86} L{cx+w*0.56},{cy+hh*0.86} "
              f"L{cx+w*0.34},{cy+hh*0.06} L{cx-w*0.34},{cy+hh*0.06} Z")
        out.append(f'<path d="{up} {dn}" fill="{SAND}"/>')
    cap = CAP if filled else INK_LOCKED
    out.append(f'<rect x="{cx-w*1.34}" y="{cy-hh-s*0.13}" width="{w*2.68}" height="{s*0.19}" fill="{cap}"/>')
    out.append(f'<rect x="{cx-w*1.34}" y="{cy+hh-s*0.06}" width="{w*2.68}" height="{s*0.19}" fill="{cap}"/>')
    return out


def flame(cx, cy, s, filled):
    w, hh = s * 0.82, s * 0.96
    d = (f"M{cx},{cy-hh} "
         f"C{cx+w*0.95},{cy-hh*0.30} {cx+w},{cy+hh*0.25} {cx+w*0.34},{cy+hh*0.76} "
         f"C{cx-w*0.18},{cy+hh*1.06} {cx-w},{cy+hh*0.48} {cx-w*0.56},{cy-hh*0.06} "
         f"C{cx-w*0.36},{cy+hh*0.24} {cx-w*0.20},{cy-hh*0.12} {cx-w*0.08},{cy+hh*0.18} Z")
    out = []
    if filled:
        out.append(f'<path d="{d}" fill="url(#gflame)"/>')
        inner = (f"M{cx},{cy-hh*0.22} "
                 f"C{cx+w*0.42},{cy+hh*0.08} {cx+w*0.34},{cy+hh*0.62} {cx},{cy+hh*0.74} "
                 f"C{cx-w*0.34},{cy+hh*0.62} {cx-w*0.42},{cy+hh*0.08} {cx},{cy-hh*0.22} Z")
        out.append(f'<path d="{inner}" fill="{FLAME_INNER}"/>')
    else:
        out.append(f'<path d="{d}" fill="none" stroke="{INK_LOCKED}" stroke-width="{s*0.15}" stroke-linejoin="round"/>')
    return out


def target(cx, cy, s, filled):
    out = []
    for rr, col in [(0.96, "#E53935"), (0.68, "#FFFDFD"), (0.42, "#E53935")]:
        if filled:
            out.append(f'<circle cx="{cx}" cy="{cy}" r="{s*rr}" fill="{col}"/>')
        else:
            out.append(f'<circle cx="{cx}" cy="{cy}" r="{s*rr}" fill="none" stroke="{INK_LOCKED}" stroke-width="{s*0.12}"/>')
    col = ARROW if filled else INK_LOCKED
    out.append(f'<line x1="{cx+s*1.16}" y1="{cy-s*0.86}" x2="{cx+s*0.48}" y2="{cy-s*0.36}" '
               f'stroke="{col}" stroke-width="{s*0.16}" stroke-linecap="round"/>')
    head = (f"M{cx+s*0.40},{cy-s*0.30} L{cx+s*0.66},{cy-s*0.44} L{cx+s*0.34},{cy-s*0.52} Z")
    if filled:
        out.append(f'<path d="{head}" fill="{col}"/>')
    else:
        out.append(f'<path d="{head}" fill="none" stroke="{col}" stroke-width="{s*0.16}" stroke-linejoin="round"/>')
    return out


def crown(cx, cy, s, filled):
    w = s * 1.9
    h = w * 0.85
    top = cy - h / 2
    d = (f"M{cx-w/2},{top+h} L{cx-w/2},{top+h*0.4} L{cx-w*0.3},{top+h*0.72} L{cx-w*0.16},{top} "
         f"L{cx},{top+h*0.5} L{cx+w*0.16},{top} L{cx+w*0.3},{top+h*0.72} L{cx+w/2},{top+h*0.4} "
         f"L{cx+w/2},{top+h} Z")
    out = []
    if filled:
        out.append(f'<path d="{d}" fill="url(#gcrown)"/>')
        out.append(f'<circle cx="{cx}" cy="{cy+w*0.16}" r="{w*0.07}" fill="#E53935"/>')
        out.append(f'<circle cx="{cx-w*0.28}" cy="{cy+w*0.26}" r="{w*0.05}" fill="#42A5F5"/>')
        out.append(f'<circle cx="{cx+w*0.28}" cy="{cy+w*0.26}" r="{w*0.05}" fill="#42A5F5"/>')
    else:
        out.append(f'<path d="{d}" fill="none" stroke="{INK_LOCKED}" stroke-width="{s*0.12}" stroke-linejoin="round"/>')
        out.append(f'<circle cx="{cx}" cy="{cy+w*0.16}" r="{w*0.07}" fill="none" stroke="{INK_LOCKED}" stroke-width="{s*0.08}"/>')
    return out


def disc(cx, cy, r, unlocked):
    if unlocked:
        return [f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#gdisc)"/>',
                f'<circle cx="{cx}" cy="{cy}" r="{r*0.97}" fill="none" stroke="{GOLD_DEEP}" stroke-width="{r*0.1}"/>']
    return [f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{DISC_LOCKED}"/>',
            f'<circle cx="{cx}" cy="{cy}" r="{r*0.97}" fill="none" stroke="{RING_LOCKED}" stroke-width="{r*0.08}"/>']


SHAPES = [("沙漏", hourglass), ("火焰", flame), ("靶心", target), ("皇冠", crown)]


def badge(cx, cy, r, idx, unlocked):
    return disc(cx, cy, r, unlocked) + SHAPES[idx][1](cx, cy, r * 0.62, unlocked)


def build():
    R = 46
    GAP = 90
    W = 4 * R * 2 + 3 * GAP + 80
    blocks = [
        ("已点亮 · 放大看形状", True, R),
        (f"已点亮 · App 里的实际直径（{ACTUAL_BADGE_PX}px ≈ 15dp）", True, ACTUAL_BADGE_PX / 2),
        ("未解锁 · 暗色轮廓", False, R),
        (f"未解锁 · 实际直径（{ACTUAL_BADGE_PX}px）", False, ACTUAL_BADGE_PX / 2),
    ]
    TOP = 56
    block_h = [R * 2 + 52, ACTUAL_BADGE_PX + 52, R * 2 + 52, ACTUAL_BADGE_PX + 52]
    H = TOP + sum(block_h) + 40

    p = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
         '<defs>',
         f'<radialGradient id="gdisc" cx="0.32" cy="0.30" r="0.8">'
         f'<stop offset="0" stop-color="{GOLD_PALE}"/><stop offset="1" stop-color="{GOLD_DEEP}"/></radialGradient>',
         f'<linearGradient id="gflame" x1="0" y1="0" x2="0" y2="1">'
         f'<stop offset="0" stop-color="{FLAME_TOP}"/><stop offset="1" stop-color="{FLAME_BOT}"/></linearGradient>',
         f'<linearGradient id="gcrown" x1="0" y1="0" x2="0" y2="1">'
         f'<stop offset="0" stop-color="#FFE082"/><stop offset="1" stop-color="{GOLD_DEEP}"/></linearGradient>',
         '</defs>',
         f'<rect width="{W}" height="{H}" fill="#FFFFFF"/>']

    y = TOP
    for (label, unlocked, r), bh in zip(blocks, block_h):
        p.append(f'<text x="{W/2}" y="{y+16}" font-size="15" font-family="sans-serif" '
                 f'fill="#5F6570" text-anchor="middle">{label}</text>')
        total = 4 * r * 2 + 3 * 40
        x0 = (W - total) / 2
        cy = y + 34 + r
        for i in range(4):
            cx = x0 + r + i * (r * 2 + 40)
            p += badge(cx, cy, r, i, unlocked)
            p.append(f'<text x="{cx}" y="{cy+r+22}" font-size="12" font-family="sans-serif" '
                     f'fill="#B0B5BD" text-anchor="middle">{SHAPES[i][0]}</text>')
        y += bh
    p.append('</svg>')
    return "\n".join(p)


if __name__ == "__main__":
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "badges_preview.svg")
    with open(out, "w", encoding="utf-8") as f:
        f.write(build())
    print("written:", out)
