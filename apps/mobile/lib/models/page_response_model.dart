import 'package:json_annotation/json_annotation.dart';

part 'page_response_model.g.dart';

// 어떤 타입의 리스트든 담을 수 있도록 제네릭 <T>를 사용합니다.
@JsonSerializable(genericArgumentFactories: true)
class PageResponse<T> {
  final List<T> content;
  final bool last;
  final int totalPages;
  final int totalElements;
  final int size;
  final int number; // 현재 페이지 번호

  PageResponse({
    required this.content,
    required this.last,
    required this.totalPages,
    required this.totalElements,
    required this.size,
    required this.number,
  });

  factory PageResponse.fromJson(
    Map<String, dynamic> json,
    T Function(Object? json) fromJsonT,
  ) => _$PageResponseFromJson(json, fromJsonT);

  Map<String, dynamic> toJson(Object? Function(T value) toJsonT) =>
      _$PageResponseToJson(this, toJsonT);
}
