# -*- coding: utf-8 -*-
"""activity_about.xml 文案 → @string（精确替换，报告遗漏）"""
import io

F = r'D:/AI任务/zcode/息屏听剧/app/src/main/res/layout/activity_about.xml'
PAIRS = [
    ('android:text="关于息屏听剧"', 'android:text="@string/about_title"'),
    ('android:text="关于这个App"', 'android:text="@string/about_section_app"'),
    ('android:text="隐私与安全"', 'android:text="@string/about_section_privacy"'),
    ('android:text="开源与联系"', 'android:text="@string/about_section_open"'),
    ('android:text="许可"', 'android:text="@string/about_section_license"'),
    ('android:text="完全离线 · 不收集任何数据"', 'android:text="@string/footer_offline"'),
]
# 长文案单独精确替换（含转义）
LONGS = [
    ('看剧时想闭眼听？点一下悬浮球，屏幕全黑：背光物理关闭、触摸屏蔽，声音照常播放——任意视频App可用。\\n\\n可选暗色夜钟与电量显示（重锁后保持最暗亮度持续可见），内容周期性微移保护OLED屏幕；来电自动退出黑幕，不会漏接电话；支持定时关闭，睡前听剧到点自动停。\\n\\n黑幕唤醒后可切集：上一集 / 播放暂停 / 下一集（可在设置中开启）。\\n\\n电能精灵：听剧积累成长值，养成五形态精灵（电火花 → 电球 → 雷云精灵 → 风暴之灵 → 雷霆之王），可作悬浮球头像，满级后继续储备；成就系统记录你的听剧里程碑。\\n\\n桌面小部件：三档尺寸，精灵形象、听剧数据与一键息屏随身可见；悬浮球支持长按快速退出。\\n\\n数据备份：自动备份至下载目录 XiTing 文件夹（仅本机），卸载重装后可一键恢复。所有数据仅保存在本机，无网络权限。',
     '@string/about_intro'),
    ('· 无 INTERNET 权限：物理上无法联网\\n· 不读取任何屏幕内容\\n· 所有数据仅保存在本机\\n· 无广告、无追踪、无埋点',
     '@string/about_privacy'),
    ('GitHub：github.com/ch1209498273/XiTing\\n（点按打开项目主页）',
     '@string/about_github'),
    ('问题反馈：1209498273@qq.com',
     '@string/about_feedback'),
    ('非商业许可（详见LICENSE）：个人学习交流可自由使用与分享（需保留署名）；任何商业用途均被禁止；禁止移除或篡改署名与溯源标识。\\n\\n本项目与 YouTube、Google 及任何视频平台无关联。仅供学习交流，请支持正版。',
     '@string/about_license'),
]
src = io.open(F, encoding='utf-8').read()
missed = []
for old, new in PAIRS + LONGS:
    if old in src:
        src = src.replace(old, new)
    else:
        missed.append(old[:50])
io.open(F, 'w', encoding='utf-8').write(src)
print('missed:', len(missed))
for x in missed: print('  -', x)
import re
left = re.findall(r'android:text="([^@][^"]*)"', src)
print('剩余硬编码:', left)
