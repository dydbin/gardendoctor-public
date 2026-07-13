// lib/services/api_service.dart
import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:retrofit/retrofit.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../config/app_config.dart';
import '../models/user_model.dart';

part 'api_service.g.dart';

@RestApi()
abstract class ApiService {
  factory ApiService(Dio dio, {String? baseUrl}) {
    dio.options
      ..connectTimeout = const Duration(seconds: 10)
      ..receiveTimeout = const Duration(seconds: 10)
      ..baseUrl = baseUrl ?? AppConfig.apiBaseUrl;

    // 인증 제외 경로는 스웨거에 맞춰 /auth/* 로만
    const noAuthPaths = <String>{
      '/auth/register',
      '/auth/login',
      '/auth/token/refresh',
    };

    dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          if (!noAuthPaths.contains(options.path)) {
            final prefs = await SharedPreferences.getInstance();
            final accessToken = prefs.getString('accessToken');
            if (accessToken != null && accessToken.isNotEmpty) {
              options.headers['Authorization'] = 'Bearer $accessToken';
            }
          }
          handler.next(options);
        },
        onError: (error, handler) async {
          // 리프레시 자체 요청이 아닐 때만 401 처리
          if (error.response?.statusCode == 401 &&
              error.requestOptions.path != '/auth/token/refresh') {
            try {
              final prefs = await SharedPreferences.getInstance();
              final refreshToken = prefs.getString('refreshToken');
              if (refreshToken == null || refreshToken.isEmpty) {
                return handler.next(error);
              }

              final refreshDio = Dio()
                ..options.connectTimeout = const Duration(seconds: 10)
                ..options.receiveTimeout = const Duration(seconds: 10)
                ..options.baseUrl = dio.options.baseUrl;

              final refreshResp = await refreshDio.post(
                '/auth/token/refresh',
                data: {'refreshToken': refreshToken},
              );

              final newAccess = refreshResp.data['accessToken'] as String?;
              final newRefresh = refreshResp.data['refreshToken'] as String?;
              if (newAccess == null || newRefresh == null) {
                return handler.next(error);
              }

              await prefs.setString('accessToken', newAccess);
              await prefs.setString('refreshToken', newRefresh);

              // 원 요청 재시도
              final req = error.requestOptions;
              req.headers['Authorization'] = 'Bearer $newAccess';
              final retryResp = await dio.fetch(req);
              return handler.resolve(retryResp);
            } catch (_) {
              return handler.next(error);
            }
          }
          return handler.next(error);
        },
      ),
    );

    if (kDebugMode) {
      dio.interceptors.add(
        LogInterceptor(
          requestHeader: false,
          requestBody: false,
          responseHeader: false,
          responseBody: false,
        ),
      );
    }
    return _ApiService(dio, baseUrl: dio.options.baseUrl);
  }

  // ===== Auth =====
  @POST('/auth/register')
  Future<void> register(@Body() RegisterRequest request);

  @POST('/auth/login')
  Future<JwtToken> login(@Body() LoginRequest request);

  @POST('/auth/logout')
  Future<AuthResponseDto> logout();

  @POST('/auth/token/refresh')
  Future<JwtToken> refresh(@Body() Map<String, String> body);

  // ✅ 스웨거: PATCH /auth/fcm-token
  @PATCH('/auth/fcm-token')
  Future<void> updateFcmToken(@Body() Map<String, String> body);

  // ===== Me / User =====
  // ✅ 스웨거: GET /auth/user/me
  @GET('/auth/user/me')
  Future<User> getUserMe();

  @PATCH('/auth/me/nickname')
  Future<User> updateNickname(@Body() Map<String, dynamic> body);

  @PATCH('/auth/me/profile-image/upload')
  @MultiPart()
  Future<User> uploadProfileImage(@Part(name: 'image') MultipartFile image);

  @DELETE('/auth/me/profile-image')
  Future<User> deleteProfileImage();

  @DELETE('/auth/me')
  Future<void> deleteAccount();
}
