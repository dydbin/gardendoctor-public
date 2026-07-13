import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:dio/dio.dart';
import '../config/app_config.dart';
import '../services/notification_api_service.dart';
import '../models/notification_model.dart';
import '../models/page_response_model.dart';
import '../services/dio_interceptor.dart';

class NotificationScreen extends StatefulWidget {
  const NotificationScreen({super.key});

  @override
  State<NotificationScreen> createState() => _NotificationScreenState();
}

class _NotificationScreenState extends State<NotificationScreen>
    with TickerProviderStateMixin {
  late NotificationApiService _api;
  final List<NotificationResponse> _notifications = [];
  bool _isLoading = true;
  bool _isMoreLoading = false;
  int _page = 0;
  bool _hasMore = true;
  final ScrollController _scrollController = ScrollController();
  late AnimationController _fadeController;
  late AnimationController _slideController;

  @override
  void initState() {
    super.initState();
    _fadeController = AnimationController(
      duration: const Duration(milliseconds: 800),
      vsync: this,
    );
    _slideController = AnimationController(
      duration: const Duration(milliseconds: 600),
      vsync: this,
    );
    _initApiAndLoad();
    _scrollController.addListener(_scrollListener);
  }

  @override
  void dispose() {
    _scrollController.removeListener(_scrollListener);
    _scrollController.dispose();
    _fadeController.dispose();
    _slideController.dispose();
    super.dispose();
  }

  void _scrollListener() {
    if (!_hasMore || _isMoreLoading) return;
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 200) {
      _loadNotifications();
    }
  }

  Future<void> _initApiAndLoad() async {
    try {
      final dio = Dio(BaseOptions(baseUrl: AppConfig.apiBaseUrlWithApi));
      dio.interceptors.add(DioInterceptor());
      assert(() {
        dio.interceptors.add(
          LogInterceptor(requestBody: true, responseBody: true),
        );
        return true;
      }());

      _api = NotificationApiService(dio);
      await _loadNotifications(isRefresh: true);
      _fadeController.forward();
      _slideController.forward();
    } catch (e) {
      debugPrint("API 초기화 실패: $e");
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _loadNotifications({bool isRefresh = false}) async {
    if (_isMoreLoading) return;

    if (isRefresh) {
      _page = 0;
      _hasMore = true;
      _notifications.clear();
      setState(() {
        _isLoading = true;
      });
    }

    if (!_hasMore) {
      setState(() {
        _isLoading = false;
      });
      return;
    }

    setState(() {
      _isMoreLoading = true;
    });

    try {
      final res = await _api.getNotifications(_page, 10, "createdAt,desc");
      final pageData = res.data;
      final List<NotificationResponse> newData = pageData.content;

      setState(() {
        _notifications.addAll(newData);
        _page++;
        _hasMore = !pageData.last;
      });
    } on DioException catch (e) {
      debugPrint("알림 로드 실패: ${e.response?.data ?? e.message}");
      _showErrorSnackBar("알림을 불러오는데 실패했습니다");
    } finally {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _isMoreLoading = false;
      });
    }
  }

  void _showErrorSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.redAccent,
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }

  String _formatDate(String raw) {
    try {
      final dt = DateTime.parse(raw);
      final now = DateTime.now();
      final diff = now.difference(dt);

      if (diff.inDays == 0) {
        if (diff.inHours == 0) {
          return "${diff.inMinutes}분 전";
        }
        return "${diff.inHours}시간 전";
      } else if (diff.inDays == 1) {
        return "어제";
      } else if (diff.inDays < 7) {
        return "${diff.inDays}일 전";
      } else {
        final mm = dt.month.toString().padLeft(2, '0');
        final dd = dt.day.toString().padLeft(2, '0');
        return "${dt.year}-$mm-$dd";
      }
    } catch (_) {
      return raw.split("T").first;
    }
  }

  Future<void> _markAsRead(int id) async {
    final idx = _notifications.indexWhere((n) => n.notificationId == id);
    if (idx == -1 || _notifications[idx].isRead) return;

    final prev = _notifications[idx];
    setState(() {
      _notifications[idx] = NotificationResponse(
        notificationId: prev.notificationId,
        title: prev.title,
        message: prev.message,
        isRead: true,
        createdAt: prev.createdAt,
      );
    });

    try {
      await _api.markAsRead(id);
    } catch (e) {
      debugPrint("알림 읽음 처리 실패: $e");
      if (!mounted) return;
      setState(() {
        _notifications[idx] = prev;
      });
      _showErrorSnackBar("읽음 처리에 실패했습니다");
    }
  }

  Future<void> _deleteNotification(int id) async {
    try {
      await _api.deleteNotification(id);
      setState(() {
        _notifications.removeWhere((n) => n.notificationId == id);
      });
    } catch (e) {
      debugPrint("알림 삭제 실패: $e");
      _showErrorSnackBar("삭제에 실패했습니다");
    }
  }

  Future<void> _deleteAllNotifications() async {
    final confirmed = await _showDeleteAllDialog();
    if (!confirmed) return;

    try {
      await _api.deleteAll();
      setState(() {
        _notifications.clear();
        _hasMore = false;
        _page = 0;
      });
    } catch (e) {
      debugPrint("전체 알림 삭제 실패: $e");
      _showErrorSnackBar("전체 삭제에 실패했습니다");
    }
  }

  Future<bool> _showDeleteAllDialog() async {
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(20),
            ),
            title: Text(
              '전체 삭제',
              style: GoogleFonts.gaegu(
                fontWeight: FontWeight.bold,
                fontSize: 20,
              ),
            ),
            content: Text(
              '모든 알림을 삭제하시겠습니까?',
              style: GoogleFonts.gaegu(fontSize: 16),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: Text('취소', style: GoogleFonts.gaegu(color: Colors.grey)),
              ),
              TextButton(
                onPressed: () => Navigator.pop(context, true),
                child: Text('삭제', style: GoogleFonts.gaegu(color: Colors.red)),
              ),
            ],
          ),
        ) ??
        false;
  }

  Widget _buildNotificationCard(NotificationResponse notification, int index) {
    return TweenAnimationBuilder<double>(
      duration: Duration(milliseconds: 300 + (index * 100)),
      tween: Tween(begin: 0.0, end: 1.0),
      builder: (context, value, child) {
        return Transform.translate(
          offset: Offset(0, 50 * (1 - value)),
          child: Opacity(
            opacity: value,
            child: Container(
              margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(20),
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: notification.isRead
                      ? [Colors.grey.shade50, Colors.grey.shade100]
                      : [Colors.white, const Color(0xFFF0FDF4)],
                ),
                boxShadow: [
                  BoxShadow(
                    color: notification.isRead
                        ? Colors.grey.withOpacity(0.1)
                        : const Color(0xFF2ECC71).withOpacity(0.1),
                    spreadRadius: 0,
                    blurRadius: 10,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: Dismissible(
                key: Key(notification.notificationId.toString()),
                background: Container(
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(20),
                    gradient: const LinearGradient(
                      colors: [Colors.redAccent, Colors.red],
                    ),
                  ),
                  alignment: Alignment.centerRight,
                  padding: const EdgeInsets.only(right: 24),
                  child: const Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.delete_outline, color: Colors.white, size: 28),
                      SizedBox(height: 4),
                      Text(
                        '삭제',
                        style: TextStyle(color: Colors.white, fontSize: 12),
                      ),
                    ],
                  ),
                ),
                onDismissed: (_) =>
                    _deleteNotification(notification.notificationId),
                child: Material(
                  color: Colors.transparent,
                  child: InkWell(
                    borderRadius: BorderRadius.circular(20),
                    onTap: notification.isRead
                        ? null
                        : () => _markAsRead(notification.notificationId),
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            width: 48,
                            height: 48,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              gradient: LinearGradient(
                                colors: notification.isRead
                                    ? [
                                        Colors.grey.shade300,
                                        Colors.grey.shade400,
                                      ]
                                    : [
                                        const Color(0xFF2ECC71),
                                        const Color(0xFF27AE60),
                                      ],
                              ),
                              boxShadow: [
                                BoxShadow(
                                  color:
                                      (notification.isRead
                                              ? Colors.grey
                                              : const Color(0xFF2ECC71))
                                          .withOpacity(0.3),
                                  spreadRadius: 0,
                                  blurRadius: 8,
                                  offset: const Offset(0, 2),
                                ),
                              ],
                            ),
                            child: Icon(
                              notification.isRead
                                  ? Icons.notifications_outlined
                                  : Icons.notifications_active_rounded,
                              color: Colors.white,
                              size: 24,
                            ),
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  mainAxisAlignment:
                                      MainAxisAlignment.spaceBetween,
                                  children: [
                                    Expanded(
                                      child: Text(
                                        notification.title,
                                        style: GoogleFonts.gaegu(
                                          fontSize: 18,
                                          fontWeight: notification.isRead
                                              ? FontWeight.w500
                                              : FontWeight.bold,
                                          color: notification.isRead
                                              ? Colors.grey.shade700
                                              : Colors.black87,
                                        ),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    Container(
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 8,
                                        vertical: 4,
                                      ),
                                      decoration: BoxDecoration(
                                        color: notification.isRead
                                            ? Colors.grey.shade200
                                            : const Color(
                                                0xFF2ECC71,
                                              ).withOpacity(0.1),
                                        borderRadius: BorderRadius.circular(12),
                                      ),
                                      child: Text(
                                        _formatDate(notification.createdAt),
                                        style: GoogleFonts.gaegu(
                                          fontSize: 12,
                                          color: notification.isRead
                                              ? Colors.grey.shade600
                                              : const Color(0xFF2ECC71),
                                          fontWeight: FontWeight.w500,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 8),
                                Text(
                                  notification.message,
                                  style: GoogleFonts.gaegu(
                                    fontSize: 15,
                                    color: notification.isRead
                                        ? Colors.grey.shade600
                                        : Colors.grey.shade800,
                                    height: 1.4,
                                  ),
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                if (!notification.isRead) ...[
                                  const SizedBox(height: 12),
                                  Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 12,
                                      vertical: 6,
                                    ),
                                    decoration: BoxDecoration(
                                      gradient: const LinearGradient(
                                        colors: [
                                          Color(0xFF2ECC71),
                                          Color(0xFF27AE60),
                                        ],
                                      ),
                                      borderRadius: BorderRadius.circular(20),
                                    ),
                                    child: Text(
                                      '읽음 처리하기',
                                      style: GoogleFonts.gaegu(
                                        fontSize: 12,
                                        color: Colors.white,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                  ),
                                ],
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildEmptyState() {
    return FadeTransition(
      opacity: _fadeController,
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 120,
              height: 120,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: LinearGradient(
                  colors: [
                    const Color(0xFF2ECC71).withOpacity(0.1),
                    const Color(0xFF27AE60).withOpacity(0.1),
                  ],
                ),
              ),
              child: const Icon(
                Icons.notifications_none_rounded,
                size: 60,
                color: Color(0xFF2ECC71),
              ),
            ),
            const SizedBox(height: 24),
            Text(
              "새로운 알림이 없습니다",
              style: GoogleFonts.gaegu(
                fontSize: 22,
                fontWeight: FontWeight.w600,
                color: Colors.grey.shade700,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              "알림이 도착하면 여기에 표시됩니다",
              style: GoogleFonts.gaegu(
                fontSize: 16,
                color: Colors.grey.shade500,
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      extendBodyBehindAppBar: true,
      appBar: AppBar(
        elevation: 0,
        backgroundColor: Colors.transparent,
        flexibleSpace: Container(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [Color(0xFF2ECC71), Color(0xFF27AE60)],
            ),
          ),
        ),
        title: SlideTransition(
          position: Tween<Offset>(begin: const Offset(-1, 0), end: Offset.zero)
              .animate(
                CurvedAnimation(
                  parent: _slideController,
                  curve: Curves.elasticOut,
                ),
              ),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  color: Colors.white.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(
                  Icons.notifications_active_rounded,
                  color: Colors.white,
                  size: 24,
                ),
              ),
              const SizedBox(width: 12),
              Text(
                "알림",
                style: GoogleFonts.gaegu(
                  fontWeight: FontWeight.bold,
                  fontSize: 24,
                  color: Colors.white,
                ),
              ),
            ],
          ),
        ),
        actions: [
          SlideTransition(
            position: Tween<Offset>(begin: const Offset(1, 0), end: Offset.zero)
                .animate(
                  CurvedAnimation(
                    parent: _slideController,
                    curve: Curves.elasticOut,
                  ),
                ),
            child: Container(
              margin: const EdgeInsets.only(right: 16),
              child: Material(
                color: Colors.white.withOpacity(0.2),
                borderRadius: BorderRadius.circular(12),
                child: InkWell(
                  borderRadius: BorderRadius.circular(12),
                  onTap: _notifications.isEmpty
                      ? null
                      : _deleteAllNotifications,
                  child: Container(
                    padding: const EdgeInsets.all(8),
                    child: Icon(
                      Icons.delete_sweep_rounded,
                      color: _notifications.isEmpty
                          ? Colors.white.withOpacity(0.5)
                          : Colors.white,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFF2ECC71), Color(0xFFF8FFF9)],
            stops: [0.0, 0.3],
          ),
        ),
        child: _isLoading
            ? const Center(
                child: CircularProgressIndicator(
                  valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                ),
              )
            : _notifications.isEmpty
            ? _buildEmptyState()
            : RefreshIndicator(
                onRefresh: () => _loadNotifications(isRefresh: true),
                color: const Color(0xFF2ECC71),
                child: ListView.builder(
                  controller: _scrollController,
                  padding: const EdgeInsets.only(top: 120, bottom: 20),
                  itemCount: _notifications.length + (_hasMore ? 1 : 0),
                  itemBuilder: (context, index) {
                    if (index == _notifications.length) {
                      return Container(
                        padding: const EdgeInsets.all(20),
                        child: const Center(
                          child: CircularProgressIndicator(
                            valueColor: AlwaysStoppedAnimation<Color>(
                              Color(0xFF2ECC71),
                            ),
                          ),
                        ),
                      );
                    }

                    return _buildNotificationCard(_notifications[index], index);
                  },
                ),
              ),
      ),
    );
  }
}
