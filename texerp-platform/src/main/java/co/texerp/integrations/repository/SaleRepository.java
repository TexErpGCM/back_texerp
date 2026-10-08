package co.texerp.integrations.repository;

import co.texerp.integrations.domain.Sale;
import co.texerp.integrations.domain.SaleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    Optional<Sale> findByQuotationId(Long quotationId);

    @Query("""
            select distinct s from Sale s
            join fetch s.customer c
            join fetch s.warehouse w
            left join fetch s.quotation q
            left join fetch s.lines l
            left join fetch l.variant v
            left join fetch v.product p
            where s.id = :id
            """)
    Optional<Sale> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findForUpdate(@Param("id") Long id);

    @Query(
            value = """
                SELECT s
                FROM Sale s
                JOIN FETCH s.customer c
                JOIN FETCH s.warehouse w
                LEFT JOIN FETCH s.quotation q
                WHERE (
                    CAST(:number AS string) IS NULL
                    OR LOWER(s.number) LIKE LOWER(
                        CONCAT('%', CAST(:number AS string), '%')
                    )
                )
                AND (
                    CAST(:customer AS string) IS NULL
                    OR LOWER(c.name) LIKE LOWER(
                        CONCAT('%', CAST(:customer AS string), '%')
                    )
                )
                AND (
                    :status IS NULL
                    OR s.status = :status
                )
                """,
            countQuery = """
                SELECT COUNT(s)
                FROM Sale s
                JOIN s.customer c
                WHERE (
                    CAST(:number AS string) IS NULL
                    OR LOWER(s.number) LIKE LOWER(
                        CONCAT('%', CAST(:number AS string), '%')
                    )
                )
                AND (
                    CAST(:customer AS string) IS NULL
                    OR LOWER(c.name) LIKE LOWER(
                        CONCAT('%', CAST(:customer AS string), '%')
                    )
                )
                AND (
                    :status IS NULL
                    OR s.status = :status
                )
                """
    )
    Page<Sale> findAllWithFilters(
            @Param("number") String number,
            @Param("customer") String customer,
            @Param("status") SaleStatus status,
            Pageable pageable
    );
}
