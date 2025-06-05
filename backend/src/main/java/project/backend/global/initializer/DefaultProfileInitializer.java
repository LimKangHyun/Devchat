package project.backend.global.initializer;


import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import project.backend.domain.imagefile.ImageFile;
import project.backend.domain.imagefile.ImageFileRepository;
import project.backend.domain.imagefile.ImageType;

@Component
@RequiredArgsConstructor
public class DefaultProfileInitializer {

	private final ImageFileRepository imageFileRepository;

	@Value("${file.images.profile.default}")
	private String defaultProfile;

	@PostConstruct
	public void initializeDefaultProfile() {
		boolean exists = imageFileRepository.existsByStoreFileName(defaultProfile);

		if (!exists) {
			imageFileRepository.save(ImageFile.builder()
				.storeFileName(defaultProfile)
				.uploadFileName(defaultProfile)
				.imageType(ImageType.PROFILE_IMAGE)
				.build());

			imageFileRepository.flush();
		}
	}
}
