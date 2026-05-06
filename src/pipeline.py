"""
pipeline.py  v5
===============
Ajout : filtre de COHERENCE COULEUR
  - Zone rouge  → impossible : feu vert, feu orange, panneaux bleus obligation
  - Zone bleue  → impossible : stop, sens interdit, vitesse, feux rouges
  - Zone jaune  → panneaux danger/triangle uniquement

Aussi : fix encodage "—" remplace par "-"
"""

import argparse
import cv2
import numpy as np
from pathlib import Path

from sign_detector import SignDetector
from classifier   import SignClassifier


# ─── Classes par couleur attendue ─────────────────────────────────────────────
# Quand OpenCV dit "rouge" → seules ces classes sont valides
RED_CLASSES = {
    "Speed_limit_20_km_h", "Speed_limit_30_km_h", "Speed_limit_50_km_h",
    "Speed_limit_60_km_h", "Speed_limit_70_km_h", "Speed_limit_80_km_h",
    "Speed_limit_100_km_h", "Speed_limit_120_km_h",
    "End_speed_limit_80_km_h", "End_of_all_speed_and_passing_limits",
    "No_entry", "No_passing", "No_passing_for_trucks", "No_vehicles",
    "Stop", "Yield", "Priority_road",
    "red-lights",   # feu rouge = rond rouge
}

BLUE_CLASSES = {
    "Ahead_only", "Turn_right_ahead", "Turn_left_ahead",
    "Keep_right", "Keep_left", "Roundabout_mandatory",
    "Go_straight_or_left", "Go_straight_or_right",
    "End_of_no_passing", "End_of_no_passing_by_trucks",
}

YELLOW_CLASSES = {
    "General_caution", "Children_crossing", "Bicycles_crossing",
    "Bumpy_road", "Slippery_road", "Road_work", "Traffic_signals",
    "Wild_animals_crossing", "Dangerous_curve_to_the_left",
    "Dangerous_curve_to_the_right", "Double_curve", "Road_narrows_on_the_right",
    "Beware_of_ice_snow",
}

# Feux de circulation : verts/oranges uniquement dans zones rouges/bleues
TRAFFIC_LIGHTS = {"green-lights", "yellow-lights", "red-lights"}


def _color_coherent(label: str, color: str) -> bool:
    """
    Verifie que la classe YOLO est coherente avec la couleur OpenCV.
    Retourne True si OK, False si incohérent (faux positif de classification).
    """
    label_low = label.lower()

    # Feux de signalisation : coherent avec rouge et bleu (pas jaune)
    if label in TRAFFIC_LIGHTS:
        if label == "green-lights":  return True   # accepté partout (rare)
        if label == "red-lights":    return color in ("red", "blue")
        return color in ("red", "blue")

    if label in RED_CLASSES:    return color in ("red",)
    if label in BLUE_CLASSES:   return color in ("blue",)
    if label in YELLOW_CLASSES: return color in ("yellow", "red")  # triangles rouges aussi

    # Classe inconnue : on accepte
    return True


