CREATE TABLE products (
    id INT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    price INT NOT NULL CHECK (price > 0),
    stock INT NOT NULL CHECK (stock >= 0)
) ENGINE=InnoDB;

CREATE TABLE purchases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    product_name VARCHAR(80) NOT NULL,
    price INT NOT NULL CHECK (price > 0),
    money INT NOT NULL,
    change_amount INT NOT NULL,
    purchased_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (money >= price),
    CHECK (change_amount = money - price),
    FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB;

INSERT INTO products (id, name, price, stock) VALUES
    (1, 'Air Mineral Demo', 4000, 10),
    (2, 'Teh Demo', 6000, 8),
    (3, 'Kopi Demo', 8000, 5);
