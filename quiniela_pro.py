import csv
import datetime
import json
import logging
import math
import os
import re
import sqlite3
import sys
import time
from collections import Counter, defaultdict
from dataclasses import dataclass
from typing import Any, Callable
from zoneinfo import ZoneInfo

import requests
from bs4 import BeautifulSoup

# --- CONFIGURACIÓN GENERAL ---
TZ_ARG = ZoneInfo("America/Argentina/Buenos_Aires")
DB_NAME = os.getenv("QUINIELA_DB", "quiniela_pro.db")
LOG_FILE = os.getenv("QUINIELA_LOG", "quiniela_ops.log")
CSV_PROVINCIA = os.getenv("CSV_PROVINCIA", "provincia_completo.csv")
CSV_CIUDAD = os.getenv("CSV_CIUDAD", "ciudad_completo.csv")
CSV_HISTORIAL = CSV_PROVINCIA  # Compatibilidad retroactiva
SNAPSHOT_JSON = os.getenv("SNAPSHOT_JSON", "sync_snapshot.json")
ARCHIVO_FALLOS = os.getenv("ARCHIVO_FALLOS", "fallos_consecutivos.txt")
INTERVALO = 3600
HTTP_TIMEOUT = 10
RETRY_BACKOFFS = (2, 8, 30)

TG_TOKEN = os.getenv("TELEGRAM_TOKEN")
TG_CHAT_ID = os.getenv("TELEGRAM_CHAT_ID")

URL_PROVINCIA = "https://www.loteria.gba.gov.ar/index.php/resultados"
URL_CIUDAD_HOME = "https://quiniela.loteriadelaciudad.gob.ar/"
URL_CIUDAD_DATA = "https://quiniela.loteriadelaciudad.gob.ar/includes/resultados-data.php?sorteo={sorteo_id}"
URL_VIVI_PROVINCIA = "https://www.vivitusuerte.com/pizarra/provincia"
URL_VIVI_CIUDAD = "https://www.vivitusuerte.com/pizarra/ciudad"
URL_RUTA_PROVINCIA = "https://www.ruta1000.com.ar/index2008.php?Resultado=Quiniela_de_la_Provincia"
URL_RUTA_CIUDAD = "https://www.ruta1000.com.ar/index2008.php?Resultado=Quiniela_de_la_Ciudad"

TURNOS_OFICIALES = ["LA PREVIA", "PRIMERA", "MATUTINA", "VESPERTINA", "NOCTURNA"]
JURISDICCIONES = ["PROVINCIA", "CIUDAD"]

TURNO_ALIASES = {
    "LA PREVIA": "LA PREVIA",
    "PREVIA": "LA PREVIA",
    "EL PRIMERO": "PRIMERA",
    "LA PRIMERA": "PRIMERA",
    "PRIMERA": "PRIMERA",
    "MATUTINA": "MATUTINA",
    "VESPERTINA": "VESPERTINA",
    "NOCTURNA": "NOCTURNA",
    "NOCT": "NOCTURNA",
    "MAT": "MATUTINA",
    "VESP": "VESPERTINA",
    "PRIM": "PRIMERA",
    "PREV": "LA PREVIA",
}

ESTADOS_VALIDOS = {"VERIFICADO", "NO VERIFICADO", "PENDIENTE", "HISTORIAL"}

# --- LOGGING ---
try:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] - %(message)s",
        handlers=[
            logging.FileHandler(LOG_FILE, encoding="utf-8"),
            logging.StreamHandler(sys.stdout),
        ],
    )
except Exception as exc:
    print(f"Error crítico iniciando logs: {exc}")
    sys.exit(1)


def ahora_argentina() -> datetime.datetime:
    return datetime.datetime.now(TZ_ARG)


def get_session() -> requests.Session:
    session = requests.Session()
    session.headers.update(
        {
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            ),
            "Accept": "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
            "Accept-Language": "es-AR,es;q=0.9,en;q=0.8",
            "Cache-Control": "no-cache",
        }
    )
    return session


def fetch_with_backoff(
    session: requests.Session,
    url: str,
    timeout: int = HTTP_TIMEOUT,
    backoffs: tuple[int, ...] = RETRY_BACKOFFS,
) -> requests.Response:
    last_exc: Exception | None = None
    attempts = len(backoffs) + 1
    for idx in range(attempts):
        try:
            resp = session.get(url, timeout=timeout)
            resp.raise_for_status()
            return resp
        except Exception as exc:
            last_exc = exc
            if idx < len(backoffs):
                wait_s = backoffs[idx]
                logging.warning(f"Reintento {idx + 1}/{len(backoffs)} para {url} en {wait_s}s ({exc})")
                time.sleep(wait_s)
    raise RuntimeError(f"Error descargando {url}: {last_exc}")


def enviar_telegram(mensaje: str) -> None:
    if not TG_TOKEN or not TG_CHAT_ID:
        return
    try:
        url = f"https://api.telegram.org/bot{TG_TOKEN}/sendMessage"
        payload = {"chat_id": TG_CHAT_ID, "text": mensaje, "parse_mode": "HTML"}
        requests.post(url, json=payload, timeout=HTTP_TIMEOUT)
    except Exception as exc:
        logging.error(f"Error enviando a Telegram: {exc}")


def normalizar_numero_4(valor: str) -> str | None:
    dig = re.sub(r"\D", "", str(valor or ""))
    if not dig or len(dig) > 4:
        return None
    return dig.zfill(4)


def validar_esquema(datos_str: str) -> bool:
    """Validación estricta: exactamente 20 números de 4 dígitos entre 0000 y 9999."""
    if not datos_str:
        return False
    numeros = [n.strip() for n in datos_str.split(",")]
    if len(numeros) != 20:
        return False
    return all(re.fullmatch(r"\d{4}", n) is not None for n in numeros)


def normalizar_turno(titulo: str) -> str | None:
    up = re.sub(r"\s+", " ", (titulo or "").upper()).strip()
    for alias in sorted(TURNO_ALIASES.keys(), key=len, reverse=True):
        if alias in up:
            return TURNO_ALIASES[alias]
    return None


def normalizar_fecha_iso(raw: str) -> str | None:
    txt = (raw or "").strip()
    m_iso = re.match(r"^(\d{4})-(\d{2})-(\d{2})$", txt)
    if m_iso:
        return txt
    m_dmy4 = re.match(r"^(\d{2})/(\d{2})/(\d{4})$", txt)
    if m_dmy4:
        d, m, y = m_dmy4.groups()
        return f"{y}-{m}-{d}"
    m_dmy2 = re.match(r"^(\d{2})/(\d{2})/(\d{2})$", txt)
    if m_dmy2:
        d, m, y = m_dmy2.groups()
        return f"20{y}-{m}-{d}"
    return None


