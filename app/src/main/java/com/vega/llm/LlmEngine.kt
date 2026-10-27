package com.vega.llm

interface LlmEngine {
    /**
     * Returns raw model output in Vega's command format (see [CommandParser]).
     * [ctxLine] is the `[ctx …]` header the fine-tuned model is trained with.
     */
    suspend fun generate(ctxLine: String, userText: String): String
}
