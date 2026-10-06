#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
息屏听剧 · 溯源工具
用法：python trace.py <apk路径>
作用：读取 APK 里的隐藏水印（.trace 资产、ZIP注释、构建ID）与签名指纹，
     用于确认「这个包是从哪个渠道/哪次构建流出去的」。
"""
import sys, os, zipfile, hashlib, json, subprocess, shutil

def find_apksigner():
    """定位 build-tools 里的 apksigner（v2签名APK用它读证书）"""
    import glob
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")         or os.path.join(os.path.expanduser("~"), "AppData", "Local", "Android", "Sdk")
    cands = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "apksigner.bat")), reverse=True)
    return cands[0] if cands else None

def cert_info(apk):
    """读取 APK 签名证书指纹（标识打包者；debug签名=keytool读不到，用apksigner）"""
    a = find_apksigner()
    if not a:
        return "(未找到apksigner，跳过证书指纹)"
    try:
        out = subprocess.run([a, "verify", "--print-certs", apk],
                             capture_output=True, text=True,
                             encoding="utf-8", errors="ignore", timeout=60)
        for line in (out.stdout + out.stderr).splitlines():
            if "SHA-256 digest" in line:
                return "签名SHA-256: " + line.split(":", 1)[1].strip()[:70] + "..."
        return "(未解析到证书)"
    except Exception as e:
        return f"(证书解析失败: {e})"

def main():
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(1)
    apk = sys.argv[1]
    if not os.path.exists(apk):
        print("文件不存在:", apk); sys.exit(1)

    data = open(apk, "rb").read()
    print("=" * 46)
    print("XiTing 溯源报告")
    print("=" * 46)
    print("文件名  :", os.path.basename(apk))
    print("大小    :", f"{len(data)/1024:.0f} KB")
    print("SHA-256 :", hashlib.sha256(data).hexdigest())

    z = zipfile.ZipFile(apk)
    comment = z.comment.decode("utf-8", "ignore").strip()
    print("ZIP注释 :", comment if comment else "(无)")

    marker = None
    for name in ("assets/trace.json", "trace.json"):
        try:
            marker = z.read(name).decode("utf-8")
            break
        except KeyError:
            continue
    if marker:
        print("溯源水印:")
        try:
            for k, v in json.loads(marker).items():
                print(f"    {k:10s}: {v}")
        except Exception:
            print("   ", marker)
    else:
        print("溯源水印: (未找到 —— 可能被篡改/重打包，或不是本项目的包)")

    print("打包者  :", cert_info(apk))
    print("=" * 46)
    print("提示：对照 watermark-ledger.json 里的 build_id 即可定位流出渠道。")

if __name__ == "__main__":
    main()