def validar_integridad_registro(
    loteria: str,
    fecha_iso: str | None,
    turno: str | None,
    datos_str: str,
    calendar: "SorteoCalendar | None" = None,
) -> tuple[bool, str]:
    if loteria not in JURISDICCIONES:
        return False, f"Jurisdicción inválida: {loteria}"
    if not fecha_iso or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", fecha_iso):
        return False, f"Fecha inválida: {fecha_iso}"
    try:
        fecha_dt = datetime.date.fromisoformat(fecha_iso)
    except ValueError:
        return False, f"Fecha no calendárica: {fecha_iso}"
    if fecha_dt.weekday() == 6:
        return False, f"Domingo sin sorteos oficiales: {fecha_iso}"
    if turno not in TURNOS_OFICIALES:
        return False, f"Turno inválido: {turno}"
    if calendar and not calendar.es_turno_habilitado(loteria, fecha_dt, turno):
        return False, f"Turno {turno} no habilitado para {loteria} el {fecha_iso}"
    if not validar_esquema(datos_str):
        return False, "Extracto no contiene exactamente 20 números de 4 dígitos"
    return True, "OK"


# --- CALENDARIO DINÁMICO DE SORTEOS ---
class SorteoCalendar:
    """
    Deduce turnos reales por día de la semana a partir del historial cargado,
    bloquea domingos (0 sorteos) y respeta modo feriado configurable.
    """

    def __init__(self, holiday_dates: set[str] | None = None, holiday_mode: bool = False):
        self.holiday_dates = set(holiday_dates or set())
        self.holiday_mode = holiday_mode
        self.weekday_turns: dict[str, list[str]] = {j: list(TURNOS_OFICIALES) for j in JURISDICCIONES}
        self.saturday_turns: dict[str, list[str]] = {j: list(TURNOS_OFICIALES) for j in JURISDICCIONES}

    def calibrate_from_db(self, db_path: str = DB_NAME) -> None:
        if not os.path.exists(db_path):
            return
        try:
            with sqlite3.connect(db_path) as conn:
                for jur in JURISDICCIONES:
                    rows = conn.execute(
                        "SELECT fecha_sorteo, turno FROM sorteos WHERE loteria=? AND fecha_sorteo != ''",
                        (jur,),
                    ).fetchall()
                    wk_counts: Counter[str] = Counter()
                    sat_counts: Counter[str] = Counter()
                    for f_iso, turno in rows:
                        try:
                            wd = datetime.date.fromisoformat(f_iso).weekday()
                        except ValueError:
                            continue
                        if wd in (0, 1, 2, 3, 4):
                            wk_counts[turno] += 1
                        elif wd == 5:
                            sat_counts[turno] += 1
                    if wk_counts:
                        self.weekday_turns[jur] = [t for t in TURNOS_OFICIALES if wk_counts[t] > 0]
                    if sat_counts:
                        self.saturday_turns[jur] = [t for t in TURNOS_OFICIALES if sat_counts[t] > 0]
        except sqlite3.Error as exc:
            logging.warning(f"No se pudo calibrar calendario desde BD: {exc}")

    def es_feriado(self, fecha: datetime.date) -> bool:
        return self.holiday_mode or (fecha.isoformat() in self.holiday_dates)

    def turnos_para_fecha(self, loteria: str, fecha: datetime.date) -> list[str]:
        if fecha.weekday() == 6 or self.es_feriado(fecha):
            return []
        if fecha.weekday() == 5:
            return list(self.saturday_turns.get(loteria, TURNOS_OFICIALES))
        return list(self.weekday_turns.get(loteria, TURNOS_OFICIALES))

    def es_turno_habilitado(self, loteria: str, fecha: datetime.date, turno: str) -> bool:
        if fecha.weekday() == 6:
            return False
        if fecha.weekday() == 5:
            return turno in self.saturday_turns.get(loteria, TURNOS_OFICIALES)
        return turno in self.weekday_turns.get(loteria, TURNOS_OFICIALES)

    def to_metadata(self) -> dict[str, Any]:
        return {
            "timezone": "America/Argentina/Buenos_Aires",
            "sunday_draws": 0,
            "weekday_turns": self.weekday_turns,
            "saturday_turns": self.saturday_turns,
            "holiday_mode": self.holiday_mode,
        }


# --- CIRCUIT BREAKER ---
def leer_fallos() -> int:
    if not os.path.exists(ARCHIVO_FALLOS):
        return 0
    try:
        with open(ARCHIVO_FALLOS, "r", encoding="utf-8") as f:
            return int(f.read().strip() or "0")
    except (ValueError, OSError):
        return 0


def manejar_fallo_scraping(motivo: str) -> None:
    fallos = leer_fallos() + 1
    with open(ARCHIVO_FALLOS, "w", encoding="utf-8") as f:
        f.write(str(fallos))
    logging.warning(f"Fallo técnico real de scraping ({motivo}). Conteo actual: {fallos}")
    if fallos in (3, 6, 12, 24):
        msg = (
            "🚨 <b>ALERTA EN QUINIELA-ZERO</b> 🚨\n\n"
            f"El recolector falló <b>{fallos} veces consecutivas</b>.\n"
            f"Motivo: <code>{motivo}</code>\n"
            "Operando temporalmente con fuentes espejo / historial local."
        )
        enviar_telegram(msg)


def resetear_fallos(avisar_recuperacion: bool = True) -> None:
    prev = leer_fallos()
    with open(ARCHIVO_FALLOS, "w", encoding="utf-8") as f:
        f.write("0")
    if prev > 0:
        logging.info(f"Circuit Breaker reseteado a estado óptimo (previo: {prev}).")
        if avisar_recuperacion and prev >= 3:
            enviar_telegram(
                "✅ <b>QUINIELA-ZERO RECUPERADO</b>\n\n"
                "Las fuentes volvieron a responder correctamente y el Circuit Breaker quedó en 0."
            )


# --- PARSERS MULTI-FUENTE ---
def parse_gba_html(html_text: str) -> dict[str, Any]:
    """Parser oficial Lotería de la Provincia (loteria.gba.gov.ar)."""
    soup = BeautifulSoup(html_text, "lxml")
    bloques = soup.select("div.sorteo")
    if not bloques:
        return {"status": "ERROR", "reason": "No se encontraron bloques div.sorteo en HTML GBA", "turnos": [], "pendientes": []}

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
        fecha_iso = normalizar_fecha_iso(fecha_raw) or ""

        sorteo_el = bloque.select_one(".nSorteo")
        nro_sorteo = re.sub(r"\D", "", sorteo_el.get_text(strip=True) if sorteo_el else "")

        bolillas_box = bloque.select_one("div.bolillas")
        if not bolillas_box:
            continue
        bolillas_detectadas += 1

        pares: dict[int, str] = {}
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
                turnos_listos.append(
                    {
                        "loteria": "PROVINCIA",
                        "turno": turno_canon,
                        "fecha_raw": fecha_raw,
                        "fecha_iso": fecha_iso,
                        "nro_sorteo": nro_sorteo,
                        "datos": datos_str,
                        "fuente": "loteria.gba.gov.ar",
                    }
                )
        elif any("-" in v for v in valores):
            pendientes.append(f"{turno_canon} ({fecha_raw or 'hoy'})")

    if turnos_listos:
        return {"status": "OK", "turnos": turnos_listos, "pendientes": pendientes, "reason": ""}
    if bolillas_detectadas > 0 and pendientes:
        return {"status": "PENDING", "turnos": [], "pendientes": pendientes, "reason": "Turnos en espera (----)"}
    return {"status": "ERROR", "reason": "Bloques div.bolillas ausentes o formato modificado", "turnos": [], "pendientes": []}


