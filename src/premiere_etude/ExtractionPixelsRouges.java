package premiere_etude;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class ExtractionPixelsRouges {
    
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }
    
    /**
     * Méthode de seuillage pour détecter les pixels rouges dans l'espace HSV
     * @param input Image d'entrée (HSV)
     * @param seuilRougeOrange Seuil bas pour le rouge-orange (0-6)
     * @param seuilRougeViolet Seuil haut pour le rouge-violet (170-180)
     * @param seuilSaturation Seuil minimum de saturation (110)
     * @return Masque binaire (blanc = rouge, noir = autre)
     */
    public static Mat seuillageRouge(Mat input, int seuilRougeOrange, int seuilRougeViolet, int seuilSaturation) {
        
        // Premier masque pour le rouge-orangé (teinte basse: 0 à seuilRougeOrange)
        Mat mask1 = new Mat();
        Core.inRange(input, new Scalar(0, seuilSaturation, 50), new Scalar(seuilRougeOrange, 255, 255), mask1);
        
        // Deuxième masque pour le rouge-violet (teinte haute: seuilRougeViolet à 180)
        Mat mask2 = new Mat();
        Core.inRange(input, new Scalar(seuilRougeViolet, seuilSaturation, 50), new Scalar(180, 255, 255), mask2);
        
        // Fusion des deux masques
        Mat result = new Mat();
        Core.bitwise_or(mask1, mask2, result);
        
        // Nettoyage morphologique
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        Imgproc.morphologyEx(result, result, Imgproc.MORPH_CLOSE, kernel);
        Imgproc.morphologyEx(result, result, Imgproc.MORPH_OPEN, kernel);
        
        // Libération mémoire
        mask1.release();
        mask2.release();
        kernel.release();
        
        return result;
    }
    
    /**
     * Convertit une image BGR en HSV
     */
    public static Mat convertirBGRversHSV(Mat imageBGR) {
        Mat imageHSV = new Mat();
        Imgproc.cvtColor(imageBGR, imageHSV, Imgproc.COLOR_BGR2HSV);
        return imageHSV;
    }
    
    public static void main(String[] args) {
        
        System.out.println("=========================================");
        System.out.println("PREMIERE ETUDE - Extraction des pixels rouges");
        System.out.println("=========================================");
        
        // Chemin de l'image à traiter
        String cheminImage = "ressources/images/p1.jpg";
        
        // 1. Charger l'image
        Mat imageOriginale = Imgcodecs.imread(cheminImage);
        
        if (imageOriginale.empty()) {
            System.out.println("Erreur: Impossible de charger l'image " + cheminImage);
            return;
        }
        
        System.out.println("Image chargee: " + imageOriginale.cols() + " x " + imageOriginale.rows());
        
        // 2. Convertir BGR vers HSV
        Mat imageHSV = convertirBGRversHSV(imageOriginale);
        
        // 3. Paramètres de seuillage (selon l'énoncé)
        //    - Rouge-orangé: teinte 0-6
        //    - Rouge-violet: teinte 170-180
        //    - Saturation minimale: 110
        int seuilRougeOrange = 6;
        int seuilRougeViolet = 170;
        int seuilSaturation = 110;
        
        System.out.println("\nParametres de seuillage:");
        System.out.println("  - Rouge-orange (teinte basse): 0 - " + seuilRougeOrange);
        System.out.println("  - Rouge-violet (teinte haute): " + seuilRougeViolet + " - 180");
        System.out.println("  - Saturation minimale: " + seuilSaturation);
        
        // 4. Appliquer le seuillage pour extraire les pixels rouges
        Mat masqueRouge = seuillageRouge(imageHSV, seuilRougeOrange, seuilRougeViolet, seuilSaturation);
        
        // 5. Compter les pixels rouges détectés
        long pixelsBlancs = Core.countNonZero(masqueRouge);
        long totalPixels = masqueRouge.rows() * masqueRouge.cols();
        double pourcentage = (pixelsBlancs * 100.0) / totalPixels;
        
        System.out.println("\nResultats:");
        System.out.println("  - Pixels rouges detectes (blancs): " + pixelsBlancs);
        System.out.println("  - Pourcentage de rouge: " + String.format("%.2f", pourcentage) + "%");
        
        // 6. Sauvegarder le masque (image en noir et blanc)
        String cheminSortie = "ressources/resultats/masque_rouge.png";
        Imgcodecs.imwrite(cheminSortie, masqueRouge);
        System.out.println("  - Masque sauvegarde: " + cheminSortie);
        
        // 7. Sauvegarder également l'image saturée (application du masque sur l'originale)
        Mat imageSaturee = new Mat();
        imageOriginale.copyTo(imageSaturee, masqueRouge);
        String cheminSaturee = "ressources/resultats/image_saturee.png";
        Imgcodecs.imwrite(cheminSaturee, imageSaturee);
        System.out.println("  - Image saturee sauvegardee: " + cheminSaturee);
        
        System.out.println("\n=========================================");
        System.out.println("Traitement termine.");
        System.out.println("=========================================");
        
        // Libération mémoire
        imageOriginale.release();
        imageHSV.release();
        masqueRouge.release();
        imageSaturee.release();
    }
}