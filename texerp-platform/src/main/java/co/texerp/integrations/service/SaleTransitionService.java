package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.*;
import co.texerp.integrations.dto.SaleDtos.SaleResponse;
import co.texerp.integrations.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
public class SaleTransitionService {
    private final SaleRepository sales;
    private final InventoryMovementRepository movements;
    private final InventoryBalanceUpdater updater;
    private final PaymentRepository payments;
    private final AuditService audit;
    private final SaleService saleService;

    public SaleTransitionService(SaleRepository sales, InventoryMovementRepository movements,
                                 InventoryBalanceUpdater updater, PaymentRepository payments,
                                 AuditService audit, SaleService saleService) {
        this.sales = sales;
        this.movements = movements;
        this.updater = updater;
        this.payments = payments;
        this.audit = audit;
        this.saleService = saleService;
    }

    @Transactional
    public SaleResponse dispatch(Long id, String evidence) {
        Sale sale = requireSale(id);
        if (sale.status != SaleStatus.PAID) {
            throw new BusinessConflictException("Solo una venta pagada puede despacharse");
        }
        if (evidence != null && evidence.length() > 1000) {
            throw new IllegalArgumentException("La evidencia no puede superar 1000 caracteres");
        }
        SaleStatus previous = sale.status;
        sale.status = SaleStatus.DISPATCHED;
        sale.dispatchedAt = Instant.now();
        sale.dispatchedBy = username();
        sale.dispatchEvidence = evidence == null || evidence.isBlank() ? null : evidence.trim();
        audit.log("SALE_DISPATCH", "Sale", sale.id, previous + " -> " + sale.status
                + " usuario=" + sale.dispatchedBy + " fecha=" + sale.dispatchedAt, null);
        sales.saveAndFlush(sale);
        return saleService.toResponse(sale);
    }

    @Transactional
    public SaleResponse deliver(Long id) {
        Sale sale = requireSale(id);
        if (sale.status != SaleStatus.DISPATCHED) {
            throw new BusinessConflictException("Solo una venta despachada puede completarse");
        }
        SaleStatus previous = sale.status;
        sale.status = SaleStatus.COMPLETED;
        sale.completedAt = Instant.now();
        sale.completedBy = username();
        audit.log("SALE_DELIVER", "Sale", sale.id, previous + " -> " + sale.status
                + " usuario=" + sale.completedBy + " fecha=" + sale.completedAt, null);
        sales.saveAndFlush(sale);
        return saleService.toResponse(sale);
    }

    @Transactional
    public SaleResponse cancel(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo de cancelación es obligatorio");
        }
        if (reason.trim().length() > 500) {
            throw new IllegalArgumentException("El motivo no puede superar 500 caracteres");
        }
        Sale sale = requireSale(id);
        if (sale.status == SaleStatus.COMPLETED) {
            throw new BusinessConflictException("La venta completada requiere un proceso de devolución o nota crédito; no admite cancelación directa");
        }
        if (sale.status == SaleStatus.DISPATCHED || sale.status == SaleStatus.CANCELLED) {
            throw new BusinessConflictException("La venta en estado " + sale.status + " no admite cancelación directa");
        }
        SaleStatus previous = sale.status;
        List<InventoryMovement> originals = movements.findBySourceDocumentAndType(
                sale.number,
                InventoryMovementType.SALE
        );        if (originals.isEmpty() || originals.size() != sale.lines.size()) {
            throw new BusinessConflictException("No se encontraron los movimientos originales de la venta; se requiere revisión manual");
        }
        for (InventoryMovement movement : originals) {
            if (movements.existsCompensationFor(movement.id)) {
                throw new BusinessConflictException("El movimiento " + movement.id + " ya fue compensado");
            }
            updater.applyCompensatingMovement(movement, sale.number + "-CANCEL", reason);
        }
        sale.status = SaleStatus.CANCELLED;
        sale.cancelledAt = Instant.now();
        sale.cancelledBy = username();
        var paid = payments.totalPaidBySaleId(sale.id);
        sale.cancellationReason = reason.trim();
        sale.paymentTreatment = paid != null && paid.signum() > 0
                ? "Pagos conservados. Reembolso pendiente de proceso financiero por " + paid.toPlainString()
                : "Sin pagos registrados; no requiere reembolso";
        audit.log("SALE_CANCEL", "Sale", sale.id, previous + " -> " + sale.status
                + " usuario=" + sale.cancelledBy + " fecha=" + sale.cancelledAt
                + " motivo=" + sale.cancellationReason + " pagos=" + sale.paymentTreatment, null);
        sales.saveAndFlush(sale);
        return saleService.toResponse(sale);
    }

    private Sale requireSale(Long id) {
        return sales.findForUpdate(id).orElseThrow(() -> new EntityNotFoundException("Venta no encontrada"));
    }

    private String username() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "sistema" : auth.getName();
    }
}
