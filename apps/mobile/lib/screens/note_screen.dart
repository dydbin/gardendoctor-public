import 'dart:convert';
import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:image_picker/image_picker.dart';
import 'package:intl/intl.dart';
import 'package:http_parser/http_parser.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../config/app_config.dart';
import '../models/diary_request_model.dart';
import '../models/diary_response_model.dart';
import '../models/user_plant_model.dart';
import '../services/diary_api_service.dart';

class NoteScreen extends StatefulWidget {
  final DateTime selectedDate;
  final DiaryResponse? editingDiary; // 수정 모드 시 전달

  const NoteScreen({super.key, required this.selectedDate, this.editingDiary});

  @override
  State<NoteScreen> createState() => _NoteScreenState();
}

class _NoteScreenState extends State<NoteScreen> {
  final TextEditingController _titleController = TextEditingController();
  final TextEditingController _notesController = TextEditingController();
  final ImagePicker _picker = ImagePicker();

  List<UserPlantResponse> _myRegisteredPlants = [];
  String? _selectedPlantNickname;
  bool _isLoadingPlants = true;
  bool _isSaving = false;
  String? _error;

  XFile? _imageFile;
  bool _watered = false;
  bool _fertilized = false;
  bool _pruned = false;
  bool _deleteExistingImage = false;

  @override
  void initState() {
    super.initState();
    _fetchMyPlants().then((_) => _initEditModeIfNeeded());
  }

  void _initEditModeIfNeeded() {
    if (widget.editingDiary != null) {
      final d = widget.editingDiary!;
      _titleController.text = d.title;
      _notesController.text = d.content ?? '';
      _watered = d.watered;
      _fertilized = d.fertilized;
      _pruned = d.pruned;

      if (d.connectedUserPlantIds.isNotEmpty &&
          _myRegisteredPlants.isNotEmpty) {
        final userPlantId = d.connectedUserPlantIds.first;
        final plant = _myRegisteredPlants.firstWhere(
          (p) => p.userPlantId == userPlantId,
          orElse: () => _myRegisteredPlants.first,
        );
        _selectedPlantNickname = plant.plantNickname;
      } else {
        _selectedPlantNickname = null;
      }
    }
  }

