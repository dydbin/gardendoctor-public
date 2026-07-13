// lib/screens/confirmation_screen.dart
import 'dart:io';
import 'dart:convert'; // ✅ jsonEncode 사용
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:http_parser/http_parser.dart';
import 'package:image_picker/image_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../config/app_config.dart';
import '../services/dio_interceptor.dart';
import 'plant_list_screen.dart'; // ✅ 등록 성공 후 이동할 화면

class ConfirmationScreen extends StatefulWidget {
  final String plantType;
  final String nickname;
  final String location;
  final String? notes;
  final XFile? imageFile;
  final int gardenUniqueId;

  // 오늘 한 일
  final bool watered;
  final bool pruned;
  final bool fertilized;

  // ✅ 알림/주기 설정
  final bool isNotificationEnabled;
  final int waterIntervalDays;
  final int pruneIntervalDays;
  final int fertilizeIntervalDays;

  const ConfirmationScreen({
    super.key,
    required this.plantType,
    required this.nickname,
    required this.location,
    required this.gardenUniqueId,
    required this.watered,
    required this.pruned,
    required this.fertilized,
    required this.isNotificationEnabled,
    required this.waterIntervalDays,
    required this.pruneIntervalDays,
    required this.fertilizeIntervalDays,
    this.notes,
    this.imageFile,
  });

  @override
  State<ConfirmationScreen> createState() => _ConfirmationScreenState();
}

class _ConfirmationScreenState extends State<ConfirmationScreen> {
  bool _saving = false;
  late final Dio dio;

  @override
  void initState() {
    super.initState();
    dio = Dio(
      BaseOptions(
        baseUrl: AppConfig.apiBaseUrl,
        connectTimeout: const Duration(seconds: 10),
        receiveTimeout: const Duration(seconds: 10),
      ),
    );
    dio.interceptors.add(DioInterceptor());
  }

  Future<void> _submit() async {
    setState(() => _saving = true);

    try {
      final prefs = await SharedPreferences.getInstance();
      final accessToken = prefs.getString('accessToken');

      final headers = <String, dynamic>{};
      if (accessToken != null && accessToken.isNotEmpty) {
        headers[HttpHeaders.authorizationHeader] = 'Bearer $accessToken';
      }

      // ✅ 서버 스펙: "isNotificationEnabled" 로 전송
      final payload = {
        "plantName": widget.plantType.trim(),
        "plantNickname": widget.nickname.trim(),
        "gardenUniqueId": widget.gardenUniqueId,
        "plantingPlace": widget.location.trim(),
        "notes": (widget.notes ?? "").trim(),
        "watered": widget.watered,
        "pruned": widget.pruned,
        "fertilized": widget.fertilized,
        "isNotificationEnabled": widget.isNotificationEnabled,
        "waterIntervalDays": widget.waterIntervalDays,
        "pruneIntervalDays": widget.pruneIntervalDays,
        "fertilizeIntervalDays": widget.fertilizeIntervalDays,
      };

      final formData = FormData();

      // JSON 파트
      formData.files.add(
        MapEntry(
          'data',
          MultipartFile.fromString(
            jsonEncode(payload),
            filename: 'data.json',
            contentType: MediaType('application', 'json'),
          ),
        ),
      );

      // 이미지 파일 파트(선택)
      if (widget.imageFile != null) {
        final path = widget.imageFile!.path;
        final ext = path.split('.').last.toLowerCase();
        final mime = (ext == 'png') ? 'png' : 'jpeg';

        formData.files.add(
          MapEntry(
            'file',
            await MultipartFile.fromFile(
              path,
              filename: path.split('/').last,
              contentType: MediaType('image', mime),
            ),
          ),
        );
      }

      await dio.post(
        '/api/user-plants',
        data: formData,
        options: Options(headers: headers, contentType: 'multipart/form-data'),
      );

      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '식물이 등록되었습니다 🌱',
            style: GoogleFonts.gaegu(color: Colors.white),
          ),
          backgroundColor: const Color(0xFF2ECC71),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          margin: const EdgeInsets.all(16),
        ),
      );

      // ✅ 리스트 화면으로 바로 이동 (스택 초기화)
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const PlantListScreen()),
        (route) => false,
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '등록에 실패했습니다: $e',
            style: GoogleFonts.gaegu(color: Colors.white),
          ),
          backgroundColor: const Color(0xFFE74C3C),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          margin: const EdgeInsets.all(16),
        ),
      );
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final rows = <_ConfirmRow>[
      _ConfirmRow(
        icon: Icons.local_florist,
        label: '식물 종류',
        value: widget.plantType,
      ),
      _ConfirmRow(icon: Icons.favorite, label: '별명', value: widget.nickname),
      _ConfirmRow(icon: Icons.place, label: '장소', value: widget.location),
      if ((widget.notes ?? '').trim().isNotEmpty)
        _ConfirmRow(
          icon: Icons.notes,
          label: '메모',
          value: widget.notes!.trim(),
        ),
    ];

    final taskChips = [
      if (widget.watered) _pill('물 주기 완료'),
      if (widget.pruned) _pill('가지치기 완료'),
      if (widget.fertilized) _pill('영양제 완료'),
    ];

    final scheduleChips = [
      _pill('알림 ${widget.isNotificationEnabled ? "켜짐" : "꺼짐"}'),
      _pill('물 ${widget.waterIntervalDays}일'),
      _pill('가지치기 ${widget.pruneIntervalDays}일'),
      _pill('영양제 ${widget.fertilizeIntervalDays}일'),
    ];

    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      appBar: AppBar(
        title: Text(
          '등록 확인',
          style: GoogleFonts.gaegu(fontWeight: FontWeight.bold),
        ),
        backgroundColor: const Color(0xFF2ECC71),
        foregroundColor: Colors.white,
        elevation: 0,
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView(
              padding: const EdgeInsets.all(24),
              children: [
                _PhotoCard(imageFile: widget.imageFile),
                const SizedBox(height: 16),
                _SectionCard(
                  title: '기본 정보',
                  child: Column(
                    children: rows
                        .map(
                          (r) => Padding(
                            padding: const EdgeInsets.symmetric(vertical: 8.0),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Icon(r.icon, color: const Color(0xFF2ECC71)),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        r.label,
                                        style: GoogleFonts.gaegu(
                                          fontSize: 14,
                                          color: Colors.grey[600],
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        r.value,
                                        style: GoogleFonts.gaegu(
                                          fontSize: 16,
                                          fontWeight: FontWeight.bold,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        )
                        .toList(),
                  ),
                ),
                const SizedBox(height: 16),
                _SectionCard(
                  title: '오늘 한 일',
                  child: taskChips.isEmpty
                      ? Text(
                          '체크한 항목이 없어요',
                          style: GoogleFonts.gaegu(color: Colors.grey[600]),
                        )
                      : Wrap(spacing: 8, runSpacing: 8, children: taskChips),
                ),
                const SizedBox(height: 16),
                _SectionCard(
                  title: '알림 & 주기',
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: scheduleChips,
                  ),
                ),
              ],
            ),
          ),
          _BottomBar(saving: _saving, onSubmit: _submit),
        ],
      ),
    );
  }

  Widget _pill(String text) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: const Color(0xFF2ECC71).withOpacity(0.12),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: const Color(0xFF2ECC71).withOpacity(0.4)),
      ),
      child: Text(
        text,
        style: GoogleFonts.gaegu(
          fontSize: 13,
          fontWeight: FontWeight.bold,
          color: const Color(0xFF2ECC71),
        ),
      ),
    );
  }
}

