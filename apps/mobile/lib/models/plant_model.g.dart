// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'plant_model.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Plant _$PlantFromJson(Map<String, dynamic> json) => Plant(
  plantId: (json['plantId'] as num).toInt(),
  plantName: json['plantName'] as String?,
  plantEnglishName: json['plantEnglishName'] as String?,
  species: json['species'] as String?,
  season: json['season'] as String?,
  imageUrl: json['plantImageUrl'] as String?,
);

Map<String, dynamic> _$PlantToJson(Plant instance) => <String, dynamic>{
  'plantId': instance.plantId,
  'plantName': instance.plantName,
  'plantEnglishName': instance.plantEnglishName,
  'species': instance.species,
  'season': instance.season,
  'plantImageUrl': instance.imageUrl,
};
