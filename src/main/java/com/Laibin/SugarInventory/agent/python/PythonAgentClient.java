package com.Laibin.SugarInventory.agent.python;

import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatRequestDTO;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatResponseDTO;

public interface PythonAgentClient {
    boolean isHealthy();

    PythonAgentChatResponseDTO chat(PythonAgentChatRequestDTO request);
}