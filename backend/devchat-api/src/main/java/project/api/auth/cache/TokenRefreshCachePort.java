package project.api.auth.cache;

import java.util.Optional;

public interface TokenRefreshCachePort {

    void saveWithGracePeriod(String oldRefreshToken, String newAccessToken);

    Optional<String> getNewTokenIfInGracePeriod(String oldRefreshToken);
}