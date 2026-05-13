package com.ledger.app.data

enum class Accelerator(val label: String) {
  CPU(label = "CPU"),
  GPU(label = "GPU"),
  NPU(label = "NPU"),
  TPU(label = "TPU"),
}

/** Built-in task IDs. */
object BuiltInTaskId {
  const val LLM_CHAT = "llm_chat"
  const val LLM_PROMPT_LAB = "llm_prompt_lab"
  const val LLM_ASK_AUDIO = "llm_ask_audio"
  const val LLM_ASK_IMAGE = "llm_ask_image"
  const val LLM_MOBILE_ACTIONS = "llm_mobile_actions"
  const val LLM_TINY_GARDEN = "llm_tiny_garden"
  const val LLM_LEDGER = "llm_ledger"
}
