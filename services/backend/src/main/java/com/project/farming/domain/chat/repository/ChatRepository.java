package com.project.farming.domain.chat.repository;

import com.project.farming.domain.chat.entity.Chat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    Page<Chat> findByUserIdOrderByChatIdDesc(Long userId, Pageable pageable);
}
