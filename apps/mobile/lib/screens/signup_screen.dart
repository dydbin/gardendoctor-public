import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:dio/dio.dart';
import '../models/user_model.dart';
import '../services/api_service.dart';
import '../services/push_token_provider.dart';

class SignupScreen extends StatefulWidget {
  const SignupScreen({super.key});

  @override
  State<SignupScreen> createState() => _SignupScreenState();
}

class _SignupScreenState extends State<SignupScreen> {
  final _formKey = GlobalKey<FormState>();

  final _emailController = TextEditingController();
  final _nicknameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _passwordConfirmController = TextEditingController();

  bool _emailChecked = false;

  /// 이메일 중복확인
  Future<void> _checkEmailDuplicate() async {
    if (_emailController.text.trim().isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('이메일을 먼저 입력해주세요.')));
      return;
    }

    // TODO: 실제 API가 있다면 아래 주석처럼 연결하세요.
    // final dio = Dio();
    // final api = ApiService(dio);
    // try {
    //   final available = await api.checkEmailDuplicate(_emailController.text.trim()); // 서버 구현 필요
    //   if (!mounted) return;
    //   if (available) {
    //     ScaffoldMessenger.of(context).showSnackBar(
    //       const SnackBar(content: Text('사용 가능한 이메일입니다.')),
    //     );
    //     setState(() => _emailChecked = true);
    //   } else {
    //     ScaffoldMessenger.of(context).showSnackBar(
    //       const SnackBar(content: Text('이미 사용 중인 이메일입니다.')),
    //     );
    //     setState(() => _emailChecked = false);
    //   }
    // } catch (e) {
    //   if (!mounted) return;
    //   ScaffoldMessenger.of(context).showSnackBar(
    //     SnackBar(content: Text('중복확인 실패: $e')),
    //   );
    // }

    // 현재는 모의 확인(1초 딜레이 후 성공)
    await Future.delayed(const Duration(seconds: 1));
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('사용 가능한 이메일입니다.')));
    setState(() => _emailChecked = true);
  }

  /// 회원가입 처리
  Future<void> _submitForm() async {
    if (_formKey.currentState!.validate()) {
      if (!_emailChecked) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('이메일 중복확인을 완료해주세요.')));
        return;
      }

      // FCM 토큰 발급
      final fcmToken = await getPushToken();

      final dio = Dio();
      final api = ApiService(dio);

      final request = RegisterRequest(
        email: _emailController.text.trim(),
        password: _passwordController.text.trim(),
        nickname: _nicknameController.text.trim(),
        fcmToken: fcmToken, // 서버 DTO에 없어도 무해
      );

      try {
        await api.register(request);

        // 가입 성공 후 자동 로그인
        final loginRequest = LoginRequest(
          email: _emailController.text.trim(),
          password: _passwordController.text.trim(),
          fcmToken: fcmToken,
        );
        await api.login(loginRequest);

        // 로그인 직후 FCM 토큰 반영
        if (fcmToken != null && fcmToken.isNotEmpty) {
          await api.updateFcmToken({"fcmToken": fcmToken});
        }

        if (!mounted) return;
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('회원가입이 완료되었습니다!')));
        Navigator.pop(context);
      } catch (e) {
        String msg = '회원가입 실패';
        if (e is DioException && e.response != null) {
          msg =
              '서버 오류: 상태 코드 ${e.response?.statusCode ?? '알 수 없음'}\n${e.response?.data ?? ''}';
        } else {
          msg = '회원가입 실패: ${e.toString()}';
        }
        if (!mounted) return;
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(msg)));
      }
    }
  }

  @override
  void dispose() {
    _emailController.dispose();
    _nicknameController.dispose();
    _passwordController.dispose();
    _passwordConfirmController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(
          '회원가입',
          style: GoogleFonts.gaegu(fontWeight: FontWeight.bold),
        ),
        backgroundColor: const Color(0xFF2ECC71),
        foregroundColor: Colors.white,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24.0),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 1) 이메일 (+중복확인)
              _buildTextFieldWithButton(
                controller: _emailController,
                labelText: '이메일',
                hintText: 'example@email.com',
                buttonText: '중복확인',
                onPressed: _checkEmailDuplicate,
                keyboardType: TextInputType.emailAddress,
                validator: (value) {
                  final v = value?.trim() ?? '';
                  if (v.isEmpty) return '이메일을 입력해주세요.';
                  if (!v.contains('@') || !v.contains('.')) {
                    return '유효한 이메일 형식이 아닙니다.';
                  }
                  return null;
                },
                onChanged: (_) => setState(() => _emailChecked = false),
              ),
              const SizedBox(height: 16),

              // 2) 닉네임
              _buildTextField(
                controller: _nicknameController,
                labelText: '닉네임',
                hintText: '2~8자',
                validator: (value) {
                  final v = value?.trim() ?? '';
                  if (v.isEmpty) return '닉네임을 입력해주세요.';
                  if (v.length < 2) return '2자 이상 입력해주세요.';
                  return null;
                },
              ),
              const SizedBox(height: 16),

              // 3) 비밀번호
              _buildTextField(
                controller: _passwordController,
                labelText: '비밀번호',
                hintText: '영문, 숫자, 특수문자 포함 8자 이상',
                obscureText: true,
                validator: (value) {
                  final v = value ?? '';
                  if (v.isEmpty) return '비밀번호를 입력해주세요.';
                  if (v.length < 8) return '8자 이상 입력해주세요.';
                  return null;
                },
              ),
              const SizedBox(height: 16),

              // 4) 비밀번호 확인
              _buildTextField(
                controller: _passwordConfirmController,
                labelText: '비밀번호 확인',
                obscureText: true,
                validator: (value) {
                  if (value != _passwordController.text) {
                    return '비밀번호가 일치하지 않습니다.';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 32),

              ElevatedButton(
                onPressed: _submitForm,
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF2ECC71),
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                child: Text(
                  '가입하기',
                  style: GoogleFonts.gaegu(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ------- 공용 UI 빌더 -------

  Widget _buildTextField({
    required TextEditingController controller,
    required String labelText,
    String? hintText,
    bool obscureText = false,
    TextInputType? keyboardType,
    String? Function(String?)? validator,
    void Function(String)? onChanged,
  }) {
    return TextFormField(
      controller: controller,
      obscureText: obscureText,
      keyboardType: keyboardType,
      decoration: InputDecoration(
        labelText: labelText,
        hintText: hintText,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
        labelStyle: GoogleFonts.gaegu(),
        hintStyle: GoogleFonts.gaegu(),
      ),
      validator: validator,
      style: GoogleFonts.gaegu(),
      onChanged: onChanged,
    );
  }

  Widget _buildTextFieldWithButton({
    required TextEditingController controller,
    required String labelText,
    required String hintText,
    required String buttonText,
    required VoidCallback onPressed,
    TextInputType? keyboardType,
    String? Function(String?)? validator,
    void Function(String)? onChanged,
  }) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: TextFormField(
            controller: controller,
            keyboardType: keyboardType,
            decoration: InputDecoration(
              labelText: labelText,
              hintText: hintText,
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
              ),
              labelStyle: GoogleFonts.gaegu(),
              hintStyle: GoogleFonts.gaegu(),
            ),
            validator: validator,
            style: GoogleFonts.gaegu(),
            onChanged: onChanged,
          ),
        ),
        const SizedBox(width: 8),
        Padding(
          padding: const EdgeInsets.only(top: 4.0),
          child: ElevatedButton(
            onPressed: onPressed,
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
            ),
            child: Text(buttonText, style: GoogleFonts.gaegu()),
          ),
        ),
      ],
    );
  }
}
