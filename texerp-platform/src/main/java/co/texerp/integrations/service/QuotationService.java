package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.*;
import co.texerp.integrations.dto.QuotationDtos.*;
import co.texerp.integrations.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class QuotationService {

    private static final int MONEY_SCALE = 2;

    private final QuotationRepository quotationRepository;
    private final SaleRepository saleRepository;
    private final CustomerRepository customerRepository;
    private final ProductVariantRepository variantRepository;
    private final SaleService saleService;

    public QuotationService(
            QuotationRepository quotationRepository,
            SaleRepository saleRepository,
            CustomerRepository customerRepository,
            ProductVariantRepository variantRepository,
            SaleService saleService
    ) {
        this.quotationRepository = quotationRepository;
        this.saleRepository = saleRepository;
        this.customerRepository = customerRepository;
        this.variantRepository = variantRepository;
        this.saleService = saleService;
    }

    @Transactional(readOnly = true)
    public Page<QuotationResponse> findAll(
            String number,
            String customer,
            QuotationStatus status,
            Pageable pageable
    ) {

        return quotationRepository
                .findAllWithFilters(
                        normalize(number),
                        normalize(customer),
                        status,
                        pageable
                )
                .map(this::toResponse);
    }

    @Transactional
    public QuotationResponse create(
            QuotationCreateRequest request
    ) {

        validateDates(
                request.issueDate(),
                request.expirationDate()
        );

        Customer customer = customerRepository
                .findById(request.customerId())
                .orElseThrow(
                        () -> new EntityNotFoundException(
                                "Cliente no encontrado"
                        )
                );

        if (!customer.active) {
            throw new IllegalArgumentException(
                    "El cliente está inactivo y no puede utilizarse en una cotización"
            );
        }

        Quotation quotation = new Quotation();

        quotation.customer = customer;
        quotation.issueDate = request.issueDate();
        quotation.expirationDate = request.expirationDate();
        quotation.conditions =
                normalizeOptional(request.conditions());
        quotation.status = QuotationStatus.DRAFT;

        BigDecimal subtotal = BigDecimal.ZERO;

        for (int i = 0; i < request.lines().size(); i++) {

            QuotationLineRequest lineRequest =
                    request.lines().get(i);

            validatePositiveQuantity(
                    lineRequest.quantity(),
                    i + 1
            );

            ProductVariant variant =
                    requireActiveVariant(
                            lineRequest.variantId()
                    );

            BigDecimal unitPrice =
                    money(lineRequest.unitPrice());

            BigDecimal lineSubtotal =
                    money(
                            lineRequest.quantity()
                                    .multiply(unitPrice)
                    );

            QuotationLine line =
                    new QuotationLine();

            line.quotation = quotation;
            line.variant = variant;
            line.quantity = lineRequest.quantity();
            line.unitPrice = unitPrice;
            line.subtotal = lineSubtotal;

            quotation.lines.add(line);

            subtotal =
                    subtotal.add(lineSubtotal);
        }

        quotation.subtotal = money(subtotal);
        quotation.total = quotation.subtotal;

        Quotation saved =
                quotationRepository
                        .saveAndFlush(quotation);

        saved.number =
                "COT-" +
                        String.format("%08d", saved.id);

        quotationRepository
                .saveAndFlush(saved);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public QuotationResponse findById(
            Long id
    ) {

        Quotation quotation =
                quotationRepository
                        .findDetailedById(id)
                        .orElseThrow(
                                () -> new EntityNotFoundException(
                                        "Cotización no encontrada"
                                )
                        );

        return toResponse(quotation);
    }

    @Transactional
    public QuotationResponse send(
            Long id
    ) {

        Quotation quotation =
                quotationRepository
                        .findForUpdate(id)
                        .orElseThrow(
                                () -> new EntityNotFoundException(
                                        "Cotización no encontrada"
                                )
                        );

        if (
                quotation.status ==
                        QuotationStatus.CANCELLED
        ) {

            throw new BusinessConflictException(
                    "La cotización está cancelada y no puede enviarse"
            );
        }

        if (
                quotation.status ==
                        QuotationStatus.CONVERTED
        ) {

            throw convertedConflict(
                    quotation.id
            );
        }

        if (isExpired(quotation)) {

            throw new BusinessConflictException(
                    "La cotización está vencida y no puede enviarse"
            );
        }

        quotation.status =
                QuotationStatus.SENT;

        return toResponse(
                quotationRepository.save(
                        quotation
                )
        );
    }

    @Transactional
    public QuotationResponse cancel(
            Long id
    ) {

        Quotation quotation =
                quotationRepository
                        .findForUpdate(id)
                        .orElseThrow(
                                () -> new EntityNotFoundException(
                                        "Cotización no encontrada"
                                )
                        );

        if (
                quotation.status ==
                        QuotationStatus.CONVERTED
        ) {

            throw convertedConflict(
                    quotation.id
            );
        }

        if (
                quotation.status ==
                        QuotationStatus.CANCELLED
        ) {

            return toResponse(
                    quotation
            );
        }

        quotation.status =
                QuotationStatus.CANCELLED;

        return toResponse(
                quotationRepository.save(
                        quotation
                )
        );
    }

    @Transactional
    public co.texerp.integrations.dto.SaleDtos.SaleResponse convert(
            Long id,
            ConvertQuotationRequest request
    ) {

        // Primero bloqueamos la cotización para impedir doble conversión.
        Quotation quotation = quotationRepository
                .findForUpdate(id)
                .orElseThrow(
                        () -> new EntityNotFoundException(
                                "Cotización no encontrada"
                        )
                );

        // Luego forzamos la carga completa de cliente, líneas,
        // variantes y productos dentro de la misma transacción.
        quotation = quotationRepository
                .findDetailedById(id)
                .orElseThrow(
                        () -> new EntityNotFoundException(
                                "Cotización no encontrada"
                        )
                );

        if (
                quotation.status == QuotationStatus.CONVERTED
                        || saleRepository.findByQuotationId(id).isPresent()
        ) {
            throw convertedConflict(id);
        }

        if (quotation.status == QuotationStatus.CANCELLED) {
            throw new BusinessConflictException(
                    "La cotización está cancelada y no puede convertirse"
            );
        }

        if (isExpired(quotation)) {
            throw new BusinessConflictException(
                    "La cotización está vencida y no puede convertirse"
            );
        }

        if (quotation.customer == null || quotation.customer.id == null) {
            throw new IllegalArgumentException(
                    "La cotización no tiene un cliente asociado"
            );
        }

        if (!quotation.customer.active) {
            throw new IllegalArgumentException(
                    "El cliente está inactivo y la cotización no puede convertirse"
            );
        }

        if (quotation.lines == null || quotation.lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "La cotización debe contener al menos una línea"
            );
        }

        for (int i = 0; i < quotation.lines.size(); i++) {

            QuotationLine line = quotation.lines.get(i);

            if (line.variant == null) {
                throw new IllegalArgumentException(
                        "La línea "
                                + (i + 1)
                                + " de la cotización no tiene una variante asociada"
                );
            }

            if (line.variant.id == null) {
                throw new IllegalArgumentException(
                        "La variante de la línea "
                                + (i + 1)
                                + " no fue cargada correctamente"
                );
            }
        }

        var sale = saleService.createFromQuotation(
                quotation,
                request.warehouseId()
        );

        quotation.status = QuotationStatus.CONVERTED;

        quotationRepository.save(quotation);

        return sale;
    }

    private BusinessConflictException convertedConflict(
            Long quotationId
    ) {

        return saleRepository
                .findByQuotationId(
                        quotationId
                )
                .map(
                        sale ->
                                new BusinessConflictException(
                                        "La cotización ya fue convertida. Venta existente: "
                                                + sale.number
                                                + " (id "
                                                + sale.id
                                                + ")"
                                )
                )
                .orElseGet(
                        () ->
                                new BusinessConflictException(
                                        "La cotización ya fue convertida"
                                )
                );
    }

    private ProductVariant requireActiveVariant(
            Long id
    ) {

        ProductVariant variant =
                variantRepository
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

    private void validateDates(
            LocalDate issueDate,
            LocalDate expirationDate
    ) {

        if (
                expirationDate.isBefore(
                        issueDate
                )
        ) {

            throw new IllegalArgumentException(
                    "La fecha de vencimiento no puede ser anterior a la fecha de emisión"
            );
        }
    }

    private void validatePositiveQuantity(
            BigDecimal quantity,
            int lineNumber
    ) {

        if (
                quantity == null
                        ||
                        quantity.signum() <= 0
        ) {

            throw new IllegalArgumentException(
                    "La cantidad de la línea "
                            + lineNumber
                            + " debe ser mayor que cero"
            );
        }
    }

    private boolean isExpired(
            Quotation quotation
    ) {

        return LocalDate
                .now()
                .isAfter(
                        quotation.expirationDate
                );
    }

    private BigDecimal money(
            BigDecimal value
    ) {

        return value.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private String normalize(
            String value
    ) {

        return value == null
                || value.isBlank()
                ? null
                : value.trim();
    }

    private String normalizeOptional(
            String value
    ) {

        if (
                value == null
                        ||
                        value.isBlank()
        ) {

            return null;
        }

        return value.trim();
    }

    private QuotationResponse toResponse(
            Quotation quotation
    ) {

        var sale =
                quotation.id == null
                        ? null
                        : saleRepository
                        .findByQuotationId(
                                quotation.id
                        )
                        .orElse(null);

        List<QuotationLineResponse> lines =
                quotation.lines == null
                        ? List.of()
                        : quotation.lines
                        .stream()
                        .map(
                                line -> {

                                    Long variantId =
                                            line.variant != null
                                                    ? line.variant.id
                                                    : null;

                                    String sku =
                                            line.variant != null
                                                    ? line.variant.sku
                                                    : null;

                                    String productName =
                                            line.variant != null
                                                    &&
                                                    line.variant.product != null
                                                    ? line.variant.product.name
                                                    : "Sin producto asociado";

                                    return new QuotationLineResponse(
                                            line.id,
                                            variantId,
                                            sku,
                                            productName,
                                            line.quantity,
                                            line.unitPrice,
                                            line.subtotal
                                    );
                                }
                        )
                        .toList();

        return new QuotationResponse(
                quotation.id,
                quotation.number,

                quotation.customer != null
                        ? quotation.customer.id
                        : null,

                quotation.customer != null
                        ? quotation.customer.document
                        : null,

                quotation.customer != null
                        ? quotation.customer.name
                        : null,

                quotation.issueDate,
                quotation.expirationDate,
                quotation.status,

                quotation.expirationDate != null
                        && isExpired(quotation),

                quotation.conditions,
                quotation.subtotal,
                quotation.total,

                sale != null
                        ? sale.id
                        : null,

                sale != null
                        ? sale.number
                        : null,

                lines
        );
    }
}