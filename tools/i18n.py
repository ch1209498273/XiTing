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
# 真正的格式占位符：%1$s、%2$.0f、%d、%s、%% —— 这些不能转义。
# 不带结尾的 $ 锚：用 re.match(s, i) 从任意位置起匹配「最长的合法 token」，
# 匹配多长取多长，而不是先切一段再判断。
FORMAT_TOKEN = re.compile(r'%%|%\d+\$[-\d.]*[a-zA-Z]|%[-#+0,(]*\d*(?:\.\d+)?[a-zA-Z]')
# TOKEN_RE 在 FORMAT_TOKEN 之后**多一个「单个 % 」分支**：
# 一次扫描就把合法的 token 与裸 % 都吃掉，回调拿到的是完整的一段，
# 不存在「第二个 % 又被当新起点」的重叠问题。
TOKEN_RE = re.compile(
    r'%%|%\d+\$[-\d.]*[a-zA-Z]|%[-#+0,(]*\d*(?:\.\d+)?[a-zA-Z]|%')


def _is_legal_token(t):
    return FORMAT_TOKEN.fullmatch(t) is not None


def parse(path):
    """→ [(key, value)]，保持文件里的原始顺序"""
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return STRING_RE.findall(f.read())


def _token_at(s, i):
    """从位置 i（指向一个 %）取出**最长的合法格式 token**。

    ⚠ 不能「切到空格为止」再整体匹配：像「比上周多了 %1$d%%（%2$s）」这种
    中日韩文案里占位符后面紧跟全角标点、整串没有空格，切出来的是
    「%1$d%%（%2$s）」，正则匹配不上 → 被当成裸 % 而整个转义成
    「%%1$d%%%」，运行时 getString 直接抛异常。

    所以改为：直接从 i 处正则匹配一个 token，匹配多长就取多长。
    """
    m = FORMAT_TOKEN.match(s, i)
    return m.group(0) if m else ""


def esc(v):
    """Android 资源里的转义。

    `&` `<` `>` `'` 都要处理，撇号不转义 aapt 会直接报错。

    ⚠ **`%` 也要转义，而且这条最容易踩**：只要一条 string 里含裸 `%`，
    aapt 就会把它当格式占位符解析。法语「100 % hors ligne」里的 `% `
    会被判成「转换字符 h 缺少参数」——这是 **error**（lint StringFormatInvalid），
    而 release 构建不跑 lint，所以**编译能过、装到手机上才炸**。

    真占位符（%1$s、%d、%2$.0f、%%）不能动，只转义那些不属于格式串的裸 `%`。
    """
    def pct(m):
        return m.group(0) if _is_legal_token(m.group(0)) else "%%"

    # ⚠ 三个关键点，全部是踩出来的：
    # 1. **必须用 TOKEN_RE 一次吃掉整个 token**，不能对每个 % 单独跑
    #    re.sub('%', ...)：「%1$d%%」里第二个 % 会被当成新起点再转义一次，
    #    结果写出「%1$d%%%」——aapt 与运行时都对不上。
    # 2. **必须在最原始的字符串上跑**。先做 & < > ' 的替换会改变长度，
    #    之后再按原索引定位就全错位了。
    # 3. str.replace 的第二个参数只能是字符串，不接受函数（那是 re.sub 的 API）。
    return TOKEN_RE.sub(pct, v).replace("&", "&amp;") \
                               .replace("<", "&lt;") \
                               .replace(">", "&gt;") \
                               .replace("'", "\\'")


def unesc(v):
    return (v.replace("\\'", "'")
             .replace("%%", "%")
             .replace("&lt;", "<")
             .replace("&gt;", ">")
             .replace("&amp;", "&"))


# 词典缓存：targets() 与 sync 都要用，不缓存会重复 exec 一遍所有文件
_DICTS = {}


