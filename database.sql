CREATE DATABASE IF NOT EXISTS auction_system;

USE auction_system;

-- Expected role values: BUYER, SELLER, ADMIN.
CREATE TABLE IF NOT EXISTS users (
    user_id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL
);