# ─── Affichage lisible des classes ────────────────────────────────────────────
CLASS_LABELS = {
    "Speed_limit_20_km_h":  ("Limite 20 km/h",   (0, 50, 220)),
    "Speed_limit_30_km_h":  ("Limite 30 km/h",   (0, 50, 220)),
    "Speed_limit_50_km_h":  ("Limite 50 km/h",   (0, 50, 220)),
    "Speed_limit_60_km_h":  ("Limite 60 km/h",   (0, 50, 220)),
    "Speed_limit_70_km_h":  ("Limite 70 km/h",   (0, 50, 220)),
    "Speed_limit_80_km_h":  ("Limite 80 km/h",   (0, 50, 220)),
    "Speed_limit_100_km_h": ("Limite 100 km/h",  (0, 50, 220)),
    "Speed_limit_120_km_h": ("Limite 120 km/h",  (0, 50, 220)),
    "End_speed_limit_80_km_h":              ("Fin limite 80",       (80, 80, 200)),
    "End_of_all_speed_and_passing_limits":  ("Fin toutes limites",  (80, 80, 200)),
    "No_entry":             ("Sens interdit",      (0,  0, 200)),
    "No_passing":           ("Depassement interdit",(0, 80, 220)),
    "No_passing_for_trucks":("Depassement camions interdit",(0,80,220)),
    "No_vehicles":          ("Circulation interdite",(0,80,220)),
    "Stop":                 ("STOP",               (0,  0, 180)),
    "Yield":                ("Cedez le passage",   (0,180,255)),
    "Priority_road":        ("Route prioritaire",  (0,180,255)),
    "Ahead_only":           ("Tout droit",         (200,100, 0)),
    "Turn_right_ahead":     ("Tourne a droite",    (200,100, 0)),
    "Turn_left_ahead":      ("Tourne a gauche",    (200,100, 0)),
    "Keep_right":           ("Serrez a droite",    (200,100, 0)),
    "Keep_left":            ("Serrez a gauche",    (200,100, 0)),
    "Roundabout_mandatory": ("Giratoire",          (200,100, 0)),
    "Go_straight_or_left":  ("Tout droit ou gauche",(200,100,0)),
    "Go_straight_or_right": ("Tout droit ou droite",(200,100,0)),
    "General_caution":      ("Danger general",     (0,180,255)),
    "Children_crossing":    ("Enfants",            (0,180,255)),
    "Bicycles_crossing":    ("Piste cyclable",     (0,180,255)),
    "Bumpy_road":           ("Route bosselee",     (0,180,255)),
    "Slippery_road":        ("Route glissante",    (0,180,255)),
    "Road_work":            ("Travaux",            (0,180,255)),
    "Traffic_signals":      ("Feux signalisation", (0,180,255)),
    "Wild_animals_crossing":("Animaux sauvages",   (0,180,255)),
    "Dangerous_curve_to_the_left":  ("Virage gauche", (0,180,255)),
    "Dangerous_curve_to_the_right": ("Virage droite", (0,180,255)),
    "Double_curve":         ("Double virage",      (0,180,255)),
    "Road_narrows_on_the_right": ("Route retrecie",(0,180,255)),
    "Beware_of_ice_snow":   ("Verglas / Neige",   (0,180,255)),
    "green-lights":         ("Feu VERT - Passez",  (0,200,  0)),
    "red-lights":           ("Feu ROUGE - Stop",   (0,  0,200)),
    "yellow-lights":        ("Feu ORANGE",         (0,200,255)),
}
DEFAULT_COLOR = (200, 140, 0)


def _get_display(label: str):
    if label in CLASS_LABELS:
        return CLASS_LABELS[label]
    return label.replace("_", " "), DEFAULT_COLOR


# ─── Dessin ───────────────────────────────────────────────────────────────────
def _draw_box(img, x, y, w, h, color, text):
    cv2.rectangle(img, (x, y), (x+w, y+h), color, 2)
    c = 14
    for cx, cy in [(x,y),(x+w,y),(x,y+h),(x+w,y+h)]:
        dx = 1 if cx == x else -1
        dy = 1 if cy == y else -1
        cv2.line(img,(cx,cy),(cx+dx*c,cy),color,4)
        cv2.line(img,(cx,cy),(cx,cy+dy*c),color,4)

    font  = cv2.FONT_HERSHEY_SIMPLEX
    scale = 0.60
    thick = 2
    (tw,th),bl = cv2.getTextSize(text, font, scale, thick)
    ty = y - 8
    if ty - th - bl < 0:
        ty = y + h + th + 8
    cv2.rectangle(img,(x, ty-th-bl-4),(x+tw+6, ty+2), color, cv2.FILLED)
    cv2.putText(img, text,(x+3, ty-bl), font, scale,(255,255,255),thick,cv2.LINE_AA)


# ─── Frame ────────────────────────────────────────────────────────────────────
def process_frame(frame, detector, classifier):
    rois      = detector.get_rois(frame)
    annotated = frame.copy()
    found     = 0

    for roi in rois:
        x, y, w, h, det_color = roi
        crop = frame[y:y+h, x:x+w]
        if crop.size == 0:
            continue

        label, conf = classifier.predict(crop)
        if label == "Inconnu":
            continue

        # ── FILTRE COHERENCE COULEUR ─────────────────────────────────────────
        if not _color_coherent(label, det_color):
            continue   # classification incohérente avec la couleur détectée

        found += 1
        display, color = _get_display(label)
        text = f"{display}  {conf*100:.0f}%"
        _draw_box(annotated, x, y, w, h, color, text)

    cv2.rectangle(annotated,(0,0),(290,38),(20,20,20),cv2.FILLED)
    cv2.putText(annotated, f"Panneaux detectes : {found}",
                (8,26), cv2.FONT_HERSHEY_SIMPLEX, 0.70,
                (255,255,255), 2, cv2.LINE_AA)
    return annotated


