package co.texerp.integrations.repository;

import co.texerp.integrations.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.sale.id = :saleId")
    BigDecimal totalPaidBySaleId(@Param("saleId") Long saleId);
}
