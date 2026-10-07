# -*- coding: utf-8 -*-
"""de —— 省电实测（被动测量）。"""

MS = {
    "meas_title": "Energiespar-Messung",
    "meas_purpose": "Methode: Liest den Energiezähler des Akkus und vergleicht den echten Verbrauch zwischen „normaler Nutzung\" und „Zuhören mit ausgeschaltetem Bildschirm\", um die Ersparnis zu berechnen.\n\nDu musst nichts tun — die Messung läuft automatisch, während du das Handy benutzt. Bei ausgeschaltetem Ladezustand wird nicht gemessen (die Daten wären meaningless).",
    "meas_progress_fmt": "Gesammelt: Bildschirm aus %1$d-mal · Bildschirm an %2$d-mal",
    "meas_level_0": "Noch zu wenige Messungen, derzeit wird ein Standardmodell verwendet. Nach ein paar Tagen normaler Nutzung wird es genauer.",
    "meas_level_1": "Noch wenige Messungen, die Schätzung ist grob. Sie wird nach ein paar Tagen besser.",
    "meas_level_2": "Ausreichend Messungen — die Schätzung ist recht verlässlich.",
    "meas_level_3": "Viele Messungen — die Schätzung ist verlässlich.",
    "meas_black_power_fmt": "Geräteverbrauch bei ausgeschaltetem Bildschirm ≈ %1$.0f mW (aus %2$d echten Sitzungen)",
    "meas_note_charging": "· Beim Laden wird nicht gemessen (der Zähler läuft hoch)",
    "meas_note_screendim": "· Bei ausgeschaltetem Display zählt es nicht als „Bildschirm an\"",
    "meas_note_minlen": "· Abschnitte unter 2 Minuten werden verworfen",
    "meas_reset": "Zurücksetzen",

}
