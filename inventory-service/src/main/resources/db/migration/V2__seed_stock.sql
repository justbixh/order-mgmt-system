-- Demo catalogue. SKU-LIMITED has a single unit so "out of stock" is easy to demonstrate.
INSERT INTO stock_items (product_id, name, available_quantity) VALUES
    ('SKU-LAPTOP',     'Laptop 14"',        10),
    ('SKU-PHONE',      'Smartphone',        25),
    ('SKU-HEADPHONES', 'Headphones',       100),
    ('SKU-KEYBOARD',   'Mechanical keyboard', 50),
    ('SKU-LIMITED',    'Limited edition',    1);
