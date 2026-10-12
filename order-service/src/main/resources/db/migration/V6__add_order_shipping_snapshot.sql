ALTER TABLE orders
    ADD COLUMN shipping_address_id BIGINT,
    ADD COLUMN shipping_recipient_name VARCHAR(150),
    ADD COLUMN shipping_recipient_phone VARCHAR(30),
    ADD COLUMN shipping_address_line VARCHAR(255),
    ADD COLUMN shipping_ward VARCHAR(100),
    ADD COLUMN shipping_district VARCHAR(100),
    ADD COLUMN shipping_province VARCHAR(100);
