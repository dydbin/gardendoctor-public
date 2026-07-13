import 'dart:io';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:image_picker/image_picker.dart';
import 'package:dio/dio.dart';

import '../config/app_config.dart';
import '../services/farm_api_service.dart';
import '../services/dio_interceptor.dart';
import '../models/farm_model.dart';
import 'confirmation_screen.dart';

/// DB에서 '기타(Other)' 텃밭의 gardenUniqueId
const int kOtherFarmId = 1;

class PlantSample {
  final String name;
  final String imagePath;
  PlantSample({required this.name, required this.imagePath});
}

class RegisterPlantScreen extends StatefulWidget {
  const RegisterPlantScreen({super.key});

  @override
  State<RegisterPlantScreen> createState() => _RegisterPlantScreenState();
}

class _RegisterPlantScreenState extends State<RegisterPlantScreen>
    with TickerProviderStateMixin {
  final TextEditingController _customPlantController = TextEditingController();
  final TextEditingController _nicknameController = TextEditingController();
  final TextEditingController _locationController = TextEditingController();
  final TextEditingController _notesController = TextEditingController();
  final ImagePicker _picker = ImagePicker();

  String? _selectedPlantName;
  String? _customPlantName;
  XFile? _imageFile;
  int? _selectedGardenId; // 텃밭 검색 선택 시에만 세팅

  // 오늘 한 일
  bool _watered = false;
  bool _pruned = false;
  bool _fertilized = false;

  // ✅ 새로 추가: 알림/주기 설정 상태
  bool _isNotificationEnabled = true;
  int _waterIntervalDays = 3;
  int _pruneIntervalDays = 14;
  int _fertilizeIntervalDays = 30;

  late final FarmApiService farmApiService;
  late AnimationController _stepAnimationController;
  late AnimationController _progressController;

  int _currentStep = 0;
  // ✅ 스텝 수 +1 (알림/주기 스텝 추가)
  // 구성: 0식물선택, 1별명, 2장소, 3오늘한일, 4알림/주기, 5메모, 6사진
  final int _totalSteps = 7;

  final List<PlantSample> _plantSamples = [
    PlantSample(name: '상추', imagePath: 'assets/images/상추.png'),
    PlantSample(name: '토마토', imagePath: 'assets/images/토마토.png'),
    PlantSample(name: '고추', imagePath: 'assets/images/고추.png'),
    PlantSample(name: '포도', imagePath: 'assets/images/포도.png'),
    PlantSample(name: '오이', imagePath: 'assets/images/오이.png'),
    PlantSample(name: '가지', imagePath: 'assets/images/가지.png'),
    PlantSample(name: '단호박', imagePath: 'assets/images/단호박.png'),
    PlantSample(name: '애호박', imagePath: 'assets/images/애호박.png'),
    PlantSample(name: '쥬키니 호박', imagePath: 'assets/images/쥬키니호박.png'),
    PlantSample(name: '딸기', imagePath: 'assets/images/딸기.png'),
    PlantSample(name: '수박', imagePath: 'assets/images/수박.png'),
    PlantSample(name: '참외', imagePath: 'assets/images/참외.png'),
  ];

  @override
  void initState() {
    super.initState();
    _stepAnimationController = AnimationController(
      duration: const Duration(milliseconds: 400),
      vsync: this,
    );
    _progressController = AnimationController(
      duration: const Duration(milliseconds: 600),
      vsync: this,
    );

    final dio = Dio(
      BaseOptions(
        baseUrl: AppConfig.apiBaseUrl,
        connectTimeout: const Duration(seconds: 8),
        receiveTimeout: const Duration(seconds: 8),
      ),
    );
    dio.interceptors.add(DioInterceptor());
    farmApiService = FarmApiService(dio);

    _stepAnimationController.forward();
  }

  @override
  void dispose() {
    _customPlantController.dispose();
    _nicknameController.dispose();
    _locationController.dispose();
    _notesController.dispose();
    _stepAnimationController.dispose();
    _progressController.dispose();
    super.dispose();
  }

  void _nextStep() {
    if (_currentStep < _totalSteps - 1) {
      setState(() => _currentStep++);
      _stepAnimationController.reset();
      _stepAnimationController.forward();
      _progressController.animateTo((_currentStep + 1) / _totalSteps);
    }
  }

  void _previousStep() {
    if (_currentStep > 0) {
      setState(() => _currentStep--);
      _stepAnimationController.reset();
      _stepAnimationController.forward();
      _progressController.animateTo((_currentStep + 1) / _totalSteps);
    }
  }

  bool _canProceedFromCurrentStep() {
    switch (_currentStep) {
      case 0:
        return (_selectedPlantName != null || _customPlantName != null);
      case 1:
        return _nicknameController.text.trim().isNotEmpty;
      case 2:
        // 장소 텍스트만 있어도 통과 (텃밭 선택은 선택사항)
        return _locationController.text.trim().isNotEmpty;
      case 3:
        return true; // 오늘 한 일
      case 4:
        return true; // 알림/주기
      case 5:
        return true; // 메모
      case 6:
        return true; // 사진
      default:
        return false;
    }
  }

  void _navigateToConfirmation() {
    final plantType = _selectedPlantName ?? _customPlantName;
    if (plantType == null || plantType.trim().isEmpty) {
      _showErrorSnackBar('식물 종류를 선택해주세요!');
      return;
    }
    if (_nicknameController.text.trim().isEmpty) {
      _showErrorSnackBar('별명을 입력해주세요!');
      return;
    }
    if (_locationController.text.trim().isEmpty) {
      _showErrorSnackBar('키우는 장소를 입력해주세요!');
      return;
    }

    // 텃밭을 선택 안 했다면 '기타(Other)' ID로 매핑
    final int gardenIdToUse = _selectedGardenId ?? kOtherFarmId;

    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ConfirmationScreen(
          plantType: plantType,
          nickname: _nicknameController.text,
          location: _locationController.text,
          notes: _notesController.text,
          imageFile: _imageFile,
          gardenUniqueId: gardenIdToUse,
          // 오늘 한 일
          watered: _watered,
          pruned: _pruned,
          fertilized: _fertilized,
          // ✅ 알림/주기 설정 전달
          isNotificationEnabled: _isNotificationEnabled,
          waterIntervalDays: _waterIntervalDays,
          pruneIntervalDays: _pruneIntervalDays,
          fertilizeIntervalDays: _fertilizeIntervalDays,
        ),
      ),
    );
  }

  void _showErrorSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message, style: GoogleFonts.gaegu(color: Colors.white)),
        backgroundColor: const Color(0xFFE74C3C),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        margin: const EdgeInsets.all(16),
      ),
    );
  }

  Future<void> _pickImage() async {
    final pickedFile = await _picker.pickImage(
      source: ImageSource.gallery,
      imageQuality: 85,
    );
    if (pickedFile != null) {
      setState(() => _imageFile = pickedFile);
    }
  }

  void _showCustomPlantDialog() {
    showDialog(
      context: context,
      barrierColor: Colors.black54,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
        title: Text(
          '직접 입력하기',
          style: GoogleFonts.gaegu(fontWeight: FontWeight.bold, fontSize: 20),
        ),
        content: TextFormField(
          controller: _customPlantController,
          decoration: InputDecoration(
            hintText: '예: 래디쉬, 바질 등',
            hintStyle: GoogleFonts.gaegu(color: Colors.grey[400]),
            filled: true,
            fillColor: const Color(0xFFF8F9FA),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(16),
              borderSide: BorderSide.none,
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(16),
              borderSide: const BorderSide(color: Color(0xFF2ECC71), width: 2),
            ),
            contentPadding: const EdgeInsets.all(16),
          ),
          style: GoogleFonts.gaegu(fontSize: 16),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('취소', style: GoogleFonts.gaegu(color: Colors.grey)),
          ),
          ElevatedButton(
            onPressed: () {
              if (_customPlantController.text.isNotEmpty) {
                setState(() {
                  _customPlantName = _customPlantController.text;
                  _selectedPlantName = null;
                });
                Navigator.pop(context);
              }
            },
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF2ECC71),
              foregroundColor: Colors.white,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
            child: Text('확인', style: GoogleFonts.gaegu()),
          ),
        ],
      ),
    );
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      // ✅ 키보드 오버플로우 문제 해결을 위해 resizeToAvoidBottomInset 추가
      resizeToAvoidBottomInset: true,
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(),
            _buildProgressBar(),
            Expanded(
              child: PageView(
                physics: const NeverScrollableScrollPhysics(),
                children: [_buildStepContent(_currentStep)],
              ),
            ),
            _buildBottomNavigation(),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      child: Row(
        children: [
          if (_currentStep > 0)
            GestureDetector(
              onTap: _previousStep,
              child: Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(12),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.05),
                      blurRadius: 8,
                      offset: const Offset(0, 2),
                    ),
                  ],
                ),
                child: const Icon(Icons.arrow_back_ios_new, size: 18),
              ),
            ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '식물 등록',
                  style: GoogleFonts.gaegu(
                    fontSize: 28,
                    fontWeight: FontWeight.bold,
                    color: const Color(0xFF1A1A1A),
                  ),
                ),
                Text(
                  '단계 ${_currentStep + 1} / $_totalSteps',
                  style: GoogleFonts.gaegu(
                    fontSize: 16,
                    color: Colors.grey[600],
                  ),
                ),
              ],
            ),
          ),
          GestureDetector(
            onTap: () => Navigator.pop(context),
            child: Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(12),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.05),
                    blurRadius: 8,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: const Icon(Icons.close, size: 18),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildProgressBar() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
      child: AnimatedBuilder(
        animation: _progressController,
        builder: (context, child) {
          return LinearProgressIndicator(
            value: (_currentStep + 1) / _totalSteps,
            backgroundColor: Colors.grey[200],
            valueColor: const AlwaysStoppedAnimation<Color>(Color(0xFF2ECC71)),
            minHeight: 4,
            borderRadius: BorderRadius.circular(2),
          );
        },
      ),
    );
  }

  Widget _buildStepContent(int step) {
    return AnimatedBuilder(
      animation: _stepAnimationController,
      builder: (context, child) {
        return SlideTransition(
          position:
              Tween<Offset>(
                begin: const Offset(1.0, 0.0),
                end: Offset.zero,
              ).animate(
                CurvedAnimation(
                  parent: _stepAnimationController,
                  curve: Curves.easeInOut,
                ),
              ),
          child: FadeTransition(
            opacity: _stepAnimationController,
            child: _getStepWidget(step),
          ),
        );
      },
    );
  }

  Widget _getStepWidget(int step) {
    switch (step) {
      case 0:
        return _buildPlantSelectionStep();
      case 1:
        return _buildNicknameStep();
      case 2:
        return _buildLocationStep();
      case 3:
        return _buildTodayTasksStep();
      case 4:
        return _buildNotificationStep(); // ✅ 추가된 스텝
      case 5:
        return _buildNotesStep();
      case 6:
        return _buildPhotoStep();
      default:
        return Container();
    }
  }

  Widget _buildPlantSelectionStep() {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '어떤 식물을 키우시나요? 🌱',
            style: GoogleFonts.gaegu(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: const Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '키우실 식물을 선택하거나 직접 입력해주세요',
            style: GoogleFonts.gaegu(fontSize: 16, color: Colors.grey[600]),
          ),
          const SizedBox(height: 32),
          if (_customPlantName != null) ...[
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFF2ECC71).withOpacity(0.1),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: const Color(0xFF2ECC71).withOpacity(0.3),
                ),
              ),
              child: Row(
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: const Color(0xFF2ECC71),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(Icons.eco, color: Colors.white, size: 20),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      _customPlantName!,
                      style: GoogleFonts.gaegu(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: const Color(0xFF2ECC71),
                      ),
                    ),
                  ),
                  GestureDetector(
                    onTap: () => setState(() => _customPlantName = null),
                    child: Container(
                      width: 28,
                      height: 28,
                      decoration: BoxDecoration(
                        color: Colors.grey[200],
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Icon(Icons.close, size: 16),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),
          ],
          Expanded(
            child: GridView.builder(
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 3,
                crossAxisSpacing: 16,
                mainAxisSpacing: 16,
                childAspectRatio: 0.85,
              ),
              itemCount: _plantSamples.length + 1,
              itemBuilder: (context, index) {
                if (index == 0) {
                  return _buildCustomPlantCard();
                }
                return _buildPlantCard(_plantSamples[index - 1]);
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCustomPlantCard() {
    return GestureDetector(
      onTap: _showCustomPlantDialog,
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: Colors.grey[200]!, width: 1.5),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.05),
              blurRadius: 8,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: const Color(0xFF2ECC71).withOpacity(0.1),
                borderRadius: BorderRadius.circular(16),
              ),
              child: const Icon(
                Icons.add_rounded,
                color: Color(0xFF2ECC71),
                size: 24,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              '직접 입력',
              style: GoogleFonts.gaegu(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: const Color(0xFF2ECC71),
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPlantCard(PlantSample plant) {
    final isSelected = _selectedPlantName == plant.name;
    return GestureDetector(
      onTap: () => setState(() {
        _selectedPlantName = isSelected ? null : plant.name;
        _customPlantName = null;
      }),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: isSelected ? const Color(0xFF2ECC71) : Colors.grey[200]!,
            width: isSelected ? 2.5 : 1.5,
          ),
          boxShadow: [
            BoxShadow(
              color: isSelected
                  ? const Color(0xFF2ECC71).withOpacity(0.15)
                  : Colors.black.withOpacity(0.05),
              blurRadius: isSelected ? 12 : 8,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: isSelected
                    ? const Color(0xFF2ECC71).withOpacity(0.1)
                    : Colors.grey[50],
                borderRadius: BorderRadius.circular(16),
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(16),
                child: Image.asset(
                  plant.imagePath,
                  width: 32,
                  height: 32,
                  fit: BoxFit.cover,
                  errorBuilder: (c, e, s) => Icon(
                    Icons.eco,
                    color: isSelected ? const Color(0xFF2ECC71) : Colors.grey,
                    size: 24,
                  ),
                ),
              ),
            ),
            const SizedBox(height: 12),
            Text(
              plant.name,
              style: GoogleFonts.gaegu(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: isSelected ? const Color(0xFF2ECC71) : Colors.black87,
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNicknameStep() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '특별한 이름을 지어주세요 ✨',
            style: GoogleFonts.gaegu(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: const Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '식물에게 애정을 담은 별명을 지어주세요',
            style: GoogleFonts.gaegu(fontSize: 16, color: Colors.grey[600]),
          ),
          const SizedBox(height: 48),
          Container(
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
            child: TextFormField(
              controller: _nicknameController,
              decoration: InputDecoration(
                hintText: '예: 토순이, 우리집 상추 등',
                hintStyle: GoogleFonts.gaegu(color: Colors.grey[400]),
                filled: true,
                fillColor: Colors.white,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(20),
                  borderSide: BorderSide.none,
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(20),
                  borderSide: const BorderSide(
                    color: Color(0xFF2ECC71),
                    width: 2,
                  ),
                ),
                contentPadding: const EdgeInsets.all(24),
                prefixIcon: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Icon(Icons.favorite_outline, color: Colors.grey[400]),
                ),
              ),
              style: GoogleFonts.gaegu(fontSize: 18),
              onChanged: (value) => setState(() {}),
            ),
          ),
        ],
      ),
    );
  }

  // ✅ 키보드 오버플로우 해결을 위해 SingleChildScrollView 추가
  Widget _buildLocationStep() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '어디서 키우시나요? 🏡',
            style: GoogleFonts.gaegu(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: const Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '식물이 자랄 특별한 장소를 알려주세요',
            style: GoogleFonts.gaegu(fontSize: 16, color: Colors.grey[600]),
          ),
          const SizedBox(height: 32),
          Container(
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
                Expanded(
                  child: TextFormField(
                    controller: _locationController,
                    decoration: InputDecoration(
                      hintText: '예: 베란다, 창고, 옥상 등',
                      hintStyle: GoogleFonts.gaegu(
                        color: Colors.grey[400],
                        fontSize: 16,
                      ),
                      filled: true,
                      fillColor: Colors.white,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(20),
                        borderSide: BorderSide.none,
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(20),
                        borderSide: const BorderSide(
                          color: Color(0xFF2ECC71),
                          width: 2,
                        ),
                      ),
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 20,
                        vertical: 18,
                      ),
                      prefixIcon: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Icon(
                          Icons.home_outlined,
                          color: Colors.grey[400],
                        ),
                      ),
                    ),
                    style: GoogleFonts.gaegu(fontSize: 16),
                    onChanged: (value) => setState(() {}),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: GestureDetector(
                    onTap: _showFarmSearchModal,
                    child: Container(
                      width: 52,
                      height: 52,
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          colors: [Color(0xFF2ECC71), Color(0xFF27AE60)],
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                        ),
                        borderRadius: BorderRadius.circular(16),
                        boxShadow: [
                          BoxShadow(
                            color: const Color(0xFF2ECC71).withOpacity(0.3),
                            blurRadius: 8,
                            offset: const Offset(0, 4),
                          ),
                        ],
                      ),
                      child: const Icon(
                        Icons.search_rounded,
                        color: Colors.white,
                        size: 22,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [
                  const Color(0xFF2ECC71).withOpacity(0.08),
                  const Color(0xFF27AE60).withOpacity(0.04),
                ],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: const Color(0xFF2ECC71).withOpacity(0.15),
              ),
            ),
            child: Row(
              children: [
                Container(
                  width: 36,
                  height: 36,
                  decoration: BoxDecoration(
                    color: const Color(0xFF2ECC71).withOpacity(0.15),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(
                    Icons.lightbulb_outline,
                    color: Color(0xFF2ECC71),
                    size: 18,
                  ),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Text(
                    '텃밭에서 키우신다면 검색 버튼을 눌러 등록된 텃밭을 찾아보세요!',
                    style: GoogleFonts.gaegu(
                      fontSize: 13,
                      color: const Color(0xFF27AE60),
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTodayTasksStep() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '오늘 해주신 일이 있나요? 🌿',
            style: GoogleFonts.gaegu(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: const Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '등록 시점에 이미 완료한 항목들을 체크해주세요',
            style: GoogleFonts.gaegu(fontSize: 16, color: Colors.grey[600]),
          ),
          const SizedBox(height: 32),
          _buildTaskCard(
            icon: Icons.water_drop_outlined,
            title: '물 주기',
            subtitle: '식물에게 물을 주셨나요?',
            value: _watered,
            onChanged: (v) => setState(() => _watered = v),
          ),
          const SizedBox(height: 16),
          _buildTaskCard(
            icon: Icons.content_cut_outlined,
            title: '가지치기',
            subtitle: '불필요한 가지를 제거하셨나요?',
            value: _pruned,
            onChanged: (v) => setState(() => _pruned = v),
          ),
          const SizedBox(height: 16),
          _buildTaskCard(
            icon: Icons.eco_outlined,
            title: '영양제 주기',
            subtitle: '영양제나 비료를 주셨나요?',
            value: _fertilized,
            onChanged: (v) => setState(() => _fertilized = v),
          ),
        ],
      ),
    );
  }

  Widget _buildTaskCard({
    required IconData icon,
    required String title,
    required String subtitle,
    required bool value,
    required ValueChanged<bool> onChanged,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: value
              ? const Color(0xFF2ECC71).withOpacity(0.3)
              : Colors.grey[200]!,
          width: value ? 2 : 1,
        ),
        boxShadow: [
          BoxShadow(
            color: value
                ? const Color(0xFF2ECC71).withOpacity(0.1)
                : Colors.black.withOpacity(0.05),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: value
                    ? const Color(0xFF2ECC71).withOpacity(0.2)
                    : Colors.grey[100],
                borderRadius: BorderRadius.circular(16),
              ),
              child: Icon(
                icon,
                color: value ? const Color(0xFF2ECC71) : Colors.grey[400],
                size: 24,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
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
                  const SizedBox(height: 4),
                  Text(
                    subtitle,
                    style: GoogleFonts.gaegu(
                      fontSize: 14,
                      color: Colors.grey[600],
                    ),
                  ),
                ],
              ),
            ),
            Switch(
              value: value,
              onChanged: onChanged,
              activeColor: const Color(0xFF2ECC71),
              activeTrackColor: const Color(0xFF2ECC71).withOpacity(0.3),
              inactiveThumbColor: Colors.grey[400],
              inactiveTrackColor: Colors.grey[200],
            ),
          ],
        ),
      ),
    );
  }

  // ✅ 키보드 오버플로우 해결을 위해 SingleChildScrollView 추가 및 UI 개선
  Widget _buildNotificationStep() {
    final chipTextStyle = GoogleFonts.gaegu(
      fontSize: 14,
      fontWeight: FontWeight.bold,
    );

    Widget buildChipRow(
      String title,
      int value,
      List<int> options,
      ValueChanged<int> onSelected,
    ) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: GoogleFonts.gaegu(
              fontSize: 16,
              color: Colors.grey[700],
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: options.map((d) {
              final selected = d == value;
              return ChoiceChip(
                label: Text(
                  '${d}일',
                  style: chipTextStyle.copyWith(
                    color: selected ? Colors.white : const Color(0xFF2ECC71),
                  ),
                ),
                selected: selected,
                onSelected: (_) => setState(() => onSelected(d)),
                selectedColor: const Color(0xFF2ECC71),
                backgroundColor: const Color(0xFF2ECC71).withOpacity(0.12),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
                materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                side: BorderSide(
                  color: selected
                      ? Colors.transparent
                      : const Color(0xFF2ECC71).withOpacity(0.3),
                ),
              );
            }).toList(),
          ),
        ],
      );
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '알림과 주기를 설정해요 ⏰',
            style: GoogleFonts.gaegu(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: const Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '원하지 않으면 알림을 꺼둘 수도 있어요',
            style: GoogleFonts.gaegu(fontSize: 16, color: Colors.grey[600]),
          ),
          const SizedBox(height: 32),

          // 알림 토글
          Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(
                color: _isNotificationEnabled
                    ? const Color(0xFF2ECC71).withOpacity(0.3)
                    : Colors.grey[200]!,
                width: _isNotificationEnabled ? 2 : 1,
              ),
              boxShadow: [
                BoxShadow(
                  color: _isNotificationEnabled
                      ? const Color(0xFF2ECC71).withOpacity(0.1)
                      : Colors.black.withOpacity(0.05),
                  blurRadius: 12,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: _isNotificationEnabled
                        ? const Color(0xFF2ECC71).withOpacity(0.15)
                        : Colors.grey[100],
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Icon(
                    Icons.notifications_active_outlined,
                    color: _isNotificationEnabled
                        ? const Color(0xFF2ECC71)
                        : Colors.grey[400],
                    size: 24,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '알림 받기',
                        style: GoogleFonts.gaegu(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      Text(
                        '설정한 주기에 따라 알림을 보내드려요',
                        style: GoogleFonts.gaegu(
                          fontSize: 13,
                          color: Colors.grey[600],
                        ),
                      ),
                    ],
                  ),
                ),
                Switch(
                  value: _isNotificationEnabled,
                  onChanged: (v) => setState(() => _isNotificationEnabled = v),
                  activeColor: const Color(0xFF2ECC71),
                  activeTrackColor: const Color(0xFF2ECC71).withOpacity(0.3),
                ),
              ],
            ),
          ),

          const SizedBox(height: 24),

          // 주기 선택(물/가지치기/영양제)
          Container(
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
                      width: 32,
                      height: 32,
                      decoration: BoxDecoration(
                        color: const Color(0xFF2ECC71).withOpacity(0.15),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Icon(
                        Icons.schedule,
                        color: Color(0xFF2ECC71),
                        size: 18,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Text(
                      '관리 주기 설정',
                      style: GoogleFonts.gaegu(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 20),
                buildChipRow('💧 물 주기', _waterIntervalDays, const [
                  1,
                  2,
                  3,
                  4,
                  5,
                  7,
                ], (d) => _waterIntervalDays = d),
                const SizedBox(height: 18),
                buildChipRow('✂️ 가지치기', _pruneIntervalDays, const [
                  7,
                  14,
                  21,
                  28,
                ], (d) => _pruneIntervalDays = d),
                const SizedBox(height: 18),
                buildChipRow('🌱 영양제', _fertilizeIntervalDays, const [
                  14,
                  30,
                  45,
                  60,
                ], (d) => _fertilizeIntervalDays = d),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ✅ 키보드 오버플로우 해결을 위해 SingleChildScrollView 추가
  Widget _buildNotesStep() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '특별한 기록을 남겨보세요 📝',
            style: GoogleFonts.gaegu(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: const Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '식물에 대한 메모나 특별한 이야기가 있다면 적어주세요',
            style: GoogleFonts.gaegu(fontSize: 16, color: Colors.grey[600]),
          ),
          const SizedBox(height: 20),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFF3498DB).withOpacity(0.08),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: const Color(0xFF3498DB).withOpacity(0.15),
              ),
            ),
            child: Row(
              children: [
                Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: const Color(0xFF3498DB).withOpacity(0.15),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(
                    Icons.info_outline,
                    color: Color(0xFF3498DB),
                    size: 18,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    '선택사항이에요. 건너뛰어도 괜찮습니다!',
                    style: GoogleFonts.gaegu(
                      fontSize: 14,
                      color: const Color(0xFF3498DB),
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          Container(
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
            child: TextFormField(
              controller: _notesController,
              maxLines: 6,
              decoration: InputDecoration(
                hintText: '예: 베란다에서 햇빛을 잘 받는 곳에 두고 있어요.\n매일 아침 물을 주려고 해요.',
                hintStyle: GoogleFonts.gaegu(
                  color: Colors.grey[400],
                  fontSize: 15,
                ),
                filled: true,
                fillColor: Colors.white,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(20),
                  borderSide: BorderSide.none,
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(20),
                  borderSide: const BorderSide(
                    color: Color(0xFF2ECC71),
                    width: 2,
                  ),
                ),
                contentPadding: const EdgeInsets.all(20),
              ),
              style: GoogleFonts.gaegu(fontSize: 16, height: 1.4),
              onChanged: (value) => setState(() {}),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPhotoStep() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '소중한 순간을 담아보세요 📸',
            style: GoogleFonts.gaegu(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: const Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            '식물의 현재 모습을 사진으로 기록해주세요',
            style: GoogleFonts.gaegu(fontSize: 16, color: Colors.grey[600]),
          ),
          const SizedBox(height: 20),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: const Color(0xFF3498DB).withOpacity(0.08),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: const Color(0xFF3498DB).withOpacity(0.15),
              ),
            ),
            child: Row(
              children: [
                Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: const Color(0xFF3498DB).withOpacity(0.15),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(
                    Icons.camera_alt_outlined,
                    color: Color(0xFF3498DB),
                    size: 18,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    '나중에도 추가할 수 있어요!',
                    style: GoogleFonts.gaegu(
                      fontSize: 14,
                      color: const Color(0xFF3498DB),
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          Container(
            height: 300,
            child: GestureDetector(
              onTap: _pickImage,
              child: Container(
                width: double.infinity,
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(
                    color: _imageFile != null
                        ? const Color(0xFF2ECC71).withOpacity(0.3)
                        : Colors.grey[200]!,
                    width: 2,
                    style: BorderStyle.solid,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: _imageFile != null
                          ? const Color(0xFF2ECC71).withOpacity(0.1)
                          : Colors.black.withOpacity(0.05),
                      blurRadius: 12,
                      offset: const Offset(0, 4),
                    ),
                  ],
                  image: _imageFile != null
                      ? DecorationImage(
                          image: FileImage(File(_imageFile!.path)),
                          fit: BoxFit.cover,
                        )
                      : null,
                ),
                child: _imageFile == null
                    ? Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Container(
                            width: 80,
                            height: 80,
                            decoration: BoxDecoration(
                              color: const Color(0xFF2ECC71).withOpacity(0.1),
                              borderRadius: BorderRadius.circular(24),
                            ),
                            child: const Icon(
                              Icons.add_a_photo_outlined,
                              size: 40,
                              color: Color(0xFF2ECC71),
                            ),
                          ),
                          const SizedBox(height: 24),
                          Text(
                            '사진 선택하기',
                            style: GoogleFonts.gaegu(
                              fontSize: 20,
                              fontWeight: FontWeight.bold,
                              color: const Color(0xFF2ECC71),
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            '갤러리에서 사진을 선택해주세요',
                            style: GoogleFonts.gaegu(
                              fontSize: 16,
                              color: Colors.grey[600],
                            ),
                          ),
                        ],
                      )
                    : Stack(
                        children: [
                          Positioned(
                            top: 16,
                            right: 16,
                            child: Container(
                              width: 40,
                              height: 40,
                              decoration: BoxDecoration(
                                color: Colors.black.withOpacity(0.5),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: const Icon(
                                Icons.edit,
                                color: Colors.white,
                                size: 20,
                              ),
                            ),
                          ),
                        ],
                      ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBottomNavigation() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 12,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: Row(
        children: [
          if (_currentStep < _totalSteps - 1) ...[
            Expanded(
              child: ElevatedButton(
                onPressed: _canProceedFromCurrentStep() ? _nextStep : null,
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF2ECC71),
                  foregroundColor: Colors.white,
                  disabledBackgroundColor: Colors.grey[200],
                  disabledForegroundColor: Colors.grey[400],
                  padding: const EdgeInsets.symmetric(vertical: 18),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  elevation: 0,
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      '다음',
                      style: GoogleFonts.gaegu(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(width: 8),
                    const Icon(Icons.arrow_forward_ios, size: 16),
                  ],
                ),
              ),
            ),
          ] else ...[
            Expanded(
              child: ElevatedButton(
                onPressed: _navigateToConfirmation,
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF2ECC71),
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 18),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  elevation: 0,
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.check_circle_outline, size: 20),
                    const SizedBox(width: 8),
                    Text(
                      '등록 완료하기',
                      style: GoogleFonts.gaegu(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

// 텃밭 검색 모달
class FarmSearchModal extends StatefulWidget {
  final FarmApiService farmApiService;
  final Function(Farm) onFarmSelected;

  const FarmSearchModal({
    super.key,
    required this.farmApiService,
    required this.onFarmSelected,
  });

  @override
  State<FarmSearchModal> createState() => _FarmSearchModalState();
}

class _FarmSearchModalState extends State<FarmSearchModal> {
  final TextEditingController _searchController = TextEditingController();
  List<Farm> _searchResults = [];
  bool _isSearching = false;
  String _searchKeyword = '';

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _searchFarms(String keyword) async {
    if (keyword.trim().isEmpty) {
      setState(() {
        _searchResults = [];
        _searchKeyword = '';
      });
      return;
    }

    setState(() {
      _isSearching = true;
      _searchKeyword = keyword;
    });

    try {
      final results = await widget.farmApiService.searchFarms(keyword);
      if (mounted) {
        setState(() {
          _searchResults = results;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _searchResults = [];
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              '검색 중 오류가 발생했습니다: ${e.toString()}',
              style: GoogleFonts.gaegu(),
            ),
            backgroundColor: Colors.red,
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
            margin: const EdgeInsets.all(16),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isSearching = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: MediaQuery.of(context).size.height * 0.85,
      decoration: const BoxDecoration(
        color: Color(0xFFF8F9FA),
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: Column(
        children: [
          Container(
            padding: const EdgeInsets.all(24),
            decoration: const BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
            ),
            child: Column(
              children: [
                Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.grey[300],
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
                const SizedBox(height: 20),
                Row(
                  children: [
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        color: const Color(0xFF2ECC71).withOpacity(0.1),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(
                        Icons.agriculture,
                        color: Color(0xFF2ECC71),
                        size: 20,
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Text(
                        '텃밭 검색',
                        style: GoogleFonts.gaegu(
                          fontSize: 24,
                          fontWeight: FontWeight.bold,
                          color: const Color(0xFF1A1A1A),
                        ),
                      ),
                    ),
                    GestureDetector(
                      onTap: () => Navigator.pop(context),
                      child: Container(
                        width: 40,
                        height: 40,
                        decoration: BoxDecoration(
                          color: Colors.grey[100],
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: const Icon(Icons.close, size: 18),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 24),
                Container(
                  decoration: BoxDecoration(
                    color: const Color(0xFFF8F9FA),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: Colors.grey[200]!),
                  ),
                  child: TextFormField(
                    controller: _searchController,
                    decoration: InputDecoration(
                      hintText: '텃밭명이나 주소를 입력하세요',
                      hintStyle: GoogleFonts.gaegu(color: Colors.grey[400]),
                      prefixIcon: const Icon(
                        Icons.search,
                        color: Color(0xFF2ECC71),
                      ),
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
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 20,
                        vertical: 16,
                      ),
                    ),
                    style: GoogleFonts.gaegu(fontSize: 16),
                    onChanged: (value) {
                      Future.delayed(const Duration(milliseconds: 500), () {
                        if (_searchController.text == value)
                          _searchFarms(value);
                      });
                    },
                    onFieldSubmitted: _searchFarms,
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: _isSearching
                ? const Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        CircularProgressIndicator(color: Color(0xFF2ECC71)),
                        SizedBox(height: 16),
                        Text('텃밭을 검색하고 있습니다...'),
                      ],
                    ),
                  )
                : _searchResults.isEmpty
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Container(
                          width: 80,
                          height: 80,
                          decoration: BoxDecoration(
                            color: Colors.grey[100],
                            borderRadius: BorderRadius.circular(24),
                          ),
                          child: Icon(
                            _searchKeyword.isEmpty
                                ? Icons.search
                                : Icons.search_off,
                            size: 40,
                            color: Colors.grey[400],
                          ),
                        ),
                        const SizedBox(height: 24),
                        Text(
                          _searchKeyword.isEmpty ? '텃밭을 검색해보세요' : '검색 결과가 없습니다',
                          style: GoogleFonts.gaegu(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                            color: Colors.grey[600],
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          _searchKeyword.isEmpty
                              ? '텃밭명이나 주소로 검색할 수 있어요'
                              : '다른 키워드로 다시 검색해보세요',
                          style: GoogleFonts.gaegu(
                            fontSize: 14,
                            color: Colors.grey[500],
                          ),
                        ),
                      ],
                    ),
                  )
                : ListView.builder(
                    padding: const EdgeInsets.all(24),
                    itemCount: _searchResults.length,
                    itemBuilder: (context, index) {
                      final farm = _searchResults[index];
                      return Container(
                        margin: const EdgeInsets.only(bottom: 12),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(20),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withOpacity(0.05),
                              blurRadius: 8,
                              offset: const Offset(0, 4),
                            ),
                          ],
                        ),
                        child: ListTile(
                          contentPadding: const EdgeInsets.all(20),
                          leading: Container(
                            width: 50,
                            height: 50,
                            decoration: BoxDecoration(
                              gradient: const LinearGradient(
                                colors: [Color(0xFF2ECC71), Color(0xFF27AE60)],
                                begin: Alignment.topLeft,
                                end: Alignment.bottomRight,
                              ),
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: const Icon(
                              Icons.agriculture,
                              color: Colors.white,
                              size: 24,
                            ),
                          ),
                          title: Text(
                            farm.farmName ?? '텃밭명 없음',
                            style: GoogleFonts.gaegu(
                              fontWeight: FontWeight.bold,
                              fontSize: 16,
                            ),
                          ),
                          subtitle: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              if (farm.operator != null) ...[
                                const SizedBox(height: 4),
                                Text(
                                  '운영: ${farm.operator}',
                                  style: GoogleFonts.gaegu(
                                    fontSize: 13,
                                    color: Colors.grey[600],
                                  ),
                                ),
                              ],
                              const SizedBox(height: 4),
                              Text(
                                farm.roadNameAddress ??
                                    farm.lotNumberAddress ??
                                    '주소 정보 없음',
                                style: GoogleFonts.gaegu(
                                  fontSize: 14,
                                  color: Colors.grey[700],
                                ),
                              ),
                            ],
                          ),
                          trailing: Container(
                            width: 32,
                            height: 32,
                            decoration: BoxDecoration(
                              color: const Color(0xFF2ECC71).withOpacity(0.1),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: const Icon(
                              Icons.arrow_forward_ios,
                              size: 14,
                              color: Color(0xFF2ECC71),
                            ),
                          ),
                          onTap: () => widget.onFarmSelected(farm),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
