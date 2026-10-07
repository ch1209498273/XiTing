# -*- coding: utf-8 -*-
"""Deutsch — lange Texte. Siehe tools/i18n/ko.py

⚠ 这一份曾经被错误地塞进了法语内容（拆分脚本按变量名切分时把 DE2 当成了 FR2），
  结果 de 的界面里显示的是法语。已按 values-de/strings.xml 重写。
"""

DE2 = {
    "restore_msg": "Wenn du « Bildschirm-aus-Zuhören » schon genutzt und wieder deinstalliert hast: Die Sicherung liegt im Ordner XiTing unter Downloads (XiTing-backup.json). Fortschritt und Statistik lassen sich hiermit einem Tastendruck zurücksetzen.\n\nFür neue Nutzer: « Nein danke » antippen.",
    "restore_other_msg": "Diese Sicherung enthält %1$d Fortschritt, die Geräte-ID stimmt aber nicht (Gerätewechsel oder Umstieg von einer älteren signierten Version). Trotzdem wiederherstellen?",
    "keepalive_msg": "Das System räumt Hintergrund-Apps auf, wodurch die Blase verschwindet. Drei Schritte für dauerhafte Stabilität:\n\n1. Akku-Whitelist (%1$s) — unten auf « Zu den Akku-Einstellungen » tippen\n\n2. Autostart: « App-Info » → Akkuverbrauch → Autostart und volle Hintergrundaktivität erlauben\n\n3. Karte sperren: Letzte Apps öffnen und auf der Karte Bildschirm-aus-Zuhören das Schlosssymbol antippen",
    "locale_dlg_msg": "« Diese Folge fertig » muss den Wiedergabefortschritt lesen.\nErlaube in den Systemeinstellungen den Benachrichtigungszugriff (wird nur zum Lesen des Fortschritts genutzt).\n\nOhne Berechtigung funktioniert der normale Countdown genauso.",
    "toast_no_progress": "Wiedergabefortschritt nicht lesbar: erst eine Folge abspielen oder den Countdown verwenden.",
    "calib_msg_calibrated": "Auf diesem Gerät kalibriert (%1$s)\nGemessene Einsparung ≈ %2$.0f mAh/h",
    "calib_msg_uncalibrated": "Noch nicht kalibriert (Schätzung nach dem OLED-Standardmodell)",
    "calib_msg_ambient": "\n\nGemessener Verbrauch bei schwarzem Bildschirm ≈ %1$.0f mA (%2$d Messungen)",
    "calib_msg_flow": "\n\nAblauf (etwa 6 Minuten):\n① 3 Minuten lang ein Video bei eingeschaltetem Bildschirm abspielen\n② Die App schaltet automatisch 3 Minuten auf schwarzen Bildschirm\n\nNicht laden; Akkustand zwischen 15 % und 95 % halten.",
    "calib_step1_init": "Jetzt ein Video abspielen und den Bildschirm eingeschaltet lassen…",
    "toast_calib_too_few": "Zu wenige gültige Messungen (wird geladen?), Kalibrierung abgebrochen",
    "toast_calib_need_service": "Bitte zuerst den Assistenten starten, dann kalibrieren",
    "toast_calib_step2": "Kalibrierung 2/2: 3 Minuten schwarzen Bildschirm lassen, nichts tun",
    "toast_calib_ok": "Kalibrierung abgeschlossen ✓ (%1$d gültige Messungen)",
    "toast_calib_invalid": "Messung ungültig (wird geladen?), bitte erneut versuchen",
    "toast_calib_done": "Kalibrierung abgeschlossen: gemessene Einsparung ≈ %1$.0f mAh/h",
    "toast_calib_timeout": "Zeitüberschreitung bei der Kalibrierung, bitte erneut versuchen",
    "share_text": "⚡ Mit « Bildschirm-aus-Zuhören » habe ich %1$s bei schwarzem Bildschirm gehört, rund %2$d mAh gespart\nMein elektrisches Wesen ist zu « %3$s » herangewachsen\nKostenlos und werbefrei, komplett offline (0,6 MB)\nhttps://github.com/ch1209498273/XiTing",
    "help_msg": "· 1 Punkt Fortschritt pro Minute Zuhören (zuerst gesammelt)\n· 5 Punkte für die erste Freigabe des Tages (zuerst gesammelt)\n· Maximal 200 gesammelte Punkte; erst abholen, dann weiterhören\n· Gesammelte Punkte verfallen nach 3 Tagen; bald ablaufende erscheinen rot\n· Tippe auf den linken Balken zum Abholen, sie zählen dann zum Wesen",
    "about_intro": "Beim Video die Augen zumachen und einfach zuhören? Ein Tipp auf die Blase, und der Bildschirm wird ganz schwarz: Hintergrundbeleuchtung physisch aus, Berührung gesperrt, der Ton läuft weiter — mit jeder Video-App.\n\nOptionale dunkle Uhr und Akkuanzeige (nach erneutem Sperren auf dunkelster Stufe sichtbar, Inhalt wandert leicht zum Schutz des OLED); bei Anrufen wird der schwarze Bildschirm automatisch verlassen, sodass kein Anruf verpasst wird; Abschalt-Timer und der smarte Timer « Diese Folge fertig »; beim Abziehen der Kopfhörer zurück zum Video.\n\nNach dem Aufwachen: Folge zurück / Wiedergabe-Pause / weiter (in den Einstellungen aktivierbar).\n\nElektrisches Wesen: Zuhören lässt es zu fünf Formen wachsen (Funke → Kugel → Gewitterwölkchen → Sturmgeist → Donnerkönig) und kann als Blasen-Avatar dienen; Erfolge halten deine Meilensteine fest.\n\nWidgets: drei Größen mit Wesen, Statistik und einem Tipp zum Ausschalten; langes Drücken der Blase beendet alles.\n\nBackup: automatisch im Ordner XiTing unter Downloads (nur Gerät), nach Neuinstallation mit einem Tipp wiederherstellbar. Alle Daten bleiben auf dem Gerät, keine Netzwerkberechtigung.",
    "about_privacy": "· Keine INTERNET-Berechtigung: kann physisch nicht online gehen\n· Liest keinen Bildschirminhalt aus\n· Alle Daten bleiben auf dem Gerät (Cloud-Backup und Gerätewechsel ausdrücklich deaktiviert)\n· Das Backup wird nur beim Tippen auf Exportieren in Downloads/XiTing geschrieben und von dir verwaltet\n· Benachrichtigungen werden nur nach deiner ausdrücklichen Freigabe gezählt (für die Anzeige auf dem schwarzen Bildschirm); Titel und Inhalt werden nie gelesen, ohne Freigabe ist die Funktion völlig inaktiv\n· Die App enthält eine Build-Kennung (Build-ID und Kanal), ausschließlich zur Rückverfolgung von Raubkopien, ohne Geräteinformationen\n· Keine Werbung, kein Tracking, keine Analyse",
    "about_github": "GitHub: github.com/ch1209498273/XiTing\n(antippen, um die Projektseite zu öffnen)",
    "about_license": "Nichtkommerzielle Lizenz (siehe LICENSE): freie Nutzung und Weitergabe zum Lernen und Austausch, sofern die Namensnennung erhalten bleibt; jede kommerzielle Nutzung ist verboten; das Entfernen oder Verändern der Namensnennung und der Rückverfolgungskennung ist untersagt.\n\nDieses Projekt steht in keiner Verbindung zu YouTube, Google oder einer Video-Plattform. Nur zum Lernen und Austreiben — bitte unterstützt Originale.",
}
