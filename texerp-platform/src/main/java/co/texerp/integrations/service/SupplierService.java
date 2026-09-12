package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.Supplier;
import co.texerp.integrations.dto.SupplierDtos.SupplierPage;
import co.texerp.integrations.dto.SupplierDtos.SupplierRequest;
import co.texerp.integrations.dto.SupplierDtos.SupplierResponse;
import co.texerp.integrations.repository.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class SupplierService {

    private final SupplierRepository repository;
    private final AuditService auditService;

    public SupplierService(SupplierRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request, HttpServletRequest httpRequest) {
        String taxId = normalizeRequired(request.taxId());
        validateUniqueTaxId(taxId, null);

        Supplier supplier = new Supplier();
        apply(supplier, request, taxId);

        // CA-1: todo proveedor recién creado queda habilitado para nuevas órdenes.
        supplier.active = true;

        Supplier saved = saveWithUniqueTaxIdControl(supplier);
        auditService.log(
                "CREAR",
                "PROVEEDOR",
                saved.id,
                "Proveedor " + saved.taxId + " creado",
                httpRequest
        );
        return toResponse(saved);
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request, HttpServletRequest httpRequest) {
        Supplier supplier = findEntity(id);
        String taxId = normalizeRequired(request.taxId());
        validateUniqueTaxId(taxId, id);

        apply(supplier, request, taxId);

        Supplier saved = saveWithUniqueTaxIdControl(supplier);
        auditService.log(
                "ACTUALIZAR",
                "PROVEEDOR",
                saved.id,
                "Proveedor " + saved.taxId + " actualizado",
                httpRequest
        );
        return toResponse(saved);
    }

    @Transactional
    public SupplierResponse changeStatus(Long id, boolean active, HttpServletRequest httpRequest) {
        Supplier supplier = findEntity(id);

        if (supplier.active == active) {
            return toResponse(supplier);
        }

        // No se elimina el registro. Esto conserva cualquier orden histórica
        // que ya esté asociada al proveedor.
        supplier.active = active;
        Supplier saved = repository.save(supplier);

        auditService.log(
                active ? "ACTIVAR" : "INACTIVAR",
                "PROVEEDOR",
                saved.id,
                "Proveedor " + saved.taxId + (active ? " activado" : " inactivado"),
                httpRequest
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SupplierResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public SupplierPage search(String taxId,
                               String name,
                               Boolean active,
                               int page,
                               int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        var result = repository.search(
                normalizeFilter(taxId),
                normalizeFilter(name),
                active,
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by("name").ascending().and(Sort.by("id").ascending())
                )
        );

        return new SupplierPage(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    /**
     * Debe ser usado por el servicio de órdenes de compra antes de asociar
     * un proveedor a una orden nueva. Los proveedores inactivos continúan
     * existiendo para consultas históricas, pero quedan bloqueados para compras nuevas.
     */
    @Transactional(readOnly = true)
    public Supplier requireActive(Long id) {
        Supplier supplier = findEntity(id);
        if (!supplier.active) {
            throw new BusinessConflictException(
                    "El proveedor está inactivo y no puede seleccionarse en nuevas órdenes de compra"
            );
        }
        return supplier;
    }

    private Supplier findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Proveedor no encontrado"));
    }

    private void validateUniqueTaxId(String taxId, Long excludedId) {
        boolean exists = excludedId == null
                ? repository.existsByTaxIdIgnoreCase(taxId)
                : repository.existsByTaxIdIgnoreCaseAndIdNot(taxId, excludedId);

        if (exists) {
            throw duplicateTaxIdConflict();
        }
    }

    private Supplier saveWithUniqueTaxIdControl(Supplier supplier) {
        try {
            return repository.saveAndFlush(supplier);
        } catch (DataIntegrityViolationException exception) {
            // La restricción UNIQUE de la BD también cubre solicitudes concurrentes.
            throw duplicateTaxIdConflict();
        }
    }

    private BusinessConflictException duplicateTaxIdConflict() {
        return new BusinessConflictException(
                "La identificación tributaria ya está asociada a otro proveedor"
        );
    }

    private void apply(Supplier supplier, SupplierRequest request, String taxId) {
        supplier.taxId = taxId;
        supplier.name = normalizeRequired(request.name());
        supplier.phone = normalizeRequired(request.phone());
        supplier.email = normalizeEmail(request.email());
    }

    private String normalizeRequired(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeFilter(String value) {
        return value == null ? "" : value.trim();
    }

    private SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.id,
                supplier.taxId,
                supplier.name,
                supplier.phone,
                supplier.email,
                supplier.active,
                supplier.createdAt,
                supplier.updatedAt
        );
    }
}
