package com.jayesh.satnav.domain.model

/**
 * Routing profiles supported by the offline routing stack.
 *
 * `profileName` maps directly to the GraphHopper profile/vehicle name that we
 * persist and send into routing requests.
 */
enum class RoutingProfile(
    val profileName: String,
) {
    CAR("car"),
    BIKE("bike"),
    FOOT("foot"),
    PUBLIC_TRANSIT("public_transit"),
    MIXED("mixed");

    companion object {
        fun default(): RoutingProfile = CAR

        fun fromString(value: String): RoutingProfile? {
            return fromProfileName(value)
        }

        fun fromProfileName(value: String): RoutingProfile? {
            return when {
                value.equals("bicycle", ignoreCase = true) -> BIKE
                value.equals("walking", ignoreCase = true) -> FOOT
                else -> entries.find { profile ->
                    profile.name.equals(value, ignoreCase = true) ||
                        profile.profileName.equals(value, ignoreCase = true)
                }
            }
        }
    }
}
