package ua.labrunner.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.labrunner.contracts.AssignmentDto
import ua.labrunner.contracts.SubmissionDto
import ua.labrunner.contracts.SubmissionStatus

// Code and URLs must be typed exactly: no autocorrection or automatic capital letters.
private val CodeKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Ascii,
)

private val LabRunnerColors = lightColorScheme(
    primary = Color(0xFF1168BD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F8),
    secondary = Color(0xFF08427B),
    surface = Color(0xFFFFFFFF),
    background = Color(0xFFF4F6FA),
    surfaceVariant = Color(0xFFEFF2F7),
)

@Composable
fun App() {
    MaterialTheme(colorScheme = LabRunnerColors) {
        val viewModel = viewModel { LabRunnerViewModel(LabRunnerApi(apiBaseUrl)) }
        val state by viewModel.state.collectAsState()
        LabRunnerScreen(state, viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabRunnerScreen(state: UiState, vm: LabRunnerViewModel) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("LabRunner", fontWeight = FontWeight.Bold)
                        Text("Автоматична перевірка завдань", style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = { TextButton(onClick = vm::refresh) { Text("Оновити", color = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { item { Banner(it, isError = true) } }
            state.message?.let { item { Banner(it, isError = false) } }

            item {
                OutlinedTextField(
                    value = state.studentName,
                    onValueChange = vm::onStudentNameChange,
                    label = { Text("Студент") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item { SectionTitle("Завдання") }
            if (state.isLoading && state.assignments.isEmpty()) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
            items(state.assignments, key = { "a-${it.id}" }) { assignment ->
                AssignmentCard(assignment, selected = assignment.id == state.selectedAssignmentId) {
                    vm.onAssignmentSelected(assignment.id)
                }
            }

            item { SectionTitle("Здача рішення") }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.repoUrl,
                            onValueChange = vm::onRepoUrlChange,
                            label = { Text("Git-репозиторій (https://github.com/...)") },
                            singleLine = true,
                            keyboardOptions = CodeKeyboard.copy(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.sourceCode,
                            onValueChange = vm::onSourceCodeChange,
                            label = { Text("або код рішення") },
                            minLines = 4,
                            keyboardOptions = CodeKeyboard,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = vm::submit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
                            if (state.isSubmitting) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Здати на перевірку")
                        }
                    }
                }
            }

            item { SectionTitle("Мої здачі (${state.submissions.size})") }
            if (state.submissions.isEmpty()) {
                item { Text("Ще немає здач", color = Color.Gray) }
            }
            items(state.submissions, key = { "s-${it.id}" }) { SubmissionCard(it) }

            item {
                Text(
                    "$platformName · API $apiBaseUrl",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Banner(text: String, isError: Boolean) {
    Surface(
        color = if (isError) Color(0xFFFDECEA) else Color(0xFFE7F5EC),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            color = if (isError) Color(0xFF9B1C1C) else Color(0xFF1E6B3A),
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AssignmentCard(assignment: AssignmentDto, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f)) {
                Text(assignment.title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(assignment.language.name.lowercase(), MaterialTheme.colorScheme.secondary)
                    Tag("макс. ${assignment.maxScore} б.", Color(0xFF5B6B7F))
                    assignment.deadline?.let { Tag("до ${it.take(10)}", Color(0xFF8A5A00)) }
                }
                Spacer(Modifier.height(6.dp))
                Text(assignment.description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF444444))
            }
        }
    }
}

@Composable
private fun SubmissionCard(submission: SubmissionDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(submission.assignmentTitle, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                StatusBadge(submission.status)
            }
            val score = submission.score?.let { "$it / ${submission.maxScore}" } ?: "— / ${submission.maxScore}"
            Text("Бал: $score", style = MaterialTheme.typography.bodyMedium)
            submission.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF555555)) }
            Text(
                "№ ${submission.id.take(8)} · ${submission.createdAt.replace('T', ' ').take(19)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: SubmissionStatus) {
    val (label, color) = when (status) {
        SubmissionStatus.QUEUED -> "У черзі" to Color(0xFF6B7280)
        SubmissionStatus.RUNNING -> "Перевіряється" to Color(0xFF1168BD)
        SubmissionStatus.PASSED -> "Зараховано" to Color(0xFF1E8E3E)
        SubmissionStatus.FAILED -> "Є помилки" to Color(0xFFC26A00)
        SubmissionStatus.ERROR -> "Помилка" to Color(0xFFC5221F)
    }
    Tag(label, color)
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
