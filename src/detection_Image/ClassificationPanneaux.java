package detection_Image;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.*;

public class ClassificationPanneaux {
    
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }
    
    private static final int TAILLE_SYMBOL = 220;
    private static final int CANVAS_SIZE = 260;
    
    private static final Map<String, String> AFFICHAGE = new HashMap<>();
    static {
        AFFICHAGE.put("30", "30 km/h");
        AFFICHAGE.put("50", "50 km/h");
        AFFICHAGE.put("70", "70 km/h");
        AFFICHAGE.put("90", "90 km/h");
        AFFICHAGE.put("110", "110 km/h");
        AFFICHAGE.put("depassement", "Interdiction de depasser");
    }
    
    // Supprimer les anciens fichiers
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
    
    private static int[] lireInfosCercle(String cheminImage) {
        String base = cheminImage.replace(".jpg", "");
        String cheminJson = base + "_infos.json";
        File f = new File(cheminJson);
        if (!f.exists()) return null;
        
        try {
            String content = new String(java.nio.file.Files.readAllBytes(f.toPath()));
            int cx = extraireValeur(content, "cx_crop");
            int cy = extraireValeur(content, "cy_crop");
            int r = extraireValeur(content, "r_crop");
            if (cx == -1 || cy == -1 || r == -1) return null;
            return new int[]{cx, cy, r};
        } catch (Exception e) {
            return null;
        }
    }
    
    private static int extraireValeur(String json, String cle) {
        String search = "\"" + cle + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return -1;
        int start = idx + search.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) {
            start++;
        }
        int end = start;
        while (end < json.length() && (json.charAt(end) >= '0' && json.charAt(end) <= '9')) {
            end++;
        }
        if (start == end) return -1;
        return Integer.parseInt(json.substring(start, end));
    }
    
    private static double[] trouverCercleRouge(Mat img) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(img, hsv, Imgproc.COLOR_BGR2HSV);
        
        Mat mask1 = new Mat();
        Mat mask2 = new Mat();
        Core.inRange(hsv, new Scalar(0, 80, 50), new Scalar(12, 255, 255), mask1);
        Core.inRange(hsv, new Scalar(168, 80, 50), new Scalar(180, 255, 255), mask2);
        
        Mat maskRed = new Mat();
        Core.bitwise_or(mask1, mask2, maskRed);
        
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        Imgproc.morphologyEx(maskRed, maskRed, Imgproc.MORPH_CLOSE, kernel);
        Imgproc.morphologyEx(maskRed, maskRed, Imgproc.MORPH_OPEN, kernel);
        
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(maskRed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        
        if (contours.isEmpty()) return null;
        
        MatOfPoint maxContour = contours.stream()
            .max(Comparator.comparingDouble(c -> Imgproc.contourArea(c)))
            .orElse(null);
        
        if (maxContour == null || Imgproc.contourArea(maxContour) < 50) return null;
        
        MatOfPoint2f contour2f = new MatOfPoint2f(maxContour.toArray());
        Point center = new Point();
        float[] radius = new float[1];
        Imgproc.minEnclosingCircle(contour2f, center, radius);
        
        return new double[]{center.x, center.y, radius[0]};
    }
    
    public static Mat extraireSymbolePanneau(String cheminImage, boolean afficherDebug) {
        Mat img = Imgcodecs.imread(cheminImage);
        if (img.empty()) return null;
        
        int[] infos = lireInfosCercle(cheminImage);
        double cx, cy, r;
        
        if (infos != null) {
            cx = infos[0];
            cy = infos[1];
            r = infos[2];
        } else {
            double[] cercle = trouverCercleRouge(img);
            if (cercle == null) return null;
            cx = cercle[0];
            cy = cercle[1];
            r = cercle[2];
        }
        
        int rInterieur = (int)(r * 0.56);
        int x1 = Math.max(0, (int)(cx - rInterieur));
        int y1 = Math.max(0, (int)(cy - rInterieur));
        int x2 = Math.min(img.cols(), (int)(cx + rInterieur));
        int y2 = Math.min(img.rows(), (int)(cy + rInterieur));
        
        Mat roi = img.submat(new Rect(x1, y1, x2 - x1, y2 - y1));
        if (roi.empty()) return null;
        
        Mat resized = new Mat();
        Imgproc.resize(roi, resized, new Size(TAILLE_SYMBOL, TAILLE_SYMBOL));
        
        Mat gray = new Mat();
        Imgproc.cvtColor(resized, gray, Imgproc.COLOR_BGR2GRAY);
        
        Mat mask = new Mat();
        Core.inRange(gray, new Scalar(0), new Scalar(95), mask);
        
        Mat circleMask = Mat.zeros(TAILLE_SYMBOL, TAILLE_SYMBOL, CvType.CV_8UC1);
        Imgproc.circle(circleMask, new Point(TAILLE_SYMBOL / 2, TAILLE_SYMBOL / 2), (int)(TAILLE_SYMBOL * 0.46), new Scalar(255), -1);
        Core.bitwise_and(mask, circleMask, mask);
        
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);
        
        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        int nbLabels = Imgproc.connectedComponentsWithStats(mask, labels, stats, centroids);
        
        Mat clean = Mat.zeros(mask.size(), CvType.CV_8UC1);
        for (int i = 1; i < nbLabels; i++) {
            double area = stats.get(i, Imgproc.CC_STAT_AREA)[0];
            if (area > 35) {
                for (int y = 0; y < labels.rows(); y++) {
                    for (int x = 0; x < labels.cols(); x++) {
                        if (labels.get(y, x)[0] == i) {
                            clean.put(y, x, 255);
                        }
                    }
                }
            }
        }
        
        mask = clean;
        
        Mat points = new Mat();
        Core.findNonZero(mask, points);
        if (points.empty()) return null;
        
        Rect boundingRect = Imgproc.boundingRect(new MatOfPoint(points));
        int marge = 20;
        
        int cropX1 = Math.max(0, boundingRect.x - marge);
        int cropY1 = Math.max(0, boundingRect.y - marge);
        int cropX2 = Math.min(TAILLE_SYMBOL, boundingRect.x + boundingRect.width + marge);
        int cropY2 = Math.min(TAILLE_SYMBOL, boundingRect.y + boundingRect.height + marge);
        
        Mat crop = mask.submat(new Rect(cropX1, cropY1, cropX2 - cropX1, cropY2 - cropY1));
        if (crop.empty()) return null;
        
        Mat finalMask = Mat.zeros(TAILLE_SYMBOL, TAILLE_SYMBOL, CvType.CV_8UC1);
        
        double scale = Math.min((TAILLE_SYMBOL * 0.55) / crop.cols(), (TAILLE_SYMBOL * 0.55) / crop.rows());
        scale = Math.min(scale, 2.2);
        
        int newW = Math.max(1, (int)(crop.cols() * scale));
        int newH = Math.max(1, (int)(crop.rows() * scale));
        
        Mat resizedCrop = new Mat();
        Imgproc.resize(crop, resizedCrop, new Size(newW, newH));
        
        int startX = (TAILLE_SYMBOL - newW) / 2;
        int startY = (TAILLE_SYMBOL - newH) / 2;
        
        resizedCrop.copyTo(finalMask.submat(new Rect(startX, startY, newW, newH)));
        
        return finalMask;
    }
    
    private static double[] comparerMasques(Mat maskTest, Mat maskRef) {
        Mat testResized = new Mat();
        Mat refResized = new Mat();
        Imgproc.resize(maskTest, testResized, new Size(TAILLE_SYMBOL, TAILLE_SYMBOL));
        Imgproc.resize(maskRef, refResized, new Size(TAILLE_SYMBOL, TAILLE_SYMBOL));
        
        Imgproc.threshold(testResized, testResized, 127, 255, Imgproc.THRESH_BINARY);
        Imgproc.threshold(refResized, refResized, 127, 255, Imgproc.THRESH_BINARY);
        
        double bestCorr = -1;
        
        double[] scales = {0.94, 1.00, 1.06};
        int[] offsets = {-6, 0, 6};
        
        for (double scale : scales) {
            int newSize = (int)(TAILLE_SYMBOL * scale);
            if (newSize <= 10 || newSize >= CANVAS_SIZE) continue;
            
            Mat refScaled = new Mat();
            Imgproc.resize(refResized, refScaled, new Size(newSize, newSize));
            
            for (int dx : offsets) {
                for (int dy : offsets) {
                    Mat canvas = Mat.zeros(CANVAS_SIZE, CANVAS_SIZE, CvType.CV_8UC1);
                    int startX = (CANVAS_SIZE - newSize) / 2 + dx;
                    int startY = (CANVAS_SIZE - newSize) / 2 + dy;
                    
                    if (startX < 0 || startY < 0) continue;
                    if (startX + newSize > CANVAS_SIZE || startY + newSize > CANVAS_SIZE) continue;
                    
                    refScaled.copyTo(canvas.submat(new Rect(startX, startY, newSize, newSize)));
                    
                    Mat result = new Mat();
                    Imgproc.matchTemplate(testResized, canvas, result, Imgproc.TM_CCOEFF_NORMED);
                    Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
                    
                    if (mmr.maxVal > bestCorr) {
                        bestCorr = mmr.maxVal;
                    }
                }
            }
        }
        
        Mat testBin = new Mat();
        Mat refBin = new Mat();
        Imgproc.threshold(maskTest, testBin, 127, 255, Imgproc.THRESH_BINARY);
        Imgproc.threshold(maskRef, refBin, 127, 255, Imgproc.THRESH_BINARY);
        
        Mat intersection = new Mat();
        Mat union = new Mat();
        Core.bitwise_and(testBin, refBin, intersection);
        Core.bitwise_or(testBin, refBin, union);
        
        double iou = Core.countNonZero(intersection) / (double)(Core.countNonZero(union) + 1e-6);
        
        double score = 0.85 * bestCorr + 0.15 * iou;
        
        return new double[]{score, bestCorr, iou};
    }
    
    /**
     * Sauvegarde l'image du panneau avec le cadre vert et la vitesse
     */
    public static void sauvegarderImageAvecCadreEtVitesse(String cheminPanneau, String classe, String cheminSortie) {
        Mat img = Imgcodecs.imread(cheminPanneau);
        if (img.empty()) {
            System.out.println("Impossible de charger l'image: " + cheminPanneau);
            return;
        }
        
        // Lire les coordonnées du cercle
        int[] infos = lireInfosCercle(cheminPanneau);
        
        if (infos != null && infos.length >= 3) {
            int cx = infos[0];
            int cy = infos[1];
            int r = infos[2];
            
            if (cx > 0 && cy > 0 && r > 0 && cx < img.cols() && cy < img.rows()) {
                // Cadre vert
                int x1 = Math.max(0, cx - r - 5);
                int y1 = Math.max(0, cy - r - 5);
                int x2 = Math.min(img.cols(), cx + r + 5);
                int y2 = Math.min(img.rows(), cy + r + 5);
                
                Imgproc.rectangle(img, new Point(x1, y1), new Point(x2, y2), new Scalar(0, 255, 0), 3);
                
                // Texte de la vitesse
                String texte = AFFICHAGE.getOrDefault(classe, classe);
                Point textPos = new Point(x1, Math.max(20, y1 - 5));
                Imgproc.putText(img, texte, textPos, Imgproc.FONT_HERSHEY_SIMPLEX, 0.6, new Scalar(0, 0, 255), 2);
                
                System.out.println("  Cadre: (" + x1 + "," + y1 + ") -> (" + x2 + "," + y2 + "), Vitesse: " + texte);
            } else {
                System.out.println("  Coordonnees invalides: cx=" + cx + ", cy=" + cy + ", r=" + r);
                // Texte au centre
                String texte = AFFICHAGE.getOrDefault(classe, classe);
                Imgproc.putText(img, texte, new Point(20, 40), Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(0, 0, 255), 2);
            }
        } else {
            System.out.println("  Aucune coordonnee trouvee dans le JSON");
            // Texte au centre
            String texte = AFFICHAGE.getOrDefault(classe, classe);
            Imgproc.putText(img, texte, new Point(20, 40), Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, new Scalar(0, 0, 255), 2);
        }
        
        // Sauvegarder
        Imgcodecs.imwrite(cheminSortie, img);
        System.out.println("  Image sauvegardee: " + cheminSortie);
        
        img.release();
    }
    
    public static ClassificationResult classifierPanneau(String cheminImage, Map<String, Mat> references, double seuilAcceptation) {
        Mat maskTest = extraireSymbolePanneau(cheminImage, false);
        if (maskTest == null) return new ClassificationResult("inconnu", maskTest, null);
        
        Map<String, ScoreInfo> scores = new HashMap<>();
        
        for (Map.Entry<String, Mat> entry : references.entrySet()) {
            double[] resultats = comparerMasques(maskTest, entry.getValue());
            scores.put(entry.getKey(), new ScoreInfo(resultats[0], resultats[1], resultats[2]));
        }
        
        String meilleureClasse = Collections.max(scores.entrySet(), Map.Entry.comparingByValue(
            (a, b) -> Double.compare(a.score, b.score)
        )).getKey();
        
        double meilleurScore = scores.get(meilleureClasse).score;
        
        if (meilleurScore < seuilAcceptation) {
            return new ClassificationResult("inconnu", maskTest, scores);
        }
        
        return new ClassificationResult(meilleureClasse, maskTest, scores);
    }
    
    public static Map<String, Mat> chargerReferences(String dossierRefs) {
        Map<String, String> fichiers = new LinkedHashMap<>();
        fichiers.put("30", "ref30.jpg");
        fichiers.put("50", "ref50.jpg");
        fichiers.put("70", "ref70.jpg");
        fichiers.put("90", "ref90.jpg");
        fichiers.put("110", "ref110.jpg");
        fichiers.put("depassement", "refdouble.jpg");
        
        Map<String, Mat> references = new HashMap<>();
        
        for (Map.Entry<String, String> entry : fichiers.entrySet()) {
            String chemin = dossierRefs + "/" + entry.getValue();
            if (!new File(chemin).exists()) {
                System.out.println("Reference manquante: " + chemin);
                continue;
            }
            
            Mat mask = extraireSymbolePanneau(chemin, false);
            if (mask == null) {
                System.out.println("Impossible d'extraire le symbole: " + chemin);
                continue;
            }
            
            references.put(entry.getKey(), mask);
        }
        
        return references;
    }
    
    public static void testerToutLeDossier(String dossierExtraits, String dossierReferences, String dossierResultats, double seuilAcceptation) {
        // Nettoyer et créer le dossier des résultats
        nettoyerDossier(dossierResultats);
        new File(dossierResultats).mkdirs();
        
        Map<String, Mat> references = chargerReferences(dossierReferences);
        if (references.isEmpty()) {
            System.out.println("Aucune reference chargee");
            return;
        }
        
        File dossier = new File(dossierExtraits);
        if (!dossier.exists()) {
            System.out.println("Le dossier " + dossierExtraits + " n'existe pas");
            return;
        }
        
        File[] fichiers = dossier.listFiles((dir, name) -> 
            name.toLowerCase().startsWith("panneau_") && name.toLowerCase().endsWith(".jpg") &&
            !name.contains("_infos"));
        
        if (fichiers == null || fichiers.length == 0) {
            System.out.println("Aucun panneau trouve dans " + dossierExtraits);
            return;
        }
        
        System.out.println("\n=========================================");
        System.out.println("CLASSIFICATION DES PANNEAUX");
        System.out.println("=========================================");
        
        for (File f : fichiers) {
            String cheminTest = f.getAbsolutePath();
            String nomFichier = f.getName();
            String baseName = nomFichier.replace(".jpg", "");
            
            System.out.println("\n==============================");
            System.out.println("Test: " + nomFichier);
            
            ClassificationResult result = classifierPanneau(cheminTest, references, seuilAcceptation);
            
            if (result.scores != null) {
                result.scores.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue().score, a.getValue().score))
                    .forEach(e -> System.out.println(e.getKey() + ": score=" + String.format("%.3f", e.getValue().score)));
            }
            
            System.out.println("Resultat: " + result.classe + " (" + AFFICHAGE.getOrDefault(result.classe, result.classe) + ")");
            
            // Sauvegarder l'image avec cadre vert et vitesse
            String sortieImage = dossierResultats + "/" + baseName + "_detecte.jpg";
            System.out.println("\n=== CREATION DE L'IMAGE AVEC CADRE VERT ===");
            sauvegarderImageAvecCadreEtVitesse(cheminTest, result.classe, sortieImage);
        }
        
        System.out.println("\n=========================================");
        System.out.println("Images sauvegardees dans: " + dossierResultats);
        System.out.println("=========================================");
    }
    
    static class ClassificationResult {
        String classe;
        Mat maskTest;
        Map<String, ScoreInfo> scores;
        
        ClassificationResult(String classe, Mat maskTest, Map<String, ScoreInfo> scores) {
            this.classe = classe;
            this.maskTest = maskTest;
            this.scores = scores;
        }
    }
    
    static class ScoreInfo {
        double score, corr, iou;
        ScoreInfo(double score, double corr, double iou) {
            this.score = score;
            this.corr = corr;
            this.iou = iou;
        }
    }
    
    public static void main(String[] args) {
        String dossierExtraits = "ressources/extraits";
        String dossierReferences = "ressources/references";
        String dossierResultats = "ressources/resultats_classification";
        
        System.out.println("=========================================");
        System.out.println("CLASSIFICATION DES PANNEAUX");
        System.out.println("=========================================");
        System.out.println("Dossier extraits: " + new File(dossierExtraits).getAbsolutePath());
        System.out.println("Dossier references: " + new File(dossierReferences).getAbsolutePath());
        System.out.println("Dossier resultats: " + new File(dossierResultats).getAbsolutePath());
        
        testerToutLeDossier(dossierExtraits, dossierReferences, dossierResultats, 0.18);
        
        System.out.println("\n=========================================");
        System.out.println("CLASSIFICATION TERMINEE");
        System.out.println("=========================================");
    }
}