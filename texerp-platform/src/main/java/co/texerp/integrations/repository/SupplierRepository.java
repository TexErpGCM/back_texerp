package co.texerp.integrations.repository;

import co.texerp.integrations.domain.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    boolean existsByTaxIdIgnoreCase(String taxId);

    boolean existsByTaxIdIgnoreCaseAndIdNot(String taxId, Long id);

    @Query(value = """
            select s from Supplier s
            where (:taxId = '' or lower(s.taxId) like lower(concat('%', :taxId, '%')))
              and (:name = '' or lower(s.name) like lower(concat('%', :name, '%')))
              and (:active is null or s.active = :active)
            order by s.name, s.id
            """,
            countQuery = """
            select count(s) from Supplier s
            where (:taxId = '' or lower(s.taxId) like lower(concat('%', :taxId, '%')))
              and (:name = '' or lower(s.name) like lower(concat('%', :name, '%')))
              and (:active is null or s.active = :active)
            """)
    Page<Supplier> search(@Param("taxId") String taxId,
                          @Param("name") String name,
                          @Param("active") Boolean active,
                          Pageable pageable);
}
