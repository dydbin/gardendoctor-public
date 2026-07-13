package com.project.farming.domain.chat.dto;

import java.util.List;

public record PythonSessionQueryRequest(List<Long> sessionIds) {
    public PythonSessionQueryRequest {
        sessionIds = List.copyOf(sessionIds);
    }
}