def parse_ciudad_index_html(html_text: str) -> list[dict[str, str]]:
    """Extrae índice de sorteos recientes (<li class='custom-option' data-value='53211'>...) de quiniela.loteriadelaciudad.gob.ar."""
    soup = BeautifulSoup(html_text, "lxml")
    entries = []
    for li in soup.select("li.custom-option"):
        val = (li.get("data-value") or "").strip()
        if not val.isdigit():
            continue
        text = li.get_text(" ", strip=True)
        m = re.search(r"(\d{2}/\d{2}/\d{4}).*?-\s*([A-ZÁÉÍÓÚÑ ]+)$", text, re.I)
        fecha_iso = normalizar_fecha_iso(m.group(1)) if m else None
        turno = normalizar_turno(m.group(2)) if m else normalizar_turno(text)
        if fecha_iso and turno:
            entries.append({"sorteo_id": val, "fecha_iso": fecha_iso, "turno": turno})
    return entries


def parse_ciudad_resultados_js(raw_text: str) -> list[dict[str, Any]]:
    """
    Parsea la respuesta oficial `window.RESULTADOS_DATA = [...]` de Lotería de la Ciudad (LOTBA),
    que incluye jurisdicción "51" (CIUDAD DE BS AS) y "53" (PROVINCIA DE BS AS).
    """
    txt = (raw_text or "").strip()
    marker = txt.find("window.RESULTADOS_DATA")
    start_idx = txt.find("[", marker if marker != -1 else 0)
    if start_idx == -1:
        return []
    try:
        payload, _ = json.JSONDecoder().raw_decode(txt[start_idx:])
    except json.JSONDecodeError:
        return []
    if not isinstance(payload, list):
        return []

    resultados: list[dict[str, Any]] = []
    jur_map = {"51": "CIUDAD", "53": "PROVINCIA"}

    for item in payload:
        fecha_raw = str(item.get("fecha") or "").replace("\\/", "/")
        fecha_iso = normalizar_fecha_iso(fecha_raw)
        turno = normalizar_turno(str(item.get("modalidad") or ""))
        nro_sorteo = str(item.get("sorteo") or "")
        jurisdicciones = item.get("jurisdicciones") or {}
        if not fecha_iso or not turno or not isinstance(jurisdicciones, dict):
            continue

        for code, jur_name in jur_map.items():
            jdata = jurisdicciones.get(code)
            if not jdata:
                continue
            nums_raw = jdata.get("numeros") or []
            pares: dict[int, str] = {}
            for entry in nums_raw:
                pos_str = str(entry.get("pos") or "")
                val_str = normalizar_numero_4(str(entry.get("val") or ""))
                if pos_str.isdigit() and val_str:
                    pos = int(pos_str)
                    if 1 <= pos <= 20:
                        pares[pos] = val_str
            if len(pares) == 20:
                datos_str = ",".join(pares[i] for i in range(1, 21))
                if validar_esquema(datos_str):
                    resultados.append(
                        {
                            "loteria": jur_name,
                            "turno": turno,
                            "fecha_raw": fecha_raw,
                            "fecha_iso": fecha_iso,
                            "nro_sorteo": nro_sorteo,
                            "datos": datos_str,
                            "fuente": "quiniela.loteriadelaciudad.gob.ar",
                        }
                    )
    return resultados


def parse_vivitusuerte_html(html_text: str, loteria: str, fecha_default_iso: str | None = None) -> list[dict[str, Any]]:
    """Parser para el espejo confiable vivitusuerte.com/pizarra/{ciudad|provincia}."""
    soup = BeautifulSoup(html_text, "lxml")
    resultados: list[dict[str, Any]] = []

    fecha_iso = fecha_default_iso
    pizarra_el = soup.select_one("#seccionPizarra[data-fecha-default]")
    if pizarra_el:
        fecha_iso = normalizar_fecha_iso(pizarra_el.get("data-fecha-default", "")) or fecha_iso
    if not fecha_iso:
        text_top = soup.get_text(" ", strip=True)
        m_date = re.search(r"(\d{2}/\d{2}/\d{4}|\d{4}-\d{2}-\d{2})", text_top)
        if m_date:
            fecha_iso = normalizar_fecha_iso(m_date.group(1))
    if not fecha_iso:
        return []

    for card in soup.select("div.card"):
        header = card.select_one(".card-header")
        if not header:
            continue
        turno = normalizar_turno(header.get_text(" ", strip=True))
        if not turno:
            continue
        table = card.select_one("table[data-pizarra-momento]")
        if not table:
            continue
        slots = [s.get_text(strip=True).rstrip(".") for s in table.select(".caja-slot")]
        vals = [v.get_text(strip=True) for v in table.select(".caja-resultado")]
        pares: dict[int, str] = {}
        for s_txt, v_txt in zip(slots, vals):
            norm = normalizar_numero_4(v_txt)
            if s_txt.isdigit() and norm:
                pos = int(s_txt)
                if 1 <= pos <= 20:
                    pares[pos] = norm
        if len(pares) == 20:
            datos_str = ",".join(pares[i] for i in range(1, 21))
            if validar_esquema(datos_str):
                resultados.append(
                    {
                        "loteria": loteria,
                        "turno": turno,
                        "fecha_raw": fecha_iso,
                        "fecha_iso": fecha_iso,
                        "nro_sorteo": "",
                        "datos": datos_str,
                        "fuente": "vivitusuerte.com",
                    }
                )
    return resultados