# ─── Rapport ──────────────────────────────────────────────────────────────────
def _report(items):
    print("\n" + "="*60)
    print(f"  RAPPORT - {len(items)} panneau(x) reconnu(s)")
    print("="*60)
    for i,(roi,label,conf,det_color) in enumerate(items):
        display, _ = _get_display(label)
        bar = "#" * int(conf*20)
        print(f"\n  #{i+1}  Classe    : {label}")
        print(f"       Sens      : {display}")
        print(f"       Couleur   : {det_color}")
        print(f"       Confiance : [{bar:<20}] {conf*100:.1f}%")
        print(f"       Position  : x={roi[0]} y={roi[1]} w={roi[2]} h={roi[3]}")
    print("="*60 + "\n")


# ─── Main ─────────────────────────────────────────────────────────────────────
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--weights",   required=True)
    ap.add_argument("--source",    required=True)
    ap.add_argument("--conf",      type=float, default=0.50)
    ap.add_argument("--min-area",  type=int,   default=500)
    ap.add_argument("--min-fill",  type=float, default=0.25)
    ap.add_argument("--save",      action="store_true")
    ap.add_argument("--debug",     action="store_true")
    args = ap.parse_args()

    print(f"\n[INFO] Modele  : {args.weights}")
    print(f"[INFO] Conf    : {args.conf*100:.0f}%")
    print(f"[INFO] Debug   : {'OUI' if args.debug else 'non'}\n")

    detector   = SignDetector(min_area=args.min_area,
                              min_fill_ratio=args.min_fill,
                              debug=args.debug)
    classifier = SignClassifier(model_path=args.weights,
                                conf_threshold=args.conf)

    source = args.source
    try:    source = int(source);  is_image = False
    except: is_image = str(source).lower().endswith((".jpg",".jpeg",".png",".bmp"))

    if is_image:
        frame = cv2.imread(str(source))
        if frame is None:
            print(f"[ERREUR] : {source}"); return

        rois    = detector.get_rois(frame)
        report_items = []
        for roi in rois:
            x,y,w,h,det_color = roi
            crop = frame[y:y+h, x:x+w]
            if crop.size == 0: continue
            label, conf = classifier.predict(crop)
            if label == "Inconnu": continue
            if not _color_coherent(label, det_color): continue
            report_items.append((roi, label, conf, det_color))

        _report(report_items)
        annotated = process_frame(frame, detector, classifier)

        if args.save:
            out = Path("outputs") / ("result_"+Path(str(source)).name)
            out.parent.mkdir(exist_ok=True)
            cv2.imwrite(str(out), annotated)
            print(f"[INFO] Sauvegarde -> {out}")

        cv2.imshow("Twizy - Sign Detection", annotated)
        print("[INFO] Appuie sur une touche pour fermer.")
        cv2.waitKey(0)
        cv2.destroyAllWindows()

    else:
        cap = cv2.VideoCapture(source)
        if not cap.isOpened():
            print(f"[ERREUR] : {source}"); return

        writer = None
        if args.save:
            fourcc = cv2.VideoWriter_fourcc(*"mp4v")
            fps    = cap.get(cv2.CAP_PROP_FPS) or 25
            ww,hh  = int(cap.get(3)), int(cap.get(4))
            out    = Path("outputs/result_video.mp4")
            out.parent.mkdir(exist_ok=True)
            writer = cv2.VideoWriter(str(out), fourcc, fps, (ww,hh))

        print("[INFO] 'q' pour quitter.")
        while True:
            ret, frame = cap.read()
            if not ret: break
            annotated = process_frame(frame, detector, classifier)
            if writer: writer.write(annotated)
            cv2.imshow("Twizy - Sign Detection", annotated)
            if cv2.waitKey(1) & 0xFF == ord("q"): break

        cap.release()
        if writer: writer.release()
        cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
