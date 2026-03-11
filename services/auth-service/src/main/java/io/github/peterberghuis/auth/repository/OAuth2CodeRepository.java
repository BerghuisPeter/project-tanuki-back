package io.github.peterberghuis.auth.repository;

import io.github.peterberghuis.auth.entity.OAuth2Code;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuth2CodeRepository extends JpaRepository<OAuth2Code, String> {
    Optional<OAuth2Code> findByCode(String code);
}
