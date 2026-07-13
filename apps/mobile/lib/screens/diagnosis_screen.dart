import 'dart:io';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:image_picker/image_picker.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import '../../models/photo_analysis_sidebar_response.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../config/app_config.dart';

class DiagnosisScreen extends StatefulWidget {
  const DiagnosisScreen({super.key});

  @override
  State<DiagnosisScreen> createState() => _DiagnosisScreenState();
}

class _DiagnosisScreenState extends State<DiagnosisScreen>
    with SingleTickerProviderStateMixin {
  final ImagePicker _picker = ImagePicker();
  XFile? _imageFile;
  bool _loading = false;
  String? _error;
  late AnimationController _animationController;
  late Animation<double> _fadeAnimation;

  @override
  void initState() {
    super.initState();
    _animationController = AnimationController(
      duration: const Duration(milliseconds: 1500),
      vsync: this,
    );
    _fadeAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _animationController, curve: Curves.easeInOut),
    );
    _animationController.forward();
  }

  @override
  void dispose() {
    _animationController.dispose();
    super.dispose();
  }

  Future<void> _pickFromCamera() async {
    final pickedFile = await _picker.pickImage(
      source: ImageSource.camera,
      imageQuality: 85,
      maxWidth: 1920,
      maxHeight: 1920,
    );
    if (pickedFile != null) {
      setState(() {
        _imageFile = pickedFile;
        _error = null;
      });
      await _analyzeImage();
    }
  }

  Future<void> _pickFromGallery() async {
    final pickedFile = await _picker.pickImage(
      source: ImageSource.gallery,
      imageQuality: 85,
      maxWidth: 1920,
      maxHeight: 1920,
    );
    if (pickedFile != null) {
      setState(() {
        _imageFile = pickedFile;
        _error = null;
      });
      await _analyzeImage();
    }
  }

  Future<void> _analyzeImage() async {
    if (_imageFile == null) return;
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final url = Uri.parse(
        '${AppConfig.apiBaseUrl}/api/photo-analysis/analyze',
      );
      final prefs = await SharedPreferences.getInstance();
      final accessToken = prefs.getString('accessToken');
      final request = http.MultipartRequest(
        'POST',
        url,
      )..files.add(await http.MultipartFile.fromPath('file', _imageFile!.path));
      if (accessToken != null && accessToken.isNotEmpty) {
        request.headers['Authorization'] = 'Bearer $accessToken';
      }

      final response = await request.send();

      if (response.statusCode == 201) {
        final responseBody = await response.stream.bytesToString();
        final data = json.decode(responseBody);
        final result = PhotoAnalysisSidebarResponseDto.fromJson(data);

        if (context.mounted) {
          Navigator.pop(context, result);
        }
      } else if (response.statusCode == 401) {
        setState(() {
          _error = "로그인이 만료되었거나 권한이 없습니다. 다시 로그인하세요.";
        });
      } else {
        setState(() {
          _error = "AI 분석 실패 (code: ${response.statusCode})";
        });
      }
    } catch (e) {
      setState(() {
        _error = "네트워크 오류: $e";
      });
    } finally {
      setState(() {
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      extendBodyBehindAppBar: true,
      appBar: AppBar(
        title: Text(
          'AI 작물 진단',
          style: GoogleFonts.gaegu(
            fontSize: 22,
            fontWeight: FontWeight.bold,
            color: Colors.white,
          ),
        ),
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(
            Icons.arrow_back_ios_new, // ✅ 통일된 아이콘
            color: Colors.white, // ✅ 통일된 색상
            size: 20, // ✅ 통일된 크기
          ),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: FadeTransition(
        opacity: _fadeAnimation,
        child: Container(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [Color(0xFF2ECC71), Color(0xFF27AE60), Color(0xFFF8F9FA)],
              stops: [0.0, 0.3, 0.7],
            ),
          ),
          child: SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                children: [
                  const SizedBox(height: 40),
                  // 헤더 텍스트
                  Text(
                    '작물 사진을 촬영하거나\n갤러리에서 선택하세요',
                    textAlign: TextAlign.center,
                    style: GoogleFonts.gaegu(
                      fontSize: 24,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                      height: 1.3,
                    ),
                  ),
                  const SizedBox(height: 30),

                  // 이미지 표시 카드
                  Expanded(
                    flex: 3,
                    child: Container(
                      width: double.infinity,
                      decoration: BoxDecoration(
                        color: const Color(0xFFF2F4F7), // ★ 은은한 회색 배경
                        borderRadius: BorderRadius.circular(24),
                        border: Border.all(color: Colors.black12), // ★ 얇은 테두리
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withOpacity(0.06),
                            blurRadius: 18,
                            offset: const Offset(0, 8),
                          ),
                        ],
                      ),
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(24),
                        child: _imageFile == null
                            ? _buildEmptyImagePlaceholder()
                            : Stack(
                                children: [
                                  // 원본 이미지
                                  Positioned.fill(
                                    child: Image.file(
                                      File(_imageFile!.path),
                                      width: double.infinity,
                                      height: double.infinity,
                                      fit: BoxFit.cover,
                                    ),
                                  ),
                                  // ★ 살짝 어둡게 (흰 배경 사진 대비 강화)
                                  Positioned.fill(
                                    child: Container(
                                      color: Colors.black.withOpacity(0.12),
                                    ),
                                  ),
                                  if (_loading)
                                    Positioned.fill(
                                      child: Container(
                                        color: Colors.black.withOpacity(0.7),
                                        child: const Center(
                                          child: Column(
                                            mainAxisAlignment:
                                                MainAxisAlignment.center,
                                            children: [
                                              CircularProgressIndicator(
                                                valueColor:
                                                    AlwaysStoppedAnimation<
                                                      Color
                                                    >(Colors.white),
                                                strokeWidth: 3,
                                              ),
                                              SizedBox(height: 16),
                                              Text(
                                                'AI가 분석 중입니다...',
                                                style: TextStyle(
                                                  color: Colors.white,
                                                  fontSize: 16,
                                                  fontWeight: FontWeight.w500,
                                                ),
                                              ),
                                            ],
                                          ),
                                        ),
                                      ),
                                    ),
                                ],
                              ),
                      ),
                    ),
                  ),

                  const SizedBox(height: 30),

                  // 에러 메시지
                  if (_error != null) ...[
                    Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        color: Colors.red.shade50,
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: Colors.red.shade200),
                      ),
                      child: Row(
                        children: [
                          Icon(Icons.error_outline, color: Colors.red.shade600),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Text(
                              _error!,
                              style: GoogleFonts.gaegu(
                                color: Colors.red.shade700,
                                fontSize: 14,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 20),
                  ],

                  // 버튼들
                  Row(
                    children: [
                      Expanded(
                        child: _buildActionButton(
                          icon: Icons.camera_alt_rounded,
                          label: '카메라 촬영',
                          color: Colors.white,
                          textColor: const Color(0xFF2ECC71),
                          onPressed: _loading ? null : _pickFromCamera,
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: _buildActionButton(
                          icon: Icons.photo_library_rounded,
                          label: '갤러리 선택',
                          color: const Color(0xFF2ECC71), // ✅ 진한 초록 배경(불투명)
                          textColor: Colors.white, // ✅ 흰 글씨
                          onPressed: _loading ? null : _pickFromGallery,
                        ),
                      ),
                    ],
                  ),

                  const SizedBox(height: 20),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildEmptyImagePlaceholder() {
    return Container(
      color: Colors.white, // 배경 흰색 유지 (카드 테두리+그림자와 대비됨)
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: const Color(0xFF2ECC71).withOpacity(0.1),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.add_photo_alternate_rounded,
              size: 80,
              color: Color(0xFF2ECC71),
            ),
          ),
          const SizedBox(height: 24),
          Text(
            '사진을 선택해 주세요',
            style: GoogleFonts.gaegu(
              fontSize: 20,
              fontWeight: FontWeight.bold,
              color: Colors.grey[600],
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '카메라로 촬영하거나 갤러리에서\n이미지를 선택할 수 있습니다',
            textAlign: TextAlign.center,
            style: GoogleFonts.gaegu(
              fontSize: 14,
              color: Colors.grey[500],
              height: 1.4,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActionButton({
    required IconData icon,
    required String label,
    required Color color,
    required Color textColor,
    required VoidCallback? onPressed,
  }) {
    return SizedBox(
      height: 56,
      child: ElevatedButton.icon(
        onPressed: onPressed,
        icon: Icon(icon, size: 22),
        label: Text(
          label,
          style: GoogleFonts.gaegu(fontSize: 16, fontWeight: FontWeight.bold),
        ),
        style: ElevatedButton.styleFrom(
          backgroundColor: color,
          foregroundColor: textColor,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          elevation: color == Colors.white ? 8 : 0,
          shadowColor: color == Colors.white
              ? Colors.black.withOpacity(0.1)
              : Colors.transparent,
        ),
      ),
    );
  }
}
