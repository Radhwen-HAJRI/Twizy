package detection_Image;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DetectionPanneauxImage {
    
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }
    
    // ==================== SUPPRESSION DES ANCIENS FICHIERS ====================
    
    private static void nettoyerDossier(String dossierPath) {
        File dossier = new File(dossierPath);
        if (dossier.exists() && dossier.isDirectory()) {
            for (File fichier : dossier.listFiles()) {
                if (fichier.isFile()) {
                    fichier.delete();
                }
            }
            System.out.println("Dossier nettoye: " + dossierPath);
        }
    }
    
    private static void sauvegarderInfos(String chemin, int panneauId, int cx, int cy, int r, 
                                         int x1, int y1, int x2, int y2, int localCx, int localCy, 
                                         String nomImage) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"id\": ").append(panneauId).append(",\n");
        sb.append("  \"source\": \"").append(nomImage).append("\",\n");
        sb.append("  \"cx_original\": ").append(cx).append(",\n");
        sb.append("  \"cy_original\": ").append(cy).append(",\n");
        sb.append("  \"r_original\": ").append(r).append(",\n");
        sb.append("  \"crop_x1\": ").append(x1).append(",\n");
        sb.append("  \"crop_y1\": ").append(y1).append(",\n");
        sb.append("  \"crop_x2\": ").append(x2).append(",\n");
        sb.append("  \"crop_y2\": ").append(y2).append(",\n");
        sb.append("  \"cx_crop\": ").append(localCx).append(",\n");
        sb.append("  \"cy_crop\": ").append(localCy).append(",\n");
        sb.append("  \"r_crop\": ").append(r).append(",\n");
        sb.append("  \"largeur_crop\": ").append(x2 - x1).append(",\n");
        sb.append("  \"hauteur_crop\": ").append(y2 - y1).append("\n");
        sb.append("}");
        
        try (FileWriter fw = new FileWriter(chemin)) {
            fw.write(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void detecterEtExtrairePanneaux(String cheminImage, String dossierSortie, String dossierControle) {
        
        new File(dossierSortie).mkdirs();
        new File(dossierControle).mkdirs();
        
        Mat img = Imgcodecs.imread(cheminImage);
        if (img.empty()) {
            System.out.println("Erreur: impossible de charger l'image " + cheminImage);
            return;
        }
        
        String nomImage = new File(cheminImage).getName().replace(".jpg", "");
        
        int maxWidth = 1200;
        if (img.cols() > maxWidth) {
            double scale = (double) maxWidth / img.cols();
            Mat resized = new Mat();
            Imgproc.resize(img, resized, new Size(maxWidth, img.rows() * scale));
            img = resized;
        }
        
        Mat output = img.clone();
        int height = img.rows();
        int width = img.cols();
        
        Mat hsv = new Mat();
        Imgproc.cvtColor(img, hsv, Imgproc.COLOR_BGR2HSV);
        
        Mat mask1 = new Mat();
        Mat mask2 = new Mat();
        Core.inRange(hsv, new Scalar(0, 90, 70), new Scalar(12, 255, 255), mask1);
        Core.inRange(hsv, new Scalar(168, 90, 70), new Scalar(180, 255, 255), mask2);
        
        Mat maskRed = new Mat();
        Core.bitwise_or(mask1, mask2, maskRed);
        
        Mat roiBas = maskRed.submat(new Rect(0, (int)(height * 0.8), width, height - (int)(height * 0.8)));
        roiBas.setTo(new Scalar(0));
        
        Mat kernelOpen = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Mat kernelClose = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        
        Imgproc.morphologyEx(maskRed, maskRed, Imgproc.MORPH_OPEN, kernelOpen);
        Imgproc.morphologyEx(maskRed, maskRed, Imgproc.MORPH_CLOSE, kernelClose);
        
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(maskRed, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        
        File dossier = new File(dossierSortie);
        int panneauId = dossier.listFiles() != null ? dossier.listFiles().length : 0;
        List<Detection> detections = new ArrayList<>();
        
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area < 80) continue;
            
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double perimeter = Imgproc.arcLength(contour2f, true);
            if (perimeter == 0) continue;
            
            double circularity = 4 * Math.PI * area / (perimeter * perimeter);
            
            Rect rect = Imgproc.boundingRect(contour);
            if (rect.width == 0 || rect.height == 0) continue;
            
            double ratio = (double) rect.width / rect.height;
            if (ratio < 0.65 || ratio > 1.35) continue;
            if (circularity < 0.65) continue;
            if (rect.width < 12 || rect.height < 12) continue;
            if (rect.width > 140 || rect.height > 140) continue;
            
            int cx = rect.x + rect.width / 2;
            int cy = rect.y + rect.height / 2;
            if (cy > height * 0.8) continue;
            
            int size = Math.max(rect.width, rect.height);
            int margin = (int)(size * 0.8);
            int r = size / 2;
            
            int x1 = Math.max(0, cx - size / 2 - margin);
            int y1 = Math.max(0, cy - size / 2 - margin);
            int x2 = Math.min(width, cx + size / 2 + margin);
            int y2 = Math.min(height, cy + size / 2 + margin);
            
            Mat roi = img.submat(new Rect(x1, y1, x2 - x1, y2 - y1));
            Mat roiRed = maskRed.submat(new Rect(x1, y1, x2 - x1, y2 - y1));
            
            if (roi.empty()) continue;
            
            Mat hsvRoi = new Mat();
            Imgproc.cvtColor(roi, hsvRoi, Imgproc.COLOR_BGR2HSV);
            Mat whiteMask = new Mat();
            Core.inRange(hsvRoi, new Scalar(0, 0, 120), new Scalar(180, 110, 255), whiteMask);
            
            int roiH = roiRed.rows();
            int roiW = roiRed.cols();
            int localCx = cx - x1;
            int localCy = cy - y1;
            
            Mat centerMask = Mat.zeros(roiH, roiW, CvType.CV_8UC1);
            Imgproc.circle(centerMask, new Point(localCx, localCy), (int)(r * 0.55), new Scalar(255), -1);
            
            Mat ringMask = Mat.zeros(roiH, roiW, CvType.CV_8UC1);
            Imgproc.circle(ringMask, new Point(localCx, localCy), (int)(r * 1.10), new Scalar(255), -1);
            Imgproc.circle(ringMask, new Point(localCx, localCy), (int)(r * 0.60), new Scalar(0), -1);
            
            double areaCenter = Core.countNonZero(centerMask);
            double areaRing = Core.countNonZero(ringMask);
            if (areaCenter == 0 || areaRing == 0) continue;
            
            Mat whiteCenter = new Mat();
            Core.bitwise_and(whiteMask, centerMask, whiteCenter);
            double whiteCenterRatio = Core.countNonZero(whiteCenter) / areaCenter;
            
            Mat redCenter = new Mat();
            Core.bitwise_and(roiRed, centerMask, redCenter);
            double redCenterRatio = Core.countNonZero(redCenter) / areaCenter;
            
            Mat redRing = new Mat();
            Core.bitwise_and(roiRed, ringMask, redRing);
            double redRingRatio = Core.countNonZero(redRing) / areaRing;
            
            if (redRingRatio < 0.18) continue;
            if (whiteCenterRatio < 0.30) continue;
            if (redCenterRatio > 0.25) continue;
            
            Mat smallRoi = img.submat(new Rect(rect.x, rect.y, rect.width, rect.height));
            Mat smallHsv = new Mat();
            Imgproc.cvtColor(smallRoi, smallHsv, Imgproc.COLOR_BGR2HSV);
            Mat smallWhite = new Mat();
            Core.inRange(smallHsv, new Scalar(0, 0, 120), new Scalar(180, 110, 255), smallWhite);
            double whiteRatioBox = Core.countNonZero(smallWhite) / (double)(rect.width * rect.height);
            
            if (whiteRatioBox < 0.20) continue;
            
            boolean doublon = false;
            for (Detection d : detections) {
                double dist = Math.sqrt(Math.pow(cx - d.cx, 2) + Math.pow(cy - d.cy, 2));
                if (dist < Math.max(size, d.size) * 0.8) {
                    doublon = true;
                    break;
                }
            }
            if (doublon) continue;
            
            detections.add(new Detection(cx, cy, size));
            panneauId++;
            
            Mat panneauExtrait = img.submat(new Rect(x1, y1, x2 - x1, y2 - y1));
            String nomFichier = dossierSortie + "/panneau_" + panneauId + ".jpg";
            Imgcodecs.imwrite(nomFichier, panneauExtrait);
            
            String jsonPath = dossierSortie + "/panneau_" + panneauId + "_infos.json";
            sauvegarderInfos(jsonPath, panneauId, cx, cy, r, x1, y1, x2, y2, localCx, localCy, nomImage);
            
            System.out.println("Panneau " + panneauId + " detecte: " + nomFichier);
            
            // Dessin sur l'image de contrôle
            Imgproc.rectangle(output, new Point(x1, y1), new Point(x2, y2), new Scalar(0, 255, 0), 3);
            Imgproc.circle(output, new Point(cx, cy), r, new Scalar(255, 0, 0), 2);
            
            // Ajouter le numéro du panneau sur l'image de contrôle
            String texte = "Panneau " + panneauId;
            Point textPos = new Point(x1, Math.max(25, y1 - 5));
            Imgproc.putText(output, texte, textPos, Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(0, 0, 255), 2);
        }
        
        // Sauvegarde de l'image de contrôle avec les rectangles
        String cheminControle = dossierControle + "/" + nomImage + "_detection.jpg";
        String cheminMasque = dossierControle + "/" + nomImage + "_masque_rouge.jpg";
        Imgcodecs.imwrite(cheminControle, output);
        Imgcodecs.imwrite(cheminMasque, maskRed);
        
        System.out.println("Detections: " + detections.size());
        System.out.println("Image controle: " + cheminControle);
    }
    
    static class Detection {
        int cx, cy, size;
        Detection(int cx, int cy, int size) {
            this.cx = cx;
            this.cy = cy;
            this.size = size;
        }
    }
    
    public static void main(String[] args) {
        String dossierExtraits = "ressources/extraits";
        String dossierControle = "ressources/controle";
        String dossierTests = "ressources/tests";
        
        System.out.println("=========================================");
        System.out.println("NETTOYAGE DES DOSSIERS");
        System.out.println("=========================================");
        nettoyerDossier(dossierExtraits);
        nettoyerDossier(dossierControle);
        
        System.out.println("\n=========================================");
        System.out.println("DETECTION DES PANNEAUX");
        System.out.println("=========================================");
        
        for (int i = 1; i <= 10; i++) {
            System.out.println("\n==============================");
            System.out.println("Traitement p" + i + ".jpg");
            detecterEtExtrairePanneaux(
                dossierTests + "/p" + i + ".jpg", 
                dossierExtraits, 
                dossierControle
            );
        }
        
        System.out.println("\n=========================================");
        System.out.println("DETECTION TERMINEE");
        System.out.println("=========================================");
    }
}