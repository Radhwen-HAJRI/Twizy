import io
import cv2
import numpy as np
import base64
import os
import tempfile
from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS
from PIL import Image, ImageDraw, ImageFont

# TensorFlow optionnel
try:
    import tensorflow as tf
    tf_model = tf.keras.models.load_model(
        "Detection_panneaux/my_model.keras",
        compile=False
    )                                
    print("✓ TensorFlow model loaded")
    TF_OK = True
except Exception as e:
    tf_model = None
    TF_OK = False
    print(f"⚠ TensorFlow not available: {e}")

# YOLOv8
try:
    from ultralytics import YOLO
    yolo_model = YOLO("runs/detect/best_train/weights/best.pt")
    print("✓ YOLOv8 model loaded")
    YOLO_OK = True
except Exception as e:
    yolo_model = None
    YOLO_OK = False
    print(f"⚠ YOLOv8 not available: {e}")

def load_label_map(path):
    label_map = {}
    try:
        with open(path, 'r') as f:
            id = None; name = None
            for line in f.readlines():
                line = line.strip()
                if line.startswith("id:"):
                    id = int(line.split(":")[1].strip().rstrip(','))
                elif line.startswith("name:"):
                    name = line.split(":")[1].strip().replace('"', '')
                if id is not None and name is not None:
                    label_map[id] = name
                    id = None; name = None
    except Exception as e:
        print(f"⚠ Label map: {e}")
    return label_map

tf_label_map = load_label_map("Detection_panneaux/test/objects_label_map.pbtxt")

app = Flask(__name__, static_folder='static', static_url_path='')
CORS(app)

@app.route('/')
def index():
    return send_from_directory('static', 'index.html')

# ── PREDICT IMAGE ─────────────────────────────────────────────────────────────
@app.route('/predict', methods=['POST'])
def predict():
    model_type = request.args.get('model', 'yolov8').lower()
    image = Image.open(io.BytesIO(request.data)).convert("RGB")

    if model_type == 'tensorflow':
        if not TF_OK:
            return jsonify({"error": "TensorFlow model not loaded"}), 500
        try:
            img = image.resize((224, 224))
            arr = np.expand_dims(np.array(img) / 255.0, axis=0)
            pred = tf_model.predict(arr)
            cls_id = int(np.argmax(pred))
            label = tf_label_map.get(cls_id, f"Classe {cls_id}").strip().strip(',')
            draw = ImageDraw.Draw(image)
            draw.text((10, 10), label, fill="red", font=ImageFont.load_default())
        except Exception as e:
            return jsonify({"error": str(e)}), 500
    else:
        if not YOLO_OK:
            return jsonify({"error": "YOLOv8 model not loaded"}), 500
        try:
            temp = "temp.jpg"
            image.save(temp)
            results = yolo_model(temp, conf=0.1)
            draw = ImageDraw.Draw(image)
            font = ImageFont.load_default()
            label = "No detection"
            for result in results:
                for box in result.boxes:
                    cls_id = int(box.cls[0])
                    conf   = float(box.conf[0])
                    label  = yolo_model.names[cls_id]
                    bbox   = box.xyxy[0].tolist()
                    draw.rectangle(bbox, outline="red", width=2)
                    draw.text((bbox[0], bbox[1]-10), f"{label} {conf:.2f}", fill="red", font=font)
            os.remove(temp)
        except Exception as e:
            return jsonify({"error": str(e)}), 500

    buffered = io.BytesIO()
    image.save(buffered, format="JPEG")
    img_base64 = base64.b64encode(buffered.getvalue()).decode("utf-8")
    return jsonify({"class": label, "image": img_base64})

# ── PREDICT VIDEO ─────────────────────────────────────────────────────────────
@app.route('/predict_video', methods=['POST'])
def predict_video():
    if not YOLO_OK:
        return jsonify({"error": "YOLOv8 model not loaded"}), 500

    tmp = tempfile.NamedTemporaryFile(delete=False, suffix='.mp4')
    tmp.write(request.data)
    tmp.close()

    cap = cv2.VideoCapture(tmp.name)
    if not cap.isOpened():
        os.remove(tmp.name)
        return jsonify({"error": "Impossible d'ouvrir la vidéo"}), 400

    fps         = cap.get(cv2.CAP_PROP_FPS) or 25
    total       = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    step        = 15  # 1 frame analysée toutes les 15 (comme Java)
    detections  = []
    frames_b64  = []
    idx         = 0

    while True:
        ret, frame = cap.read()
        if not ret: break

        if idx % step == 0:
            tmp_f = f"tmp_frame_{idx}.jpg"
            cv2.imwrite(tmp_f, frame)
            results = yolo_model(tmp_f, conf=0.1)
            os.remove(tmp_f)

            for result in results:
                for box in result.boxes:
                    cls_id = int(box.cls[0])
                    conf   = float(box.conf[0])
                    label  = yolo_model.names[cls_id]
                    bbox   = box.xyxy[0].tolist()
                    cv2.rectangle(frame, (int(bbox[0]),int(bbox[1])), (int(bbox[2]),int(bbox[3])), (0,0,255), 2)
                    cv2.putText(frame, f"{label} {conf:.2f}", (int(bbox[0]), int(bbox[1])-10),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0,0,255), 2)
                    detections.append({
                        "frame": idx,
                        "time": round(idx/fps, 2),
                        "class": label,
                        "confidence": round(conf, 2)
                    })

            _, buf = cv2.imencode('.jpg', frame)
            frames_b64.append({
                "frame": idx,
                "time":  round(idx/fps, 2),
                "image": base64.b64encode(buf).decode('utf-8')
            })
        idx += 1

    cap.release()
    os.remove(tmp.name)

    return jsonify({
        "total_frames":    total,
        "analyzed_frames": len(frames_b64),
        "fps":             fps,
        "detections":      detections,
        "frames":          frames_b64
    })

if __name__ == "__main__":
    print("\nStarting Flask on http://localhost:5000")
    app.run(host='0.0.0.0', port=5000, debug=False)
