package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.InventoryMovement;
import co.texerp.integrations.domain.InventoryMovementType;
import co.texerp.integrations.dto.InventoryMovementDtos.InventoryMovementPage;
import co.texerp.integrations.dto.InventoryMovementDtos.InventoryMovementResponse;
import co.texerp.integrations.repository.InventoryMovementRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class InventoryMovementService {

    private final InventoryMovementRepository repository;
    private final InventoryBalanceUpdater balanceUpdater;

    public InventoryMovementService(
            InventoryMovementRepository repository,
            InventoryBalanceUpdater balanceUpdater
    ) {
        this.repository = repository;
        this.balanceUpdater = balanceUpdater;
    }

    @Transactional(readOnly = true)
    public InventoryMovementPage search(
            Instant fromDate,
            Instant toDate,
            String sku,
            String warehouse,
            String type,
            String document,
            int page,
            int size
    ) {
        validateDateRange(fromDate, toDate);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        InventoryMovementType movementType = parseType(type);

        var result = repository.search(
                fromDate,
                toDate,
                normalizeFilter(sku),
                normalizeFilter(warehouse),
                movementType,
                normalizeFilter(document),
                PageRequest.of(safePage, safeSize)
        );

        return new InventoryMovementPage(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public InventoryMovementResponse findById(Long id) {
        return toResponse(requireDetailed(id));
    }

    @Transactional
    public InventoryMovementResponse compensate(
            Long movementId,
            String sourceDocument,
            String reason
    ) {
        InventoryMovement original = repository.findForCompensation(movementId)
                .orElseThrow(() -> new EntityNotFoundException("Movimiento de inventario no encontrado"));

        if (repository.existsCompensationFor(movementId)) {
            throw new BusinessConflictException("El movimiento ya posee un movimiento compensatorio");
        }

        var result = balanceUpdater.applyCompensatingMovement(
                original,
                sourceDocument,
                reason
        );

        return toResponse(result.movement());
    }

    @Transactional(readOnly = true)
    public void rejectMutation(Long id) {
        requireDetailed(id);
        throw new BusinessConflictException(
                "Los movimientos confirmados son inmutables. Cree un movimiento compensatorio para corregirlo"
        );
    }

    private InventoryMovement requireDetailed(Long id) {
        return repository.findDetailedById(id)
                .orElseThrow(() -> new EntityNotFoundException("Movimiento de inventario no encontrado"));
    }

    private InventoryMovementResponse toResponse(InventoryMovement movement) {
        return new InventoryMovementResponse(
                movement.id,
                movement.variant.id,
                movement.variant.sku,
                movement.variant.product.id,
                movement.variant.product.code,
                movement.variant.product.name,
                movement.warehouse.id,
                movement.warehouse.code,
                movement.warehouse.name,
                movement.type,
                movement.quantity,
                movement.reservedDelta,
                movement.previousAvailable,
                movement.newAvailable,
                movement.previousReserved,
                movement.newReserved,
                movement.sourceDocument,
                movement.performedBy,
                movement.movementAt,
                movement.reason,
                movement.compensatesMovement == null ? null : movement.compensatesMovement.id
        );
    }

    private InventoryMovementType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return InventoryMovementType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Tipo de movimiento inválido. Valores permitidos: SALE, RECEIPT, ADJUSTMENT, COMPENSATION"
            );
        }
    }

    private void validateDateRange(Instant fromDate, Instant toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la fecha final");
        }
    }

    private String normalizeFilter(String value) {
        return value == null ? "" : value.trim();
    }
}
