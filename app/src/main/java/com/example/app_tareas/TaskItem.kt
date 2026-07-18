package com.example.app_tareas

data class TaskItem(
    val id: Long,
    var title: String,
    var completed: Boolean = false
)
