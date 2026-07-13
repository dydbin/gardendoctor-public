// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'user_plant_model.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

UserPlantResponse _$UserPlantResponseFromJson(Map<String, dynamic> json) =>
    UserPlantResponse(
      userPlantId: (json['userPlantId'] as num?)?.toInt(),
      plantName: json['plantName'] as String?,
      plantNickname: json['plantNickname'] as String?,
      plantingPlace: json['plantingPlace'] as String?,
      plantedDate: json['plantedDate'] as String?,
      notes: json['notes'] as String?,
      userPlantImageUrl: json['userPlantImageUrl'] as String?,
      plantEnglishName: json['plantEnglishName'] as String?,
      species: json['species'] as String?,
      season: json['season'] as String?,
      plantImageUrl: json['plantImageUrl'] as String?,
      gardenUniqueId: (json['gardenUniqueId'] as num?)?.toInt(),
      isNotificationEnabled: json['isNotificationEnabled'] as bool?,
      waterIntervalDays: (json['waterIntervalDays'] as num?)?.toInt(),
      pruneIntervalDays: (json['pruneIntervalDays'] as num?)?.toInt(),
      fertilizeIntervalDays: (json['fertilizeIntervalDays'] as num?)?.toInt(),
      watered: json['watered'] as bool?,
      pruned: json['pruned'] as bool?,
      fertilized: json['fertilized'] as bool?,
    );

Map<String, dynamic> _$UserPlantResponseToJson(UserPlantResponse instance) =>
    <String, dynamic>{
      'userPlantId': instance.userPlantId,
      'plantName': instance.plantName,
      'plantNickname': instance.plantNickname,
      'plantingPlace': instance.plantingPlace,
      'plantedDate': instance.plantedDate,
      'notes': instance.notes,
      'userPlantImageUrl': instance.userPlantImageUrl,
      'plantEnglishName': instance.plantEnglishName,
      'species': instance.species,
      'season': instance.season,
      'plantImageUrl': instance.plantImageUrl,
      'gardenUniqueId': instance.gardenUniqueId,
      'isNotificationEnabled': instance.isNotificationEnabled,
      'waterIntervalDays': instance.waterIntervalDays,
      'pruneIntervalDays': instance.pruneIntervalDays,
      'fertilizeIntervalDays': instance.fertilizeIntervalDays,
      'watered': instance.watered,
      'pruned': instance.pruned,
      'fertilized': instance.fertilized,
    };
