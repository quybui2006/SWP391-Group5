-- Create table for storing seller account activation identity (CCCD & Tax)
CREATE TABLE IF NOT EXISTS seller_activations (
    user_id BIGINT PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(254) NOT NULL,
    id_card_name VARCHAR(150) NOT NULL,
    id_card_number VARCHAR(50) NOT NULL,
    id_card_date DATE NOT NULL,
    id_card_place VARCHAR(150) NOT NULL,
    business_type VARCHAR(50) NOT NULL DEFAULT 'INDIVIDUAL',
    tax_code VARCHAR(50) NULL,
    tax_name VARCHAR(150) NULL,
    tax_email VARCHAR(254) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
