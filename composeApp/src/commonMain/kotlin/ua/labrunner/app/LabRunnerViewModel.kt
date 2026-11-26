package ua.labrunner.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ua.labrunner.contracts.AssignmentDto
import ua.labrunner.contracts.CreateSubmissionRequest
import ua.labrunner.contracts.SubmissionDto

data class UiState(
    val studentName: String = "Владислав Петрик",
    val assignments: List<AssignmentDto> = emptyList(),
    val selectedAssignmentId: Long? = null,
    val repoUrl: String = "",
    val sourceCode: String = "",
    val submissions: List<SubmissionDto> = emptyList(),
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val selectedAssignment: AssignmentDto? get() = assignments.firstOrNull { it.id == selectedAssignmentId }
    val canSubmit: Boolean
        get() = !isSubmitting && selectedAssignmentId != null && studentName.isNotBlank() &&
            (repoUrl.isNotBlank() || sourceCode.isNotBlank())
}

class LabRunnerViewModel(private val api: LabRunnerApi) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
        // Lab 2: simple polling. Push notifications (FCM / APNs) replace it in a later lab.
        viewModelScope.launch {
            while (isActive) {
                delay(2_000)
                if (_state.value.submissions.any { !it.status.isFinal }) loadSubmissions()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { api.assignments() }
                .onSuccess { list ->
                    _state.update { s ->
                        s.copy(assignments = list, selectedAssignmentId = s.selectedAssignmentId ?: list.firstOrNull()?.id)
                    }
                    loadSubmissions()
                }
                .onFailure { e -> _state.update { it.copy(error = "API недоступний: ${e.message}") } }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun onStudentNameChange(value: String) = _state.update { it.copy(studentName = value) }
    fun onRepoUrlChange(value: String) = _state.update { it.copy(repoUrl = value) }
    fun onSourceCodeChange(value: String) = _state.update { it.copy(sourceCode = value) }
    fun onAssignmentSelected(id: Long) = _state.update { it.copy(selectedAssignmentId = id) }

    fun submit() {
        val s = _state.value
        val assignmentId = s.selectedAssignmentId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null, message = null) }
            runCatching {
                api.submit(
                    CreateSubmissionRequest(
                        assignmentId = assignmentId,
                        studentName = s.studentName.trim(),
                        repoUrl = s.repoUrl.trim().ifEmpty { null },
                        sourceCode = s.sourceCode.ifBlank { null },
                    ),
                )
            }.onSuccess { created ->
                _state.update {
                    it.copy(
                        submissions = listOf(created) + it.submissions,
                        repoUrl = "",
                        sourceCode = "",
                        message = "Здачу прийнято, вона в черзі на перевірку",
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(error = "Не вдалося здати: ${e.message}") }
            }
            _state.update { it.copy(isSubmitting = false) }
        }
    }

    private suspend fun loadSubmissions() {
        val name = _state.value.studentName.trim()
        if (name.isEmpty()) return
        runCatching { api.submissions(name) }
            .onSuccess { list -> _state.update { it.copy(submissions = list, error = null) } }
            .onFailure { e -> _state.update { it.copy(error = "API недоступний: ${e.message}") } }
    }
}
