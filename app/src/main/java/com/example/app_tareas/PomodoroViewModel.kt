
package com.example.app_tareas

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PomodoroViewModel : ViewModel() {

    companion object {
        const val POMODORO_DURATION = 25 * 60 * 1000L
        private const val PREFS_NAME      = "pomodoro_state"
        private const val KEY_REMAINING   = "timer_remaining"
        private const val KEY_END_TIME    = "timer_end"
        private const val KEY_RUNNING     = "timer_running"
        private const val KEY_PAUSED      = "timer_paused"
        private const val KEY_ACTIVE_TASK = "active_task"
        private const val KEY_TASK_COUNT  = "task_count"
        private const val KEY_SESSION_COUNT = "session_count"
    }

    // ── Estado público ────────────────────────────────────────────────────────
    val tasks    = mutableListOf<TaskItem>()
    val sessions = mutableListOf<SessionItem>()

    var remainingMillis: Long = POMODORO_DURATION
        private set
    var endTimeMillis: Long = 0L
        private set
    var isRunning: Boolean  = false
        private set
    var isPaused: Boolean   = false
        private set
    var activeTaskId: Long? = null
        private set

    private var prefs: SharedPreferences? = null
    private var loaded = false

    // ── Persistencia ─────────────────────────────────────────────────────────

    /** Carga el estado desde SharedPreferences. Solo se ejecuta una vez. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val p = prefs ?: return

        // Tareas
        tasks.clear()
        val taskCount = p.getInt(KEY_TASK_COUNT, 0)
        repeat(taskCount) { i ->
            tasks += TaskItem(
                id        = p.getLong("task_${i}_id", i.toLong()),
                title     = p.getString("task_${i}_title", "") ?: "",
                completed = p.getBoolean("task_${i}_completed", false)
            )
        }

        // Sesiones
        sessions.clear()
        val sessionCount = p.getInt(KEY_SESSION_COUNT, 0)
        repeat(sessionCount) { i ->
            sessions += SessionItem(
                id              = p.getLong("session_${i}_id", i.toLong()),
                taskTitle       = p.getString("session_${i}_task", "Tarea") ?: "Tarea",
                durationMinutes = p.getInt("session_${i}_duration", 25),
                date            = p.getString("session_${i}_date", "") ?: ""
            )
        }

        // Timer
        remainingMillis = p.getLong(KEY_REMAINING, POMODORO_DURATION)
        endTimeMillis   = p.getLong(KEY_END_TIME, 0L)
        isRunning       = p.getBoolean(KEY_RUNNING, false)
        isPaused        = p.getBoolean(KEY_PAUSED, false)
        activeTaskId    = p.getLong(KEY_ACTIVE_TASK, Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE }

        // Recalcular tiempo real si el temporizador estaba corriendo
        if (isRunning) {
            val newRemaining = endTimeMillis - System.currentTimeMillis()
            if (newRemaining <= 0L) {
                remainingMillis = 0L
                isRunning = false
                isPaused  = false
            } else {
                remainingMillis = newRemaining
            }
        }
    }

    fun save() {
        val p = prefs ?: return
        val editor = p.edit()

        editor.putLong(KEY_REMAINING, remainingMillis)
        editor.putLong(KEY_END_TIME, endTimeMillis)
        editor.putBoolean(KEY_RUNNING, isRunning)
        editor.putBoolean(KEY_PAUSED, isPaused)
        editor.putInt(KEY_TASK_COUNT, tasks.size)
        editor.putInt(KEY_SESSION_COUNT, sessions.size)

        if (activeTaskId == null) editor.remove(KEY_ACTIVE_TASK)
        else editor.putLong(KEY_ACTIVE_TASK, activeTaskId!!)

        tasks.forEachIndexed { i, task ->
            editor.putLong("task_${i}_id", task.id)
            editor.putString("task_${i}_title", task.title)
            editor.putBoolean("task_${i}_completed", task.completed)
        }

        sessions.forEachIndexed { i, session ->
            editor.putLong("session_${i}_id", session.id)
            editor.putString("session_${i}_task", session.taskTitle)
            editor.putInt("session_${i}_duration", session.durationMinutes)
            editor.putString("session_${i}_date", session.date)
        }

        editor.apply()
    }

    // ── Tareas ────────────────────────────────────────────────────────────────

    fun addTask(title: String): Boolean {
        val clean = title.trim()
        if (clean.isEmpty()) return false
        val newId = (tasks.maxOfOrNull { it.id } ?: 0L) + 1L
        tasks += TaskItem(newId, clean)
        save()
        return true
    }

    fun deleteTask(id: Long) {
        tasks.removeAll { it.id == id }
        if (activeTaskId == id) activeTaskId = null
        save()
    }

    fun toggleTask(id: Long, completed: Boolean) {
        tasks.find { it.id == id }?.completed = completed
        save()
    }

    fun selectTask(id: Long) {
        if (tasks.any { it.id == id }) {
            activeTaskId = id
            save()
        }
    }

    fun activeTask(): TaskItem? = tasks.find { it.id == activeTaskId }

    // ── Temporizador ─────────────────────────────────────────────────────────

    fun start() {
        if (isRunning) return
        endTimeMillis = System.currentTimeMillis() + remainingMillis
        isRunning = true
        isPaused  = false
        save()
    }

    fun pause() {
        if (!isRunning) return
        updateTimer()
        isRunning = false
        isPaused  = true
        save()
    }

    fun resume() {
        if (!isPaused || remainingMillis <= 0L) return
        endTimeMillis = System.currentTimeMillis() + remainingMillis
        isRunning = true
        isPaused  = false
        save()
    }

    fun reset() {
        remainingMillis = POMODORO_DURATION
        endTimeMillis   = 0L
        isRunning       = false
        isPaused        = false
        save()
    }

    /**
     * Recalcula el tiempo restante a partir del endTime real.
     * @return true solo si el temporizador acaba de terminar en esta llamada.
     */
    fun updateTimer(): Boolean {
        if (!isRunning) return false
        val newRemaining = endTimeMillis - System.currentTimeMillis()
        return if (newRemaining <= 0L) {
            remainingMillis = 0L
            isRunning = false
            isPaused  = false
            save()
            true
        } else {
            remainingMillis = newRemaining
            false
        }
    }

    fun completeSession(): SessionItem {
        val title = activeTask()?.title ?: "Sin tarea seleccionada"
        val session = SessionItem(
            id              = (sessions.maxOfOrNull { it.id } ?: 0L) + 1L,
            taskTitle       = title,
            durationMinutes = 25,
            date            = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(Date())
        )
        sessions.add(0, session)
        save()
        return session
    }
}
