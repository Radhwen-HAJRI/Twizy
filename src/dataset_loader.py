import os
import pandas as pd
import shutil
import yaml

def format_dataset_for_yolo(csv_path, source_dir, dest_dir):
    df = pd.read_csv(csv_path)
    
    for index, row in df.iterrows():
        img_name = row[0] 
        
        classes = row[1:]
        if classes.sum() > 0:
            
            label = classes.idxmax()
        else:
            label = "Inconnu"
            
        
        class_dir = os.path.join(dest_dir, label)
        os.makedirs(class_dir, exist_ok=True)
        
        
        src_path = os.path.join(source_dir, img_name)
        if os.path.exists(src_path):
            shutil.copy(src_path, os.path.join(class_dir, img_name))

if __name__ == "__main__":
    
    base_path = '../database/BDD_Roboflow_Tazi/BDD_Roboflow_Tazi'
    
    
    out_path = '../dataset_yolo_format'

    
    format_dataset_for_yolo(f'{base_path}/train/_classes.csv', f'{base_path}/train', f'{out_path}/train')
    
    
    format_dataset_for_yolo(f'{base_path}/valid/_classes.csv', f'{base_path}/valid', f'{out_path}/valid')
    
   
    format_dataset_for_yolo(f'{base_path}/test/_classes.csv', f'{base_path}/test', f'{out_path}/test')
    
    print("Dataset formaté avec succès pour YOLOv8 Classification.")