package com.navio.damtests.data.sync

import com.navio.damtests.data.local.entity.QuestionStats
import com.navio.damtests.data.local.entity.TestAttempt

/**
 * Pure merge logic for syncing the statistics history (test attempts and
 * per-question stats) between Room and Firestore. No Android, no Firebase —
 * fully unit-testable.
 *
 * Each merge returns a [Plan]: what must be written locally (it only exists
 * in the cloud, or the cloud copy is newer) and what must be uploaded (it only
 * exists locally, or the local copy is newer).
 */
object StatsMerger {

    data class Plan<T>(val toLocal: List<T>, val toCloud: List<T>)

    /**
     * Identity of an attempt across devices. Room ids are local auto-increments,
     * so two devices can't share them; subject + topic + timestamp (ms) can.
     * Also used as the Firestore document id.
     */
    fun attemptKey(attempt: TestAttempt): String =
        "${attempt.subjectId}_${attempt.topicId}_${attempt.timestamp}"

    /**
     * Attempts are immutable, so merging is a set union by [attemptKey].
     *
     * @param uploadLocalOnly true only when [cloud] is the user's COMPLETE cloud
     *   history (first sync). With a partial (incremental) download, a local
     *   attempt missing from [cloud] may already be in the cloud.
     */
    fun mergeAttempts(
        local: List<TestAttempt>,
        cloud: List<TestAttempt>,
        uploadLocalOnly: Boolean
    ): Plan<TestAttempt> {
        val localKeys = local.map(::attemptKey).toSet()
        val cloudKeys = cloud.map(::attemptKey).toSet()

        val toLocal = cloud
            .filter { attemptKey(it) !in localKeys }
            .distinctBy(::attemptKey)
            .map { it.copy(id = 0) } // let Room assign its own id

        val toCloud = if (uploadLocalOnly) {
            local.filter { attemptKey(it) !in cloudKeys }
        } else emptyList()

        return Plan(toLocal, toCloud)
    }

    /**
     * Question stats are cumulative counters per question. For each stableId
     * the copy with the most recent lastSeenTimestamp wins (same rule as
     * topic progress).
     *
     * @param uploadLocalNewer true only when [cloud] is complete (first sync);
     *   see [mergeAttempts].
     */
    fun mergeQuestionStats(
        local: List<QuestionStats>,
        cloud: List<QuestionStats>,
        uploadLocalNewer: Boolean
    ): Plan<QuestionStats> {
        val localById = local.associateBy { it.stableId }
        val cloudById = cloud.associateBy { it.stableId }

        val toLocal = cloudById.values.filter { c ->
            val l = localById[c.stableId]
            l == null || c.lastSeenTimestamp > l.lastSeenTimestamp
        }

        val toCloud = if (uploadLocalNewer) {
            local.filter { l ->
                val c = cloudById[l.stableId]
                c == null || l.lastSeenTimestamp > c.lastSeenTimestamp
            }
        } else emptyList()

        return Plan(toLocal, toCloud)
    }
}