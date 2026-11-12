import requests
from bs4 import BeautifulSoup

URLS = [
    ("CIUDAD", "https://www.loteriadelaciudad.gob.ar/resultados.html", ".premio"),
    ("PROVINCIA", "https://www.loteria.gba.gov.ar/index.php/resultados", ".premio"),
]

for nombre, url, selector in URLS:
    print(f"\n=== {nombre} ===")
    try:
        r = requests.get(url, headers={"User-Agent": "Mozilla/5.0"}, timeout=15)
        print(f"HTTP {r.status_code} | {len(r.text)} bytes")
        soup = BeautifulSoup(r.text, "html.parser")
        items = [s.get_text(strip=True) for s in soup.select(selector)]
        print(f"Elementos '{selector}': {len(items)}")
        if items:
            print(f"Primeros 5: {items[:5]}")
        else:
            clases = set()
            for tag in soup.find_all(class_=True):
                for c in tag.get("class", []):
                    low = c.lower()
                    if "prem" in low or "result" in low or "num" in low:
                        clases.add(c)
            print(f"Clases candidatas: {sorted(clases)[:10] or 'ninguna'}")
    except Exception as e:
        print(f"ERROR: {e}")
