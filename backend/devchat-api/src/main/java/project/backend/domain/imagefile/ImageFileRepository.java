package project.backend.domain.imagefile;

import java.util.Objects;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImageFileRepository extends JpaRepository<ImageFile, Long> {

    boolean existsByStoreFileName(String imgName);

    Optional<ImageFile> findByUploadFileName(String uploadFileName);

    Optional<ImageFile> findByStoreFileName(String storeFileName);
}
