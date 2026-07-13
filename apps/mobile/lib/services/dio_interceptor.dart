// lib/services/dio_interceptor.dart

import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';

class DioInterceptor extends Interceptor {
  @override
  void onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    // SharedPreferences에서 저장된 액세스 토큰을 가져옵니다.
    final prefs = await SharedPreferences.getInstance();
    final accessToken = prefs.getString('accessToken');

    // 토큰이 존재하면 헤더에 'Authorization'으로 추가합니다.
    if (accessToken != null) {
      options.headers['Authorization'] = 'Bearer $accessToken';
    }

    // 요청을 계속 진행시킵니다.
    return super.onRequest(options, handler);
  }
}
