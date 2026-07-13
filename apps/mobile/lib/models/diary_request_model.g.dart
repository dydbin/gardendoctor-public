// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'diary_request_model.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

DiaryRequest _$DiaryRequestFromJson(Map<String, dynamic> json) => DiaryRequest(
  selectedUserPlantIds: (json['selectedUserPlantIds'] as List<dynamic>)
      .map((e) => (e as num).toInt())
      .toList(),
  title: json['title'] as String,
  content: json['content'] as String,
  diaryDate: json['diaryDate'] as String,
  watered: json['watered'] as bool,
  fertilized: json['fertilized'] as bool,
  pruned: json['pruned'] as bool,
  deleteExistingImage: json['deleteExistingImage'] as bool?,
);

Map<String, dynamic> _$DiaryRequestToJson(DiaryRequest instance) =>
    <String, dynamic>{
      'selectedUserPlantIds': instance.selectedUserPlantIds,
      'title': instance.title,
      'content': instance.content,
      'diaryDate': instance.diaryDate,
      'watered': instance.watered,
      'fertilized': instance.fertilized,
      'pruned': instance.pruned,
      'deleteExistingImage': instance.deleteExistingImage,
    };
