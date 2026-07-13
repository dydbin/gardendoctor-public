import 'package:dio/dio.dart';
import 'package:retrofit/retrofit.dart';
import '../config/app_config.dart';
import '../models/notification_model.dart';
import '../models/page_response_model.dart'; // 방금 만든 페이지 모델 import

part 'notification_api_service.g.dart';

// baseUrl을 공통 경로까지만 설정합니다.
@RestApi()
abstract class NotificationApiService {
  factory NotificationApiService(Dio dio, {String? baseUrl}) {
    dio.options.baseUrl = baseUrl ?? AppConfig.apiBaseUrlWithApi;
    return _NotificationApiService(dio, baseUrl: dio.options.baseUrl);
  }

  // 1. 경로를 완전하게 수정
  // 2. 반환 타입을 PageResponse<NotificationResponse> 로 수정
  @GET("/notifications")
  Future<HttpResponse<PageResponse<NotificationResponse>>> getNotifications(
    @Query("page") int page,
    @Query("size") int size,
    @Query("sort") String sort,
  );

  @GET("/notifications/unread/count")
  Future<HttpResponse<int>> getUnreadCount();

  @PATCH("/notifications/{id}/read")
  Future<HttpResponse<NotificationResponse>> markAsRead(@Path("id") int id);

  @DELETE("/notifications/{id}")
  Future<HttpResponse<void>> deleteNotification(@Path("id") int id);

  @DELETE("/notifications/all")
  Future<HttpResponse<void>> deleteAll();
}
