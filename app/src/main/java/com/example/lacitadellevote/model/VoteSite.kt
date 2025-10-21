package com.example.lacitadellevote.model

data class VoteSite(
    val id: String,
    val name: String,
    val url: String,
    val cooldownMinutes: Long
)
