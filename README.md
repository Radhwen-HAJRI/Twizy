# Twizzy — Détection de Panneaux de Signalisation

Projet Bureau d'Étude — ISN 2A — 2025/2026  
Amina Noukra · Yassine El-Ksir · Radhwen Hajri · Wadie Zarada

---

## Lancer le projet

### 1. Démarrer l'API Flask (backend)
```bash
python app.py
```
L'API démarre sur **http://localhost:5000**  
Laisser ce terminal ouvert.

### 2. Ouvrir l'interface web
Aller sur **http://localhost:5000** dans le navigateur.

### 3. Lancer l'interface Java (optionnel)
```bash
cd Projet/src
javac -cp "../lib/*" *.java
java -cp "../lib/*;." SimpleGui
```

---

## Installation des dépendances

### Python
```bash
pip install flask flask-cors pillow numpy ultralytics tensorflow==2.13.0 keras==2.13.1 opencv-python pymysql
```

### Java
Les JARs sont déjà dans `Projet/lib/` :

| JAR | Rôle |
|-----|------|
| `flatlaf-3.4.1.jar` | Dark theme Java Swing |
| `vlcj-4.8.2.jar` | Lecture vidéo Java |
| `jna-5.13.0.jar` | Binding natif VLC |
| `json-20231013.jar` | Parsing JSON |
| `opencv-480.jar` | OpenCV Java |
| `mysql-connector-j-8.0.33.jar` | Connexion MySQL |

---

## Prérequis système

| Outil | Version | Lien |
|-------|---------|------|
| Python | 3.11 | https://www.python.org/downloads/release/python-3110/ |
| Java JDK | 17+ | https://adoptium.net/ |
| VLC (64 bits) | dernière | https://www.videolan.org/vlc/ |
| MySQL | 8.0+ | https://dev.mysql.com/downloads/installer/ |
| Git | dernière | https://git-scm.com/ |

---

## Configuration MySQL

Lancer MySQL et exécuter :

```sql
CREATE DATABASE twizzy;
USE twizzy;
CREATE TABLE detections (
    id INT AUTO_INCREMENT PRIMARY KEY,
    date_heure DATETIME DEFAULT NOW(),
    modele VARCHAR(20),
    classe_detectee VARCHAR(50),
    confiance FLOAT,
    image_path VARCHAR(255)
);
```

Mettre à jour le mot de passe dans `Projet/src/DatabaseManager.java` :
```java
private static final String PASSWORD = "votre_mot_de_passe";
```

---

## Structure du projet

```
Twizzy/
├── app.py                        → API Flask unifiée (YOLOv8 + TensorFlow)
├── api_yolo.py                   → API Flask YOLOv8 seul
├── api_tensor.py                 → API Flask TensorFlow seul
├── api.py                        → API Flask CNN maison
├── static/
│   └── index.html                → Interface web (image + vidéo)
├── Projet/
│   ├── lib/                      → JARs Java
│   └── src/
│       ├── SimpleGui.java        → Interface principale + historique MySQL
│       ├── Interface_image.java  → Détection image Java
│       ├── VideoDetectionWindow.java → Détection vidéo Java
│       └── DatabaseManager.java  → Gestion MySQL
├── Detection_panneaux/
│   └── my_model.keras            → Modèle CNN TensorFlow entraîné
├── TWIZZY_dataset/               → Dataset YOLOv8 (935 images)
├── runs/detect/best_train/
│   └── weights/best2.pt          → Poids YOLOv8 entraîné
├── tensorFlow/                   → Scripts entraînement TensorFlow
├── Yolov8/                       → Scripts entraînement YOLOv8
└── requirements.txt              → Dépendances Python
```

---

## Routes API

| Route | Méthode | Description |
|-------|---------|-------------|
| `GET /` | GET | Interface web |
| `/predict` | POST | Détection sur image (YOLOv8 par défaut) |
| `/predict?model=tensorflow` | POST | Détection avec CNN TensorFlow |
| `/predict_video` | POST | Analyse vidéo frame par frame |

---

## Technologies utilisées

### Vision par ordinateur
- **OpenCV** — traitement vidéo frame par frame
- **YOLOv8n** (Ultralytics) — détection et localisation temps réel
- **TensorFlow / Keras** — CNN maison (4 blocs Conv2D + Dense)

### Backend
- **Flask** — API REST Python
- **flask-cors** — gestion CORS
- **Pillow** — manipulation d'images
- **NumPy** — calcul matriciel
- **OpenCV-Python** — traitement vidéo

### Interface Java
- **Java Swing** — interface graphique desktop
- **FlatLaf** — dark theme moderne
- **vlcj** — lecture vidéo via VLC
- **JSON.org** — parsing des réponses API
- **MySQL Connector/J** — sauvegarde historique détections

### Interface web
- **HTML / CSS / JavaScript** — interface responsive
- **Fetch API** — communication avec Flask

### Base de données
- **MySQL 8.0** — historique des détections

---

## Problèmes fréquents

| Problème | Solution |
|----------|----------|
| `TensorFlow model not loaded` | Vérifier `Detection_panneaux/my_model.keras` |
| `YOLOv8 model not loaded` | Vérifier `runs/detect/best_train/weights/best2.pt` |
| `libvlc.dll introuvable` | Installer VLC 64 bits dans `C:\Program Files\VideoLAN\VLC` |
| `Connection refused` | Lancer `python app.py` avant l'interface Java |
| `MySQL connexion échouée` | Vérifier mot de passe dans `DatabaseManager.java` |
| Erreur compilation Java | Vérifier JDK 17+ : `java -version` |
