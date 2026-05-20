import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.Base64;
import org.json.JSONObject;
import java.awt.BasicStroke;
import java.util.List;
import java.awt.Rectangle;

public class Interface_image extends JFrame {

    // ── Palette complète ──────────────────────────────────────────────────────
    private static final Color BG_DARK       = new Color(13,  17,  23);
    private static final Color SIDEBAR_COLOR = new Color(22,  27,  34);
    private static final Color BG_COLOR      = new Color(13,  17,  23);
    private static final Color CARD_BG       = new Color(22,  27,  34);
    private static final Color PRIMARY_COLOR = new Color(56, 139, 253);
    private static final Color ACCENT_COLOR  = new Color(163,113, 247);
    private static final Color SUCCESS_COLOR = new Color(63, 185,  80);
    private static final Color DANGER_COLOR  = new Color(248, 81,  73);
    private static final Color TEXT_LIGHT    = new Color(201,209, 217);
    private static final Color TEXT_DIM      = new Color(110,118, 129);
    private static final Color TEXT_DARK     = new Color(201,209, 217);
    private static final Color BORDER_COLOR  = new Color(48,  54,  61);
    private static final String VLC_PATH     = "C:\\Program Files\\VideoLAN\\VLC";

    // ── State ─────────────────────────────────────────────────────────────────
    private double        imageScaleFactor     = 0.5;
    private double        detectionScaleFactor = 0.7;
    private boolean       autoDetect           = false;
    private BufferedImage originalImage        = null;

    // ── UI refs ───────────────────────────────────────────────────────────────
    private JLabel imageLabel;
    private JLabel imageLabel2;
    private JLabel resultBanner;
    private JLabel statusLabel;
    private JPanel imagesPanel;

    // ══════════════════════════════════════════════════════════════════════════
    public Interface_image() {
        super("Road Sign Detection — Twizzy");
        setupFrame();
        buildUI();
        setVisible(true);
    }

