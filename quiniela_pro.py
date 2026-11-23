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
TURNOS_OFICIALES = ["LA PREVIA", "PRIMERA", "MATUTINA", "VESPERTINA", "NOCTURNA"]

TURNO_ALIASES = {
    "LA PREVIA": "LA PREVIA",
    "PREVIA": "LA PREVIA",
    "EL PRIMERO": "PRIMERA",
    "PRIMERA": "PRIMERA",
    "LA PRIMERA": "PRIMERA",
    "MATUTINA": "MATUTINA",
    "VESPERTINA": "VESPERTINA",
    "NOCTURNA": "NOCTURNA",
}

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
    retries = Retry(
        total=4,
        backoff_factor=1.5,
        status_forcelist=[429, 500, 502, 503, 504],
        allowed_methods=["GET", "HEAD"],
    )
    session.mount("https://", HTTPAdapter(max_retries=retries))
    session.mount("http://", HTTPAdapter(max_retries=retries))
    session.headers.update({
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "es-AR,es;q=0.9,en;q=0.8",
        "Cache-Control": "no-cache",
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


def normalizar_turno(titulo: str) -> str | None:
    up = (titulo or "").upper()
    for alias, canon in TURNO_ALIASES.items():
        if alias in up:
            return canon
    return None


def leer_fallos() -> int:
    if not os.path.exists(ARCHIVO_FALLOS):
        return 0
    try:
        with open(ARCHIVO_FALLOS, "r", encoding="utf-8") as f:
            return int(f.read().strip() or "0")
    except (ValueError, OSError):
        return 0


def manejar_fallo_scraping(motivo: str) -> None:
    """Circuit Breaker Persistente: Solo alerta por caídas HTTP reales o cambio estructural HTML."""
    fallos = leer_fallos() + 1
    with open(ARCHIVO_FALLOS, "w", encoding="utf-8") as f:
        f.write(str(fallos))

    logging.warning(f"Fallo técnico real de scraping ({motivo}). Conteo actual: {fallos}")

    # Evitar spam cada hora tras alertar: alerta en fallo 3, 6, 12, etc.
    if fallos in (3, 6, 12, 24):
        msg = (
            "🚨 <b>ALERTA CRÍTICA EN TERMINATOR V4</b> 🚨\n\n"
            f"El scraper falló <b>{fallos} veces consecutivas</b>.\n"
            f"Motivo detectado: <code>{motivo}</code>\n"
            "Operando temporalmente en modo Historial."
        )
        enviar_telegram(msg)
        logging.error("Circuit Breaker abierto (Trip). Alerta crítica emitida.")


def resetear_fallos(avisar_recuperacion: bool = True) -> None:
    prev = leer_fallos()
    with open(ARCHIVO_FALLOS, "w", encoding="utf-8") as f:
        f.write("0")
    if prev > 0:
        logging.info(f"Circuit Breaker reseteado a estado óptimo (0 fallos, previo: {prev}).")
        if avisar_recuperacion and prev >= 3:
            enviar_telegram(
                "✅ <b>TERMINATOR V4 RECUPERADO</b>\n\n"
                "Lotería GBA volvió a responder correctamente y el Circuit Breaker quedó en 0 fallos."
            )


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
                    fecha_sorteo TEXT DEFAULT '',
                    guardado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)
            cols = {r[1] for r in conn.execute("PRAGMA table_info(sorteos)").fetchall()}
            if "fecha_sorteo" not in cols:
                conn.execute("ALTER TABLE sorteos ADD COLUMN fecha_sorteo TEXT DEFAULT ''")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_loteria_turno ON sorteos(loteria, turno)")
            conn.execute("CREATE INDEX IF NOT EXISTS idx_loteria_turno_fecha ON sorteos(loteria, turno, fecha_sorteo)")
            conn.commit()
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
                        fecha_iso = row[0].strip()
                        turno = TURNO_ALIASES.get(row[2].strip().upper(), row[2].strip().upper())
                        numeros = row[3:23]
                        numeros_limpios = [n.strip() for n in numeros if n.strip().isdigit()]

                        if len(numeros_limpios) == 20:
                            datos_str = ",".join(numeros_limpios)
                            if validar_esquema(datos_str):
                                lote_datos.append(("PROVINCIA", turno, datos_str, fecha_iso))

                        if len(lote_datos) >= 1000:
                            conn.executemany(
                                "INSERT INTO sorteos (loteria, turno, datos, fecha_sorteo) VALUES (?, ?, ?, ?)",
                                lote_datos,
                            )
                            conn.commit()
                            lote_datos.clear()

                if lote_datos:
                    conn.executemany(
                        "INSERT INTO sorteos (loteria, turno, datos, fecha_sorteo) VALUES (?, ?, ?, ?)",
                        lote_datos,
                    )
                    conn.commit()

            logging.info("¡ÉXITO! Historial importado, validado y persistido.")
    except Exception as e:
        logging.error(f"Error crítico importando CSV: {e}")


def parse_gba_html(html_text: str) -> dict:
    """
    Parser estructural por bloque oficial de Lotería GBA (div.sorteo).
    Distingue explícitamente entre:
      - OK: al menos un turno publicado con 20 bolillas válidas de 4 dígitos
      - PENDING: HTML íntegro (div.sorteo + div.bolillas presentes), turnos aún en '----'
      - ERROR: HTML inválido / cambio estructural real
    """
    soup = BeautifulSoup(html_text, "lxml")
    bloques = soup.select("div.sorteo")
    if not bloques:
        return {"status": "ERROR", "reason": "No se encontraron bloques div.sorteo en HTML", "turnos": [], "pendientes": []}

    turnos_listos = []
    pendientes = []
    bolillas_detectadas = 0

    for bloque in bloques:
        h3 = bloque.find("h3")
        titulo = h3.get_text(" ", strip=True) if h3 else ""
        turno_canon = normalizar_turno(titulo)
        if not turno_canon:
            continue

        fecha_el = bloque.select_one(".fechaSorteo")
        fecha_raw = fecha_el.get_text(strip=True) if fecha_el else ""
        # Normalizar DD/MM/YYYY -> YYYY-MM-DD si coincide
        fecha_iso = fecha_raw
        m_fecha = re.match(r"^(\d{2})/(\d{2})/(\d{4})$", fecha_raw)
        if m_fecha:
            fecha_iso = f"{m_fecha.group(3)}-{m_fecha.group(2)}-{m_fecha.group(1)}"

        sorteo_el = bloque.select_one(".nSorteo")
        nro_sorteo = sorteo_el.get_text(strip=True) if sorteo_el else ""

        bolillas_box = bloque.select_one("div.bolillas")
        if not bolillas_box:
            continue
        bolillas_detectadas += 1

        # Cada posición tiene 2 spans: <span>01</span><span>1234</span> o <span>----</span>
        pares = {}
        for item in bolillas_box.find_all("div", recursive=True):
            spans = item.find_all("span", recursive=False)
            if len(spans) >= 2:
                pos_txt = spans[0].get_text(strip=True)
                val_txt = spans[1].get_text(strip=True)
                if pos_txt.isdigit():
                    pos = int(pos_txt)
                    if 1 <= pos <= 20:
                        pares[pos] = val_txt

        if len(pares) != 20:
            # Fallback por orden secuencial de spans si hubiera variación mínima de divs
            spans_all = [s.get_text(strip=True) for s in bolillas_box.find_all("span")]
            if len(spans_all) >= 40:
                pares = {}
                for idx in range(0, 40, 2):
                    pos_txt, val_txt = spans_all[idx], spans_all[idx + 1]
                    if pos_txt.isdigit():
                        pares[int(pos_txt)] = val_txt

        if len(pares) != 20:
            continue

        valores = [pares[i] for i in range(1, 21)]
        if all(re.fullmatch(r"\d{4}", v) for v in valores):
            datos_str = ",".join(valores)
            if validar_esquema(datos_str):
                turnos_listos.append({
                    "turno": turno_canon,
                    "fecha_raw": fecha_raw,
                    "fecha_iso": fecha_iso,
                    "nro_sorteo": nro_sorteo,
                    "datos": datos_str,
                })
        elif any("-" in v for v in valores):
            pendientes.append(f"{turno_canon} ({fecha_raw or 'hoy'})")

    if turnos_listos:
        return {"status": "OK", "turnos": turnos_listos, "pendientes": pendientes, "reason": ""}
    if bolillas_detectadas > 0 and pendientes:
        return {"status": "PENDING", "turnos": [], "pendientes": pendientes, "reason": "Turnos en espera (----)"}
    return {"status": "ERROR", "reason": "Bloques div.bolillas ausentes o formato modificado", "turnos": [], "pendientes": []}


def scrape_web(session: requests.Session) -> dict:
    try:
        r = session.get(URL_PROVINCIA, timeout=20)
        r.raise_for_status()
        return parse_gba_html(r.text)
    except Exception as e:
        logging.error(f"Falla en red durante scraping: {e}")
        return {"status": "ERROR", "reason": str(e), "turnos": [], "pendientes": []}


def run_analytics(loteria: str, turno: str) -> tuple[list[tuple[str, float]], str | None]:
    try:
        with sqlite3.connect(DB_NAME) as conn:
            filas = conn.execute(
                "SELECT datos FROM sorteos WHERE loteria=? AND turno=? ORDER BY id",
                (loteria, turno),
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
    ahora_arg = datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(hours=3)
    fecha_hoy = ahora_arg.strftime("%d/%m/%Y")
    hora_actual = ahora_arg.hour

    logging.info(f"Ciclo iniciado a las {hora_actual:02d}:{ahora_arg.minute:02d}hs ARG ({fecha_hoy}).")

    resultado = scrape_web(session)
    nuevos_guardados = []

    if resultado["status"] == "OK":
        resetear_fallos(avisar_recuperacion=True)
        try:
            with sqlite3.connect(DB_NAME) as conn:
                # Últimos extractos guardados recientemente para evitar duplicación cruzada entre turnos
                recientes_any = {
                    r[0]
                    for r in conn.execute(
                        "SELECT datos FROM sorteos WHERE loteria='PROVINCIA' ORDER BY id DESC LIMIT 8"
                    ).fetchall()
                }
                for item in resultado["turnos"]:
                    turno = item["turno"]
                    fecha_iso = item["fecha_iso"]
                    datos = item["datos"]

                    prev_turno = conn.execute(
                        "SELECT datos, fecha_sorteo FROM sorteos WHERE loteria='PROVINCIA' AND turno=? ORDER BY id DESC LIMIT 1",
                        (turno,),
                    ).fetchone()

                    ya_existe_fecha = bool(
                        fecha_iso
                        and conn.execute(
                            "SELECT 1 FROM sorteos WHERE loteria='PROVINCIA' AND turno=? AND fecha_sorteo=?",
                            (turno, fecha_iso),
                        ).fetchone()
                    )

                    if ya_existe_fecha or (prev_turno and prev_turno[0] == datos):
                        continue

                    if datos in recientes_any:
                        logging.warning(f"Omitiendo {turno} ({fecha_iso}): mismos números ya registrados en otro turno reciente.")
                        continue

                    conn.execute(
                        "INSERT INTO sorteos (loteria, turno, datos, fecha_sorteo) VALUES (?, ?, ?, ?)",
                        ("PROVINCIA", turno, datos, fecha_iso),
                    )
                    conn.commit()
                    recientes_any.add(datos)
                    cambios = True
                    nuevos_guardados.append(item)
                    logging.info(f"Registro atómico insertado con éxito: {turno} ({item['fecha_raw']} {item['nro_sorteo']}).")
        except sqlite3.Error as e:
            logging.error(f"Error transaccional al insertar en BD: {e}")

    elif resultado["status"] == "PENDING":
        # Sitio saludable pero sin sorteo publicado todavía (ej. feriado, domingo o antes de 10:15 ART)
        resetear_fallos(avisar_recuperacion=True)
        logging.info(f"Lotería GBA operativa en espera de sorteo ({', '.join(resultado['pendientes'])}).")

    else:
        manejar_fallo_scraping(resultado.get("reason") or "Error desconocido")

    reporte = f"🏆 <b>REPORTE TERMINATOR V4 (INDUSTRIAL) - {fecha_hoy}</b> 🏆\n\n"

    if nuevos_guardados:
        reporte += "🆕 <b>EXTRACTOS OFICIALES PUBLICADOS</b>\n"
        for item in nuevos_guardados:
            cabeza = item["datos"].split(",")[0]
            top5 = " · ".join(item["datos"].split(",")[:5])
            reporte += f"• <b>{item['turno']}</b> ({item['fecha_raw']}): Cabeza <code>{cabeza}</code> | Top 5: <code>{top5}</code>\n"
        reporte += "\n"

    for t in TURNOS_OFICIALES:
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
