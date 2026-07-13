// lib/screens/daily_log_screen.dart

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:table_calendar/table_calendar.dart';
import 'package:intl/intl.dart';
import 'dart:io';
import 'package:shared_preferences/shared_preferences.dart';

import '../config/app_config.dart';
import '../models/diary_response_model.dart';
import '../models/user_plant_model.dart';
import '../services/diary_api_service.dart';
import '../services/diary_cursor_pager.dart';
import 'note_screen.dart';
import 'diary_detail_screen.dart';

class DailyLogScreen extends StatefulWidget {
  const DailyLogScreen({super.key});
  @override
  State<DailyLogScreen> createState() => _DailyLogScreenState();
}

class _DailyLogScreenState extends State<DailyLogScreen> {
  DateTime _focusedDay = DateTime.now();
  DateTime? _selectedDay;

  Map<DateTime, List<DiaryResponse>> _events = {};
  bool _isLoading = true;
  String? _error;

  // ⭐️ 추가: 식물 목록, 현재 선택한 필터(식물)
  List<UserPlantResponse> _myPlants = [];
  int? _selectedPlantId; // null이면 전체보기

  @override
  void initState() {
    super.initState();
    _selectedDay = _focusedDay;
    _fetchPlantsAndDiaries();
  }

  Future<void> _fetchPlantsAndDiaries() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
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

      // 1) 내 식물 목록 가져오기 (필터 드롭다운용)
      final userPlantsResponse = await dio.get('/api/user-plants');
      final myPlants = (userPlantsResponse.data as List)
          .map((e) => UserPlantResponse.fromJson(e))
          .toList();

      // 2) 일지 목록 가져오기 (필터 반영)
      List<DiaryResponse> diaries;
      if (_selectedPlantId == null) {
        final pager = DiaryCursorPager(
          fetchPage: (cursor, size) async {
            final response = await apiService.getMyDiariesByCursor(
              cursor,
              size,
            );
            return response.data;
          },
        );
        diaries = await pager.loadAll();
      } else {
        diaries = await apiService.getDiariesByUserPlant(_selectedPlantId!);
      }

      // 3) 날짜별로 그룹핑
      final Map<DateTime, List<DiaryResponse>> events = {};
      for (final diary in diaries) {
        final diaryDateStr = diary.diaryDate;
        if (diaryDateStr != null && diaryDateStr.isNotEmpty) {
          final date = DateTime.parse(diaryDateStr);
          final normalizedDate = DateTime(date.year, date.month, date.day);
          events.putIfAbsent(normalizedDate, () => []);
          events[normalizedDate]!.add(diary);
        }
      }

