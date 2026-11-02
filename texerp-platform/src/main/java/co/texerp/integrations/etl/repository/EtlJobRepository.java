
package co.texerp.integrations.etl.repository;

import co.texerp.integrations.etl.domain.EtlJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EtlJobRepository
        extends JpaRepository<EtlJob, Long> {

    Optional<EtlJob> findByName(String name);
}
