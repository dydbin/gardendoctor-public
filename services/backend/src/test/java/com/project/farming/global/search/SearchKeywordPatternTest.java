package com.project.farming.global.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchKeywordPatternTest {

    @Test
    void prefixShouldCreateIndexFriendlyLikePattern() {
        assertThat(SearchKeywordPattern.prefix(" tomato "))
                .isEqualTo("tomato%");
    }

    @Test
    void containsShouldKeepMiddleMatchPatternForExplicitFullTextLikeSearches() {
        assertThat(SearchKeywordPattern.contains("tomato"))
                .isEqualTo("%tomato%");
    }

    @Test
    void likeWildcardsShouldBeEscaped() {
        assertThat(SearchKeywordPattern.prefix("50%_!"))
                .isEqualTo("50!%!_!!%");
        assertThat(SearchKeywordPattern.contains("50%_!"))
                .isEqualTo("%50!%!_!!%");
    }

    @Test
    void blankKeywordShouldBeRejected() {
        assertThatThrownBy(() -> SearchKeywordPattern.prefix(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("검색어는 비어 있을 수 없습니다.");
    }
}
