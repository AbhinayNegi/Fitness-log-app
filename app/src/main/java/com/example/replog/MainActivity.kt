package com.example.replog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.replog.data.ExerciseDraft
import com.example.replog.data.SetDraft
import com.example.replog.data.WorkoutSessionEntity
import com.example.replog.data.WorkoutWithExercises
import com.example.replog.ui.WorkoutViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class AppScreen { HOME, CREATE, DETAILS, ACTIVE }

class MainActivity : ComponentActivity() {
    private val workoutViewModel: WorkoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RepLogApp(workoutViewModel) }
    }
}

private class SetFormState(
    repsValue: String = "10",
    weightValue: String = "0",
    completedValue: Boolean = false
) {
    var reps by mutableStateOf(repsValue)
    var weight by mutableStateOf(weightValue)
    var completed by mutableStateOf(completedValue)
}

private class ExerciseFormState(val name: String, initialSets: List<SetFormState> = listOf(SetFormState())) {
    val id: String = UUID.randomUUID().toString()
    val sets = mutableStateListOf<SetFormState>().apply { addAll(initialSets) }
}

private class ActiveWorkoutState(
    val name: String,
    val startedAt: Long,
    val exercises: List<ExerciseFormState>
)

private data class DeleteSetRequest(val exerciseId: String, val setIndex: Int, val exerciseName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepLogApp(model: WorkoutViewModel = viewModel()) {
    val workouts by model.workouts.collectAsState()
    val sessions by model.sessions.collectAsState()
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var selectedWorkoutId by remember { mutableStateOf<Long?>(null) }
    var activeWorkout by remember { mutableStateOf<ActiveWorkoutState?>(null) }
    var completedSession by remember { mutableStateOf<WorkoutSessionEntity?>(null) }
    var showLeaveWorkout by remember { mutableStateOf(false) }
    val selectedWorkout = workouts.firstOrNull { it.workout.id == selectedWorkoutId }

    BackHandler(enabled = screen != AppScreen.HOME) {
        when (screen) {
            AppScreen.CREATE -> screen = AppScreen.HOME
            AppScreen.DETAILS -> screen = AppScreen.HOME
            AppScreen.ACTIVE -> showLeaveWorkout = true
            AppScreen.HOME -> Unit
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (screen) {
                                AppScreen.HOME -> "Rep Log"
                                AppScreen.CREATE -> "Create workout"
                                AppScreen.DETAILS -> selectedWorkout?.workout?.name ?: "Workout"
                                AppScreen.ACTIVE -> activeWorkout?.name ?: "Workout in progress"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        if (screen != AppScreen.HOME) {
                            TextButton(onClick = {
                                if (screen == AppScreen.ACTIVE) showLeaveWorkout = true
                                else screen = AppScreen.HOME
                            }) { Text("Back") }
                        }
                    }
                )
            }
        ) { padding ->
            when (screen) {
                AppScreen.HOME -> WorkoutListScreen(
                    workouts = workouts,
                    sessions = sessions,
                    modifier = Modifier.padding(padding),
                    onCreate = { screen = AppScreen.CREATE },
                    onOpenWorkout = { workout ->
                        selectedWorkoutId = workout.workout.id
                        screen = AppScreen.DETAILS
                    }
                )

                AppScreen.CREATE -> CreateWorkoutScreen(
                    modifier = Modifier.padding(padding),
                    onCancel = { screen = AppScreen.HOME },
                    onSave = { name, exercises ->
                        model.saveWorkout(name, exercises) { screen = AppScreen.HOME }
                    }
                )

                AppScreen.DETAILS -> {
                    if (selectedWorkout != null) {
                        WorkoutDetailScreen(
                            workout = selectedWorkout,
                            modifier = Modifier.padding(padding),
                            onStart = {
                                val runExercises = selectedWorkout.exercises
                                    .sortedBy { it.exercise.position }
                                    .map { exercise ->
                                        ExerciseFormState(
                                            exercise.exercise.name,
                                            exercise.sets.sortedBy { it.position }.map { set ->
                                                SetFormState(set.reps.toString(), formatWeight(set.weight))
                                            }
                                        )
                                    }
                                activeWorkout = ActiveWorkoutState(
                                    name = selectedWorkout.workout.name,
                                    startedAt = System.currentTimeMillis(),
                                    exercises = runExercises
                                )
                                screen = AppScreen.ACTIVE
                            }
                        )
                    }
                }

                AppScreen.ACTIVE -> {
                    val current = activeWorkout
                    if (current != null) {
                        ActiveWorkoutScreen(
                            workout = current,
                            modifier = Modifier.padding(padding),
                            onComplete = { exercises ->
                                model.finishWorkout(current.name, current.startedAt, exercises) { result ->
                                    completedSession = result
                                    activeWorkout = null
                                    screen = AppScreen.HOME
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showLeaveWorkout) {
        AlertDialog(
            onDismissRequest = { showLeaveWorkout = false },
            title = { Text("Leave workout?") },
            text = { Text("Your current session will not be saved if you leave now.") },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveWorkout = false
                    activeWorkout = null
                    screen = AppScreen.DETAILS
                }) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveWorkout = false }) { Text("Keep working out") }
            }
        )
    }

    completedSession?.let { session ->
        WorkoutCompleteDialog(session = session, onDismiss = { completedSession = null })
    }
}

@Composable
private fun WorkoutListScreen(
    workouts: List<WorkoutWithExercises>,
    sessions: List<WorkoutSessionEntity>,
    modifier: Modifier = Modifier,
    onCreate: () -> Unit,
    onOpenWorkout: (WorkoutWithExercises) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Your training, tracked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Choose a workout or build a new one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("＋  Create workout", modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        item { Text("MY WORKOUTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        if (workouts.isEmpty()) {
            item {
                EmptyCard("No workouts yet", "Create a workout for a training day to get started.")
            }
        } else {
            items(workouts, key = { "workout-${it.workout.id}" }) { workout ->
                WorkoutPlanCard(workout, onClick = { onOpenWorkout(workout) })
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("RECENT WORKOUTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (sessions.isEmpty()) {
            item { EmptyCard("No completed workouts", "Finished sessions will appear here.") }
        } else {
            items(sessions, key = { "session-${it.id}" }) { session -> SessionHistoryCard(session) }
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun WorkoutPlanCard(item: WorkoutWithExercises, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(item.workout.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${item.exercises.size} exercises  ·  Tap to open", color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.exercises.sortedBy { it.exercise.position }.take(3).forEach { exercise ->
                Text("${exercise.exercise.name}  ·  ${exercise.sets.size} sets", style = MaterialTheme.typography.bodyMedium)
            }
            if (item.exercises.size > 3) {
                Text("+${item.exercises.size - 3} more", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(session: WorkoutSessionEntity) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(session.workoutName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.completedAt)),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${formatDuration(session.durationMs)}  ·  ${session.completedSetCount} sets  ·  ${session.completedExerciseCount} exercises  ·  ${formatWeight(session.totalVolume)} volume",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun WorkoutDetailScreen(
    workout: WorkoutWithExercises,
    modifier: Modifier = Modifier,
    onStart: () -> Unit
) {
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Workout plan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${workout.exercises.size} exercises",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(workout.exercises.sortedBy { it.exercise.position }, key = { it.exercise.id }) { exercise ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(exercise.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        exercise.sets.sortedBy { it.position }.forEachIndexed { index, set ->
                            Text(
                                "Set ${index + 1}   ·   ${set.reps} reps   ·   ${formatWeight(set.weight)} weight",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Start Workout", modifier = Modifier.padding(vertical = 4.dp)) }
    }
}

@Composable
private fun ActiveWorkoutScreen(
    workout: ActiveWorkoutState,
    modifier: Modifier = Modifier,
    onComplete: (List<ExerciseDraft>) -> Unit
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var pendingDelete by remember { mutableStateOf<DeleteSetRequest?>(null) }

    LaunchedEffect(workout.startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Column(modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("WORKOUT IN PROGRESS", style = MaterialTheme.typography.labelSmall)
                    Text(workout.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(formatDuration(now - workout.startedAt), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(workout.exercises, key = { it.id }) { exercise ->
                ExerciseEditorCard(
                    exercise = exercise,
                    active = true,
                    onAddSet = {
                        val previous = exercise.sets.lastOrNull()
                        exercise.sets.add(SetFormState(previous?.reps ?: "10", previous?.weight ?: "0"))
                    },
                    onRemoveSet = { setIndex ->
                        pendingDelete = DeleteSetRequest(exercise.id, setIndex, exercise.name)
                    },
                    onCompleteSet = { setIndex, completed -> exercise.sets.getOrNull(setIndex)?.completed = completed }
                )
            }
        }

        HorizontalDivider()
        Button(
            onClick = { onComplete(workout.exercises.toDrafts()) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Complete Workout", modifier = Modifier.padding(vertical = 4.dp)) }
    }

    pendingDelete?.let { request ->
        RemoveSetDialog(request = request, onDismiss = { pendingDelete = null }, onRemove = {
            workout.exercises.firstOrNull { it.id == request.exerciseId }?.sets?.let { sets ->
                if (request.setIndex in sets.indices) sets.removeAt(request.setIndex)
            }
            pendingDelete = null
        })
    }
}

@Composable
private fun WorkoutCompleteDialog(session: WorkoutSessionEntity, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Workout complete!") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(session.workoutName, fontWeight = FontWeight.SemiBold)
                HorizontalDivider()
                SummaryLine("Time", formatDuration(session.durationMs))
                SummaryLine("Total volume", "${formatWeight(session.totalVolume)} (reps × weight)")
                SummaryLine("Sets completed", session.completedSetCount.toString())
                SummaryLine("Exercises completed", session.completedExerciseCount.toString())
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CreateWorkoutScreen(
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onSave: (String, List<ExerciseDraft>) -> Unit
) {
    var workoutName by remember { mutableStateOf("") }
    val exercises = remember { mutableStateListOf<ExerciseFormState>() }
    var showAddExercise by remember { mutableStateOf(false) }
    var newExerciseName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<DeleteSetRequest?>(null) }

    val canSave = workoutName.isNotBlank() && exercises.isNotEmpty() && exercises.all { exercise ->
        exercise.sets.isNotEmpty() && exercise.sets.all { set ->
            (set.reps.toIntOrNull() ?: 0) > 0 && parseWeight(set.weight) != null
        }
    }

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Name this workout", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = workoutName,
                    onValueChange = { workoutName = it },
                    label = { Text("Workout name") },
                    placeholder = { Text("e.g. Pull day") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text("EXERCISES", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }

            items(exercises, key = { it.id }) { exercise ->
                ExerciseEditorCard(
                    exercise = exercise,
                    onAddSet = {
                        val previous = exercise.sets.lastOrNull()
                        exercise.sets.add(SetFormState(previous?.reps ?: "10", previous?.weight ?: "0"))
                    },
                    onRemoveSet = { setIndex -> pendingDelete = DeleteSetRequest(exercise.id, setIndex, exercise.name) }
                )
            }

            if (exercises.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            "Add an exercise to start building this workout.",
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { onSave(workoutName.trim(), exercises.toDrafts()) },
                enabled = canSave,
                modifier = Modifier.weight(1f)
            ) { Text("Save workout") }
        }
    }

    if (showAddExercise) {
        AlertDialog(
            onDismissRequest = { showAddExercise = false; newExerciseName = "" },
            title = { Text("Add exercise") },
            text = {
                OutlinedTextField(
                    value = newExerciseName,
                    onValueChange = { newExerciseName = it },
                    label = { Text("Exercise name") },
                    placeholder = { Text("e.g. Incline dumbbell press") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        exercises.add(ExerciseFormState(newExerciseName.trim()))
                        newExerciseName = ""
                        showAddExercise = false
                    },
                    enabled = newExerciseName.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddExercise = false; newExerciseName = "" }) { Text("Cancel") }
            }
        )
    }

    pendingDelete?.let { request ->
        RemoveSetDialog(request = request, onDismiss = { pendingDelete = null }, onRemove = {
            exercises.firstOrNull { it.id == request.exerciseId }?.sets?.let { sets ->
                if (request.setIndex in sets.indices) sets.removeAt(request.setIndex)
            }
            pendingDelete = null
        })
    }
}

@Composable
private fun RemoveSetDialog(request: DeleteSetRequest, onDismiss: () -> Unit, onRemove: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove set?") },
        text = { Text("Are you sure you want to remove Set ${request.setIndex + 1} from ${request.exerciseName}?") },
        confirmButton = {
            TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep set") } }
    )
}

@Composable
private fun ExerciseEditorCard(
    exercise: ExerciseFormState,
    active: Boolean = false,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onCompleteSet: (Int, Boolean) -> Unit = { _, _ -> }
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Set", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.labelSmall)
                Text("Reps", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                Text("Weight", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                if (active) Text("Done", modifier = Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall)
                else Spacer(Modifier.width(40.dp))
                Spacer(Modifier.width(40.dp))
            }
            HorizontalDivider()

            if (exercise.sets.isEmpty()) {
                Text("No sets yet. Add a set to continue.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                exercise.sets.forEachIndexed { index, set ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            (index + 1).toString(),
                            modifier = Modifier.width(30.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Start
                        )
                        CompactNumberField(
                            value = set.reps,
                            placeholder = "10",
                            keyboardType = KeyboardType.Number,
                            onValueChange = { set.reps = it.filter(Char::isDigit).take(3) },
                            modifier = Modifier.weight(1f)
                        )
                        CompactNumberField(
                            value = set.weight,
                            placeholder = "0",
                            keyboardType = KeyboardType.Decimal,
                            onValueChange = { value -> set.weight = cleanWeightInput(value) },
                            modifier = Modifier.weight(1f)
                        )
                        if (active) {
                            Checkbox(
                                checked = set.completed,
                                onCheckedChange = { onCompleteSet(index, it) },
                                modifier = Modifier.width(40.dp).padding(0.dp)
                            )
                        } else {
                            Spacer(Modifier.width(40.dp))
                        }
                        TextButton(
                            onClick = { onRemoveSet(index) },
                            modifier = Modifier.width(40.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("×", fontSize = 20.sp, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }

            OutlinedButton(onClick = onAddSet, modifier = Modifier.fillMaxWidth()) { Text("Add set") }
        }
    }
}

@Composable
private fun CompactNumberField(
    value: String,
    placeholder: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 11.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = TextStyle(color = textColor, fontSize = 14.sp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = hintColor, fontSize = 14.sp)
                innerTextField()
            }
        }
    )
}

private fun List<ExerciseFormState>.toDrafts(): List<ExerciseDraft> = map { exercise ->
    ExerciseDraft(
        name = exercise.name,
        sets = exercise.sets.mapNotNull { set ->
            val reps = set.reps.toIntOrNull()
            val weight = parseWeight(set.weight)
            if (reps != null && reps > 0 && weight != null) SetDraft(reps, weight, set.completed) else null
        }
    )
}

private fun cleanWeightInput(value: String): String {
    val filtered = value.filter { it.isDigit() || it == '.' || it == ',' }
    val separator = filtered.indexOfFirst { it == '.' || it == ',' }
    return if (separator < 0) filtered.take(6)
    else filtered.take(separator + 1) + filtered.drop(separator + 1).filter(Char::isDigit).take(2)
}

private fun parseWeight(value: String): Double? = value.trim()
    .replace(',', '.')
    .toDoubleOrNull()
    ?.takeIf { it.isFinite() && it >= 0.0 }

private fun formatWeight(weight: Double): String = BigDecimal.valueOf(weight)
    .setScale(2, RoundingMode.HALF_UP)
    .stripTrailingZeros()
    .toPlainString()

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}
