package com.jayesh.satnav.navigation

import kotlinx.serialization.Serializable

/**
 * Debug-only navigation routes.
 */
@Serializable
object DebugSettings : NavRoute {
    override val route: String = "debug_settings"
}

/**
 * Extended route parser for debug builds.
 */
fun parseDebugRouteArguments(route: String, arguments: Map<String, String>): NavRoute? {
    return when (route) {
        DebugSettings.route -> DebugSettings
        else -> null
    }
}

/**
 * Combine debug routes with main routes.
 */
fun parseAllRouteArguments(route: String, arguments: Map<String, String>): NavRoute? {
    return parseRouteArguments(route, arguments) ?: parseDebugRouteArguments(route, arguments)
}