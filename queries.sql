USE auction_system;

SELECT VERSION();

SELECT TABLE_NAME, ENGINE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'auction_system'
AND TABLE_NAME IN ('users', 'products', 'auctions', 'bids');

SHOW CREATE TABLE products;
SHOW CREATE TABLE auctions;
SHOW CREATE TABLE bids;

USE auction_system;

SELECT
    TABLE_NAME,
    COLUMN_NAME,
    CONSTRAINT_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'auction_system'
AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME;

SELECT TABLE_NAME, ENGINE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'auction_system'
AND TABLE_NAME IN ('users', 'products', 'auctions', 'bids');

USE auction_system;

DESCRIBE products;

SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'auction_system'
  AND TABLE_NAME = 'products'
  AND COLUMN_NAME = 'image_path';
  
  USE auction_system;

ALTER TABLE products
ADD COLUMN image_path VARCHAR(255) NULL;

DESCRIBE products;
  