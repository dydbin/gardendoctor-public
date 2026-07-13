// lib/screens/diary_detail_screen.dart

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:intl/intl.dart';
import 'dart:io';
import 'package:shared_preferences/shared_preferences.dart';

import '../config/app_config.dart';
import '../models/diary_response_model.dart';
import '../services/diary_api_service.dart';
import 'note_screen.dart';

class DiaryDetailScreen extends StatefulWidget {
  final DiaryResponse diary;

  const DiaryDetailScreen({super.key, required this.diary});

  @override
  State<DiaryDetailScreen> createState() => _DiaryDetailScreenState();
}

class _DiaryDetailScreenState extends State<DiaryDetailScreen> {
  bool _isDeleting = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      appBar: AppBar(
        title: Text(
          '일지 상세',
          style: GoogleFonts.gaegu(fontWeight: FontWeight.bold, fontSize: 24),
        ),
        backgroundColor: Colors.white,
        elevation: 1,
        actions: [
          IconButton(
            icon: const Icon(Icons.edit, color: Color(0xFF2ECC71)),
            onPressed: _editDiary,
          ),
          IconButton(
            icon: const Icon(Icons.delete, color: Colors.redAccent),
            onPressed: _showDeleteDialog,
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildImageSection(),
            const SizedBox(height: 20),
            _buildTitleSection(),
            const SizedBox(height: 16),
            _buildDateSection(),
            const SizedBox(height: 20),
            _buildContentSection(),
            const SizedBox(height: 20),
            _buildCareInfoSection(),
          ],
        ),
      ),
    );
  }

  Widget _buildImageSection() {
    return Card(
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(16),
        child: widget.diary.imageUrl != null
            ? Image.network(
                widget.diary.imageUrl!,
                width: double.infinity,
                height: 200,
                fit: BoxFit.cover,
                errorBuilder: (context, error, stackTrace) => Container(
                  width: double.infinity,
                  height: 200,
                  color: Colors.grey[200],
                  child: const Icon(
                    Icons.image_not_supported,
                    size: 50,
                    color: Colors.grey,
                  ),
                ),
              )
            : Container(
                width: double.infinity,
                height: 200,
                color: Colors.grey[200],
                child: const Icon(
                  Icons.notes_rounded,
                  size: 50,
                  color: Colors.grey,
                ),
              ),
      ),
    );
  }

  Widget _buildTitleSection() {
    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '제목',
              style: GoogleFonts.gaegu(
                fontSize: 14,
                color: Colors.grey[600],
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              widget.diary.title,
              style: GoogleFonts.gaegu(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                color: Colors.grey[800],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDateSection() {
    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Row(
          children: [
            const Icon(
              Icons.calendar_today,
              color: Color(0xFF2ECC71),
              size: 20,
            ),
            const SizedBox(width: 12),
            Text(
              widget.diary.diaryDate != null
                  ? DateFormat(
                      'yyyy년 M월 d일 EEEE',
                      'ko_KR',
                    ).format(DateTime.parse(widget.diary.diaryDate!))
                  : '날짜 정보 없음',
              style: GoogleFonts.gaegu(fontSize: 16, color: Colors.grey[700]),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildContentSection() {
    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '내용',
              style: GoogleFonts.gaegu(
                fontSize: 14,
                color: Colors.grey[600],
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              widget.diary.content ?? '내용이 없습니다.',
              style: GoogleFonts.gaegu(
                fontSize: 16,
                color: Colors.grey[800],
                height: 1.5,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCareInfoSection() {
    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '식물 관리 정보',
              style: GoogleFonts.gaegu(
                fontSize: 14,
                color: Colors.grey[600],
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildCareItem(
                  '물주기',
                  widget.diary.watered ?? false,
                  Icons.water_drop,
                ),
                _buildCareItem(
                  '가지치기',
                  widget.diary.pruned ?? false,
                  Icons.content_cut,
                ),
                _buildCareItem(
                  '비료주기',
                  widget.diary.fertilized ?? false,
                  Icons.eco,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCareItem(String label, bool isDone, IconData icon) {
    return Column(
      children: [
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: isDone
                ? const Color(0xFF2ECC71).withOpacity(0.1)
                : Colors.grey[100],
            shape: BoxShape.circle,
          ),
          child: Icon(
            icon,
            color: isDone ? const Color(0xFF2ECC71) : Colors.grey,
            size: 24,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          label,
          style: GoogleFonts.gaegu(
            fontSize: 12,
            color: isDone ? const Color(0xFF2ECC71) : Colors.grey,
          ),
        ),
      ],
    );
  }

  void _editDiary() async {
    final result = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => NoteScreen(
          selectedDate: DateTime.parse(widget.diary.diaryDate!),
          editingDiary: widget.diary, // 수정할 일지 전달
        ),
      ),
    );

    if (result == true) {
      // 수정이 완료되면 이전 화면으로 돌아가면서 새로고침 신호 전달
      Navigator.pop(context, true);
    }
  }

  void _showDeleteDialog() {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          title: Text(
            '일지 삭제',
            style: GoogleFonts.gaegu(fontWeight: FontWeight.bold),
          ),
          content: Text(
            '정말로 이 일지를 삭제하시겠습니까?\n삭제된 일지는 복구할 수 없습니다.',
            style: GoogleFonts.gaegu(),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text('취소', style: GoogleFonts.gaegu(color: Colors.grey)),
            ),
            TextButton(
              onPressed: _isDeleting ? null : _deleteDiary,
              child: _isDeleting
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Text(
                      '삭제',
                      style: GoogleFonts.gaegu(color: Colors.redAccent),
                    ),
            ),
          ],
        );
      },
    );
  }

  void _deleteDiary() async {
    setState(() => _isDeleting = true);

    try {
      final prefs = await SharedPreferences.getInstance();
      final accessToken = prefs.getString('accessToken');

      final dio = Dio(
        BaseOptions(
          baseUrl: AppConfig.apiBaseUrl,
          headers: {HttpHeaders.authorizationHeader: 'Bearer $accessToken'},
        ),
      );

      final apiService = DiaryApiService(dio);
      await apiService.deleteDiary(widget.diary.diaryId!);

      if (mounted) {
        Navigator.pop(context); // 다이얼로그 닫기
        Navigator.pop(context, true); // 상세 화면 닫기 및 새로고침 신호 전달

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('일지가 삭제되었습니다.', style: GoogleFonts.gaegu()),
            backgroundColor: const Color(0xFF2ECC71),
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10),
            ),
          ),
        );
      }
    } on DioException catch (e) {
      if (mounted) {
        Navigator.pop(context); // 다이얼로그 닫기

        String errorMessage = "삭제에 실패했습니다.";
        if (e.response?.statusCode == 401) {
          errorMessage = "인증에 실패했습니다. 다시 로그인해주세요.";
        } else if (e.response?.statusCode == 404) {
          errorMessage = "삭제할 일지를 찾을 수 없습니다.";
        }

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(errorMessage, style: GoogleFonts.gaegu()),
            backgroundColor: Colors.redAccent,
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10),
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isDeleting = false);
    }
  }
}
