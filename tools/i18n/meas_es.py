# -*- coding: utf-8 -*-
"""es —— 省电实测（被动测量）。"""

MS = {
    "meas_title": "Medición de ahorro",
    "meas_purpose": "Método: lee el contador de energía de la batería y compara el consumo real entre «uso normal» y «escucha con pantalla apagada» para calcular el ahorro.\n\nNo tienes que hacer nada: la medición es automática mientras usas el móvil. No se mide con el cargador (los datos no tendrían sentido).",
    "meas_progress_fmt": "Recogido: pantalla apagada %1$d veces · pantalla encendida %2$d veces",
    "meas_level_0": "Aún pocas muestras; ahora se usa un modelo genérico. Se afina tras unos días de uso normal.",
    "meas_level_1": "Pocas muestras todavía; la estimación es aproximada. Mejora en unos días.",
    "meas_level_2": "Muestras suficientes: la estimación es bastante fiable.",
    "meas_level_3": "Muchas muestras: la estimación es fiable.",
    "meas_black_power_fmt": "Consumo del móvil con pantalla apagada ≈ %1$.0f mW (según %2$d sesiones reales)",
    "meas_note_charging": "· No se mide con el cargador (el contador sube)",
    "meas_note_screendim": "· Con la pantalla apagada no cuenta como «pantalla encendida»",
    "meas_note_minlen": "· Los tramos de menos de 2 minutos se descartan",
    "meas_reset": "Borrar y empezar",

}
