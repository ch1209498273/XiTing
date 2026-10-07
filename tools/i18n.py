#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
多语言同步工具。

## 为什么需要它

本项目不用 AppCompat（零依赖），所以每种语言就是一份独立的
`res/values-XX/strings.xml`，靠 AGP 自动挑对应目录。这带来一个必然的麻烦：

> 新增一条中文文案后，必须同步更新**每一个**语言文件，否则漏掉的那个
> 语言会悄悄回落成中文——不报错、不崩溃，只是界面上有一处没翻译。

手工维护的代价：zh 有 N 条，就有 N × (语言数-1) 处要改。漏一处就是 bug，
而且只有切到那个语言才看得见。

## 三条命令

    python tools/i18n.py sync     # 把 zh 新增的 key 同步到所有语言，保留已有译文
    python tools/i18n.py check     # 报告覆盖率，有未译完则退出码 1（可挂 CI）
    python tools/i18n.py table     # 覆盖率表格

## sync 的关键性质：**只增不覆盖**

已存在的译文一个字节都不动。新的 key 会填成 `⟨TODO⟨中文原文⟩⟩`，
这样即使忘了翻，切到那个语言看到的也是「这是一个待翻译的条目 + 原文」，
而不是沉默地混入中文。`check` 会把这些条目报出来。

这也是为什么可以放心反复跑 sync：它是幂等的。
"""
import os
import re
import sys
import glob
import xml.etree.ElementTree as ET

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
SRC = os.path.join(RES, "values")

# 语言显示名（语言选择对话框用）
LANG_NAMES = {
    "en": "English",
    "ja": "日本語",
    "ko": "한국어",
    "zh-rTW": "繁體中文",
    "fr": "Français",
    "de": "Deutsch",
    "es": "Español",
    "ru": "Русский",
    "ar": "العربية",
}

STRING_RE = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
TODO_RE = re.compile(r'^⟨TODO⟨.*⟩⟩$', re.S)


def parse(path):
    """→ [(key, value)]，保持文件里的原始顺序"""
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return STRING_RE.findall(f.read())


def esc(v):
    """Android 资源里的转义：& < > ' 都要处理，撇号不转义 aapt 会直接报错"""
    return (v.replace("&", "&amp;")
             .replace("<", "&lt;")
             .replace(">", "&gt;")
             .replace("'", "\\'"))


def unesc(v):
    return (v.replace("\\'", "'")
             .replace("&lt;", "<")
             .replace("&gt;", ">")
             .replace("&amp;", "&"))


# 词典缓存：targets() 与 sync 都要用，不缓存会重复 exec 一遍所有文件
_DICTS = {}


def load_dicts():
    """tools/i18n/*.py 里的 dict：人工维护的译文。

    文件名约定：`<lang>.py`（单个语言）或 `long_<lang>.py`（同语言的长段落）。
    文件里可以用 `LANGS = ["de", "en", ...]` 声明这份词典适用于哪些语言——
    语言名与译文无关的内容（如「语言自称名」）就靠这个跨语言共用一份。
    按**文件名排序**依次 merge，靠后的覆盖靠前的。
    """
    d = os.path.join(os.path.dirname(os.path.abspath(__file__)), "i18n")
    if not os.path.isdir(d):
        return {}
    out = {}
    for f in sorted(glob.glob(os.path.join(d, "*.py"))):
        base = os.path.basename(f)[:-3]
        default_lang = base[5:] if base.startswith("long_") else base
        ns = {}
        try:
            with open(f, encoding="utf-8") as fh:
                exec(compile(fh.read(), f, "exec"), ns)
        except Exception as e:
            print("⚠ 读取 %s 失败: %s" % (f, e))
            continue
        langs = ns.get("LANGS") or [default_lang]
        n = 0
        for k, v in ns.items():
            if k.isupper() and k != "LANGS" and isinstance(v, dict):
                for lg in langs:
                    out.setdefault(lg, {}).update(v)
                n += len(v)
        print("  词典 %-16s -> %-18s %3d 条" % (base, ",".join(langs), n))
    _DICTS.clear()
    _DICTS.update(out)
    return out


