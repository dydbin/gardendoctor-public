import 'package:json_annotation/json_annotation.dart';

part 'user_model.g.dart';

@JsonSerializable()
class RegisterRequest {
  final String email;
  final String password;
  final String nickname;
  final String? fcmToken; // FCM 토큰 필드 추가

  RegisterRequest({
    required this.email,
    required this.password,
    required this.nickname,
    this.fcmToken,
  });

  factory RegisterRequest.fromJson(Map<String, dynamic> json) =>
      _$RegisterRequestFromJson(json);
  Map<String, dynamic> toJson() => _$RegisterRequestToJson(this);
}

@JsonSerializable()
class LoginRequest {
  final String email;
  final String password;
  final String? fcmToken; // FCM 토큰 필드 추가

  LoginRequest({required this.email, required this.password, this.fcmToken});

  factory LoginRequest.fromJson(Map<String, dynamic> json) =>
      _$LoginRequestFromJson(json);
  Map<String, dynamic> toJson() => _$LoginRequestToJson(this);
}

@JsonSerializable()
class JwtToken {
  final String accessToken;
  final String refreshToken;

  JwtToken({required this.accessToken, required this.refreshToken});

  factory JwtToken.fromJson(Map<String, dynamic> json) =>
      _$JwtTokenFromJson(json);
  Map<String, dynamic> toJson() => _$JwtTokenToJson(this);
}

@JsonSerializable()
class User {
  final int userId;
  final String email;
  final String nickname;
  final String? profileImageUrl;
  final String? oauthProvider;
  final String role;
  final String? subscriptionStatus;

  User({
    required this.userId,
    required this.email,
    required this.nickname,
    this.profileImageUrl,
    this.oauthProvider,
    required this.role,
    this.subscriptionStatus,
  });

  factory User.fromJson(Map<String, dynamic> json) => _$UserFromJson(json);
  Map<String, dynamic> toJson() => _$UserToJson(this);
}

@JsonSerializable()
class AuthResponseDto {
  final String? accessToken;
  final String? refreshToken;
  final String message;
  final String? errorCode;
  final dynamic data;

  AuthResponseDto({
    this.accessToken,
    this.refreshToken,
    required this.message,
    this.errorCode,
    this.data,
  });

  factory AuthResponseDto.fromJson(Map<String, dynamic> json) =>
      _$AuthResponseDtoFromJson(json);
  Map<String, dynamic> toJson() => _$AuthResponseDtoToJson(this);
}
