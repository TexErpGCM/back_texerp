package co.texerp.integrations.repository;

import co.texerp.integrations.domain.UnitOfMeasure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, Long> {
    Optional<UnitOfMeasure> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
}
