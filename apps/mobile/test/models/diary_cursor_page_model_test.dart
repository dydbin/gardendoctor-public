import 'package:farmbootcamp/models/diary_cursor_page_model.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('공통 응답의 data에서 일지 커서 페이지를 파싱한다', () {
    final response = DiaryCursorResponse.fromJson({
      'status': 200,
      'data': {
        'content': [_diaryJson(1)],
        'size': 20,
        'hasNext': true,
        'nextCursor': 'cursor-1',
      },
    });

    expect(response.data.content.single.diaryId, 1);
    expect(response.data.size, 20);
    expect(response.data.hasNext, isTrue);
    expect(response.data.nextCursor, 'cursor-1');
  });

  test('data 객체가 없으면 잘못된 서버 응답으로 처리한다', () {
    expect(
      () => DiaryCursorResponse.fromJson({'data': null}),
      throwsFormatException,
    );
  });
}

Map<String, dynamic> _diaryJson(int id) => {
  'diaryId': id,
  'userId': 10,
  'title': 'diary-$id',
  'content': null,
  'diaryDate': '2026-07-13',
  'imageUrl': null,
  'watered': false,
  'pruned': false,
  'fertilized': false,
  'createdAt': '2026-07-13T10:00:00',
  'updatedAt': null,
  'connectedUserPlantIds': <int>[],
};
