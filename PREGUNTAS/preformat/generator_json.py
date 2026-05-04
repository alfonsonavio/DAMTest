import json
import os

def menu_creador():
    asignatura = input("📚 Nombre de la asignatura (ID): ").strip().lower()
    archivo_path = f"{asignatura}.json"
    
    # Cargar datos existentes si el archivo ya existe
    preguntas_totales = []
    if os.path.exists(archivo_path):
        try:
            with open(archivo_path, 'r', encoding='utf-8') as f:
                preguntas_totales = json.load(f)
            print(f"📖 Archivo '{archivo_path}' encontrado. Se añadirán las nuevas preguntas.")
        except:
            print("⚠️ El archivo existente estaba corrupto. Empezando de cero.")

    continuar_temas = True
    while continuar_temas:
        tema = input("\n📂 Número del tema (solo el número): ").strip()
        
        continuar_preguntas = True
        while continuar_preguntas:
            print("\n--- Nueva Pregunta ---")
            contexto = input("📝 Enunciado/Contexto (deja vacío si no hay): ").strip()
            texto = input("❓ Pregunta: ").strip()
            
            opciones = []
            for i in range(4):
                opciones.append(input(f"   Opción {i}: ").strip())
            
            try:
                correcta = int(input("✅ Índice de opción correcta (0-3): "))
            except ValueError:
                correcta = 0
                print("⚠️ Valor inválido, se asignó 0 por defecto.")

            # Crear el objeto de la pregunta
            nueva_q = {
                "subjectId": asignatura,
                "topicId": int(tema),
                "text": texto,
                "options": opciones,
                "correctOptionIndex": correcta
            }
            
            if contexto:
                nueva_q["contextText"] = contexto

            preguntas_totales.append(nueva_q)

            resp = input("\n¿Añadir otra pregunta a este TEMA? (s/n): ").lower()
            if resp != 's':
                continuar_preguntas = False

        resp_tema = input("¿Añadir otro TEMA a esta ASIGNATURA? (s/n): ").lower()
        if resp_tema != 's':
            continuar_temas = False

    # Guardar el resultado
    with open(archivo_path, 'w', encoding='utf-8') as f:
        json.dump(preguntas_totales, f, indent=4, ensure_ascii=False)
    
    print(f"\n🚀 ¡Listo! El archivo '{archivo_path}' ha sido actualizado con éxito.")

if __name__ == "__main__":
    menu_creador()