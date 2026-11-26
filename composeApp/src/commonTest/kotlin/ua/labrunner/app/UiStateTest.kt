package ua.labrunner.app

import ua.labrunner.contracts.AssignmentDto
import ua.labrunner.contracts.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiStateTest {
    private val assignment = AssignmentDto(1, "Лаб 1", "d", Language.KOTLIN, 10)

    @Test
    fun cannotSubmitWithoutAssignmentOrPayload() {
        assertFalse(UiState(assignments = listOf(assignment)).canSubmit)
        assertFalse(UiState(selectedAssignmentId = 1, repoUrl = "  ", sourceCode = "").canSubmit)
    }

    @Test
    fun canSubmitCodeOrRepositoryForSelectedAssignment() {
        assertTrue(UiState(selectedAssignmentId = 1, sourceCode = "fun main() {}").canSubmit)
        assertTrue(UiState(selectedAssignmentId = 1, repoUrl = "https://github.com/a/b").canSubmit)
    }

    @Test
    fun submitIsDisabledWhileSendingOrWithoutStudentName() {
        assertFalse(UiState(selectedAssignmentId = 1, sourceCode = "x", isSubmitting = true).canSubmit)
        assertFalse(UiState(selectedAssignmentId = 1, sourceCode = "x", studentName = " ").canSubmit)
    }

    @Test
    fun selectedAssignmentIsResolvedById() {
        assertEquals(assignment, UiState(assignments = listOf(assignment), selectedAssignmentId = 1).selectedAssignment)
    }
}
