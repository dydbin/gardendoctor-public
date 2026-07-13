package com.project.farming.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "chat",
        indexes = @Index(name = "idx_chat_user", columnList = "user_id")
)
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // Python FastAPI 서버에서 관리하는 세션의 ID
    @Column(name = "python_session_id", nullable = false)
    private Long pythonSessionId;
}
