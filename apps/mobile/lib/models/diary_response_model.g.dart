// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'diary_response_model.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

DiaryResponse _$DiaryResponseFromJson(Map<String, dynamic> json) =>
    DiaryResponse(
      diaryId: (json['diaryId'] as num).toInt(),
      userId: (json['userId'] as num).toInt(),
      title: json['title'] as String,
      content: json['content'] as String?,
      diaryDate: json['diaryDate'] as String?,
      imageUrl: json['imageUrl'] as String?,
      watered: json['watered'] as bool,
      pruned: json['pruned'] as bool,
      fertilized: json['fertilized'] as bool,
      createdAt: json['createdAt'] as String,
      updatedAt: json['updatedAt'] as String?,
      connectedUserPlantIds: (json['connectedUserPlantIds'] as List<dynamic>)
          .map((e) => (e as num).toInt())
          .toList(),
    );

Map<String, dynamic> _$DiaryResponseToJson(DiaryResponse instance) =>
    <String, dynamic>{
      'diaryId': instance.diaryId,
      'userId': instance.userId,
      'title': instance.title,
      'content': instance.content,
      'diaryDate': instance.diaryDate,
      'imageUrl': instance.imageUrl,
      'watered': instance.watered,
      'pruned': instance.pruned,
      'fertilized': instance.fertilized,
      'createdAt': instance.createdAt,
      'updatedAt': instance.updatedAt,
      'connectedUserPlantIds': instance.connectedUserPlantIds,
    };
