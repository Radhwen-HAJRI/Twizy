import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.json.JSONObject;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

public class VideoDetectionWindow extends JFrame {

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color PRIMARY_COLOR = new Color(41, 128, 185);
    private static final Color ACCENT_COLOR  = new Color(39, 174,  96);
    private static final Color BG_COLOR      = new Color(245, 247, 250);
    private static final Color SIDEBAR_COLOR = new Color(30,  40,  55);
    private static final Color SIDEBAR_HOVER = new Color(41,  55,  75);
    private static final Color TEXT_LIGHT    = new Color(220, 225, 235);
    private static final Color TEXT_DARK     = new Color(44,  62,  80);
    private static final Color CARD_BG       = Color.WHITE;
    private static final Color BORDER_COLOR  = new Color(210, 215, 220);
    private static final Color SPEED_BG      = new Color(50,  50,  70);
    private static final Color SPEED_FG      = new Color(255, 200, 50);

    // ── VLC & media ───────────────────────────────────────────────────────────
    private EmbeddedMediaPlayerComponent mediaPlayerComponent;
    private boolean vlcAvailable = false;

    // ── UI ────────────────────────────────────────────────────────────────────
    private JPanel  videoSurface;
    private JPanel  detectedVideoSurface;
    private JSlider progressSlider;
    private JLabel  detectionLabel;
    private JLabel  statusLabel;
    private JLabel  speedDisplayLabel;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean isPlaying = false;
    private Timer   progressTimer;
    private Timer   apiRequestTimer;
    private static final int API_REQUEST_INTERVAL = 800;  // ms entre chaque appel API

    // ── Cohérence vitesse ─────────────────────────────────────────────────────
    private String lastDetectedClass = null;

    // ── Verrou anti-double-appel API ─────────────────────────────────────────
    private volatile boolean apiCallInProgress = false;

    // ── Fichier snapshot temporaire ───────────────────────────────────────────
    private File snapshotFile;

    // ══════════════════════════════════════════════════════════════════════════
    public VideoDetectionWindow() {
        super("Détection Vidéo — Reconnaissance de Panneaux");
        try {
            snapshotFile = File.createTempFile("vlc_snap_", ".jpg");
            snapshotFile.deleteOnExit();
        } catch (IOException e) {
            System.err.println("Impossible de créer le fichier snapshot temp.");
        }
        setupFrame();
        buildUI();
        initVLC();
        progressTimer = new Timer(1000, e -> updateProgress());
        if (!vlcAvailable) showVlcWarning();
    }

    private void setupFrame() {
        setSize(1200, 880);
        setMinimumSize(new Dimension(950, 750));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_COLOR);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UI
    // ══════════════════════════════════════════════════════════════════════════
    private void buildUI() {
        add(buildSidebar(),   BorderLayout.WEST);
        add(buildContent(),   BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel buildSidebar() {
        JPanel sb = new JPanel();
        sb.setBackground(SIDEBAR_COLOR);
        sb.setPreferredSize(new Dimension(220, 0));
        sb.setLayout(new BoxLayout(sb, BoxLayout.Y_AXIS));

        JPanel logo = new JPanel();
        logo.setBackground(new Color(20, 28, 42));
        logo.setMaximumSize(new Dimension(220, 75));
        logo.setLayout(new BoxLayout(logo, BoxLayout.Y_AXIS));
        logo.setBorder(BorderFactory.createEmptyBorder(16, 14, 16, 14));
        JLabel t1 = new JLabel("  Détection Vidéo");
        t1.setFont(new Font("Arial", Font.BOLD, 15));
        t1.setForeground(Color.WHITE);
        JLabel t2 = new JLabel("Panneaux Routiers (YOLO)");
        t2.setFont(new Font("Arial", Font.PLAIN, 11));
        t2.setForeground(new Color(150, 160, 180));
        logo.add(t1); logo.add(Box.createVerticalStrut(3)); logo.add(t2);
        sb.add(logo);

        sb.add(sectionLabel("CONTRÔLES"));
        sb.add(sidebarBtn("►  Ouvrir une vidéo",  ACCENT_COLOR,           e -> openVideo()));
        sb.add(sidebarBtn("▶  Lecture / Pause",    PRIMARY_COLOR,          e -> togglePlay()));
        sb.add(sidebarBtn("■  Stop",               new Color(150, 50, 50), e -> stopVideo()));

        sb.add(Box.createVerticalStrut(12));
        sb.add(sectionLabel("PROGRESSION"));
        progressSlider = new JSlider(0, 100, 0);
        progressSlider.setEnabled(false);
        progressSlider.setBackground(SIDEBAR_COLOR);
        progressSlider.setForeground(TEXT_LIGHT);
        progressSlider.setMaximumSize(new Dimension(190, 30));
        progressSlider.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
        progressSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressSlider.addChangeListener(e -> {
            if (!progressSlider.getValueIsAdjusting() && mediaPlayerComponent != null)
                mediaPlayerComponent.mediaPlayer().controls()
                        .setPosition(progressSlider.getValue() / 100.0f);
        });
        sb.add(progressSlider);

        sb.add(Box.createVerticalGlue());
        JLabel footer = new JLabel("Les 4 Fantastiques © 2025");
        footer.setFont(new Font("Arial", Font.PLAIN, 10));
        footer.setForeground(new Color(100, 110, 130));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 14, 12, 0));
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        sb.add(footer);
        return sb;
    }

