package activite2;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

public class Activite2 {
    
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }
    
    public static void main(String[] args) {
        String cheminImage = "ressources/images/bgr.png";
        Mat image = Imgcodecs.imread(cheminImage);
        
        if (image.empty()) {
            System.out.println("Erreur: Impossible de charger l'image");
            return;
        }
        
        System.out.println("Extraction du canal vert...");
        
        Mat canalVert = new Mat();
        Core.extractChannel(image, canalVert, 1);
        
        String sortie = "ressources/resultats/canal_vert.png";
        Imgcodecs.imwrite(sortie, canalVert);
        
        System.out.println("Image sauvegardée: " + sortie);
    }
}