class _ConfirmRow {
  final IconData icon;
  final String label;
  final String value;
  _ConfirmRow({required this.icon, required this.label, required this.value});
}

class _SectionCard extends StatelessWidget {
  final String title;
  final Widget child;
  const _SectionCard({required this.title, required this.child, super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: GoogleFonts.gaegu(
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: const Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 12),
          child,
        ],
      ),
    );
  }
}

class _PhotoCard extends StatelessWidget {
  final XFile? imageFile;
  const _PhotoCard({required this.imageFile, super.key});

  @override
  Widget build(BuildContext context) {
    final hasImage = imageFile != null;
    return Container(
      height: 200,
      decoration: BoxDecoration(
        color: hasImage ? Colors.transparent : Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.grey[200]!),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
        image: hasImage
            ? DecorationImage(
                image: FileImage(File(imageFile!.path)),
                fit: BoxFit.cover,
              )
            : null,
      ),
      child: !hasImage
          ? Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.image, color: Colors.grey[400], size: 40),
                  const SizedBox(height: 8),
                  Text(
                    '사진 없음',
                    style: GoogleFonts.gaegu(color: Colors.grey[600]),
                  ),
                ],
              ),
            )
          : const SizedBox.shrink(),
    );
  }
}

class _BottomBar extends StatelessWidget {
  final bool saving;
  final VoidCallback onSubmit;
  const _BottomBar({required this.saving, required this.onSubmit, super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: const BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(
            color: Color(0x14000000),
            blurRadius: 12,
            offset: Offset(0, -4),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: SizedBox(
          width: double.infinity,
          child: ElevatedButton.icon(
            onPressed: saving ? null : onSubmit,
            icon: saving
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                    ),
                  )
                : const Icon(Icons.check_circle_outline, size: 20),
            label: Text(
              saving ? '등록 중...' : '이 설정으로 등록하기',
              style: GoogleFonts.gaegu(
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF2ECC71),
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(vertical: 16),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(14),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
