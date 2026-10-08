-- HU-03 Administrar roles y permisos

CREATE TABLE IF NOT EXISTS app_role (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(255),
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_role_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS role_permission (
    role_id BIGINT NOT NULL,
    permission VARCHAR(100) NOT NULL,
    CONSTRAINT pk_role_permission PRIMARY KEY (role_id, permission),
    CONSTRAINT fk_role_permission_role
        FOREIGN KEY (role_id) REFERENCES app_role(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS app_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_app_user_role PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_app_user_role_user
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_app_user_role_role
        FOREIGN KEY (role_id) REFERENCES app_role(id) ON DELETE CASCADE
);

INSERT INTO app_role (name, description, system_role, created_at, updated_at)
VALUES
    ('ADMINISTRADOR', 'Acceso administrativo completo', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ANALISTA', 'Consulta y análisis operativo', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('VENDEDOR', 'Gestión comercial', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;

-- Migra el rol único histórico a la nueva relación N:M.
INSERT INTO app_user_role (user_id, role_id)
SELECT u.id, r.id
FROM app_user u
JOIN app_role r ON upper(r.name) = upper(cast(u.role as varchar))
WHERE u.role IS NOT NULL
ON CONFLICT DO NOTHING;

-- La columna antigua se conserva temporalmente por seguridad de rollback,
-- pero deja de ser obligatoria y ya no es utilizada por JPA.
ALTER TABLE app_user ALTER COLUMN role DROP NOT NULL;

-- Permisos del administrador: catálogo completo actual.
INSERT INTO role_permission (role_id, permission)
SELECT r.id, p.permission
FROM app_role r
CROSS JOIN (VALUES
    ('DASHBOARD_READ'),
    ('USER_READ'), ('USER_CREATE'), ('USER_UPDATE'), ('USER_DELETE'),
    ('ROLE_READ'), ('ROLE_CREATE'), ('ROLE_UPDATE'), ('ROLE_DELETE'), ('PERMISSION_READ'),
    ('AUDIT_READ'),
    ('CUSTOMER_READ'), ('CUSTOMER_CREATE'), ('CUSTOMER_UPDATE'),
    ('WAREHOUSE_READ'), ('WAREHOUSE_CREATE'), ('WAREHOUSE_UPDATE'),
    ('PRODUCT_READ'), ('PRODUCT_CREATE'), ('PRODUCT_UPDATE'),
    ('PRODUCT_VARIANT_READ'), ('PRODUCT_VARIANT_CREATE'), ('PRODUCT_VARIANT_UPDATE'),
    ('SUPPLIER_READ'), ('SUPPLIER_CREATE'), ('SUPPLIER_UPDATE'),
    ('INVENTORY_READ'), ('INVENTORY_MOVEMENT_READ'), ('INVENTORY_ADJUST'), ('INVENTORY_NEGATIVE_ADJUST'),
    ('QUOTATION_READ'), ('QUOTATION_CREATE'), ('QUOTATION_UPDATE'), ('QUOTATION_CONVERT'),
    ('SALE_READ'), ('SALE_CREATE'), ('PAYMENT_CREATE')
) AS p(permission)
WHERE r.name = 'ADMINISTRADOR'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission)
SELECT r.id, p.permission
FROM app_role r
CROSS JOIN (VALUES
    ('DASHBOARD_READ'),
    ('INVENTORY_READ'), ('INVENTORY_MOVEMENT_READ'),
    ('QUOTATION_READ'), ('SALE_READ')
) AS p(permission)
WHERE r.name = 'ANALISTA'
ON CONFLICT DO NOTHING;

INSERT INTO role_permission (role_id, permission)
SELECT r.id, p.permission
FROM app_role r
CROSS JOIN (VALUES
    ('DASHBOARD_READ'), ('CUSTOMER_READ'), ('INVENTORY_READ'),
    ('QUOTATION_READ'), ('QUOTATION_CREATE'), ('QUOTATION_UPDATE'), ('QUOTATION_CONVERT'),
    ('SALE_READ'), ('SALE_CREATE'), ('PAYMENT_CREATE')
) AS p(permission)
WHERE r.name = 'VENDEDOR'
ON CONFLICT DO NOTHING;