  @override
  void dispose() {
    _titleController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _fetchMyPlants() async {
    try {
      final SharedPreferences prefs = await SharedPreferences.getInstance();
      final accessToken = prefs.getString('accessToken');
      final dio = Dio(
        BaseOptions(
          baseUrl: AppConfig.apiBaseUrl,
          headers: {HttpHeaders.authorizationHeader: 'Bearer $accessToken'},
        ),
      );
      final response = await dio.get('/api/user-plants');
      final List<UserPlantResponse> plants = (response.data as List)
          .map((e) => UserPlantResponse.fromJson(e))
          .toList();
      if (mounted) setState(() => _myRegisteredPlants = plants);
    } on DioException catch (e) {
      if (mounted) setState(() => _error = "식물 목록 로딩 실패: ${e.message}");
    } finally {
      if (mounted) setState(() => _isLoadingPlants = false);
    }
  }

  Future<void> _pickImage() async {
    final pickedFile = await _picker.pickImage(
      source: ImageSource.gallery,
      imageQuality: 80,
    );
    if (pickedFile != null) setState(() => _imageFile = pickedFile);
  }

  Future<void> _saveNote() async {
    if (_isSaving) return;

    if (_selectedPlantNickname == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('어떤 식물에 대한 기록인지 선택해주세요!', style: GoogleFonts.gaegu()),
        ),
      );
      return;
    }
    if (_titleController.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('제목은 꼭 입력해주세요!', style: GoogleFonts.gaegu())),
      );
      return;
    }

    setState(() => _isSaving = true);

    try {
      final selectedPlant = _myRegisteredPlants.firstWhere(
        (plant) => plant.plantNickname == _selectedPlantNickname,
      );

      if (selectedPlant.userPlantId == null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '식물 정보에 문제가 있습니다. 관리자에게 문의하세요.',
              style: GoogleFonts.gaegu(),
            ),
          ),
        );
        setState(() => _isSaving = false);
        return;
      }

      final diaryRequest = DiaryRequest(
        selectedUserPlantIds: [selectedPlant.userPlantId!],
        title: _titleController.text.trim(),
        content: _notesController.text.trim(),
        diaryDate: DateFormat('yyyy-MM-dd').format(widget.selectedDate),
        watered: _watered,
        fertilized: _fertilized,
        pruned: _pruned,
        deleteExistingImage: _deleteExistingImage,
      );
      final String requestJson = jsonEncode(diaryRequest.toJson());

      final prefs = await SharedPreferences.getInstance();
      final accessToken = prefs.getString('accessToken');
      final dio = Dio(
        BaseOptions(
          baseUrl: AppConfig.apiBaseUrl,
          headers: {HttpHeaders.authorizationHeader: 'Bearer $accessToken'},
        ),
      );

      if (widget.editingDiary == null) {
        // 신규 등록
        final formData = FormData();
        formData.files.add(
          MapEntry(
            'diaryRequest',
            MultipartFile.fromString(
              requestJson,
              contentType: MediaType('application', 'json'),
              filename: 'data.json',
            ),
          ),
        );
        if (_imageFile != null) {
          final file = File(_imageFile!.path);
          formData.files.add(
            MapEntry(
              'imageFile',
              await MultipartFile.fromFile(
                file.path,
                filename: 'note.jpg',
                contentType: MediaType('image', 'jpeg'),
              ),
            ),
          );
        }
        final response = await dio.post('/api/diaries', data: formData);
        if (response.statusCode == 200 || response.statusCode == 201) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text('일지가 성공적으로 기록되었어요!', style: GoogleFonts.gaegu()),
                backgroundColor: Colors.green,
              ),
            );
            Navigator.pop(context, true);
          }
        } else {
          throw Exception("기록 실패: ${response.statusCode}");
        }
      } else {
        // 수정 모드 (PUT)
        final diaryId = widget.editingDiary!.diaryId;
        final formData = FormData();
        formData.files.add(
          MapEntry(
            'request',
            MultipartFile.fromString(
              requestJson,
              contentType: MediaType('application', 'json'),
              filename: 'data.json',
            ),
          ),
        );
        if (_imageFile != null) {
          final file = File(_imageFile!.path);
          formData.files.add(
            MapEntry(
              'newImageFile',
              await MultipartFile.fromFile(
                file.path,
                filename: 'note.jpg',
                contentType: MediaType('image', 'jpeg'),
              ),
            ),
          );
        }
        final response = await dio.put('/api/diaries/$diaryId', data: formData);
        if (response.statusCode == 200) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text('일지가 성공적으로 수정되었어요!', style: GoogleFonts.gaegu()),
                backgroundColor: Colors.green,
              ),
            );
            Navigator.pop(context, true);
          }
        } else {
          throw Exception("수정 실패: ${response.statusCode}");
        }
      }
    } on DioException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '저장 실패: ${e.response?.data['message'] ?? e.message}',
              style: GoogleFonts.gaegu(),
            ),
            backgroundColor: Colors.redAccent,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    String formattedDate = DateFormat(
      'yyyy. MM. dd (E)',
      'ko_KR',
    ).format(widget.selectedDate);

    return Scaffold(
      backgroundColor: const Color(0xFFFDFCF8),
      appBar: AppBar(
        title: Text(
          widget.editingDiary == null ? '오늘의 일지' : '일지 수정',
          style: GoogleFonts.gaegu(fontWeight: FontWeight.bold),
        ),
        backgroundColor: const Color(0xFF81C784),
        foregroundColor: Colors.white,
        elevation: 0,
        actions: [
          TextButton(
            onPressed: _isSaving ? null : _saveNote,
            child: _isSaving
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      color: Colors.white,
                      strokeWidth: 2.5,
                    ),
                  )
                : Text(
                    widget.editingDiary == null ? '기록하기' : '수정하기',
                    style: GoogleFonts.gaegu(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
          ),
        ],
      ),
      body: _isLoadingPlants
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Text(_error!, textAlign: TextAlign.center),
              ),
            )
          : SingleChildScrollView(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildDateChip(formattedDate),
                  const SizedBox(height: 24),
                  _buildTitleField(),
                  const SizedBox(height: 24),
                  _buildMyPlantSelection(),
                  const SizedBox(height: 24),
                  _buildImagePicker(),
                  if (widget.editingDiary != null &&
                      widget.editingDiary!.imageUrl != null)
                    CheckboxListTile(
                      title: Text('기존 이미지 삭제', style: GoogleFonts.gaegu()),
                      value: _deleteExistingImage,
                      onChanged: (val) =>
                          setState(() => _deleteExistingImage = val ?? false),
                    ),
                  const SizedBox(height: 24),
                  _buildCareSection(),
                  const SizedBox(height: 24),
                  _buildNoteField(),
                ],
              ),
            ),
    );
  }

  // 이하 UI 빌드 위젯들은 그대로!
  Widget _buildDateChip(String formattedDate) {
    return Center(
      child: Text(
        formattedDate,
        style: GoogleFonts.gaegu(
          fontSize: 20,
          fontWeight: FontWeight.bold,
          color: Colors.grey[700],
        ),
      ),
    );
  }

  Widget _buildTitleField() {
    return TextFormField(
      controller: _titleController,
      decoration: InputDecoration(
        hintText: '✏️ 오늘의 일지 제목',
        hintStyle: GoogleFonts.gaegu(color: Colors.grey[500], fontSize: 24),
        border: InputBorder.none,
      ),
      style: GoogleFonts.gaegu(
        fontSize: 24,
        fontWeight: FontWeight.bold,
        color: const Color(0xFF3E2723),
      ),
    );
  }

  Widget _buildMyPlantSelection() {
    if (_myRegisteredPlants.isEmpty) {
      return Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: Colors.grey[100],
          borderRadius: BorderRadius.circular(15),
        ),
        child: Center(
          child: Text(
            '등록된 식물이 없습니다.\n먼저 내 식물을 등록해주세요.',
            style: GoogleFonts.gaegu(),
            textAlign: TextAlign.center,
          ),
        ),
      );
    }
    return DropdownButtonFormField<String>(
      value: _selectedPlantNickname,
      hint: Text(
        '어떤 식물의 기록인가요?',
        style: GoogleFonts.gaegu(color: Colors.grey[600]),
      ),
      decoration: InputDecoration(
        prefixIcon: const Icon(
          Icons.filter_vintage_rounded,
          color: Color(0xFF2ECC71),
        ),
        filled: true,
        fillColor: Colors.green.withOpacity(0.05),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(15),
          borderSide: BorderSide.none,
        ),
      ),
      items: _myRegisteredPlants.map((plant) {
        return DropdownMenuItem(
          value: plant.plantNickname,
          child: Row(
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: plant.userPlantImageUrl != null
                    ? Image.network(
                        plant.userPlantImageUrl!,
                        width: 24,
                        height: 24,
                        fit: BoxFit.cover,
                        errorBuilder: (c, e, s) => const Icon(
                          Icons.eco_rounded,
                          size: 24,
                          color: Colors.grey,
                        ),
                      )
                    : const Icon(
                        Icons.eco_rounded,
                        size: 24,
                        color: Colors.grey,
                      ),
              ),
              const SizedBox(width: 8),
              Text(plant.plantNickname ?? '이름 없음', style: GoogleFonts.gaegu()),
            ],
          ),
        );
      }).toList(),
      onChanged: (value) => setState(() => _selectedPlantNickname = value),
      style: GoogleFonts.gaegu(color: Colors.black87),
    );
  }

  Widget _buildImagePicker() {
    return GestureDetector(
      onTap: _pickImage,
      child: Container(
        height: 200,
        width: double.infinity,
        decoration: BoxDecoration(
          color: Colors.brown[50],
          borderRadius: BorderRadius.circular(15),
          border: Border.all(color: Colors.brown.withOpacity(0.2)),
          image: _imageFile != null
              ? DecorationImage(
                  image: FileImage(File(_imageFile!.path)),
                  fit: BoxFit.cover,
                )
              : null,
        ),
        child: _imageFile == null
            ? Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.add_a_photo_outlined,
                      size: 40,
                      color: Colors.brown[300],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      '사진 붙이기',
                      style: GoogleFonts.gaegu(color: Colors.brown[400]),
                    ),
                  ],
                ),
              )
            : null,
      ),
    );
  }

  Widget _buildCareSection() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceAround,
      children: [
        _buildCareItem(
          icon: Icons.water_drop_rounded,
          label: '물주기',
          isChecked: _watered,
          color: const Color(0xFF3498DB),
          onTap: () => setState(() => _watered = !_watered),
        ),
        _buildCareItem(
          icon: Icons.science_rounded,
          label: '영양제',
          isChecked: _fertilized,
          color: const Color(0xFFE67E22),
          onTap: () => setState(() => _fertilized = !_fertilized),
        ),
        _buildCareItem(
          icon: Icons.grass_rounded,
          label: '가지치기',
          isChecked: _pruned,
          color: const Color(0xFF9B59B6),
          onTap: () => setState(() => _pruned = !_pruned),
        ),
      ],
    );
  }

  Widget _buildCareItem({
    required IconData icon,
    required String label,
    required bool isChecked,
    required Color color,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Column(
        children: [
          AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: isChecked ? color : Colors.grey[200],
              shape: BoxShape.circle,
              boxShadow: isChecked
                  ? [
                      BoxShadow(
                        color: color.withOpacity(0.4),
                        blurRadius: 8,
                        offset: const Offset(0, 4),
                      ),
                    ]
                  : [],
            ),
            child: Icon(
              icon,
              size: 28,
              color: isChecked ? Colors.white : Colors.grey[600],
            ),
          ),
          const SizedBox(height: 8),
          Text(
            label,
            style: GoogleFonts.gaegu(
              fontWeight: FontWeight.bold,
              color: isChecked ? color : Colors.grey[600],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildNoteField() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(15),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: TextFormField(
        controller: _notesController,
        maxLines: 10,
        decoration: InputDecoration(
          hintText:
              '오늘 식물은 어떤 모습이었나요?\n어떤 변화가 있었는지, 어떤 기분이 들었는지 자유롭게 기록해보세요...',
          hintStyle: GoogleFonts.gaegu(color: Colors.grey[400], height: 1.7),
          border: InputBorder.none,
        ),
        style: GoogleFonts.gaegu(
          fontSize: 16,
          height: 1.7,
          color: const Color(0xFF3E2723),
        ),
      ),
    );
  }
}
