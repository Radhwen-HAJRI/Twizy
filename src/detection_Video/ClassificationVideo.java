package detection_Video;



import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.*;

public class ClassificationVideo {
    
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
    
    // ==================== MÊMES MÉTHODES QUE ClassificationPanneaux ====================
    
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
    
    private static String extraireValeurString(String json, String cle) {
        String search = "\"" + cle + "\":\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int start = idx + search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
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
    
    // Extraction du symbole à partir d'un fichier (pour les panneaux vidéo extraits)
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
    
    // ==================== CLASSIFICATION VIDEO ====================
    
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
    
    public static void classerPanneauxVideo(String dossierExtraits, String dossierReferences) {
        Map<String, Mat> references = chargerReferences(dossierReferences);
        if (references.isEmpty()) {
            System.out.println("Aucune reference chargee");
            return;
        }
        
        File dossier = new File(dossierExtraits);
        File[] fichiers = dossier.listFiles((dir, name) -> 
            name.startsWith("panneau_video_") && name.endsWith(".jpg") && !name.contains("_infos"));
        
        if (fichiers == null || fichiers.length == 0) {
            System.out.println("Aucun panneau trouve dans " + dossierExtraits);
            return;
        }
        
        // Trier par numéro
        Arrays.sort(fichiers, (a, b) -> {
            int numA = Integer.parseInt(a.getName().replaceAll("\\D+", ""));
            int numB = Integer.parseInt(b.getName().replaceAll("\\D+", ""));
            return Integer.compare(numA, numB);
        });
        
        System.out.println("\n========================================");
        System.out.println("CLASSIFICATION DES PANNEAUX VIDEO");
        System.out.println("========================================\n");
        
        for (File f : fichiers) {
            String cheminTest = f.getAbsolutePath();
            String nomFichier = f.getName();
            
            System.out.println("Test: " + nomFichier);
            
            Mat maskTest = extraireSymbolePanneau(cheminTest, false);
            if (maskTest == null) {
                System.out.println("  -> Impossible d'extraire le symbole");
                continue;
            }
            
            // Calculer les scores avec toutes les références
            Map<String, Double> scores = new LinkedHashMap<>();
            String meilleureClasse = "inconnu";
            double meilleurScore = -1;
            
            for (Map.Entry<String, Mat> entry : references.entrySet()) {
                double[] resultats = comparerMasques(maskTest, entry.getValue());
                double score = resultats[0];
                scores.put(entry.getKey(), score);
                
                if (score > meilleurScore) {
                    meilleurScore = score;
                    meilleureClasse = entry.getKey();
                }
            }
            
            // Afficher les scores triés
            scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(e -> System.out.printf("  %s: %.3f%n", e.getKey(), e.getValue()));
            
            String nomAffichage = AFFICHAGE.getOrDefault(meilleureClasse, meilleureClasse);
            System.out.println("Resultat: " + nomAffichage + " (score: " + String.format("%.3f", meilleurScore) + ")");
            System.out.println();
            
            maskTest.release();
        }
        
        System.out.println("========================================");
    }
    
    public static void main(String[] args) {
        String dossierExtraits = "ressources/video_extraits";
        String dossierReferences = "ressources/references";
        
        classerPanneauxVideo(dossierExtraits, dossierReferences);
    }
}