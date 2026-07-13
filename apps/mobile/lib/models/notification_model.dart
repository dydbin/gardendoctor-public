import 'package:json_annotation/json_annotation.dart';

part 'notification_model.g.dart';

@JsonSerializable()
class NotificationResponse {
  final int notificationId;
  final String title;
  final String message;
  final bool isRead;
  final String createdAt;

  NotificationResponse({
    required this.notificationId,
    required this.title,
    required this.message,
    required this.isRead,
    required this.createdAt,
  });

  // ✅ 모든 상황에서 안전하게 변환!
  factory NotificationResponse.fromJson(Map<String, dynamic> json) {
    // print(json); // ← 실제 받아오는 값 체크용 (처음엔 이거 넣고 실제 로그 확인!)
    return NotificationResponse(
      notificationId: json['notificationId'] is int
          ? json['notificationId']
          : int.tryParse(json['notificationId'].toString()) ?? 0,
      title: json['title']?.toString() ?? '',
      message: json['message']?.toString() ?? '',
      isRead: json['isRead'] is bool
          ? json['isRead']
          : (json['isRead']?.toString() == 'true'),
      createdAt: json['createdAt']?.toString() ?? '',
    );
  }

  Map<String, dynamic> toJson() => _$NotificationResponseToJson(this);
}
