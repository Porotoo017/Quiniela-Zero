import sqlite3
import requests
import time
import logging
import sys
import os
import re
import csv
import datetime
from bs4 import BeautifulSoup
from collections import defaultdict, Counter
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

# --- CONFIGURACIÓN ---
DB_NAME = "quiniela_pro.db"
LOG_FILE = "quiniela_ops.log"
CSV_HISTORIAL = "provincia_completo.csv"
INTERVALO = 3600
ARCHIVO_FALLOS = "fallos_consecutivos.txt"

TG_TOKEN = os.getenv("TELEGRAM_TOKEN")
TG_CHAT_ID = os.getenv("TELEGRAM_CHAT_ID")

URL_PROVINCIA = "https://www.loteria.gba.gov.ar/index.php/resultados"

# --- LOGGING ---
try:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] - %(message)s",
        handlers=[logging.FileHandler(LOG_FILE, encoding="utf-8"), logging.StreamHandler(sys.stdout)],
    )
except Exception as e:
    print(f"Error crítico iniciando logs: {e}")
    sys.exit(1)

def get_session() -> requests.Session:
    session = requests.Session()
    retries = Retry(total=3, backoff_factor=1, status_forcelist=[502, 503, 504, 429])
    session.mount('https://', HTTPAdapter(max_retries=retries))
    session.headers.update({
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept-Language": "es-AR,es;q=0.9"
    })
    return session

def enviar_telegram(mensaje: str) -> None:
    if not TG_TOKEN or not TG_CHAT_ID:
        return
    try:
        url = f"https://api.telegram.org/bot{TG_TOKEN}/sendMessage"
        payload = {"chat_id": TG_CHAT_ID, "text": mensaje, "parse_mode": "HTML"}
        requests.post(url, json=payload, timeout=10)
    except Exception as e:
        logging.error(f"Error enviando a Telegram: {e}")

def validar_esquema(datos_str: str) -> bool:
    """Validación estricta: Exactamente 20 números de 4 dígitos."""
    if not datos_str:
        return False
    numeros = datos_str.split(",")
    if len(numeros) != 20:
        return False
    return all(n.isdigit() and len(n) == 4 for n in numeros)

def manejar_fallo_scraping() -> None:
    """Circuit Breaker Persistente: Alerta a Telegram tras 3 fallos consecutivos."""
    fallos = 0
    if os.path.exists(ARCHIVO_FALLOS):
        with open(ARCHIVO_FALLOS, "r") as f:
            try:
                fallos = int(f.read().strip())
            except ValueError:
                fallos = 0
                
    fallos += 1
    with open(ARCHIVO_FALLOS, "w") as f:
        f.write(str(fallos))
        
    logging.warning(f"Fallo de scraping detectado. Conteo actual: {fallos}")
    
    if fallos >= 3:
        msg = (
            "🚨 <b>ALERTA CRÍTICA EN TERMINATOR V4</b> 🚨\n\n"
            "El scraper ha fallado 3 veces consecutivas. Posible caída del servidor "
            "de Lotería GBA o cambio estructural en el HTML. Operando en modo Historial."
        )
        enviar_telegram(msg)
        logging.error("Circuit Breaker abierto (Trip). Alerta crítica emitida.")

def resetear_fallos() -> None:
    if os.path.exists(ARCHIVO_FALLOS):
        with open(ARCHIVO_FALLOS, "w") as f:
            f.write("0")
        logging.info("Circuit Breaker reseteado a estado óptimo (0 fallos).")

