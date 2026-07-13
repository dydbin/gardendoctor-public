package com.project.farming.global.response;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorPageResponseTest {

    @Test
    void responseShouldDefensivelyCopyContentAndExposeOnlyNextCursorMetadata() {
        List<String> source = new ArrayList<>(List.of("a", "b"));

        CursorPageResponse<String> response = new CursorPageResponse<>(source, 2, true, "next-token");
        source.clear();

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo("next-token");
        assertThatThrownBy(() -> response.content().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void finalPageShouldNotExposeAContinuationCursor() {
        CursorPageResponse<String> response = new CursorPageResponse<>(List.of("a"), 20, false, null);

        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }
}
