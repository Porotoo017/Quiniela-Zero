import csv
import datetime
import json
import os
import sqlite3
import tempfile
import unittest

import quiniela_pro as qp


SAMPLE_GBA_HTML = """
<div class="sorteo">
  <h3>La Previa</h3>
  <span class="fechaSorteo">24/11/2026</span>
  <span class="nSorteo">Sorteo 41200</span>
  <div class="bolillas">
    <div><span>01</span><span>0388</span></div>
    <div><span>02</span><span>7582</span></div>
    <div><span>03</span><span>0600</span></div>
    <div><span>04</span><span>3991</span></div>
    <div><span>05</span><span>1234</span></div>
    <div><span>06</span><span>5678</span></div>
    <div><span>07</span><span>9012</span></div>
    <div><span>08</span><span>3456</span></div>
    <div><span>09</span><span>7890</span></div>
    <div><span>10</span><span>1111</span></div>
    <div><span>11</span><span>2222</span></div>
    <div><span>12</span><span>3333</span></div>
    <div><span>13</span><span>4444</span></div>
    <div><span>14</span><span>5555</span></div>
    <div><span>15</span><span>6666</span></div>
    <div><span>16</span><span>7777</span></div>
    <div><span>17</span><span>8888</span></div>
    <div><span>18</span><span>9999</span></div>
    <div><span>19</span><span>0001</span></div>
    <div><span>20</span><span>0002</span></div>
  </div>
</div>
"""

SAMPLE_GBA_PENDING = """
<div class="sorteo">
  <h3>Matutina</h3>
  <span class="fechaSorteo">25/11/2026</span>
  <div class="bolillas">
    <div><span>01</span><span>----</span></div>
    <div><span>02</span><span>----</span></div>
    <div><span>03</span><span>----</span></div>
    <div><span>04</span><span>----</span></div>
    <div><span>05</span><span>----</span></div>
    <div><span>06</span><span>----</span></div>
    <div><span>07</span><span>----</span></div>
    <div><span>08</span><span>----</span></div>
    <div><span>09</span><span>----</span></div>
    <div><span>10</span><span>----</span></div>
    <div><span>11</span><span>----</span></div>
    <div><span>12</span><span>----</span></div>
    <div><span>13</span><span>----</span></div>
    <div><span>14</span><span>----</span></div>
    <div><span>15</span><span>----</span></div>
    <div><span>16</span><span>----</span></div>
    <div><span>17</span><span>----</span></div>
    <div><span>18</span><span>----</span></div>
    <div><span>19</span><span>----</span></div>
    <div><span>20</span><span>----</span></div>
  </div>
</div>
"""

SAMPLE_CIUDAD_JS = (
    'window.RESULTADOS_DATA = [{"sorteo":53211,"fecha":"24\\/11\\/2026","modalidad":"NOCT",'
    '"jurisdicciones":{"51":{"nombre":"CIUDAD DE BS AS","numeros":['
    + ",".join(f'{{"pos":"{i:02d}","val":"{1000+i:04d}"}}' for i in range(1, 21))
    + ']},"53":{"nombre":"PROVINCIA DE BS AS","numeros":['
    + ",".join(f'{{"pos":"{i:02d}","val":"{2000+i:04d}"}}' for i in range(1, 21))
    + "]}}}];"
)


