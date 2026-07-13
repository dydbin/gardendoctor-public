import 'dart:io';
import 'dart:typed_data'; // Uint8List를 사용하기 위해 추가
import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';
import 'package:image/image.dart' as img;
import 'package:image_gallery_saver_plus/image_gallery_saver_plus.dart'; // 패키지 변경
import 'package:farmbootcamp/main.dart';

class CameraScreen extends StatefulWidget {
  const CameraScreen({super.key});

  @override
  State<CameraScreen> createState() => _CameraScreenState();
}

class _CameraScreenState extends State<CameraScreen> {
  late CameraController _controller;
  final ImagePicker _picker = ImagePicker();

  @override
  void initState() {
    super.initState();
    _controller = CameraController(
      cameras[0],
      ResolutionPreset.high,
      enableAudio: false,
    );
    _controller
        .initialize()
        .then((_) {
          if (!mounted) return;
          setState(() {});
        })
        .catchError((Object e) {
          if (e is CameraException) {
            debugPrint('카메라 에러: ${e.code}, ${e.description}');
          }
        });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  // 이미지를 올바른 방향으로 회전시키는 함수
  Future<XFile> _getRotatedImage(XFile originalImage) async {
    final imageBytes = await originalImage.readAsBytes();
    final decodedImage = img.decodeImage(imageBytes);

    if (decodedImage == null) {
      return originalImage;
    }

    // 이미지가 가로 방향일 경우 90도 회전
    if (decodedImage.width > decodedImage.height) {
      final rotatedImage = img.copyRotate(decodedImage, angle: 90);
      final rotatedBytes = img.encodeJpg(rotatedImage);
      final tempDir = await getTemporaryDirectory();
      final tempPath =
          '${tempDir.path}/${DateTime.now().millisecondsSinceEpoch}.jpg';
      final tempFile = File(tempPath);
      await tempFile.writeAsBytes(rotatedBytes);
      return XFile(tempPath);
    }

    return originalImage;
  }

  // 이미지를 갤러리에 저장 (image_gallery_saver_plus 사용)
  Future<void> _saveAndProceed(XFile imageFile) async {
    try {
      final rotatedImage = await _getRotatedImage(imageFile);
      final fileBytes = await rotatedImage.readAsBytes();

      // image_gallery_saver_plus를 사용하여 이미지 저장
      final result = await ImageGallerySaverPlus.saveImage(
        Uint8List.fromList(fileBytes),
        name: 'doctorfarm_${DateTime.now().millisecondsSinceEpoch}',
      );

      if (result != null && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('갤러리에 사진이 저장되었습니다.', style: GoogleFonts.gaegu()),
          ),
        );
        Navigator.pop(context, rotatedImage);
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('저장 실패', style: GoogleFonts.gaegu())),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('저장 실패: $e', style: GoogleFonts.gaegu())),
        );
      }
    }
  }

  // 갤러리에서 이미지 선택
  Future<void> _pickImageFromGallery() async {
    final XFile? image = await _picker.pickImage(source: ImageSource.gallery);
    if (image != null) {
      await _saveAndProceed(image);
    }
  }

  // 사진 촬영
  Future<void> _takePicture() async {
    if (!_controller.value.isInitialized || _controller.value.isTakingPicture) {
      return;
    }
    try {
      final XFile image = await _controller.takePicture();
      await _saveAndProceed(image);
    } on CameraException catch (e) {
      debugPrint("사진 촬영 에러: $e");
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_controller.value.isInitialized) {
      return const Scaffold(
        backgroundColor: Colors.black,
        body: Center(child: CircularProgressIndicator()),
      );
    }

    final mediaSize = MediaQuery.of(context).size;
    final scale = 1 / (_controller.value.aspectRatio * mediaSize.aspectRatio);

    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        alignment: Alignment.center,
        children: [
          Transform.scale(
            scale: scale,
            alignment: Alignment.center,
            child: CameraPreview(_controller),
          ),
          Positioned(
            top: 0,
            left: 0,
            right: 0,
            child: AppBar(
              title: Text('진단할 식물 촬영하기', style: GoogleFonts.gaegu()),
              backgroundColor: Colors.transparent,
              elevation: 0,
              foregroundColor: Colors.white,
              leading: IconButton(
                icon: const Icon(Icons.arrow_back_ios),
                onPressed: () => Navigator.of(context).pop(),
              ),
              actions: [
                IconButton(
                  icon: const Icon(Icons.info_outline),
                  onPressed: () {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text(
                          '현재 상추, 토마토, 고추 등 12종의 식물만 진단 가능해요.',
                          style: GoogleFonts.gaegu(fontSize: 16),
                        ),
                        backgroundColor: Colors.black.withOpacity(0.7),
                        behavior: SnackBarBehavior.floating,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(15),
                        ),
                        margin: const EdgeInsets.all(16),
                      ),
                    );
                  },
                ),
              ],
            ),
          ),
          Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                width: 280,
                height: 280,
                decoration: BoxDecoration(
                  border: Border.all(
                    color: Colors.white.withOpacity(0.8),
                    width: 2,
                  ),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Icon(
                  Icons.eco_outlined,
                  color: Colors.white.withOpacity(0.8),
                  size: 100,
                ),
              ),
              const SizedBox(height: 20),
              Text(
                '프레임 안에 하나의 식물만 놓아주세요.',
                style: GoogleFonts.gaegu(color: Colors.white, fontSize: 16),
              ),
              const SizedBox(height: 5),
              Text(
                '밝고, 명확하고, 초점이 맞아야 합니다.',
                style: GoogleFonts.gaegu(color: Colors.white70, fontSize: 14),
              ),
            ],
          ),
          Positioned(
            bottom: 40,
            left: 0,
            right: 0,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                IconButton(
                  onPressed: _pickImageFromGallery,
                  icon: const Icon(
                    Icons.photo_outlined,
                    color: Colors.white,
                    size: 32,
                  ),
                ),
                GestureDetector(
                  onTap: _takePicture,
                  child: Container(
                    width: 70,
                    height: 70,
                    decoration: BoxDecoration(
                      color: const Color(0xFF2ECC71),
                      shape: BoxShape.circle,
                      border: Border.all(color: Colors.white, width: 4),
                    ),
                  ),
                ),
                const SizedBox(width: 50),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
