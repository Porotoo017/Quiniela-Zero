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
 *  - Premios oficiales por 8, 7, 6 o 5 aciertos, además de reglas configurables de control.
 */
object QuinielaPlusEngine {
    const val DEFAULT_TICKET_COST_ARS = 1000.0

    fun validarCombinacion(
        numeros: List<String>,
        cantidadBoletas: Int = 1,
        costoUnitario: Double = DEFAULT_TICKET_COST_ARS
    ): PlusEvaluacion {
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

    /**
     * Evalúa una jugada individual de Control de Jugadas contra el sorteo oficial de esa fecha.
     */
    fun evaluarJugada(
        jugada: PlusJugada,
        sorteo: PlusSorteo?,
        reglas: PlusReglasPremios
    ): PlusJugada {
        val cleanNums = jugada.numeros
            .map { it.trim().takeLast(2).padStart(2, '0') }
            .filter { it.length == 2 && it.all(Char::isDigit) }
            .distinct()

        if (sorteo == null || sorteo.numerosPlus.isEmpty()) {
            return jugada.copy(
                numeros = cleanNums,
                estado = EstadoJugada.PENDIENTE,
                aciertosExactos = emptyList(),
                aciertosParciales = emptyList(),
                premioGanado = 0.0,
                detalleComparacion = "Pendiente · Sin sorteo oficial cargado para ${jugada.fechaIso}"
            )
        }

        val targetList: List<String> = when (jugada.modalidad) {
            ModalidadPlus.QUINIELA_PLUS -> sorteo.numerosPlus
            ModalidadPlus.SUPER_PLUS -> {
                if (sorteo.numerosCompletos4.size < 8) {
                    return jugada.copy(
                        numeros = cleanNums,
                        estado = EstadoJugada.PENDIENTE,
                        aciertosExactos = emptyList(),
                        aciertosParciales = emptyList(),
                        premioGanado = 0.0,
                        detalleComparacion = "Pendiente · El sorteo de ${jugada.fechaIso} no posee 4 cifras para Súper Plus"
                    )
                }
                sorteo.numerosCompletos4.map { it.take(2) }
            }
            ModalidadPlus.CHANCE_PLUS -> {
                if (sorteo.numerosCompletos4.size < 8) {
                    return jugada.copy(
                        numeros = cleanNums,
                        estado = EstadoJugada.PENDIENTE,
                        aciertosExactos = emptyList(),
                        aciertosParciales = emptyList(),
                        premioGanado = 0.0,
                        detalleComparacion = "Pendiente · El sorteo de ${jugada.fechaIso} no posee 4 cifras para Chance Plus"
                    )
                }
                sorteo.numerosCompletos4.map { it.substring(1, 3) }
            }
            ModalidadPlus.CABEZA_EXACTA -> listOf(sorteo.numerosPlus.first())
        }

        val targetSet = targetList.toSet()
        val targetInts = targetSet.mapNotNull { it.toIntOrNull() }.toSet()
        val targetEndings = targetInts.map { it % 10 }.toSet()

        val exactos = cleanNums.filter { it in targetSet }
        val parciales = cleanNums.filter { num ->
            if (num in targetSet) return@filter false
            val v = num.toIntOrNull() ?: return@filter false
            val approxHit = reglas.habilitarParcialAproximacion &&
                (((v + 1) % 100) in targetInts || ((v + 99) % 100) in targetInts)
            val endingHit = reglas.habilitarParcialTerminacion && (v % 10) in targetEndings
            approxHit || endingHit
        }

        val premioExacto = when (jugada.modalidad) {
            ModalidadPlus.CABEZA_EXACTA -> if (exactos.isNotEmpty()) jugada.montoApostado * reglas.multCabezaExacta else 0.0
            ModalidadPlus.QUINIELA_PLUS, ModalidadPlus.SUPER_PLUS, ModalidadPlus.CHANCE_PLUS -> when (exactos.size) {
                8 -> jugada.montoApostado * reglas.mult8Aciertos
                7 -> jugada.montoApostado * reglas.mult7Aciertos
                6 -> jugada.montoApostado * reglas.mult6Aciertos
                5 -> jugada.montoApostado * reglas.mult5Aciertos
                else -> 0.0
            }
        }

        val premioParcial = if (
            parciales.isNotEmpty() &&
            reglas.multAciertoParcial > 0.0 &&
            (reglas.habilitarParcialAproximacion || reglas.habilitarParcialTerminacion)
        ) {
            jugada.montoApostado * reglas.multAciertoParcial * parciales.size
        } else 0.0

        val premioTotal = premioExacto + premioParcial
        val gano = premioTotal > 0.0
        val estado = if (gano) EstadoJugada.GANO else EstadoJugada.NO_GANO

        val detalle = buildString {
            append("${jugada.modalidad.label}: ")
            append("${exactos.size} exactos")
            if (parciales.isNotEmpty()) append(" · ${parciales.size} parciales")
            if (gano) {
                append(" · Premio $${String.format(java.util.Locale.US, "%,.0f", premioTotal)}")
            } else {
                append(" · Sin premio según reglas")
            }
        }

        return jugada.copy(
            numeros = cleanNums,
            estado = estado,
            aciertosExactos = exactos,
            aciertosParciales = parciales,
            premioGanado = premioTotal,
            detalleComparacion = detalle
        )
    }

    fun evaluarTodasJugadas(
        jugadas: List<PlusJugada>,
        historialPorFecha: Map<String, PlusSorteo>,
        reglas: PlusReglasPremios
    ): List<PlusJugada> = jugadas.map { j ->
        evaluarJugada(j, historialPorFecha[j.fechaIso], reglas)
    }

    fun calcularResumenJugadas(jugadas: List<PlusJugada>): PlusResumenJugadas {
        val total = jugadas.size
        val resueltas = jugadas.filter { it.estado != EstadoJugada.PENDIENTE }
        val ganadoras = resueltas.count { it.estado == EstadoJugada.GANO }
        val pendientes = jugadas.count { it.estado == EstadoJugada.PENDIENTE }
        val totalApostado = jugadas.sumOf { it.montoApostado }
        val totalGanado = jugadas.sumOf { it.premioGanado }
        val pctAcierto = if (resueltas.isNotEmpty()) (ganadoras * 100.0) / resueltas.size else 0.0

        return PlusResumenJugadas(
            totalJugadas = total,
            jugadasResueltas = resueltas.size,
            jugadasGanadoras = ganadoras,
            jugadasPendientes = pendientes,
            totalApostado = totalApostado,
            totalGanado = totalGanado,
            balanceNeto = totalGanado - totalApostado,
            porcentajeAcierto = pctAcierto
        )
    }
}
