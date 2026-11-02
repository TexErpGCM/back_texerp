package co.texerp.integrations.repository;

import co.texerp.integrations.domain.PurchaseReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceipt, Long> {
    List<PurchaseReceipt> findByPurchaseOrderIdOrderByReceivedAtDesc(Long purchaseOrderId);
}
