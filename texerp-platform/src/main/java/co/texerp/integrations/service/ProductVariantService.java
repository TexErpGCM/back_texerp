
package co.texerp.integrations.service;

import co.texerp.integrations.config.BusinessConflictException;
import co.texerp.integrations.domain.Product;
import co.texerp.integrations.domain.ProductVariant;
import co.texerp.integrations.domain.UnitOfMeasure;
import co.texerp.integrations.dto.ProductVariantDtos.*;
import co.texerp.integrations.repository.ProductRepository;
import co.texerp.integrations.repository.ProductVariantRepository;
import co.texerp.integrations.repository.UnitOfMeasureRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductVariantService {

    private final ProductVariantRepository repository;
    private final ProductRepository productRepository;
    private final UnitOfMeasureRepository unitRepository;
    private final AuditService auditService;
    private final EntityManager entityManager;

    public ProductVariantService(
            ProductVariantRepository repository,
            ProductRepository productRepository,
            UnitOfMeasureRepository unitRepository,
            AuditService auditService,
            EntityManager entityManager
    ) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.unitRepository = unitRepository;
        this.auditService = auditService;
        this.entityManager = entityManager;
    }

    @Transactional
    public VariantResponse create(
            Long productId,
            VariantRequest request,
            HttpServletRequest http
    ) {
        Product product = findProduct(productId);

        if (!product.active) {
            throw new BusinessConflictException(
                    "El producto padre está inactivo"
            );
        }

        String sku = required(request.sku());
        validateUniqueSku(sku, null);

        UnitOfMeasure unit = findActiveUnit(request.unitCode());

        ProductVariant variant = new ProductVariant();
        variant.product = product;

        apply(variant, request, sku, unit);

        ProductVariant saved = repository.save(variant);

        auditService.log(
                "CREAR",
                "VARIANTE_PRODUCTO",
                saved.id,
                "Variante " + saved.sku +
                        " creada para producto " + product.code,
                http
        );

        return toResponse(saved);
    }

    @Transactional
    public VariantResponse update(
            Long id,
            VariantRequest request,
            HttpServletRequest http
    ) {
        ProductVariant variant = findEntity(id);

        String sku = required(request.sku());
        validateUniqueSku(sku, id);

        UnitOfMeasure unit = findActiveUnit(request.unitCode());

        apply(variant, request, sku, unit);

        ProductVariant saved = repository.save(variant);

        auditService.log(
                "ACTUALIZAR",
                "VARIANTE_PRODUCTO",
                saved.id,
                "Variante " + saved.sku + " actualizada",
                http
        );

        return toResponse(saved);
    }

    @Transactional
    public VariantResponse updatePriceCost(
            Long id,
            PriceCostRequest request,
            HttpServletRequest http
    ) {
        ProductVariant variant = findEntity(id);

        variant.cost = request.cost();
        variant.salePrice = request.salePrice();

        ProductVariant saved = repository.save(variant);

        auditService.log(
                "ACTUALIZAR_PRECIO_COSTO",
                "VARIANTE_PRODUCTO",
                saved.id,
                "Costo y precio vigentes actualizados para SKU "
                        + saved.sku,
                http
        );

        return toResponse(saved);
    }

    @Transactional
    public VariantResponse changeStatus(
            Long id,
            boolean active,
            HttpServletRequest http
    ) {
        ProductVariant variant = findEntity(id);

        variant.active = active;

        ProductVariant saved = repository.save(variant);

        auditService.log(
                active ? "ACTIVAR" : "INACTIVAR",
                "VARIANTE_PRODUCTO",
                saved.id,
                "Variante " + saved.sku +
                        (active ? " activada" : " inactivada"),
                http
        );

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public VariantResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public VariantPage search(
            Long productId,
            String sku,
            String color,
            Boolean active,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        var result = repository.search(
                productId,
                filter(sku),
                filter(color),
                active,
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by("sku")
                                .ascending()
                                .and(Sort.by("id"))
                )
        );

        return new VariantPage(
                result.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ProductVariant requireActive(Long id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "El ID de la variante es obligatorio"
            );
        }

        ProductVariant variant = findEntity(id);

        if (!variant.active) {
            throw new BusinessConflictException(
                    "La variante de producto está inactiva"
            );
        }

        // Validar el producto directamente en la base de datos.
        // No depender de variant.product.active.
        Long productId = entityManager
                .createQuery("""
            SELECT p.id
            FROM ProductVariant v
            JOIN v.product p
            WHERE v.id = :variantId
            """, Long.class)
                .setParameter("variantId", id)
                .getResultStream()
                .findFirst()
                .orElseThrow(() ->
                        new BusinessConflictException(
                                "La variante no tiene un producto padre válido"
                        )
                );

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Producto padre no encontrado: " + productId
                        )
                );

        if (!product.active) {
            throw new BusinessConflictException(
                    "El producto asociado a la variante está inactivo"
            );
        }

        return variant;
    }

    private Product findProduct(Long id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "El ID del producto es obligatorio"
            );
        }

        return productRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Producto padre no encontrado"
                        )
                );
    }

    private ProductVariant findEntity(Long id) {

        if (id == null) {
            throw new IllegalArgumentException(
                    "El ID de la variante es obligatorio"
            );
        }

        return repository.findByIdWithRelations(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Variante no encontrada: " + id
                        )
                );
    }

    private Long entityId(Object entity) {

        if (entity == null) {
            throw new IllegalArgumentException(
                    "La entidad es obligatoria"
            );
        }

        Object id = entityManager
                .getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .getIdentifier(entity);

        if (!(id instanceof Long identifier)) {
            throw new IllegalStateException(
                    "No fue posible obtener el identificador JPA"
            );
        }

        return identifier;
    }

    private UnitOfMeasure findActiveUnit(String code) {

        UnitOfMeasure unit = unitRepository
                .findByCodeIgnoreCase(required(code))
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Unidad de medida no encontrada"
                        )
                );

        if (!unit.active) {
            throw new BusinessConflictException(
                    "La unidad de medida está inactiva"
            );
        }

        return unit;
    }

    private void validateUniqueSku(
            String sku,
            Long excludedId
    ) {
        boolean exists = excludedId == null
                ? repository.existsBySkuIgnoreCase(sku)
                : repository.existsBySkuIgnoreCaseAndIdNot(
                sku,
                excludedId
        );

        if (exists) {
            throw new BusinessConflictException(
                    "El SKU ya está en uso"
            );
        }
    }

    private void apply(
            ProductVariant v,
            VariantRequest r,
            String sku,
            UnitOfMeasure unit
    ) {
        v.sku = sku;
        v.color = required(r.color());
        v.pattern = filter(r.pattern());
        v.width = r.width();
        v.unit = unit;
        v.cost = r.cost();
        v.salePrice = r.salePrice();
        v.active = r.active();
    }

    private String required(String value) {
        return value == null ? null : value.trim();
    }

    private String filter(String value) {
        return value == null ? "" : value.trim();
    }

    private VariantResponse toResponse(ProductVariant v) {

        Long productId = entityId(v.product);
        Product product = findProduct(productId);

        Long unitId = entityId(v.unit);

        UnitOfMeasure unit = unitRepository.findById(unitId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Unidad de medida no encontrada"
                        )
                );

        return new VariantResponse(
                v.id,
                productId,
                product.code,
                v.sku,
                v.color,
                v.pattern,
                v.width,
                unit.code,
                unit.name,
                v.cost,
                v.salePrice,
                v.active,
                v.createdAt,
                v.updatedAt
        );
    }
}
