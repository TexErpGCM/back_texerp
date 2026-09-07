package co.texerp.integrations.repository;

import co.texerp.integrations.domain.ProductVariant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    boolean existsBySkuIgnoreCase(String sku);
    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    @Query(value = """
            select v from ProductVariant v join fetch v.product p join fetch v.unit u
            where (:productId is null or p.id = :productId)
              and (:sku = '' or lower(v.sku) like lower(concat('%', :sku, '%')))
              and (:color = '' or lower(v.color) like lower(concat('%', :color, '%')))
              and (:active is null or v.active = :active)
            """,
            countQuery = """
            select count(v) from ProductVariant v
            where (:productId is null or v.product.id = :productId)
              and (:sku = '' or lower(v.sku) like lower(concat('%', :sku, '%')))
              and (:color = '' or lower(v.color) like lower(concat('%', :color, '%')))
              and (:active is null or v.active = :active)
            """)
    Page<ProductVariant> search(@Param("productId") Long productId,
                                @Param("sku") String sku,
                                @Param("color") String color,
                                @Param("active") Boolean active,
                                Pageable pageable);
}