    private JLabel sectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Arial", Font.BOLD, 10));
        lbl.setForeground(new Color(100, 115, 140));
        lbl.setBorder(BorderFactory.createEmptyBorder(16, 14, 6, 0));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setMaximumSize(new Dimension(220, 28));
        return lbl;
    }

    private JPanel sidebarBtn(String text, Color accent, ActionListener action) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(SIDEBAR_COLOR);
        wrapper.setMaximumSize(new Dimension(220, 42));
        wrapper.setPreferredSize(new Dimension(220, 42));
        JPanel bar = new JPanel();
        bar.setBackground(SIDEBAR_COLOR);
        bar.setPreferredSize(new Dimension(3, 42));
        JButton btn = new JButton(text);
        btn.setFont(new Font("Arial", Font.PLAIN, 13));
        btn.setForeground(TEXT_LIGHT);
        btn.setBackground(SIDEBAR_COLOR);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 8));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(SIDEBAR_HOVER); bar.setBackground(accent); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(SIDEBAR_COLOR); bar.setBackground(SIDEBAR_COLOR); }
        });
        btn.addActionListener(action);
        wrapper.add(bar, BorderLayout.WEST);
        wrapper.add(btn, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBackground(BG_COLOR);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel videosPanel = new JPanel(new GridLayout(2, 1, 0, 8));
        videosPanel.setBackground(BG_COLOR);

        JPanel topCard = buildCard("Vidéo originale");
        videoSurface = new JPanel(new BorderLayout());
        videoSurface.setBackground(Color.BLACK);
        topCard.add(videoSurface, BorderLayout.CENTER);
        videosPanel.add(topCard);

        JPanel botCard = buildCard("Vidéo avec détections YOLO");
        detectedVideoSurface = new JPanel(new BorderLayout());
        detectedVideoSurface.setBackground(Color.BLACK);
        botCard.add(detectedVideoSurface, BorderLayout.CENTER);
        videosPanel.add(botCard);

        content.add(videosPanel, BorderLayout.CENTER);

        // Panneau droit — bandeau de détection
        JPanel rightPanel = new JPanel(new BorderLayout(0, 10));
        rightPanel.setBackground(BG_COLOR);
        rightPanel.setPreferredSize(new Dimension(260, 0));

        // Carte principale : bandeau élégant
        JPanel detectionCard = new JPanel(new BorderLayout(0, 0));
        detectionCard.setBackground(CARD_BG);
        detectionCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT_COLOR, 2, true),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));

        // En-tête de la carte
        JPanel cardHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 10));
        cardHeader.setBackground(new Color(39, 174, 96, 30)); // vert très léger
        cardHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(39, 174, 96, 80)));
        JLabel headerIcon = new JLabel("●  Résultat de la détection");
        headerIcon.setFont(new Font("Arial", Font.BOLD, 12));
        headerIcon.setForeground(new Color(30, 120, 60));
        cardHeader.add(headerIcon);
        detectionCard.add(cardHeader, BorderLayout.NORTH);

        // Zone centrale : texte du panneau détecté
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(CARD_BG);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(18, 14, 18, 14));

        speedDisplayLabel = new JLabel("<html><center>En attente<br>de détection</center></html>", SwingConstants.CENTER);
        speedDisplayLabel.setFont(new Font("Georgia", Font.BOLD, 20));
        speedDisplayLabel.setForeground(TEXT_DARK);
        speedDisplayLabel.setBackground(CARD_BG);
        speedDisplayLabel.setOpaque(false);
        centerPanel.add(speedDisplayLabel);
        detectionCard.add(centerPanel, BorderLayout.CENTER);

        // Pied de carte : confiance / statut
        detectionLabel = new JLabel("Aucune détection en cours", SwingConstants.CENTER);
        detectionLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        detectionLabel.setForeground(new Color(140, 150, 165));
        detectionLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 225, 230)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        detectionCard.add(detectionLabel, BorderLayout.SOUTH);

        rightPanel.add(detectionCard, BorderLayout.NORTH);
        content.add(rightPanel, BorderLayout.EAST);
        return content;
    }

    private JPanel buildCard(String title) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        header.setBackground(new Color(248, 250, 252));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("Arial", Font.BOLD, 13));
        lbl.setForeground(TEXT_DARK);
        header.add(lbl);
        card.add(header, BorderLayout.NORTH);
        return card;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(230, 235, 240));
        bar.setPreferredSize(new Dimension(0, 26));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(0, 16, 0, 16)));
        statusLabel = new JLabel("Prêt. Choisissez une vidéo pour commencer.");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        statusLabel.setForeground(new Color(100, 110, 130));
        bar.add(statusLabel, BorderLayout.WEST);
        return bar;
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  VLC
    // ══════════════════════════════════════════════════════════════════════════
    private void initVLC() {
        try {
            boolean found = new NativeDiscovery().discover();
            if (!found) { vlcAvailable = false; return; }
            mediaPlayerComponent = new EmbeddedMediaPlayerComponent();
            mediaPlayerComponent.setPreferredSize(new Dimension(800, 340));
            videoSurface.removeAll();
            videoSurface.setLayout(new BorderLayout());
            videoSurface.add(mediaPlayerComponent, BorderLayout.CENTER);
            videoSurface.revalidate();
            videoSurface.repaint();
            vlcAvailable = true;
        } catch (Exception | UnsatisfiedLinkError e) {
            vlcAvailable = false;
        }
    }

    private void showVlcWarning() {
        videoSurface.removeAll();
        videoSurface.setLayout(new BorderLayout());
        JLabel warn = new JLabel(
                "<html><center><b style='font-size:14px'>VLC non trouvé</b><br><br>" +
                "VLC Media Player (64 bits) doit être installé.<br>" +
                "Téléchargez-le sur <b>videolan.org</b>.</center></html>",
                SwingConstants.CENTER);
        warn.setForeground(new Color(180, 60, 60));
        videoSurface.add(warn, BorderLayout.CENTER);
        videoSurface.revalidate();
        setStatus("⚠ VLC introuvable.");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  COMMANDES VIDÉO
    // ══════════════════════════════════════════════════════════════════════════
    private void openVideo() {
        if (!vlcAvailable) {
            JOptionPane.showMessageDialog(this,
                    "<html><b>VLC non disponible.</b><br>Installez VLC 64 bits.</html>",
                    "VLC requis", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter(
                "Vidéos (*.mp4, *.avi, *.mkv, *.mov)", "mp4", "avi", "mkv", "mov"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File video = fc.getSelectedFile();
            try {
                stopVideo();
                mediaPlayerComponent.mediaPlayer().media().play(video.getAbsolutePath());
                progressSlider.setEnabled(true);
                progressSlider.setValue(0);
                isPlaying = true;
                progressTimer.start();
                startDetection();
                setStatus("Lecture : " + video.getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur : " + ex.getMessage(),
                        "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void togglePlay() {
        if (!vlcAvailable || mediaPlayerComponent == null) return;
        if (isPlaying) {
            mediaPlayerComponent.mediaPlayer().controls().pause();
            progressTimer.stop();
            if (apiRequestTimer != null) apiRequestTimer.stop();
            isPlaying = false;
            setStatus("Pause.");
        } else {
            mediaPlayerComponent.mediaPlayer().controls().play();
            progressTimer.start();
            startDetection();
            isPlaying = true;
            setStatus("Lecture en cours...");
        }
    }

    private void stopVideo() {
        if (mediaPlayerComponent != null)
            mediaPlayerComponent.mediaPlayer().controls().stop();
        progressTimer.stop();
        if (apiRequestTimer != null) apiRequestTimer.stop();
        isPlaying = false;
        apiCallInProgress = false;
        progressSlider.setValue(0);
        progressSlider.setEnabled(false);
        detectionLabel.setText("Aucune détection en cours");
        lastDetectedClass = null;
        speedDisplayLabel.setText("<html><center><span style='font-size:13px;color:#999999;"
                + "font-style:italic'>En attente<br>de détection</span></center></html>");
        setStatus("Arrêt.");
    }

    private void updateProgress() {
        if (mediaPlayerComponent != null && isPlaying) {
            float pos = mediaPlayerComponent.mediaPlayer().status().position();
            progressSlider.setValue((int)(pos * 100));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DÉTECTION — MÉTHODE CORRIGÉE
    //
    //  PROBLÈME ORIGINAL :
    //  1. snapshots().get() retourne null très souvent (asynchrone/non fiable)
    //  2. L'appel API était bloquant sur l'EDT → gèle l'interface
    //  3. On envoyait une sous-image (ROI) alors que YOLO attend l'image entière
    //
    //  SOLUTION :
    //  - On demande à VLC de sauvegarder un snapshot sur disque (saveSnapshot)
    //    puis on lit ce fichier → beaucoup plus fiable que get()
    //  - L'appel HTTP se fait dans un thread séparé (non-bloquant)
    //  - On envoie l'image complète à l'API YOLO, qui dessine elle-même les boîtes
    // ══════════════════════════════════════════════════════════════════════════
    private void startDetection() {
        if (apiRequestTimer != null) apiRequestTimer.stop();

        apiRequestTimer = new Timer(API_REQUEST_INTERVAL, e -> {
            // Ne pas empiler les appels si le précédent n'est pas terminé
            if (!isPlaying || apiCallInProgress) return;
            captureAndSendToAPI();
        });
        apiRequestTimer.start();
    }

    /**
     * Capture une frame via VLC (snapshot sur disque) puis envoie à l'API.
     * Tout se passe dans un thread séparé pour ne pas bloquer l'UI.
     */
    private void captureAndSendToAPI() {
        apiCallInProgress = true;

        new Thread(() -> {
            try {
                // ── Étape 1 : demander à VLC de sauvegarder un snapshot ────────
                // saveSnapshot() est synchrone côté VLC : il écrit le fichier sur disque
                // puis retourne. C'est BEAUCOUP plus fiable que snapshots().get().
                boolean saved = mediaPlayerComponent.mediaPlayer()
                        .snapshots().save(snapshotFile);

                if (!saved || !snapshotFile.exists() || snapshotFile.length() == 0) {
                    System.out.println("[SNAP] Snapshot non disponible, on réessaie au prochain tick.");
                    apiCallInProgress = false;
                    return;
                }

                // Petite pause pour s'assurer que VLC a fini d'écrire le fichier
                Thread.sleep(80);

                // Vérifier que le fichier est lisible
                BufferedImage testRead = ImageIO.read(snapshotFile);
                if (testRead == null) {
                    System.out.println("[SNAP] Fichier snapshot illisible.");
                    apiCallInProgress = false;
                    return;
                }

                // ── Étape 2 : envoyer l'image COMPLÈTE à l'API YOLO ───────────
                // On envoie l'image entière (pas une ROI) : YOLO détecte et renvoie
                // l'image annotée avec les boîtes.
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:5000/predict"))
                        .header("Content-Type", "image/jpeg")
                        .POST(HttpRequest.BodyPublishers.ofFile(snapshotFile.toPath()))
                        .build();

                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    System.err.println("[API] Erreur HTTP " + response.statusCode());
                    apiCallInProgress = false;
                    return;
                }

                // ── Étape 3 : parser la réponse JSON ─────────────────────────
                JSONObject json = new JSONObject(response.body());
                String detectedClass = json.optString("class", "inconnu");
                String base64Img     = json.optString("image", null);

                // Formatter le label YOLO en texte lisible ("speed_50" → "Vitesse limite : 50 km/h")
                final String finalClass = formatLabel(detectedClass);

                // Décoder l'image annotée renvoyée par l'API
                BufferedImage annotated = null;
                if (base64Img != null && !base64Img.isEmpty()) {
                    byte[] decoded = Base64.getDecoder().decode(base64Img);
                    annotated = ImageIO.read(new ByteArrayInputStream(decoded));
                }
                final BufferedImage finalAnnotated = annotated;

                // ── Étape 4 : mettre à jour l'UI sur l'EDT ────────────────────
                SwingUtilities.invokeLater(() -> {
                    // Afficher directement le label formaté dans le cadre vert
                    String displayText = "<html><center><span style='font-family:Georgia;font-size:20px;"
                            + "font-weight:bold;color:#1a7a3c'>" + finalClass + "</span></center></html>";
                    speedDisplayLabel.setText(displayText);
                    // Pied de carte : classe brute YOLO pour référence
                    detectionLabel.setText("Classe YOLO : " + detectedClass);
                    detectionLabel.setForeground(new Color(110, 120, 140));
                    detectionLabel.setVisible(true);

                    if (finalAnnotated != null) {
                        int w = detectedVideoSurface.getWidth();
                        int h = detectedVideoSurface.getHeight();
                        if (w > 0 && h > 0) {
                            Image scaled = finalAnnotated.getScaledInstance(w, h, Image.SCALE_FAST);
                            JLabel imgLabel = new JLabel(new ImageIcon(scaled));
                            detectedVideoSurface.removeAll();
                            detectedVideoSurface.add(imgLabel, BorderLayout.CENTER);
                            detectedVideoSurface.revalidate();
                            detectedVideoSurface.repaint();
                        }
                    }
                    setStatus("Détection : " + finalClass);
                });

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                System.err.println("[DETECT] Erreur : " + ex.getMessage());
            } finally {
                apiCallInProgress = false;
            }
        }).start();
    }

    private String formatLabel(String raw) {
        if (raw == null) return "Inconnu";
        String lower = raw.toLowerCase().trim();
        if (lower.startsWith("speed_")) {
            String num = lower.replace("speed_", "").replaceAll("[^0-9]", "");
            return "Vitesse limite : " + num + " km/h";
        }
        switch (lower) {
            case "stop":                   return "STOP";
            case "give_way": case "yield": return "Cédez le passage";
            case "no_entry":               return "Sens interdit";
            case "no_parking":             return "Interdiction de stationner";
            case "pedestrian":             return "Passage piéton";
            case "roundabout":             return "Rond-point obligatoire";
            default:
                String s = raw.replace("_", " ");
                return s.substring(0, 1).toUpperCase() + s.substring(1);
        }
    }

    private String extractSpeed(String label) {
        if (label == null) return null;
        String lower = label.toLowerCase().trim();

        // Format exact YOLO : "speed_50", "speed_70", "speed_110" etc.
        // On extrait UNIQUEMENT les chiffres qui suivent "speed_" ou "speed"
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("speed_?(\\d+)").matcher(lower);
        if (m.find()) return m.group(1) + " km/h";

        // Format numérique seul : "50", "70" (label ne contenant que des chiffres)
        if (lower.matches("\\d+")) return lower + " km/h";

        return null;
    }

    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public void dispose() {
        stopVideo();
        if (mediaPlayerComponent != null) mediaPlayerComponent.release();
        if (snapshotFile != null) snapshotFile.delete();
        super.dispose();
    }
}