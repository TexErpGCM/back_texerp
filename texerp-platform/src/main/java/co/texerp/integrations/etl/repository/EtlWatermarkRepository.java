
package co.texerp.integrations.etl.repository;

import co.texerp.integrations.etl.domain.EtlWatermark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EtlWatermarkRepository
        extends JpaRepository<EtlWatermark, Long> {
}
