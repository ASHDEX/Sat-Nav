package com.jayesh.satnav.domain.model

data class NavInstruction(
    val sign: Int,
    val streetName: String,
    val distanceMeters: Double,
    val durationMillis: Long,
) {
    val turnLabel: String
        get() = when (sign) {
            -3 -> "Sharp left"
            -2 -> "Turn left"
            -1 -> "Slight left"
             0 -> "Continue"
             1 -> "Slight right"
             2 -> "Turn right"
             3 -> "Sharp right"
             4 -> "Arrive"
             5 -> "Via point"
             6 -> "Roundabout"
            else -> "Continue"
        }

    val humanText: String
        get() = if (streetName.isNotBlank()) "$turnLabel on $streetName" else turnLabel

    companion object {
        const val SIGN_CONTINUE = 0
        const val SIGN_FINISH = 4
    }
}
