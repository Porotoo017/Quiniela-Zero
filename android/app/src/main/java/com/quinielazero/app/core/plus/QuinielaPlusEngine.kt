package com.quinielazero.app.core.plus

import com.quinielazero.app.core.model.CandidatoEstadistico
import com.quinielazero.app.core.model.PlusEvaluacion
import com.quinielazero.app.core.model.SorteoDomain

/**
 * Reglas oficiales de Quiniela Plus (Provincia de Buenos Aires):
 *  - Se eligen exactamente 8 números distintos de 2 cifras (00 al 99).
 *  - Sortea de lunes a sábado junto con la Nocturna de Provincia (20 extracciones).
 *  - Una misma boleta cubre 3 modalidades simultáneas:
 *      * Quiniela Plus (últimas 2 cifras)
 *      * Súper Plus (primeras 2 cifras)
 *      * Chance Plus (2 cifras centrales)
 *  - Premios oficiales por 8, 7, 6 o 5 aciertos.
 */
object QuinielaPlusEngine {
    const val DEFAULT_TICKET_COST_ARS = 1000.0

    fun validarCombinacion(numeros: List<String>, cantidadBoletas: Int = 1, costoUnitario: Double = DEFAULT_TICKET_COST_ARS): PlusEvaluacion {
        val limpios = numeros.map { it.trim().padStart(2, '0') }
        val formatoValido = limpios.all { it.length == 2 && it.all(Char::isDigit) }
        val distintos = limpios.toSet()

        val (valida, motivo) = when {
            !formatoValido -> false to "Todos los números deben tener 2 dígitos entre 00 y 99."
            limpios.size != 8 -> false to "Quiniela Plus requiere exactamente 8 números (${limpios.size}/8)."
            distintos.size != 8 -> false to "Los 8 números de la boleta deben ser distintos."
            else -> true to "Combinación válida · Cubre Plus, Súper Plus y Chance Plus."
        }

        return PlusEvaluacion(
            numeros8 = limpios,
            esValida = valida,
            motivo = motivo,
            costoArs = cantidadBoletas.coerceAtLeast(1) * costoUnitario
        )
    }

    fun autocompletarDesdeMotor(
        candidatos: List<CandidatoEstadistico>,
        frios: List<CandidatoEstadistico>,
        semillaExtra: Int = 0
    ): List<String> {
        val pool = LinkedHashSet<String>()
        candidatos.forEach { pool.add(it.numero.takeLast(2).padStart(2, '0')) }
        frios.forEach { if (pool.size < 8) pool.add(it.numero.takeLast(2).padStart(2, '0')) }
        var cursor = (17 + semillaExtra * 13) % 100
        while (pool.size < 8) {
            pool.add(cursor.toString().padStart(2, '0'))
            cursor = (cursor + 11) % 100
        }
        return pool.take(8).sorted()
    }

    fun controlarContraNocturnaProvincia(
        numeros8: List<String>,
        nocturnaProvincia: SorteoDomain?,
        costoArs: Double = DEFAULT_TICKET_COST_ARS
    ): PlusEvaluacion {
        val base = validarCombinacion(numeros8, 1, costoArs)
        if (!base.esValida || nocturnaProvincia == null || nocturnaProvincia.numeros.size != 20) {
            return base
        }

        val elegidos = base.numeros8.toSet()
        val ultimos2 = nocturnaProvincia.numeros.map { it.takeLast(2) }.toSet()
        val primeros2 = nocturnaProvincia.numeros.map { it.take(2) }.toSet()
        val medios2 = nocturnaProvincia.numeros.map { it.substring(1, 3) }.toSet()

        val hitsPlus = elegidos.count { it in ultimos2 }
        val hitsSuper = elegidos.count { it in primeros2 }
        val hitsChance = elegidos.count { it in medios2 }
        val mejor = maxOf(hitsPlus, hitsSuper, hitsChance)

        val tier = when (mejor) {
            8 -> "Pozo Mayor (8 aciertos)"
            7 -> "Segundo Premio (7 aciertos)"
            6 -> "Tercer Premio (6 aciertos)"
            5 -> "Recupero / 5 aciertos"
            else -> "Sin premio ($mejor/8 mejor modalidad)"
        }

        return base.copy(
            aciertosPlus = hitsPlus,
            aciertosSuperPlus = hitsSuper,
            aciertosChancePlus = hitsChance,
            premioTier = tier
        )
    }
}
