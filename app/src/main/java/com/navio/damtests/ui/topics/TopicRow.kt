package com.navio.damtests.ui.topics

import com.navio.damtests.data.local.entity.Topic
import com.navio.damtests.data.local.entity.TopicProgress

/**
 * A row of the topic list.
 *
 * @property lastPercent Last test score of the topic (0-100), or null if it
 *                       has never been done.
 */
data class TopicRow(
    val topic: Topic,
    val lastPercent: Int?,
    val hasPdf: Boolean
) {
    companion object {
        /** Rows for [topics], with the last score of each and whether it has a PDF. */
        fun build(
            topics: List<Topic>,
            progress: List<TopicProgress>,
            pdfTopicIds: Set<String>
        ): List<TopicRow> {
            val byTopic = progress.associateBy { it.topicId }
            return topics.map { topic ->
                TopicRow(
                    topic       = topic,
                    lastPercent = byTopic[topic.id]?.let(::percentOf),
                    hasPdf      = topic.id in pdfTopicIds
                )
            }
        }

        private fun percentOf(progress: TopicProgress): Int? =
            progress.totalQuestions.takeIf { it > 0 }?.let { progress.lastScore * 100 / it }
    }
}