package com.yohanvelasquez.recordatorios.ui.navegacion

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yohanvelasquez.recordatorios.ui.detalle.DetalleRoute
import com.yohanvelasquez.recordatorios.ui.formulario.FormularioRoute
import com.yohanvelasquez.recordatorios.ui.lista.ListaRoute

object Rutas {
    const val LISTA = "lista"
    const val CREAR = "crear"
    const val ARG_ID = "recordatorioId"
    const val DETALLE = "detalle/{$ARG_ID}"

    fun detalle(id: Int) = "detalle/$id"
}

/** Único lugar que conoce el NavController; las pantallas solo reciben lambdas. */
@Composable
fun RecordatoriosNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Rutas.LISTA, modifier = modifier) {
        composable(Rutas.LISTA) {
            ListaRoute(
                onCrearRecordatorio = { navController.navigate(Rutas.CREAR) },
                onRecordatorioClick = { id -> navController.navigate(Rutas.detalle(id)) },
            )
        }
        composable(Rutas.CREAR) {
            FormularioRoute(
                onCancelar = { navController.volver() },
                onVolver = { navController.volver() },
            )
        }
        composable(
            route = Rutas.DETALLE,
            arguments = listOf(navArgument(Rutas.ARG_ID) { type = NavType.IntType }),
        ) {
            DetalleRoute(onVolver = { navController.volver() })
        }
    }
}

/**
 * Regresa al destino anterior con popBackStack(). Solo actúa si la pantalla actual está activa,
 * así un doble toque en "Cancelar" no saca también el listado del historial.
 */
private fun NavHostController.volver() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}
