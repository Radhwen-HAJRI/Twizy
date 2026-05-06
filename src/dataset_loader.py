import os
import pandas as pd
import shutil
import yaml

def format_dataset_for_yolo(csv_path, source_dir, dest_dir):
    df = pd.read_csv(csv_path)
    
    for index, row in df.iterrows():
        img_name = row[0] # Le nom du fichier image
        # Trouver la classe (colonne avec le '1' du one-hot encoding)
        classes = row[1:]
        if classes.sum() > 0:
            # S'il y a du multi-label, on prend la classe dominante pour le dossier
            label = classes.idxmax()
        else:
            label = "Inconnu"
            
        # Créer le dossier cible
        class_dir = os.path.join(dest_dir, label)
        os.makedirs(class_dir, exist_ok=True)
        
        # Copier l'image
        src_path = os.path.join(source_dir, img_name)
        if os.path.exists(src_path):
            shutil.copy(src_path, os.path.join(class_dir, img_name))

if __name__ == "__main__":
    # Le chemin exact vers ton double dossier
    base_path = '../database/BDD_Roboflow_Tazi/BDD_Roboflow_Tazi'
    
    # Le dossier de sortie (qui sera créé à la racine de twizzyy)
    out_path = '../dataset_yolo_format'

    # Formater le dossier d'entraînement (Train)
    format_dataset_for_yolo(f'{base_path}/train/_classes.csv', f'{base_path}/train', f'{out_path}/train')
    
    # Formater le dossier de validation (Valid)
    format_dataset_for_yolo(f'{base_path}/valid/_classes.csv', f'{base_path}/valid', f'{out_path}/valid')
    
    # Formater le dossier de test (Test)
    format_dataset_for_yolo(f'{base_path}/test/_classes.csv', f'{base_path}/test', f'{out_path}/test')
    
    print("Dataset formaté avec succès pour YOLOv8 Classification.")