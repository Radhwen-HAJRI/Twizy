package activite1;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

public class Activite1 {
    
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }
    
    public static void main(String[] args) {
        String cheminImage = "ressources/images/activite1.png";
        Mat image = Imgcodecs.imread(cheminImage);
        
        if (image.empty()) {
            System.out.println("Erreur: Impossible de charger l'image");
            return;
        }
        
        System.out.println("Détection des pixels rouges:");
        System.out.println("+ = rouge, . = autre\n");
        
        for (int y = 0; y < image.rows(); y++) {
            for (int x = 0; x < image.cols(); x++) {
                double[] pixel = image.get(y, x);
                double bleu = pixel[0];
                double vert = pixel[1];
                double rouge = pixel[2];
                
                if (rouge > 150 && vert < 100 && bleu < 100) {
                    System.out.print("+");
                } else {
                    System.out.print(".");
                }
            }
            System.out.println();
        }
    }
}