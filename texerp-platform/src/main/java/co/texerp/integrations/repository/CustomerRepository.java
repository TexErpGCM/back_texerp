package co.texerp.integrations.repository;

import co.texerp.integrations.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByDocumentIgnoreCase(String document);

    boolean existsByDocumentIgnoreCaseAndIdNot(String document, Long id);

    @Query(value = """
            select c from Customer c
            where (:document = '' or lower(c.document) like lower(concat('%', :document, '%')))
              and (:name = '' or lower(c.name) like lower(concat('%', :name, '%')))
              and (:type = '' or lower(c.type) like lower(concat('%', :type, '%')))
              and (:active is null or c.active = :active)
            order by c.name, c.id
            """,
            countQuery = """
            select count(c) from Customer c
            where (:document = '' or lower(c.document) like lower(concat('%', :document, '%')))
              and (:name = '' or lower(c.name) like lower(concat('%', :name, '%')))
              and (:type = '' or lower(c.type) like lower(concat('%', :type, '%')))
              and (:active is null or c.active = :active)
            """)
    Page<Customer> search(@Param("document") String document,
                          @Param("name") String name,
                          @Param("type") String type,
                          @Param("active") Boolean active,
                          Pageable pageable);
}
