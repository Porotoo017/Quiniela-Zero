import requests
from quiniela_pro import get_session, parse_gba_html, URL_PROVINCIA

session = get_session()
print(f"Consultando {URL_PROVINCIA} ...")
r = session.get(URL_PROVINCIA, timeout=20)
print(f"HTTP {r.status_code} | {len(r.text)} bytes")
res = parse_gba_html(r.text)
print("Estado parser:", res["status"], "| Motivo:", res.get("reason") or "OK")
print("Turnos listos:", len(res["turnos"]))
for t in res["turnos"]:
    print(f"  - {t['turno']} ({t['fecha_raw']} {t['nro_sorteo']}): {t['datos'][:35]}...")
print("Turnos pendientes (----):", res["pendientes"])