def parse_ruta1000_html(html_text: str, loteria: str) -> list[dict[str, Any]]:
    """Parser de respaldo para ruta1000.com.ar (extrae bloques con fecha DD/MM/YY y posiciones 1º..20º)."""
    soup = BeautifulSoup(html_text, "lxml")
    resultados: list[dict[str, Any]] = []
    # Busca encabezados de extracto con día + turno + DD/MM/YY
    for anchor in soup.find_all("a"):
        txt = anchor.get_text(" ", strip=True)
        m = re.search(r"(Previa|Primera|Matutina|Vespertina|Nocturna)\s+(\d{2}/\d{2}/\d{2,4})", txt, re.I)
        if not m:
            continue
        turno = normalizar_turno(m.group(1))
        fecha_iso = normalizar_fecha_iso(m.group(2))
        if not turno or not fecha_iso:
            continue
        table = anchor.find_parent("table")
        if not table:
            continue
        # Buscar números de 4 dígitos dentro de la tabla contenedora si es extracto completo
        pares: dict[int, str] = {}
        cells = [c.get_text(" ", strip=True) for c in table.find_all(["td", "th"])]
        for idx, cell in enumerate(cells[:-1]):
            m_pos = re.match(r"^(\d{1,2})\s*[º°.]?$", cell)
            if m_pos:
                pos = int(m_pos.group(1))
                norm = normalizar_numero_4(cells[idx + 1])
                if 1 <= pos <= 20 and norm:
                    pares[pos] = norm
        if len(pares) == 20:
            datos_str = ",".join(pares[i] for i in range(1, 21))
            if validar_esquema(datos_str):
                resultados.append(
                    {
                        "loteria": loteria,
                        "turno": turno,
                        "fecha_raw": m.group(2),
                        "fecha_iso": fecha_iso,
                        "nro_sorteo": "",
                        "datos": datos_str,
                        "fuente": "ruta1000.com.ar",
                    }
                )
    return resultados


# --- BASE DE DATOS Y PERSISTENCIA CONTINUA ---
def init_db(db_path: str = DB_NAME) -> None:
    try:
        with sqlite3.connect(db_path) as conn:
            conn.execute("PRAGMA journal_mode=WAL;")
            conn.execute("PRAGMA synchronous=NORMAL;")
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS sorteos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    loteria TEXT NOT NULL,
                    turno TEXT NOT NULL,
                    datos TEXT NOT NULL,
                    fecha_sorteo TEXT NOT NULL DEFAULT '',
                    estado_verificacion TEXT NOT NULL DEFAULT 'HISTORIAL',
                    fuente_primaria TEXT NOT NULL DEFAULT '',
                    fuente_secundaria TEXT NOT NULL DEFAULT '',
                    nro_sorteo TEXT NOT NULL DEFAULT '',
                    guardado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """
            )
            cols = {r[1] for r in conn.execute("PRAGMA table_info(sorteos)").fetchall()}
            migrations = {
                "fecha_sorteo": "ALTER TABLE sorteos ADD COLUMN fecha_sorteo TEXT NOT NULL DEFAULT ''",
                "estado_verificacion": "ALTER TABLE sorteos ADD COLUMN estado_verificacion TEXT NOT NULL DEFAULT 'HISTORIAL'",
                "fuente_primaria": "ALTER TABLE sorteos ADD COLUMN fuente_primaria TEXT NOT NULL DEFAULT ''",
                "fuente_secundaria": "ALTER TABLE sorteos ADD COLUMN fuente_secundaria TEXT NOT NULL DEFAULT ''",
                "nro_sorteo": "ALTER TABLE sorteos ADD COLUMN nro_sorteo TEXT NOT NULL DEFAULT ''",
            }
            for col, ddl in migrations.items():
                if col not in cols:
                    conn.execute(ddl)

            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS fuentes_salud_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fuente TEXT NOT NULL,
                    jurisdiccion TEXT NOT NULL,
                    estado TEXT NOT NULL,
                    http_status INTEGER DEFAULT 0,
                    latencia_ms INTEGER DEFAULT 0,
                    detalle TEXT DEFAULT '',
                    checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """
            )
            conn.execute("CREATE INDEX IF NOT EXISTS idx_loteria_turno ON sorteos(loteria, turno)")
            conn.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_sorteo ON sorteos(loteria, fecha_sorteo, turno) WHERE fecha_sorteo != ''"
            )
            conn.commit()
    except sqlite3.Error as exc:
        logging.error(f"Error crítico DB: {exc}")
        sys.exit(1)


def _leer_csv_a_dict(csv_path: str, loteria_default: str) -> list[tuple[str, str, str, str]]:
    if not os.path.exists(csv_path):
        return []
    registros: list[tuple[str, str, str, str]] = []
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.reader(f)
        next(reader, None)
        for row in reader:
            if len(row) < 23:
                continue
            fecha_iso = normalizar_fecha_iso(row[0].strip())
            loteria = (row[1].strip().upper() or loteria_default)
            turno = normalizar_turno(row[2].strip())
            nums = [normalizar_numero_4(n) for n in row[3:23]]
            if not fecha_iso or not turno or any(n is None for n in nums):
                continue
            datos_str = ",".join(n for n in nums if n is not None)
            if validar_esquema(datos_str):
                registros.append((loteria, turno, datos_str, fecha_iso))
    return registros


def importar_historial(db_path: str = DB_NAME, csv_provincia: str = CSV_PROVINCIA, csv_ciudad: str = CSV_CIUDAD) -> int:
    """Importa idempotentemente Provincia y Ciudad desde los CSV sin duplicar registros."""
    insertados = 0
    try:
        with sqlite3.connect(db_path) as conn:
            for path, jur in ((csv_provincia, "PROVINCIA"), (csv_ciudad, "CIUDAD")):
                rows = _leer_csv_a_dict(path, jur)
                if not rows:
                    continue
                for loteria, turno, datos_str, fecha_iso in rows:
                    cur = conn.execute(
                        """
                        INSERT OR IGNORE INTO sorteos (
                            loteria, turno, datos, fecha_sorteo, estado_verificacion, fuente_primaria
                        ) VALUES (?, ?, ?, ?, 'HISTORIAL', ?)
                        """,
                        (loteria, turno, datos_str, fecha_iso, os.path.basename(path)),
                    )
                    insertados += cur.rowcount
            conn.commit()
        logging.info(f"Importación idempotente completada. Nuevas filas agregadas: {insertados}")
    except Exception as exc:
        logging.error(f"Error importando historiales CSV: {exc}")
    return insertados


