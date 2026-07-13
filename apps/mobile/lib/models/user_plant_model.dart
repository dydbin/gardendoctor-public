// lib/models/user_plant_model.dart
import 'package:json_annotation/json_annotation.dart';

part 'user_plant_model.g.dart';

@JsonSerializable()
class UserPlantResponse {
  final int? userPlantId;
  final String? plantName;
  final String? plantNickname;
  final String? plantingPlace;
  final String? plantedDate;
  final String? notes;
  final String? userPlantImageUrl;

  final String? plantEnglishName;
  final String? species;
  final String? season;
  final String? plantImageUrl;

  final int? gardenUniqueId;

  // ✅ 목록/상세 모두 커버
  final bool? isNotificationEnabled;
  final int? waterIntervalDays;
  final int? pruneIntervalDays;
  final int? fertilizeIntervalDays;

  final bool? watered;
  final bool? pruned;
  final bool? fertilized;

  UserPlantResponse({
    this.userPlantId,
    this.plantName,
    this.plantNickname,
    this.plantingPlace,
    this.plantedDate,
    this.notes,
    this.userPlantImageUrl,
    this.plantEnglishName,
    this.species,
    this.season,
    this.plantImageUrl,
    this.gardenUniqueId,
    this.isNotificationEnabled,
    this.waterIntervalDays,
    this.pruneIntervalDays,
    this.fertilizeIntervalDays,
    this.watered,
    this.pruned,
    this.fertilized,
  });

  factory UserPlantResponse.fromJson(Map<String, dynamic> json) {
    final normalized = Map<String, dynamic>.from(json);

    // ---- 알림 키 보정: 두 키 중 하나라도 "켜짐"이면 true
    bool? _toBool(dynamic v) {
      if (v == null) return null;
      if (v is bool) return v;
      if (v is num) return v != 0;
      if (v is String) {
        final s = v.trim().toLowerCase();
        if (s == 'true' || s == 'y' || s == 'yes' || s == 'on' || s == '1')
          return true;
        if (s == 'false' || s == 'n' || s == 'no' || s == 'off' || s == '0')
          return false;
      }
      return null;
    }

    final n1 = _toBool(normalized['isNotificationEnabled']);
    final n2 = _toBool(normalized['notificationEnabled']);
    bool? finalN;
    if (n1 == true || n2 == true) {
      finalN = true;
    } else if (n1 == false || n2 == false) {
      finalN = false;
    } else {
      finalN = null;
    }
    normalized['isNotificationEnabled'] = finalN;

    // ---- 주기 값 정규화: "7" -> 7, 0/<=0/null -> null(미표시)
    int? _toInt(dynamic v) {
      if (v == null) return null;
      if (v is int) return v;
      if (v is num) return v.toInt();
      if (v is String) return int.tryParse(v);
      return null;
    }

    int? _normalizeInterval(dynamic v) {
      final i = _toInt(v);
      if (i == null || i <= 0) return null; // 0은 미설정으로 간주
      return i;
    }

    normalized['waterIntervalDays'] = _normalizeInterval(
      normalized['waterIntervalDays'],
    );
    normalized['pruneIntervalDays'] = _normalizeInterval(
      normalized['pruneIntervalDays'],
    );
    normalized['fertilizeIntervalDays'] = _normalizeInterval(
      normalized['fertilizeIntervalDays'],
    );

    return _$UserPlantResponseFromJson(normalized);
  }

  Map<String, dynamic> toJson() => _$UserPlantResponseToJson(this);
}
