// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'notification_model.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

NotificationResponse _$NotificationResponseFromJson(
  Map<String, dynamic> json,
) => NotificationResponse(
  notificationId: (json['notificationId'] as num).toInt(),
  title: json['title'] as String,
  message: json['message'] as String,
  isRead: json['isRead'] as bool,
  createdAt: json['createdAt'] as String,
);

Map<String, dynamic> _$NotificationResponseToJson(
  NotificationResponse instance,
) => <String, dynamic>{
  'notificationId': instance.notificationId,
  'title': instance.title,
  'message': instance.message,
  'isRead': instance.isRead,
  'createdAt': instance.createdAt,
};
