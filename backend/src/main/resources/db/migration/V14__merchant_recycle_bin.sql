ALTER TABLE shop DROP CONSTRAINT shop_merchant_state_check;
ALTER TABLE shop ADD CONSTRAINT shop_merchant_state_check
    CHECK (merchant_state IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED', 'DELETED'));
ALTER TABLE shop ADD CONSTRAINT deleted_merchant_unpublished CHECK (merchant_state <> 'DELETED' OR NOT published);
