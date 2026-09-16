package com.Teenkung.devSkills.storage;

import com.Teenkung.devSkills.api.booster.XpBoosterSnapshot;
import com.Teenkung.devSkills.domain.user.UserProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StorageProvider extends AutoCloseable {

    void initialize();

    UserProfile loadProfile(UUID uuid);

    void saveProfile(UserProfile profile);

    List<XpBoosterSnapshot> loadBoosters();

    void saveBooster(XpBoosterSnapshot booster);

    boolean deleteBooster(UUID boosterId);

    int deleteExpiredBoosters(long expiresAtOrBeforeEpochMillis);

    void recordPlayerIdentity(UUID playerId, String playerName);

    Optional<UUID> findKnownPlayerUuid(String playerName);

    @Override
    void close();
}