def guardar_sorteo_verificado(
    conn: sqlite3.Connection,
    loteria: str,
    fecha_iso: str,
    turno: str,
    datos_str: str,
    estado: str,
    fuente_1: str,
    fuente_2: str = "",
    nro_sorteo: str = "",
) -> bool:
    """Inserta o actualiza un sorteo garantizando unicidad por (loteria, fecha_sorteo, turno)."""
    existente = conn.execute(
        "SELECT id, datos, estado_verificacion FROM sorteos WHERE loteria=? AND fecha_sorteo=? AND turno=?",
        (loteria, fecha_iso, turno),
    ).fetchone()
    if existente:
        row_id, prev_datos, prev_estado = existente
        if prev_datos == datos_str and prev_estado in ("VERIFICADO", "HISTORIAL"):
            return False
        if estado == "VERIFICADO" or (prev_estado == "PENDIENTE" and estado != "PENDIENTE"):
            conn.execute(
                """
                UPDATE sorteos
                SET datos=?, estado_verificacion=?, fuente_primaria=?, fuente_secundaria=?, nro_sorteo=?
                WHERE id=?
                """,
                (datos_str, estado, fuente_1, fuente_2, nro_sorteo, row_id),
            )
            return True
        return False

    conn.execute(
        """
        INSERT INTO sorteos (
            loteria, turno, datos, fecha_sorteo, estado_verificacion, fuente_primaria, fuente_secundaria, nro_sorteo
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (loteria, turno, datos_str, fecha_iso, estado, fuente_1, fuente_2, nro_sorteo),
    )
    return True


def exportar_csvs_desde_db(db_path: str = DB_NAME, csv_provincia: str = CSV_PROVINCIA, csv_ciudad: str = CSV_CIUDAD) -> None:
    """Mantiene ambos CSV históricos ordenados cronológicamente y sincronizados con todo nuevo sorteo."""
    header = ["fecha", "loteria", "turno"] + [f"n{i}" for i in range(1, 21)]
    turno_order = {t: i for i, t in enumerate(TURNOS_OFICIALES)}
    with sqlite3.connect(db_path) as conn:
        for jur, target_csv in (("PROVINCIA", csv_provincia), ("CIUDAD", csv_ciudad)):
            rows = conn.execute(
                """
                SELECT fecha_sorteo, loteria, turno, datos
                FROM sorteos
                WHERE loteria=? AND fecha_sorteo != ''
                """,
                (jur,),
            ).fetchall()
            rows.sort(key=lambda r: (r[0], turno_order.get(r[2], 99)))
            with open(target_csv, "w", encoding="utf-8", newline="") as f:
                writer = csv.writer(f)
                writer.writerow(header)
                for f_iso, lot, turno, datos in rows:
                    nums = datos.split(",")
                    if len(nums) == 20:
                        writer.writerow([f_iso, lot, turno] + nums)


# --- GESTOR DE FUENTES Y VALIDACIÓN CRUZADA ---
@dataclass
class SourceRecord:
    loteria: str
    fecha_iso: str
    turno: str
    datos: str
    fuente: str
    nro_sorteo: str = ""


class DataSourcesManager:
    """
    Orquestador multi-fuente:
      1) Lotería GBA (Provincia)
      2) Lotería de la Ciudad LOTBA JSON (Ciudad + Provincia)
      3) Espejos confiables (vivitusuerte.com, ruta1000.com.ar)
    """

    def __init__(self, session: requests.Session, db_path: str = DB_NAME, calendar: SorteoCalendar | None = None):
        self.session = session
        self.db_path = db_path
        self.calendar = calendar or SorteoCalendar()
        self.health_events: list[dict[str, Any]] = []

    def _log_health(self, fuente: str, jurisdiccion: str, estado: str, http_status: int, lat_ms: int, detalle: str = "") -> None:
        self.health_events.append(
            {
                "fuente": fuente,
                "jurisdiccion": jurisdiccion,
                "estado": estado,
                "http_status": http_status,
                "latencia_ms": lat_ms,
                "detalle": detalle,
                "checked_at": ahora_argentina().isoformat(),
            }
        )
        try:
            with sqlite3.connect(self.db_path) as conn:
                conn.execute(
                    """
                    INSERT INTO fuentes_salud_log (fuente, jurisdiccion, estado, http_status, latencia_ms, detalle)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    (fuente, jurisdiccion, estado, http_status, lat_ms, detalle[:300]),
                )
                conn.commit()
        except sqlite3.Error:
            pass

    def _safe_get(self, url: str, fuente: str, jurisdiccion: str) -> str | None:
        t0 = time.monotonic()
        try:
            resp = fetch_with_backoff(self.session, url)
            lat = int((time.monotonic() - t0) * 1000)
            self._log_health(fuente, jurisdiccion, "OK", resp.status_code, lat)
            return resp.text
        except Exception as exc:
            lat = int((time.monotonic() - t0) * 1000)
            self._log_health(fuente, jurisdiccion, "ERROR", 0, lat, str(exc))
            return None

    def recolectar_todo(self, max_ciudad_sorteos: int = 10) -> tuple[list[SourceRecord], list[str], bool]:
        records: list[SourceRecord] = []
        pendientes: list[str] = []
        alguna_ok = False

        # 1. Oficial Provincia (GBA)
        gba_html = self._safe_get(URL_PROVINCIA, "loteria.gba.gov.ar", "PROVINCIA")
        if gba_html:
            parsed = parse_gba_html(gba_html)
            if parsed["status"] in ("OK", "PENDING"):
                alguna_ok = True
                pendientes.extend(parsed.get("pendientes", []))
                for t in parsed.get("turnos", []):
                    records.append(
                        SourceRecord(
                            loteria="PROVINCIA",
                            fecha_iso=t["fecha_iso"],
                            turno=t["turno"],
                            datos=t["datos"],
                            fuente="loteria.gba.gov.ar",
                            nro_sorteo=t.get("nro_sorteo", ""),
                        )
                    )
            else:
                self._log_health("loteria.gba.gov.ar", "PROVINCIA", "SCHEMA_CHANGED", 200, 0, parsed["reason"])

        # 2. Oficial Ciudad (LOTBA índice + JSON por sorteo — también trae Provincia "53")
        ciudad_html = self._safe_get(URL_CIUDAD_HOME, "quiniela.loteriadelaciudad.gob.ar", "CIUDAD")
        if ciudad_html:
            entries = parse_ciudad_index_html(ciudad_html)
            if entries:
                alguna_ok = True
                for entry in entries[:max_ciudad_sorteos]:
                    sid = entry["sorteo_id"]
                    js_txt = self._safe_get(
                        URL_CIUDAD_DATA.format(sorteo_id=sid),
                        "loteriadelaciudad_api",
                        "AMBAS",
                    )
                    if not js_txt:
                        continue
                    for r in parse_ciudad_resultados_js(js_txt):
                        records.append(
                            SourceRecord(
                                loteria=r["loteria"],
                                fecha_iso=r["fecha_iso"],
                                turno=r["turno"],
                                datos=r["datos"],
                                fuente=r["fuente"],
                                nro_sorteo=r.get("nro_sorteo", ""),
                            )
                        )
            else:
                self._log_health("quiniela.loteriadelaciudad.gob.ar", "CIUDAD", "SCHEMA_CHANGED", 200, 0, "Índice vacío")

        # 3. Espejos confiables (vivitusuerte + ruta1000) para validación cruzada y contingencia
        for url, jur in ((URL_VIVI_PROVINCIA, "PROVINCIA"), (URL_VIVI_CIUDAD, "CIUDAD")):
            v_html = self._safe_get(url, "vivitusuerte.com", jur)
            if v_html:
                parsed_v = parse_vivitusuerte_html(v_html, jur)
                if parsed_v:
                    alguna_ok = True
                    for r in parsed_v:
                        records.append(
                            SourceRecord(
                                loteria=r["loteria"],
                                fecha_iso=r["fecha_iso"],
                                turno=r["turno"],
                                datos=r["datos"],
                                fuente="vivitusuerte.com",
                            )
                        )

        for url, jur in ((URL_RUTA_PROVINCIA, "PROVINCIA"), (URL_RUTA_CIUDAD, "CIUDAD")):
            r_html = self._safe_get(url, "ruta1000.com.ar", jur)
            if r_html:
                parsed_r = parse_ruta1000_html(r_html, jur)
                if parsed_r:
                    alguna_ok = True
                    for r in parsed_r:
                        records.append(
                            SourceRecord(
                                loteria=r["loteria"],
                                fecha_iso=r["fecha_iso"],
                                turno=r["turno"],
                                datos=r["datos"],
                                fuente="ruta1000.com.ar",
                            )
                        )

        return records, pendientes, alguna_ok

    def cruzar_y_validar(self, records: list[SourceRecord]) -> list[dict[str, Any]]:
        """
        Agrupa por (loteria, fecha_iso, turno) y valida:
          - >=2 fuentes independientes coincidentes -> VERIFICADO
          - 1 fuente -> NO VERIFICADO
          - discrepancia entre fuentes -> PENDIENTE
        """
        agrupado: dict[tuple[str, str, str], dict[str, SourceRecord]] = defaultdict(dict)
        for rec in records:
            ok, motivo = validar_integridad_registro(
                rec.loteria, rec.fecha_iso, rec.turno, rec.datos, self.calendar
            )
            if not ok:
                logging.warning(f"Registro descartado por integridad ({rec.fuente}): {motivo}")
                continue
            key = (rec.loteria, rec.fecha_iso, rec.turno)
            agrupado[key][rec.fuente] = rec

        consolidados: list[dict[str, Any]] = []
        for (loteria, fecha_iso, turno), por_fuente in sorted(agrupado.items()):
            extractos = {src: r.datos for src, r in por_fuente.items()}
            conteo = Counter(extractos.values())
            mejor_datos, veces = conteo.most_common(1)[0]
            fuentes_coincidentes = [src for src, d in extractos.items() if d == mejor_datos]
            nro = next((r.nro_sorteo for r in por_fuente.values() if r.nro_sorteo), "")

            if len(set(extractos.values())) > 1 and veces < 2:
                estado = "PENDIENTE"
                fuente_1 = list(por_fuente.keys())[0]
                fuente_2 = list(por_fuente.keys())[1] if len(por_fuente) > 1 else ""
                logging.warning(f"Discrepancia detectada en {loteria} {fecha_iso} {turno}: marcado PENDIENTE")
            elif veces >= 2:
                estado = "VERIFICADO"
                fuente_1 = fuentes_coincidentes[0]
                fuente_2 = fuentes_coincidentes[1]
            else:
                estado = "NO VERIFICADO"
                fuente_1 = fuentes_coincidentes[0]
                fuente_2 = ""

            consolidados.append(
                {
                    "loteria": loteria,
                    "fecha_iso": fecha_iso,
                    "turno": turno,
                    "datos": mejor_datos,
                    "estado": estado,
                    "fuente_1": fuente_1,
                    "fuente_2": fuente_2,
                    "nro_sorteo": nro,
                }
            )
        return consolidados


