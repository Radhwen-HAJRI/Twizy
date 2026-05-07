package detection_Video;



import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DetectionVideo {
    
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }
    
    static List<PanneauExtrait> panneauxExtraits = new ArrayList<>();
    static final int DISTANCE_MIN = 120;
    static final int DELAI_MIN_FRAMES = 80;
    
    private static void sauvegarderInfos(String chemin, int panneauId, int cx, int cy, int r, 
                                         int x1, int y1, int x2, int y2, int localCx, int localCy, 
                                         int frameCount, double whiteRatio, double redRingRatio) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"id\": ").append(panneauId).append(",\n");
        sb.append("  \"frame\": ").append(frameCount).append(",\n");
        sb.append("  \"cx_original\": ").append(cx).append(",\n");
        sb.append("  \"cy_original\": ").append(cy).append(",\n");
        sb.append("  \"r_original\": ").append(r).append(",\n");
        sb.append("  \"white_ratio\": ").append(String.format("%.3f", whiteRatio)).append(",\n");
        sb.append("  \"red_ring_ratio\": ").append(String.format("%.3f", redRingRatio)).append(",\n");
        sb.append("  \"crop_x1\": ").append(x1).append(",\n");
        sb.append("  \"crop_y1\": ").append(y1).append(",\n");
        sb.append("  \"crop_x2\": ").append(x2).append(",\n");
        sb.append("  \"crop_y2\": ").append(y2).append(",\n");
        sb.append("  \"cx_crop\": ").append(localCx).append(",\n");
        sb.append("  \"cy_crop\": ").append(localCy).append(",\n");
        sb.append("  \"r_crop\": ").append(r).append("\n");
        sb.append("}");
        
        try (FileWriter fw = new FileWriter(chemin)) {
            fw.write(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static BufferedImage matToBufferedImage(Mat mat) {
        MatOfByte mob = new MatOfByte();
        Imgcodecs.imencode(".jpg", mat, mob);
        byte[] bytes = mob.toArray();
        try {
            InputStream in = new ByteArrayInputStream(bytes);
            return ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }
    
    private static boolean aDesChiffres(Mat roi) {
        Mat gray = new Mat();
        Imgproc.cvtColor(roi, gray, Imgproc.COLOR_BGR2GRAY);
        
        Mat mask = new Mat();
        Core.inRange(gray, new Scalar(0), new Scalar(100), mask);
        
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel);
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel);
        
        int centerX = mask.cols() / 2;
        int centerY = mask.rows() / 2;
        int rayon = mask.cols() / 3;
        
        Mat centerMask = Mat.zeros(mask.size(), CvType.CV_8UC1);
        Imgproc.circle(centerMask, new Point(centerX, centerY), rayon, new Scalar(255), -1);
        
        Mat centerRoi = new Mat();
        Core.bitwise_and(mask, centerMask, centerRoi);
        
        double pixelSombres = Core.countNonZero(centerRoi);
        double surface = Math.PI * rayon * rayon;
        double ratioSombres = pixelSombres / surface;
        
        gray.release();
        mask.release();
        kernel.release();
        centerMask.release();
        centerRoi.release();
        
        return (ratioSombres > 0.05 && ratioSombres < 0.50);
    }
    
    private static boolean estDejaExtrait(int cx, int cy) {
        for (PanneauExtrait p : panneauxExtraits) {
            double dist = Math.sqrt(Math.pow(cx - p.cx, 2) + Math.pow(cy - p.cy, 2));
            if (dist < DISTANCE_MIN) {
                return true;
            }
        }
        return false;
    }
    
    public static void detecterPanneauxVideo(String cheminVideo, String dossierSortie, String dossierControle) {
        
        new File(dossierSortie).mkdirs();
        new File(dossierControle).mkdirs();
        
        VideoCapture camera = new VideoCapture(cheminVideo);
        if (!camera.isOpened()) {
            System.out.println("Erreur: impossible d'ouvrir la video " + cheminVideo);
            return;
        }
        
        JFrame frame = new JFrame("Detection de panneaux - Video");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JLabel vidLabel = new JLabel();
        frame.setContentPane(vidLabel);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        
        Mat frameMat = new Mat();
        int frameCount = 0;
        int panneauId = 1;
        
        File dossier = new File(dossierSortie);
        if (dossier.exists()) {
            for (File f : dossier.listFiles()) {
                f.delete();
            }
        }
        
        panneauxExtraits.clear();
        
        System.out.println("========================================");
        System.out.println("DETECTION SUR VIDEO");
        System.out.println("========================================");
        System.out.println("Video: " + cheminVideo);
        System.out.println();
        
        int derniereFrameExtraction = 0;
        
        while (camera.read(frameMat)) {
            frameCount++;
            if (frameMat.empty()) continue;
            
            Mat frameAffichee = frameMat.clone();
            int largeur = frameMat.cols();
            int milieu = largeur / 2;
            
            // Analyser toutes les 15 frames
            if (frameCount % 15 == 0 && frameCount - derniereFrameExtraction >= DELAI_MIN_FRAMES) {
                
                Mat hsv = new Mat();
                Imgproc.cvtColor(frameMat, hsv, Imgproc.COLOR_BGR2HSV);
                
                Mat mask1 = new Mat();
                Mat mask2 = new Mat();
                Core.inRange(hsv, new Scalar(0, 70, 50), new Scalar(15, 255, 255), mask1);  // Seuils élargis
                Core.inRange(hsv, new Scalar(158, 70, 50), new Scalar(180, 255, 255), mask2); // Seuils élargis
                
                Mat maskRed = new Mat();
                Core.bitwise_or(mask1, mask2, maskRed);
                
                int height = frameMat.rows();
                int width = frameMat.cols();
                
                Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
                Imgproc.morphologyEx(maskRed, maskRed, Imgproc.MORPH_CLOSE, kernel);
                Imgproc.morphologyEx(maskRed, maskRed, Imgproc.MORPH_OPEN, kernel);
                
                List<MatOfPoint> contours = new ArrayList<>();
                Mat hierarchy = new Mat();
                Imgproc.findContours(maskRed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
                
                // Stocker tous les panneaux valides
                List<Detection> panneauxValides = new ArrayList<>();
                
                for (MatOfPoint contour : contours) {
                    double area = Imgproc.contourArea(contour);
                    if (area < 80 || area > 6000) continue;  // Seuil abaissé
                    
                    MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
                    double perimeter = Imgproc.arcLength(contour2f, true);
                    if (perimeter == 0) continue;
                    
                    double circularity = 4 * Math.PI * area / (perimeter * perimeter);
                    contour2f.release();
                    
                    if (circularity < 0.5) continue;  // Seuil abaissé
                    
                    Rect rect = Imgproc.boundingRect(contour);
                    double ratio = (double) rect.width / rect.height;
                    if (ratio < 0.6 || ratio > 1.5) continue;  // Tolérance élargie
                    
                    int cx = rect.x + rect.width / 2;
                    int cy = rect.y + rect.height / 2;
                    int r = Math.max(rect.width, rect.height) / 2;
                    
                    if (cy < height * 0.1 || cy > height * 0.9) continue;  // Zone élargie
                    if (r < 15 || r > 100) continue;  // Taille élargie
                    
                    int x1 = Math.max(0, cx - r - 5);
                    int y1 = Math.max(0, cy - r - 5);
                    int x2 = Math.min(width, cx + r + 5);
                    int y2 = Math.min(height, cy + r + 5);
                    
                    if (x2 <= x1 || y2 <= y1) continue;
                    
                    Mat roi = frameMat.submat(new Rect(x1, y1, x2 - x1, y2 - y1));
                    if (roi.empty()) continue;
                    
                    int localCx = cx - x1;
                    int localCy = cy - y1;
                    
                    Mat hsvRoi = new Mat();
                    Imgproc.cvtColor(roi, hsvRoi, Imgproc.COLOR_BGR2HSV);
                    Mat whiteMask = new Mat();
                    Core.inRange(hsvRoi, new Scalar(0, 0, 80), new Scalar(180, 100, 255), whiteMask);  // Seuil blanc abaissé
                    
                    int centerR = Math.max(8, r / 3);
                    int centerCx = roi.cols() / 2;
                    int centerCy = roi.rows() / 2;
                    
                    Mat centre = Mat.zeros(roi.rows(), roi.cols(), CvType.CV_8UC1);
                    Imgproc.circle(centre, new Point(centerCx, centerCy), centerR, new Scalar(255), -1);
                    
                    Mat whiteCentre = new Mat();
                    Core.bitwise_and(whiteMask, centre, whiteCentre);
                    double whiteRatio = Core.countNonZero(whiteCentre) / (Math.PI * centerR * centerR);
                    
                    Mat ringMask = Mat.zeros(roi.rows(), roi.cols(), CvType.CV_8UC1);
                    Imgproc.circle(ringMask, new Point(centerCx, centerCy), (int)(r * 0.9), new Scalar(255), -1);
                    Imgproc.circle(ringMask, new Point(centerCx, centerCy), (int)(r * 0.4), new Scalar(0), -1);
                    
                    Mat roiRed = maskRed.submat(new Rect(x1, y1, x2 - x1, y2 - y1));
                    Mat redRing = new Mat();
                    Core.bitwise_and(roiRed, ringMask, redRing);
                    
                    double ringArea = Math.PI * (r*0.9)*(r*0.9) - Math.PI * (r*0.4)*(r*0.4);
                    double redRingRatio = (ringArea > 0) ? Core.countNonZero(redRing) / ringArea : 0;
                    
                    boolean aDesChiffres = aDesChiffres(roi);
                    double validationScore = (whiteRatio * 0.4) + (redRingRatio * 0.4);
                    if (aDesChiffres) validationScore += 0.2;
                    
                    // Seuils assouplis pour les panneaux latéraux
                    boolean estValide = (whiteRatio > 0.08 && whiteRatio < 0.80 && 
                                        redRingRatio > 0.08 && validationScore > 0.22 &&
                                        aDesChiffres);
                    
                    if (estValide) {
                        Detection detection = new Detection();
                        detection.cx = cx;
                        detection.cy = cy;
                        detection.r = r;
                        detection.x1 = x1;
                        detection.y1 = y1;
                        detection.x2 = x2;
                        detection.y2 = y2;
                        detection.localCx = localCx;
                        detection.localCy = localCy;
                        detection.whiteRatio = whiteRatio;
                        detection.redRingRatio = redRingRatio;
                        detection.roi = roi.clone();
                        detection.aDesChiffres = aDesChiffres;
                        detection.score = validationScore;
                        detection.zone = (cx < milieu - 70) ? "GAUCHE" : ((cx > milieu + 70) ? "DROITE" : "CENTRE");
                        panneauxValides.add(detection);
                    }
                    
                    roi.release();
                    hsvRoi.release();
                    whiteMask.release();
                    centre.release();
                    whiteCentre.release();
                    ringMask.release();
                    roiRed.release();
                    redRing.release();
                }
                
                hsv.release();
                mask1.release();
                mask2.release();
                maskRed.release();
                kernel.release();
                hierarchy.release();
                
                // Extraire les panneaux (un par zone maximum)
                Detection meilleurGauche = null, meilleurDroite = null, meilleurCentre = null;
                double scoreGauche = -1, scoreDroite = -1, scoreCentre = -1;
                
                for (Detection d : panneauxValides) {
                    if (d.zone.equals("GAUCHE") && d.score > scoreGauche) {
                        scoreGauche = d.score;
                        meilleurGauche = d;
                    } else if (d.zone.equals("DROITE") && d.score > scoreDroite) {
                        scoreDroite = d.score;
                        meilleurDroite = d;
                    } else if (d.zone.equals("CENTRE") && d.score > scoreCentre) {
                        scoreCentre = d.score;
                        meilleurCentre = d;
                    }
                }
                
                // Extraire les panneaux
                if (meilleurGauche != null && !estDejaExtrait(meilleurGauche.cx, meilleurGauche.cy)) {
                    extrairePanneau(meilleurGauche, panneauId++, frameCount, dossierSortie, frameAffichee);
                    derniereFrameExtraction = frameCount;
                }
                if (meilleurCentre != null && !estDejaExtrait(meilleurCentre.cx, meilleurCentre.cy)) {
                    extrairePanneau(meilleurCentre, panneauId++, frameCount, dossierSortie, frameAffichee);
                    derniereFrameExtraction = frameCount;
                }
                if (meilleurDroite != null && !estDejaExtrait(meilleurDroite.cx, meilleurDroite.cy)) {
                    extrairePanneau(meilleurDroite, panneauId++, frameCount, dossierSortie, frameAffichee);
                    derniereFrameExtraction = frameCount;
                }
            }
            
            ImageIcon icon = new ImageIcon(matToBufferedImage(frameAffichee));
            vidLabel.setIcon(icon);
            vidLabel.repaint();
            frameAffichee.release();
            
            try { Thread.sleep(30); } catch (InterruptedException e) {}
        }
        
        camera.release();
        System.out.println("\n========================================");
        System.out.println("Analyse video terminee.");
        System.out.println("Panneaux extraits: " + (panneauId - 1));
        System.out.println("========================================");
    }
    
    private static void extrairePanneau(Detection detection, int panneauId, int frameCount, 
                                        String dossierSortie, Mat frameAffichee) {
        panneauxExtraits.add(new PanneauExtrait(detection.cx, detection.cy));
        
        String nomFichier = dossierSortie + "/panneau_video_" + panneauId + ".jpg";
        Imgcodecs.imwrite(nomFichier, detection.roi);
        
        String jsonPath = dossierSortie + "/panneau_video_" + panneauId + "_infos.json";
        sauvegarderInfos(jsonPath, panneauId, detection.cx, detection.cy, 
                        detection.r, detection.x1, detection.y1,
                        detection.x2, detection.y2, detection.localCx,
                        detection.localCy, frameCount, detection.whiteRatio, 
                        detection.redRingRatio);
        
        System.out.println("[Frame " + frameCount + "] Panneau video " + panneauId + 
                         " extrait (" + detection.zone + " à " + detection.cx + "," + detection.cy + 
                         ", score: " + String.format("%.3f", detection.score) + ")");
        
        // Dessiner sur l'image
        Imgproc.rectangle(frameAffichee, new Point(detection.x1, detection.y1), 
                        new Point(detection.x2, detection.y2), new Scalar(0, 255, 0), 3);
        Imgproc.circle(frameAffichee, new Point(detection.cx, detection.cy), 
                     detection.r, new Scalar(255, 0, 0), 2);
        
        detection.roi.release();
    }
    
    static class PanneauExtrait {
        int cx, cy;
        PanneauExtrait(int cx, int cy) {
            this.cx = cx;
            this.cy = cy;
        }
    }
    
    static class Detection {
        int cx, cy, r;
        int x1, y1, x2, y2;
        int localCx, localCy;
        double whiteRatio, redRingRatio, score;
        boolean aDesChiffres;
        String zone;
        Mat roi;
    }
    
    public static void main(String[] args) {
        String dossierVideos = "ressources/videos";
        String dossierExtraits = "ressources/video_extraits";
        String dossierControle = "ressources/video_controle";
        
        // Pour la première vidéo (panneau central)
       detecterPanneauxVideo(dossierVideos + "/video1.mp4", dossierExtraits, dossierControle);
        
        // Pour la deuxième vidéo (panneaux gauche et droite)
       // detecterPanneauxVideo(dossierVideos + "/video2.mp4", dossierExtraits, dossierControle);
    }
}
