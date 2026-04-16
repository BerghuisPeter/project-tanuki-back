package io.github.peterberghuis.profile.repository;

import io.github.peterberghuis.profile.entity.UserPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferencesEntity, UUID> {
}
