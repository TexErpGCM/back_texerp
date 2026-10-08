CREATE TABLE quotation (
    id BIGSERIAL PRIMARY KEY,
    number VARCHAR(30),
    customer_id BIGINT NOT NULL,
    issue_date DATE NOT NULL,
    expiration_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    conditions VARCHAR(1000),
    subtotal NUMERIC(19,2) NOT NULL DEFAULT 0,
    total NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_quotation_number UNIQUE (number),
    CONSTRAINT fk_quotation_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT ck_quotation_dates CHECK (expiration_date >= issue_date),
    CONSTRAINT ck_quotation_total CHECK (subtotal >= 0 AND total >= 0)
);

CREATE TABLE quotation_line (
    id BIGSERIAL PRIMARY KEY,
    quotation_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity NUMERIC(19,3) NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    subtotal NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_quotation_line_quotation FOREIGN KEY (quotation_id) REFERENCES quotation(id),
    CONSTRAINT fk_quotation_line_variant FOREIGN KEY (variant_id) REFERENCES product_variant(id),
    CONSTRAINT ck_quotation_line_quantity CHECK (quantity > 0),
    CONSTRAINT ck_quotation_line_price CHECK (unit_price >= 0 AND subtotal >= 0)
);

CREATE TABLE sale (
    id BIGSERIAL PRIMARY KEY,
    number VARCHAR(30),
    customer_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    quotation_id BIGINT,
    status VARCHAR(30) NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    subtotal NUMERIC(19,2) NOT NULL DEFAULT 0,
    discount_total NUMERIC(19,2) NOT NULL DEFAULT 0,
    tax_total NUMERIC(19,2) NOT NULL DEFAULT 0,
    total NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_sale_number UNIQUE (number),
    CONSTRAINT uk_sale_quotation UNIQUE (quotation_id),
    CONSTRAINT fk_sale_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_sale_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id),
    CONSTRAINT fk_sale_quotation FOREIGN KEY (quotation_id) REFERENCES quotation(id),
    CONSTRAINT ck_sale_amounts CHECK (subtotal >= 0 AND discount_total >= 0 AND tax_total >= 0 AND total >= 0)
);

CREATE TABLE sale_line (
    id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    variant_id BIGINT NOT NULL,
    quantity NUMERIC(19,3) NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    unit_cost NUMERIC(19,2) NOT NULL,
    discount_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    tax_rate NUMERIC(9,4) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(19,2) NOT NULL,
    total NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_sale_line_sale FOREIGN KEY (sale_id) REFERENCES sale(id),
    CONSTRAINT fk_sale_line_variant FOREIGN KEY (variant_id) REFERENCES product_variant(id),
    CONSTRAINT ck_sale_line_quantity CHECK (quantity > 0),
    CONSTRAINT ck_sale_line_amounts CHECK (
        unit_price >= 0 AND unit_cost >= 0 AND discount_amount >= 0 AND tax_rate >= 0 AND tax_amount >= 0 AND subtotal >= 0 AND total >= 0
    )
);

CREATE TABLE payment (
    id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    method VARCHAR(50) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reference VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_payment_sale FOREIGN KEY (sale_id) REFERENCES sale(id),
    CONSTRAINT ck_payment_amount CHECK (amount > 0)
);

CREATE INDEX idx_quotation_customer ON quotation(customer_id);
CREATE INDEX idx_quotation_status ON quotation(status);
CREATE INDEX idx_quotation_expiration ON quotation(expiration_date);
CREATE INDEX idx_quotation_line_quotation ON quotation_line(quotation_id);
CREATE INDEX idx_sale_customer ON sale(customer_id);
CREATE INDEX idx_sale_warehouse ON sale(warehouse_id);
CREATE INDEX idx_sale_status ON sale(status);
CREATE INDEX idx_sale_line_sale ON sale_line(sale_id);
CREATE INDEX idx_payment_sale ON payment(sale_id);
CREATE INDEX idx_payment_paid_at ON payment(paid_at);
