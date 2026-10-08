package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.*;
import co.texerp.integrations.dto.SaleDtos.*;
import co.texerp.integrations.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class SaleService {
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final SaleRepository saleRepository;
    private final CustomerRepository customerRepository;
    private final ProductVariantRepository variantRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryBalanceUpdater inventoryBalanceUpdater;
    private final PaymentRepository paymentRepository;

    public SaleService(
            SaleRepository saleRepository,
            CustomerRepository customerRepository,
            ProductVariantRepository variantRepository,
            WarehouseRepository warehouseRepository,
            InventoryBalanceRepository balanceRepository,
            InventoryBalanceUpdater inventoryBalanceUpdater,
            PaymentRepository paymentRepository
    ) {
        this.saleRepository = saleRepository;
        this.customerRepository = customerRepository;
        this.variantRepository = variantRepository;
        this.warehouseRepository = warehouseRepository;
        this.balanceRepository = balanceRepository;
        this.inventoryBalanceUpdater = inventoryBalanceUpdater;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public SaleResponse create(SaleCreateRequest request) {
        Customer customer = requireActiveCustomer(request.customerId());
        Warehouse warehouse = requireActiveWarehouse(request.warehouseId());

        List<PreparedLine> prepared = new ArrayList<>();
        for (int i = 0; i < request.lines().size(); i++) {
            SaleLineRequest requestLine = request.lines().get(i);
            validatePositiveQuantity(requestLine.quantity(), i + 1);
            ProductVariant variant = requireActiveVariant(requestLine.variantId());
            BigDecimal unitPrice = requestLine.unitPrice() == null ? variant.salePrice : requestLine.unitPrice();
            prepared.add(prepareLine(variant, requestLine.quantity(), unitPrice,
                    requestLine.discountAmount(), requestLine.taxRate(), i + 1));
        }

        return confirm(customer, warehouse, null, prepared);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SaleResponse createFromQuotation(Quotation quotation, Long warehouseId) {

        if (quotation == null || quotation.id == null) {
            throw new IllegalArgumentException("La cotización es obligatoria");
        }

        if (warehouseId == null) {
            throw new IllegalArgumentException("La bodega es obligatoria");
        }

        if (saleRepository.findByQuotationId(quotation.id).isPresent()) {
            Sale existing = saleRepository.findByQuotationId(quotation.id)
                    .orElseThrow();

            throw new BusinessConflictException(
                    "La cotización ya fue convertida. Venta existente: "
                            + existing.number
                            + " (id "
                            + existing.id
                            + ")"
            );
        }

        if (quotation.customer == null || quotation.customer.id == null) {
            throw new IllegalArgumentException(
                    "La cotización no tiene un cliente asociado"
            );
        }

        if (quotation.lines == null || quotation.lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "La cotización no tiene líneas"
            );
        }

        Customer customer = requireActiveCustomer(
                quotation.customer.id
        );

        Warehouse warehouse = requireActiveWarehouse(
                warehouseId
        );

        List<PreparedLine> prepared = new ArrayList<>();

        for (int i = 0; i < quotation.lines.size(); i++) {

            QuotationLine quoteLine = quotation.lines.get(i);

            if (quoteLine == null) {
                throw new IllegalArgumentException(
                        "La línea " + (i + 1) + " de la cotización es inválida"
                );
            }

            validatePositiveQuantity(
                    quoteLine.quantity,
                    i + 1
            );

            if (quoteLine.variant == null || quoteLine.variant.id == null) {
                throw new IllegalArgumentException(
                        "La línea "
                                + (i + 1)
                                + " de la cotización no tiene una variante asociada"
                );
            }

            ProductVariant variant = requireActiveVariant(
                    quoteLine.variant.id
            );

            if (quoteLine.unitPrice == null || quoteLine.unitPrice.signum() < 0) {
                throw new IllegalArgumentException(
                        "El precio aprobado de la línea "
                                + (i + 1)
                                + " es inválido"
                );
            }

            prepared.add(
                    prepareLine(
                            variant,
                            quoteLine.quantity,
                            quoteLine.unitPrice,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            i + 1
                    )
            );
        }

        return confirm(
                customer,
                warehouse,
                quotation,
                prepared
        );
    }
    @Transactional(readOnly = true)
    public SaleResponse findById(Long id) {
        Sale sale = saleRepository.findDetailedById(id)
                .orElseThrow(() -> new EntityNotFoundException("Venta no encontrada"));
        return toResponse(sale);
    }

    private SaleResponse confirm(Customer customer, Warehouse warehouse, Quotation quotation, List<PreparedLine> lines) {
        // Prevalidación completa para informar todas las reglas antes de crear la venta.
        for (PreparedLine line : lines) {
            BigDecimal available = balanceRepository.findByVariantIdAndWarehouseId(line.variant.id, warehouse.id)
                    .map(balance -> balance.availableQuantity)
                    .orElse(BigDecimal.ZERO);
            if (available.compareTo(line.quantity) < 0) {
                throw new BusinessConflictException(
                        "Stock insuficiente para SKU " + line.variant.sku +
                                ". Disponible: " + available.toPlainString() +
                                ", solicitado: " + line.quantity.toPlainString()
                );
            }
        }

        Sale sale = new Sale();
        sale.customer = customer;
        sale.warehouse = warehouse;
        sale.quotation = quotation;
        sale.status = SaleStatus.CONFIRMED;
        sale.confirmedAt = Instant.now();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (PreparedLine item : lines) {
            SaleLine line = new SaleLine();
            line.sale = sale;
            line.variant = item.variant;
            line.quantity = item.quantity;
            line.unitPrice = item.unitPrice;
            line.unitCost = money(item.variant.cost);
            line.discountAmount = item.discountAmount;
            line.taxRate = item.taxRate;
            line.taxAmount = item.taxAmount;
            line.subtotal = item.subtotal;
            line.total = item.total;
            sale.lines.add(line);

            subtotal = subtotal.add(item.subtotal);
            discount = discount.add(item.discountAmount);
            tax = tax.add(item.taxAmount);
            total = total.add(item.total);
        }

        sale.subtotal = money(subtotal);
        sale.discountTotal = money(discount);
        sale.taxTotal = money(tax);
        sale.total = money(total);

        Sale saved = saleRepository.saveAndFlush(sale);
        saved.number = "VTA-" + String.format("%08d", saved.id);
        saleRepository.saveAndFlush(saved);

        String sourceDocument = saved.number;
        for (PreparedLine item : lines) {
            // Si otro proceso consumió stock entre la prevalidación y este punto,
            // el updater con bloqueo pesimista fallará y @Transactional revierte TODO.
            inventoryBalanceUpdater.applyConfirmedMovement(
                    item.variant.id,
                    warehouse.id,
                    InventoryMovementType.SALE,
                    item.quantity.negate(),
                    BigDecimal.ZERO,
                    sourceDocument
            );
        }

        return toResponse(saved);
    }

    private PreparedLine prepareLine(
            ProductVariant variant,
            BigDecimal quantity,
            BigDecimal rawUnitPrice,
            BigDecimal rawDiscount,
            BigDecimal rawTaxRate,
            int lineNumber
    ) {
        if (rawUnitPrice == null || rawUnitPrice.signum() < 0) {
            throw new IllegalArgumentException("El precio unitario de la línea " + lineNumber + " no puede ser negativo");
        }
        BigDecimal unitPrice = money(rawUnitPrice);
        BigDecimal lineSubtotal = money(quantity.multiply(unitPrice));
        BigDecimal discount = rawDiscount == null ? BigDecimal.ZERO : money(rawDiscount);
        BigDecimal taxRate = rawTaxRate == null ? BigDecimal.ZERO : rawTaxRate;

        if (discount.signum() < 0 || discount.compareTo(lineSubtotal) > 0) {
            throw new IllegalArgumentException("El descuento de la línea " + lineNumber + " es inválido");
        }
        if (taxRate.signum() < 0) {
            throw new IllegalArgumentException("El impuesto de la línea " + lineNumber + " es inválido");
        }

        BigDecimal taxableBase = lineSubtotal.subtract(discount);
        BigDecimal taxAmount = money(taxableBase.multiply(taxRate).divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
        BigDecimal total = money(taxableBase.add(taxAmount));
        return new PreparedLine(variant, quantity, unitPrice, discount, taxRate, taxAmount, lineSubtotal, total);
    }

    private Customer requireActiveCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado"));
        if (!customer.active) throw new IllegalArgumentException("El cliente está inactivo");
        return customer;
    }

    private ProductVariant requireActiveVariant(Long id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "El id de la variante no puede ser nulo"
            );
        }

        ProductVariant variant = variantRepository
                .findById(id)
                .orElseThrow(
                        () -> new EntityNotFoundException(
                                "Variante no encontrada: " + id
                        )
                );

        if (!variant.active) {
            throw new IllegalArgumentException(
                    "La variante "
                            + variant.sku
                            + " está inactiva"
            );
        }

        return variant;
    }
    private Warehouse requireActiveWarehouse(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bodega no encontrada"));
        if (!warehouse.active) throw new IllegalArgumentException("La bodega está inactiva");
        return warehouse;
    }

    private void validatePositiveQuantity(BigDecimal quantity, int lineNumber) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad de la línea " + lineNumber + " debe ser mayor que cero");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    SaleResponse toResponse(Sale sale) {

        BigDecimal totalPaid =
                sale.id == null
                        ? BigDecimal.ZERO
                        : paymentRepository.totalPaidBySaleId(sale.id);

        totalPaid = money(
                totalPaid == null
                        ? BigDecimal.ZERO
                        : totalPaid
        );

        BigDecimal pending = money(
                sale.total
                        .subtract(totalPaid)
                        .max(BigDecimal.ZERO)
        );

        return new SaleResponse(
                sale.id,
                sale.number,
                sale.status,
                sale.confirmedAt,

                sale.customer != null ? sale.customer.id : null,
                sale.customer != null ? sale.customer.document : null,
                sale.customer != null ? sale.customer.name : null,

                sale.warehouse != null ? sale.warehouse.id : null,
                sale.warehouse != null ? sale.warehouse.code : null,
                sale.warehouse != null ? sale.warehouse.name : null,

                sale.quotation == null ? null : sale.quotation.id,
                sale.quotation == null ? null : sale.quotation.number,

                sale.subtotal,
                sale.discountTotal,
                sale.taxTotal,
                sale.total,
                totalPaid,
                pending,

                sale.lines.stream()
                        .map(line -> {

                            String productName =
                                    line.variant != null
                                            && line.variant.product != null
                                            ? line.variant.product.name
                                            : "Sin producto asociado";

                            return new SaleLineResponse(
                                    line.id,
                                    line.variant != null ? line.variant.id : null,
                                    line.variant != null ? line.variant.sku : null,
                                    productName,
                                    line.quantity,
                                    line.unitPrice,
                                    line.unitCost,
                                    line.discountAmount,
                                    line.taxRate,
                                    line.taxAmount,
                                    line.subtotal,
                                    line.total
                            );
                        })
                        .toList(),

                sale.payments.stream()
                        .map(payment -> new PaymentResponse(
                                payment.id,
                                payment.method,
                                payment.amount,
                                payment.paidAt,
                                payment.reference
                        ))
                        .toList()
        );
    }

    private record PreparedLine(
            ProductVariant variant, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal discountAmount, BigDecimal taxRate, BigDecimal taxAmount,
            BigDecimal subtotal, BigDecimal total
    ) {}

    @Transactional(readOnly = true)
    public Page<SaleResponse> findAll(
            String number,
            String customer,
            SaleStatus status,
            Pageable pageable
    ) {

        return saleRepository
                .findAllWithFilters(
                        normalize(number),
                        normalize(customer),
                        status,
                        pageable
                )
                .map(this::toResponse);
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
