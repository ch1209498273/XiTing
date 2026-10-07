# -*- coding: utf-8 -*-
"""
语言自称名。七种语言内容完全相同——语言就是它自己的名字，
任何翻译都是错的，所以不做本地化，直接给所有语言同一份。

放最后一个文件（文件名排序在 long_* 之后）以确保它覆盖其他文件里的同名 key。
"""

LANGS = ["de", "en", "es", "fr", "ja", "ko", "ru"]

COMMON = {
    "lang_name_zh": "简体中文",
    "lang_name_en": "English",
    "lang_name_ja": "日本語",
    "lang_name_ko": "한국어",
    "lang_name_fr": "Français",
    "lang_name_de": "Deutsch",
    "lang_name_es": "Español",
    "lang_name_ru": "Русский",
}
