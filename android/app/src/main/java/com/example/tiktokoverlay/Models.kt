package com.example.tiktokoverlay

data class SignupRequest(
    val email: String,
    val password: String,
    val role: String, // "fan" or "creator"
    val tiktok_username: String? = null
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    val user: UserDto
)

data class UserDto(
    val id: String,
    val role: String,
    val email: String
)

data class CreatorLookupResponse(
    val registered: Boolean,
    val verified: Boolean? = null,
    val tiktok_username: String? = null
)

data class InitiateDonationRequest(
    val tiktok_username: String,
    val amount: String
)

data class InitiateDonationResponse(
    val checkout_url: String?,
    val tx_ref: String,
    val test_mode: Boolean = false
)

data class DonationStatusResponse(
    val status: String,
    val amount: Double,
    val to_tiktok_username: String
)
