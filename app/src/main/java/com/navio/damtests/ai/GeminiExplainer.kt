package com.navio.damtests.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.navio.damtests.core.RemoteConfigManager

/**
 * Gemini client for the review explanations. The model name is read from
 * Remote Config ([RemoteConfigManager.KEY_GEMINI_MODEL]), so it can be
 * changed without a release when a model is retired or overloaded.
 *
 * The API key is injected at construction time and sourced from
 * [com.navio.damtests.core.RemoteConfigManager] — never hardcoded or stored in source control.
 *
 * Errors are not handled here: [ExplanationService] decides whether to retry
 * or fall back to another provider.
 *
 * @param apiKey  Gemini API key retrieved from Firebase Remote Config.
 */
class GeminiExplainer(apiKey: String) {

    private val generativeModel = GenerativeModel(
        modelName = RemoteConfigManager.getStringOr(
            RemoteConfigManager.KEY_GEMINI_MODEL,
            RemoteConfigManager.DEFAULT_GEMINI_MODEL
        ),
        apiKey = apiKey
    )

    /** Text generated for [prompt]; throws if the call fails or the answer is empty. */
    suspend fun generate(prompt: String): String {
        val text = generativeModel.generateContent(prompt).text
        check(!text.isNullOrBlank()) { "Gemini returned an empty answer" }
        return text.trim()
    }
}