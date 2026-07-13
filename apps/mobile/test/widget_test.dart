import 'package:farmbootcamp/config/app_config.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('public build uses a non-routable API default', () {
    expect(Uri.parse(AppConfig.apiBaseUrl).host, 'api.example.invalid');
  });

  test('public build keeps optional providers disabled', () {
    expect(AppConfig.firebaseConfigured, isFalse);
    expect(AppConfig.kakaoNativeAppKey, isEmpty);
    expect(AppConfig.kakaoMapAppKey, isEmpty);
  });
}
