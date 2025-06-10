package project.backend.domain.imagefile;


import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import java.io.IOException;
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
import software.amazon.awssdk.core.exception.SdkClientException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageFileService {

	private final ImageFileRepository imageFileRepository;
	private final AmazonS3 amazonS3;

	@Value("${file.images.profile.path}")
	private String profilePath;

	@Value("${file.images.chat.path}")
	private String chatImagePath;

	@Value("${cloud.aws.s3.bucket}")
	private String bucket;

	@Transactional
	public ImageFile saveChatImage(MultipartFile file) {
		String originalFilename = file.getOriginalFilename();
		String storeFileName = uploadImageToS3(file, chatImagePath);

		ImageFile imageFile = ImageFile.of(storeFileName, originalFilename);
		return imageFileRepository.save(imageFile);
	}

	@Transactional
	public String saveProfileImage(MultipartFile file) {
		return uploadImageToS3(file, profilePath);
	}


	@Transactional
	protected String uploadImageToS3(MultipartFile file, String s3Path) {
		String storeFileName = genStorageFileName(file);

		String s3Key = s3Path + storeFileName;

		try {
			log.info("[S3] 이미지 업로드 : {}", s3Key);
			// 메타데이터 설정
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentType(file.getContentType());
			metadata.setContentLength(file.getSize());

			// 업로드 실행
			amazonS3.putObject(
				new PutObjectRequest(bucket, s3Key, file.getInputStream(), metadata));

		} catch (IOException | SdkClientException | AmazonServiceException e) {
			log.error("파일 업로드 실패", e);
			throw new ImageFileException(ImageFileErrorCode.FILE_SAVE_FAILURE);
		}

		return storeFileName;
	}


	private String genStorageFileName(MultipartFile file) {
		String originalFilename = file.getOriginalFilename();

		validateFileName(originalFilename);
		checkFileTypeIsImage(file.getContentType());

		String extension = originalFilename.substring(originalFilename.lastIndexOf("."))
			.toLowerCase();

		checkFileExtensionIsImage(extension);

		return UUID.randomUUID() + extension;
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

	private void validateFileName(String fileName) {
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
