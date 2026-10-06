#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 png2vector.py 追踪出的 SVG 转成 Android VectorDrawable。"""
import sys, re

HDR = '''<?xml version="1.0" encoding="utf-8"?>
<!--
  由 tools/png2vector.py 从 {src} 追踪生成（轮廓跟随 + 道格拉斯-普克简化）。
  不要手工编辑：改动请重新追踪，否则会与原始设计脱节。
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="432"
    android:viewportHeight="432">
'''


def main(svg, out_xml, src_name):
    txt = open(svg, encoding="utf-8").read()
    paths = re.findall(r'<path fill="(#[0-9A-Fa-f]{6})" d="(.*?)"/>', txt, re.S)
    if not paths:
        raise SystemExit("SVG 里没有 path")
    body = HDR.format(src=src_name)
    for color, d in paths:
        d = " ".join(d.split())
        body += f'    <path android:fillColor="{color}" android:pathData="{d}" />\n'
    body += "</vector>\n"
    with open(out_xml, "w", encoding="utf-8", newline="\n") as f:
        f.write(body)
    npts = sum(len(re.findall(r"L", d)) for _, d in paths)
    print(f"written: {out_xml}  ({len(paths)} 条路径, 约 {npts} 个控制点, "
          f"{len(body.encode('utf-8'))} 字节)")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])