package com.project.farming.global.response;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void pageShouldExposeTotalsAndNextPageState() {
        PageResponse<String> response = PageResponse.from(
                new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5));

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void sliceShouldAvoidInventingUnknownTotals() {
        PageResponse<String> response = PageResponse.from(
                new SliceImpl<>(List.of("a"), PageRequest.of(0, 1), false));

        assertThat(response.hasNext()).isFalse();
        assertThat(response.totalElements()).isNull();
        assertThat(response.totalPages()).isNull();
    }
}
