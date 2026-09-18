package co.texerp.integrations.repository;

import co.texerp.integrations.domain.InventoryMovement;
import co.texerp.integrations.domain.InventoryMovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    @Query(value = """
            select m
            from InventoryMovement m
            join fetch m.variant v
            join fetch v.product p
            join fetch m.warehouse w
            left join fetch m.compensatesMovement cm
            where (:fromDate is null or m.movementAt >= :fromDate)
              and (:toDate is null or m.movementAt <= :toDate)
              and (:sku = '' or lower(v.sku) like lower(concat('%', :sku, '%')))
              and (:warehouse = ''
                   or lower(w.code) like lower(concat('%', :warehouse, '%'))
                   or lower(w.name) like lower(concat('%', :warehouse, '%')))
              and (:movementType is null or m.type = :movementType)
              and (:document = '' or lower(m.sourceDocument) like lower(concat('%', :document, '%')))
            order by m.movementAt desc, m.id desc
            """,
            countQuery = """
            select count(m)
            from InventoryMovement m
            join m.variant v
            join m.warehouse w
            where (:fromDate is null or m.movementAt >= :fromDate)
              and (:toDate is null or m.movementAt <= :toDate)
              and (:sku = '' or lower(v.sku) like lower(concat('%', :sku, '%')))
              and (:warehouse = ''
                   or lower(w.code) like lower(concat('%', :warehouse, '%'))
                   or lower(w.name) like lower(concat('%', :warehouse, '%')))
              and (:movementType is null or m.type = :movementType)
              and (:document = '' or lower(m.sourceDocument) like lower(concat('%', :document, '%')))
            """)
    Page<InventoryMovement> search(
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("sku") String sku,
            @Param("warehouse") String warehouse,
            @Param("movementType") InventoryMovementType movementType,
            @Param("document") String document,
            Pageable pageable
    );

    @Query("""
            select m
            from InventoryMovement m
            join fetch m.variant v
            join fetch v.product p
            join fetch m.warehouse w
            left join fetch m.compensatesMovement cm
            where m.id = :id
            """)
    Optional<InventoryMovement> findDetailedById(@Param("id") Long id);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m
            from InventoryMovement m
            join fetch m.variant v
            join fetch v.product p
            join fetch m.warehouse w
            where m.id = :id
            """)
    Optional<InventoryMovement> findForCompensation(@Param("id") Long id);

    @Query("""
            select case when count(m) > 0 then true else false end
            from InventoryMovement m
            where m.compensatesMovement.id = :movementId
            """)
    boolean existsCompensationFor(@Param("movementId") Long movementId);
}
