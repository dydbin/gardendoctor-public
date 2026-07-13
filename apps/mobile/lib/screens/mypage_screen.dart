import 'dart:io';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:dio/dio.dart';
import 'package:image_picker/image_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/user_model.dart';
import '../services/api_service.dart';
import 'login_screen.dart';

class MyPageScreen extends StatefulWidget {
  const MyPageScreen({super.key});

  @override
  State<MyPageScreen> createState() => _MyPageScreenState();
}

class _MyPageScreenState extends State<MyPageScreen>
    with TickerProviderStateMixin {
  User? _user;
  bool _isLoading = true;
  String? _errorMessage;
  final ImagePicker _picker = ImagePicker();
  late AnimationController _animationController;
  late Animation<double> _fadeAnimation;

  @override
  void initState() {
    super.initState();
    _animationController = AnimationController(
      duration: const Duration(milliseconds: 800),
      vsync: this,
    );
    _fadeAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _animationController, curve: Curves.easeInOut),
    );
    _fetchUserData();
  }

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }

  Future<void> _fetchUserData() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final dio = Dio();
      final api = ApiService(dio);

      final User fetchedUser = await api.getUserMe();
      if (!mounted) return;
      setState(() {
        _user = fetchedUser;
        _isLoading = false;
      });
      _animationController.forward();
    } on DioException catch (e) {
      final code = e.response?.statusCode;
      final body = e.response?.data;
      final typ = e.type;

      if (!mounted) return;
      setState(() {
        _errorMessage =
            '사용자 정보를 불러오지 못했습니다. '
            '(status: ${code ?? '-'}, type: $typ, body: ${body ?? '-'})';
        _isLoading = false;
      });

      if (code == 401 || code == 403) {
        _showSessionExpiredDialog();
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _errorMessage = '알 수 없는 오류 발생: ${e.toString()}';
        _isLoading = false;
      });
    }
  }

  Future<void> _changeNickname() async {
    final controller = TextEditingController();
    final result = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => Container(
        padding: EdgeInsets.only(
          bottom: MediaQuery.of(context).viewInsets.bottom,
        ),
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.grey[300],
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 24),
              Text(
                '닉네임 변경',
                style: GoogleFonts.gaegu(
                  fontSize: 24,
                  fontWeight: FontWeight.bold,
                  color: const Color(0xFF1A1B1E),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                '새로운 닉네임을 입력해주세요',
                style: GoogleFonts.gaegu(fontSize: 14, color: Colors.grey[600]),
              ),
              const SizedBox(height: 24),
              TextField(
                controller: controller,
                autofocus: true,
                decoration: InputDecoration(
                  hintText: '새 닉네임 입력',
                  hintStyle: GoogleFonts.gaegu(color: Colors.grey[400]),
                  filled: true,
                  fillColor: Colors.grey[50],
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: BorderSide.none,
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                    borderSide: const BorderSide(
                      color: Color(0xFF2ECC71),
                      width: 2,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 24),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => Navigator.pop(context),
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                        side: BorderSide(color: Colors.grey[300]!),
                      ),
                      child: Text(
                        '취소',
                        style: GoogleFonts.gaegu(
                          fontWeight: FontWeight.w600,
                          color: Colors.grey[700],
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () {
                        final newNickname = controller.text.trim();
                        if (newNickname.isEmpty) return;
                        Navigator.pop(context, newNickname);
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF2ECC71),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                        elevation: 0,
                      ),
                      child: Text(
                        '변경',
                        style: GoogleFonts.gaegu(fontWeight: FontWeight.w600),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
            ],
          ),
        ),
      ),
    );

    if (result != null && result.isNotEmpty) {
      try {
        setState(() => _isLoading = true);
        final dio = Dio();
        final api = ApiService(dio);
        final user = await api.updateNickname({"newNickname": result});
        if (!mounted) return;
        setState(() {
          _user = user;
          _isLoading = false;
        });
        _showSuccessSnackBar('닉네임이 변경되었습니다.');
      } catch (_) {
        if (!mounted) return;
        setState(() => _isLoading = false);
        _showErrorSnackBar('닉네임 변경 실패');
      }
    }
  }

  Future<void> _uploadProfileImage() async {
    final XFile? image = await _picker.pickImage(
      source: ImageSource.gallery,
      imageQuality: 80,
    );
    if (image == null) return;

    try {
      setState(() => _isLoading = true);
      final dio = Dio();
      final api = ApiService(dio);
      final file = File(image.path);

      final multipart = await MultipartFile.fromFile(
        file.path,
        filename: file.uri.pathSegments.isNotEmpty
            ? file.uri.pathSegments.last
            : 'image.jpg',
      );

      final user = await api.uploadProfileImage(multipart);
      if (!mounted) return;
      setState(() {
        _user = user;
        _isLoading = false;
      });
      _showSuccessSnackBar('프로필 이미지가 변경되었습니다.');
    } catch (_) {
      if (!mounted) return;
      setState(() => _isLoading = false);
      _showErrorSnackBar('프로필 이미지 변경 실패');
    }
  }

  Future<void> _deleteProfileImage() async {
    try {
      setState(() => _isLoading = true);
      final dio = Dio();
      final api = ApiService(dio);
      final user = await api.deleteProfileImage();
      if (!mounted) return;
      setState(() {
        _user = user;
        _isLoading = false;
      });
      _showSuccessSnackBar('기본 프로필 이미지로 변경되었습니다.');
    } catch (_) {
      if (!mounted) return;
      setState(() => _isLoading = false);
      _showErrorSnackBar('프로필 이미지 삭제 실패');
    }
  }

  Future<void> _deleteAccount() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Text(
          '회원 탈퇴',
          style: GoogleFonts.gaegu(
            fontWeight: FontWeight.bold,
            color: const Color(0xFF1A1B1E),
          ),
        ),
        content: Text(
          '정말로 회원 탈퇴하시겠습니까?\n이 작업은 취소할 수 없습니다.',
          style: GoogleFonts.gaegu(color: Colors.grey[700]),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(
              '취소',
              style: GoogleFonts.gaegu(
                fontWeight: FontWeight.w600,
                color: Colors.grey[600],
              ),
            ),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(
              '탈퇴',
              style: GoogleFonts.gaegu(
                fontWeight: FontWeight.w600,
                color: Colors.red,
              ),
            ),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    try {
      setState(() => _isLoading = true);
      final dio = Dio();
      final api = ApiService(dio);
      await api.deleteAccount();

      final prefs = await SharedPreferences.getInstance();
      await prefs.clear();

      if (context.mounted) {
        Navigator.pushAndRemoveUntil(
          context,
          MaterialPageRoute(builder: (_) => const LoginScreen()),
          (route) => false,
        );
      }
    } catch (_) {
      if (!mounted) return;
      setState(() => _isLoading = false);
      _showErrorSnackBar('회원 탈퇴 실패');
    }
  }

  Future<void> _logout() async {
    try {
      final dio = Dio();
      final api = ApiService(dio);
      await api.logout();
      final prefs = await SharedPreferences.getInstance();
      await prefs.clear();
      if (context.mounted) {
        Navigator.pushAndRemoveUntil(
          context,
          MaterialPageRoute(builder: (_) => const LoginScreen()),
          (route) => false,
        );
      }
    } catch (_) {
      final prefs = await SharedPreferences.getInstance();
      await prefs.clear();
      if (context.mounted) {
        Navigator.pushAndRemoveUntil(
          context,
          MaterialPageRoute(builder: (_) => const LoginScreen()),
          (route) => false,
        );
      }
    }
  }

  void _showSessionExpiredDialog() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Text(
          '세션 만료',
          style: GoogleFonts.gaegu(
            fontWeight: FontWeight.bold,
            color: const Color(0xFF1A1B1E),
          ),
        ),
        content: Text(
          '세션이 만료되었습니다. 다시 로그인해주세요.',
          style: GoogleFonts.gaegu(color: Colors.grey[700]),
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              _logout();
            },
            child: Text(
              '확인',
              style: GoogleFonts.gaegu(
                fontWeight: FontWeight.w600,
                color: const Color(0xFF2ECC71),
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _showSuccessSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message, style: GoogleFonts.gaegu()),
        backgroundColor: const Color(0xFF2ECC71),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      ),
    );
  }

  void _showErrorSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message, style: GoogleFonts.gaegu()),
        backgroundColor: Colors.red,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.grey[50],
      // ✅ NearbyFarmScreen과 동일한 톤의 AppBar 적용
      appBar: AppBar(
        title: const Text(
          '마이페이지',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        backgroundColor: Colors.green[600]?.withOpacity(0.95),
        foregroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _fetchUserData,
            tooltip: '새로고침',
          ),
        ],
      ),
      body: _isLoading
          ? const Center(
              child: CircularProgressIndicator(color: Color(0xFF2ECC71)),
            )
          : _errorMessage != null
          ? _buildErrorWidget()
          : FadeTransition(opacity: _fadeAnimation, child: _buildContent()),
    );
  }

  Widget _buildErrorWidget() {
    return Container(
      margin: const EdgeInsets.all(20),
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.error_outline, size: 64, color: Colors.red[300]),
            const SizedBox(height: 16),
            Text(
              _errorMessage!,
              textAlign: TextAlign.center,
              style: GoogleFonts.gaegu(color: Colors.red[700], fontSize: 16),
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: _fetchUserData,
              icon: const Icon(Icons.refresh),
              label: Text(
                '다시 시도',
                style: GoogleFonts.gaegu(fontWeight: FontWeight.w600),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF2ECC71),
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 12,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                elevation: 0,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildContent() {
    if (_user == null) return const SizedBox();

    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        children: [
          _buildProfileCard(),
          const SizedBox(height: 24),
          _buildInfoCard(),
          const SizedBox(height: 24),
          _buildActionCard(),
        ],
      ),
    );
  }

  Widget _buildProfileCard() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: Colors.grey.withOpacity(0.1),
            blurRadius: 20,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        children: [
          Stack(
            children: [
              Container(
                width: 100,
                height: 100,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: LinearGradient(
                    colors: [Colors.grey[200]!, Colors.grey[300]!],
                  ),
                ),
                child:
                    _user!.profileImageUrl != null &&
                        _user!.profileImageUrl!.isNotEmpty
                    ? ClipRRect(
                        borderRadius: BorderRadius.circular(50),
                        child: Image.network(
                          _user!.profileImageUrl!,
                          width: 100,
                          height: 100,
                          fit: BoxFit.cover,
                          errorBuilder: (context, error, stackTrace) => Icon(
                            Icons.person,
                            size: 50,
                            color: Colors.grey[600],
                          ),
                        ),
                      )
                    : Icon(Icons.person, size: 50, color: Colors.grey[600]),
              ),
              Positioned(
                bottom: 0,
                right: 0,
                child: GestureDetector(
                  onTap: () => _showImageOptions(),
                  child: Container(
                    width: 32,
                    height: 32,
                    decoration: const BoxDecoration(
                      color: Color(0xFF2ECC71),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.camera_alt,
                      color: Colors.white,
                      size: 18,
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Text(
            _user!.nickname,
            style: GoogleFonts.gaegu(
              fontSize: 28,
              fontWeight: FontWeight.bold,
              color: const Color(0xFF1A1B1E),
            ),
          ),
          const SizedBox(height: 4),
          Text(
            _user!.email,
            style: GoogleFonts.gaegu(fontSize: 16, color: Colors.grey[600]),
          ),
          const SizedBox(height: 16),
          OutlinedButton.icon(
            onPressed: _changeNickname,
            icon: const Icon(Icons.edit, size: 18),
            label: Text(
              '닉네임 변경',
              style: GoogleFonts.gaegu(fontWeight: FontWeight.w600),
            ),
            style: OutlinedButton.styleFrom(
              foregroundColor: const Color(0xFF2ECC71),
              side: const BorderSide(color: Color(0xFF2ECC71)),
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoCard() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: Colors.grey.withOpacity(0.1),
            blurRadius: 20,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '계정 정보',
            style: GoogleFonts.gaegu(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: const Color(0xFF1A1B1E),
            ),
          ),
          const SizedBox(height: 20),
          _buildInfoItem(Icons.badge_outlined, '역할', _user!.role),
          if (_user!.oauthProvider != null && _user!.oauthProvider!.isNotEmpty)
            _buildInfoItem(
              Icons.login_outlined,
              'OAuth 제공자',
              _user!.oauthProvider!,
            ),
        ],
      ),
    );
  }

  Widget _buildInfoItem(IconData icon, String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: const Color(0xFF2ECC71).withOpacity(0.1),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(icon, color: const Color(0xFF2ECC71), size: 20),
          ),
          const SizedBox(width: 16),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: GoogleFonts.gaegu(
                  fontSize: 14,
                  color: Colors.grey[600],
                  fontWeight: FontWeight.w500,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                value,
                style: GoogleFonts.gaegu(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  color: const Color(0xFF1A1B1E),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildActionCard() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: Colors.grey.withOpacity(0.1),
            blurRadius: 20,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '설정',
            style: GoogleFonts.gaegu(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: const Color(0xFF1A1B1E),
            ),
          ),
          const SizedBox(height: 20),
          _buildActionItem(
            Icons.settings_outlined,
            '설정',
            Colors.grey[700]!,
            () {
              _showSuccessSnackBar('설정 화면으로 이동합니다.');
            },
          ),
          _buildActionItem(
            Icons.logout_outlined,
            '로그아웃',
            Colors.grey[700]!,
            _logout,
          ),
          _buildActionItem(
            Icons.delete_forever_outlined,
            '회원 탈퇴',
            Colors.red,
            _deleteAccount,
          ),
        ],
      ),
    );
  }

  Widget _buildActionItem(
    IconData icon,
    String title,
    Color color,
    VoidCallback onTap,
  ) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 8),
        child: Row(
          children: [
            Icon(icon, color: color, size: 24),
            const SizedBox(width: 16),
            Text(
              title,
              style: GoogleFonts.gaegu(
                fontSize: 18,
                fontWeight: FontWeight.w600,
                color: color,
              ),
            ),
            const Spacer(),
            Icon(Icons.chevron_right, color: Colors.grey[400], size: 20),
          ],
        ),
      ),
    );
  }

  void _showImageOptions() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (context) => Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(height: 12),
            Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.grey[300],
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(height: 20),
            ListTile(
              leading: const Icon(
                Icons.photo_library,
                color: Color(0xFF2ECC71),
              ),
              title: Text('갤러리에서 선택', style: GoogleFonts.gaegu(fontSize: 16)),
              onTap: () {
                Navigator.pop(context);
                _uploadProfileImage();
              },
            ),
            ListTile(
              leading: const Icon(Icons.delete, color: Colors.red),
              title: Text('기본 이미지로 변경', style: GoogleFonts.gaegu(fontSize: 16)),
              onTap: () {
                Navigator.pop(context);
                _deleteProfileImage();
              },
            ),
            const SizedBox(height: 20),
          ],
        ),
      ),
    );
  }
}