def targets(include_missing=False):
    """所有非 values（默认）目录。include_missing=True 时，把有词典但还没建
    strings.xml 的语言也算进来（首次 sync 时用来创建）。"""
    out = []
    have = set()
    for d in sorted(glob.glob(os.path.join(RES, "values-*"))):
        p = os.path.join(d, "strings.xml")
        if os.path.exists(p):
            lang = os.path.basename(d).replace("values-", "")
            have.add(lang)
            out.append((lang, p))
    if include_missing:
        for lang in sorted(_DICTS):
            if lang not in have:
                d = os.path.join(RES, "values-%s" % lang)
                out.append((lang, os.path.join(d, "strings.xml")))
    return out


def write_lang(path, pairs, lang):
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- %s。由 tools/i18n.py sync 从 values/strings.xml 同步，请勿手改。 -->" % LANG_NAMES.get(lang, lang),
        "<resources>",
    ]
    for k, v in pairs:
        lines.append('    <string name="%s">%s</string>' % (k, esc(v)))
    lines += ["</resources>", ""]
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))


def cmd_sync():
    src = parse(os.path.join(SRC, "strings.xml"))
    if not src:
        print("✗ 读不到 values/strings.xml")
        return 1
    src_map = dict(src)
    print("加载译文词典：")
    dicts = load_dicts()
    added_total = 0

    for lang, path in targets(include_missing=True):
        cur = dict(parse(path))
        man = dicts.get(lang, {})
        pairs, added, kept, filled = [], 0, 0, 0
        for k, zh in src:
            have = unesc(cur.get(k, "")).strip() if k in cur else ""
            # 之前 sync 填进去的 ⟨TODO⟨…⟩⟩ 不算译文，必须能被词典覆盖重填
            if have and not TODO_RE.match(have):
                pairs.append((k, have))
                kept += 1
            elif k in man:
                pairs.append((k, man[k]))
                filled += 1
            else:
                pairs.append((k, "⟨TODO⟨%s⟩⟩" % zh))
                added += 1
        dropped = [k for k in cur if k not in src_map]
        write_lang(path, pairs, lang)
        added_total += added
        msg = "%-8s 保留 %3d  词典补 %3d  待译 %3d" % (lang, kept, filled, added)
        if dropped:
            msg += "  丢弃 %d" % len(dropped)
        print(msg)
    print("\n共 %d 条源文案，%d 种语言。" % (len(src), len(targets(include_missing=True))))
    if added_total:
        print("⚠ 有 %d 处待翻译（sync 不会覆盖已有译文）" % added_total)
    return 0


def cmd_check():
    src = dict(parse(os.path.join(SRC, "strings.xml")))
    bad = 0
    for lang, path in targets():
        cur = dict(parse(path))
        missing = [k for k in src if k not in cur]
        todo = [k for k, v in cur.items() if TODO_RE.match(unesc(v).strip())]
        stale = [k for k in cur if k not in src]
        n = len(src) - len(todo) - len(missing)
        pct = 100.0 * n / len(src) if src else 0
        flag = "✓" if not (missing or todo or stale) else "✗"
        print("%s %-8s %5.1f%%  (%d/%d)" % (flag, lang, pct, n, len(src)))
        for k in (missing + todo + stale)[:8]:
            print("      - %s" % k)
        if len(missing + todo + stale) > 8:
            print("      ... 还有 %d 条" % (len(missing + todo + stale) - 8))
        if missing or todo or stale:
            bad = 1
    return bad


def cmd_table():
    src = dict(parse(os.path.join(SRC, "strings.xml")))
    print("语言      覆盖   已译   待译")
    print("-" * 34)
    for lang, path in targets(include_missing=True):
        cur = dict(parse(path))
        todo = sum(1 for v in cur.values() if TODO_RE.match(unesc(v).strip()))
        done = len([k for k in src if k in cur]) - todo
        need = len(src) - done
        print("%-8s %5.1f%%  %4d  %4d" % (
            lang, 100.0 * done / len(src) if src else 0, done, need))


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "check"
    fn = {"sync": cmd_sync, "check": cmd_check, "table": cmd_table}.get(cmd)
    if not fn:
        print(__doc__)
        sys.exit(2)
    sys.exit(fn())
