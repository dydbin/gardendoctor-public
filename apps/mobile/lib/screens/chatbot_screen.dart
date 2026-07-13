import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../config/app_config.dart';

class ChatbotScreen extends StatefulWidget {
  const ChatbotScreen({super.key});

  @override
  State<ChatbotScreen> createState() => _ChatbotScreenState();
}

class _ChatbotScreenState extends State<ChatbotScreen> {
  // ✅ 한 곳에서만 바꾸면 되도록 BASE URL 상수화
  static const String kBaseUrl = AppConfig.apiBaseUrl;

  final TextEditingController _controller = TextEditingController();
  final ScrollController _scrollController = ScrollController();

  /// 메시지: { role: "user" | "assistant" | "bot", content: String, timestamp: DateTime }
  List<Map<String, dynamic>> _messages = <Map<String, dynamic>>[];

  /// 채팅방: { chatId: String, title: String, lastMessage: String, time: String(ISO) }
  List<Map<String, dynamic>> _chatRooms = <Map<String, dynamic>>[];

  String? _selectedChatId;
  bool _loadingRooms = false;
  bool _loadingMessages = false;
  bool _sending = false;

  @override
  void initState() {
    super.initState();
    _fetchChatRooms();
  }

  Future<Map<String, String>> _authHeaders() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('accessToken') ?? '';
    return <String, String>{
      'Content-Type': 'application/json',
      'Authorization': 'Bearer $token',
    };
  }

  void _showSnack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  Future<void> _fetchChatRooms() async {
    setState(() => _loadingRooms = true);
    try {
      final headers = await _authHeaders();
      final url = Uri.parse('$kBaseUrl/api/chat/sessions');
      final res = await http.get(url, headers: headers);

      if (res.statusCode == 200) {
        final rooms = json.decode(res.body) as List<dynamic>;
        setState(() {
          _chatRooms = rooms.map<Map<String, dynamic>>((room) {
            final r = room as Map<String, dynamic>;
            final title = (r['query']?.toString() ?? '채팅방').trim();
            final safeTitle = title.isNotEmpty ? title : '채팅방';
            return <String, dynamic>{
              "chatId": r['chatId'].toString(),
              "title": safeTitle,
              // 서버 DTO엔 lastMessage가 없으니 빈 값(또는 필요시 수정)
              "lastMessage": r['lastMessage']?.toString() ?? '',
              "time": r['updated_at']?.toString() ?? '',
            };
          }).toList();

          if (_chatRooms.isNotEmpty) {
            _selectChatRoom(_chatRooms.first['chatId'] as String);
          } else {
            _selectedChatId = null;
            _messages = <Map<String, dynamic>>[];
          }
        });
      } else if (res.statusCode == 401) {
        _showSnack('세션이 만료되었습니다. 다시 로그인해주세요.');
        setState(() {
          _chatRooms = <Map<String, dynamic>>[];
          _selectedChatId = null;
          _messages = <Map<String, dynamic>>[];
        });
      } else {
        _showSnack('채팅방 목록을 가져오지 못했습니다. (${res.statusCode})');
        setState(() {
          _chatRooms = <Map<String, dynamic>>[];
          _selectedChatId = null;
          _messages = <Map<String, dynamic>>[];
        });
      }
    } catch (e) {
      _showSnack('네트워크 오류가 발생했어요.');
      setState(() {
        _chatRooms = <Map<String, dynamic>>[];
        _selectedChatId = null;
        _messages = <Map<String, dynamic>>[];
      });
    } finally {
      if (mounted) setState(() => _loadingRooms = false);
    }
  }

  Future<void> _selectChatRoom(String chatId) async {
    setState(() {
      _selectedChatId = chatId;
      _messages = <Map<String, dynamic>>[];
      _loadingMessages = true;
    });

    try {
      final headers = await _authHeaders();
      // ✅ 전체 메시지 (user+assistant) 불러오기
      final url = Uri.parse(
        '$kBaseUrl/api/chat/history/messages/all?chatId=$chatId',
      );
      final res = await http.get(url, headers: headers);

      if (res.statusCode == 200) {
        final msgList = json.decode(res.body) as List<dynamic>;
        setState(() {
          _messages = msgList.map<Map<String, dynamic>>((m) {
            final mm = m as Map<String, dynamic>;
            final role = (mm['role']?.toString() ?? '').toLowerCase();
            final content = mm['query']?.toString() ?? '';
            final ts =
                DateTime.tryParse(mm['timestamp']?.toString() ?? '') ??
                DateTime.now();
            return <String, dynamic>{
              "role": role == 'assistant'
                  ? 'assistant'
                  : role == 'user'
                  ? 'user'
                  : 'bot',
              "content": content,
              "timestamp": ts,
            };
          }).toList();
        });
      } else if (res.statusCode == 401) {
        _showSnack('세션이 만료되었습니다. 다시 로그인해주세요.');
        setState(() => _messages = <Map<String, dynamic>>[]);
      } else {
        setState(
          () => _messages = <Map<String, dynamic>>[
            <String, dynamic>{
              "role": "bot",
              "content": "메시지 불러오기 실패 (${res.statusCode})",
              "timestamp": DateTime.now(),
            },
          ],
        );
      }
    } catch (e) {
      setState(
        () => _messages = <Map<String, dynamic>>[
          <String, dynamic>{
            "role": "bot",
            "content": "네트워크 오류가 발생했습니다.",
            "timestamp": DateTime.now(),
          },
        ],
      );
    } finally {
      if (mounted) setState(() => _loadingMessages = false);
      _scrollToBottom();
    }
  }

  /// ✅ 서버 응답을 A/B 포맷 모두 지원하도록 해석
  void _applyServerResponse(dynamic decoded, String userTextJustSent) {
    // B안: { chatId, messages: [ {role, query}, ... ] } (Python 세션 형식)
    if (decoded is Map && decoded['messages'] is List) {
      final List msgs = decoded['messages'] as List;
      final assistantMsg = msgs.reversed.firstWhere(
        (m) => ((m as Map)['role']?.toString().toLowerCase() == 'assistant'),
        orElse: () => null,
      );
      final assistantText = assistantMsg != null
          ? (assistantMsg as Map)['query']?.toString()
          : null;

      setState(() {
        _messages.last["content"] = assistantText ?? "답변을 찾을 수 없습니다.";
        _messages.last["role"] = "assistant";
      });
      return;
    }

    // A안: { chatId, question, answer } (Spring ChatResponseDto)
    if (decoded is Map &&
        (decoded.containsKey('answer') || decoded.containsKey('question'))) {
      final answer = decoded['answer']?.toString() ?? "답변을 찾을 수 없습니다.";
      setState(() {
        _messages.last["content"] = answer;
        _messages.last["role"] = "assistant";
      });
      return;
    }

    // 그 외: 문자열/기타 → 응답 본문 그대로
    setState(() {
      _messages.last["content"] = decoded.toString();
      _messages.last["role"] = "assistant";
    });
  }

  Future<void> _sendMessage() async {
    final text = _controller.text.trim();
    if (text.isEmpty || _sending) return;

    setState(() {
      // 사용자 메시지 추가
      _messages.add(<String, dynamic>{
        "role": "user",
        "content": text,
        "timestamp": DateTime.now(),
      });
      // 대기 중 버블
      _messages.add(<String, dynamic>{
        "role": "bot",
        "content": "응답을 기다리는 중...",
        "timestamp": DateTime.now(),
      });
      _sending = true;
    });
    _controller.clear();
    _scrollToBottom();

    try {
      final headers = await _authHeaders();
      final url = Uri.parse('$kBaseUrl/api/chat');

      final Map<String, dynamic> body = <String, dynamic>{"query": text};
      if (_selectedChatId != null) {
        body["chatId"] = int.tryParse(_selectedChatId!);
      }

      final res = await http.post(
        url,
        headers: headers,
        body: json.encode(body),
      );

      if (res.statusCode == 200) {
        dynamic decoded;
        try {
          decoded = json.decode(res.body);
        } catch (_) {
          decoded = res.body;
        }

        // chatId 갱신
        if (decoded is Map && decoded['chatId'] != null) {
          setState(() {
            _selectedChatId = decoded['chatId'].toString();
          });
        }

        // ✅ A/B 포맷 공용 처리
        _applyServerResponse(decoded, text);

        // 새 세션이 생성됐을 수 있으니 목록 갱신
        _fetchChatRooms();
      } else if (res.statusCode == 401) {
        setState(() {
          _messages.last["content"] = "세션이 만료되었습니다. 다시 로그인해주세요.";
          _messages.last["role"] = "bot";
        });
      } else {
        setState(() {
          _messages.last["content"] = "서버 오류: ${res.statusCode}";
          _messages.last["role"] = "bot";
        });
      }
    } catch (e) {
      setState(() {
        _messages.last["content"] = "네트워크 오류가 발생했습니다.";
        _messages.last["role"] = "bot";
      });
    } finally {
      if (mounted) setState(() => _sending = false);
      _scrollToBottom();
    }
  }

  void _startNewChat() {
    // 다음 전송 시 백엔드가 새 세션을 생성함
    setState(() {
      _selectedChatId = null;
      _messages.clear();
    });
    _showSnack('새 대화를 시작합니다.');
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  String _formatTime(DateTime time) {
    return "${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}";
  }

  String _formatIso(String iso) {
    try {
      final dt = DateTime.parse(iso);
      return "${dt.month}/${dt.day} ${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}";
    } catch (_) {
      return iso;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F9FA),
      appBar: AppBar(
        elevation: 0,
        backgroundColor: Colors.white,
        foregroundColor: Colors.black87,
        title: const Row(
          children: [
            CircleAvatar(
              radius: 16,
              backgroundColor: Color(0xFF2ECC71),
              child: Icon(Icons.local_florist, color: Colors.white, size: 18),
            ),
            SizedBox(width: 12),
            Text(
              "AI Garden Doctor",
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w600,
                color: Colors.black87,
              ),
            ),
            SizedBox(width: 4),
            Text(
              "어시스턴트",
              style: TextStyle(fontSize: 12, color: Color(0xFF2ECC71)),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _fetchChatRooms,
          ),
        ],
      ),
      drawer: Drawer(
        child: Column(
          children: [
            Container(
              height: 120,
              width: double.infinity,
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  colors: [Color(0xFF2ECC71), Color(0xFF27AE60)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
              ),
              child: const SafeArea(
                child: Padding(
                  padding: EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      Text(
                        "대화 기록",
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 20,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      SizedBox(height: 4),
                      Text(
                        "최근 대화",
                        style: TextStyle(color: Colors.white70, fontSize: 14),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            _loadingRooms
                ? const Center(
                    child: Padding(
                      padding: EdgeInsets.all(24),
                      child: CircularProgressIndicator(),
                    ),
                  )
                : Expanded(
                    child: ListView.builder(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      itemCount: _chatRooms.length,
                      itemBuilder: (context, index) {
                        final room = _chatRooms[index];
                        final isSelected = room["chatId"] == _selectedChatId;

                        final title = (room["title"]?.toString() ?? '채팅방');
                        final leadingChar = title.isNotEmpty
                            ? title.characters.first
                            : '챗';

                        return ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          leading: CircleAvatar(
                            backgroundColor: isSelected
                                ? const Color(0xFF2ECC71)
                                : const Color(0xFF2ECC71).withOpacity(0.1),
                            child: Text(
                              leadingChar,
                              style: TextStyle(
                                color: isSelected
                                    ? Colors.white
                                    : const Color(0xFF2ECC71),
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          title: Text(
                            title,
                            style: TextStyle(
                              fontWeight: FontWeight.w600,
                              fontSize: 15,
                              color: isSelected
                                  ? const Color(0xFF2ECC71)
                                  : Colors.black,
                            ),
                          ),
                          subtitle: Text(
                            room["lastMessage"]?.toString() ?? '',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              color: Colors.grey[600],
                              fontSize: 12,
                            ),
                          ),
                          trailing: Text(
                            _formatIso(room["time"]?.toString() ?? ''),
                            style: TextStyle(
                              color: Colors.grey[500],
                              fontSize: 11,
                            ),
                          ),
                          onTap: () {
                            Navigator.pop(context);
                            _selectChatRoom(room["chatId"] as String);
                          },
                        );
                      },
                    ),
                  ),
            GestureDetector(
              onTap: () {
                Navigator.pop(context);
                _startNewChat(); // ✅ 새 대화 시작
              },
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.grey[50],
                  border: Border(top: BorderSide(color: Colors.grey[200]!)),
                ),
                child: Row(
                  children: [
                    Icon(Icons.add, color: Colors.grey[600]),
                    const SizedBox(width: 12),
                    Text(
                      "새 대화 시작",
                      style: TextStyle(
                        color: Colors.grey[700],
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
      body: Column(
        children: [
          _loadingMessages
              ? const Padding(
                  padding: EdgeInsets.only(top: 50),
                  child: CircularProgressIndicator(),
                )
              : Expanded(
                  child: ListView.builder(
                    controller: _scrollController,
                    padding: const EdgeInsets.all(16),
                    itemCount: _messages.length,
                    itemBuilder: (context, index) {
                      final message = _messages[index];
                      final isUser = message["role"] == "user";
                      final isAssistant = message["role"] == "assistant";
                      final timestamp = message["timestamp"] as DateTime;

                      return Padding(
                        padding: const EdgeInsets.only(bottom: 16),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisAlignment: isUser
                              ? MainAxisAlignment.end
                              : MainAxisAlignment.start,
                          children: [
                            if (!isUser) ...[
                              CircleAvatar(
                                radius: 16,
                                backgroundColor: isAssistant
                                    ? const Color(0xFF2ECC71)
                                    : Colors.grey[300],
                                child: Icon(
                                  isAssistant
                                      ? Icons.local_florist
                                      : Icons.smart_toy,
                                  color: isAssistant
                                      ? Colors.white
                                      : Colors.grey[600],
                                  size: 16,
                                ),
                              ),
                              const SizedBox(width: 8),
                            ],
                            Flexible(
                              child: Column(
                                crossAxisAlignment: isUser
                                    ? CrossAxisAlignment.end
                                    : CrossAxisAlignment.start,
                                children: [
                                  Container(
                                    constraints: BoxConstraints(
                                      maxWidth:
                                          MediaQuery.of(context).size.width *
                                          0.7,
                                    ),
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 16,
                                      vertical: 12,
                                    ),
                                    decoration: BoxDecoration(
                                      color: isUser
                                          ? const Color(0xFF2ECC71)
                                          : Colors.white,
                                      borderRadius: BorderRadius.circular(20),
                                      boxShadow: [
                                        BoxShadow(
                                          color: Colors.black.withOpacity(0.05),
                                          blurRadius: 5,
                                          offset: const Offset(0, 2),
                                        ),
                                      ],
                                    ),
                                    child: Text(
                                      message["content"]?.toString() ?? '',
                                      style: TextStyle(
                                        color: isUser
                                            ? Colors.white
                                            : Colors.black87,
                                        fontSize: 14,
                                        height: 1.4,
                                      ),
                                    ),
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    _formatTime(timestamp),
                                    style: TextStyle(
                                      color: Colors.grey[500],
                                      fontSize: 11,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            if (isUser) ...[
                              const SizedBox(width: 8),
                              CircleAvatar(
                                radius: 16,
                                backgroundColor: Colors.grey[300],
                                child: Icon(
                                  Icons.person,
                                  color: Colors.grey[600],
                                  size: 16,
                                ),
                              ),
                            ],
                          ],
                        ),
                      );
                    },
                  ),
                ),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.white,
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withOpacity(0.1),
                  blurRadius: 10,
                  offset: const Offset(0, -2),
                ),
              ],
            ),
            child: SafeArea(
              child: Row(
                children: [
                  IconButton(
                    icon: Icon(Icons.attach_file, color: Colors.grey[600]),
                    onPressed: () {
                      // 파일 첨부 기능(선택)
                    },
                  ),
                  Expanded(
                    child: Container(
                      decoration: BoxDecoration(
                        color: Colors.grey[100],
                        borderRadius: BorderRadius.circular(24),
                      ),
                      child: TextField(
                        controller: _controller,
                        maxLines: null,
                        decoration: const InputDecoration(
                          hintText: "메시지를 입력하세요...",
                          hintStyle: TextStyle(color: Colors.grey),
                          border: InputBorder.none,
                          contentPadding: EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 12,
                          ),
                        ),
                        onSubmitted: (_) => _sendMessage(),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Container(
                    decoration: const BoxDecoration(
                      color: Color(0xFF2ECC71),
                      shape: BoxShape.circle,
                    ),
                    child: IconButton(
                      icon: const Icon(Icons.send, color: Colors.white),
                      onPressed: _sendMessage,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
