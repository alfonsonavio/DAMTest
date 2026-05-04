import firebase_admin
from firebase_admin import credentials, db
import json

# 1. CONFIGURACIÓN (Verifica que el nombre del .json de tu clave sea correcto)
cred = credentials.Certificate("damtests-5ec43-firebase-adminsdk-fbsvc-c7a2bd3207.json")
if not firebase_admin._apps:
    firebase_admin.initialize_app(cred, {
        'databaseURL': 'https://damtests-5ec43-default-rtdb.firebaseio.com/'
    })

def upload_questions(questions_list):
    temas_a_procesar = {}
    
    for q in questions_list:
        # Aseguramos que el topicId sea un string (ej: "tema_1")
        tid = q.get('topicId', '1')
        topic_key = f"tema_{tid}" if isinstance(tid, int) or str(tid).isdigit() else tid
        
        key = (q['subjectId'], topic_key)
        if key not in temas_a_procesar:
            temas_a_procesar[key] = []
        temas_a_procesar[key].append(q)

    for (subj, topic_key), questions in temas_a_procesar.items():
        preguntas_dict = {}
        
        for i, q in enumerate(questions):
            # LÓGICA DE DETECCIÓN DE FORMATO
            # Si tiene la lista 'options', sacamos de ahí. Si no, buscamos 'optionA'
            if 'options' in q:
                opt_a = q['options'][0]
                opt_b = q['options'][1]
                opt_c = q['options'][2]
                opt_d = q['options'][3]
            else:
                opt_a = q.get('optionA', "")
                opt_b = q.get('optionB', "")
                opt_c = q.get('optionC', "")
                opt_d = q.get('optionD', "")

            datos_pregunta = {
                "subjectId": subj,
                "topicId": topic_key,
                "text": q['text'],
                "optionA": opt_a,
                "optionB": opt_b,
                "optionC": opt_c,
                "optionD": opt_d,
                "correctOptionIndex": q['correctOptionIndex']
            }
            
            if 'contextText' in q:
                datos_pregunta["contextText"] = q['contextText']
            
            preguntas_dict[f"p{i+1}"] = datos_pregunta

        # Subida a Firebase
        ref_preguntas = db.reference(f'preguntas/{subj}/{topic_key}')
        ref_preguntas.set(preguntas_dict) 
        
        # Versión
        ref_version = db.reference(f'versiones/{subj}/{topic_key}')
        current_version = ref_version.get() or 0
        ref_version.set(current_version + 1)
        
        print(f"✅ Subido y Convertido: {subj} -> {topic_key} (v{current_version + 1})")

# CAMBIA ESTO por el archivo que quieras subir en cada momento
nombre_archivo = "sostenibilidad.json" 

try:
    with open(nombre_archivo, 'r', encoding='utf-8') as f:
        datos = json.load(f)
    upload_questions(datos)
except FileNotFoundError:
    print(f"❌ Error: No se encuentra el archivo {nombre_archivo}")
except Exception as e:
    print(f"❌ Error inesperado: {e}")