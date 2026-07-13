import 'package:firebase_core/firebase_core.dart';

import 'app_config.dart';

class FirebaseRuntimeConfig {
  const FirebaseRuntimeConfig._();

  static FirebaseOptions? get current {
    if (!AppConfig.firebaseConfigured) {
      return null;
    }

    return const FirebaseOptions(
      apiKey: AppConfig.firebaseApiKey,
      appId: AppConfig.firebaseAppId,
      messagingSenderId: AppConfig.firebaseMessagingSenderId,
      projectId: AppConfig.firebaseProjectId,
      storageBucket: AppConfig.firebaseStorageBucket,
    );
  }
}
