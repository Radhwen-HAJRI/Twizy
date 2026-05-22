import java.sql.*;
import java.time.LocalDateTime;

public class DatabaseManager {

    private static final String URL      = "jdbc:mysql://localhost:3306/twizzy";
    private static final String USER     = "root";
    private static final String PASSWORD = "root123"; // Mets ton mot de passe ici

    private static Connection connection = null;

    // ── Connexion ─────────────────────────────────────────────────────────────
    public static void connect() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("✓ MySQL connecté");
        } catch (Exception e) {
            System.err.println("⚠ MySQL non disponible : " + e.getMessage());
            connection = null;
        }
    }

    // ── Insertion détection ───────────────────────────────────────────────────
    public static void saveDetection(String modele, String classe, float confiance, String imagePath) {
        if (connection == null) return;
        try {
            String sql = "INSERT INTO detections (modele, classe_detectee, confiance, image_path) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(sql);
            stmt.setString(1, modele);
            stmt.setString(2, classe);
            stmt.setFloat(3, confiance);
            stmt.setString(4, imagePath);
            stmt.executeUpdate();
            stmt.close();
        } catch (SQLException e) {
            System.err.println("⚠ Erreur insertion : " + e.getMessage());
        }
    }

    // ── Fermeture ─────────────────────────────────────────────────────────────
    public static void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("✓ MySQL déconnecté");
            }
        } catch (SQLException e) {
            System.err.println("⚠ Erreur déconnexion : " + e.getMessage());
        }
    }
}
