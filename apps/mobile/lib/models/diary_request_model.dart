import 'package:json_annotation/json_annotation.dart';

part 'diary_request_model.g.dart';

@JsonSerializable()
class DiaryRequest {
  final List<int> selectedUserPlantIds;
  final String title;
  final String content;
  final String diaryDate; // <-- 반드시 추가! (YYYY-MM-DD)
  final bool watered;
  final bool fertilized;
  final bool pruned;
  final bool? deleteExistingImage;

  DiaryRequest({
    required this.selectedUserPlantIds,
    required this.title,
    required this.content,
    required this.diaryDate, // <-- 필수!
    required this.watered,
    required this.fertilized,
    required this.pruned,
    this.deleteExistingImage,
  });

  factory DiaryRequest.fromJson(Map<String, dynamic> json) =>
      _$DiaryRequestFromJson(json);
  Map<String, dynamic> toJson() => _$DiaryRequestToJson(this);
}
