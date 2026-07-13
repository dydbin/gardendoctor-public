import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_web_auth_2/flutter_web_auth_2.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../config/app_config.dart';
import '../models/user_model.dart';
import '../services/api_service.dart';
import '../services/push_token_provider.dart';
import 'home_screen.dart';
import 'signup_screen.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  // ⭐ 서버 주소
  // 카카오는 http 사설 IP도 가능(콘솔에 동일 도메인 등록 필요)
  // 구글은 보통 HTTPS 공개 도메인(ngrok 등) 필요 → 프로덕션/테스트 시 여기만 https로 교체
  final String _baseUrl = AppConfig.apiBaseUrl;

  final _idController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _isPasswordVisible = false;
  bool _isLoading = false;

  Future<void> _login() async {
    if (_idController.text.trim().isEmpty ||
        _passwordController.text.trim().isEmpty) {
      _showSnackBar('이메일과 비밀번호를 입력해주세요.');
      return;
    }
    setState(() => _isLoading = true);

    try {
      final dio = Dio();
      final api = ApiService(dio);

      // FCM 토큰 발급
      final fcmToken = await getPushToken();

      final request = LoginRequest(
        email: _idController.text.trim(),
        password: _passwordController.text.trim(),
        fcmToken: fcmToken, // 서버에서 무시되어도 무방
      );

      final JwtToken token = await api.login(request);
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('accessToken', token.accessToken);
      await prefs.setString('refreshToken', token.refreshToken);

      // 로그인 성공 직후 FCM 토큰 서버 반영
      if (fcmToken != null && fcmToken.isNotEmpty) {
        await api.updateFcmToken({"fcmToken": fcmToken});
      }

      _showSnackBar('로그인 성공!', isSuccess: true);
      if (!mounted) return;
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(builder: (_) => const HomeScreen()),
      );
    } catch (e) {
      String errorMessage = '로그인 실패';
      if (e is DioException && e.response != null) {
        errorMessage =
            e.response?.data['message']?.toString() ?? '서버 오류가 발생했습니다.';
      }
      _showSnackBar(errorMessage);
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _signInWithGoogle() async => _socialSignIn('google');
  Future<void> _signInWithKakao() async => _socialSignIn('kakao');

  /// ⭐ 소셜 로그인 시작 → 외부 브라우저 → 딥링크(gardendoctor://oauth2redirect)로 복귀
  Future<void> _socialSignIn(String provider) async {
    setState(() => _isLoading = true);
    try {
      // ✅ OAuth 시작 URL: redirect_uri만 전달 (구글 invalid_request 방지)
      final originalUrl = Uri.parse('$_baseUrl/oauth2/authorization/$provider');
      final authUrl = originalUrl
          .replace(
            queryParameters: {'redirect_uri': 'gardendoctor://oauth2redirect'},
          )
          .toString();

      final resultUrl = await FlutterWebAuth2.authenticate(
        url: authUrl,
        // ✅ AndroidManifest / iOS Info.plist 의 URL scheme 와 동일
        callbackUrlScheme: 'gardendoctor',
      );

      // 예: gardendoctor://oauth2redirect#accessToken=...&refreshToken=...
      final uri = Uri.parse(resultUrl);

      // ✅ 프래그먼트(#)에서 토큰 파싱
      final fragment = uri.fragment; // "accessToken=...&refreshToken=..."
      if (fragment.isEmpty) {
        throw Exception('토큰이 포함된 프래그먼트가 비었습니다.');
      }
      final fragParams = Uri.splitQueryString(fragment);

      final accessToken = fragParams['accessToken'];
      final refreshToken = fragParams['refreshToken'];

      if (accessToken == null || refreshToken == null) {
        throw Exception('토큰이 반환되지 않았습니다.');
      }

      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('accessToken', accessToken);
      await prefs.setString('refreshToken', refreshToken);

      // 소셜 로그인 후 FCM 토큰 갱신
      final fcmToken = await getPushToken();
      if (fcmToken != null && fcmToken.isNotEmpty) {
        final dio = Dio();
        final api = ApiService(dio, baseUrl: _baseUrl);
        await api.updateFcmToken({"fcmToken": fcmToken});
      }

      _showSnackBar('$provider 로그인 성공!', isSuccess: true);

      if (!mounted) return;
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(builder: (_) => const HomeScreen()),
      );
    } catch (e) {
      _showSnackBar('$provider 로그인 실패: $e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _showSnackBar(String message, {bool isSuccess = false}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message, style: GoogleFonts.gaegu(color: Colors.white)),
        backgroundColor: isSuccess ? Colors.green : Colors.red,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      ),
    );
  }

  @override
  void dispose() {
    _idController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 60),
              Container(
                padding: const EdgeInsets.all(24),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(24),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.05),
                      blurRadius: 20,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: Column(
                  children: [
                    Container(
                      width: 80,
                      height: 80,
                      decoration: const BoxDecoration(
                        color: Color(0xFF2ECC71),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.eco,
                        color: Colors.white,
                        size: 40,
                      ),
                    ),
                    const SizedBox(height: 16),
                    Text(
                      '텃밭 닥터',
                      style: GoogleFonts.gaegu(
                        fontSize: 32,
                        fontWeight: FontWeight.bold,
                        color: const Color(0xFF2ECC71),
                      ),
                    ),
                    Text(
                      '스마트 농업의 시작',
                      style: GoogleFonts.gaegu(
                        fontSize: 16,
                        color: Colors.grey[600],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 40),
              Container(
                padding: const EdgeInsets.all(24),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(24),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.05),
                      blurRadius: 20,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      '로그인',
                      style: GoogleFonts.gaegu(
                        fontSize: 24,
                        fontWeight: FontWeight.bold,
                        color: const Color(0xFF2C3E50),
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 24),
                    TextFormField(
                      controller: _idController,
                      decoration: InputDecoration(
                        labelText: '이메일',
                        hintText: 'example@email.com',
                        prefixIcon: const Icon(
                          Icons.email_outlined,
                          color: Color(0xFF2ECC71),
                        ),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(16),
                          borderSide: BorderSide.none,
                        ),
                        filled: true,
                        fillColor: const Color(0xFFF8F9FA),
                        labelStyle: GoogleFonts.gaegu(color: Colors.grey[700]),
                        hintStyle: GoogleFonts.gaegu(color: Colors.grey[500]),
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 20,
                          vertical: 16,
                        ),
                      ),
                      style: GoogleFonts.gaegu(fontSize: 16),
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _passwordController,
                      obscureText: !_isPasswordVisible,
                      decoration: InputDecoration(
                        labelText: '비밀번호',
                        hintText: '비밀번호를 입력하세요',
                        prefixIcon: const Icon(
                          Icons.lock_outline,
                          color: Color(0xFF2ECC71),
                        ),
                        suffixIcon: IconButton(
                          icon: Icon(
                            _isPasswordVisible
                                ? Icons.visibility_off
                                : Icons.visibility,
                            color: Colors.grey[600],
                          ),
                          onPressed: () => setState(
                            () => _isPasswordVisible = !_isPasswordVisible,
                          ),
                        ),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(16),
                          borderSide: BorderSide.none,
                        ),
                        filled: true,
                        fillColor: const Color(0xFFF8F9FA),
                        labelStyle: GoogleFonts.gaegu(color: Colors.grey[700]),
                        hintStyle: GoogleFonts.gaegu(color: Colors.grey[500]),
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 20,
                          vertical: 16,
                        ),
                      ),
                      style: GoogleFonts.gaegu(fontSize: 16),
                    ),
                    const SizedBox(height: 24),
                    ElevatedButton(
                      onPressed: _isLoading ? null : _login,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF2ECC71),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(16),
                        ),
                        elevation: 0,
                      ),
                      child: _isLoading
                          ? const SizedBox(
                              height: 20,
                              width: 20,
                              child: CircularProgressIndicator(
                                color: Colors.white,
                                strokeWidth: 2,
                              ),
                            )
                          : Text(
                              '로그인',
                              style: GoogleFonts.gaegu(
                                fontSize: 18,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              Row(
                children: [
                  Expanded(child: Divider(color: Colors.grey[300])),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Text(
                      '또는',
                      style: GoogleFonts.gaegu(
                        color: Colors.grey[600],
                        fontSize: 14,
                      ),
                    ),
                  ),
                  Expanded(child: Divider(color: Colors.grey[300])),
                ],
              ),
              const SizedBox(height: 24),
              Container(
                padding: const EdgeInsets.all(24),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(24),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.05),
                      blurRadius: 20,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: Column(
                  children: [
                    Text(
                      '간편 로그인',
                      style: GoogleFonts.gaegu(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: const Color(0xFF2C3E50),
                      ),
                    ),
                    const SizedBox(height: 20),
                    _SocialLoginButton(
                      onPressed: _isLoading ? null : _signInWithGoogle,
                      icon: Icons.g_mobiledata,
                      text: 'Google로 계속하기',
                      color: const Color(0xFFDB4437),
                      isLoading: _isLoading,
                    ),
                    const SizedBox(height: 12),
                    _SocialLoginButton(
                      onPressed: _isLoading ? null : _signInWithKakao,
                      icon: Icons.chat_bubble,
                      text: 'Kakao로 계속하기',
                      color: const Color(0xFFFFE812),
                      textColor: const Color(0xFF3C1E1E),
                      isLoading: _isLoading,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 32),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    '아직 회원이 아니신가요? ',
                    style: GoogleFonts.gaegu(
                      color: Colors.grey[700],
                      fontSize: 16,
                    ),
                  ),
                  GestureDetector(
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(builder: (_) => const SignupScreen()),
                    ),
                    child: Text(
                      '회원가입',
                      style: GoogleFonts.gaegu(
                        fontWeight: FontWeight.bold,
                        color: const Color(0xFF2ECC71),
                        fontSize: 16,
                        decoration: TextDecoration.underline,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 32),
            ],
          ),
        ),
      ),
    );
  }
}

class _SocialLoginButton extends StatelessWidget {
  final VoidCallback? onPressed;
  final IconData icon;
  final String text;
  final Color color;
  final Color textColor;
  final bool isLoading;

  const _SocialLoginButton({
    required this.onPressed,
    required this.icon,
    required this.text,
    required this.color,
    this.textColor = Colors.white,
    this.isLoading = false,
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: ElevatedButton(
        onPressed: onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: color,
          foregroundColor: textColor,
          padding: const EdgeInsets.symmetric(vertical: 14),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          elevation: 0,
        ),
        child: isLoading
            ? SizedBox(
                height: 20,
                width: 20,
                child: CircularProgressIndicator(
                  color: textColor,
                  strokeWidth: 2,
                ),
              )
            : Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(icon, size: 24),
                  const SizedBox(width: 12),
                  Text(
                    text,
                    style: GoogleFonts.gaegu(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
      ),
    );
  }
}
