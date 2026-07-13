class Farm {
  final int gardenUniqueId;
  final String? operator;
  final String? farmName;
  final String? roadNameAddress;
  final String? lotNumberAddress;
  final String? facilities;
  final bool? available;
  final String? contact;
  final double? latitude;
  final double? longitude;
  final String? farmImageUrl; // ✅ 이름 변경

  Farm({
    required this.gardenUniqueId,
    this.operator,
    this.farmName,
    this.roadNameAddress,
    this.lotNumberAddress,
    this.facilities,
    this.available,
    this.contact,
    this.latitude,
    this.longitude,
    this.farmImageUrl, // ✅
  });

  factory Farm.fromJson(Map<String, dynamic> json) {
    return Farm(
      gardenUniqueId: json['gardenUniqueId'],
      operator: json['operator'],
      farmName: json['farmName'],
      roadNameAddress: json['roadNameAddress'],
      lotNumberAddress: json['lotNumberAddress'],
      facilities: json['facilities'],
      available: json['available'],
      contact: json['contact'],
      latitude: (json['latitude'] as num?)?.toDouble(),
      longitude: (json['longitude'] as num?)?.toDouble(),
      farmImageUrl: json['farmImageUrl'], // ✅ 여기
    );
  }
}