class QuinielaEngineTests(unittest.TestCase):
    def test_parsers_valid_pending_and_malformed(self):
        res_ok = qp.parse_gba_html(SAMPLE_GBA_HTML)
        self.assertEqual(res_ok["status"], "OK")
        self.assertEqual(len(res_ok["turnos"]), 1)
        self.assertEqual(res_ok["turnos"][0]["fecha_iso"], "2026-11-24")
        self.assertEqual(res_ok["turnos"][0]["turno"], "LA PREVIA")

        res_pending = qp.parse_gba_html(SAMPLE_GBA_PENDING)
        self.assertEqual(res_pending["status"], "PENDING")

        res_bad = qp.parse_gba_html("<html><body><div>roto</div></body></html>")
        self.assertEqual(res_bad["status"], "ERROR")

        ciudad_items = qp.parse_ciudad_resultados_js(SAMPLE_CIUDAD_JS)
        self.assertEqual(len(ciudad_items), 2)
        self.assertEqual({r["loteria"] for r in ciudad_items}, {"CIUDAD", "PROVINCIA"})
        self.assertEqual(ciudad_items[0]["fecha_iso"], "2026-11-24")
        self.assertEqual(ciudad_items[0]["turno"], "NOCTURNA")

    def test_calendar_rules_and_sunday_block(self):
        cal = qp.SorteoCalendar(holiday_dates={"2026-12-25"})
        # Domingo 2026-11-22 -> 0 sorteos
        sunday = datetime.date(2026, 11, 22)
        self.assertEqual(cal.turnos_para_fecha("PROVINCIA", sunday), [])
        self.assertFalse(cal.es_turno_habilitado("CIUDAD", sunday, "NOCTURNA"))
        # Feriado configurado -> 0 sorteos
        self.assertEqual(cal.turnos_para_fecha("PROVINCIA", datetime.date(2026, 12, 25)), [])
        # Lunes -> 5 sorteos
        self.assertEqual(len(cal.turnos_para_fecha("PROVINCIA", datetime.date(2026, 11, 23))), 5)

    def test_cross_validation_and_persistence(self):
        with tempfile.TemporaryDirectory() as tmp:
            db_path = os.path.join(tmp, "test.db")
            csv_p = os.path.join(tmp, "prov.csv")
            csv_c = os.path.join(tmp, "ciu.csv")
            qp.init_db(db_path)
            mgr = qp.DataSourcesManager(qp.get_session(), db_path=db_path)

            datos_a = ",".join(f"{1000+i:04d}" for i in range(20))
            datos_b = ",".join(f"{3000+i:04d}" for i in range(20))
            records = [
                qp.SourceRecord("PROVINCIA", "2026-11-24", "LA PREVIA", datos_a, "loteria.gba.gov.ar"),
                qp.SourceRecord("PROVINCIA", "2026-11-24", "LA PREVIA", datos_a, "vivitusuerte.com"),
                qp.SourceRecord("CIUDAD", "2026-11-24", "LA PREVIA", datos_a, "quiniela.loteriadelaciudad.gob.ar"),
                qp.SourceRecord("CIUDAD", "2026-11-24", "PRIMERA", datos_a, "quiniela.loteriadelaciudad.gob.ar"),
                qp.SourceRecord("CIUDAD", "2026-11-24", "PRIMERA", datos_b, "vivitusuerte.com"),
                # Domingo inválido debe filtrarse por integridad
                qp.SourceRecord("PROVINCIA", "2026-11-22", "NOCTURNA", datos_a, "loteria.gba.gov.ar"),
            ]
            consolidados = mgr.cruzar_y_validar(records)
            estados = {(c["loteria"], c["turno"]): c["estado"] for c in consolidados}
            self.assertEqual(estados[("PROVINCIA", "LA PREVIA")], "VERIFICADO")
            self.assertEqual(estados[("CIUDAD", "LA PREVIA")], "NO VERIFICADO")
            self.assertEqual(estados[("CIUDAD", "PRIMERA")], "PENDIENTE")
            self.assertNotIn(("PROVINCIA", "NOCTURNA"), estados)

            with sqlite3.connect(db_path) as conn:
                for c in consolidados:
                    qp.guardar_sorteo_verificado(
                        conn, c["loteria"], c["fecha_iso"], c["turno"], c["datos"], c["estado"], c["fuente_1"], c["fuente_2"]
                    )
                conn.commit()
            qp.exportar_csvs_desde_db(db_path, csv_p, csv_c)
            with open(csv_p, "r", encoding="utf-8") as f:
                rows = list(csv.reader(f))
            self.assertEqual(len(rows), 2)
            est = qp.analizar_estadisticas("PROVINCIA", window_days=None, top_n=5, db_path=db_path)
            self.assertEqual(est["total_sorteos"], 1)
            self.assertEqual(len(est["candidatos"]), 5)
            self.assertIn("Basado en 1 sorteos", est["candidatos"][0]["resumen"])



    def test_lotba_trailing_js_and_vivitusuerte_attr_date(self):
        js_trailing = SAMPLE_CIUDAD_JS + "window.POZO_ULTIMO_SORTEO = 0;"
        items = qp.parse_ciudad_resultados_js(js_trailing)
        self.assertEqual(len(items), 2)

        vivi_html = (
            '<div id="seccionPizarra" data-fecha-default="2026-11-24">'
            '<div class="card"><div class="card-header">Nocturna</div>'
            '<table data-pizarra-momento="5">'
            + "".join(f'<tr><td class="caja-slot">{i}.</td><td class="caja-resultado">{1000+i:04d}</td></tr>' for i in range(1, 21))
            + '</table></div></div>'
        )
        v_items = qp.parse_vivitusuerte_html(vivi_html, 'CIUDAD')
        self.assertEqual(len(v_items), 1)
        self.assertEqual(v_items[0]['fecha_iso'], '2026-11-24')


if __name__ == "__main__":
    unittest.main()
