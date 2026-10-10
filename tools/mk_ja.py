#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 values-ja/strings.xml。

为什么不手写：244 条文案，靠人手抄一遍键名必然出错（漏一个键 = 日语界面里
那一处回落成中文）。这里从 values/strings.xml 读出**有序键名**，
再按 key 取译文，缺一条就报错退出。

用法：
    python tools/mk_ja.py            # 检查缺译（不写文件）
    python tools/mk_ja.py --write    # 生成 values-ja/strings.xml
"""
import re
import sys
import os

# Windows 控制台默认 GBK，打印「✓」等符号会 UnicodeEncodeError。
# 与其让脚本因为一句提示语失败，不如直接切到 UTF-8 并降级错误处理。
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ZH = os.path.join(ROOT, "app", "src", "main", "res", "values", "strings.xml")
JA_OUT = os.path.join(ROOT, "app", "src", "main", "res", "values-ja", "strings.xml")

# key -> 日文译文。必须覆盖 zh 里的每一个 key。
JA = {
    "app_name": "画面オフ再生",
    "tile_label": "画面オフ再生",
    "nav_home": "ホーム",
    "nav_stats": "統計",
    "nav_settings": "設定",
    "footer_offline": "完全オフライン · データ収集なし",
    "title_stats": "省エネ統計",
    "title_settings": "設定",
    "status_running": "実行中",
    "status_idle": "停止中",
    "pill_on": "オン",
    "pill_off": "オンにする",
    "pill_battery_on": "登録済み",
    "pill_battery_off": "登録する",
    "pill_abnormal": "異常 · 修復",
    "hero_title_start": "画面オフ再生を開始",
    "hero_title_stop": "画面オフ再生 実行中",
    "hero_sub_start": "起動後は映像を見ながらバブルをタップするだけでOK",
    "hero_sub_stop": "映像中にバブルをタップすると画面が消え、音は再生し続けます · タップで停止",
    "timer_title": "自動終了",
    "keepalive_title": "自動終了防止",
    "keepalive_sub": "設定ガイド",
    "stats_section": "省エネ統計",
    "view_details": "詳細を見る ›",
    "label_today": "今日",
    "label_week": "直近7日",
    "label_total": "累計",
    "perm_section": "サービスと権限",
    "perm_overlay": "オーバーレイ権限",
    "perm_battery": "バッテリーの白リスト登録",
    "perm_notify": "通知権限",
    "stats_hint": "画面オフ再生の視聴時間と省エネの推定（端末内に保存）",
    "chart_title": "直近7日間の1日あたりの画面オフ時間",
    "ach_sub_empty": "視聴の積み重ねで，逐步点灯",
    "ach_sub_latest": "最新解禁：%1$s",
    "ach_progress": "精霊の実績",
    "pet_subtitle": "電能精霊 · あなたの実視聴時間で育成",
    "sum_mah_default": "累計推定節約 ≈ 0 mAh",
    "sum_extra_default": "最長1回 — · 画面オフ 0回",
    "timer_none": "未設定",
    "recent_records": "最近のセッション記録 · 最近30日分のみ保持",
    "pager_prev": "‹ 前へ",
    "pager_next": "次へ ›",
    "settings_personal": "パーソナライズ",
    "switch_direct": "画面を軽くタップで解除",
    "switch_info": "画面オフ中に時刻とバッテリーを表示",
    "switch_media": "画面オフ中に再生コントロールを表示",
    "switch_headset": "イヤホン抜去で自動的に動画へ戻る",
    "bubble_style": "バブル表示",
    "settings_data": "データ",
    "row_export": "データのバックアップを書き出す",
    "row_export_sub": "任意の場所に保存",
    "row_restore": "バックアップから復元",
    "row_restore_sub": "成長値と統計",
    "settings_about": "このアプリについて",
    "row_update": "更新を確認",
    "row_share": "友達に共有",
    "share_sub": "1日1回 · +5 成長値獲得予定",
    "row_about": "画面オフ再生について",
    "row_about_sub": "使い方 / プライバシー / オープンソース",
    "widget_black": "画面オフ",
    "widget_active": "実行中",
    "widget_idle": "停止中",
    "widget_today_fmt": "今日 %1$s",
    "widget_growth_next": "成長値 %1$d · 次の形態まで %2$d",
    "widget_growth_max": "成長値 %1$d · 最高到達",
    "black_hint": "画面オフ再生中 · 軽くタップするとロック解除",
    "black_return": "🔒 タップで動画へ戻る",
    "channel_name": "画面オフ再生アシスタント",
    "notif_title": "画面オフ再生アシスタント 実行中",
    "notif_text": "バブルをタップすると画面が消え音は再生し続けます%1$s",
    "notif_action_listen": "画面オフ再生",
    "notif_action_restore": "画面を戻す",
    "notif_action_stop": "アシスタントを終了",
    "exit_confirm_title": "画面オフ再生を終了しますか？",
    "exit_confirm_yes": "終了",
    "exit_notif_title": "画面オフ再生を終了しました",
    "exit_notif_text": "「元に戻す」でバブルと画面オフ機能を復帰できます",
    "exit_notif_undo": "元に戻す",
    "notif_action_bubble_hide": "バブルを隠す",
    "notif_action_bubble_show": "バブルを表示",
    "switch_bubble": "バブルを表示",
    "pet_spark": "電火花",
    "pet_ball": "電球",
    "pet_cloud": "雷雲精霊",
    "pet_storm": "嵐の精霊",
    "pet_king": "雷王",
    "perm_fix_hint": "システム画面で「オーバーレイ」をオフにしてから再度オンにすると修復します",
    "perm_need_overlay": "オーバーレイ権限を許可してください",
    "toast_overlay_granted": "オーバーレイ権限を許可しました",
    "toast_battery_ok": "すでにバッテリー最適化の対象外です",
    "toast_notify_granted": "通知権限を許可しました",
    "toast_assistant_stopped": "アシスタントを停止しました",
    "toast_assistant_started": "アシスタントを起動しました。映像中にバブルをタップしてください",
    "toast_call": "着信があるため画面オフを解除しました",
    "toast_headset": "イヤホンが外れたため動画に戻りました",
    "timer_item_episode": "🎧 この話まで聴く",
    "timer_15": "15分",
    "timer_30": "30分",
    "timer_60": "60分",
    "timer_cancel": "タイマーを解除",
    "toast_timer_set": "自動終了：%1$d分後",
    "toast_timer_episode": "この話の終了時に自動で画面を消します",
    "toast_timer_expired": "タイマーが終了しました",
    "toast_timer_done": "タイマー終了、画面オフを解除しました",
    "toast_timer_cancelled": "自動終了を解除しました",
    "toast_episode_done": "この話の終了後に自動で画面を消します（現在 %1$d分%2$d秒 後）",
    "timer_remain_fmt": "残り %1$d 分",
    "restore_title": "履歴データを復元",
    "restore_msg": "以前「画面オフ再生」を使用していて削除した場合：バックアップはダウンロードの XiTing フォルダ（XiTing-backup.json）にあります。ここから成長値と統計をワンタップで復元できます。\n\n初めて使う場合は「不要」をタップしてください。",
    "restore_pick": "バックアップファイルを選択",
    "restore_no": "不要",
    "toast_invalid_backup": "有効なバックアップファイルではありません",
    "restore_other_title": "バックアップは別の端末からのものです",
    "restore_other_msg": "このバックアップの成長値は %1$d です。端末ID が本機と一致しません（機種変更、または旧署名の版からの移行）。復元しますか？",
    "restore_ok_btn": "復元",
    "dlg_cancel": "キャンセル",
    "toast_restore_done": "データを復元しました ✓（成長値 %1$d）",
    "toast_restore_fail": "復元に失敗しました",
    "toast_picker_fail": "ファイル選択を開けませんでした",
    "toast_export_ok": "書き出しました ✓",
    "toast_export_fail": "書き出しに失敗しました",
    "keepalive_dlg_title": "自動終了防止の設定",
    "keepalive_msg": "システムがバックグラウンドのアプリを整理するとバブルが消えます。以下の3ステップで設定すると安定します：\n\n1. バッテリーの白リスト（%1$s）——下の「バッテリー設定へ」をタップ\n\n2. 自動起動：「アプリ情報へ」→ バッテリー使用量 → 自動起動／完全バックグラウンド動作を許可\n\n3. 最近のタスクでロック：最近のタスクを pullsown し、画面オフ再生のカードでロックをタップ",
    "state_done": "完了 ✓",
    "state_todo": "未完了",
    "keepalive_btn_battery": "バッテリー設定へ",
    "keepalive_btn_ok": "了解",
    "keepalive_btn_appdetail": "アプリ情報へ",
    "locale_dlg_title": "「通知の読み取り権限」が必要です",
    "locale_dlg_msg": "「この話まで聴く」は再生中の話数を読み取る必要があります。\nシステム設定で画面オフ再生の「通知の読み取り権限」を許可してください（再生位置の読み取りのみに使用し、他の目的には使いません）。\n\n権限を付与しない場合も通常のカウントダウンタイマーで利用できます。",
    "locale_dlg_go": "権限を付与",
    "locale_dlg_fallback": "タイマーを使う",
    "toast_no_progress": "再生位置を読み取れませんでした：先に1話再生してから選ぶか、タイマーを使ってください",
    "ach_toast_fmt": "🏆 実績解除：%1$s %2$s",
    "ach_dlg_title_fmt": "精霊の実績 %1$d/%2$d",
    "ach_dlg_hint": "バッジは精霊の左に、下から難易度順に点灯します",
    "ach_row_unlocked": "点灯済み",
    "ach_row_locked": "未解除",
    "dlg_ok": "OK",
    "toast_charge": "成長値 +%1$d を回収 ✓",
    "pet_caption_sleepy": "%1$s はお眠り中 · 1話聴くと起きます",
    "pet_caption_max": "%1$s · 最高到達 · 成長値 %2$d 蓄積中",
    "pet_caption_progress": "%1$s · 成長値 %2$d / %3$d",
    "ach_c100_t": "百回の習慣",
    "ach_c100_d": "画面オフを累計100回",
    "ach_marathon_t": "マラソン",
    "ach_marathon_d": "1回の連続視聴で1時間",
    "ach_week_t": "継続",
    "ach_week_d": "7日連続で画面オフ記録あり",
    "ach_king_t": "雷王の戴冠式",
    "ach_king_d": "精霊が雷王に進化",
    "calib_title": "省エネ実測",
    "calib_msg_calibrated": "本機の実測で校正済み（%1$s）\n実測の節約レート ≈ %2$.0f mAh/時",
    "calib_msg_uncalibrated": "まだ実測校正していません（現在はOLEDの汎用モデルで推定）",
    "calib_msg_ambient": "\n\n画面オフ中の端末消費電力の実測 ≈ %1$.0f mA（%2$d 回サンプル）",
    "calib_msg_flow": "\n\n校正手順（約6分）：\n① 画面をオンにしたまま任意の動画を3分再生\n② アプリが自動的に画面を消し、3分待機\n\n充電しないでください。バッテリーは15%〜95%に保ってください。",
    "calib_btn_restart": "再校正",
    "calib_btn_start": "校正を開始",
    "calib_btn_close": "閉じる",
    "calib_step1_title": "校正 1/2 · 画面をオンで再生",
    "calib_step1_init": "直ちに任意の動画を再生し、画面をオンにしたままにしてください…",
    "toast_calib_too_few": "有効なサンプルが不足しています（充電中ではありませんか？）。校正を中止しました",
    "toast_calib_need_service": "先に画面オフ再生アシスタントを起動してください",
    "toast_calib_step2": "校正手順 2/2：画面を消したまま3分待機、操作は不要です",
    "toast_calib_ok": "校正完了 ✓（有効サンプル %1$d）",
    "toast_calib_invalid": "校正サンプルが無効です（充電中ではありませんか？）。再試行してください",
    "toast_calib_done": "校正完了：本機の実測節約 ≈ %1$.0f mAh/時",
    "toast_calib_timeout": "校正がタイムアウトしました。再試行してください",
    "saved_basis_measured": "本機の実測に基づく",
    "saved_basis_model": "OLED画面の消費電力で推定",
    "sum_saved_fmt": "%1$s節約 ≈ %2$d mAh（%3$s）",
    "fmt_sessions": "%1$d 回",
    "sum_extra_fmt": "最長1回 %1$s · 画面オフ %2$d 回",
    "list_empty": "記録はまだありません——画面オフ再生を1回行うとここに明細が表示されます",
    "pager_info_fmt": "%1$d / %2$d ページ · 全 %3$d 件",
    "day_today": "今日",
    "day_yesterday": "昨日",
    "share_chooser": "共有先",
    "share_text": "⚡ 私は「画面オフ再生」で画面を消して %1$s 聴き、推定節約 %2$d mAh でした\n電能精霊は「%3$s」まで進化しました\n完全無料・広告なしの画面オフ再生ツール（0.6MB・オフライン動作）\nhttps://github.com/ch1209498273/XiTing",
    "toast_share_done": "共有完了 · +%1$d 成長値獲得予定（統計ページの左のバーで回収）",
    "toast_share_claimed": "本日の共有報酬は受取済みです。また明日（1日1回）",
    "share_sub_claimed": "本日受取済み ✓ · また明日",
    "share_sub_avail": "今日は +%1$d 獲得できます · 未回収",
    "help_title": "成長値について",
    "help_msg": "・視聴1分につき成長値1点（まず未回収に入ります）\n・1日目の初回共有で成長値5点（まず未回収に入ります）\n・未回収の上限は200点。到達後は蓄積されません——先に回収してから視聴してください\n・未回収の成長値は3日間有効です。期限が近い分はバー上で赤く表示されます\n・左のバーをタップすると回収され、精霊のレベルに反映されます",
    "toast_media_on": "オンにしました：画面オフ解除後に ⏮ ⏸ ⏭ キーを表示します",
    "toast_browser_fail": "ブラウザを開けませんでした",
    "update_value_fmt": "現在 v%1$s",
    "bubble_default": "画面オフ（デフォルト）",
    "bubble_label_off": "画面オフ",
    "bubble_dlg_title": "バブル表示（リアルタイムプレビュー）",
    "toast_bubble_locked": "「%1$s」は未解禁 · 成長値 %2$d が必要",
    "bubble_value_default": "デフォルト",
    "unit_h": "時",
    "unit_m": "分",
    "unit_m_full": "分",
    "unit_s": "秒",
    "about_title": "画面オフ再生について",
    "about_section_app": "このアプリについて",
    "about_intro": "映像を見ながら目を閉じてSimply聴きたい？ バブルを1タップするだけで画面が完全に消えます：バックライトが物理的に消え、タッチも遮断され、音はそのまま再生されます——あらゆる動画アプリで使えます。\n\n暗い表示の時計とバッテリー表示を選択できます（再ロック後も最低輝度で常時表示し、内容を定期的にわずかに動かしてOLEDパネルを保護）；着信時は自動的に画面オフを解除するので電話を逃しません；自動終了と「この話まで聴く」スマートタイマーに対応；イヤホン抜去で自動的に動画へ戻ります。\n\n画面オフの解除後は話送り：前へ / 再生一時停止 / 次へ（設定で有効化できます）。\n\n電能精霊：視聴で得た成長値を蓄積し、5形態の精霊を育てます（電火花 → 電球 → 雷雲精霊 → 嵐の精霊 → 雷王）。バブルのアバターとしても使えます。最高到達後も成長値は蓄積。実績システムが視聴マイルストーンを記録します。\n\nホーム画面ウィジェット：3サイズで、精霊・視聴データ・ワンタップ画面オフを常設。バブルは長押しで素早く終了できます。\n\nデータバックアップ：ダウンロードの XiTing フォルダに自動バックアップ（端末内のみ）、再インストール後もワンタップで復元。全データは端末内にのみ保存され、ネットワーク権限はありません。",
    "about_section_privacy": "プライバシーと安全",
    "about_privacy": "・INTERNET 権限なし：物理的にネットワークに接続できません\n・画面の内容は一切読み取りません\n・全データは端末内のみに保存（システムのクラウドバックアップと機種変更時の自動移行は明示的にオフ）\n・バックアップはあなたが明示的に書き出しをタップした時のみ「ダウンロード/XiTing」ディレクトリに保存され、管理は自分で行います\n・「通知の読み取り権限」を明示的に付与した場合に限り、画面オフのバッジ用に通知件数をカウントします。通知のタイトルや内容は読み取らず、権限がなければ機能全体が動作しません\n・アプリにはビルド識別子（ビルドIDとチャネル）を含みます（不正複製の追跡専用で、端末情報は一切含みません）\n・広告なし、トラッキングなし、計測なし",
    "about_section_open": "オープンソースと連絡先",
    "about_github": "GitHub：github.com/ch1209498273/XiTing\n（タップでプロジェクトページを開く）",
    "about_feedback": "フィードバック：1209498273@qq.com",
    "about_section_license": "ライセンス",
    "about_license": "非商用ライセンス（「LICENSE」参照）：個人の学習・交流目的での自由な使用と共有は可能（出典の明記が必要）。いかなる商用利用も禁止。出典および追跡識別子の削除・改変は禁止。\n\n本プロジェクトは YouTube、Google およびあらゆる動画プラットフォームとは一切関係ありません。学習・交流目的のみを指了指し、正版の支援をお願いします。",
    "about_version_fmt": "バージョン %1$s · ビルドID %2$s",
    "gallery_title": "精霊図鑑",
    "count_placeholder": "0 回",
    "dur_min_placeholder": "0分",
    "dur_placeholder": "0秒",
    "update_placeholder": "現在 v—",
    "version_placeholder": "バージョン —",
    "pager_placeholder": "1 / 1",
    "gallery_btn": "🎨 図鑑",
    "streak_fmt": "🔥 連続視聴 %1$d 日",
    "streak_best_fmt": "最長 %1$d 日",
    "toast_rare_energy": "⚡ レアな雷嵐エネルギー +%1$d！",
    "toast_skin_unlock": "🎨 新スキン解禁：%1$s",
    "skin_default_n": "クラシック配色",
    "skin_cond_default": "デフォルト形態",
    "skin_star_n": "星夜の魂",
    "skin_cond_star": "累計節約 1000 mAh",
    "skin_aurora_n": "オーロラの精霊",
    "skin_cond_aurora": "1回の連続視聴 2時間",
    "skin_sakura_n": "桜雨精霊",
    "skin_cond_sakura": "連続視聴 14日",
    "skin_magma_n": "マグマの暴君",
    "skin_cond_magma": "最高到達後にさらに2000成長値",
    "skin_jade_n": "翡翠の雷公",
    "skin_cond_jade": "連続視聴 60日",
    "gallery_locked": "🔒 %1$s（%2$s）",
    "gallery_worn": "「%1$s」に着せ替えました",
    "gallery_form_label": "形態",
    "gallery_skin_label": "配色",
    "gallery_locked_short": "🔒%1$s",
    "gallery_form_locked_toast": "成長値 %1$d に達するとこの形態が解禁されます",
    "gallery_form_next": "さらに %1$d 成長値たまると次の形態が解禁されます",
    "gallery_form_maxed": "全形態を解禁しました",
    "bubble_style_title": "バブル表示",
    "toast_battery_fail": "システム設定を開けませんでした。アプリ情報から手動で設定してください",
    "chart_no_data": "データがありません",
    "lang_title": "言語 / Language",
    "lang_sub": "表示言語",
    "lang_system": "システムに従う",
    "lang_zh": "简体中文",
    "lang_en": "English",
    "lang_ja": "日本語",
    "lang_restart_note": "変更は即座に反映されます",
}


def main():
    with open(ZH, encoding="utf-8") as f:
        src = f.read()

    pairs = re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', src, re.S)
    keys = [k for k, _ in pairs]

    missing = [k for k in keys if k not in JA]
    if missing:
        print("✗ 缺译 %d 条：" % len(missing))
        for k in missing:
            print("    %s" % k)
        sys.exit(1)

    if "--write" not in sys.argv:
        print("✓ 词表覆盖全部 %d 条。--write 可生成。" % len(keys))
        return

    os.makedirs(os.path.dirname(JA_OUT), exist_ok=True)
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- 日语。由 tools/mk_ja.py 从 values/strings.xml 的键顺序生成，勿手改。 -->",
        "<resources>",
    ]
    for k, _ in pairs:
        # aapt 对未转义的撇号会报错，与 values-en 保持同样处理
        v = (JA[k].replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace("'", "\\'"))
        lines.append('    <string name="%s">%s</string>' % (k, v))
    lines.append("</resources>")
    lines.append("")

    with open(JA_OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print("✓ 已生成 %s（%d 条）" % (JA_OUT, len(pairs)))


if __name__ == "__main__":
    main()