def load_dicts():
    """tools/i18n/*.py 里的 dict：人工维护的译文。

    文件名约定：`<lang>.py`（短文案）、`long_<lang>.py`（长段落）、
    `code_<lang>.py`（从 Kotlin 代码搬来的文案）。三个前缀都表示
    「去掉前缀后的部分就是语言码」，所以一个文件里只放一种语言。
    文件里可以用 `LANGS = [...]` 声明一份词典跨哪些语言共用——
    语言名与译文无关的内容（如「语言自称名」）就靠这个。
    按**文件名排序**依次 merge，靠后的覆盖靠前的。
    """
    d = os.path.join(os.path.dirname(os.path.abspath(__file__)), "i18n")
    if not os.path.isdir(d):
        return {}
    out = {}
    for f in sorted(glob.glob(os.path.join(d, "*.py"))):
        base = os.path.basename(f)[:-3]
        default_lang = base.split("_", 1)[1] if "_" in base else base
        ns = {}
        try:
            with open(f, encoding="utf-8") as fh:
                exec(compile(fh.read(), f, "exec"), ns)
        except Exception as e:
            print("⚠ 读取 %s 失败: %s" % (f, e))
            continue
        langs = ns.get("LANGS") or [default_lang]
        dicts = [
            (k, v) for k, v in ns.items()
            if k.isupper() and k != "LANGS" and isinstance(v, dict)
        ]
        # 防呆 1：一份文件里多个 dict + LANGS 指定多语言 = 它们会互相覆盖，
        # 加载顺序决定谁赢。这种错误很隐蔽（所有语言被写成同一种），
        # 而且往往是因为「顺手把两种语言放一个文件」造成的。
        if len(langs) > 1 and len(dicts) > 1:
            print("⚠ 词典 %-16s 声明了多语言 %s，文件里却有 %d 个 dict（%s）："
                  % (base, langs, len(dicts), ", ".join(k for k, _ in dicts)))
            print("     它们会互相覆盖，结果所有语言都变成最后一条的内容。")
            print("     若确实要共用，请合并成一个 dict；否则去掉 LANGS。")
        # 防呆 2：单语言文件里出现多个 dict，同样可疑 ——
        # 实际发生过：code_en.py 里同时放了 EN 和 JA，结果日语灌进了英语。
        if len(langs) == 1 and len(dicts) > 1:
            print("⚠ 词典 %-16s 属于语言「%s」，却含 %d 个 dict（%s）。"
                  % (base, langs[0], len(dicts), ", ".join(k for k, _ in dicts)))
            print("     全部都会归到「%s」，靠后的覆盖靠前的。请拆成每个语言一个文件。"
                  % langs[0])
        n = 0
        for k, v in dicts:
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
    print()
    bad += check_placeholders(os.path.join(SRC, "strings.xml"), None)
    if bad:
        print("⚠ 有 %d 处问题（占位符不一致会在运行时崩溃）" % bad)
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


def check_placeholders(src_path, lang_path):
    """比较各语言与源文的占位符集合。

    这是**运行时会崩**的那类错：某语言把 %1$s 写成了 %%1$s（或反之），
    aapt 不会报错，但 getString(id, args) 运行时抛
    IllegalFormatException / MissingFormatArgumentException。
    lint 能报一部分，但手改文件时很容易踩。
    """
    src = dict(parse(src_path))
    bad = 0
    for lang, path in targets():
        cur = dict(parse(path))
        for k, v in cur.items():
            if k not in src:
                continue
            # 日期/数字格式串（SimpleDateFormat 模式）各语言顺序本来就不同
            # （M月d日 vs 1.2.），它们不作为 getString 参数传入，不参与比对。
            if k.endswith("_date_fmt"):
                continue
            a = set(FMT.findall(unesc(src[k]).replace('%%', '\x00')))
            b = set(FMT.findall(unesc(v).replace('%%', '\x00')))
            if a != b:
                print("   ✗ %-7s %-20s 源=%s 本地=%s" % (lang, k, sorted(a), sorted(b)))
                bad += 1
    return bad


# 格式占位符：%1$s / %2$.0f / %d
# ⚠ 中间**不能有空格**。Java 的 Formatter 允许「% 1$s」这种写法，但 aapt 不允许，
# 而且它会把「100 % hors ligne」里的「% h」误当成占位符（实际上那是个语法错误，
# 运行时会抛 UnknownFormatConversionException）。
FMT = re.compile(r'%(?!%)(?:\d+\$)?[-#+0,(]*\d*(?:\.\d+)?[a-zA-Z]')


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "check"
    fn = {"sync": cmd_sync, "check": cmd_check, "table": cmd_table}.get(cmd)
    if not fn:
        print(__doc__)
        sys.exit(2)
    sys.exit(fn())
