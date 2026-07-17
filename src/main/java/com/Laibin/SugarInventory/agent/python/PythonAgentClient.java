package com.Laibin.SugarInventory.agent.python;

import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatRequestDTO;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatResponseDTO;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentStreamEventDTO;

import java.util.function.Consumer;

public interface PythonAgentClient {
    boolean isHealthy();

    PythonAgentChatResponseDTO chat(PythonAgentChatRequestDTO request);

    default void stream(PythonAgentChatRequestDTO request, Consumer<PythonAgentStreamEventDTO> eventConsumer) {
        throw new PythonAgentClientException("PYTHON_AGENT_STREAM_UNAVAILABLE", true);
    }

    default void cancel(String agentSessionId, String messageId) {
        throw new PythonAgentClientException("PYTHON_AGENT_CANCEL_UNAVAILABLE", true);
    }

    default void clearSession(String agentSessionId) {
        throw new PythonAgentClientException("PYTHON_AGENT_CLEAR_SESSION_UNAVAILABLE", true);
    }
}
