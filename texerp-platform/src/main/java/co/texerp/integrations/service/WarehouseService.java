package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.Warehouse;
import co.texerp.integrations.dto.WarehouseDtos.WarehousePage;
import co.texerp.integrations.dto.WarehouseDtos.WarehouseRequest;
import co.texerp.integrations.dto.WarehouseDtos.WarehouseResponse;
import co.texerp.integrations.repository.WarehouseRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class WarehouseService {

    private final WarehouseRepository repository;
    private final AuditService auditService;

    public WarehouseService(WarehouseRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest request, HttpServletRequest httpRequest) {
        String code = normalizeCode(request.code());
        validateUniqueCode(code, null);

        Warehouse warehouse = new Warehouse();
        apply(warehouse, request, code);

        // CA-1: toda bodega nueva queda disponible para operaciones de inventario.
        warehouse.active = true;

        Warehouse saved = saveWithUniqueCodeControl(warehouse);
        auditService.log(
                "CREAR",
                "BODEGA",
                saved.id,
                "Bodega " + saved.code + " creada",
                httpRequest
        );
        return toResponse(saved);
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest request, HttpServletRequest httpRequest) {
        Warehouse warehouse = findEntity(id);
        String code = normalizeCode(request.code());
        validateUniqueCode(code, id);

        apply(warehouse, request, code);

        Warehouse saved = saveWithUniqueCodeControl(warehouse);
        auditService.log(
                "ACTUALIZAR",
                "BODEGA",
                saved.id,
                "Bodega " + saved.code + " actualizada",
                httpRequest
        );
        return toResponse(saved);
    }

    @Transactional
    public WarehouseResponse changeStatus(Long id, boolean active, HttpServletRequest httpRequest) {
        Warehouse warehouse = findEntity(id);

        if (warehouse.active == active) {
            return toResponse(warehouse);
        }

        // CA-3: la bodega nunca se elimina físicamente. Al inactivarla se conserva
        // el registro para que balances y movimientos históricos mantengan su referencia.
        warehouse.active = active;
        Warehouse saved = repository.save(warehouse);

        auditService.log(
                active ? "ACTIVAR" : "INACTIVAR",
                "BODEGA",
                saved.id,
                "Bodega " + saved.code + (active ? " activada" : " inactivada"),
                httpRequest
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public WarehousePage search(String code,
                                String name,
                                String location,
                                Boolean active,
                                int page,
                                int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        var result = repository.search(
                normalizeFilter(code),
                normalizeFilter(name),
                normalizeFilter(location),
                active,
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by("name").ascending().and(Sort.by("id").ascending())
                )
        );

        return new WarehousePage(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    /**
     * Punto de control para ventas, recepciones y ajustes de inventario.
     * Debe invocarse antes de registrar una operación que afecte existencias.
     */
    @Transactional(readOnly = true)
    public Warehouse requireActive(Long id) {
        Warehouse warehouse = findEntity(id);
        if (!warehouse.active) {
            throw new BusinessConflictException(
                    "La bodega está inactiva y no puede utilizarse en ventas, recepciones ni ajustes de inventario"
            );
        }
        return warehouse;
    }

    private Warehouse findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bodega no encontrada"));
    }

    private void validateUniqueCode(String code, Long excludedId) {
        boolean exists = excludedId == null
                ? repository.existsByCodeIgnoreCase(code)
                : repository.existsByCodeIgnoreCaseAndIdNot(code, excludedId);

        if (exists) {
            throw duplicateCodeConflict();
        }
    }

    private Warehouse saveWithUniqueCodeControl(Warehouse warehouse) {
        try {
            return repository.saveAndFlush(warehouse);
        } catch (DataIntegrityViolationException exception) {
            // La restricción UNIQUE de la BD cubre también solicitudes concurrentes.
            throw duplicateCodeConflict();
        }
    }

    private BusinessConflictException duplicateCodeConflict() {
        return new BusinessConflictException(
                "El código de bodega ya está registrado"
        );
    }

    private void apply(Warehouse warehouse, WarehouseRequest request, String code) {
        warehouse.code = code;
        warehouse.name = normalizeRequired(request.name());
        warehouse.location = normalizeRequired(request.location());
    }

    private String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRequired(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeFilter(String value) {
        return value == null ? "" : value.trim();
    }

    private WarehouseResponse toResponse(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.id,
                warehouse.code,
                warehouse.name,
                warehouse.location,
                warehouse.active,
                warehouse.createdAt,
                warehouse.updatedAt
        );
    }
}
