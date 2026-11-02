
-- HU-19 / Recepción de órdenes de compra
-- Sincronizar los estados permitidos con PurchaseOrderStatus.java

ALTER TABLE public.purchase_order
DROP CONSTRAINT ck_purchase_order_status;

ALTER TABLE public.purchase_order
    ADD CONSTRAINT ck_purchase_order_status
        CHECK (
            status IN (
                       'PENDING_APPROVAL',
                       'APPROVED',
                       'REJECTED',
                       'PARTIALLY_RECEIVED',
                       'RECEIVED'
                )
            );
