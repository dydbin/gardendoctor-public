// com.project.farming.global.image.entity.ImageDomainType.java
package com.project.farming.global.image.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImageDomainType {
    // 각 도메인에 대한 설명을 추가할 수 있습니다.
    USER("사용자 프로필"),
    PLANT("식물 정보"),
    DIARY("일지"),
    FARM("텃밭 정보"),
    USERPLANT("사용자 등록 식물"),
    PHOTO("분석용 사진"),
    // TODO: 다른 도메인이 추가될 때 여기에 정의
    // BOARD("게시글"),
    // COMMENT("댓글"),
    // PRODUCT("상품")
    ;

    private final String description;

    public static ImageDomainType from(String value) {
        try {
            return ImageDomainType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("존재하지 않는 이미지 도메인 타입입니다: " + value);
        }
    }

}