package com.project.farming.global.search;

public final class SearchKeywordPattern {

    private static final char LIKE_ESCAPE_CHARACTER = '!';

    private SearchKeywordPattern() {
    }

    public static String contains(String keyword) {
        String normalizedKeyword = normalize(keyword);
        return "%" + escapeLike(normalizedKeyword) + "%";
    }

    public static String prefix(String keyword) {
        String normalizedKeyword = normalize(keyword);
        return escapeLike(normalizedKeyword) + "%";
    }

    private static String normalize(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("검색어는 비어 있을 수 없습니다.");
        }
        return keyword.trim();
    }

    private static String escapeLike(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == LIKE_ESCAPE_CHARACTER || current == '%' || current == '_') {
                escaped.append(LIKE_ESCAPE_CHARACTER);
            }
            escaped.append(current);
        }
        return escaped.toString();
    }
}
