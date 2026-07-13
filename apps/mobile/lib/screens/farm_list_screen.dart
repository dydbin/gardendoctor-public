import 'package:flutter/material.dart';
import 'package:dio/dio.dart';

import '../config/app_config.dart';
import '../models/farm_model.dart';
import '../services/farm_api_service.dart';
import '../services/dio_interceptor.dart'; // ✅ 토큰/에러 공통 처리

class FarmListScreen extends StatefulWidget {
  const FarmListScreen({super.key});

  @override
  State<FarmListScreen> createState() => _FarmListScreenState();
}

class _FarmListScreenState extends State<FarmListScreen> {
  late final FarmApiService farmApiService;
  List<Farm> farmList = [];
  bool isLoading = true;

  @override
  void initState() {
    super.initState();

    final dio = Dio(
      BaseOptions(
        baseUrl: AppConfig.apiBaseUrl,
        connectTimeout: const Duration(seconds: 8),
        receiveTimeout: const Duration(seconds: 8),
      ),
    );

    // ✅ JWT 자동첨부/401 처리 등
    dio.interceptors.add(DioInterceptor());

    farmApiService = FarmApiService(dio);
    fetchFarms();
  }

  Future<void> fetchFarms() async {
    try {
      final farms = await farmApiService.getAllFarms();
      if (!mounted) return;
      setState(() {
        farmList = farms;
        isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => isLoading = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('텃밭 목록을 불러오지 못했습니다.')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('주말농장 목록')),
      body: isLoading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: fetchFarms,
              child: ListView.builder(
                physics: const AlwaysScrollableScrollPhysics(),
                itemCount: farmList.length,
                itemBuilder: (context, index) {
                  final farm = farmList[index];
                  return ListTile(
                    leading: const Icon(Icons.agriculture),
                    title: Text(farm.farmName ?? '텃밭 이름 없음'),
                    // ✅ 주소 우선순위: 지번(lot) → 도로명(road) → '주소 없음'
                    subtitle: Text(
                      farm.lotNumberAddress ?? farm.roadNameAddress ?? '주소 없음',
                    ),
                    onTap: () {
                      // 상세 화면이 있으면 여기서 네비게이션
                    },
                  );
                },
              ),
            ),
    );
  }
}
