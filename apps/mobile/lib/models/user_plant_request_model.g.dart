// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'user_plant_request_model.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

UserPlantRequest _$UserPlantRequestFromJson(Map<String, dynamic> json) =>
    UserPlantRequest(
      plantName: json['plantName'] as String,
      plantNickname: json['plantNickname'] as String,
      gardenUniqueId: (json['gardenUniqueId'] as num).toInt(),
      plantingPlace: json['plantingPlace'] as String?,
      plantedDate: json['plantedDate'] as String?,
      notes: json['notes'] as String?,
      isNotificationEnabled: json['isNotificationEnabled'] as bool,
      waterIntervalDays: (json['waterIntervalDays'] as num).toInt(),
      pruneIntervalDays: (json['pruneIntervalDays'] as num).toInt(),
      fertilizeIntervalDays: (json['fertilizeIntervalDays'] as num).toInt(),
      watered: json['watered'] as bool,
      pruned: json['pruned'] as bool,
      fertilized: json['fertilized'] as bool,
    );

Map<String, dynamic> _$UserPlantRequestToJson(UserPlantRequest instance) =>
    <String, dynamic>{
      'plantName': instance.plantName,
      'plantNickname': instance.plantNickname,
      'gardenUniqueId': instance.gardenUniqueId,
      'plantingPlace': instance.plantingPlace,
      'plantedDate': instance.plantedDate,
      'notes': instance.notes,
      'isNotificationEnabled': instance.isNotificationEnabled,
      'waterIntervalDays': instance.waterIntervalDays,
      'pruneIntervalDays': instance.pruneIntervalDays,
      'fertilizeIntervalDays': instance.fertilizeIntervalDays,
      'watered': instance.watered,
      'pruned': instance.pruned,
      'fertilized': instance.fertilized,
    };