# --- MOTOR ESTADÍSTICO TRANSPARENTE ---
def analizar_estadisticas(
    loteria: str,
    window_days: int | None = 90,
    top_n: int = 5,
    evitar_repeticion_inmediata: bool = True,
    db_path: str = DB_NAME,
    incluir_cruce_opuesto: bool = True,
) -> dict[str, Any]:
    """
    Motor Cuantitativo Multi-Variable Pro Max:
      - Z-Score Binomial + EWMA multi-horizonte (λ=0.08 / 0.012)
      - Análisis de supervivencia (Z_atraso = (g - Ḡ)/σ_G)
      - Markov Lift condicionado a la última cabeza
      - Cruce Provincia <-> Ciudad + equilibrio de terminaciones Chi²
    """
    with sqlite3.connect(db_path) as conn:
        query = "SELECT fecha_sorteo, turno, datos FROM sorteos WHERE loteria=? AND fecha_sorteo != ''"
        params: list[Any] = [loteria]
        if window_days is not None and window_days > 0:
            cutoff = (ahora_argentina().date() - datetime.timedelta(days=window_days)).isoformat()
            query += " AND fecha_sorteo >= ?"
            params.append(cutoff)
        query += " ORDER BY fecha_sorteo ASC, id ASC"
        filas = conn.execute(query, params).fetchall()

        opuesta = "CIUDAD" if loteria == "PROVINCIA" else "PROVINCIA"
        filas_op = []
        if incluir_cruce_opuesto:
            q_op = "SELECT fecha_sorteo, turno, datos FROM sorteos WHERE loteria=? AND fecha_sorteo != ''"
            p_op: list[Any] = [opuesta]
            if window_days is not None and window_days > 0:
                cutoff = (ahora_argentina().date() - datetime.timedelta(days=window_days)).isoformat()
                q_op += " AND fecha_sorteo >= ?"
                p_op.append(cutoff)
            q_op += " ORDER BY fecha_sorteo ASC, id ASC"
            filas_op = conn.execute(q_op, p_op).fetchall()

    total_sorteos = len(filas)
    if total_sorteos == 0:
        return {"loteria": loteria, "total_sorteos": 0, "candidatos": [], "mensaje": "Sin historial suficiente"}

    frecuencia_simple: Counter[str] = Counter()
    frecuencia_sorteos: Counter[str] = Counter()
    ewma_short: defaultdict[str, float] = defaultdict(float)
    ewma_long: defaultdict[str, float] = defaultdict(float)
    head_weighted: defaultdict[str, float] = defaultdict(float)
    indices_aparicion: defaultdict[str, list[int]] = defaultdict(list)
    ultimas_cabezas_opuestas: defaultdict[str, float] = defaultdict(float)
    term_recent: Counter[int] = Counter()
    markov_head: defaultdict[str, Counter[str]] = defaultdict(Counter)
    head_totals: Counter[str] = Counter()

    prev_head = None
    for idx, (_, _, datos) in enumerate(filas):
        age = max(0, total_sorteos - 1 - idx)
        w_short = math.exp(-0.08 * age)
        w_long = math.exp(-0.012 * age)
        nums = [n[-2:] for n in datos.split(",") if len(n) >= 2]
        vistas = set()
        curr_head = nums[0] if nums else None
        for pos, n2 in enumerate(nums):
            frecuencia_simple[n2] += 1
            pos_w = 1.8 if pos == 0 else (1.25 if pos < 5 else 1.0)
            head_weighted[n2] += w_long * pos_w
            ewma_short[n2] += w_short * pos_w
            ewma_long[n2] += w_long * pos_w
            if n2 not in vistas:
                vistas.add(n2)
                frecuencia_sorteos[n2] += 1
                indices_aparicion[n2].append(idx)
                if age < 14 and n2.isdigit():
                    term_recent[int(n2) % 10] += 1
                if prev_head is not None:
                    markov_head[prev_head][n2] += 1
        if prev_head is not None:
            head_totals[prev_head] += 1
        prev_head = curr_head

    for idx_op, (_, _, datos_op) in enumerate(filas_op[-25:]):
        rec = math.exp(-0.07 * max(0, len(filas_op[-25:]) - 1 - idx_op))
        nums_op = [n[-2:] for n in datos_op.split(",") if len(n) >= 2]
        for pos, n2 in enumerate(nums_op):
            mult = 2.0 if pos == 0 else (0.8 if pos < 5 else 0.25)
            ultimas_cabezas_opuestas[n2] += rec * mult

    ultimo_nums = [n[-2:] for n in filas[-1][2].split(",") if len(n) >= 2]
    ultimo_set = set(ultimo_nums)
    ultima_cabeza = ultimo_nums[0] if ultimo_nums else None

    max_hw = max(head_weighted.values(), default=1.0) or 1.0
    max_es = max(ewma_short.values(), default=1.0) or 1.0
    max_el = max(ewma_long.values(), default=1.0) or 1.0
    max_opp = max(ultimas_cabezas_opuestas.values(), default=1.0) or 1.0
    exp_term = max(1.0, sum(term_recent.values()) / 10.0)
    n_obs = max(1, total_sorteos * 20)

    scores = []
    for num_int in range(100):
        n2 = f"{num_int:02d}"
        if evitar_repeticion_inmediata and n2 in ultimo_set:
            continue
        idxs = indices_aparicion[n2]
        atraso = (total_sorteos - 1) - (idxs[-1] if idxs else -1)
        gaps = [idxs[j] - idxs[j - 1] for j in range(1, len(idxs))] if len(idxs) >= 2 else [5]
        mean_g = max(1.0, sum(gaps) / len(gaps))
        std_g = max(1.5, math.sqrt(sum((g - mean_g) ** 2 for g in gaps) / len(gaps)))
        z_gap = (atraso - mean_g) / std_g
        surv_due = sum(1 for g in gaps if g <= atraso) / len(gaps)

        p_base = (frecuencia_sorteos[n2] + 1.0) / (total_sorteos + 5.0)
        if ultima_cabeza and head_totals[ultima_cabeza] >= 5:
            p_cond = (markov_head[ultima_cabeza][n2] + 0.5) / (head_totals[ultima_cabeza] + 2.5)
            markov_lift = max(0.4, min(3.0, p_cond / p_base))
        else:
            markov_lift = 1.0

        momentum = (ewma_short[n2] / max_es) - (ewma_long[n2] / max_el)
        term_def = max(-1.0, min(1.0, (exp_term - term_recent[num_int % 10]) / exp_term))

        f_freq = head_weighted[n2] / max_hw
        f_mom = max(0.0, min(1.0, momentum + 0.5))
        f_surv = 0.6 * surv_due + 0.4 * max(0.0, min(1.0, (z_gap + 1.5) / 4.0))
        f_mk = max(0.0, min(1.0, (markov_lift - 0.5) / 2.0))
        f_cross = (ultimas_cabezas_opuestas[n2] / max_opp) if incluir_cruce_opuesto else 0.0
        f_bal = (term_def + 1.0) / 2.0

        total_score = 0.28 * f_freq + 0.16 * f_mom + 0.22 * f_surv + 0.16 * f_mk + 0.12 * f_cross + 0.06 * f_bal
        freq_rel = frecuencia_simple[n2] / float(n_obs)
        scores.append((n2, total_score, freq_rel, atraso, z_gap, markov_lift))

    scores.sort(key=lambda x: x[1], reverse=True)
    candidatos = []
    for n2, score, freq_rel, atraso, z_gap, lift in scores[:top_n]:
        margen = 1.96 * math.sqrt(max(0.0, freq_rel * (1.0 - freq_rel)) / n_obs)
        confianza = "Alta" if total_sorteos >= 120 else ("Media" if total_sorteos >= 40 else "Baja")
        candidatos.append(
            {
                "numero": n2,
                "score": round(score, 4),
                "frecuencia_pct": round(freq_rel * 100.0, 2),
                "margen_error_pct": round(margen * 100.0, 2),
                "z_atraso": round(z_gap, 2),
                "markov_lift": round(lift, 2),
                "atraso_sorteos": atraso,
                "confianza": confianza,
                "resumen": (
                    f"Basado en {total_sorteos} sorteos, frecuencia {freq_rel * 100:.1f}% "
                    f"(±{margen * 100:.2f}%)"
                ),
            }
        )

    return {
        "loteria": loteria,
        "total_sorteos": total_sorteos,
        "window_days": window_days,
        "candidatos": candidatos,
    }


