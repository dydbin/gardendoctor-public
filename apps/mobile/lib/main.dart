import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/date_symbol_data_local.dart'; // 날짜 포맷
import 'package:kakao_flutter_sdk_user/kakao_flutter_sdk_user.dart'; // 카카오 로그인 SDK
import 'package:kakao_map_plugin/kakao_map_plugin.dart'; // 카카오 지도 플러그인
import 'package:firebase_core/firebase_core.dart'; // ⭐ Firebase Core
import 'package:firebase_messaging/firebase_messaging.dart'; // ⭐ Firebase Messaging
import 'package:flutter_local_notifications/flutter_local_notifications.dart'; // ⭐ 로컬 알림 패키지 추가

import 'config/app_config.dart';
import 'config/firebase_runtime_config.dart';
import 'screens/home_screen.dart';

// 전역으로 카메라 리스트를 담을 변수 선언
late List<CameraDescription> cameras;

// 로컬 알림 플러그인 인스턴스 (전역)
final FlutterLocalNotificationsPlugin flutterLocalNotificationsPlugin =
    FlutterLocalNotificationsPlugin();

// 백그라운드 메시지 핸들러 (필수)
@pragma('vm:entry-point')
Future<void> _firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  final firebaseOptions = FirebaseRuntimeConfig.current;
  if (firebaseOptions == null) return;
  if (Firebase.apps.isEmpty) {
    await Firebase.initializeApp(options: firebaseOptions);
  }

  // 백그라운드(종료/잠금)에서 메시지 오면 여기서 로컬 알림 띄우기 (선택)
  RemoteNotification? notification = message.notification;
  AndroidNotification? android = message.notification?.android;
  if (notification != null && android != null) {
    flutterLocalNotificationsPlugin.show(
      notification.hashCode,
      notification.title,
      notification.body,
      NotificationDetails(
        android: AndroidNotificationDetails(
          'high_importance_channel', // channelId
          '중요 알림', // channelName
          channelDescription: '중요 알림을 위한 채널',
          importance: Importance.max,
          priority: Priority.high,
          icon: '@mipmap/ic_launcher',
        ),
      ),
    );
  }

  print("백그라운드에서 알림 수신: ${message.notification?.title}");
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // 날짜 데이터 초기화
  await initializeDateFormatting();

  // 사용 가능한 카메라 목록 가져오기
  cameras = await availableCameras();

  // Kakao 로그인 SDK 초기화
  if (AppConfig.kakaoNativeAppKey.isNotEmpty) {
    KakaoSdk.init(nativeAppKey: AppConfig.kakaoNativeAppKey);
  }

  // 카카오 지도 SDK 초기화
  if (AppConfig.kakaoMapAppKey.isNotEmpty) {
    AuthRepository.initialize(appKey: AppConfig.kakaoMapAppKey);
  }

  final firebaseOptions = FirebaseRuntimeConfig.current;
  if (firebaseOptions != null) {
    await Firebase.initializeApp(options: firebaseOptions);

    // ⭐ flutter_local_notifications 초기화
    const AndroidInitializationSettings initializationSettingsAndroid =
        AndroidInitializationSettings('@mipmap/ic_launcher');
    final InitializationSettings initializationSettings =
        InitializationSettings(android: initializationSettingsAndroid);
    await flutterLocalNotificationsPlugin.initialize(initializationSettings);

    // ⭐ Firebase Messaging 설정
    FirebaseMessaging messaging = FirebaseMessaging.instance;

    // 알림 권한 요청 (iOS용)
    await messaging.requestPermission(alert: true, badge: true, sound: true);

    // FCM 토큰 확인
    await messaging.getToken();

    // 백그라운드 메시지 핸들러 등록
    FirebaseMessaging.onBackgroundMessage(_firebaseMessagingBackgroundHandler);

    // 앱이 켜져 있을 때(포그라운드) 알림 수신 시 로컬 알림 띄우기
    FirebaseMessaging.onMessage.listen((RemoteMessage message) {
      print("포그라운드 알림 수신: ${message.notification?.title}");

      RemoteNotification? notification = message.notification;
      AndroidNotification? android = message.notification?.android;
      if (notification != null && android != null) {
        flutterLocalNotificationsPlugin.show(
          notification.hashCode,
          notification.title,
          notification.body,
          NotificationDetails(
            android: AndroidNotificationDetails(
              'high_importance_channel', // channelId (고유값)
              '중요 알림', // channelName
              channelDescription: '중요 알림을 위한 채널',
              importance: Importance.max,
              priority: Priority.high,
              icon: '@mipmap/ic_launcher',
            ),
          ),
        );
      }
    });
  }

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Farm Bootcamp',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF2ECC71)),
        useMaterial3: true,
        textTheme: GoogleFonts.gaeguTextTheme(),
      ),
      home: const HomeScreen(),
    );
  }
}
