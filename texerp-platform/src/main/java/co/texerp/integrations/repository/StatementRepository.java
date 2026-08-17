package co.texerp.integrations.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface StatementRepository<T> extends JpaRepository<T, Long> {
}
