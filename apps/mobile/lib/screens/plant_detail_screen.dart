// lib/screens/plant_detail_screen.dart
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:image_picker/image_picker.dart';
import 'package:http_parser/http_parser.dart';
import 'dart:convert'; // ⬅️ 파일 상단에 추가
import '../config/app_config.dart';
import '../../models/user_plant_model.dart';
import 'register_plant_screen.dart';
import '../../services/farm_api_service.dart';
import '../../services/dio_interceptor.dart';

class PlantDetailScreen extends StatefulWidget {
  final int userPlantId;

  const PlantDetailScreen({super.key, required this.userPlantId});

  @override
  State<PlantDetailScreen> createState() => _PlantDetailScreenState();
}

class _PlantDetailScreenState extends State<PlantDetailScreen>
    with TickerProviderStateMixin {
  UserPlantResponse? _plant;
  bool _loading = true;
  String? _error;
  bool _saving = false;

  final _nicknameController = TextEditingController();
  final _locationController = TextEditingController();
  final _notesController = TextEditingController();

  File? _imageFile;
  int? _selectedGardenId;

  // ✅ 알림 & 주기(상세/수정용 상태)
  bool _notificationEnabled = false;
  int _waterIntervalDays = 3;
  int _pruneIntervalDays = 14;
  int _fertilizeIntervalDays = 30;

  late final FarmApiService farmApiService;
  late AnimationController _slideController;
  late AnimationController _fadeController;

  @override
  void initState() {
    super.initState();
    _slideController = AnimationController(
      duration: const Duration(milliseconds: 600),
      vsync: this,
    );
    _fadeController = AnimationController(
      duration: const Duration(milliseconds: 800),
      vsync: this,
    );

    _fetchDetail();

    final dio = Dio(
      BaseOptions(
        baseUrl: AppConfig.apiBaseUrl,
        connectTimeout: const Duration(seconds: 8),
        receiveTimeout: const Duration(seconds: 8),
      ),
    );
    dio.interceptors.add(DioInterceptor());
    farmApiService = FarmApiService(dio);
  }

  @override
  void dispose() {
    _nicknameController.dispose();
    _locationController.dispose();
    _notesController.dispose();
    _slideController.dispose();
    _fadeController.dispose();
    super.dispose();
  }

  Future<void> _fetchDetail() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    final prefs = await SharedPreferences.getInstance();
    final accessToken = prefs.getString('accessToken');
    final dio = Dio(
      BaseOptions(
        baseUrl: AppConfig.apiBaseUrl,
        headers: {HttpHeaders.authorizationHeader: 'Bearer $accessToken'},
        connectTimeout: const Duration(seconds: 8),
        receiveTimeout: const Duration(seconds: 8),
      ),
    );

    try {
      final response = await dio.get('/api/user-plants/${widget.userPlantId}');
      final data = UserPlantResponse.fromJson(response.data);

      // ⭕ 모델 값 사용 + 기본값
      _notificationEnabled = data.isNotificationEnabled ?? false;
      _waterIntervalDays = data.waterIntervalDays ?? 3;
      _pruneIntervalDays = data.pruneIntervalDays ?? 14;
      _fertilizeIntervalDays = data.fertilizeIntervalDays ?? 30;

      setState(() {
        _plant = data;
        _loading = false;
        _nicknameController.text = _plant?.plantNickname ?? '';
        _locationController.text = _plant?.plantingPlace ?? '';
        _notesController.text = _plant?.notes ?? '';
        _selectedGardenId = _plant?.gardenUniqueId;
      });

      _slideController.forward();
      _fadeController.forward();
    } catch (e) {
      setState(() {
        _loading = false;
        _error = "상세 정보를 불러오지 못했습니다.";
      });
    }
  }

  // 안전 변환 유틸
  int _asInt(dynamic v, int fallback) {
    if (v is int) return v;
    if (v is String) return int.tryParse(v) ?? fallback;
    return fallback;
  }

  bool _asBool(dynamic v, bool fallback) {
    if (v is bool) return v;
    if (v is String) return v.toLowerCase() == 'true';
    if (v is num) return v != 0;
    return fallback;
  }

  void _showDeleteDialog() {
    showDialog(
      context: context,
      barrierColor: Colors.black54,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        title: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: const Color(0xFFE74C3C).withOpacity(0.1),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(
                Icons.warning_rounded,
                color: Color(0xFFE74C3C),
                size: 20,
              ),
            ),
            const SizedBox(width: 12),
            Text(
              '정말 삭제하시겠어요?',
              style: GoogleFonts.gaegu(
                fontWeight: FontWeight.bold,
                fontSize: 18,
              ),
            ),
          ],
        ),
        content: Text(
          '삭제한 식물 정보는 복구할 수 없어요.\n정말로 삭제하시겠습니까?',
          style: GoogleFonts.gaegu(fontSize: 16, height: 1.5),
        ),
        actions: [
          TextButton(
            child: Text(
              '취소',
              style: GoogleFonts.gaegu(color: Colors.grey[600]),
            ),
            onPressed: () => Navigator.pop(context),
          ),
          ElevatedButton(
            onPressed: _deletePlant,
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFFE74C3C),
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
            child: Text(
              '삭제하기',
              style: GoogleFonts.gaegu(fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _deletePlant() async {
    Navigator.pop(context);
    setState(() => _saving = true);

    final prefs = await SharedPreferences.getInstance();
    final accessToken = prefs.getString('accessToken');
    final dio = Dio(
      BaseOptions(
        baseUrl: AppConfig.apiBaseUrl,
        headers: {HttpHeaders.authorizationHeader: 'Bearer $accessToken'},
        connectTimeout: const Duration(seconds: 8),
        receiveTimeout: const Duration(seconds: 8),
      ),
    );

    try {
      await dio.delete('/api/user-plants/${widget.userPlantId}');
      if (mounted) {
        Navigator.pop(context, true);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '식물이 삭제되었습니다',
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
      }
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '삭제에 실패했습니다',
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
    }
    setState(() => _saving = false);
  }

  void _showFarmSearchModal() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => FarmSearchModal(
        farmApiService: farmApiService,
        onFarmSelected: (farm) {
          setState(() {
            _selectedGardenId = farm.gardenUniqueId;
            _locationController.text =
                '${farm.farmName ?? "-"} (${farm.roadNameAddress ?? farm.lotNumberAddress ?? ""})';
          });
          Navigator.pop(context);
        },
      ),
    );
  }

  void _showEditDialog() {
    showDialog(
      context: context,
      barrierColor: Colors.black54,
      builder: (context) => Dialog(
        insetPadding: const EdgeInsets.all(24),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        child: StatefulBuilder(
          builder: (context, setDialogState) => Container(
            constraints: BoxConstraints(
              maxHeight: MediaQuery.of(context).size.height * 0.85,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // 헤더
                Container(
                  padding: const EdgeInsets.all(24),
                  decoration: const BoxDecoration(
                    color: Color(0xFF2ECC71),
                    borderRadius: BorderRadius.vertical(
                      top: Radius.circular(24),
                    ),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.edit, color: Colors.white, size: 24),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          '식물 정보 수정',
                          style: GoogleFonts.gaegu(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                      ),
                      GestureDetector(
                        onTap: () => Navigator.pop(context),
                        child: Container(
                          width: 32,
                          height: 32,
                          decoration: BoxDecoration(
                            color: Colors.white.withOpacity(0.2),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Icon(
                            Icons.close,
                            color: Colors.white,
                            size: 18,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                // 내용
                Flexible(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      children: [
                        _buildEditField(
                          controller: _nicknameController,
                          label: '별명',
                          icon: Icons.favorite_outline,
                        ),
                        const SizedBox(height: 20),
                        Row(
                          children: [
                            Expanded(
                              child: _buildEditField(
                                controller: _locationController,
                                label: '장소',
                                icon: Icons.location_on_outlined,
                              ),
                            ),
                            const SizedBox(width: 12),
                            Container(
                              width: 56,
                              height: 56,
                              decoration: BoxDecoration(
                                gradient: const LinearGradient(
                                  colors: [
                                    Color(0xFF2ECC71),
                                    Color(0xFF27AE60),
                                  ],
                                  begin: Alignment.topLeft,
                                  end: Alignment.bottomRight,
                                ),
                                borderRadius: BorderRadius.circular(16),
                              ),
                              child: IconButton(
                                padding: EdgeInsets.zero, // ⬅️ 여백 제거
                                iconSize: 24,
                                onPressed: _showFarmSearchModal,
                                icon: const Icon(
                                  Icons.search,
                                  color: Colors.white,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 20),
                        _buildEditField(
                          controller: _notesController,
                          label: '메모',
                          icon: Icons.note_outlined,
                          maxLines: 3,
                        ),

                        const SizedBox(height: 24),

                        // ✅ 알림 & 주기 편집 블록
                        _buildScheduleEditor(setDialogState),

                        const SizedBox(height: 24),

                        // 사진 선택
                        GestureDetector(
                          onTap: () async {
                            final picked = await ImagePicker().pickImage(
                              source: ImageSource.gallery,
                            );
                            if (picked != null) {
                              setDialogState(() {
                                _imageFile = File(picked.path);
                              });
                            }
                          },
                          child: Container(
                            width: double.infinity,
                            padding: const EdgeInsets.all(20),
                            decoration: BoxDecoration(
                              color: const Color(0xFF2ECC71).withOpacity(0.1),
                              borderRadius: BorderRadius.circular(16),
                              border: Border.all(
                                color: const Color(0xFF2ECC71).withOpacity(0.3),
                              ),
                            ),
                            child: Column(
                              children: [
                                Container(
                                  width: 48,
                                  height: 48,
                                  decoration: BoxDecoration(
                                    color: const Color(
                                      0xFF2ECC71,
                                    ).withOpacity(0.2),
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                  child: const Icon(
                                    Icons.camera_alt,
                                    color: Color(0xFF2ECC71),
                                    size: 24,
                                  ),
                                ),
                                const SizedBox(height: 12),
                                Text(
                                  '새 사진 선택하기',
                                  style: GoogleFonts.gaegu(
                                    fontSize: 16,
                                    fontWeight: FontWeight.bold,
                                    color: const Color(0xFF2ECC71),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                        if (_imageFile != null) ...[
                          const SizedBox(height: 16),
                          Container(
                            width: double.infinity,
                            height: 120,
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(16),
                              border: Border.all(
                                color: const Color(0xFF2ECC71).withOpacity(0.3),
                              ),
                            ),
                            child: ClipRRect(
                              borderRadius: BorderRadius.circular(16),
                              child: Image.file(_imageFile!, fit: BoxFit.cover),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
                // 하단 버튼
                Container(
                  padding: const EdgeInsets.all(24),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.1),
                        blurRadius: 8,
                        offset: const Offset(0, -2),
                      ),
                    ],
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: TextButton(
                          onPressed: () => Navigator.pop(context),
                          child: Text(
                            '취소',
                            style: GoogleFonts.gaegu(
                              fontSize: 16,
                              color: Colors.grey[600],
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        flex: 2,
                        child: ElevatedButton(
                          onPressed: _saving ? null : _editPlant,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: const Color(0xFF2ECC71),
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(vertical: 16),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(12),
                            ),
                          ),
                          child: _saving
                              ? const SizedBox(
                                  width: 20,
                                  height: 20,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    valueColor: AlwaysStoppedAnimation<Color>(
                                      Colors.white,
                                    ),
                                  ),
                                )
                              : Text(
                                  '저장하기',
                                  style: GoogleFonts.gaegu(
                                    fontSize: 16,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildEditField({
    required TextEditingController controller,
    required String label,
    required IconData icon,
    int maxLines = 1,
  }) {
    final bool isMultiline = maxLines > 1;

    Widget _label() {
      return Row(
        children: [
          Icon(icon, color: const Color(0xFF2ECC71), size: 18),
          const SizedBox(width: 6),
          Text(
            label,
            style: GoogleFonts.gaegu(
              fontSize: 14,
              color: Colors.grey[700],
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _label(),
        const SizedBox(height: 8),
        Container(
          // 단일라인은 높이를 고정해 버튼(56)과 수평 정렬, 멀티라인은 자연스럽게 늘어남
          height: isMultiline ? null : 56,
          decoration: BoxDecoration(
            color: const Color(0xFFF8F9FA),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: const Color(0xFFE8ECEF)),
          ),
          child: TextFormField(
            controller: controller,
            maxLines: maxLines,
            decoration: InputDecoration(
              hintText: isMultiline ? '내용을 입력하세요' : '입력',
              hintStyle: GoogleFonts.gaegu(color: Colors.grey[500]),
              // ⬇️ labelText 제거! (겹치는 원인)
              labelText: null,
              // ⬇️ prefixIcon 제거하고, 위 라벨에서 아이콘 처리
              prefixIcon: null,
              isDense: true,
              filled: true,
              fillColor: const Color(0xFFF8F9FA),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: BorderSide.none,
              ),
              focusedBorder: OutlineInputBorder(
                borderRadius: BorderRadius.circular(16),
                borderSide: const BorderSide(
                  color: Color(0xFF2ECC71),
                  width: 2,
                ),
              ),
              contentPadding: EdgeInsets.symmetric(
                horizontal: 16,
                vertical: isMultiline ? 12 : 14,
              ),
            ),
            style: GoogleFonts.gaegu(fontSize: 16),
          ),
        ),
      ],
    );
  }

  // ✅ 알림 & 주기 편집 UI
  // ✅ 알림 & 주기 편집 UI (오버플로우 방지 버전)
  Widget _buildScheduleEditor(void Function(void Function()) setDialogState) {
    final intervals = const [1, 2, 3, 5, 7, 10, 14, 21, 30, 60];

    Widget drop({
      required int value,
      required void Function(int?) onChanged,
      required String label,
      required IconData icon,
    }) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: const Color(0xFF2ECC71), size: 18),
              const SizedBox(width: 6),
              Text(
                label,
                style: GoogleFonts.gaegu(
                  fontSize: 14,
                  color: Colors.grey[700],
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Container(
            height: 56,
            decoration: BoxDecoration(
              color: const Color(0xFFF8F9FA),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: const Color(0xFFE8ECEF)),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: DropdownButtonFormField<int>(
              value: value,
              isExpanded: true, // 좁은 화면에서 넘침 방지
              items: intervals
                  .map(
                    (d) => DropdownMenuItem<int>(
                      value: d,
                      child: Text('$d일', style: GoogleFonts.gaegu()),
                    ),
                  )
                  .toList(),
              onChanged: (v) => setDialogState(() => onChanged(v)),
              decoration: InputDecoration(
                // ⬇️ labelText 제거! (겹침 원천 차단)
                labelText: null,
                isDense: true,
                border: InputBorder.none,
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: 8,
                  vertical: 10,
                ),
              ),
            ),
          ),
        ],
      );
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        // ⬅️ 좁은 화면(예: 다이얼로그 내 작은 폭)에서는 세로 배치로 전환
        final isNarrow = constraints.maxWidth < 380;

        return Container(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(16),
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
                '알림 & 주기',
                style: GoogleFonts.gaegu(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 12),

              // 🔔 스위치 줄: 라벨 말줄임 + 스위치를 FittedBox로 감싸 가로폭 보호
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 12,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: const Color(0xFFF8F9FA),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.notifications_active_outlined,
                      color: Color(0xFF2ECC71),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        '알림 사용',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: GoogleFonts.gaegu(fontSize: 16),
                      ),
                    ),
                    // ⬇️ 스위치를 FittedBox로 감싸면 작은 폭에서도 잘 맞춰짐
                    FittedBox(
                      fit: BoxFit.scaleDown,
                      child: Switch(
                        value: _notificationEnabled,
                        onChanged: (v) =>
                            setDialogState(() => _notificationEnabled = v),
                        activeColor: const Color(0xFF2ECC71),
                        activeTrackColor: const Color(
                          0xFF2ECC71,
                        ).withOpacity(0.3),
                        inactiveThumbColor: Colors.grey[400],
                        inactiveTrackColor: Colors.grey[200],
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 12),

              // 💧 물 주기 / ✂️ 가지치기 : 좁으면 세로, 넓으면 가로
              if (!isNarrow) ...[
                Row(
                  children: [
                    Expanded(
                      child: drop(
                        value: _waterIntervalDays,
                        onChanged: (v) =>
                            _waterIntervalDays = v ?? _waterIntervalDays,
                        label: '물 주기',
                        icon: Icons.water_drop_outlined,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: drop(
                        value: _pruneIntervalDays,
                        onChanged: (v) =>
                            _pruneIntervalDays = v ?? _pruneIntervalDays,
                        label: '가지치기',
                        icon: Icons.content_cut_outlined,
                      ),
                    ),
                  ],
                ),
              ] else ...[
                drop(
                  value: _waterIntervalDays,
                  onChanged: (v) =>
                      _waterIntervalDays = v ?? _waterIntervalDays,
                  label: '물 주기',
                  icon: Icons.water_drop_outlined,
                ),
                const SizedBox(height: 8),
                drop(
                  value: _pruneIntervalDays,
                  onChanged: (v) =>
                      _pruneIntervalDays = v ?? _pruneIntervalDays,
                  label: '가지치기',
                  icon: Icons.content_cut_outlined,
                ),
              ],

              const SizedBox(height: 8),

              // 🌱 영양제 : 우측 공간 채우는 Expanded 제거하고 단독 배치(좁은 화면 호환)
              drop(
                value: _fertilizeIntervalDays,
                onChanged: (v) =>
                    _fertilizeIntervalDays = v ?? _fertilizeIntervalDays,
                label: '영양제',
                icon: Icons.eco_outlined,
              ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _editPlant() async {
    Navigator.pop(context);
    setState(() => _saving = true);

    final prefs = await SharedPreferences.getInstance();
    final accessToken = prefs.getString('accessToken');
    final dio = Dio(
      BaseOptions(
        baseUrl: AppConfig.apiBaseUrl,
        headers: {HttpHeaders.authorizationHeader: 'Bearer $accessToken'},
        connectTimeout: const Duration(seconds: 8),
        receiveTimeout: const Duration(seconds: 8),
      ),
    );

    final gardenId = _selectedGardenId ?? (_plant?.gardenUniqueId ?? 1);

    // ⭕ Map으로 안전하게 만들고, 키 이름은 isNotificationEnabled 로 통일
    final payload = {
      "plantName": _plant?.plantName ?? "",
      "plantNickname": _nicknameController.text.trim(),
      "plantingPlace": _locationController.text.trim(),
      "notes": _notesController.text.trim(),
      "gardenUniqueId": gardenId,
      "watered": _plant?.watered ?? false,
      "pruned": _plant?.pruned ?? false,
      "fertilized": _plant?.fertilized ?? false,
      "isNotificationEnabled": _notificationEnabled, // ← 여기!!
      "waterIntervalDays": _waterIntervalDays,
      "pruneIntervalDays": _pruneIntervalDays,
      "fertilizeIntervalDays": _fertilizeIntervalDays,
    };

    try {
      final formData = FormData();

      // data 파트 (application/json)
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

      // 파일 파트(선택)
      if (_imageFile != null) {
        final ext = _imageFile!.path.split('.').last.toLowerCase();
        final mime = (ext == 'png') ? 'png' : 'jpeg';

        formData.files.add(
          MapEntry(
            'file',
            await MultipartFile.fromFile(
              _imageFile!.path,
              filename: _imageFile!.path.split('/').last,
              contentType: MediaType('image', mime),
            ),
          ),
        );
      }

      await dio.put(
        '/api/user-plants/${widget.userPlantId}',
        data: formData,
        options: Options(contentType: 'multipart/form-data'),
      );

      if (mounted) {
        _fetchDetail();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '식물 정보가 수정되었습니다',
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
      }
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '수정에 실패했습니다',
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
      setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      body: _loading
          ? _buildLoadingState()
          : _error != null
          ? _buildErrorState()
          : _plant == null
          ? _buildNoDataState()
          : _buildPlantDetail(),
    );
  }

  Widget _buildLoadingState() {
    return const Scaffold(
      backgroundColor: Color(0xFFF8F9FA),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircularProgressIndicator(color: Color(0xFF2ECC71), strokeWidth: 3),
            SizedBox(height: 24),
            Text(
              '식물 정보를 불러오고 있어요...',
              style: TextStyle(fontSize: 16, color: Colors.grey),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildErrorState() {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      appBar: AppBar(
        backgroundColor: const Color(0xFF2ECC71),
        foregroundColor: Colors.white,
        elevation: 0,
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                color: const Color(0xFFE74C3C).withOpacity(0.1),
                borderRadius: BorderRadius.circular(24),
              ),
              child: const Icon(
                Icons.error_outline,
                size: 40,
                color: Color(0xFFE74C3C),
              ),
            ),
            const SizedBox(height: 24),
            Text(
              _error!,
              style: GoogleFonts.gaegu(
                fontSize: 18,
                color: const Color(0xFFE74C3C),
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _fetchDetail,
              style: ElevatedButton.styleFrom(
                backgroundColor: const Color(0xFF2ECC71),
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              child: Text(
                '다시 시도',
                style: GoogleFonts.gaegu(fontWeight: FontWeight.bold),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNoDataState() {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      appBar: AppBar(
        backgroundColor: const Color(0xFF2ECC71),
        foregroundColor: Colors.white,
        elevation: 0,
      ),
      body: const Center(child: Text('데이터가 없습니다.')),
    );
  }

  Widget _buildPlantDetail() {
    return CustomScrollView(
      slivers: [
        _buildSliverAppBar(),
        SliverToBoxAdapter(
          child: AnimatedBuilder(
            animation: _fadeController,
            builder: (context, child) {
              return FadeTransition(
                opacity: _fadeController,
                child: _buildContent(),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildSliverAppBar() {
    return SliverAppBar(
      expandedHeight: 300,
      pinned: true,
      backgroundColor: const Color(0xFF2ECC71),
      foregroundColor: Colors.white,
      elevation: 0,
      flexibleSpace: FlexibleSpaceBar(
        background: Stack(
          children: [
            Container(
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  colors: [Color(0xFF2ECC71), Color(0xFF27AE60)],
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                ),
              ),
            ),
            Container(
              margin: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(24),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.2),
                    blurRadius: 20,
                    offset: const Offset(0, 8),
                  ),
                ],
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(24),
                child: _plant!.userPlantImageUrl != null
                    ? Image.network(
                        _plant!.userPlantImageUrl!,
                        width: double.infinity,
                        height: double.infinity,
                        fit: BoxFit.cover,
                        errorBuilder: (c, e, s) => Container(
                          color: Colors.white,
                          child: const Icon(
                            Icons.eco,
                            size: 80,
                            color: Color(0xFF2ECC71),
                          ),
                        ),
                      )
                    : Container(
                        color: Colors.white,
                        child: const Icon(
                          Icons.eco,
                          size: 80,
                          color: Color(0xFF2ECC71),
                        ),
                      ),
              ),
            ),
          ],
        ),
      ),
      actions: [
        Container(
          width: 40,
          height: 40,
          margin: const EdgeInsets.only(right: 8),
          decoration: BoxDecoration(
            color: Colors.white.withOpacity(0.2),
            borderRadius: BorderRadius.circular(12),
          ),
          child: IconButton(
            icon: const Icon(Icons.edit, size: 20),
            onPressed: _showEditDialog,
            tooltip: '수정',
          ),
        ),
        Container(
          width: 40,
          height: 40,
          margin: const EdgeInsets.only(right: 8),
          decoration: BoxDecoration(
            color: Colors.white.withOpacity(0.2),
            borderRadius: BorderRadius.circular(12),
          ),
          child: IconButton(
            icon: const Icon(Icons.delete, size: 20),
            onPressed: _showDeleteDialog,
            tooltip: '삭제',
          ),
        ),
      ],
    );
  }

  Widget _buildContent() {
    return AnimatedBuilder(
      animation: _slideController,
      builder: (context, child) {
        return SlideTransition(
          position: Tween<Offset>(begin: const Offset(0, 0.3), end: Offset.zero)
              .animate(
                CurvedAnimation(
                  parent: _slideController,
                  curve: Curves.easeInOut,
                ),
              ),
          child: Container(
            margin: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildMainInfo(),
                const SizedBox(height: 24),
                _buildDetailCards(),
                const SizedBox(height: 24),
                _buildScheduleSummary(), // ✅ 알림 & 주기 요약
                const SizedBox(height: 24),
                if (_plant!.plantImageUrl != null) _buildPlantInfoImage(),
                const SizedBox(height: 100), // Bottom padding
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildMainInfo() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(24),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 20,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [Color(0xFF2ECC71), Color(0xFF27AE60)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(16),
                ),
                child: const Icon(
                  Icons.favorite,
                  color: Colors.white,
                  size: 24,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _plant!.plantNickname ?? '이름 없음',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: GoogleFonts.gaegu(
                        fontSize: 28,
                        fontWeight: FontWeight.bold,
                        color: const Color(0xFF1A1A1A),
                      ),
                    ),
                    Text(
                      _plant!.plantName ?? '식물 정보 없음',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: GoogleFonts.gaegu(
                        fontSize: 18,
                        color: const Color(0xFF2ECC71),
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          if (_plant!.plantingPlace != null) ...[
            Row(
              children: [
                Expanded(
                  // ⬅️ 바깥 Row에 폭 제한
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 8,
                    ),
                    decoration: BoxDecoration(
                      color: const Color(0xFF2ECC71).withOpacity(0.1),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Row(
                      children: [
                        const Icon(
                          Icons.location_on,
                          size: 16,
                          color: Color(0xFF2ECC71),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          // ⬅️ 텍스트가 남는 폭만 사용
                          child: Text(
                            _plant!.plantingPlace!,
                            maxLines: 1, // ⬅️ 한 줄 제한
                            overflow: TextOverflow.ellipsis, // ⬅️ 넘치면 … 처리
                            style: GoogleFonts.gaegu(
                              fontSize: 14,
                              color: const Color(0xFF2ECC71),
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ],
          if (_plant!.plantedDate != null) ...[
            const SizedBox(height: 12),
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    color: const Color(0xFF3498DB).withOpacity(0.1),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(
                        Icons.calendar_today,
                        size: 16,
                        color: Color(0xFF3498DB),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        '심은 날: ${_plant!.plantedDate!.split('T').first}',
                        style: GoogleFonts.gaegu(
                          fontSize: 14,
                          color: const Color(0xFF3498DB),
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildDetailCards() {
    final details = [
      if (_plant!.plantEnglishName != null)
        {
          'label': '영문명',
          'value': _plant!.plantEnglishName!,
          'icon': Icons.language,
        },
      if (_plant!.species != null)
        {'label': '품종', 'value': _plant!.species!, 'icon': Icons.category},
      if (_plant!.season != null)
        {'label': '계절', 'value': _plant!.season!, 'icon': Icons.wb_sunny},
    ];

    if (details.isEmpty && (_plant!.notes == null || _plant!.notes!.isEmpty)) {
      return const SizedBox.shrink();
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (details.isNotEmpty) ...[
          Text(
            '상세 정보',
            style: GoogleFonts.gaegu(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: const Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 16),
          ...details.map(
            (detail) => Container(
              margin: const EdgeInsets.only(bottom: 12),
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
              child: Row(
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: const Color(0xFF2ECC71).withOpacity(0.1),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      detail['icon'] as IconData,
                      color: const Color(0xFF2ECC71),
                      size: 20,
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          detail['label'] as String,
                          style: GoogleFonts.gaegu(
                            fontSize: 14,
                            color: Colors.grey[600],
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          detail['value'] as String,
                          style: GoogleFonts.gaegu(
                            fontSize: 16,
                            fontWeight: FontWeight.bold,
                            color: const Color(0xFF1A1A1A),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
        if (_plant!.notes != null && _plant!.notes!.isNotEmpty) ...[
          const SizedBox(height: 24),
          Text(
            '나만의 메모',
            style: GoogleFonts.gaegu(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: const Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 16),
          Container(
            width: double.infinity,
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
                Row(
                  children: [
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        color: const Color(0xFFE67E22).withOpacity(0.1),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(
                        Icons.note_outlined,
                        color: Color(0xFFE67E22),
                        size: 20,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Text(
                      '기록',
                      style: GoogleFonts.gaegu(
                        fontSize: 16,
                        color: Colors.grey[600],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                Text(
                  _plant!.notes!,
                  style: GoogleFonts.gaegu(
                    fontSize: 16,
                    color: const Color(0xFF1A1A1A),
                    height: 1.6,
                  ),
                ),
              ],
            ),
          ),
        ],
      ],
    );
  }

  // ✅ 알림 & 주기 요약 섹션 (상세 화면에서 보여줌)
  Widget _buildScheduleSummary() {
    List<Widget> chips = [
      _chip(
        '알림 ${_notificationEnabled ? "켜짐" : "꺼짐"}',
        on: _notificationEnabled,
      ),
      _chip('물 ${_waterIntervalDays}일', on: true),
      _chip('가지치기 ${_pruneIntervalDays}일', on: true),
      _chip('영양제 ${_fertilizeIntervalDays}일', on: true),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '알림 & 주기',
          style: GoogleFonts.gaegu(
            fontSize: 22,
            fontWeight: FontWeight.bold,
            color: const Color(0xFF1A1A1A),
          ),
        ),
        const SizedBox(height: 12),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(16),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.05),
                blurRadius: 12,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Wrap(spacing: 8, runSpacing: 8, children: chips),
        ),
      ],
    );
  }

  Widget _chip(String text, {required bool on}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: on
            ? const Color(0xFF2ECC71).withOpacity(0.12)
            : Colors.grey[100],
        borderRadius: BorderRadius.circular(999),
        border: Border.all(
          color: on
              ? const Color(0xFF2ECC71).withOpacity(0.4)
              : Colors.grey[300]!,
        ),
      ),
      child: Text(
        text,
        style: GoogleFonts.gaegu(
          fontSize: 13,
          fontWeight: FontWeight.bold,
          color: on ? const Color(0xFF2ECC71) : Colors.grey[600],
        ),
      ),
    );
  }

  Widget _buildPlantInfoImage() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '식물 정보 사진',
          style: GoogleFonts.gaegu(
            fontSize: 22,
            fontWeight: FontWeight.bold,
            color: const Color(0xFF1A1A1A),
          ),
        ),
        const SizedBox(height: 16),
        Container(
          width: double.infinity,
          height: 200,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.1),
                blurRadius: 12,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(20),
            child: Image.network(
              _plant!.plantImageUrl!,
              fit: BoxFit.cover,
              errorBuilder: (c, e, s) => Container(
                color: Colors.grey[200],
                child: Icon(
                  Icons.image_not_supported,
                  size: 60,
                  color: Colors.grey[400],
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
