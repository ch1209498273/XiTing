# -*- coding: utf-8 -*-
"""en —— 省电实测（被动测量）。"""

MS = {
    "meas_title": "Power measurement",
    "meas_purpose": "Method: reads your battery's energy counter and compares the real consumption between \"normal use\" and \"screen-off listening\" to work out the saving.\n\nYou don't need to do anything — samples are taken automatically as you use your phone. Not measured while charging (the data would be meaningless).",
    "meas_progress_fmt": "Collected: screen-off %1$d times · screen-on %2$d times",
    "meas_level_0": "Not enough samples yet, using a generic model for now. It gets more accurate after a few days of normal use.",
    "meas_level_1": "Few samples so far, treat the estimate as rough. It improves after a few days.",
    "meas_level_2": "A good number of samples — the estimate is fairly reliable.",
    "meas_level_3": "Plenty of samples — the estimate is reliable.",
    "meas_black_power_fmt": "Whole-device draw with screen off ≈ %1$.0f mW (from %2$d real sessions)",
    "meas_note_charging": "· Not measured while charging (the counter goes back up)",
    "meas_note_screendim": "· Not counted as screen-on while the display is off",
    "meas_note_minlen": "· Segments shorter than 2 minutes are ignored",
    "meas_reset": "Clear & restart",

}
