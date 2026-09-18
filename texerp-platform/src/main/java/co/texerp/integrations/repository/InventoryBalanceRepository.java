package co.texerp.integrations.repository;

import co.texerp.integrations.domain.InventoryBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {

    @Query(value = """
            select b
            from InventoryBalance b
            join fetch b.variant v
            join fetch v.product p
            join fetch b.warehouse w
            where (:sku = '' or lower(v.sku) like lower(concat('%', :sku, '%')))
              and (:product = ''
                   or lower(p.code) like lower(concat('%', :product, '%'))
                   or lower(p.name) like lower(concat('%', :product, '%')))
              and (:warehouse = ''
                   or lower(w.code) like lower(concat('%', :warehouse, '%'))
                   or lower(w.name) like lower(concat('%', :warehouse, '%')))
              and (:status = ''
                   or (:status = 'OUT_OF_STOCK' and b.availableQuantity <= 0)
                   or (:status = 'LOW_STOCK' and b.availableQuantity > 0 and b.availableQuantity <= b.minimumQuantity)
                   or (:status = 'AVAILABLE' and b.availableQuantity > b.minimumQuantity))
            order by v.sku, w.name, b.id
            """,
            countQuery = """
            select count(b)
            from InventoryBalance b
            join b.variant v
            join v.product p
            join b.warehouse w
            where (:sku = '' or lower(v.sku) like lower(concat('%', :sku, '%')))
              and (:product = ''
                   or lower(p.code) like lower(concat('%', :product, '%'))
                   or lower(p.name) like lower(concat('%', :product, '%')))
              and (:warehouse = ''
                   or lower(w.code) like lower(concat('%', :warehouse, '%'))
                   or lower(w.name) like lower(concat('%', :warehouse, '%')))
              and (:status = ''
                   or (:status = 'OUT_OF_STOCK' and b.availableQuantity <= 0)
                   or (:status = 'LOW_STOCK' and b.availableQuantity > 0 and b.availableQuantity <= b.minimumQuantity)
                   or (:status = 'AVAILABLE' and b.availableQuantity > b.minimumQuantity))
            """)
    Page<InventoryBalance> search(
            @Param("sku") String sku,
            @Param("product") String product,
            @Param("warehouse") String warehouse,
            @Param("status") String status,
            Pageable pageable
    );

    @Query("""
            select b
            from InventoryBalance b
            join fetch b.variant v
            join fetch v.product p
            join fetch b.warehouse w
            where lower(v.sku) = lower(:sku)
            order by w.name, w.id
            """)
    List<InventoryBalance> findAllBySku(@Param("sku") String sku);

    Optional<InventoryBalance> findByVariantIdAndWarehouseId(Long variantId, Long warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b
            from InventoryBalance b
            where b.variant.id = :variantId
              and b.warehouse.id = :warehouseId
            """)
    Optional<InventoryBalance> findForUpdate(
            @Param("variantId") Long variantId,
            @Param("warehouseId") Long warehouseId
    );
}
