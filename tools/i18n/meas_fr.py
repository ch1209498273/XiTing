# -*- coding: utf-8 -*-
"""fr —— 省电实测（被动测量）。"""

MS = {
    "meas_title": "Mesure d'économie",
    "meas_purpose": "Méthode : lit le compteur d'énergie de la batterie et compare la consommation réelle entre « usage normal » et « écoute écran off » pour en déduire l'économie.\n\nVous n'avez rien à faire — la mesure se fait automatiquement pendant que vous utilisez le téléphone. Rien n'est mesuré en charge (les données n'auraient aucun sens).",
    "meas_progress_fmt": "Collecté : écran off %1$d fois · écran allumé %2$d fois",
    "meas_level_0": "Pas encore assez d'échantillons, estimation sur un modèle générique. Plus juste après quelques jours d'usage normal.",
    "meas_level_1": "Peu d'échantillons, l'estimation est approximative. Elle s'améliore après quelques jours.",
    "meas_level_2": "Échantillons suffisants — l'estimation est assez fiable.",
    "meas_level_3": "Beaucoup d'échantillons — l'estimation est fiable.",
    "meas_black_power_fmt": "Consommation écran éteint ≈ %1$.0f mW (d'après %2$d sessions réelles)",
    "meas_note_charging": "· Pas de mesure en charge (le compteur remonte)",
    "meas_note_screendim": "· Écran éteint ne compte pas comme « écran allumé »",
    "meas_note_minlen": "· Les segments de moins de 2 minutes sont ignorés",
    "meas_reset": "Effacer et relancer",

}
