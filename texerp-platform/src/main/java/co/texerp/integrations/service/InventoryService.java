package co.texerp.integrations.service;

import co.texerp.integrations.domain.InventoryBalance;
import co.texerp.integrations.domain.InventoryStatus;
import co.texerp.integrations.domain.ProductVariant;
import co.texerp.integrations.domain.Warehouse;
import co.texerp.integrations.dto.InventoryDtos.InventoryBalanceResponse;
import co.texerp.integrations.dto.InventoryDtos.InventoryPage;
import co.texerp.integrations.dto.InventoryDtos.SkuInventoryResponse;
import co.texerp.integrations.repository.InventoryBalanceRepository;
import co.texerp.integrations.repository.ProductVariantRepository;
import co.texerp.integrations.repository.WarehouseRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
public class InventoryService {

    private final InventoryBalanceRepository repository;
    private final ProductVariantRepository variantRepository;
    private final WarehouseRepository warehouseRepository;
    private final AuditService auditService;

    public InventoryService(
            InventoryBalanceRepository repository,
            ProductVariantRepository variantRepository,
            WarehouseRepository warehouseRepository,
            AuditService auditService
    ) {
        this.repository = repository;
        this.variantRepository = variantRepository;
        this.warehouseRepository = warehouseRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public InventoryPage search(
            String sku,
            String product,
            String warehouse,
            String status,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedStatus = normalizeStatus(status);

        var result = repository.search(
                normalizeFilter(sku),
                normalizeFilter(product),
                normalizeFilter(warehouse),
                normalizedStatus,
                PageRequest.of(safePage, safeSize)
        );

        return new InventoryPage(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public SkuInventoryResponse findBySku(String sku) {
        String normalizedSku = required(sku);
        ProductVariant variant = variantRepository.findBySkuIgnoreCase(normalizedSku)
                .orElseThrow(() -> new EntityNotFoundException("Variante no encontrada para el SKU indicado"));

        var balances = repository.findAllBySku(normalizedSku)
                .stream()
                .map(this::toResponse)
                .toList();

        // CA-3: si la variante existe pero todavía no posee balances, se responde
        // correctamente con una lista vacía en lugar de generar un error técnico.
        return new SkuInventoryResponse(
                variant.id,
                variant.sku,
                variant.product.id,
                variant.product.code,
                variant.product.name,
                balances
        );
    }

    @Transactional
    public InventoryBalanceResponse configureMinimum(
            Long variantId,
            Long warehouseId,
            BigDecimal minimum,
            HttpServletRequest httpRequest
    ) {
        ProductVariant variant = findVariant(variantId);
        Warehouse warehouse = findWarehouse(warehouseId);

        if (minimum == null || minimum.signum() < 0) {
            throw new IllegalArgumentException("El mínimo debe ser igual o mayor a cero");
        }

        InventoryBalance balance = repository
                .findForUpdate(variantId, warehouseId)
                .orElseGet(() -> newBalance(variant, warehouse));

        balance.minimumQuantity = minimum;
        InventoryBalance saved = repository.save(balance);

        auditService.log(
                "CONFIGURAR_MINIMO",
                "INVENTARIO",
                saved.id,
                "Mínimo actualizado para SKU " + variant.sku + " en bodega " + warehouse.code,
                httpRequest
        );

        return toResponse(saved);
    }

    private ProductVariant findVariant(Long id) {
        return variantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Variante no encontrada"));
    }

    private Warehouse findWarehouse(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bodega no encontrada"));
    }

    private InventoryBalance newBalance(ProductVariant variant, Warehouse warehouse) {
        InventoryBalance balance = new InventoryBalance();
        balance.variant = variant;
        balance.warehouse = warehouse;
        balance.availableQuantity = BigDecimal.ZERO;
        balance.reservedQuantity = BigDecimal.ZERO;
        balance.minimumQuantity = BigDecimal.ZERO;
        return balance;
    }

    private InventoryBalanceResponse toResponse(InventoryBalance balance) {
        InventoryStatus status = calculateStatus(balance);
        boolean lowStock = balance.availableQuantity.compareTo(balance.minimumQuantity) <= 0;

        return new InventoryBalanceResponse(
                balance.id,
                balance.variant.id,
                balance.variant.sku,
                balance.variant.product.id,
                balance.variant.product.code,
                balance.variant.product.name,
                balance.warehouse.id,
                balance.warehouse.code,
                balance.warehouse.name,
                balance.availableQuantity,
                balance.reservedQuantity,
                balance.minimumQuantity,
                status,
                lowStock,
                balance.updatedAt
        );
    }

    private InventoryStatus calculateStatus(InventoryBalance balance) {
        if (balance.availableQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return InventoryStatus.OUT_OF_STOCK;
        }
        if (balance.availableQuantity.compareTo(balance.minimumQuantity) <= 0) {
            return InventoryStatus.LOW_STOCK;
        }
        return InventoryStatus.AVAILABLE;
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        return switch (normalized) {
            case "AVAILABLE", "DISPONIBLE" -> InventoryStatus.AVAILABLE.name();
            case "LOW_STOCK", "LOW", "BAJO_MINIMO", "BAJO_MÍNIMO" -> InventoryStatus.LOW_STOCK.name();
            case "OUT_OF_STOCK", "SIN_EXISTENCIA", "AGOTADO" -> InventoryStatus.OUT_OF_STOCK.name();
            default -> throw new IllegalArgumentException(
                    "Estado de inventario inválido. Use AVAILABLE, LOW_STOCK u OUT_OF_STOCK"
            );
        };
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El SKU es obligatorio");
        }
        return value.trim();
    }

    private String normalizeFilter(String value) {
        return value == null ? "" : value.trim();
    }
}
