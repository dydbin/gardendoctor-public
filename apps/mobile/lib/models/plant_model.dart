import 'package:json_annotation/json_annotation.dart';

part 'plant_model.g.dart';

@JsonSerializable()
class Plant {
  final int plantId;
  final String? plantName;
  final String? plantEnglishName;
  final String? species;
  final String? season;

  @JsonKey(name: 'plantImageUrl') // ✅ 백엔드 키와 매핑
  final String? imageUrl;

  Plant({
    required this.plantId,
    this.plantName,
    this.plantEnglishName,
    this.species,
    this.season,
    this.imageUrl,
  });

  factory Plant.fromJson(Map<String, dynamic> json) => _$PlantFromJson(json);
  Map<String, dynamic> toJson() => _$PlantToJson(this);
}
