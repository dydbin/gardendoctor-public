package com.project.farming.domain.notification.command;

import com.project.farming.domain.notification.entity.Notice;

public record NoticeCommand(String title, String content) {

    public NoticeCommand {
        Notice.validateText(title, content);
    }
}
