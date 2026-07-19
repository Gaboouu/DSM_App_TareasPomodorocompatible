
package com.example.app_tareas

import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.app_tareas.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: PomodoroViewModel

    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            val finished = viewModel.updateTimer()
            if (finished) {
                val session = viewModel.completeSession()
                Toast.makeText(
                    this@MainActivity,
                    "¡Pomodoro completado: ${session.taskTitle}!",
                    Toast.LENGTH_LONG
                ).show()
                refreshAll()
            } else {
                refreshTimer()
            }
            if (viewModel.isRunning) {
                handler.postDelayed(this, 1000L)
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[PomodoroViewModel::class.java]
        viewModel.load(this)

        setupButtons()
        refreshAll()
    }

    override fun onStart() {
        super.onStart()
        val justFinished = viewModel.updateTimer()
        if (justFinished) {
            val session = viewModel.completeSession()
            Toast.makeText(
                this,
                "¡Pomodoro completado mientras estabas fuera: ${session.taskTitle}!",
                Toast.LENGTH_LONG
            ).show()
        }
        refreshAll()

        if (viewModel.isRunning) {
            handler.removeCallbacks(timerRunnable)
            handler.postDelayed(timerRunnable, 1000L)
        }
    }

    override fun onStop() {
        handler.removeCallbacks(timerRunnable)
        viewModel.save()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerRunnable)
        super.onDestroy()
    }



    private fun setupButtons() {
        binding.btnAddTask.setOnClickListener {
            val text = binding.etTask.text.toString()
            if (viewModel.addTask(text)) {
                binding.etTask.text?.clear()
                refreshAll()
            } else {
                binding.etTask.error = "Escribe una tarea válida"
            }
        }

        binding.btnStart.setOnClickListener {
            if (viewModel.activeTask() == null) {
                Toast.makeText(this, "Selecciona una tarea activa primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.start()
            handler.removeCallbacks(timerRunnable)
            handler.postDelayed(timerRunnable, 1000L)
            refreshAll()
        }

        binding.btnPause.setOnClickListener {
            viewModel.pause()
            handler.removeCallbacks(timerRunnable)
            refreshAll()
        }

        binding.btnResume.setOnClickListener {
            viewModel.resume()
            handler.removeCallbacks(timerRunnable)
            handler.postDelayed(timerRunnable, 1000L)
            refreshAll()
        }

        binding.btnReset.setOnClickListener {
            viewModel.reset()
            handler.removeCallbacks(timerRunnable)
            refreshAll()
        }
    }



    private fun refreshAll() {
        refreshTimer()
        refreshTasks()
        refreshHistory()
        refreshSummary()
    }

    private fun refreshTimer() {
        val time = viewModel.remainingMillis.coerceAtLeast(0L)
        val minutes = time / 60_000L
        val seconds = (time % 60_000L) / 1_000L
        binding.tvTimer.text = String.format("%02d:%02d", minutes, seconds)

        val total    = PomodoroViewModel.POMODORO_DURATION
        val progress = (((total - time) * 100L) / total).toInt().coerceIn(0, 100)
        binding.progressBar.progress = progress

        binding.tvActiveTask.text =
            viewModel.activeTask()?.let { "Tarea activa: ${it.title}" }
                ?: "Tarea activa: ninguna"

        binding.btnStart.isEnabled  = !viewModel.isRunning && !viewModel.isPaused
        binding.btnPause.isEnabled  = viewModel.isRunning
        binding.btnResume.isEnabled = viewModel.isPaused
        binding.btnReset.isEnabled  = viewModel.isRunning || viewModel.isPaused ||
                viewModel.remainingMillis < PomodoroViewModel.POMODORO_DURATION
    }

    private fun refreshTasks() {
        binding.tasksContainer.removeAllViews()

        if (viewModel.tasks.isEmpty()) {
            binding.tvEmptyTasks.visibility = View.VISIBLE
            return
        }
        binding.tvEmptyTasks.visibility = View.GONE

        viewModel.tasks.forEach { task ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                setBackgroundResource(R.drawable.bg_task_card)
            }


            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
            }

            val check = CheckBox(this).apply {
                text      = task.title
                isChecked = task.completed
                textSize  = 16f
                if (task.completed) {
                    paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    alpha = 0.45f
                } else {
                    paintFlags = paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    alpha = 1f
                }
                setOnCheckedChangeListener { _, checked ->
                    viewModel.toggleTask(task.id, checked)
                    refreshAll()
                }
            }
            topRow.addView(
                check,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            val btnSelect = Button(this).apply {
                text      = if (viewModel.activeTaskId == task.id) "✓ ACTIVA" else "Seleccionar"
                isEnabled = viewModel.activeTaskId != task.id
                setOnClickListener {
                    viewModel.selectTask(task.id)
                    refreshAll()
                }
            }
            topRow.addView(btnSelect)
            card.addView(topRow)

            val btnDelete = Button(this).apply {
                text = "Eliminar tarea"
                setOnClickListener {
                    viewModel.deleteTask(task.id)
                    refreshAll()
                }
            }
            card.addView(btnDelete)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dpToPx(10)) }
            binding.tasksContainer.addView(card, params)
        }
    }

    private fun refreshHistory() {
        binding.historyContainer.removeAllViews()

        if (viewModel.sessions.isEmpty()) {
            binding.tvEmptyHistory.visibility = View.VISIBLE
            return
        }
        binding.tvEmptyHistory.visibility = View.GONE

        viewModel.sessions.forEachIndexed { index, session ->
            val item = TextView(this).apply {
                text = "${index + 1}. ${session.taskTitle}\n" +
                        "${session.durationMinutes} min  •  ${session.date}"
                textSize = 15f
                setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                setBackgroundResource(R.drawable.bg_history_item)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dpToPx(6)) }
            binding.historyContainer.addView(item, params)
        }
    }

    private fun refreshSummary() {
        val pending = viewModel.tasks.count { !it.completed }
        binding.tvSummary.text =
            "Pendientes: $pending   •   Sesiones: ${viewModel.sessions.size}"
    }


    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()
}
