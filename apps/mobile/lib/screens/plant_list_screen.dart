// lib/screens/plant_list_screen.dart
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../config/app_config.dart';
import '../../models/user_plant_model.dart';
import 'register_plant_screen.dart';
import 'plant_detail_screen.dart';

class PlantListScreen extends StatefulWidget {
  const PlantListScreen({super.key});

  @override
  State<PlantListScreen> createState() => _PlantListScreenState();
}

class _PlantListScreenState extends State<PlantListScreen>
    with TickerProviderStateMixin {
  final Color kBrand = const Color(0xFF2ECC71);
  final Color kBrandDark = const Color(0xFF27AE60);

  List<UserPlantResponse> _plants = [];
  bool _loading = true;
  late AnimationController _listAnimationController;
  late AnimationController _fabAnimationController;

  @override
  void initState() {
    super.initState();
    _listAnimationController = AnimationController(
      duration: const Duration(milliseconds: 600),
      vsync: this,
    );
    _fabAnimationController = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );
    _fetchPlants();
  }

  @override
  void dispose() {
    _listAnimationController.dispose();
    _fabAnimationController.dispose();
    super.dispose();
  }

  Future<void> _fetchPlants() async {
    setState(() => _loading = true);

    final prefs = await SharedPreferences.getInstance();
    final accessToken = prefs.getString('accessToken');
    final dio = Dio(
      BaseOptions(
        baseUrl: AppConfig.apiBaseUrl,
        headers: {HttpHeaders.authorizationHeader: 'Bearer $accessToken'},
      ),
    );

    try {
      final response = await dio.get('/api/user-plants');
      print('[DEBUG] 서버 응답: ${response.data}');

      List<UserPlantResponse> parsed = [];

      // 응답 형태가 배열이거나 { data: [] } 두 케이스 대응
      List<dynamic>? list;
      if (response.data is List) {
        list = response.data as List<dynamic>;
      } else if (response.data is Map && response.data['data'] is List) {
        list = (response.data as Map)['data'] as List<dynamic>;
      }

      if (list != null) {
        parsed = list.map((e) {
          Map<String, dynamic> m = {};
          if (e is Map) {
            m = Map<String, dynamic>.from(e as Map);
          }

          // 🔎 원본 로그 (문제 분석용)
          print(
            '[RAW] id=${m['userPlantId']} '
            'isNotif=${m['isNotificationEnabled']} notif=${m['notificationEnabled']} '
            'W=${m['waterIntervalDays']} P=${m['pruneIntervalDays']} F=${m['fertilizeIntervalDays']}',
          );

          final plant = UserPlantResponse.fromJson(m);

          print(
            '[PARSED] id=${plant.userPlantId} '
            'notif=${plant.isNotificationEnabled} '
            'W=${plant.waterIntervalDays} P=${plant.pruneIntervalDays} F=${plant.fertilizeIntervalDays}',
          );
          return plant;
        }).toList();
      } else {
        print('[ERROR] 서버에서 알 수 없는 형식의 데이터를 반환했습니다.');
      }

      setState(() {
        _plants = parsed;
        _loading = false;
      });

      _listAnimationController.forward();
      Future.delayed(const Duration(milliseconds: 300), () {
        _fabAnimationController.forward();
      });
    } catch (e, stack) {
      print("[DEBUG] 에러: $e");
      print(stack);
      setState(() => _loading = false);
    }
  }

  void _goToRegisterPlant() async {
    final result = await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const RegisterPlantScreen()),
    );
    _fetchPlants(); // 결과와 상관없이 새로고침
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(),
            Expanded(
              child: _loading
                  ? _buildLoadingState()
                  : _plants.isEmpty
                  ? _buildEmptyState()
                  : _buildPlantList(),
            ),
          ],
        ),
      ),
      floatingActionButton: _buildFloatingActionButton(),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
      child: Row(
        children: [
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [kBrand, kBrandDark],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(16),
            ),
            child: const Icon(Icons.eco, color: Colors.white, size: 24),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '내 식물들',
                  style: GoogleFonts.gaegu(
                    fontSize: 28,
                    fontWeight: FontWeight.bold,
                    color: const Color(0xFF1A1A1A),
                  ),
                ),
                Text(
                  '${_plants.length}개의 식물을 키우고 있어요 🌱',
                  style: GoogleFonts.gaegu(
                    fontSize: 16,
                    color: Colors.grey[600],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLoadingState() {
    return const Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(color: Color(0xFF2ECC71), strokeWidth: 3),
          SizedBox(height: 24),
          Text(
            '식물들을 불러오고 있어요...',
            style: TextStyle(fontSize: 16, color: Colors.grey),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 120,
            height: 120,
            decoration: BoxDecoration(
              color: kBrand.withOpacity(0.1),
              borderRadius: BorderRadius.circular(32),
            ),
            child: Icon(Icons.eco, size: 64, color: kBrand.withOpacity(0.6)),
          ),
          const SizedBox(height: 32),
          Text(
            '아직 등록된 식물이 없어요',
            style: GoogleFonts.gaegu(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: const Color(0xFF1A1A1A),
            ),
          ),
          const SizedBox(height: 12),
          Text(
            '첫 번째 식물을 등록해서\n여러분만의 정원을 시작해보세요!',
            textAlign: TextAlign.center,
            style: GoogleFonts.gaegu(
              fontSize: 16,
              color: Colors.grey[600],
              height: 1.5,
            ),
          ),
          const SizedBox(height: 40),
          ElevatedButton.icon(
            onPressed: _goToRegisterPlant,
            icon: const Icon(Icons.add_rounded),
            label: Text(
              '첫 식물 등록하기',
              style: GoogleFonts.gaegu(
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            style: ElevatedButton.styleFrom(
              backgroundColor: kBrand,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(16),
              ),
              elevation: 0,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPlantList() {
    return AnimatedBuilder(
      animation: _listAnimationController,
      builder: (context, child) {
        return RefreshIndicator(
          color: kBrand,
          onRefresh: _fetchPlants,
          child: ListView.builder(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
            itemCount: _plants.length,
            itemBuilder: (context, index) {
              final animation = Tween<double>(begin: 0.0, end: 1.0).animate(
                CurvedAnimation(
                  parent: _listAnimationController,
                  curve: Interval(
                    index * 0.1,
                    (index * 0.1) + 0.5,
                    curve: Curves.easeInOut,
                  ),
                ),
              );

              return SlideTransition(
                position: Tween<Offset>(
                  begin: const Offset(0, 0.3),
                  end: Offset.zero,
                ).animate(animation),
                child: FadeTransition(
                  opacity: animation,
                  child: _buildPlantCard(_plants[index]),
                ),
              );
            },
          ),
        );
      },
    );
  }

  Widget _buildPlantCard(UserPlantResponse plant) {
    return Container(
      margin: const EdgeInsets.only(bottom: 20),
      child: GestureDetector(
        onTap: () async {
          print('[DEBUG] onTap: plant.userPlantId = ${plant.userPlantId}');
          if (plant.userPlantId != null) {
            final result = await Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) =>
                    PlantDetailScreen(userPlantId: plant.userPlantId!),
              ),
            );
            if (result == true) {
              _fetchPlants(); // 삭제/수정 반영
            }
          } else {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(
                  "userPlantId가 null입니다. DB/백엔드 응답을 확인하세요.",
                  style: GoogleFonts.gaegu(),
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
        },
        child: Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(24),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.06),
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
                  // 썸네일
                  Container(
                    width: 80,
                    height: 80,
                    decoration: BoxDecoration(
                      color: kBrand.withOpacity(0.08),
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(color: kBrand.withOpacity(0.2)),
                    ),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(20),
                      child: plant.userPlantImageUrl != null
                          ? Image.network(
                              plant.userPlantImageUrl!,
                              fit: BoxFit.cover,
                              errorBuilder: (c, e, s) =>
                                  Icon(Icons.eco, color: kBrand, size: 32),
                            )
                          : Icon(Icons.eco, size: 32, color: kBrand),
                    ),
                  ),
                  const SizedBox(width: 20),
                  // 텍스트 영역
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          plant.plantNickname ?? '이름 없음',
                          style: GoogleFonts.gaegu(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: const Color(0xFF1A1A1A),
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 6),
                        Text(
                          plant.plantName ?? '식물 정보 없음',
                          style: GoogleFonts.gaegu(
                            fontSize: 16,
                            color: kBrandDark,
                            fontWeight: FontWeight.w600,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 12,
                                vertical: 4,
                              ),
                              decoration: BoxDecoration(
                                color: kBrand.withOpacity(0.12),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(
                                    Icons.location_on,
                                    size: 14,
                                    color: kBrandDark,
                                  ),
                                  const SizedBox(width: 4),
                                  Text(
                                    plant.plantingPlace ?? '장소 미정',
                                    style: GoogleFonts.gaegu(
                                      fontSize: 12,
                                      color: kBrandDark,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  // 화살표
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: kBrand.withOpacity(0.12),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      Icons.arrow_forward_ios,
                      size: 16,
                      color: kBrandDark,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              // ✅ 알림/주기 요약 Chips (필드 있을 때만)
              _buildScheduleChips(plant),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildScheduleChips(UserPlantResponse plant) {
    final chips = <Widget>[];

    // 알림 여부 - 시각적 구별 강화
    if (plant.isNotificationEnabled != null) {
      chips.add(
        _chip(
          plant.isNotificationEnabled! ? '알림 켜짐' : '알림 꺼짐',
          icon: plant.isNotificationEnabled!
              ? Icons.notifications_active
              : Icons.notifications_off,
          isNotificationChip: true,
          isEnabled: plant.isNotificationEnabled!,
        ),
      );
    }

    // 주기 정보는 기존 스타일 유지
    bool _validDays(int? v) => v != null && v > 0;

    if (_validDays(plant.waterIntervalDays)) {
      chips.add(_chip('물 ${plant.waterIntervalDays}일', icon: Icons.water_drop));
    }
    if (_validDays(plant.pruneIntervalDays)) {
      chips.add(
        _chip('가지치기 ${plant.pruneIntervalDays}일', icon: Icons.content_cut),
      );
    }
    if (_validDays(plant.fertilizeIntervalDays)) {
      chips.add(_chip('영양제 ${plant.fertilizeIntervalDays}일', icon: Icons.eco));
    }

    if (chips.isEmpty) {
      return const SizedBox.shrink();
    }

    return Wrap(spacing: 8, runSpacing: 8, children: chips);
  }

  Widget _chip(
    String text, {
    IconData? icon,
    bool emphasize = false,
    bool isNotificationChip = false,
    bool isEnabled = true,
  }) {
    // 알림 칩인 경우 특별한 스타일링
    if (isNotificationChip) {
      if (isEnabled) {
        // 알림 켜짐 - 초록색 그라데이션
        return Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            gradient: LinearGradient(
              colors: [kBrand, kBrandDark],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(999),
            boxShadow: [
              BoxShadow(
                color: kBrand.withOpacity(0.3),
                blurRadius: 8,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (icon != null) ...[
                Icon(icon, size: 14, color: Colors.white),
                const SizedBox(width: 6),
              ],
              Text(
                text,
                style: GoogleFonts.gaegu(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
            ],
          ),
        );
      } else {
        // 알림 꺼짐 - 회색 + 점선 테두리
        return Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            color: Colors.grey[100],
            borderRadius: BorderRadius.circular(999),
            border: Border.all(
              color: Colors.grey[400]!,
              width: 1.5,
              strokeAlign: BorderSide.strokeAlignInside,
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (icon != null) ...[
                Icon(icon, size: 14, color: Colors.grey[600]),
                const SizedBox(width: 6),
              ],
              Text(
                text,
                style: GoogleFonts.gaegu(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  color: Colors.grey[600],
                ),
              ),
            ],
          ),
        );
      }
    }

    // 기존 일반 칩 스타일 (주기 정보용)
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [kBrand.withOpacity(0.8), kBrandDark.withOpacity(0.8)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(999),
        boxShadow: [
          BoxShadow(
            color: kBrand.withOpacity(0.2),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 14, color: Colors.white),
            const SizedBox(width: 6),
          ],
          Text(
            text,
            style: GoogleFonts.gaegu(
              fontSize: 12,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFloatingActionButton() {
    return AnimatedBuilder(
      animation: _fabAnimationController,
      builder: (context, child) {
        return ScaleTransition(
          scale: _fabAnimationController,
          child: Container(
            margin: const EdgeInsets.only(bottom: 20),
            child: FloatingActionButton.extended(
              onPressed: _goToRegisterPlant,
              backgroundColor: kBrand,
              foregroundColor: Colors.white,
              elevation: 8,
              icon: const Icon(Icons.add_rounded, size: 24),
              label: Text(
                '새 식물 등록',
                style: GoogleFonts.gaegu(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
              ),
            ),
          ),
        );
      },
    );
  }
}
