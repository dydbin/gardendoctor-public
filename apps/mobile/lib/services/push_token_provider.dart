import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';

Future<String?> getPushToken() async {
  if (Firebase.apps.isEmpty) {
    return null;
  }
  return FirebaseMessaging.instance.getToken();
}
