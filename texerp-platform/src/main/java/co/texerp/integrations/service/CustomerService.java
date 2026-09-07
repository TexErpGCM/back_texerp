package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.Customer;
import co.texerp.integrations.dto.CustomerDtos.CustomerPage;
import co.texerp.integrations.dto.CustomerDtos.CustomerRequest;
import co.texerp.integrations.dto.CustomerDtos.CustomerResponse;
import co.texerp.integrations.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final AuditService auditService;

    public CustomerService(CustomerRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request, HttpServletRequest httpRequest) {
        String document = normalizeRequired(request.document());
        validateUniqueDocument(document, null);

        Customer customer = new Customer();
        apply(customer, request, document);

        Customer saved = saveWithUniqueDocumentControl(customer);
        auditService.log(
                "CREAR",
                "CLIENTE",
                saved.id,
                "Cliente " + saved.document + " creado",
                httpRequest
        );
        return toResponse(saved);
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request, HttpServletRequest httpRequest) {
        Customer customer = findEntity(id);
        String document = normalizeRequired(request.document());
        validateUniqueDocument(document, id);

        apply(customer, request, document);

        Customer saved = saveWithUniqueDocumentControl(customer);
        auditService.log(
                "ACTUALIZAR",
                "CLIENTE",
                saved.id,
                "Cliente " + saved.document + " actualizado",
                httpRequest
        );
        return toResponse(saved);
    }

    @Transactional
    public CustomerResponse changeStatus(Long id, boolean active, HttpServletRequest httpRequest) {
        Customer customer = findEntity(id);

        if (customer.active == active) {
            return toResponse(customer);
        }

        customer.active = active;
        Customer saved = repository.save(customer);

        auditService.log(
                active ? "ACTIVAR" : "INACTIVAR",
                "CLIENTE",
                saved.id,
                "Cliente " + saved.document + (active ? " activado" : " inactivado"),
                httpRequest
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public CustomerPage search(String document,
                               String name,
                               String type,
                               Boolean active,
                               int page,
                               int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        var result = repository.search(
                normalizeFilter(document),
                normalizeFilter(name),
                normalizeFilter(type),
                active,
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by("name").ascending().and(Sort.by("id").ascending())
                )
        );

        return new CustomerPage(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    /**
     * Punto de control que deben utilizar cotizaciones y ventas antes de asociar
     * un cliente a una nueva transacción. Conserva los clientes inactivos para
     * consultas históricas, pero impide utilizarlos en operaciones nuevas.
     */
    @Transactional(readOnly = true)
    public Customer requireActive(Long id) {
        Customer customer = findEntity(id);
        if (!customer.active) {
            throw new BusinessConflictException(
                    "El cliente está inactivo y no puede utilizarse en nuevas ventas o cotizaciones"
            );
        }
        return customer;
    }

    private Customer findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado"));
    }

    private void validateUniqueDocument(String document, Long excludedId) {
        boolean exists = excludedId == null
                ? repository.existsByDocumentIgnoreCase(document)
                : repository.existsByDocumentIgnoreCaseAndIdNot(document, excludedId);

        if (exists) {
            throw duplicateDocumentConflict();
        }
    }

    private Customer saveWithUniqueDocumentControl(Customer customer) {
        try {
            return repository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException exception) {
            // La restricción única de BD cubre también condiciones de carrera
            // entre dos solicitudes concurrentes con el mismo documento.
            throw duplicateDocumentConflict();
        }
    }

    private BusinessConflictException duplicateDocumentConflict() {
        return new BusinessConflictException(
                "El documento del cliente ya está asociado a otro cliente"
        );
    }

    private void apply(Customer customer, CustomerRequest request, String document) {
        customer.document = document;
        customer.name = normalizeRequired(request.name());
        customer.type = normalizeRequired(request.type());
        customer.email = normalizeOptional(request.email());
        customer.phone = normalizeOptional(request.phone());
        customer.classification = normalizeOptional(request.classification());
        customer.active = request.active();
    }

    private String normalizeRequired(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeFilter(String value) {
        return value == null ? "" : value.trim();
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.id,
                customer.document,
                customer.name,
                customer.type,
                customer.email,
                customer.phone,
                customer.classification,
                customer.active,
                customer.createdAt,
                customer.updatedAt
        );
    }
}
