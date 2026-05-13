package com.ledger.app.runtime

import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine

data class LlmModelInstance(val engine: Engine, var conversation: Conversation)
