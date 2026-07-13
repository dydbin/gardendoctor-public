import 'package:flutter/material.dart';
import 'package:dio/dio.dart';
import 'package:geolocator/geolocator.dart';
import 'package:kakao_map_plugin/kakao_map_plugin.dart';
import '../config/app_config.dart';
import '../models/farm_model.dart';
import '../services/farm_api_service.dart';
import '../services/dio_interceptor.dart';

class NearbyFarmScreen extends StatefulWidget {
  const NearbyFarmScreen({super.key});

  @override
  State<NearbyFarmScreen> createState() => _NearbyFarmScreenState();
}

class _NearbyFarmScreenState extends State<NearbyFarmScreen>
    with TickerProviderStateMixin {
  late final FarmApiService farmApiService;
  List<Farm> farmList = [];
  List<Farm> filteredFarmList = [];
  bool isLoading = true;
  String? errorMsg;
  int? selectedFarmIndex;

  LatLng? _initialPosition;
  KakaoMapController? _mapController;
  Set<Marker> markers = {};
  bool _isMapReady = false;

  final TextEditingController _searchController = TextEditingController();
  DraggableScrollableController? _scrollController;
  String _searchQuery = '';

  @override
  void initState() {
    super.initState();

    try {
      _scrollController = DraggableScrollableController();
    } catch (e) {
      print('ScrollController initialization error: $e');
      _scrollController = null;
    }

    final dio = Dio(
      BaseOptions(
        baseUrl: AppConfig.apiBaseUrl,
        connectTimeout: const Duration(seconds: 8),
        receiveTimeout: const Duration(seconds: 8),
      ),
    );
    dio.interceptors.add(DioInterceptor());
    farmApiService = FarmApiService(dio);

    fetchNearbyFarms();
    _searchController.addListener(_onSearchChanged);
  }

  @override
  void dispose() {
    _searchController.dispose();
    _scrollController?.dispose();
    super.dispose();
  }

  void _onSearchChanged() {
    if (!mounted) return;
    setState(() {
      _searchQuery = _searchController.text.toLowerCase();
      _filterFarms();
    });
  }

  void _filterFarms() {
    if (_searchQuery.isEmpty) {
      filteredFarmList = List.from(farmList);
    } else {
      filteredFarmList = farmList.where((farm) {
        final name = (farm.farmName ?? '').toLowerCase(); // 변경!
        final address = (farm.roadNameAddress ?? '').toLowerCase();
        final operator = (farm.operator ?? '').toLowerCase();

        return name.contains(_searchQuery) ||
            address.contains(_searchQuery) ||
            operator.contains(_searchQuery);
      }).toList();
    }
  }

  Future<void> fetchNearbyFarms() async {
    if (!mounted) return;

    setState(() {
      isLoading = true;
      errorMsg = null;
      selectedFarmIndex = null;
      _isMapReady = false;
    });

    try {
      final pos = await _determinePosition();
      if (!mounted) return;

      _initialPosition = LatLng(pos.latitude, pos.longitude);

      final farms = await farmApiService.getNearbyFarms(
        latitude: pos.latitude,
        longitude: pos.longitude,
        radius: 20,
      );

      if (!mounted) return;

      await _createMarkers(farms);

      setState(() {
        farmList = farms;
        filteredFarmList = List.from(farms);
        isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        errorMsg = e is DioException
            ? '데이터를 불러오는 데 실패했습니다: ${e.message ?? 'Unknown error'}'
            : e.toString();
        isLoading = false;
      });
    }
  }

  Future<void> _createMarkers(List<Farm> farms) async {
    final newMarkers = <Marker>{};

    if (_initialPosition != null) {
      final currentMarker = Marker(
        markerId: 'current_location',
        latLng: _initialPosition!,
        infoWindowContent: '현재 위치',
        width: 40,
        height: 50,
      );
      newMarkers.add(currentMarker);
    }

    for (int i = 0; i < farms.length; i++) {
      final farm = farms[i];

      final lat = farm.latitude;
      final lng = farm.longitude;

      if (lat != null && lng != null && lat > 0.0 && lng > 0.0) {
        try {
          final marker = Marker(
            markerId: 'farm_${farm.gardenUniqueId ?? i}_$i',
            latLng: LatLng(lat, lng),
            infoWindowContent: farm.farmName ?? '텃밭 $i', // 변경!
            width: 35,
            height: 45,
          );
          newMarkers.add(marker);
        } catch (e) {
          print('Marker creation error for farm $i: $e');
        }
      }
    }

    if (mounted) {
      setState(() {
        markers = newMarkers;
      });
    }
  }

  void _onMapCreated(KakaoMapController controller) {
    _mapController = controller;

    Future.delayed(const Duration(milliseconds: 500), () {
      if (mounted) {
        setState(() {
          _isMapReady = true;
        });
        _fitMarkersToMap();
      }
    });
  }

  void _fitMarkersToMap() {
    if (!_isMapReady || markers.isEmpty || _mapController == null || !mounted)
      return;

    try {
      final farmMarkers = markers
          .where((m) => m.markerId.startsWith('farm_'))
          .toList();

      if (farmMarkers.isEmpty) {
        if (_initialPosition != null) {
          _mapController?.setCenter(_initialPosition!);
          _mapController?.setLevel(4);
        }
        return;
      }

      if (farmMarkers.length == 1) {
        _mapController?.setCenter(farmMarkers.first.latLng);
        _mapController?.setLevel(3);
        return;
      }

      double minLat = farmMarkers.first.latLng.latitude;
      double maxLat = farmMarkers.first.latLng.latitude;
      double minLng = farmMarkers.first.latLng.longitude;
      double maxLng = farmMarkers.first.latLng.longitude;

      for (final marker in farmMarkers) {
        final lat = marker.latLng.latitude;
        final lng = marker.latLng.longitude;
        if (lat < minLat) minLat = lat;
        if (lat > maxLat) maxLat = lat;
        if (lng < minLng) minLng = lng;
        if (lng > maxLng) maxLng = lng;
      }

      final bounds = [
        LatLng(minLat - 0.005, minLng - 0.005),
        LatLng(maxLat + 0.005, maxLng + 0.005),
      ];

      _mapController?.fitBounds(bounds);
    } catch (e) {
      print('fitMarkersToMap error: $e');
    }
  }

  Future<Position> _determinePosition() async {
    bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) throw Exception('위치 서비스가 비활성화되어 있습니다.');

    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) {
        throw Exception('위치 권한이 거부되었습니다.');
      }
    }
    if (permission == LocationPermission.deniedForever) {
      throw Exception('위치 권한이 영구적으로 거부되었습니다.');
    }

    return await Geolocator.getCurrentPosition();
  }

  void _onFarmTap(int index) {
    if (!mounted || !_isMapReady) return;

    if (index < 0 || index >= filteredFarmList.length) return;

    final farm = filteredFarmList[index];

    final lat = farm.latitude;
    final lng = farm.longitude;
    if (lat == null || lng == null) return;

    int originalIndex = -1;
    for (int i = 0; i < farmList.length; i++) {
      final originalFarm = farmList[i];

      if (originalFarm == farm ||
          (originalFarm.gardenUniqueId == farm.gardenUniqueId)) {
        originalIndex = i;
        break;
      }
    }

    if (originalIndex == -1) return;

    setState(() {
      selectedFarmIndex = originalIndex;
    });

    try {
      if (_mapController != null) {
        _mapController!.panTo(LatLng(lat, lng));
        _mapController!.setLevel(2);
      }
    } catch (e) {
      print('Map controller error: $e');
    }

    try {
      final currentSize = _scrollController?.size ?? 0.35;
      if (currentSize > 0.6) {
        _scrollController?.animateTo(
          0.35,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeInOut,
        );
      }
    } catch (e) {
      print('Scroll animation error: $e');
    }
  }

  void showFarmDetailDialog(Farm farm) {
    if (!mounted) return;

    showDialog(
      context: context,
      builder: (ctx) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        child: SingleChildScrollView(
          child: Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(20),
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Colors.green[50]!, Colors.white],
              ),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.green[100],
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Icon(
                        Icons.agriculture,
                        color: Colors.green[700],
                        size: 24,
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Text(
                        farm.farmName ?? '텃밭', // 변경!
                        style: const TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 20),
                if (farm.operator != null && farm.operator!.isNotEmpty)
                  _buildInfoCard('운영자', farm.operator!, Icons.person),
                _buildInfoCard(
                  '주소',
                  farm.roadNameAddress ?? '주소 없음',
                  Icons.location_on,
                ),
                if (farm.facilities != null && farm.facilities!.isNotEmpty)
                  _buildInfoCard('시설', farm.facilities!, Icons.build),
                if (farm.contact != null && farm.contact!.isNotEmpty)
                  _buildInfoCard('연락처', farm.contact!, Icons.phone),
                if (farm.available != null)
                  _buildInfoCard(
                    '이용 가능',
                    farm.available! ? '가능' : '불가능',
                    farm.available! ? Icons.check_circle : Icons.cancel,
                    textColor: farm.available! ? Colors.green : Colors.red,
                  ),
                const SizedBox(height: 24),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    onPressed: () => Navigator.of(ctx).pop(),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.green[600],
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    child: const Text(
                      '닫기',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildInfoCard(
    String label,
    String value,
    IconData icon, {
    Color? textColor,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey[200]!),
      ),
      child: Row(
        children: [
          Icon(icon, color: Colors.green[600], size: 20),
          const SizedBox(width: 12),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: TextStyle(
                  fontSize: 12,
                  color: Colors.grey[600],
                  fontWeight: FontWeight.w500,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                value,
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                  color: textColor ?? Colors.black87,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.grey[50],
      extendBodyBehindAppBar: true,
      appBar: AppBar(
        title: const Text(
          '내 주변 텃밭',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        backgroundColor: Colors.green[600]?.withOpacity(0.95),
        foregroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(Icons.my_location_rounded),
            onPressed: () {
              if (_initialPosition != null &&
                  _mapController != null &&
                  _isMapReady) {
                try {
                  _mapController!.setCenter(_initialPosition!);
                  _mapController!.setLevel(4);
                } catch (e) {
                  print('Location button error: $e');
                }
              }
            },
            tooltip: '현재 위치',
          ),
        ],
      ),
      body: Stack(
        children: [
          // 지도 (전체 화면)
          _buildMap(),

          // 로딩/에러 오버레이
          if (isLoading || errorMsg != null) _buildOverlay(),

          // 바텀시트 (DraggableScrollableSheet 사용)
          if (!isLoading && errorMsg == null) _buildBottomSheet(),

          // 새로고침 버튼
          if (!isLoading && errorMsg == null) _buildRefreshButton(),
        ],
      ),
    );
  }

  Widget _buildMap() {
    if (_initialPosition == null) return Container();

    return KakaoMap(
      onMapCreated: _onMapCreated,
      center: _initialPosition!,
      markers: markers.toList(),
      onMarkerTap: (String markerId, LatLng latLng, int? zIndex) {
        if (markerId == 'current_location') return;

        try {
          final farmIdMatch = RegExp(r'farm_.*_(\d+)').firstMatch(markerId);
          if (farmIdMatch != null) {
            final index = int.tryParse(farmIdMatch.group(1) ?? '') ?? -1;
            if (index >= 0 && index < farmList.length) {
              showFarmDetailDialog(farmList[index]);
            }
          }
        } catch (e) {
          print('Marker tap error: $e');
        }
      },
    );
  }

  Widget _buildOverlay() {
    if (isLoading) {
      return Container(
        color: Colors.black.withOpacity(0.2),
        child: const Center(
          child: Card(
            child: Padding(
              padding: EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  CircularProgressIndicator(color: Colors.green),
                  SizedBox(height: 16),
                  Text('주변 텃밭을 찾고 있습니다...', style: TextStyle(fontSize: 16)),
                ],
              ),
            ),
          ),
        ),
      );
    }

    if (errorMsg != null) {
      return Container(
        color: Colors.black.withOpacity(0.2),
        child: Center(
          child: Card(
            margin: const EdgeInsets.all(16),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.error_outline, size: 64, color: Colors.red),
                  const SizedBox(height: 16),
                  Text(
                    errorMsg!,
                    textAlign: TextAlign.center,
                    style: const TextStyle(fontSize: 16),
                  ),
                  const SizedBox(height: 16),
                  ElevatedButton(
                    onPressed: fetchNearbyFarms,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.green[600],
                    ),
                    child: const Text(
                      '다시 시도',
                      style: TextStyle(color: Colors.white),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
    }

    return const SizedBox.shrink();
  }

  Widget _buildBottomSheet() {
    return DraggableScrollableSheet(
      controller: _scrollController,
      initialChildSize: 0.35,
      minChildSize: 0.15,
      maxChildSize: 0.85,
      snap: true,
      snapSizes: const [0.15, 0.35, 0.85],
      builder: (context, scrollController) {
        return Container(
          decoration: const BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
            boxShadow: [
              BoxShadow(
                color: Colors.black12,
                blurRadius: 10,
                offset: Offset(0, -5),
              ),
            ],
          ),
          child: Column(
            children: [
              // 드래그 핸들
              Container(
                margin: const EdgeInsets.symmetric(vertical: 12),
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.grey[300],
                  borderRadius: BorderRadius.circular(2),
                ),
              ),

              // 헤더
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
                child: Column(
                  children: [
                    Row(
                      children: [
                        Icon(
                          Icons.agriculture,
                          color: Colors.green[600],
                          size: 24,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          '근처 텃밭 ${farmList.length}곳',
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const Spacer(),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 6,
                          ),
                          decoration: BoxDecoration(
                            color: Colors.green[50],
                            borderRadius: BorderRadius.circular(20),
                          ),
                          child: Text(
                            '${markers.length - 1}개 마커',
                            style: TextStyle(
                              color: Colors.green[700],
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),

                    // 검색바
                    Container(
                      decoration: BoxDecoration(
                        color: Colors.grey[100],
                        borderRadius: BorderRadius.circular(25),
                      ),
                      child: TextField(
                        controller: _searchController,
                        decoration: InputDecoration(
                          hintText: '텃밭 이름, 주소, 운영자로 검색...',
                          hintStyle: TextStyle(color: Colors.grey[500]),
                          prefixIcon: Icon(
                            Icons.search,
                            color: Colors.grey[500],
                          ),
                          suffixIcon: _searchQuery.isNotEmpty
                              ? IconButton(
                                  icon: Icon(
                                    Icons.clear,
                                    color: Colors.grey[500],
                                  ),
                                  onPressed: () {
                                    _searchController.clear();
                                  },
                                )
                              : null,
                          border: InputBorder.none,
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 20,
                            vertical: 16,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),

              // 리스트
              Expanded(
                child: filteredFarmList.isEmpty
                    ? _buildEmptyState()
                    : ListView.builder(
                        controller: scrollController,
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        physics: const ClampingScrollPhysics(),
                        itemCount: filteredFarmList.length,
                        itemBuilder: (context, index) => _buildFarmCard(index),
                      ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            _searchQuery.isNotEmpty ? Icons.search_off : Icons.agriculture,
            size: 64,
            color: Colors.grey[400],
          ),
          const SizedBox(height: 16),
          Text(
            _searchQuery.isNotEmpty ? '검색 결과가 없습니다' : '주변에 텃밭이 없습니다',
            style: const TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w600,
              color: Colors.grey,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            _searchQuery.isNotEmpty ? '다른 키워드로 검색해보세요' : '검색 반경을 늘려보세요',
            style: const TextStyle(fontSize: 14, color: Colors.grey),
          ),
        ],
      ),
    );
  }

  Widget _buildFarmCard(int index) {
    if (index < 0 || index >= filteredFarmList.length) {
      return const SizedBox.shrink();
    }

    final farm = filteredFarmList[index];

    int originalIndex = -1;
    for (int i = 0; i < farmList.length; i++) {
      final originalFarm = farmList[i];

      if (originalFarm == farm ||
          (originalFarm.gardenUniqueId == farm.gardenUniqueId)) {
        originalIndex = i;
        break;
      }
    }

    final isSelected =
        selectedFarmIndex == originalIndex && originalIndex != -1;
    final lat = farm.latitude;
    final lng = farm.longitude;
    final hasValidCoords = lat != null && lng != null && lat > 0.0 && lng > 0.0;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        gradient: isSelected
            ? LinearGradient(
                colors: [
                  Colors.green[50]!,
                  Colors.green[100]!.withOpacity(0.3),
                ],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              )
            : null,
        color: isSelected ? null : Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isSelected ? Colors.green : Colors.grey[200]!,
          width: isSelected ? 2 : 1,
        ),
        boxShadow: [
          BoxShadow(
            color: isSelected
                ? Colors.green.withOpacity(0.1)
                : Colors.black.withOpacity(0.05),
            blurRadius: isSelected ? 8 : 4,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: hasValidCoords ? () => _onFarmTap(index) : null,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                // 아이콘
                Container(
                  width: 56,
                  height: 56,
                  decoration: BoxDecoration(
                    gradient: hasValidCoords
                        ? LinearGradient(
                            colors: isSelected
                                ? [Colors.green, Colors.green[700]!]
                                : [Colors.green[100]!, Colors.green[200]!],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          )
                        : LinearGradient(
                            colors: [Colors.red[100]!, Colors.red[200]!],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Icon(
                    hasValidCoords ? Icons.agriculture : Icons.error_outline,
                    color: hasValidCoords
                        ? (isSelected ? Colors.white : Colors.green[700])
                        : Colors.red[700],
                    size: 28,
                  ),
                ),
                const SizedBox(width: 16),

                // 정보
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        farm.farmName ?? '이름 없음', // 변경!
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                          color: isSelected
                              ? Colors.green[800]
                              : Colors.black87,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        farm.roadNameAddress ?? '주소 없음',
                        style: const TextStyle(
                          color: Colors.grey,
                          fontSize: 14,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      if (farm.operator != null &&
                          farm.operator!.isNotEmpty) ...[
                        const SizedBox(height: 4),
                        Row(
                          children: [
                            Icon(
                              Icons.person,
                              size: 14,
                              color: Colors.grey[600],
                            ),
                            const SizedBox(width: 4),
                            Text(
                              farm.operator!,
                              style: TextStyle(
                                color: Colors.grey[600],
                                fontSize: 12,
                                fontWeight: FontWeight.w500,
                              ),
                            ),
                          ],
                        ),
                      ],
                      if (farm.available != null) ...[
                        const SizedBox(height: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 4,
                          ),
                          decoration: BoxDecoration(
                            color: farm.available!
                                ? Colors.green[100]
                                : Colors.red[100],
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Text(
                            farm.available! ? '이용 가능' : '이용 불가',
                            style: TextStyle(
                              color: farm.available!
                                  ? Colors.green[700]
                                  : Colors.red[700],
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),

                // 화살표
                Icon(
                  hasValidCoords ? Icons.chevron_right : Icons.location_off,
                  color: hasValidCoords
                      ? (isSelected ? Colors.green : Colors.grey)
                      : Colors.red,
                  size: 24,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildRefreshButton() {
    return Positioned(
      top: MediaQuery.of(context).padding.top + 70,
      right: 16,
      child: FloatingActionButton.small(
        onPressed: fetchNearbyFarms,
        backgroundColor: Colors.white,
        elevation: 4,
        child: Icon(Icons.refresh, color: Colors.green[600]),
      ),
    );
  }
}
