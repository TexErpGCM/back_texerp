package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.Payment;
import co.texerp.integrations.domain.Sale;
import co.texerp.integrations.domain.SaleStatus;
import co.texerp.integrations.dto.PaymentDtos.PaymentCreateRequest;
import co.texerp.integrations.dto.SaleDtos.SaleResponse;
import co.texerp.integrations.repository.PaymentRepository;
import co.texerp.integrations.repository.SaleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
public class PaymentService {
    private final SaleRepository saleRepository;
    private final PaymentRepository paymentRepository;
    private final SaleService saleService;

    public PaymentService(SaleRepository saleRepository, PaymentRepository paymentRepository, SaleService saleService) {
        this.saleRepository = saleRepository;
        this.paymentRepository = paymentRepository;
        this.saleService = saleService;
    }

    @Transactional
    public SaleResponse register(Long saleId, PaymentCreateRequest request) {
        if (saleId == null) {
            throw new IllegalArgumentException("El identificador de la venta es obligatorio");
        }
        if (request == null || request.amount() == null || request.amount().signum() <= 0) {
            throw new IllegalArgumentException("El monto del pago debe ser mayor que cero");
        }
        if (request.method() == null || request.method().isBlank() || request.method().length() > 50) {
            throw new IllegalArgumentException("El método de pago es obligatorio y no puede superar 50 caracteres");
        }
        if (request.reference() != null && request.reference().length() > 150) {
            throw new IllegalArgumentException("La referencia no puede superar 150 caracteres");
        }

        BigDecimal amount;
        try {
            amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("El monto admite máximo dos decimales", ex);
        }

        // Bloqueo de la venta: serializa los registros de pagos concurrentes.
        Sale sale = saleRepository.findForUpdate(saleId)
                .orElseThrow(() -> new EntityNotFoundException("Venta no encontrada"));

        if (sale.status == SaleStatus.CANCELLED || sale.status == SaleStatus.DISPATCHED
                || sale.status == SaleStatus.COMPLETED) {
            throw new BusinessConflictException("La venta no permite pagos en estado " + sale.status);
        }

        BigDecimal totalPaid = money(paymentRepository.totalPaidBySaleId(saleId));
        BigDecimal pending = money(sale.total.subtract(totalPaid));

        if (pending.signum() <= 0 || sale.status == SaleStatus.PAID) {
            throw new BusinessConflictException("La venta ya se encuentra pagada");
        }
        if (amount.compareTo(pending) > 0) {
            throw new BusinessConflictException(
                    "El pago excede el saldo pendiente. Saldo pendiente: " + pending.toPlainString());
        }

        Payment payment = new Payment();
        payment.sale = sale;
        payment.method = request.method().trim();
        payment.amount = amount;
        payment.paidAt = request.paidAt() == null ? Instant.now() : request.paidAt();
        payment.reference = normalizeOptional(request.reference());
        paymentRepository.saveAndFlush(payment);

        BigDecimal newPaid = totalPaid.add(amount);
        sale.status = newPaid.compareTo(money(sale.total)) == 0
                ? SaleStatus.PAID : SaleStatus.PARTIALLY_PAID;
        saleRepository.saveAndFlush(sale);

        return saleService.findById(saleId);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
