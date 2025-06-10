package project.backend.domain.imagefile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageFile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long imageId;

	@Column(nullable = false)
	private String storeFileName;

	@Column(nullable = false)
	private String uploadFileName;


	public static ImageFile of(String storeFileName, String uploadFileName) {
		return ImageFile.builder()
			.storeFileName(storeFileName)
			.uploadFileName(uploadFileName)
			.build();
	}

}
