package com.project.farming.global.pagination;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreatedAtIdCursorCodecTest {

    @Test
    void cursorShouldRoundTripTimestampPrecisionAndId() {
        CreatedAtIdCursor cursor = new CreatedAtIdCursor(
                LocalDateTime.of(2026, 7, 10, 12, 34, 56, 123_456_000),
                42L
        );

        String encoded = CreatedAtIdCursorCodec.encode(cursor);

        assertThat(CreatedAtIdCursorCodec.decode(encoded)).isEqualTo(cursor);
    }

    @Test
    void malformedOrOversizedCursorShouldBeRejected() {
        assertThatThrownBy(() -> CreatedAtIdCursorCodec.decode("not-base64!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 cursor입니다.");
        assertThatThrownBy(() -> CreatedAtIdCursorCodec.decode("a".repeat(257)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않은 cursor입니다.");
    }
}
