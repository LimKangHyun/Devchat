package project.backend.domain.imagefile;


import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import project.backend.global.exception.ex.ImageFileException;
import project.backend.global.exception.errorcode.ImageFileErrorCode;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageFileService {

	private final ImageFileRepository imageFileRepository;

	@Value("${file.images.profile.path}")
	private String profilePath;

	@Value("${file.images.chat.path}")
	private String chatImagePath;

	@Transactional
	public ImageFile saveChatImage(MultipartFile file) {
		String uploadFileName = file.getOriginalFilename();

		checkExtension(uploadFileName);
		checkFileTypeIsImage(file.getContentType());

		String extension = uploadFileName.substring(uploadFileName.lastIndexOf(".")).toLowerCase();

		checkFileExtensionIsImage(extension);

		String storeFileName = UUID.randomUUID() + extension;

		ImageFile imageFile = ImageFile.of(storeFileName, uploadFileName);
		imageFileRepository.saveAndFlush(imageFile);
		Path savePath = Paths.get(chatImagePath, storeFileName);

		try {
			log.info("📁 저장 경로: {}", savePath.toAbsolutePath());
			file.transferTo(savePath);
			return imageFile;

		} catch (IOException e) {
			imageFileRepository.delete(imageFile);
			log.error("파일 저장 중 IOException 발생", e);
			throw new ImageFileException(ImageFileErrorCode.FILE_SAVE_FAILURE);
		}
	}

	@Transactional
	public String saveProfileImage(MultipartFile file) {
		String originalFilename = file.getOriginalFilename();
		checkExtension(originalFilename);
		checkFileTypeIsImage(file.getContentType());

		String extension = originalFilename.substring(originalFilename.lastIndexOf("."))
			.toLowerCase();

		checkFileExtensionIsImage(extension);

		String storeFileName = UUID.randomUUID() + extension;

		Path savePath = Paths.get(profilePath, storeFileName);

		try {
			log.info("📁 저장 경로: {}", savePath.toAbsolutePath());
			file.transferTo(savePath);

		} catch (IOException e) {
			log.error("파일 저장 중 IOException 발생", e);
			throw new ImageFileException(ImageFileErrorCode.FILE_SAVE_FAILURE);
		}

		return storeFileName;
	}


	private void checkFileExtensionIsImage(String extension) {
		List<String> imageExtensions = List.of(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp");
		if (!imageExtensions.contains(extension)) {
			throw new ImageFileException(ImageFileErrorCode.INVALID_IMAGE_TYPE);
		}
	}

	private void checkFileTypeIsImage(String fileType) {
		if (fileType == null || !fileType.startsWith("image/")) {
			throw new ImageFileException(ImageFileErrorCode.INVALID_IMAGE_TYPE);
		}
	}

	private void checkExtension(String fileName) {
		if (fileName == null || !fileName.contains(".")) {
			throw new ImageFileException(ImageFileErrorCode.INVALID_IMAGE_TYPE);
		}
	}

	@Transactional(readOnly = true)
	public ImageFile getImageById(Long imageFileId) {
		return imageFileRepository.findById(imageFileId)
			.orElseThrow(() -> new ImageFileException(ImageFileErrorCode.FILE_NOT_FOUND));
	}
}
