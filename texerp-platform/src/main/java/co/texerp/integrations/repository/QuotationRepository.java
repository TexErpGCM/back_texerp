package co.texerp.integrations.repository;

import co.texerp.integrations.domain.Quotation;
import co.texerp.integrations.domain.QuotationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    @Query("""
            select distinct q
            from Quotation q
            join fetch q.customer c
            left join fetch q.lines l
            left join fetch l.variant v
            left join fetch v.product p
            where q.id = :id
            """)
    Optional<Quotation> findDetailedById(
            @Param("id") Long id
    );


    /*
     * Para operaciones simples como enviar/cancelar.
     * No necesitamos cargar líneas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select q
            from Quotation q
            join fetch q.customer
            where q.id = :id
            """)
    Optional<Quotation> findForUpdate(
            @Param("id") Long id
    );


    /*
     * Para convertir a venta.
     * Bloquea y además carga TODO:
     * cliente -> líneas -> variante -> producto.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select distinct q
            from Quotation q
            join fetch q.customer c
            left join fetch q.lines l
            left join fetch l.variant v
            left join fetch v.product p
            where q.id = :id
            """)
    Optional<Quotation> findDetailedForUpdate(
            @Param("id") Long id
    );


    @Query(
            value = """
                    SELECT q
                    FROM Quotation q
                    JOIN FETCH q.customer c
                    WHERE (
                        CAST(:number AS string) IS NULL
                        OR LOWER(q.number) LIKE LOWER(
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
                        OR q.status = :status
                    )
                    """,
            countQuery = """
                    SELECT COUNT(q)
                    FROM Quotation q
                    JOIN q.customer c
                    WHERE (
                        CAST(:number AS string) IS NULL
                        OR LOWER(q.number) LIKE LOWER(
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
                        OR q.status = :status
                    )
                    """
    )
    Page<Quotation> findAllWithFilters(
            @Param("number") String number,
            @Param("customer") String customer,
            @Param("status") QuotationStatus status,
            Pageable pageable
    );
}