def init_db() -> None:
    try:
        with sqlite3.connect(DB_NAME) as conn:
            conn.execute("PRAGMA journal_mode=WAL;")
            conn.execute("PRAGMA synchronous=NORMAL;")
            try:
                conn.execute("SELECT turno FROM sorteos LIMIT 1")
            except sqlite3.OperationalError:
                conn.execute("DROP TABLE IF EXISTS sorteos")
            
            conn.execute("""
                CREATE TABLE IF NOT EXISTS sorteos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    loteria TEXT NOT NULL,
                    turno TEXT NOT NULL,
                    datos TEXT NOT NULL,
                    guardado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)
            conn.execute("CREATE INDEX IF NOT EXISTS idx_loteria_turno ON sorteos(loteria, turno)")
            logging.info("Base de datos inicializada y optimizada atómicamente.")
    except sqlite3.Error as e:
        logging.error(f"Error crítico DB: {e}")
        sys.exit(1)

def importar_historial() -> None:
    try:
        with sqlite3.connect(DB_NAME) as conn:
            count = conn.execute("SELECT COUNT(*) FROM sorteos").fetchone()[0]
            if count > 0:
                return
            if not os.path.exists(CSV_HISTORIAL):
                return

            logging.info(f"Importando historial optimizado desde {CSV_HISTORIAL}...")
            lote_datos = []
            
            with open(CSV_HISTORIAL, "r", encoding="utf-8") as f:
                reader = csv.reader(f)
                next(reader, None)
                for row in reader:
                    if len(row) >= 23:
                        turno = row[2].strip().upper()
                        numeros = row[3:23]
                        numeros_limpios = [n.strip() for n in numeros if n.strip().isdigit()]
                        
                        if len(numeros_limpios) == 20:
                            datos_str = ",".join(numeros_limpios)
                            if validar_esquema(datos_str):
                                lote_datos.append(("PROVINCIA", turno, datos_str))
                            
                        if len(lote_datos) >= 1000:
                            conn.executemany("INSERT INTO sorteos (loteria, turno, datos) VALUES (?, ?, ?)", lote_datos)
                            conn.commit()
                            lote_datos.clear()
                            
                if lote_datos:
                    conn.executemany("INSERT INTO sorteos (loteria, turno, datos) VALUES (?, ?, ?)", lote_datos)
                    conn.commit()
                    
            logging.info("¡ÉXITO! Historial importado, validado y persistido.")
    except Exception as e:
        logging.error(f"Error crítico importando CSV: {e}")

def scrape_web(session: requests.Session) -> str | None:
    try:
        r = session.get(URL_PROVINCIA, timeout=15)
        r.raise_for_status()
        soup = BeautifulSoup(r.text, "lxml")
        
        nodos = soup.find_all(string=re.compile(r'^\s*\d{4}\s*$'))
        numeros = [n.strip() for n in nodos if n.strip().isdigit()]

        if len(numeros) < 20:
            return None

        datos_str = ",".join(numeros[-20:])
        
        if validar_esquema(datos_str):
            return datos_str
        else:
            logging.error("Datos extraídos bloqueados por validación estricta de esquema.")
            return None
            
    except Exception as e:
        logging.error(f"Falla en red durante scraping: {e}")
        return None

def run_analytics(loteria: str, turno: str) -> tuple[list[tuple[str, float]], str | None]:
    try:
        with sqlite3.connect(DB_NAME) as conn:
            # CORRECCIÓN CRÍTICA AQUÍ: Se agregó el 'AND' en la consulta SQL
            filas = conn.execute(
                "SELECT datos FROM sorteos WHERE loteria=? AND turno=? ORDER BY id", 
                (loteria, turno)
            ).fetchall()
    except sqlite3.Error as e:
        logging.error(f"Error consultando base de datos para analítica: {e}")
        return [], None
    
    if not filas:
        return [], None
    
    transiciones_1 = defaultdict(list)
    transiciones_2 = defaultdict(list)
    total_filas = len(filas)
    peso_reciente = 3.0 
    limite_reciente = max(0, total_filas - 500) 

    for i, (datos,) in enumerate(filas):
        nums = [n[-2:] for n in datos.split(",") if n]
        peso = int(peso_reciente) if i >= limite_reciente else 1
        
        for a, b in zip(nums, nums[1:]):
            transiciones_1[a].extend([b] * peso)
            
        for a, b, c in zip(nums, nums[1:], nums[2:]):
            transiciones_2[(a, b)].extend([c] * peso)

    ultimo_sorteo = filas[-1][0].split(",")
    ultimo = ultimo_sorteo[-1][-2:]
    penultimo = ultimo_sorteo[-2][-2:] if len(ultimo_sorteo) >= 2 else None

    top = []
    
    if penultimo:
        siguientes_2 = transiciones_2.get((penultimo, ultimo), [])
        if len(siguientes_2) >= 5:
            total_2 = len(siguientes_2)
            top = [(num, freq / total_2) for num, freq in Counter(siguientes_2).most_common(3)]
            logging.info(f"[{turno}] Ejecución Markov 2do Orden (Par: {penultimo}-{ultimo}).")
    
    if not top:
        siguientes_1 = transiciones_1.get(ultimo, [])
        if siguientes_1:
            total_1 = len(siguientes_1)
            top = [(num, freq / total_1) for num, freq in Counter(siguientes_1).most_common(3)]
            logging.info(f"[{turno}] Fallback a Markov 1er Orden (Último: {ultimo}).")

    return top, ultimo

def ejecutar_ciclo(session: requests.Session) -> bool:
    cambios = False
    reporte = "🏆 <b>REPORTE TERMINATOR V4 (INDUSTRIAL)</b> 🏆\n\n"
    
    ahora_arg = datetime.datetime.utcnow() - datetime.timedelta(hours=3)
    hora_actual = ahora_arg.hour

    if hora_actual < 11:
        turno_detectado = "LA PREVIA"
    elif hora_actual < 13:
        turno_detectado = "EL PRIMERO"
    elif hora_actual < 16:
        turno_detectado = "MATUTINA"
    elif hora_actual < 19:
        turno_detectado = "VESPERTINA"
    else:
        turno_detectado = "NOCTURNA"
    
    logging.info(f"Ciclo iniciado a las {hora_actual}hs ARG. Turno asignado: {turno_detectado}")

    data_web = scrape_web(session)
    
    if data_web:
        resetear_fallos()
        try:
            with sqlite3.connect(DB_NAME) as conn:
                prev = conn.execute(
                    "SELECT datos FROM sorteos WHERE loteria='PROVINCIA' AND turno=? ORDER BY id DESC LIMIT 1",
                    (turno_detectado,)
                ).fetchone()
                
                if not prev or prev[0] != data_web:
                    conn.execute(
                        "INSERT INTO sorteos (loteria, turno, datos) VALUES (?,?,?)",
                        ("PROVINCIA", turno_detectado, data_web)
                    )
                    conn.commit()
                    cambios = True
                    logging.info(f"Registro atómico insertado con éxito en turno: {turno_detectado}.")
        except sqlite3.Error as e:
            logging.error(f"Error transaccional al insertar en BD: {e}")
    else:
        manejar_fallo_scraping()

    turnos_analizar = [turno_detectado, "MATUTINA", "VESPERTINA", "NOCTURNA"]
    
    for t in set(turnos_analizar): 
        top, ultimo = run_analytics("PROVINCIA", t)
        if ultimo:
            candidatos = [f"<b>{n}</b> ({p:.1%})" for n, p in top] or ["sin datos suficientes"]
            reporte += f"🎲 Turno: <b>{t}</b>\n"
            reporte += f"🔢 Último: <code>{ultimo}</code>\n"
            reporte += f"🎯 Candidatos: {', '.join(candidatos)}\n"
            reporte += "➖➖➖➖➖➖➖➖➖➖\n\n"

    try:
        with open("resultados.txt", "w", encoding="utf-8") as f:
            f.write(reporte)
    except Exception as e:
        logging.error(f"Error escribiendo archivo de resultados locales: {e}")
        
    if cambios:
        enviar_telegram(reporte)
        
    return cambios

def main() -> None:
    init_db()
    importar_historial()
    logging.info("=== MOTOR TERMINATOR V4 (INDUSTRIAL) 100% OPERATIVO ===")

    session = get_session()
    
    if os.getenv("GITHUB_ACTIONS") == "true":
        cambios = ejecutar_ciclo(session)
        print(f"CAMBIOS={1 if cambios else 0}")
        return

    try:
        while True:
            ejecutar_ciclo(session)
            time.sleep(INTERVALO)
    except KeyboardInterrupt:
        logging.info("Interrupción de sistema detectada. Apagando motor de forma segura.")

if __name__ == "__main__":
    main()