def run_analytics(loteria: str, turno: str) -> tuple[list[tuple[str, float]], str | None]:
    """Compatibilidad con firma anterior para reporte de Telegram por turno."""
    try:
        with sqlite3.connect(DB_NAME) as conn:
            filas = conn.execute(
                "SELECT datos FROM sorteos WHERE loteria=? AND turno=? ORDER BY fecha_sorteo ASC, id ASC",
                (loteria, turno),
            ).fetchall()
    except sqlite3.Error as exc:
        logging.error(f"Error consultando BD en run_analytics: {exc}")
        return [], None

    if not filas:
        return [], None

    transiciones_1 = defaultdict(list)
    transiciones_2 = defaultdict(list)
    total_filas = len(filas)
    limite_reciente = max(0, total_filas - 500)

    for i, (datos,) in enumerate(filas):
        nums = [n[-2:] for n in datos.split(",") if n]
        peso = 3 if i >= limite_reciente else 1
        for a, b in zip(nums, nums[1:]):
            transiciones_1[a].extend([b] * peso)
        for a, b, c in zip(nums, nums[1:], nums[2:]):
            transiciones_2[(a, b)].extend([c] * peso)

    ultimo_sorteo = filas[-1][0].split(",")
    ultimo = ultimo_sorteo[-1][-2:]
    penultimo = ultimo_sorteo[-2][-2:] if len(ultimo_sorteo) >= 2 else None
    top: list[tuple[str, float]] = []

    if penultimo:
        siguientes_2 = transiciones_2.get((penultimo, ultimo), [])
        if len(siguientes_2) >= 5:
            total_2 = len(siguientes_2)
            top = [(num, freq / total_2) for num, freq in Counter(siguientes_2).most_common(3)]
    if not top:
        siguientes_1 = transiciones_1.get(ultimo, [])
        if siguientes_1:
            total_1 = len(siguientes_1)
            top = [(num, freq / total_1) for num, freq in Counter(siguientes_1).most_common(3)]
    return top, ultimo


