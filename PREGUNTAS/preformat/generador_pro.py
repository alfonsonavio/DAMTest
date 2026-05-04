import json
import os

def menu_creador():
    asignatura = input("📚 Asignatura (ej: sostenibilidad): ").strip().lower()
    archivo_path = f"{asignatura}.json"
    
    # Cargar preguntas existentes si el archivo ya existe
    preguntas_totales = []
    if os.path.exists(archivo_path):
        with open(archivo_path, 'r', encoding='utf-8') as f:
            preguntas_totales = json.load(f)

    print("\n--- Configuración del Bloque ---")
    print("1. Tema Normal (tema_X)")
    print("2. Caso Práctico (caso_X)")
    print("3. Repaso Final (repaso_X)")
    tipo = input("Selecciona tipo (1-3): ")
    num = input("Número del bloque: ")
    
    prefijos = {"1": "tema_", "2": "caso_", "3": "repaso_"}
    topic_id = f"{prefijos.get(tipo, 'tema_')}{num}"
    
    enunciado_comun = input("📝 Enunciado general (deja vacío si no hay): ").strip()

    while True:
        print(f"\n--- Nueva pregunta para {topic_id} ---")
        texto = input("❓ Texto de la pregunta: ").strip()
        op_a = input("   A) ").strip()
        op_b = input("   B) ").strip()
        op_c = input("   C) ").strip()
        op_d = input("   D) ").strip()
        correcta = int(input("✅ Índice correcta (0:A, 1:B, 2:C, 3:D): "))

        q = {
            "subjectId": asignatura,
            "topicId": topic_id,
            "text": texto,
            "optionA": op_a,
            "optionB": op_b,
            "optionC": op_c,
            "optionD": op_d,
            "correctOptionIndex": correcta
        }
        
        if enunciado_comun:
            q["contextText"] = enunciado_comun
        
        preguntas_totales.append(q)
        
        if input("\n¿Añadir otra pregunta a este archivo? (s/n): ").lower() != 's':
            break

    with open(archivo_path, 'w', encoding='utf-8') as f:
        json.dump(preguntas_totales, f, indent=4, ensure_ascii=False)
    
    print(f"\n🚀 ¡Hecho! Datos guardados en {archivo_path}")
    print(f"Ahora puedes ejecutar el uploader para subir {topic_id}.")

if __name__ == "__main__":
    menu_creador()