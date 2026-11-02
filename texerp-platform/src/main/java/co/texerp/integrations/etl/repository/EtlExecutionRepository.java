
package co.texerp.integrations.etl.repository;

import co.texerp.integrations.etl.domain.EtlExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EtlExecutionRepository
        extends JpaRepository<EtlExecution, Long>,
        JpaSpecificationExecutor<EtlExecution> {
}
