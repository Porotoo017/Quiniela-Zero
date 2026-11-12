import sqlite3
import requests
import time
import logging
import sys
import os
import re
from bs4 import BeautifulSoup
from collections import defaultdict, Counter

# --- CONFIGURACIÓN ---
DB_NAME = "quiniela_pro.db"
LOG_FILE = "quiniela_ops.log"
INTERVALO = 3600

LOTERIAS = [
    {"nombre": "CIUDAD", "url": "https://www.loteriadelaciudad.gob.ar/resultados.html", "selector": ".premio"},
    {"nombre": "PROVINCIA", "url": "https://www.loteria.gba.gov.ar/index.php/resultados", "selector": ".premio"}
]

# --- LOGGING ---
try:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s - %(levelname)s - %(message)s",
        handlers=[logging.FileHandler(LOG_FILE), logging.StreamHandler(sys.stdout)],
    )
except Exception as e:
    print(f"Error crítico iniciando logs: {e}")
    sys.exit(1)

def init_db():
    with sqlite3.connect(DB_NAME) as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS sorteos (
                id INTEGER PRIMARY KEY,
                loteria TEXT NOT NULL,
                datos TEXT NOT NULL,
                guardado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.execute("CREATE INDEX IF NOT EXISTS idx_loteria ON sorteos(loteria)")

def scrape(loteria_cfg):
    try:
        headers = {"User-Agent": "Mozilla/5.0 (Quiniela-Bot/2.0)"}
        r = requests.get(loteria_cfg["url"], headers=headers, timeout=15)
        r.raise_for_status()
        soup = BeautifulSoup(r.text, "html.parser")
        
        # 1. Intentamos con el selector de la config
        items = [s.get_text(strip=True) for s in soup.select(loteria_cfg["selector"])]
        numeros = [n for n in items if n.isdigit() and len(n) == 4]
        
        # 2. BLOQUE DE DIAGNÓSTICO AVANZADO (si el selector falla)
        if len(numeros) < 20:
            print(f"\n--- ATENCION: Selector '{loteria_cfg['selector']}' falló en {loteria_cfg['nombre']} ---")
            print(f"--- BUSCANDO CUALQUIER TEXTO DE 4 DÍGITOS EN LA PAGINA ---")
            
            # Buscamos todos los textos que sean exactamente 4 números
            nodos_4_digitos = soup.find_all(string=re.compile(r'^\s*\d{4}\s*$'))
            
            if not nodos_4_digitos:
                print("¡No se encontró NINGÚN número de 4 dígitos en el HTML! (Puede que carguen por JavaScript).")
            else:
                print(f"Se encontraron {len(nodos_4_digitos)} textos de 4 dígitos. Aquí están los primeros 10 y sus etiquetas HTML:")
                for nodo in nodos_4_digitos[:10]:
                    padre = nodo.parent
                    clases = padre.get('class', [])
                    print(f" -> Numero: {nodo.strip()} | Etiqueta: <{padre.name}> | Clases: {clases} | ID: {padre.get('id')}")
            print("------------------------------------------------------------\n")

        return ",".join(numeros[-20:]) if len(numeros) >= 20 else None
    except Exception as e:
        logging.error(f"Fallo en scraping {loteria_cfg['nombre']}: {e}")
        return None

def run_analytics(loteria_nombre):
    with sqlite3.connect(DB_NAME) as conn:
        filas = conn.execute("SELECT datos FROM sorteos WHERE loteria=? ORDER BY id", (loteria_nombre,)).fetchall()
    
    if not filas:
        return [], None
    
    transiciones = defaultdict(list)
    for (datos,) in filas:
        nums = [n[-2:] for n in datos.split(",") if n]
        for a, b in zip(nums, nums[1:]):
            transiciones[a].append(b)

    ultimo = filas[-1][0].split(",")[-1][-2:]
    siguientes = transiciones.get(ultimo, [])
    if not siguientes:
        return [], ultimo

    total = len(siguientes)
    top = [(num, freq / total) for num, freq in Counter(siguientes).most_common(3)]
    return top, ultimo

def ejecutar_ciclo():
    cambios = False
    for loteria in LOTERIAS:
        data = scrape(loteria)
        if not data:
            continue

        with sqlite3.connect(DB_NAME) as conn:
            prev = conn.execute(
                "SELECT datos FROM sorteos WHERE loteria=? ORDER BY id DESC LIMIT 1",
                (loteria["nombre"],),
            ).fetchone()
            if prev and prev[0] == data:
                logging.info(f"{loteria['nombre']}: Sin cambios.")
                continue
            conn.execute(
                "INSERT INTO sorteos (loteria, datos) VALUES (?,?)",
                (loteria["nombre"], data),
            )
            cambios = True

        top, ultimo = run_analytics(loteria["nombre"])
        candidatos = [f"{n} ({p:.1%})" for n, p in top] or ["sin datos suficientes"]
        logging.info(
            f"{loteria['nombre']}: Nuevo registro. "
            f"Último: {ultimo}. Candidatos: {candidatos}"
        )
    return cambios

def main():
    init_db()
    logging.info("=== MOTOR QUINIELA-ZERO INICIADO ===")

    modo_ci = os.getenv("GITHUB_ACTIONS") == "true"

    if modo_ci:
        cambios = ejecutar_ciclo()
        print(f"CAMBIOS={1 if cambios else 0}")
        return

    try:
        while True:
            ejecutar_ciclo()
            logging.info(f"Esperando {INTERVALO}s hasta el próximo ciclo...")
            time.sleep(INTERVALO)
    except KeyboardInterrupt:
        logging.info("Sistema detenido por el usuario.")

if __name__ == "__main__":
    main()