    private void setupFrame() {
        setSize(1400, 860);
        setMinimumSize(new Dimension(1000, 700));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_DARK);
    }

    private void buildUI() {
        add(buildSidebar(),   BorderLayout.WEST);
        add(buildMain(),      BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SIDEBAR
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildSidebar() {
        JPanel sb = new JPanel();
        sb.setLayout(new BoxLayout(sb, BoxLayout.Y_AXIS));
        sb.setBackground(SIDEBAR_COLOR);
        sb.setPreferredSize(new Dimension(220, 0));
        sb.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COLOR));

        // ── Logo
        sb.add(logoPanel());
        sb.add(sep());

        // ── Actions
        sb.add(sectionLbl("ACTIONS"));
        sb.add(Box.createVerticalStrut(4));
        sb.add(sidebarBtn("  📁   Choisir une image",  PRIMARY_COLOR, e -> choix_image()));
        sb.add(Box.createVerticalStrut(4));
        sb.add(sidebarBtn("  🔍   Détecter",           SUCCESS_COLOR, e -> detecter_panneau()));
        sb.add(Box.createVerticalStrut(4));
        sb.add(sidebarBtn("  🎬   Détection vidéo",    ACCENT_COLOR,  e -> openVideoDetection()));
        sb.add(Box.createVerticalStrut(4));
        sb.add(sidebarBtn("  🗑️    Effacer",            DANGER_COLOR,  e -> clearImages()));
        sb.add(Box.createVerticalStrut(16));
        sb.add(sep());

        // ── Options
        sb.add(sectionLbl("OPTIONS"));
        sb.add(sliderBlock("Zoom image",    25, 100, 50,
            v -> { imageScaleFactor = v/100.0; if (originalImage!=null) refreshMainImage(); }));
        sb.add(Box.createVerticalStrut(10));
        sb.add(sliderBlock("Zoom résultat", 25, 100, 70,
            v -> { detectionScaleFactor = v/100.0; refreshDetectionImage(); }));
        sb.add(Box.createVerticalStrut(12));

        JPanel cbPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        cbPanel.setBackground(SIDEBAR_COLOR);
        cbPanel.setMaximumSize(new Dimension(220, 28));
        JCheckBox cb = new JCheckBox("Détection auto");
        cb.setBackground(SIDEBAR_COLOR);
        cb.setForeground(TEXT_DIM);
        cb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cb.setFocusPainted(false);
        cb.addActionListener(e -> autoDetect = cb.isSelected());
        cbPanel.add(cb);
        sb.add(cbPanel);

        sb.add(Box.createVerticalGlue());

        // ── Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 10));
        footer.setBackground(SIDEBAR_COLOR);
        footer.setMaximumSize(new Dimension(220, 36));
        JLabel ver = new JLabel("Twizzy  v1.0  ·  ISN 2A");
        ver.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        ver.setForeground(TEXT_DIM);
        footer.add(ver);
        sb.add(footer);

        return sb;
    }

    private JPanel logoPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 18));
        p.setBackground(SIDEBAR_COLOR);
        p.setMaximumSize(new Dimension(220, 70));

        JLabel dot = new JLabel("⬤");
        dot.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        dot.setForeground(SUCCESS_COLOR);

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setBackground(SIDEBAR_COLOR);
        JLabel t1 = new JLabel("Détection Panneaux");
        t1.setFont(new Font("Segoe UI", Font.BOLD, 13));
        t1.setForeground(TEXT_LIGHT);
        JLabel t2 = new JLabel("Analyse Routière IA");
        t2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        t2.setForeground(TEXT_DIM);
        stack.add(t1); stack.add(t2);

        p.add(dot); p.add(stack);
        return p;
    }

    private JSeparator sep() {
        JSeparator s = new JSeparator();
        s.setForeground(BORDER_COLOR);
        s.setBackground(SIDEBAR_COLOR);
        s.setMaximumSize(new Dimension(220, 1));
        return s;
    }

    private JLabel sectionLbl(String txt) {
        JLabel l = new JLabel(txt);
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setForeground(TEXT_DIM);
        l.setBorder(BorderFactory.createEmptyBorder(12, 16, 4, 0));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JButton sidebarBtn(String text, Color accent, ActionListener action) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2.setColor(new Color(accent.getRed(),
                                          accent.getGreen(),
                                          accent.getBlue(), 28));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(accent);
                    g2.fillRoundRect(0, (getHeight()-22)/2, 3, 22, 3, 3);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btn.setForeground(TEXT_LIGHT);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(220, 38));
        btn.setPreferredSize(new Dimension(220, 38));
        btn.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
        btn.addActionListener(action);
        return btn;
    }

    private JPanel sliderBlock(String label, int min, int max, int init,
                                java.util.function.IntConsumer onChange) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(SIDEBAR_COLOR);
        p.setMaximumSize(new Dimension(220, 52));
        p.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));

        JLabel lbl = new JLabel(label + ": " + init + "%");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbl.setForeground(TEXT_DIM);
        lbl.setAlignmentX(LEFT_ALIGNMENT);

        JSlider sl = new JSlider(min, max, init);
        sl.setBackground(SIDEBAR_COLOR);
        sl.setAlignmentX(LEFT_ALIGNMENT);
        sl.setMaximumSize(new Dimension(188, 22));
        sl.addChangeListener(e -> {
            lbl.setText(label + ": " + sl.getValue() + "%");
            if (!sl.getValueIsAdjusting()) onChange.accept(sl.getValue());
        });
        p.add(lbl); p.add(Box.createVerticalStrut(3)); p.add(sl);
        return p;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  MAIN CONTENT
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel buildMain() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BG_COLOR);

        // Top bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(SIDEBAR_COLOR);
        topBar.setPreferredSize(new Dimension(0, 48));
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
        JLabel pageTitle = new JLabel("   Analyse d'images");
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        pageTitle.setForeground(TEXT_LIGHT);
        topBar.add(pageTitle, BorderLayout.WEST);

        // Result banner
        resultBanner = new JLabel("", SwingConstants.LEFT);
        resultBanner.setFont(new Font("Segoe UI", Font.BOLD, 13));
        resultBanner.setOpaque(true);
        resultBanner.setBackground(new Color(35, 134, 54));
        resultBanner.setForeground(Color.WHITE);
        resultBanner.setPreferredSize(new Dimension(0, 0));
        resultBanner.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));

        // Cards
        imagesPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        imagesPanel.setBackground(BG_COLOR);
        imagesPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        imagesPanel.add(buildCard("Image originale",      PRIMARY_COLOR, true));
        imagesPanel.add(buildCard("Résultat de détection", SUCCESS_COLOR, false));

        JScrollPane scroll = new JScrollPane(imagesPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_COLOR);

        JPanel centre = new JPanel(new BorderLayout());
        centre.setBackground(BG_COLOR);
        centre.add(resultBanner, BorderLayout.NORTH);
        centre.add(scroll,       BorderLayout.CENTER);

        main.add(topBar, BorderLayout.NORTH);
        main.add(centre, BorderLayout.CENTER);
        return main;
    }

    private JPanel buildCard(String title, Color accent, boolean isLeft) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        header.setBackground(new Color(30, 36, 46));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));
        JLabel dot = new JLabel("⬤");
        dot.setForeground(accent);
        dot.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        JLabel hTitle = new JLabel(title);
        hTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        hTitle.setForeground(TEXT_LIGHT);
        header.add(dot); header.add(hTitle);
        card.add(header, BorderLayout.NORTH);

        JLabel imgLbl = new JLabel(
            isLeft ? "Aucune image chargée" : "En attente de détection",
            SwingConstants.CENTER);
        imgLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        imgLbl.setForeground(TEXT_DIM);
        imgLbl.setBackground(CARD_BG);
        imgLbl.setOpaque(true);

        if (isLeft) imageLabel  = imgLbl;
        else        imageLabel2 = imgLbl;

        card.add(imgLbl, BorderLayout.CENTER);
        return card;
    }

    // ── Status bar ────────────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(SIDEBAR_COLOR);
        bar.setPreferredSize(new Dimension(0, 26));
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
            BorderFactory.createEmptyBorder(0, 16, 0, 16)));
        statusLabel = new JLabel("Prêt.");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setForeground(TEXT_DIM);
        bar.add(statusLabel, BorderLayout.WEST);
        JLabel hint = new JLabel("localhost:5000  ·  YOLOv8");
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hint.setForeground(new Color(70, 78, 89));
        bar.add(hint, BorderLayout.EAST);
        return bar;
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BANNER
    // ══════════════════════════════════════════════════════════════════════════
    private void showBanner(String detectedClass) {
        resultBanner.setText("✓   Panneau détecté : " + formatLabel(detectedClass));
        resultBanner.setBackground(new Color(35, 134, 54));
        resultBanner.setForeground(Color.WHITE);
        resultBanner.setPreferredSize(new Dimension(0, 40));
        if (resultBanner.getParent() != null) {
            resultBanner.getParent().revalidate();
            resultBanner.getParent().repaint();
        }
    }

    private void hideBanner() {
        resultBanner.setText("");
        resultBanner.setPreferredSize(new Dimension(0, 0));
        if (resultBanner.getParent() != null) {
            resultBanner.getParent().revalidate();
            resultBanner.getParent().repaint();
        }
    }

    private String formatLabel(String raw) {
        if (raw == null) return "Inconnu";
        String lower = raw.toLowerCase().trim();
        if (lower.startsWith("speed_")) {
            String num = lower.replace("speed_", "").replaceAll("[^0-9]", "");
            return "Vitesse limite : " + num + " km/h";
        }
        switch (lower) {
            case "stop":       return "STOP";
            case "give_way":
            case "yield":      return "Cédez le passage";
            case "no_entry":   return "Sens interdit";
            case "no_parking": return "Interdiction de stationner";
            case "pedestrian": return "Passage piéton";
            case "roundabout": return "Rond-point obligatoire";
            default:
                String s = raw.replace("_", " ");
                return s.substring(0, 1).toUpperCase() + s.substring(1);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════════
    private void refreshMainImage() {
        if (originalImage == null) return;
        int w = (int)(originalImage.getWidth()  * imageScaleFactor);
        int h = (int)(originalImage.getHeight() * imageScaleFactor);
        imageLabel.setIcon(new ImageIcon(
            originalImage.getScaledInstance(w, h, Image.SCALE_SMOOTH)));
        imageLabel.setText(null);
    }

    private void refreshDetectionImage() {
        if (imageLabel2.getIcon() == null || originalImage == null) return;
        int w = (int)(originalImage.getWidth()  * imageScaleFactor);
        int h = (int)(originalImage.getHeight() * imageScaleFactor);
        Image src = ((ImageIcon) imageLabel2.getIcon()).getImage();
        imageLabel2.setIcon(new ImageIcon(
            src.getScaledInstance(w, h, Image.SCALE_SMOOTH)));
    }

    private void clearImages() {
        originalImage = null;
        imageLabel.setIcon(null);  imageLabel.setText("Aucune image chargée");
        imageLabel2.setIcon(null); imageLabel2.setText("En attente de détection");
        hideBanner();
        imagesPanel.revalidate(); imagesPanel.repaint();
        setStatus("Images effacées.");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  VLC
    // ══════════════════════════════════════════════════════════════════════════
    private void forceVlcPath() {
        System.setProperty("jna.library.path",  VLC_PATH);
        System.setProperty("vlcj.library.path", VLC_PATH);
        try {
            java.lang.reflect.Field field =
                ClassLoader.class.getDeclaredField("usr_paths");
            field.setAccessible(true);
            String[] paths = (String[]) field.get(null);
            boolean found = false;
            for (String p : paths) if (p.equals(VLC_PATH)) { found = true; break; }
            if (!found) {
                String[] np = new String[paths.length + 1];
                System.arraycopy(paths, 0, np, 0, paths.length);
                np[paths.length] = VLC_PATH;
                field.set(null, np);
            }
        } catch (Exception e) {
            System.err.println("[VLC] " + e.getMessage());
        }
    }

    private void openVideoDetection() {
        forceVlcPath();
        try {
            VideoDetectionWindow win = new VideoDetectionWindow();
            win.setLocationRelativeTo(this);
            win.setVisible(true);
            setStatus("Fenêtre vidéo ouverte.");
        } catch (UnsatisfiedLinkError | NoClassDefFoundError err) {
            JOptionPane.showMessageDialog(this,
                "<html><b>Impossible de charger VLC.</b><br>Chemin : "
                + VLC_PATH + "<br><i>" + err.getMessage() + "</i></html>",
                "VLC requis", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DETECTION
    // ══════════════════════════════════════════════════════════════════════════
    private void choix_image() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String n = f.getName().toLowerCase();
                return n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".png");
            }
            public String getDescription() { return "Images (*.jpg, *.jpeg, *.png)"; }
        });
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                originalImage = ImageIO.read(fc.getSelectedFile());
                int w = (int)(originalImage.getWidth()  * imageScaleFactor);
                int h = (int)(originalImage.getHeight() * imageScaleFactor);
                imageLabel.setIcon(new ImageIcon(
                    originalImage.getScaledInstance(w, h, Image.SCALE_SMOOTH)));
                imageLabel.setText(null);
                imageLabel2.setIcon(null);
                imageLabel2.setText("En attente de détection");
                hideBanner();
                imagesPanel.revalidate();
                setStatus("Image chargée : " + fc.getSelectedFile().getName());
                if (autoDetect) detecter_panneau();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Erreur de lecture.",
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void detecter_panneau() {
        if (imageLabel.getIcon() == null) {
            JOptionPane.showMessageDialog(this,
                "Veuillez d'abord sélectionner une image.",
                "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        setStatus("Détection en cours…");
        new Thread(() -> {
            try {
                File tmp = File.createTempFile("img", ".jpg");
                ImageIO.write(originalImage, "jpg", tmp);
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:5000/predict"))
                    .header("Content-Type", "image/jpeg")
                    .POST(HttpRequest.BodyPublishers.ofFile(tmp.toPath()))
                    .build();
                HttpResponse<String> resp = client.send(req,
                    HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JSONObject json = new JSONObject(resp.body());
                    String cls   = json.getString("class");
                    String b64   = json.getString("image");
                    byte[] bytes = Base64.getDecoder().decode(b64);
                    BufferedImage det = ImageIO.read(new ByteArrayInputStream(bytes));
                    Graphics2D g2d = det.createGraphics();
                    g2d.setColor(new Color(39, 174, 96));
                    g2d.setStroke(new BasicStroke(4));
                    g2d.drawRect(8, 8, det.getWidth()-16, det.getHeight()-16);
                    g2d.dispose();
                    int w = (int)(det.getWidth()  * detectionScaleFactor);
                    int h = (int)(det.getHeight() * detectionScaleFactor);
                    Image scaled = det.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                    SwingUtilities.invokeLater(() -> {
                        imageLabel2.setIcon(new ImageIcon(scaled));
                        imageLabel2.setText(null);
                        showBanner(cls);
                        imagesPanel.revalidate(); imagesPanel.repaint();
                        setStatus("Panneau détecté : " + formatLabel(cls));
                    });
                } else throw new IOException("HTTP " + resp.statusCode());
                tmp.delete();
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("Erreur : " + ex.getMessage());
                    JOptionPane.showMessageDialog(Interface_image.this,
                        "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    @SuppressWarnings("unused")
    private void drawDetectionRectangles(BufferedImage image, List<Rectangle> detections) {
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.GREEN);
        g2d.setStroke(new BasicStroke(3));
        for (Rectangle r : detections) g2d.drawRect(r.x, r.y, r.width, r.height);
        g2d.dispose();
    }

    // ══════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        File libvlc = new File("C:\\Program Files\\VideoLAN\\VLC\\libvlc.dll");
        System.out.println("[Start] libvlc.dll: " + libvlc.exists());
        try { com.formdev.flatlaf.FlatDarkLaf.setup(); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(Interface_image::new);
    }
}