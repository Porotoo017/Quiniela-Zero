import sqlite3
import requests
import time
import logging
import sys
import os
import re
import csv
from bs4 import BeautifulSoup
from collections import defaultdict, Counter

# --- CONFIGURACIÓN ---
DB_NAME = "quiniela_pro.db"
LOG_FILE = "quiniela_ops.log"
INTERVALO = 3600
CSV_HISTORIAL = "provincia_completo.csv"

# --- TELEGRAM CONFIG ---
TG_TOKEN = os.getenv("TELEGRAM_TOKEN")
TG_CHAT_ID = os.getenv("TELEGRAM_CHAT_ID")

LOTERIAS = [
    {"nombre": "PROVINCIA", "url": "https://www.loteria.gba.gov.ar/index.php/resultados", "selector": "N/A"}
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

def enviar_telegram(mensaje):
    if not TG_TOKEN or not TG_CHAT_ID:
        logging.warning("Faltan credenciales de Telegram. Omitiendo envío.")
        return
    try:
        url = f"https://api.telegram.org/bot{TG_TOKEN}/sendMessage"
        payload = {"chat_id": TG_CHAT_ID, "text": mensaje}
        respuesta = requests.post(url, json=payload, timeout=10)
        logging.info(f"Respuesta de Telegram: {respuesta.status_code}")
    except Exception as e:
        logging.error(f"Error enviando a Telegram: {e}")

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

def importar_historial():
    archivos = {
        "PROVINCIA": "provincia_completo.csv"
    }

    with sqlite3.connect(DB_NAME) as conn:
        for loteria, archivo in archivos.items():
            count = conn.execute("SELECT COUNT(*) FROM sorteos WHERE loteria=?", (loteria,)).fetchone()[0]
            if count > 0:
                continue

            if not os.path.exists(archivo):
                logging.warning(f"No se encontró {archivo} para importar.")
                continue

            logging.info(f"Base de datos vacía para {loteria}. Importando historial desde {archivo}...")
            insertados = 0
            
            try:
                with open(archivo, "r", encoding="utf-8") as f:
                    reader = csv.reader(f)
                    next(reader, None)
                    
                    for row in reader:
                        if len(row) >= 23:
                            numeros = row[3:23] 
                            numeros_limpios = [n.strip() for n in numeros if n.strip().isdigit()]
                            
                            if len(numeros_limpios) == 20:
                                datos = ",".join(numeros_limpios)
                                conn.execute("INSERT INTO sorteos (loteria, datos) VALUES (?, ?)", (loteria, datos))
                                insertados += 1
                logging.info(f"¡ÉXITO! Se importaron {insertados} sorteos históricos de {loteria}.")
            except Exception as e:
                logging.error(f"Error importando {archivo}: {e}")

def scrape(loteria_cfg):
    try:
        headers = {"User-Agent": "Mozilla/5.0 (Quiniela-Bot/2.0)"}
        r = requests.get(loteria_cfg["url"], headers=headers, timeout=15)
        r.raise_for_status()
        soup = BeautifulSoup(r.text, "html.parser")
        
        nodos_4_digitos = soup.find_all(string=re.compile(r'^\s*\d{4}\s*$'))
        numeros = [nodo.strip() for nodo in nodos_4_digitos]

        if not numeros:
            return None

        return ",".join(numeros[-20:])
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
    reporte = "=== REPORTE DE QUINIELA-ZERO ===\n\n"
    hubo_datos = False
    
    for loteria in LOTERIAS:
        data = scrape(loteria)
        
        # Si la página está vacía, usamos el historial
        if not data:
            logging.warning(f"{loteria['nombre']}: No se encontraron números en la web.")
            top, ultimo = run_analytics(loteria["nombre"])
            if ultimo:
                hubo_datos = True
                candidatos = [f"{n} ({p:.1%})" for n, p in top] or ["sin datos suficientes"]
                reporte += f"Lotería: {loteria['nombre']}\nÚltimo número: {ultimo} (Historial)\nCandidatos: {', '.join(candidatos)}\n----------------\n\n"
            continue

        hubo_datos = True

        # Si hay datos, comparamos con la base
        with sqlite3.connect(DB_NAME) as conn:
            prev = conn.execute(
                "SELECT datos FROM sorteos WHERE loteria=? ORDER BY id DESC LIMIT 1",
                (loteria["nombre"],),
            ).fetchone()
            
            if prev and prev[0] == data:
                logging.info(f"{loteria['nombre']}: Sin cambios en la web.")
                top, ultimo = run_analytics(loteria["nombre"])
                candidatos = [f"{n} ({p:.1%})" for n, p in top] or ["sin datos suficientes"]
                reporte += f"Lotería: {loteria['nombre']}\nÚltimo número: {ultimo} (Sin cambios)\nCandidatos: {', '.join(candidatos)}\n----------------\n\n"
                continue
                
            conn.execute(
                "INSERT INTO sorteos (loteria, datos) VALUES (?,?)",
                (loteria["nombre"], data),
            )
            cambios = True

        top, ultimo = run_analytics(loteria["nombre"])
        candidatos = [f"{n} ({p:.1%})" for n, p in top] or ["sin datos suficientes"]
        reporte += f"Lotería: {loteria['nombre']}\nÚltimo número: {ultimo}\nCandidatos: {', '.join(candidatos)}\n----------------\n\n"
        logging.info(f"{loteria['nombre']}: Nuevo registro. Último: {ultimo}. Candidatos: {candidatos}")
    
    if hubo_datos:
        with open("resultados.txt", "w", encoding="utf-8") as f:
            f.write(reporte)
        
        # EL CANDADO FINAL: Solo envía a Telegram si hubo un número NUEVO
        if cambios:
            enviar_telegram(reporte)
        
    return cambios

def main():
    init_db()
    importar_historial()
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
