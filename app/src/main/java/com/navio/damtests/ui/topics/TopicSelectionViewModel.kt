package com.navio.damtests.ui.topics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navio.damtests.data.local.entity.Topic
import com.navio.damtests.data.pdf.PdfReleaseRepository
import com.navio.damtests.data.quiz.QuizRepository
import com.navio.damtests.data.topics.PracticeOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State of a subject's screen: the practice modes on top (with live counts of
 * failed and favourite questions) and the list of topics with their scores.
 */
@HiltViewModel
class TopicSelectionViewModel @Inject constructor(
    private val repository: QuizRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val practice: PracticeOptions = PracticeOptions.NONE,
        val failedCount: Int = 0,
        val favoriteCount: Int = 0,
        val topics: List<TopicRow> = emptyList()
    )

    /** What loads once per screen: topics, PDFs and available modes. */
    private data class Content(
        val subjectId: String,
        val topics: List<Topic>,
        val pdfTopicIds: Set<String>,
        val practice: PracticeOptions
    )

    private val content = MutableStateFlow<Content?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState> = content
        .flatMapLatest { loaded ->
            if (loaded == null) return@flatMapLatest flowOf(UiState())
            combine(
                repository.getProgressFlow(loaded.subjectId),
                repository.getFailedCountFlow(loaded.subjectId),
                repository.getFavoriteCountFlow(loaded.subjectId)
            ) { progress, failed, favorites ->
                UiState(
                    loading = false,
                    practice = loaded.practice,
                    failedCount = failed,
                    favoriteCount = favorites,
                    topics = TopicRow.build(loaded.topics, progress, loaded.pdfTopicIds)
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState())

    /** Loads the subject once; later calls (rotation) are ignored. */
    fun load(subjectId: String) {
        if (content.value?.subjectId == subjectId) return
        viewModelScope.launch {
            val pdfTopicIds = PdfReleaseRepository.getTopicIdsWithPdf(appContext, subjectId)
            content.value = Content(
                subjectId = subjectId,
                topics = repository.getStudyTopics(subjectId, pdfTopicIds),
                pdfTopicIds = pdfTopicIds,
                practice = repository.getPracticeOptions(subjectId)
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
