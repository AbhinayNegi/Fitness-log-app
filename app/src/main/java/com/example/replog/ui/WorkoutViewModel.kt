package com.example.replog.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.replog.data.ExerciseDraft
import com.example.replog.data.WorkoutDatabase
import com.example.replog.data.WorkoutRepository
import com.example.replog.data.WorkoutSessionEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WorkoutRepository(WorkoutDatabase.get(application).workoutDao())
    val workouts = repository.workouts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val sessions = repository.sessions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    fun saveWorkout(name: String, exercises: List<ExerciseDraft>, onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.saveWorkout(name, exercises)
            onSaved()
        }
    }

    fun finishWorkout(
        name: String,
        startedAt: Long,
        exercises: List<ExerciseDraft>,
        onFinished: (WorkoutSessionEntity) -> Unit
    ) {
        viewModelScope.launch {
            onFinished(repository.finishWorkout(name, startedAt, exercises))
        }
    }
}
