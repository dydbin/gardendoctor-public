import 'package:farmbootcamp/models/diary_cursor_page_model.dart';
import 'package:farmbootcamp/models/diary_response_model.dart';
import 'package:farmbootcamp/services/diary_cursor_pager.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('다음 커서를 이어서 조회하고 중복 일지는 ID 기준으로 병합한다', () async {
    final requestedCursors = <String?>[];
    final requestedSizes = <int>[];
    var callCount = 0;
    final pager = DiaryCursorPager(
      fetchPage: (cursor, size) async {
        requestedCursors.add(cursor);
        requestedSizes.add(size);
        callCount++;
        if (callCount == 1) {
          return DiaryCursorPage(
            content: [
              _diary(3),
              _diary(2, title: 'old-title'),
            ],
            size: 2,
            hasNext: true,
            nextCursor: 'cursor-2',
          );
        }
        return DiaryCursorPage(
          content: [
            _diary(2, title: 'new-title'),
            _diary(1),
          ],
          size: 2,
          hasNext: false,
          nextCursor: null,
        );
      },
      pageSize: 2,
    );

    final diaries = await pager.loadAll();

    expect(requestedCursors, [null, 'cursor-2']);
    expect(requestedSizes, [2, 2]);
    expect(diaries.map((diary) => diary.diaryId), [3, 2, 1]);
    expect(diaries[1].title, 'new-title');
    expect(pager.hasNext, isFalse);
    expect(pager.nextCursor, isNull);
  });

  test('hasNext가 true인데 다음 커서가 없으면 순회를 중단한다', () async {
    final pager = DiaryCursorPager(
      fetchPage: (cursor, size) async => DiaryCursorPage(
        content: [_diary(1)],
        size: 1,
        hasNext: true,
        nextCursor: null,
      ),
    );

    await expectLater(pager.loadNext(), throwsFormatException);
    expect(pager.items, isEmpty);
    expect(pager.hasNext, isTrue);
  });

  test('서버가 같은 커서를 반복하면 무한 요청 대신 실패한다', () async {
    var callCount = 0;
    final pager = DiaryCursorPager(
      fetchPage: (cursor, size) async {
        callCount++;
        return DiaryCursorPage(
          content: [_diary(callCount)],
          size: 1,
          hasNext: true,
          nextCursor: 'same-cursor',
        );
      },
    );

    await pager.loadNext();
    await expectLater(pager.loadNext(), throwsFormatException);
    expect(callCount, 2);
  });

  test('페이지 크기는 서버 계약 범위만 허용한다', () {
    Future<DiaryCursorPage> fetchPage(String? cursor, int size) async =>
        const DiaryCursorPage(
          content: [],
          size: 0,
          hasNext: false,
          nextCursor: null,
        );

    expect(
      () => DiaryCursorPager(fetchPage: fetchPage, pageSize: 0),
      throwsArgumentError,
    );
    expect(
      () => DiaryCursorPager(fetchPage: fetchPage, pageSize: 101),
      throwsArgumentError,
    );
  });
}

DiaryResponse _diary(int id, {String? title}) => DiaryResponse(
  diaryId: id,
  userId: 10,
  title: title ?? 'diary-$id',
  diaryDate: '2026-07-13',
  watered: false,
  pruned: false,
  fertilized: false,
  createdAt: '2026-07-13T10:00:00',
  connectedUserPlantIds: const [],
);
