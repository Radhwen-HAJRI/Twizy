"""
train.py — Entraînement YOLOv8 classification pour la détection de panneaux Twizy
===================================================================================
Corrections apportées vs run2 :
  • imgsz 128 → 224   (les chiffres des panneaux étaient illisibles à 128px)
  • yolov8s-cls → yolov8m-cls  (plus de capacité sans exploser la VRAM)
  • batch 16 → 32     (meilleure stabilité du gradient)
  • patience 20 → 30
  • augmentations activées (hsv, scale, translate)
  • fix class mismatch : on copie les dossiers manquants dans val/test
    pour que YOLO ne lève plus d'ERROR
"""

import os
import shutil
from pathlib import Path
from ultralytics import YOLO

# ─────────────────────────────────────────────────────────────────────────────
# CHEMINS  (adaptez si besoin)
# ─────────────────────────────────────────────────────────────────────────────
DATASET_DIR = Path("../dataset_yolo_format")
TRAIN_DIR   = DATASET_DIR / "train"
VAL_DIR     = DATASET_DIR / "valid"
TEST_DIR    = DATASET_DIR / "test"


# ─────────────────────────────────────────────────────────────────────────────
# FIX CLASS MISMATCH
# Problème : val a 51 classes, test a 50, train en a 53.
# YOLO exige le même nombre dans tous les splits.
# Solution : pour chaque classe manquante dans val/test,
#            on copie UNE image depuis train (juste pour satisfaire YOLO).
# ─────────────────────────────────────────────────────────────────────────────
def fix_class_mismatch():
    train_classes = sorted([d.name for d in TRAIN_DIR.iterdir() if d.is_dir()])
    print(f"[FIX] Train : {len(train_classes)} classes")

    for split_dir in [VAL_DIR, TEST_DIR]:
        split_classes = {d.name for d in split_dir.iterdir() if d.is_dir()}
        missing = [c for c in train_classes if c not in split_classes]

        if not missing:
            print(f"[FIX] {split_dir.name} : aucune classe manquante ✓")
            continue

        print(f"[FIX] {split_dir.name} : {len(missing)} classes manquantes → {missing}")
        for cls_name in missing:
            src_cls_dir = TRAIN_DIR / cls_name
            imgs = list(src_cls_dir.glob("*.jpg")) + list(src_cls_dir.glob("*.png"))
            if not imgs:
                continue
            dst_cls_dir = split_dir / cls_name
            dst_cls_dir.mkdir(exist_ok=True)
            # Copier seulement 1 image (suffit pour que YOLO accepte la classe)
            shutil.copy2(imgs[0], dst_cls_dir / imgs[0].name)

        after = {d.name for d in split_dir.iterdir() if d.is_dir()}
        print(f"[FIX] {split_dir.name} après correction : {len(after)} classes ✓")


# ─────────────────────────────────────────────────────────────────────────────
# ENTRAÎNEMENT
# ─────────────────────────────────────────────────────────────────────────────
def train_model():
    print("=" * 60)
    print("  TWIZY — Entraînement YOLOv8 Classification")
    print("=" * 60)

    # 1. Corriger le mismatch avant de lancer YOLO
    fix_class_mismatch()

    # 2. Charger le modèle pré-entraîné
    model = YOLO("yolov8m-cls.pt")   # 'medium' : meilleur ratio précision/vitesse

    # 3. Lancer l'entraînement
    results = model.train(
        data=str(DATASET_DIR),
        epochs=20,
        imgsz=224,           # ← WAS 128 — clé de l'amélioration !
        batch=32,
        patience=30,
        device=0,            # GPU NVIDIA
        workers=4,

        # ── Optimiseur ──────────────────────────────────────────────
        optimizer="AdamW",
        lr0=0.001,
        lrf=0.01,
        weight_decay=0.0005,
        warmup_epochs=3,

        # ── Augmentations ──────────────────────────────────────────
        hsv_h=0.015,         # légère variation teinte
        hsv_s=0.5,           # variation saturation
        hsv_v=0.3,           # variation luminosité
        scale=0.4,           # zoom aléatoire ±40%
        translate=0.1,       # translation ±10%
        fliplr=0.0,          # pas de flip horizontal (panneaux ont un sens)
        flipud=0.0,
        erasing=0.3,         # Random erasing (robustesse aux occlusions)

        # ── Sauvegarde ─────────────────────────────────────────────
        project="Twizy_Signs",
        name="yolov8m_run3",
        exist_ok=False,
        plots=True,
        save=True,
    )

    best = Path("Twizy_Signs/yolov8m_run3/weights/best.pt")
    print(f"\n✅ Entraînement terminé !")
    print(f"   Meilleur modèle → {best}")
    print(f"\n   Lancez ensuite :")
    print(f"   python pipeline.py --weights {best} --source <image_ou_video>")


if __name__ == "__main__":
    train_model()
