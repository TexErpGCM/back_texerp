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
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new IllegalArgumentException("El monto del pago debe ser mayor que cero");
        }

        Sale sale = saleRepository.findForUpdate(saleId)
                .orElseThrow(() -> new EntityNotFoundException("Venta no encontrada"));

        BigDecimal totalPaid = money(paymentRepository.totalPaidBySaleId(saleId));
        BigDecimal pending = money(sale.total.subtract(totalPaid));
        BigDecimal amount = money(request.amount());

        if (pending.signum() <= 0 || sale.status == SaleStatus.PAID) {
            throw new BusinessConflictException("La venta ya se encuentra pagada");
        }
        if (amount.compareTo(pending) > 0) {
            throw new BusinessConflictException(
                    "El pago excede el saldo pendiente. Saldo pendiente: " + pending.toPlainString()
            );
        }

        Payment payment = new Payment();
        payment.sale = sale;
        payment.method = request.method().trim();
        payment.amount = amount;
        payment.paidAt = request.paidAt() == null ? Instant.now() : request.paidAt();
        payment.reference = normalizeOptional(request.reference());
        paymentRepository.save(payment);

        BigDecimal newPaid = totalPaid.add(amount);
        sale.status = newPaid.compareTo(sale.total) >= 0
                ? SaleStatus.PAID
                : SaleStatus.PARTIALLY_PAID;
        saleRepository.saveAndFlush(sale);

        return saleService.findById(saleId);
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO.setScale(2);
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
