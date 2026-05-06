"""
classifier.py — Chargement et inférence du modèle YOLOv8
=========================================================
Corrections vs version précédente :
  • Seuil de confiance configurable (défaut 0.55)
  • Retourne "Inconnu" uniquement sous le seuil
  • top_k pour afficher plusieurs candidats
"""

from ultralytics import YOLO
import numpy as np


# Seuil de confiance par défaut.
# En dessous : on considère que le modèle ne sait pas.
DEFAULT_CONF_THRESHOLD = 0.55


class SignClassifier:
    """
    Wrapper YOLOv8 classification.

    Paramètres
    ----------
    model_path : str
        Chemin vers le fichier best.pt entraîné.
    conf_threshold : float
        Confiance minimale pour accepter une prédiction.
        Tout ce qui est en dessous → "Inconnu".
    """

    def __init__(self, model_path: str, conf_threshold: float = DEFAULT_CONF_THRESHOLD):
        self.model = YOLO(model_path)
        self.conf_threshold = conf_threshold

    def predict(self, roi_image: np.ndarray) -> tuple[str, float]:
        """
        Prédit la classe d'un ROI (image BGR numpy).

        Retourne
        --------
        label : str
            Nom de la classe ou "Inconnu" si sous le seuil.
        confidence : float
            Score de confiance (0.0 → 1.0).
        """
        results = self.model(roi_image, verbose=False)
        probs = results[0].probs

        top_id   = probs.top1
        conf     = float(probs.top1conf)
        label    = results[0].names[top_id]

        if conf < self.conf_threshold:
            return "Inconnu", conf

        return label, conf

    def predict_topk(self, roi_image: np.ndarray, k: int = 3) -> list[dict]:
        """
        Retourne les k meilleures prédictions (filtrées par seuil).

        Retourne
        --------
        list of {"label": str, "conf": float}
        """
        results = self.model(roi_image, verbose=False)
        probs   = results[0].probs
        names   = results[0].names

        top5_ids  = probs.top5
        top5_conf = probs.top5conf.tolist()

        preds = []
        for cls_id, conf in zip(top5_ids[:k], top5_conf[:k]):
            if conf < self.conf_threshold:
                continue
            preds.append({"label": names[int(cls_id)], "conf": round(conf, 3)})

        return preds
