-- examples/04-db-check.json üçün demo baza (H2 in-memory). Hər bağlantıda işləyir, ona görə idempotentdir.
CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100));
MERGE INTO users KEY (id) VALUES (1, 'Leanne Graham', 'Sincere@april.biz');
MERGE INTO users KEY (id) VALUES (2, 'Ervin Howell', 'Shanna@melissa.tv');
