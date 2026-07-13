package com.project.farming.domain.chat.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blankQuestionAndNonPositiveChatIdShouldBeRejected() {
        ChatRequest request = new ChatRequest();
        request.setChatId(0L);
        request.setQuery(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("chatId", "query");
    }

    @Test
    void oversizedQuestionShouldBeRejected() {
        ChatRequest request = new ChatRequest();
        request.setQuery("q".repeat(1001));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("query");
    }
}
