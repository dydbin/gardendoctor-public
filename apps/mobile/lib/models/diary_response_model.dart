// lib/models/diary_response_model.dart

import 'package:json_annotation/json_annotation.dart';

part 'diary_response_model.g.dart';

@JsonSerializable()
class DiaryResponse {
  final int diaryId;
  final int userId;
  final String title;
  final String? content;
  final String? diaryDate; // ⭐️ 추가: 백엔드 날짜 문자열("2024-08-04" 등) 응답 받음
  final String? imageUrl;
  final bool watered;
  final bool pruned;
  final bool fertilized;
  final String createdAt; // DateTime으로 파싱 필요
  final String? updatedAt;
  final List<int> connectedUserPlantIds;

  DiaryResponse({
    required this.diaryId,
    required this.userId,
    required this.title,
    this.content,
    this.diaryDate, // ⭐️ 생성자에도 추가
    this.imageUrl,
    required this.watered,
    required this.pruned,
    required this.fertilized,
    required this.createdAt,
    this.updatedAt,
    required this.connectedUserPlantIds,
  });

  factory DiaryResponse.fromJson(Map<String, dynamic> json) =>
      _$DiaryResponseFromJson(json);

  Map<String, dynamic> toJson() => _$DiaryResponseToJson(this);
}
