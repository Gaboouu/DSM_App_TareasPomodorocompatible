package com.example.app_tareas

data class SessionItem(
    val id: Long,
    val taskTitle: String,
    val durationMinutes: Int,
    val date: String
)
