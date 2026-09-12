package co.texerp.integrations.repository;

import co.texerp.integrations.domain.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    @Query(value = """
            select w from Warehouse w
            where (:code = '' or lower(w.code) like lower(concat('%', :code, '%')))
              and (:name = '' or lower(w.name) like lower(concat('%', :name, '%')))
              and (:location = '' or lower(w.location) like lower(concat('%', :location, '%')))
              and (:active is null or w.active = :active)
            order by w.name, w.id
            """,
            countQuery = """
            select count(w) from Warehouse w
            where (:code = '' or lower(w.code) like lower(concat('%', :code, '%')))
              and (:name = '' or lower(w.name) like lower(concat('%', :name, '%')))
              and (:location = '' or lower(w.location) like lower(concat('%', :location, '%')))
              and (:active is null or w.active = :active)
            """)
    Page<Warehouse> search(@Param("code") String code,
                           @Param("name") String name,
                           @Param("location") String location,
                           @Param("active") Boolean active,
                           Pageable pageable);
}
