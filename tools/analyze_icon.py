#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
分析 launcher 图标的 alpha 掩膜：
  · 输出非透明像素的精确包围盒（用于 1:1 还原矢量的坐标）
  · 把纯白剪影合成到深色底上，便于肉眼比对形状
只读脚本，不修改任何资源。
"""
import sys, zlib, struct


def read_png(path):
    """最小 PNG 读取：只处理 8bit RGBA（本项目的图标都是）。"""
    with open(path, "rb") as f:
        data = f.read()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not png"
    pos = 8
    w = h = bitdepth = colortype = None
    idat = b""
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        typ = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + ln]
        if typ == b"IHDR":
            w, h, bitdepth, colortype = struct.unpack(">IIBB", body[:10])
        elif typ == b"IDAT":
            idat += body
        elif typ == b"IEND":
            break
        pos += 12 + ln
    raw = zlib.decompress(idat)
    # colortype 6 = RGBA, colortype 2 = RGB（本项目的前景图是把背景色烤进去的无 alpha 图）
    if colortype == 6:
        bpp = 4
    elif colortype == 2:
        bpp = 3
    else:
        raise AssertionError(f"unsupported ctype={colortype} depth={bitdepth}")
    stride = w * bpp
    out = bytearray(w * h * 4)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        ft = raw[p]; p += 1
        line = bytearray(raw[p:p + stride]); p += stride
        # ⚠️ 滤波回溯的「左邻」步长是 **bpp**，不是 4。RGB（bpp=3）时硬编码 4 会解出
        # 一张颜色全乱的图（而 RGBA 恰好蒙对，所以只有 RGB 图会出错 —— 很难发现）。
        if ft == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif ft == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ft == 3:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif ft == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line

    if bpp == 4:
        return w, h, out

    # RGB -> RGBA（补满不透明 alpha）
    rgba = bytearray(w * h * 4)
    for i in range(w * h):
        rgba[i * 4] = out[i * 3]
        rgba[i * 4 + 1] = out[i * 3 + 1]
        rgba[i * 4 + 2] = out[i * 3 + 2]
        rgba[i * 4 + 3] = 255
    return w, h, rgba


def main(path):
    w, h, px = read_png(path)
    minx, miny, maxx, maxy = w, h, -1, -1
    cols = {}
    for y in range(h):
        base = y * w * 4
        for x in range(w):
            o = base + x * 4
            a = px[o + 3]
            if a > 16:
                if x < minx: minx = x
                if x > maxx: maxx = x
                if y < miny: miny = y
                if y > maxy: maxy = y
                key = (px[o], px[o + 1], px[o + 2])
                cols[key] = cols.get(key, 0) + 1
    print(f"文件: {path}")
    print(f"尺寸: {w} x {h}")
    print(f"不透明像素包围盒: x [{minx}..{maxx}]  y [{miny}..{maxy}]"
          f"   ({maxx-minx+1} x {maxy-miny+1})")
    top = sorted(cols.items(), key=lambda kv: -kv[1])[:4]
    print("主要颜色:", ", ".join(f"#{r:02X}{g:02X}{b:02X}x{hex(c)}" for (r, g, b), c in top))

    # 逐行扫描顶边，找轮廓形状（用于还原耳罩弧线）
    print("\n顶边轮廓 (每 4% 高度采样的最左/最右不透明 x)：")
    for k in range(0, 25):
        y = miny + int((maxy - miny) * k / 24)
        base = y * w * 4
        xs = [x for x in range(w) if px[base + x * 4 + 3] > 128]
        if xs:
            print(f"  y={y:4d} ({(y-miny)/(maxy-miny)*100:5.1f}%): x {min(xs):4d}..{max(xs):4d}")
        else:
            print(f"  y={y:4d}: (空)")


if __name__ == "__main__":
    main(sys.argv[1])