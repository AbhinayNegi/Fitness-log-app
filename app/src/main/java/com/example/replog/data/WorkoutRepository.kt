package com.example.replog.data

import kotlinx.coroutines.flow.Flow

data class SetDraft(val reps: Int, val weight: Double, val completed: Boolean = false)

data class ExerciseDraft(val name: String, val sets: List<SetDraft>)

class WorkoutRepository(private val dao: WorkoutDao) {
    val workouts: Flow<List<WorkoutWithExercises>> = dao.observeWorkouts()
    val sessions: Flow<List<WorkoutSessionEntity>> = dao.observeSessions()

    suspend fun saveWorkout(name: String, exercises: List<ExerciseDraft>) {
        val workoutId = dao.insertWorkout(WorkoutEntity(name = name.trim()))
        exercises.forEachIndexed { exerciseIndex, draft ->
            val exerciseId = dao.insertExercise(
                ExerciseEntity(
                    workoutId = workoutId,
                    name = draft.name.trim(),
                    position = exerciseIndex
                )
            )
            dao.insertSets(draft.sets.mapIndexed { setIndex, set ->
                SetEntity(
                    exerciseId = exerciseId,
                    position = setIndex,
                    reps = set.reps,
                    weight = set.weight
                )
            })
        }
    }

    suspend fun finishWorkout(
        name: String,
        startedAt: Long,
        exercises: List<ExerciseDraft>
    ): WorkoutSessionEntity {
        val completedAt = System.currentTimeMillis()
        val completedSets = exercises.flatMap { exercise -> exercise.sets }.filter { it.completed }
        val session = WorkoutSessionEntity(
            workoutName = name.trim(),
            startedAt = startedAt,
            completedAt = completedAt,
            durationMs = (completedAt - startedAt).coerceAtLeast(0L),
            totalVolume = completedSets.sumOf { it.reps * it.weight },
            completedSetCount = completedSets.size,
            completedExerciseCount = exercises.count { exercise -> exercise.sets.any { it.completed } }
        )
        val sessionId = dao.insertSession(session)
        dao.insertSessionSets(exercises.flatMapIndexed { exerciseIndex, exercise ->
            exercise.sets.mapIndexed { setIndex, set ->
                WorkoutSessionSetEntity(
                    sessionId = sessionId,
                    exerciseName = exercise.name,
                    exercisePosition = exerciseIndex,
                    setPosition = setIndex,
                    reps = set.reps,
                    weight = set.weight,
                    completed = set.completed
                )
            }
        })
        return session.copy(id = sessionId)
    }
}
