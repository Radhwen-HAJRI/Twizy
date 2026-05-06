"""
sign_detector.py  v7
====================
Nouveaute : get_rois() retourne aussi la COULEUR DOMINANTE de chaque ROI
(red / blue / yellow) pour que le pipeline puisse valider la coherence
avec la prediction YOLO.
"""

import cv2
import numpy as np


RED_L1,  RED_U1   = np.array([0,   70, 60]),  np.array([12,  255, 255])
RED_L2,  RED_U2   = np.array([158, 70, 60]),  np.array([180, 255, 255])
BLUE_L,  BLUE_U   = np.array([95,  80, 60]),  np.array([135, 255, 255])
YELLOW_L, YELLOW_U = np.array([15, 80, 80]),  np.array([38,  255, 255])


class SignDetector:
    def __init__(
        self,
        min_area: int         = 500,
        max_area_ratio: float = 0.18,
        max_side_ratio: float = 0.42,
        min_side: int         = 25,
        min_fill_ratio: float = 0.25,
        min_solidity: float   = 0.40,
        padding: int          = 5,
        nms_iou_thresh: float = 0.35,
        debug: bool           = False,
    ):
        self.min_area        = min_area
        self.max_area_ratio  = max_area_ratio
        self.max_side_ratio  = max_side_ratio
        self.min_side        = min_side
        self.min_fill_ratio  = min_fill_ratio
        self.min_solidity    = min_solidity
        self.padding         = padding
        self.nms_iou_thresh  = nms_iou_thresh
        self.debug           = debug

    def get_rois(self, image: np.ndarray) -> list[tuple]:
        """
        Retourne liste de (x, y, w, h, color)
        color = 'red' | 'blue' | 'yellow'
        """
        H, W = image.shape[:2]
        max_area   = H * W * self.max_area_ratio
        max_side_w = W * self.max_side_ratio
        max_side_h = H * self.max_side_ratio

        enhanced = self._enhance(image)
        hsv = cv2.cvtColor(enhanced, cv2.COLOR_BGR2HSV)

        mask_red = cv2.bitwise_or(
            cv2.inRange(hsv, RED_L1, RED_U1),
            cv2.inRange(hsv, RED_L2, RED_U2),
        )
        mask_blue   = cv2.inRange(hsv, BLUE_L,   BLUE_U)
        mask_yellow = cv2.inRange(hsv, YELLOW_L, YELLOW_U)
        mask_all    = cv2.bitwise_or(cv2.bitwise_or(mask_red, mask_blue), mask_yellow)

        k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
        mask_clean = cv2.morphologyEx(mask_all, cv2.MORPH_CLOSE, k, iterations=2)
        mask_clean = cv2.morphologyEx(mask_clean, cv2.MORPH_OPEN,  k, iterations=1)

        if self.debug:
            cv2.imshow("DEBUG Rouge",  mask_red)
            cv2.imshow("DEBUG Bleu",   mask_blue)
            cv2.imshow("DEBUG Final",  mask_clean)

        contours, _ = cv2.findContours(mask_clean, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        boxes = []

        for cnt in contours:
            area = cv2.contourArea(cnt)
            if area < self.min_area or area > max_area:
                continue

            hull = cv2.convexHull(cnt)
            x, y, w, h = cv2.boundingRect(hull)

            if w > max_side_w or h > max_side_h:
                continue
            if w < self.min_side or h < self.min_side:
                continue

            ratio = w / h
            if not (0.40 < ratio < 2.5):
                continue

            solidity = area / (w * h)
            if solidity < self.min_solidity:
                if self.debug:
                    print(f"  [REJET solid={solidity:.2f}] ({x},{y},{w},{h})")
                continue

            roi_mask = mask_clean[y:y+h, x:x+w]
            fill = cv2.countNonZero(roi_mask) / (w * h)
            if fill < self.min_fill_ratio:
                if self.debug:
                    print(f"  [REJET fill={fill:.2f}] ({x},{y},{w},{h})")
                continue

            # Couleur dominante dans cette ROI
            r_px = cv2.countNonZero(mask_red  [y:y+h, x:x+w])
            b_px = cv2.countNonZero(mask_blue [y:y+h, x:x+w])
            y_px = cv2.countNonZero(mask_yellow[y:y+h, x:x+w])
            dom = max(r_px, b_px, y_px)
            if   dom == r_px: color = "red"
            elif dom == b_px: color = "blue"
            else:             color = "yellow"

            if self.debug:
                print(f"  [OK color={color} solid={solidity:.2f} fill={fill:.2f}] ({x},{y},{w},{h})")

            px = max(0, x - self.padding)
            py = max(0, y - self.padding)
            pw = min(W - px, w + 2 * self.padding)
            ph = min(H - py, h + 2 * self.padding)
            boxes.append((px, py, pw, ph, color))

        return self._nms(boxes)

    @staticmethod
    def _enhance(img):
        lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
        l, a, b = cv2.split(lab)
        clahe = cv2.createCLAHE(clipLimit=2.5, tileGridSize=(8, 8))
        return cv2.cvtColor(cv2.merge((clahe.apply(l), a, b)), cv2.COLOR_LAB2BGR)

    def _nms(self, boxes):
        if not boxes:
            return []
        boxes = sorted(boxes, key=lambda b: b[2]*b[3], reverse=True)
        kept  = []
        for b in boxes:
            if all(self._iou(b, k) < self.nms_iou_thresh for k in kept):
                kept.append(b)
        return kept

    @staticmethod
    def _iou(a, b):
        ax1,ay1,aw,ah = a[:4];  bx1,by1,bw,bh = b[:4]
        ix1=max(ax1,bx1); iy1=max(ay1,by1)
        ix2=min(ax1+aw,bx1+bw); iy2=min(ay1+ah,by1+bh)
        inter=max(0,ix2-ix1)*max(0,iy2-iy1)
        union=aw*ah+bw*bh-inter
        return inter/union if union>0 else 0.0
