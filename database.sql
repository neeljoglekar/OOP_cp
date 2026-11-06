-- Create the database and auction persistence tables.
-- Existing users and account data are preserved; no DROP or data deletion is used.
CREATE DATABASE IF NOT EXISTS auction_system;

USE auction_system;

-- Existing account table. CREATE IF NOT EXISTS does not alter an existing table.
-- Expected role values are BUYER, SELLER, and ADMIN.
CREATE TABLE IF NOT EXISTS users (
    user_id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL
) ENGINE=InnoDB;

-- Product IDs are Java Strings. Product currently has no owner field, so seller
-- ownership is recorded by the auction that offers the product.
CREATE TABLE IF NOT EXISTS products (
    product_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    `condition` VARCHAR(100) NOT NULL,
    image_path VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Add the image reference to an existing products table without replacing rows.
SET @image_path_column_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'products'
      AND COLUMN_NAME = 'image_path'
);
SET @image_path_migration = IF(
    @image_path_column_exists = 0,
    'ALTER TABLE products ADD COLUMN image_path VARCHAR(255) NULL',
    'SELECT ''products.image_path already exists'''
);
PREPARE image_path_migration_statement FROM @image_path_migration;
EXECUTE image_path_migration_statement;
DEALLOCATE PREPARE image_path_migration_statement;

-- Auction IDs are Java Strings. `closed` stores only manual closure; the
-- effective status is derived from `closed` and `end_time`. A NULL end_time
-- represents the Java LocalDateTime.MAX no-expiry compatibility value.
-- The seller relationship belongs to Auction in the Java model.
CREATE TABLE IF NOT EXISTS auctions (
    auction_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci PRIMARY KEY,
    product_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    seller_id INT NOT NULL,
    end_time DATETIME(6) NULL,
    minimum_bid_increment DECIMAL(19, 4) NOT NULL,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_auctions_seller (seller_id),
    INDEX idx_auctions_end_time (end_time),
    CONSTRAINT chk_auctions_increment_positive CHECK (minimum_bid_increment > 0),
    CONSTRAINT fk_auctions_product
        FOREIGN KEY (product_id) REFERENCES products (product_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_auctions_seller
        FOREIGN KEY (seller_id) REFERENCES users (user_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Highest bid and winner are derived from bid rows and are not duplicated here.
-- DATETIME(6) stores microseconds; the DAO should persist Bid.getTimestamp().
CREATE TABLE IF NOT EXISTS bids (
    bid_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    auction_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    buyer_id INT NOT NULL,
    bid_amount DECIMAL(19, 4) NOT NULL,
    bid_timestamp DATETIME(6) NOT NULL,
    INDEX idx_bids_auction_amount (auction_id, bid_amount),
    INDEX idx_bids_auction_timestamp (auction_id, bid_timestamp),
    INDEX idx_bids_buyer_auction (buyer_id, auction_id),
    CONSTRAINT chk_bids_amount_positive CHECK (bid_amount > 0),
    CONSTRAINT fk_bids_auction
        FOREIGN KEY (auction_id) REFERENCES auctions (auction_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_bids_buyer
        FOREIGN KEY (buyer_id) REFERENCES users (user_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
