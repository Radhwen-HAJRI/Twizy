import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class SimpleGui {

    private static final Color BG           = new Color(10,  13,  20);
    private static final Color PANEL        = new Color(16,  20,  30);
    private static final Color CARD         = new Color(20,  26,  40);
    private static final Color SIDEBAR      = new Color(14,  18,  28);
    private static final Color BLUE         = new Color(56, 139, 253);
    private static final Color GREEN        = new Color(35, 197, 112);
    private static final Color RED          = new Color(248, 81,  73);
    private static final Color PURPLE       = new Color(163,113, 247);
    private static final Color ORANGE       = new Color(240,150,  50);
    private static final Color TEXT         = new Color(220,227, 235);
    private static final Color TEXT_MUTED   = new Color(100,110, 130);
    private static final Color BORDER       = new Color(35,  42,  60);
    private static final Color BORDER_LIGHT = new Color(55,  65,  90);

    private static String   imagePath  = null;
    private static String   modelType  = "yolov8"; // "yolov8" ou "tensorflow"
    private static JLabel   imgLeft;
    private static JLabel   imgRight;
    private static JLabel   statusLbl;
    private static JLabel   bannerLbl;
    private static JPanel   cardsPanel;
    private static double   zoom       = 0.6;
    private static JFrame   mainFrame;

    public static void main(String[] args) {
        try { com.formdev.flatlaf.FlatDarkLaf.setup(); }
        catch (Exception ignored) {}
        UIManager.put("Panel.background", PANEL);
        SwingUtilities.invokeLater(SimpleGui::launch);
    }

    private static void launch() {
        mainFrame = new JFrame("Twizzy — Détection de Panneaux");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(1440, 880);
        mainFrame.setMinimumSize(new Dimension(1100, 700));
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setBackground(BG);
        mainFrame.setLayout(new BorderLayout());
        mainFrame.add(sidebar(mainFrame), BorderLayout.WEST);
        mainFrame.add(content(),          BorderLayout.CENTER);
        mainFrame.add(statusBar(),        BorderLayout.SOUTH);
        mainFrame.setVisible(true);
    }

    // ── SIDEBAR ───────────────────────────────────────────────────────────────
    private static JPanel sidebar(JFrame frame) {
        JPanel sb = new JPanel();
        sb.setLayout(new BoxLayout(sb, BoxLayout.Y_AXIS));
        sb.setBackground(SIDEBAR);
        sb.setPreferredSize(new Dimension(230, 0));
        sb.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));

        // Logo
        sb.add(Box.createVerticalStrut(22));
        JPanel logoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        logoRow.setBackground(SIDEBAR);
        logoRow.setMaximumSize(new Dimension(230, 52));
        JPanel iconCircle = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(56, 139, 253, 30));
                g2.fillOval(0, 0, 36, 36);
                g2.setColor(BLUE);
                g2.setStroke(new BasicStroke(2));
                g2.drawOval(1, 1, 34, 34);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
                g2.setColor(BLUE);
                FontMetrics fm = g2.getFontMetrics();
                String t = "T";
                g2.drawString(t, (36 - fm.stringWidth(t))/2, (36 + fm.getAscent() - fm.getDescent())/2);
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(36, 36); }
        };
        iconCircle.setOpaque(false);
        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setBackground(SIDEBAR);
        JLabel t1 = new JLabel("Twizzy");
        t1.setFont(new Font("Segoe UI", Font.BOLD, 14));
        t1.setForeground(TEXT);
        JLabel t2 = new JLabel("Détection Panneaux IA");
        t2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        t2.setForeground(TEXT_MUTED);
        textCol.add(t1); textCol.add(t2);
        logoRow.add(iconCircle); logoRow.add(textCol);
        sb.add(logoRow);
        sb.add(Box.createVerticalStrut(20));
        sb.add(divider());
        sb.add(Box.createVerticalStrut(16));

        // Actions
        sb.add(sectionTitle("ACTIONS"));
        sb.add(Box.createVerticalStrut(6));
        sb.add(iconBtn("Choisir une image", BLUE, drawFolderIcon(BLUE), e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images", "jpg","jpeg","png"));
            if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                imagePath = fc.getSelectedFile().getAbsolutePath();
                loadLeft(imagePath);
                status("Image : " + fc.getSelectedFile().getName());
            }
        }));
        sb.add(Box.createVerticalStrut(2));
        sb.add(iconBtn("Détecter le panneau", GREEN, drawSearchIcon(GREEN), e -> detect()));
        sb.add(Box.createVerticalStrut(2));
        sb.add(iconBtn("Ouvrir vidéo", PURPLE, drawVideoIcon(PURPLE), e -> openVideo()));
        sb.add(Box.createVerticalStrut(2));
        sb.add(iconBtn("Effacer", RED, drawTrashIcon(RED), e -> clear()));

        sb.add(Box.createVerticalStrut(20));
        sb.add(divider());
        sb.add(Box.createVerticalStrut(14));

        // Modèle
        sb.add(sectionTitle("MODÈLE"));
        sb.add(Box.createVerticalStrut(8));
        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        modelPanel.setBackground(SIDEBAR);
        modelPanel.setMaximumSize(new Dimension(230, 32));
        String[] models = {"YOLOv8", "TensorFlow (CNN)"};
        JComboBox<String> modelBox = new JComboBox<>(models);
        modelBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        modelBox.setBackground(new Color(25, 30, 45));
        modelBox.setForeground(TEXT);
        modelBox.setMaximumSize(new Dimension(190, 28));
        modelBox.addActionListener(e -> {
            modelType = modelBox.getSelectedIndex() == 0 ? "yolov8" : "tensorflow";
            status("Modèle : " + (modelBox.getSelectedIndex() == 0 ? "YOLOv8" : "TensorFlow CNN"));
            // Mettre à jour le badge
            updateBadge(modelBox.getSelectedIndex() == 0 ? "YOLOv8" : "CNN");
        });
        modelPanel.add(modelBox);
        sb.add(modelPanel);

        sb.add(Box.createVerticalStrut(16));
        sb.add(divider());
        sb.add(Box.createVerticalStrut(14));

        // Options
        sb.add(sectionTitle("OPTIONS"));
        sb.add(Box.createVerticalStrut(10));
        sb.add(sliderRow("Zoom", 20, 100, 60, v -> {
            zoom = v / 100.0;
            if (imagePath != null) loadLeft(imagePath);
        }));

        sb.add(Box.createVerticalGlue());
        sb.add(divider());

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        foot.setBackground(SIDEBAR);
        foot.setMaximumSize(new Dimension(230, 38));
        JLabel statusDot = new JLabel("●");
        statusDot.setFont(new Font("Segoe UI", Font.PLAIN, 9));
        statusDot.setForeground(GREEN);
        foot.add(statusDot);
        foot.add(lbl("v1.0  •  ISN 2A", 10, Font.PLAIN, TEXT_MUTED));
        sb.add(foot);

        return sb;
    }

    // ── Badge (mise à jour dynamique) ─────────────────────────────────────────
    private static JLabel badgeLabel;

    private static void updateBadge(String text) {
        if (badgeLabel != null) {
            badgeLabel.setText("  ● " + text + "  ");
            badgeLabel.setForeground(text.equals("YOLOv8") ? GREEN : ORANGE);
            badgeLabel.repaint();
        }
    }

    // ── CONTENT ───────────────────────────────────────────────────────────────
    private static JPanel content() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(PANEL);
        topBar.setPreferredSize(new Dimension(0, 52));
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 14));
        titleRow.setBackground(PANEL);
        titleRow.add(lbl("Analyse d'images", 14, Font.BOLD, TEXT));
        topBar.add(titleRow, BorderLayout.WEST);

        badgeLabel = new JLabel("  ● YOLOv8  ") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fg = getForeground();
                g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 25));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 80));
                g2.setStroke(new BasicStroke(1));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        badgeLabel.setForeground(GREEN);
        badgeLabel.setOpaque(false);
        JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 13));
        badgeWrap.setBackground(PANEL);
        badgeWrap.add(badgeLabel);
        topBar.add(badgeWrap, BorderLayout.EAST);

        bannerLbl = new JLabel("", SwingConstants.LEFT) {
            @Override protected void paintComponent(Graphics g) {
                if (getText().isEmpty()) return;
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setColor(new Color(22, 90, 45));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(GREEN);
                g2.fillRect(0, getHeight()-2, getWidth(), 2);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bannerLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        bannerLbl.setForeground(new Color(180, 255, 200));
        bannerLbl.setOpaque(false);
        bannerLbl.setPreferredSize(new Dimension(0, 0));
        bannerLbl.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));

        cardsPanel = new JPanel(new GridLayout(1, 2, 16, 0));
        cardsPanel.setBackground(BG);
        cardsPanel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        cardsPanel.add(buildCard("Image originale",       BLUE,  true));
        cardsPanel.add(buildCard("Résultat de détection", GREEN, false));

        JScrollPane scroll = new JScrollPane(cardsPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);

        JPanel centre = new JPanel(new BorderLayout());
        centre.setBackground(BG);
        centre.add(bannerLbl, BorderLayout.NORTH);
        centre.add(scroll,    BorderLayout.CENTER);

        root.add(topBar,  BorderLayout.NORTH);
        root.add(centre,  BorderLayout.CENTER);
        return root;
    }

    private static JPanel buildCard(String title, Color accent, boolean isLeft) {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(BORDER_LIGHT);
                g2.setStroke(new BasicStroke(1));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 14, 14);
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawLine(20, 1, getWidth()-20, 1);
                g2.dispose();
            }
        };
        card.setOpaque(false);

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 12));
        header.setOpaque(false);
        JPanel iconDot = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 30));
                g2.fillOval(0, 0, 22, 22);
                g2.setColor(accent);
                g2.fillOval(7, 7, 8, 8);
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(22, 22); }
            { setOpaque(false); }
        };
        JLabel hTitle = lbl(title, 12, Font.BOLD, TEXT);
        header.add(iconDot); header.add(hTitle);

        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER); sep.setBackground(BORDER);

        JLabel imgLbl = new JLabel(
            isLeft ? "Choisir une image dans la barre latérale" : "En attente de détection...",
            SwingConstants.CENTER);
        imgLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        imgLbl.setForeground(TEXT_MUTED);
        imgLbl.setOpaque(false);

        if (isLeft) imgLeft  = imgLbl;
        else        imgRight = imgLbl;

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(header, BorderLayout.CENTER);
        top.add(sep,    BorderLayout.SOUTH);
        card.add(top,    BorderLayout.NORTH);
        card.add(imgLbl, BorderLayout.CENTER);
        return card;
    }

    private static JPanel statusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(SIDEBAR);
        bar.setPreferredSize(new Dimension(0, 28));
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
            BorderFactory.createEmptyBorder(0, 18, 0, 18)));
        statusLbl = lbl("Prêt.", 11, Font.PLAIN, TEXT_MUTED);
        JLabel right = lbl("localhost:5000  •  Twizzy v1.0", 11, Font.PLAIN, new Color(45, 55, 75));
        bar.add(statusLbl, BorderLayout.WEST);
        bar.add(right,     BorderLayout.EAST);
        return bar;
    }

    // ── ICONS ─────────────────────────────────────────────────────────────────
    private static JPanel drawFolderIcon(Color c) {
        return new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c); g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawRoundRect(1, 5, 14, 10, 2, 2);
                g2.drawLine(1, 8, 6, 8); g2.drawLine(6, 8, 8, 5); g2.drawLine(8, 5, 15, 5);
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(18, 18); }
            { setOpaque(false); }
        };
    }

    private static JPanel drawSearchIcon(Color c) {
        return new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c); g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawOval(2, 2, 10, 10); g2.drawLine(10, 10, 15, 15);
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(18, 18); }
            { setOpaque(false); }
        };
    }

    private static JPanel drawVideoIcon(Color c) {
        return new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c); g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawRoundRect(1, 4, 10, 10, 2, 2);
                int[] px = {12, 16, 12}; int[] py = {5, 9, 13};
                g2.drawPolygon(px, py, 3);
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(18, 18); }
            { setOpaque(false); }
        };
    }

    private static JPanel drawTrashIcon(Color c) {
        return new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(c); g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawRoundRect(3, 5, 10, 10, 2, 2);
                g2.drawLine(1, 5, 15, 5);
                g2.drawLine(6, 5, 6, 3); g2.drawLine(10, 5, 10, 3); g2.drawLine(6, 3, 10, 3);
                g2.drawLine(6, 8, 6, 12); g2.drawLine(10, 8, 10, 12);
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(18, 18); }
            { setOpaque(false); }
        };
    }

    private static JButton iconBtn(String text, Color accent, JPanel icon, ActionListener action) {
        JButton b = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 22));
                    g2.fillRoundRect(4, 2, getWidth()-8, getHeight()-4, 8, 8);
                    g2.setColor(accent);
                    g2.fillRoundRect(2, (getHeight()-20)/2, 3, 20, 3, 3);
                }
                if (getModel().isPressed()) {
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
                    g2.fillRoundRect(4, 2, getWidth()-8, getHeight()-4, 8, 8);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setLayout(new FlowLayout(FlowLayout.LEFT, 14, 0));
        b.add(icon);
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lbl.setForeground(TEXT);
        b.add(lbl);
        b.setOpaque(false); b.setContentAreaFilled(false);
        b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMaximumSize(new Dimension(230, 42));
        b.setPreferredSize(new Dimension(230, 42));
        b.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        b.addActionListener(action);
        return b;
    }

    private static JLabel lbl(String t, int size, int style, Color c) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("Segoe UI", style, size));
        l.setForeground(c);
        return l;
    }

    private static JSeparator divider() {
        JSeparator s = new JSeparator();
        s.setForeground(BORDER); s.setBackground(SIDEBAR);
        s.setMaximumSize(new Dimension(230, 1));
        return s;
    }

    private static JLabel sectionTitle(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setForeground(TEXT_MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JPanel sliderRow(String label, int min, int max, int init,
                                     java.util.function.IntConsumer onChange) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(SIDEBAR);
        p.setMaximumSize(new Dimension(230, 54));
        p.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));
        JLabel lbl = lbl(label + " : " + init + "%", 11, Font.PLAIN, TEXT_MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        JSlider sl = new JSlider(min, max, init);
        sl.setBackground(SIDEBAR);
        sl.setAlignmentX(Component.LEFT_ALIGNMENT);
        sl.setMaximumSize(new Dimension(194, 22));
        sl.addChangeListener(e -> {
            lbl.setText(label + " : " + sl.getValue() + "%");
            if (!sl.getValueIsAdjusting()) onChange.accept(sl.getValue());
        });
        p.add(lbl); p.add(Box.createVerticalStrut(4)); p.add(sl);
        return p;
    }

    // ── LOGIC ─────────────────────────────────────────────────────────────────
    private static void loadLeft(String path) {
        try {
            ImageIcon icon = new ImageIcon(path);
            if (icon.getIconWidth() <= 0) { imgLeft.setText("Image introuvable."); return; }
            int w = (int)(icon.getIconWidth()  * zoom);
            int h = (int)(icon.getIconHeight() * zoom);
            imgLeft.setIcon(new ImageIcon(icon.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH)));
            imgLeft.setText(null);
            cardsPanel.revalidate();
        } catch (Exception ex) { imgLeft.setText("Erreur."); }
    }

    private static void clear() {
        imagePath = null;
        imgLeft.setIcon(null);  imgLeft.setText("Choisir une image dans la barre latérale");
        imgRight.setIcon(null); imgRight.setText("En attente de détection...");
        bannerLbl.setText(""); bannerLbl.setPreferredSize(new Dimension(0, 0));
        if (bannerLbl.getParent() != null) {
            bannerLbl.getParent().revalidate();
            bannerLbl.getParent().repaint();
        }
        status("Effacé.");
    }

    private static void openVideo() {
        try {
            // Force VLC path avant d'ouvrir
            System.setProperty("jna.library.path",  "C:\\Program Files\\VideoLAN\\VLC");
            System.setProperty("vlcj.library.path", "C:\\Program Files\\VideoLAN\\VLC");
            VideoDetectionWindow win = new VideoDetectionWindow();
            win.setLocationRelativeTo(mainFrame);
            win.setVisible(true);
            status("Fenêtre vidéo ouverte.");
        } catch (UnsatisfiedLinkError | NoClassDefFoundError err) {
            JOptionPane.showMessageDialog(mainFrame,
                "<html><b>VLC introuvable.</b><br>Installez VLC 64 bits depuis videolan.org</html>",
                "VLC requis", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainFrame,
                "Erreur : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void detect() {
        if (imagePath == null || imgLeft.getIcon() == null) {
            JOptionPane.showMessageDialog(null, "Choisissez d'abord une image.",
                "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        status("Détection en cours… (" + modelType + ")");
        new Thread(() -> {
            try {
                String apiUrl = "http://127.0.0.1:5000/predict?model=" + modelType;
                URL url = new URL(apiUrl);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/octet-stream");
                try (OutputStream os = con.getOutputStream();
                     FileInputStream fis = new FileInputStream(imagePath)) {
                    byte[] buf = new byte[4096]; int n;
                    while ((n = fis.read(buf)) != -1) os.write(buf, 0, n);
                }
                BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line; while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                String resp = sb.toString();
                String cls = "Inconnu", b64 = null;
                int ci = resp.indexOf("\"class\"");
                if (ci != -1) { int c=resp.indexOf(":",ci),qs=resp.indexOf("\"",c),qe=resp.indexOf("\"",qs+1); cls=resp.substring(qs+1,qe); }
                int ii = resp.indexOf("\"image\"");
                if (ii != -1) { int c=resp.indexOf(":",ii),qs=resp.indexOf("\"",c),qe=resp.indexOf("\"",qs+1); b64=resp.substring(qs+1,qe); }
                final String label = formatLabel(cls);
                final String img64 = b64;
                SwingUtilities.invokeLater(() -> {
                    bannerLbl.setText("  ✓   Panneau détecté : " + label);
                    bannerLbl.setPreferredSize(new Dimension(0, 42));
                    if (bannerLbl.getParent() != null) {
                        bannerLbl.getParent().revalidate();
                        bannerLbl.getParent().repaint();
                    }
                    if (img64 != null) {
                        try {
                            byte[] bytes = Base64.getDecoder().decode(img64);
                            ImageIcon ic = new ImageIcon(bytes);
                            int w = (int)(ic.getIconWidth()  * zoom);
                            int h = (int)(ic.getIconHeight() * zoom);
                            imgRight.setIcon(new ImageIcon(ic.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH)));
                            imgRight.setText(null);
                        } catch (Exception ex) { imgRight.setText("Erreur affichage."); }
                    }
                    cardsPanel.revalidate(); cardsPanel.repaint();
                    status("✓  Détecté : " + label);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    status("Erreur : " + ex.getMessage());
                    JOptionPane.showMessageDialog(null, "Erreur API : " + ex.getMessage(),
                        "Erreur", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private static String formatLabel(String raw) {
        if (raw == null) return "Inconnu";
        String l = raw.toLowerCase().trim();
        if (l.startsWith("speed_")) return "Vitesse : " + l.replace("speed_","").replaceAll("[^0-9]","") + " km/h";
        switch (l) {
            case "stop":       return "STOP";
            case "give_way":
            case "yield":      return "Cédez le passage";
            case "no_entry":   return "Sens interdit";
            case "no_parking": return "Stationnement interdit";
            case "pedestrian": return "Passage piéton";
            case "roundabout": return "Rond-point";
            default: String s=raw.replace("_"," "); return s.substring(0,1).toUpperCase()+s.substring(1);
        }
    }

    private static void status(String msg) {
        SwingUtilities.invokeLater(() -> statusLbl.setText(msg));
    }

    public static int set_dimension(int mesure, double facteur) {
        return (int) Math.round(mesure * facteur);
    }
}