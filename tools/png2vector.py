#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PNG 轮廓追踪器：把 launcher 图标直接转成 VectorDrawable 的 pathData。

为什么不用手工重画：桌面图标天天可见，手绘偏几个像素就是永久瑕疵。
这里从 alpha 掩膜跑边界跟随 + 道格拉斯-普克简化，输出的路径与原图像素级一致。

按颜色分层：单色图标输出 1 条路径；前景图按主色分层各出一条。
"""
import sys, os, zlib, struct

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from analyze_icon import read_png  # noqa: E402


def trace_contours(mask, w, h, tol=0.6, min_area=12):
    """Moore 邻域边界跟随，返回一圈坐标点（顺时针，去重闭合）。"""
    def at(x, y):
        return 0 <= x < w and 0 <= y < h and mask[y * w + x]

    visited = bytearray(w * h)
    contours = []
    nb = [(-1, 0), (-1, -1), (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1)]

    for sy in range(h):
        for sx in range(w):
            if not at(sx, sy) or visited[sy * w + sx]:
                continue
            # 从该点开始走边界
            start = (sx, sy)
            pts = [start]
            cx, cy = sx, sy
            bdir = 6  # 上一个来的方向，初始朝下
            for _ in range(w * h * 4):
                found = False
                for k in range(8):
                    d = (bdir + 5 + k) % 8
                    nx, ny = cx + nb[d][0], cy + nb[d][1]
                    if at(nx, ny):
                        cx, cy = nx, ny
                        bdir = d
                        if not (cx == start[0] and cy == start[1]):
                            pts.append((cx, cy))
                        found = True
                        break
                if not found:
                    break
                if cx == start[0] and cy == start[1] and len(pts) > 2:
                    break
            for (x, y) in pts:
                visited[y * w + x] = 1
            if len(pts) >= 4:
                a = abs(sum(pts[i][0] * pts[(i + 1) % len(pts)][1] - pts[(i + 1) % len(pts)][0] * pts[i][1]
                            for i in range(len(pts)))) / 2.0
                if a >= min_area:
                    contours.append((a, pts))
    contours.sort(key=lambda t: -t[0])
    return [p for _, p in contours]


def rdp(pts, tol):
    """道格拉斯-普克折线简化（闭合轮廓，固定起点后开放处理）。"""
    if len(pts) < 3:
        return pts
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        a, b = stack.pop()
        if b <= a + 1:
            continue
        ax, ay = pts[a]
        bx, by = pts[b]
        dx, dy = bx - ax, by - ay
        den = (dx * dx + dy * dy) ** 0.5
        best, bi = -1.0, -1
        for i in range(a + 1, b):
            x, y = pts[i]
            d = abs(dy * x - dx * y + bx * ay - by * ax) / (den if den else 1.0)
            if d > best:
                best, bi = d, i
        if best > tol:
            keep[bi] = True
            stack.append((a, bi))
            stack.append((bi, b))
    return [p for p, k in zip(pts, keep) if k]


def path_data(pts, prec=1):
    p = [(round(x, prec), round(y, prec)) for x, y in pts]
    p = [p[0]] + [q for i, q in enumerate(p[1:], 1) if q != p[i - 1]]
    d = "M" + "L".join(f"{x},{y}" for x, y in p)
    return d + "Z"


# 图层定义：用谓词而不是颜色聚类。
# 图标带渐变 + 抗锯齿 + 背景抖动（实测 2546 种颜色），按颜色分桶会得到
# #44FC14 这种垃圾色；改用「亮度阈值 + 色调判据」才稳定。
def layers_foreground():
    return [
        ("#FFFFFF", lambda r, g, b: (r + g + b) / 3.0 > 90, "近白：耳机主体"),
        ("#E3B75A", lambda r, g, b: r > 140 and g > 110 and b < 150 and (r - b) > 45, "金色：弯月"),
    ]


def layers_monochrome():
    return [("#FFFFFF", lambda r, g, b: True, "单色剪影")]


def main(png, out_svg, scale=108.0, mode="foreground"):
    w, h, px = read_png(png)
    specs = layers_foreground() if mode == "foreground" else layers_monochrome()
    alpha = px[3::4] if px[3::4] and px[3] is not None else None

    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{scale:.0f}" height="{scale:.0f}" '
             f'viewBox="0 0 {w} {h}">']
    for hexc, pred, desc in specs:
        mask = bytearray(w * h)
        rr = int(hexc[1:3], 16); gg = int(hexc[3:5], 16); bb = int(hexc[5:7], 16)
        for i in range(w * h):
            o = i * 4
            if px[o + 3] <= 128:
                continue
            if pred(px[o], px[o + 1], px[o + 2]):
                mask[i] = 1
        n = sum(mask)
        parts.append(f'<path fill="{hexc}" d="')
        total = 0
        for pts in trace_contours(mask, w, h):
            simp = rdp(pts, 0.55)
            if len(simp) < 3:
                continue
            parts.append("  " + path_data(simp))
            total += len(simp)
        parts.append('"/>')
        print(f"  {hexc} ({desc}): {n} 像素, {total} 个控制点")
    parts.append("</svg>")
    with open(out_svg, "w", encoding="utf-8") as f:
        f.write("\n".join(parts))
    print(f"written: {out_svg}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], mode=(sys.argv[3] if len(sys.argv) > 3 else "foreground"))