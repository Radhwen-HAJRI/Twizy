package activite3;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class Activite3 {
    
    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }
    
    public static void main(String[] args) {
        System.out.println("=== ACTIVITE 3 - Conversion BGR vers HSV ===");
        
        String cheminImage = "ressources/images/p0.jpg";
        Mat imageOriginale = Imgcodecs.imread(cheminImage);
        
        if (imageOriginale.empty()) {
            System.out.println("ERREUR: Impossible de charger l'image");
            return;
        }
        
        System.out.println("Image chargee: " + imageOriginale.cols() + " x " + imageOriginale.rows());
        
        Mat imageHSV = new Mat();
        Imgproc.cvtColor(imageOriginale, imageHSV, Imgproc.COLOR_BGR2HSV);
        
        String cheminHSV = "ressources/resultats/p0_HSV.png";
        Imgcodecs.imwrite(cheminHSV, imageHSV);
        System.out.println("Image HSV sauvegardee: " + cheminHSV);
        
        HighGui.imshow("Image Originale (BGR)", imageOriginale);
        HighGui.imshow("Image en HSV", imageHSV);
        
        System.out.println("Appuyez sur une touche pour fermer...");
        HighGui.waitKey(0);
        HighGui.destroyAllWindows();
        
        System.out.println("Programme termine.");
    }
}