      if (mounted) {
        setState(() {
          _myPlants = myPlants;
          _events = events;
        });
      }
    } on DioException catch (e) {
      if (mounted) {
        if (e.response?.statusCode == 401) {
          setState(() => _error = "인증에 실패했습니다. 다시 로그인해주세요.");
        } else {
          setState(() => _error = "일지 로딩 실패: ${e.message}");
        }
      }
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  List<DiaryResponse> _getEventsForDay(DateTime day) {
    return _events[DateTime(day.year, day.month, day.day)] ?? [];
  }

  // ⭐️ 필터 드롭다운 변경
  void _onPlantFilterChanged(int? userPlantId) async {
    setState(() {
      _selectedPlantId = userPlantId;
      _isLoading = true;
    });
    await _fetchPlantsAndDiaries();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      appBar: AppBar(
        title: Text(
          '성장 일지',
          style: GoogleFonts.gaegu(fontWeight: FontWeight.bold, fontSize: 24),
        ),
        backgroundColor: Colors.white,
        elevation: 1,
      ),
      body: SafeArea(
        child: Column(
          children: [
            _buildCalendarCard(),
            _buildDateHeader(),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: _buildPlantFilterDropdown(),
            ),
            Expanded(child: _buildEventList()),
          ],
        ),
      ),
      floatingActionButton: _buildFloatingActionButton(),
    );
  }

  Widget _buildCalendarCard() {
    return Card(
      margin: const EdgeInsets.all(16),
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: TableCalendar<DiaryResponse>(
        locale: 'ko_KR',
        firstDay: DateTime.utc(2020, 1, 1),
        lastDay: DateTime.utc(2030, 12, 31),
        focusedDay: _focusedDay,
        selectedDayPredicate: (day) => isSameDay(_selectedDay, day),
        onDaySelected: (selectedDay, focusedDay) => setState(() {
          _selectedDay = selectedDay;
          _focusedDay = focusedDay;
        }),
        eventLoader: _getEventsForDay,
        calendarStyle: CalendarStyle(
          todayDecoration: BoxDecoration(
            color: const Color(0xFF2ECC71).withValues(alpha: 0.3),
            shape: BoxShape.circle,
          ),
          selectedDecoration: const BoxDecoration(
            color: Color(0xFF2ECC71),
            shape: BoxShape.circle,
          ),
          markerDecoration: const BoxDecoration(
            color: Colors.orangeAccent,
            shape: BoxShape.circle,
          ),
        ),
        headerStyle: HeaderStyle(
          formatButtonVisible: false,
          titleCentered: true,
          titleTextStyle: GoogleFonts.gaegu(fontSize: 20),
        ),
        daysOfWeekStyle: DaysOfWeekStyle(
          weekdayStyle: GoogleFonts.gaegu(color: Colors.grey[600]),
          weekendStyle: GoogleFonts.gaegu(color: Colors.redAccent),
        ),
      ),
    );
  }

  Widget _buildDateHeader() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20.0, vertical: 12.0),
      child: Row(
        children: [
          Text(
            DateFormat('M월 d일 EEEE', 'ko_KR').format(_selectedDay!),
            style: GoogleFonts.gaegu(
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: Colors.grey[800],
            ),
          ),
          const Spacer(),
          Text(
            '${_getEventsForDay(_selectedDay!).length}개의 기록',
            style: GoogleFonts.gaegu(fontSize: 14, color: Colors.grey[600]),
          ),
        ],
      ),
    );
  }

  // ⭐️ 식물별/전체 필터 드롭다운
  Widget _buildPlantFilterDropdown() {
    return DropdownButtonFormField<int>(
      initialValue: _selectedPlantId,
      isExpanded: true,
      decoration: InputDecoration(
        filled: true,
        fillColor: Colors.green.withValues(alpha: 0.05),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(15),
          borderSide: BorderSide.none,
        ),
        contentPadding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
      ),
      hint: Text("전체 보기", style: GoogleFonts.gaegu(color: Colors.grey[700])),
      items: [
        DropdownMenuItem<int>(
          value: null,
          child: Text(
            "전체 보기",
            style: GoogleFonts.gaegu(fontWeight: FontWeight.bold),
          ),
        ),
        ..._myPlants.map(
          (plant) => DropdownMenuItem<int>(
            value: plant.userPlantId,
            child: Row(
              children: [
                if (plant.userPlantImageUrl != null)
                  ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: Image.network(
                      plant.userPlantImageUrl!,
                      width: 24,
                      height: 24,
                      fit: BoxFit.cover,
                    ),
                  )
                else
                  const Icon(Icons.eco_rounded, size: 22, color: Colors.grey),
                const SizedBox(width: 8),
                Text(
                  plant.plantNickname ?? plant.plantName ?? '',
                  style: GoogleFonts.gaegu(),
                ),
              ],
            ),
          ),
        ),
      ],
      onChanged: (value) {
        _onPlantFilterChanged(value);
      },
      style: GoogleFonts.gaegu(color: Colors.black87),
    );
  }

  Widget _buildEventList() {
    if (_isLoading) return const Center(child: CircularProgressIndicator());
    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Text(
            _error!,
            textAlign: TextAlign.center,
            style: GoogleFonts.gaegu(fontSize: 16, color: Colors.redAccent),
          ),
        ),
      );
    }

    final events = _getEventsForDay(_selectedDay!);
    if (events.isEmpty) {
      return Center(
        child: Text(
          '작성된 일지가 없어요.',
          style: GoogleFonts.gaegu(fontSize: 18, color: Colors.grey),
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      itemCount: events.length,
      itemBuilder: (context, index) {
        final diary = events[index];
        return Card(
          margin: const EdgeInsets.only(bottom: 12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          elevation: 1,
          child: ListTile(
            leading: ClipRRect(
              borderRadius: BorderRadius.circular(8.0),
              child: diary.imageUrl != null
                  ? Image.network(
                      diary.imageUrl!,
                      width: 56,
                      height: 56,
                      fit: BoxFit.cover,
                      errorBuilder: (context, error, stackTrace) =>
                          const Icon(Icons.notes_rounded, size: 30),
                    )
                  : Container(
                      width: 56,
                      height: 56,
                      color: Colors.grey[200],
                      child: const Icon(
                        Icons.notes_rounded,
                        size: 30,
                        color: Colors.grey,
                      ),
                    ),
            ),
            title: Text(
              diary.title,
              style: GoogleFonts.gaegu(
                fontWeight: FontWeight.bold,
                fontSize: 16,
              ),
            ),
            subtitle: Text(
              diary.content ?? '',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: GoogleFonts.gaegu(),
            ),
            onTap: () async {
              final prefs = await SharedPreferences.getInstance();
              final accessToken = prefs.getString('accessToken');
              final dio = Dio(
                BaseOptions(
                  baseUrl: AppConfig.apiBaseUrl,
                  headers: {
                    HttpHeaders.authorizationHeader: 'Bearer $accessToken',
                  },
                ),
              );
              final apiService = DiaryApiService(dio);

              DiaryResponse detail;
              try {
                detail = await apiService.getDiary(diary.diaryId);
              } catch (e) {
                detail = diary; // 실패시 기존거
              }

              if (!mounted) return;
              final result = await Navigator.push<bool>(
                context,
                MaterialPageRoute(
                  builder: (_) =>
                      DiaryDetailScreen(diary: detail), // ✅ non-null!
                ),
              );

              if (result == true) {
                _fetchPlantsAndDiaries();
              }
            },
          ),
        );
      },
    );
  }

  Widget _buildFloatingActionButton() {
    return FloatingActionButton(
      onPressed: () async {
        final result = await Navigator.push<bool>(
          context,
          MaterialPageRoute(
            builder: (_) => NoteScreen(selectedDate: _selectedDay!),
          ),
        );
        if (result == true) {
          _fetchPlantsAndDiaries();
        }
      },
      backgroundColor: const Color(0xFF2ECC71),
      child: const Icon(Icons.edit, color: Colors.white),
    );
  }
}
