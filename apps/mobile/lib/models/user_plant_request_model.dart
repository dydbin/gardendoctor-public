// lib/models/user_plant_request_model.dart
import 'package:json_annotation/json_annotation.dart';

part 'user_plant_request_model.g.dart';

@JsonSerializable()
class UserPlantRequest {
  // 식물 기본
  final String plantName;
  final String plantNickname;

  // 위치
  final int gardenUniqueId;
  final String? plantingPlace;

  // 메타
  final String? plantedDate;
  final String? notes;

  // 🔔 서버 키는 "isNotificationEnabled"
  @JsonKey(name: 'isNotificationEnabled')
  final bool isNotificationEnabled;

  // ⏱ 주기(일)
  final int waterIntervalDays;
  final int pruneIntervalDays;
  final int fertilizeIntervalDays;

  // ✅ 오늘 한 일
  final bool watered;
  final bool pruned;
  final bool fertilized;

  const UserPlantRequest({
    required this.plantName,
    required this.plantNickname,
    required this.gardenUniqueId,
    this.plantingPlace,
    this.plantedDate,
    this.notes,
    required this.isNotificationEnabled,
    required this.waterIntervalDays,
    required this.pruneIntervalDays,
    required this.fertilizeIntervalDays,
    required this.watered,
    required this.pruned,
    required this.fertilized,
  });

  factory UserPlantRequest.fromJson(Map<String, dynamic> json) =>
      _$UserPlantRequestFromJson(json);

  Map<String, dynamic> toJson() => _$UserPlantRequestToJson(this);
}
