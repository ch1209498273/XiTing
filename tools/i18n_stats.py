# -*- coding: utf-8 -*-
"""page_stats.xml 硬编码文案 → @string 引用（精确旧→新替换，全部唯一）"""
import io

F = r'D:/AI任务/zcode/息屏听剧/app/src/main/res/layout/page_stats.xml'
PAIRS = [
    ('android:text="电能精灵 · 由你的真实听剧时长喂养"', 'android:text="@string/pet_subtitle"'),
    ('android:text="息屏听剧的时长与省电估算（保存在本机）"', 'android:text="@string/stats_hint"'),
    ('android:text="今日"', 'android:text="@string/label_today"'),
    ('android:text="近7天"', 'android:text="@string/label_week"'),
    ('android:text="累计"', 'android:text="@string/label_total"'),
    ('android:text="累计估算省电 ≈ 0 mAh"', 'android:text="@string/sum_mah_default"'),
    ('android:text="最长单次 — · 共 0 次息屏"', 'android:text="@string/sum_extra_default"'),
    ('android:text="精灵成就 · 0/8"', 'android:text="@string/ach_progress"'),
    ('android:text="听剧积累，逐一点亮"', 'android:text="@string/ach_sub_empty"'),
    ('android:text="近7天每日息屏时长"', 'android:text="@string/chart_title"'),
    ('android:text="最近会话记录 · 仅保留最近30天"', 'android:text="@string/recent_records"'),
    ('android:text="‹ 上一页"', 'android:text="@string/pager_prev"'),
    ('android:text="下一页 ›"', 'android:text="@string/pager_next"'),
]
src = io.open(F, encoding='utf-8').read()
missed = []
for old, new in PAIRS:
    if old in src:
        src = src.replace(old, new)
    else:
        missed.append(old)
# 动态默认值 "0分钟"/"0 次" 保留（代码会覆盖），"第 1 / 1 页" 为含占位数字的动态默认，保留
io.open(F, 'w', encoding='utf-8').write(src)
print('missed:', missed if missed else '无')
import re
left = re.findall(r'android:text="([^@][^"]*)"', src)
print('剩余硬编码:', left)
