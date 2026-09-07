package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.Product;
import co.texerp.integrations.dto.ProductDtos.ProductPage;
import co.texerp.integrations.dto.ProductDtos.ProductRequest;
import co.texerp.integrations.dto.ProductDtos.ProductResponse;
import co.texerp.integrations.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final ProductRepository repository;
    private final AuditService auditService;

    public ProductService(ProductRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public ProductResponse create(ProductRequest request, HttpServletRequest httpRequest) {
        String code = normalizeRequired(request.code());
        validateUniqueCode(code, null);
        Product product = new Product();
        apply(product, request, code);
        Product saved = repository.save(product);
        auditService.log("CREAR", "PRODUCTO", saved.id, "Producto " + saved.code + " creado", httpRequest);
        return toResponse(saved);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request, HttpServletRequest httpRequest) {
        Product product = findEntity(id);
        String code = normalizeRequired(request.code());
        validateUniqueCode(code, id);
        apply(product, request, code);
        Product saved = repository.save(product);
        auditService.log("ACTUALIZAR", "PRODUCTO", saved.id, "Producto " + saved.code + " actualizado", httpRequest);
        return toResponse(saved);
    }

    @Transactional
    public ProductResponse changeStatus(Long id, boolean active, HttpServletRequest httpRequest) {
        Product product = findEntity(id);
        product.active = active;
        Product saved = repository.save(product);
        auditService.log(active ? "ACTIVAR" : "INACTIVAR", "PRODUCTO", saved.id,
                "Producto " + saved.code + (active ? " activado" : " inactivado"), httpRequest);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public ProductPage search(String code, String name, String fabricType, Boolean active, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        var result = repository.search(normalizeFilter(code), normalizeFilter(name),
                normalizeFilter(fabricType), active,
                PageRequest.of(safePage, safeSize, Sort.by("name").ascending().and(Sort.by("id"))));
        return new ProductPage(result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** Punto de control para cotizaciones, ventas y órdenes de compra. */
    @Transactional(readOnly = true)
    public Product requireActive(Long id) {
        Product product = findEntity(id);
        if (!product.active) {
            throw new BusinessConflictException("El producto está inactivo y no puede utilizarse en nuevas transacciones");
        }
        return product;
    }

    private Product findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado"));
    }

    private void validateUniqueCode(String code, Long excludedId) {
        boolean exists = excludedId == null
                ? repository.existsByCodeIgnoreCase(code)
                : repository.existsByCodeIgnoreCaseAndIdNot(code, excludedId);
        if (exists) {
            throw new BusinessConflictException("El código del producto ya está en uso");
        }
    }

    private void apply(Product product, ProductRequest request, String code) {
        product.code = code;
        product.name = normalizeRequired(request.name());
        product.fabricType = normalizeRequired(request.fabricType());
        product.composition = normalizeRequired(request.composition());
        product.active = request.active();
    }

    private String normalizeRequired(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeFilter(String value) {
        return value == null ? "" : value.trim();
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(p.id, p.code, p.name, p.fabricType, p.composition,
                p.active, p.createdAt, p.updatedAt);
    }
}
