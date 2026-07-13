import 'diary_response_model.dart';

class DiaryCursorPage {
  final List<DiaryResponse> content;
  final int size;
  final bool hasNext;
  final String? nextCursor;

  const DiaryCursorPage({
    required this.content,
    required this.size,
    required this.hasNext,
    required this.nextCursor,
  });

  factory DiaryCursorPage.fromJson(Map<String, dynamic> json) {
    final rawContent = json['content'];
    if (rawContent is! List) {
      throw const FormatException('Cursor page content must be a list.');
    }

    return DiaryCursorPage(
      content: rawContent
          .map(
            (item) =>
                DiaryResponse.fromJson(Map<String, dynamic>.from(item as Map)),
          )
          .toList(growable: false),
      size: json['size'] as int,
      hasNext: json['hasNext'] as bool,
      nextCursor: json['nextCursor'] as String?,
    );
  }
}

class DiaryCursorResponse {
  final DiaryCursorPage data;

  const DiaryCursorResponse({required this.data});

  factory DiaryCursorResponse.fromJson(Map<String, dynamic> json) {
    final rawData = json['data'];
    if (rawData is! Map) {
      throw const FormatException('Cursor response data must be an object.');
    }

    return DiaryCursorResponse(
      data: DiaryCursorPage.fromJson(Map<String, dynamic>.from(rawData)),
    );
  }
}
