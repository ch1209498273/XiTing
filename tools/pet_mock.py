# -*- coding: utf-8 -*-
"""精灵图鉴收集系统效果图：6 款换色变种 + streak 徽章"""
import os
from PIL import Image, ImageDraw, ImageFont
import numpy as np

ASSET = r'D:/AI任务/zcode/息屏听剧/物料/小红书_v3/assets'
pet = Image.open(os.path.join(ASSET, 'pet_raw.png')).convert('RGB')
F = r'C:/Windows/Fonts/msyh.ttc'
f_name = ImageFont.truetype(F, 34)
f_cond = ImageFont.truetype(F, 22)
f_title = ImageFont.truetype(F, 40)
f_sub = ImageFont.truetype(F, 24)
f_big = ImageFont.truetype(F, 44)


def recolor(img, hue_shift):
    a = np.array(img.convert('RGBA'), dtype=np.float32)
    r, g, b = a[..., 0] / 255, a[..., 1] / 255, a[..., 2] / 255
    al = a[..., 3]
    mx = np.max(a[..., :3], axis=-1) / 255
    mn = np.min(a[..., :3], axis=-1) / 255
    l = (mx + mn) / 2
    d = mx - mn
    sat = np.where(d == 0, 0, d / (1 - np.abs(2 * l - 1) + 1e-6))
    h = np.zeros_like(mx)
    m1 = (mx == r) & (d > 0)
    h[m1] = ((g - b)[m1] / d[m1]) % 6
    m2 = (mx == g) & (d > 0)
    h[m2] = (b - r)[m2] / d[m2] + 2
    m3 = (mx == b) & (d > 0)
    h[m3] = (r - g)[m3] / d[m3] + 4
    h = h / 6
    h = (h + hue_shift / 360) % 1.0
    c = (1 - np.abs(2 * l - 1)) * sat
    x = c * (1 - np.abs((h * 6) % 2 - 1))
    mm = l - c / 2
    z = np.zeros_like(h)
    conds = [(h < 1 / 6), (h < 1 / 3), (h < 1 / 2), (h < 2 / 3), (h < 5 / 6)]
    r2 = np.select(conds, [c, x, z, z, x])
    g2 = np.select(conds, [x, c, c, x, z])
    b2 = np.select(conds, [z, x, x, c, c])
    out = np.stack([((r2 + mm) * 255).clip(0, 255),
                    ((g2 + mm) * 255).clip(0, 255),
                    ((b2 + mm) * 255).clip(0, 255), al], axis=-1).astype(np.uint8)
    return Image.fromarray(out, 'RGBA')


def medallion(variant_img, size=290):
    cell = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(cell)
    d.ellipse([10, 10, size - 10, size - 10], fill=(248, 249, 252))
    inner = size - 70
    sw, sh2 = variant_img.size
    side = min(sw, sh2)
    sq = variant_img.crop(((sw - side) // 2, (sh2 - side) // 2,
                           (sw + side) // 2, (sh2 + side) // 2)).resize((inner, inner), Image.LANCZOS)
    pm = Image.new('L', (inner, inner), 0)
    ImageDraw.Draw(pm).rounded_rectangle([0, 0, inner - 1, inner - 1], radius=40, fill=255)
    cell.paste(sq, (int((size - inner) / 2), 10 + int((size - inner) / 2)), pm)
    d.ellipse([10, 10, size - 10, size - 10], outline=(240, 200, 126, 200), width=4)
    return cell


img = Image.new('RGBA', (1080, 1250))
bgd = ImageDraw.Draw(img)
for y in range(1250):
    t = y / 1250
    bgd.line([(0, y), (1080, y)], fill=(int(14 + 6 * t), int(18 + 10 * t), int(30 + 16 * t), 255))

d = ImageDraw.Draw(img)
d.text((50, 40), "精灵图鉴 · 收集", font=f_title, fill=(255, 255, 255, 255))
d.text((50, 100), "同一只精灵随里程碑换装 —— 全部离线解锁", font=f_sub, fill=(160, 175, 205, 255))

variants = [
    (0, "雷霆之王", "默认形态", True),
    (40, "星夜之魂", "累计听剧 10 小时", False),
    (110, "极光之灵", "累计听剧 50 小时", False),
    (190, "樱雨精灵", "连续听剧 7 天", False),
    (200, "熔岩暴君", "累计省电 5000 mAh", False),
    (290, "翡翠雷公", "连续听剧 30 天", False),
]
for i, (hue, name, cond, owned) in enumerate(variants):
    col, row = i % 3, i // 3
    x, y = 40 + col * 340, 160 + row * 430
    v = recolor(pet, hue) if hue else pet
    m = medallion(v, 290)
    if not owned:
        m = m.point(lambda p: int(p * 0.45))
        dim = Image.new('RGBA', m.size, (0, 0, 0, 0))
        dd = ImageDraw.Draw(dim)
        dd.ellipse([110, 110, 180, 180], fill=(30, 36, 50, 230))
        m.alpha_composite(dim)
    img.alpha_composite(m, (x, y))
    d = ImageDraw.Draw(img)
    d.text((x + 20, y + 310), ("✅ " if owned else "🔒 ") + name, font=f_name,
           fill=(255, 255, 255, 255) if owned else (130, 140, 160, 255))
    d.text((x + 20, y + 352), cond, font=f_cond, fill=(160, 175, 205, 255))

d.rounded_rectangle([740, 155, 1040, 262], radius=28, fill=(20, 34, 58, 255),
                    outline=(240, 200, 126, 150), width=3)
d.text((768, 172), "🔥 连续听剧", font=f_sub, fill=(255, 255, 255, 255))
d.text((768, 208), "7 天", font=f_big, fill=(240, 200, 126, 255))

out = os.path.join(ASSET, 'pet_collection_mock.png')
img.convert('RGB').save(out)
print('OK', img.size, os.path.getsize(out), 'bytes')
