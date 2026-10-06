CREATE USER identity_user WITH PASSWORD 'identity_password';
CREATE USER catalog_user WITH PASSWORD 'catalog_password';
CREATE USER order_user WITH PASSWORD 'order_password';
CREATE USER payment_user WITH PASSWORD 'payment_password';
CREATE USER notification_user WITH PASSWORD 'notification_password';

CREATE DATABASE identity_db OWNER identity_user;
CREATE DATABASE catalog_db OWNER catalog_user;
CREATE DATABASE order_db OWNER order_user;
CREATE DATABASE payment_db OWNER payment_user;
CREATE DATABASE notification_db OWNER notification_user;