def exportar_snapshot_movil(
    db_path: str = DB_NAME,
    out_path: str = SNAPSHOT_JSON,
    calendar: SorteoCalendar | None = None,
    health_events: list[dict[str, Any]] | None = None,
    dias_recientes: int = 180,
) -> None:
    """Genera sync_snapshot.json liviano para sincronización automática de la app Android."""
    cal = calendar or SorteoCalendar()
    cal.calibrate_from_db(db_path)
    cutoff = (ahora_argentina().date() - datetime.timedelta(days=dias_recientes)).isoformat()
    with sqlite3.connect(db_path) as conn:
        totales = {
            row[0]: row[1]
            for row in conn.execute("SELECT loteria, COUNT(*) FROM sorteos GROUP BY loteria").fetchall()
        }
        recientes = conn.execute(
            """
            SELECT loteria, fecha_sorteo, turno, datos, estado_verificacion, fuente_primaria, fuente_secundaria, nro_sorteo
            FROM sorteos
            WHERE fecha_sorteo >= ?
            ORDER BY fecha_sorteo DESC, id DESC
            """,
            (cutoff,),
        ).fetchall()

    payload = {
        "schema_version": 1,
        "generated_at_art": ahora_argentina().isoformat(),
        "totales_historicos": totales,
        "calendar": cal.to_metadata(),
        "estimaciones": {
            "PROVINCIA": analizar_estadisticas("PROVINCIA", window_days=90, top_n=5, db_path=db_path),
            "CIUDAD": analizar_estadisticas("CIUDAD", window_days=90, top_n=5, db_path=db_path),
        },
        "fuentes_salud": health_events or [],
        "sorteos_recientes": [
            {
                "loteria": r[0],
                "fecha": r[1],
                "turno": r[2],
                "numeros": r[3].split(","),
                "estado": r[4],
                "fuente_primaria": r[5],
                "fuente_secundaria": r[6],
                "nro_sorteo": r[7],
            }
            for r in recientes
        ],
    }
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))


def ejecutar_ciclo(session: requests.Session, max_ciudad_sorteos: int = 10) -> bool:
    cambios = False
    ahora_arg = ahora_argentina()
    fecha_hoy = ahora_arg.strftime("%d/%m/%Y")
    calendar = SorteoCalendar()
    calendar.calibrate_from_db(DB_NAME)

    if ahora_arg.weekday() == 6:
        logging.info("Domingo: no hay sorteos oficiales en Provincia ni Ciudad. Solo monitoreo.")

    manager = DataSourcesManager(session, db_path=DB_NAME, calendar=calendar)
    records, pendientes, alguna_ok = manager.recolectar_todo(max_ciudad_sorteos=max_ciudad_sorteos)
    consolidados = manager.cruzar_y_validar(records)

    nuevos_guardados: list[dict[str, Any]] = []
    if consolidados or alguna_ok:
        resetear_fallos(avisar_recuperacion=True)
        with sqlite3.connect(DB_NAME) as conn:
            for item in consolidados:
                if guardar_sorteo_verificado(
                    conn,
                    loteria=item["loteria"],
                    fecha_iso=item["fecha_iso"],
                    turno=item["turno"],
                    datos_str=item["datos"],
                    estado=item["estado"],
                    fuente_1=item["fuente_1"],
                    fuente_2=item["fuente_2"],
                    nro_sorteo=item["nro_sorteo"],
                ):
                    cambios = True
                    nuevos_guardados.append(item)
            conn.commit()
    else:
        manejar_fallo_scraping("Todas las fuentes oficiales y espejos fallaron en el ciclo")

    if cambios:
        exportar_csvs_desde_db(DB_NAME, CSV_PROVINCIA, CSV_CIUDAD)

    exportar_snapshot_movil(
        db_path=DB_NAME,
        out_path=SNAPSHOT_JSON,
        calendar=calendar,
        health_events=manager.health_events,
    )

    reporte = f"🏆 <b>REPORTE QUINIELA-ZERO PRO MAX - {fecha_hoy}</b> 🏆\n\n"
    if nuevos_guardados:
        reporte += "🆕 <b>SORTEOS REGISTRADOS AUTOMATICAMENTE</b>\n"
        for item in nuevos_guardados[:12]:
            cabeza = item["datos"].split(",")[0]
            reporte += (
                f"• <b>{item['loteria']} · {item['turno']}</b> ({item['fecha_iso']}) "
                f"[{item['estado']}]: Cabeza <code>{cabeza}</code>\n"
            )
        reporte += "\n"
    elif pendientes:
        reporte += f"⏳ En espera: {', '.join(pendientes)}\n\n"

    for jur in JURISDICCIONES:
        est = analizar_estadisticas(jur, window_days=90, top_n=5, db_path=DB_NAME)
        cands = est.get("candidatos") or []
        if cands:
            listado = ", ".join(f"<b>{c['numero']}</b> ({c['frecuencia_pct']}%)" for c in cands)
            reporte += f"📊 <b>{jur}</b> ({est['total_sorteos']} sorteos 90d): {listado}\n"

    reporte += "\n<i>Las probabilidades mostradas son estimaciones estadísticas. Jugá responsablemente.</i>\n"

    try:
        with open("resultados.txt", "w", encoding="utf-8") as f:
            f.write(reporte)
    except Exception as exc:
        logging.error(f"Error escribiendo resultados.txt: {exc}")

    if cambios:
        enviar_telegram(reporte)

    return cambios


def main() -> None:
    init_db(DB_NAME)
    importar_historial(DB_NAME, CSV_PROVINCIA, CSV_CIUDAD)
    logging.info("=== MOTOR QUINIELA-ZERO PRO MAX 100% OPERATIVO ===")
    session = get_session()

    if "--backfill" in sys.argv:
        cambios = ejecutar_ciclo(session, max_ciudad_sorteos=135)
        print(f"BACKFILL_CAMBIOS={1 if cambios else 0}")
        return

    if os.getenv("GITHUB_ACTIONS") == "true" or "--once" in sys.argv:
        cambios = ejecutar_ciclo(session)
        print(f"CAMBIOS={1 if cambios else 0}")
        return

    try:
        while True:
            ejecutar_ciclo(session)
            time.sleep(INTERVALO)
    except KeyboardInterrupt:
        logging.info("Interrupción detectada. Apagando motor de forma segura.")


if __name__ == "__main__":
    main()
