// models/photo_analysis_sidebar_response.dart
class PhotoAnalysisSidebarResponseDto {
  final String detectedDisease;
  final String analysisSummary;
  final String solution;
  final String imageUrl;

  PhotoAnalysisSidebarResponseDto({
    required this.detectedDisease,
    required this.analysisSummary,
    required this.solution,
    required this.imageUrl,
  });

  factory PhotoAnalysisSidebarResponseDto.fromJson(Map<String, dynamic> json) {
    return PhotoAnalysisSidebarResponseDto(
      detectedDisease: json['detectedDisease'] ?? '',
      analysisSummary: json['analysisSummary'] ?? '',
      solution: json['solution'] ?? '',
      imageUrl: json['imageUrl'] ?? '',
    );
  }
}
