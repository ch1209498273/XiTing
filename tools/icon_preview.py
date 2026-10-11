#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
成就图标（activity-alias 启动图标）前景的形体验证预览。
四个徽章图形从 Badges.kt 的 Canvas 路径 1:1 翻译成 SVG（视口 108，s=26，中心 54,54），
渲染成「自适应图标 = 圆角方底 + 前景」的成品观感：144px（48dp@3x 桌面实际大小）
与 72px（小尺寸辨识度）两档。改任何路径后必须重跑本预览再装机。
生成：.cowork-temp/icon_preview.svg
"""
import os

# 背景与主色（与 Badges.kt / colors.xml 一致）
BG = "#1F1B10"
CROWN_TOP, CROWN_DEEP = "#FFE082", "#FFA000"
CROWN_GEM = "#E53935"
CROWN_GEM_B = "#42A5F5"
SAND, FRAME, CAP = "#E65100", "#6D4C41", "#5D4037"
FLAME_TOP, FLAME_BOT, FLAME_IN = "#FF7043", "#D84315", "#FFD54F"
TGT_RED, TGT_WHITE, ARROW = "#E53935", "#FFFDFD", "#37474F"

# 四个前景的 pathData（108 视口，s=26，cx=cy=54）
CROWN = "M29.3,75 L29.3,49.8 L39.2,63.2 L46.1,33 L54,54 L61.9,33 L68.8,63.2 L78.7,49.8 L78.7,75 Z"
HOURGLASS_FRAME = "M36.9,30.6 L71.1,30.6 L54,54 Z M36.9,77.4 L71.1,77.4 L54,54 Z"
HOURGLASS_UP = "M44.0,45.4 L64.0,45.4 L54,54 Z"
HOURGLASS_DN = "M43.9,74.9 L64.1,74.9 L60.0,55.0 L48.0,55.0 Z"
FLAME_OUT = "M54,25.2 C77.35,47.48 78.6,63.66 61.98,78.66 C48.87,87.94 28.46,71.13 39.7,55.1 C44.6,63.68 47.52,53.1 50.35,62.1 Z"
FLAME_IN = "M54,44.04 C64.71,52.99 62.71,68.63 54,72.08 C45.29,68.63 43.29,52.99 54,44.04 Z"


def icon(name, body, x):
    """一个 108 视口的图标，放到 (x,0)，外加 144px 观感（外框即图标边界）"""
    return f'''
  <g transform="translate({x},0)">
    <rect width="108" height="108" rx="24" fill="{BG}"/>
    {body}
    <rect width="108" height="108" rx="24" fill="none" stroke="#333" stroke-width="1"/>
    <text x="54" y="126" font-size="11" fill="#333" text-anchor="middle">{name}</text>
  </g>'''


def crown_body():
    return f'''
    <defs><linearGradient id="cg" x1="0" y1="33" x2="0" y2="79" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="{CROWN_TOP}"/><stop offset="1" stop-color="{CROWN_DEEP}"/>
    </linearGradient></defs>
    <path d="{CROWN}" fill="url(#cg)"/>
    <circle cx="54" cy="61.9" r="3.5" fill="{CROWN_GEM}"/>
    <circle cx="40.2" cy="66.8" r="2.5" fill="{CROWN_GEM_B}"/>
    <circle cx="67.8" cy="66.8" r="2.5" fill="{CROWN_GEM_B}"/>'''


def hourglass_body():
    return f'''
    <path d="{HOURGLASS_UP}" fill="{SAND}"/>
    <path d="{HOURGLASS_DN}" fill="{SAND}"/>
    <path d="{HOURGLASS_FRAME}" fill="none" stroke="{FRAME}" stroke-width="4.4" stroke-linejoin="round"/>
    <rect x="30.4" y="26.6" width="47.2" height="6" fill="{CAP}"/>
    <rect x="30.4" y="76.4" width="47.2" height="6" fill="{CAP}"/>'''


def flame_body():
    return f'''
    <defs><linearGradient id="fg" x1="0" y1="29" x2="0" y2="79" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="{FLAME_TOP}"/><stop offset="1" stop-color="{FLAME_BOT}"/>
    </linearGradient></defs>
    <path d="{FLAME_OUT}" fill="url(#fg)"/>
    <path d="{FLAME_IN}" fill="{FLAME_IN}"/>'''


def target_body():
    return f'''
    <circle cx="54" cy="54" r="24.96" fill="{TGT_RED}"/>
    <circle cx="54" cy="54" r="17.68" fill="{TGT_WHITE}"/>
    <circle cx="54" cy="54" r="10.92" fill="{TGT_RED}"/>
    <line x1="84.16" y1="31.64" x2="66.48" y2="44.56" stroke="{ARROW}" stroke-width="4.16" stroke-linecap="round"/>
    <path d="M64.4,46.2 L71.16,42.56 L62.84,40.48 Z" fill="{ARROW}"/>'''


icons = [
    ("经典（对照）", crown_body()),   # 占位对照：正式版默认图标另有资源
    ("雷霆加冕 king", crown_body()),
    ("马拉松 hourglass", hourglass_body()),
    ("持之以恒 flame", flame_body()),
    ("百次成习 target", target_body()),
]

cells = []
for i, (name, body) in enumerate(icons):
    cells.append(icon(name, body, i * 150))
    cells.append(f'''
  <g transform="translate({i * 150 + 33},150) scale({72 / 108})">
    <rect width="108" height="108" rx="24" fill="{BG}"/>
    {body}
  </g>''')

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="750" height="260" style="background:#fff">
{''.join(cells)}
</svg>'''

out = os.path.join(os.path.dirname(__file__), "..", ".cowork-temp", "icon_preview.svg")
with open(out, "w", encoding="utf-8") as f:
    f.write(svg)
print("written:", os.path.abspath(out))
