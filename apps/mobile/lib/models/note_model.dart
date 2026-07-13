import 'package:image_picker/image_picker.dart';

class Note {
  final String title;
  final String? registeredPlantNickname; // 어떤 식물에 대한 일지인지 (선택사항)
  final DateTime date;
  final XFile? image;
  final bool watered;
  final bool fertilized;
  final bool pruned;
  final String text;

  Note({
    required this.title,
    this.registeredPlantNickname,
    required this.date,
    this.image,
    required this.watered,
    required this.fertilized,
    required this.pruned,
    required this.text,
  });
}
