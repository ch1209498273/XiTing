# -*- coding: utf-8 -*-
"""
English —— strings moved out of Kotlin code.
See tools/i18n/ko.py for the general convention.

⚠ One language per file. load_dicts assigns *every* dict in this file to the
  language derived from the filename, so a stray second dict here would
  silently overwrite the first.
"""

EN = {
    "dur_sec_fmt": "%1$ds",
    "dur_min_fmt": "%1$d min",
    "dur_hour_fmt": "%1$.1f h",
    "battery_fmt": "Battery %1$d%",
    "cd_prev_episode": "Previous episode",
    "cd_play_pause": "Play / pause",
    "cd_next_episode": "Next episode",
    "bubble_label_plain": "Screen off",
    "notif_timer_fmt": " · turns off in %1$d min",
    "dlg_got_it": "Got it",
    "calib_date_fmt": "MMM d",
    "calib_progress_fmt": "Keep the screen on and playing, do not touch the phone\nRemaining %1$d:%2$02d (%3$d samples)",
    "evolve_fmt": "🎉 Evolved! %1$s → %2$s",
    "mah_saved_fmt": "⚡ Saved %1$d mAh",
}
