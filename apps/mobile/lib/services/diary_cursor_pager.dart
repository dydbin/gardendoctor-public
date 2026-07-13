import 'dart:collection';

import '../models/diary_cursor_page_model.dart';
import '../models/diary_response_model.dart';

typedef DiaryCursorPageFetcher =
    Future<DiaryCursorPage> Function(String? cursor, int size);

class DiaryCursorPager {
  DiaryCursorPager({
    required DiaryCursorPageFetcher fetchPage,
    this.pageSize = 100,
  }) : _fetchPage = fetchPage {
    if (pageSize < 1 || pageSize > 100) {
      throw ArgumentError.value(
        pageSize,
        'pageSize',
        'must be between 1 and 100',
      );
    }
  }

  final DiaryCursorPageFetcher _fetchPage;
  final int pageSize;
  final LinkedHashMap<int, DiaryResponse> _itemsById = LinkedHashMap();
  final Set<String> _requestedCursors = {};

  String? _nextCursor;
  bool _hasNext = true;
  bool _isLoading = false;

  List<DiaryResponse> get items => List.unmodifiable(_itemsById.values);
  bool get hasNext => _hasNext;
  String? get nextCursor => _nextCursor;

  Future<List<DiaryResponse>> loadNext() async {
    if (!_hasNext) return items;
    if (_isLoading) {
      throw StateError('A cursor page is already being loaded.');
    }

    _isLoading = true;
    final requestedCursor = _nextCursor;
    try {
      final page = await _fetchPage(requestedCursor, pageSize);
      final resolvedNextCursor = _validateNextCursor(page, requestedCursor);

      for (final diary in page.content) {
        _itemsById[diary.diaryId] = diary;
      }

      if (requestedCursor != null) {
        _requestedCursors.add(requestedCursor);
      }
      _hasNext = page.hasNext;
      _nextCursor = resolvedNextCursor;
      return items;
    } finally {
      _isLoading = false;
    }
  }

  Future<List<DiaryResponse>> loadAll() async {
    while (_hasNext) {
      await loadNext();
    }
    return items;
  }

  String? _validateNextCursor(DiaryCursorPage page, String? requestedCursor) {
    if (!page.hasNext) return null;

    final nextCursor = page.nextCursor;
    if (nextCursor == null || nextCursor.trim().isEmpty) {
      throw const FormatException(
        'nextCursor is required when hasNext is true.',
      );
    }
    if (nextCursor == requestedCursor ||
        _requestedCursors.contains(nextCursor)) {
      throw const FormatException(
        'Cursor response contains a repeated cursor.',
      );
    }
    return nextCursor;
  }
}
