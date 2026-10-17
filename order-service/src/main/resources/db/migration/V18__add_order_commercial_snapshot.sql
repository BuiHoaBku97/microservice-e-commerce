ALTER TABLE orders ADD COLUMN sales_channel VARCHAR(20) NOT NULL DEFAULT 'WEB';
ALTER TABLE orders ADD COLUMN subtotal BIGINT;
ALTER TABLE orders ADD COLUMN shipping_fee BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN payment_method VARCHAR(30) NOT NULL DEFAULT 'MOCK_ONLINE';
ALTER TABLE orders ADD COLUMN customer_note VARCHAR(1000);

UPDATE orders SET subtotal = total_amount - shipping_fee WHERE subtotal IS NULL;

ALTER TABLE orders ALTER COLUMN subtotal SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT ck_orders_sales_channel CHECK (sales_channel = 'WEB');
ALTER TABLE orders ADD CONSTRAINT ck_orders_subtotal CHECK (subtotal > 0);
ALTER TABLE orders ADD CONSTRAINT ck_orders_shipping_fee CHECK (shipping_fee >= 0);
ALTER TABLE orders ADD CONSTRAINT ck_orders_total_amount
    CHECK (total_amount = subtotal + shipping_fee);
ALTER TABLE orders ADD CONSTRAINT ck_orders_payment_method
    CHECK (payment_method = 'MOCK_ONLINE');

ALTER TABLE order_items ADD COLUMN product_id BIGINT;
ALTER TABLE order_items ADD COLUMN sku_code VARCHAR(100);
ALTER TABLE order_items ADD COLUMN product_name VARCHAR(255);
ALTER TABLE order_items ADD COLUMN color VARCHAR(50);
ALTER TABLE order_items ADD COLUMN size VARCHAR(30);
ALTER TABLE order_items ADD COLUMN currency VARCHAR(3);

UPDATE order_items oi
SET product_id = 0,
    sku_code = 'SKU-' || oi.sku_id,
    product_name = 'Legacy product',
    color = '',
    size = '',
    currency = o.currency
FROM orders o
WHERE o.id = oi.order_id;

ALTER TABLE order_items ALTER COLUMN product_id SET NOT NULL;
ALTER TABLE order_items ALTER COLUMN sku_code SET NOT NULL;
ALTER TABLE order_items ALTER COLUMN product_name SET NOT NULL;
ALTER TABLE order_items ALTER COLUMN color SET NOT NULL;
ALTER TABLE order_items ALTER COLUMN size SET NOT NULL;
ALTER TABLE order_items ALTER COLUMN currency SET NOT NULL;
