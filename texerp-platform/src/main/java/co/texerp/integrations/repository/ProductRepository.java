package co.texerp.integrations.repository;

import co.texerp.integrations.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    @Query(value = """
            select p from Product p
            where (:code = '' or lower(p.code) like lower(concat('%', :code, '%')))
              and (:name = '' or lower(p.name) like lower(concat('%', :name, '%')))
              and (:fabricType = '' or lower(p.fabricType) like lower(concat('%', :fabricType, '%')))
              and (:active is null or p.active = :active)
            order by p.name, p.id
            """,
            countQuery = """
            select count(p) from Product p
            where (:code = '' or lower(p.code) like lower(concat('%', :code, '%')))
              and (:name = '' or lower(p.name) like lower(concat('%', :name, '%')))
              and (:fabricType = '' or lower(p.fabricType) like lower(concat('%', :fabricType, '%')))
              and (:active is null or p.active = :active)
            """)
    Page<Product> search(@Param("code") String code,
                         @Param("name") String name,
                         @Param("fabricType") String fabricType,
                         @Param("active") Boolean active,
                         Pageable pageable);
}
