CREATE TABLE stores (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sync_id TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  logo_url TEXT NOT NULL DEFAULT '',
  address TEXT NOT NULL DEFAULT '',
  phone TEXT NOT NULL DEFAULT '',
  color_hex TEXT NOT NULL DEFAULT '#0284C7',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  version INTEGER NOT NULL DEFAULT 1,
  deleted_at INTEGER
);

CREATE TABLE catalog_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sync_id TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('CATEGORY', 'SUPPLIER')),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  version INTEGER NOT NULL DEFAULT 1,
  deleted_at INTEGER,
  UNIQUE(type, name)
);

CREATE TABLE products (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sync_id TEXT NOT NULL UNIQUE,
  store_sync_id TEXT NOT NULL REFERENCES stores(sync_id),
  name TEXT NOT NULL,
  category TEXT NOT NULL,
  supplier TEXT NOT NULL DEFAULT '',
  cost_price_minor INTEGER NOT NULL CHECK (cost_price_minor >= 0),
  sale_price_minor INTEGER NOT NULL CHECK (sale_price_minor >= 0),
  stock INTEGER NOT NULL CHECK (stock >= 0),
  min_stock_alert INTEGER NOT NULL DEFAULT 5 CHECK (min_stock_alert >= 0),
  photo_url TEXT NOT NULL DEFAULT '',
  sale_link TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  version INTEGER NOT NULL DEFAULT 1,
  deleted_at INTEGER
);

CREATE INDEX products_store_sync_id_idx ON products(store_sync_id);
CREATE INDEX products_updated_at_idx ON products(updated_at);

CREATE TABLE sales (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sync_id TEXT NOT NULL UNIQUE,
  operation_id TEXT NOT NULL UNIQUE,
  store_sync_id TEXT NOT NULL REFERENCES stores(sync_id),
  product_sync_id TEXT NOT NULL REFERENCES products(sync_id),
  product_name TEXT NOT NULL,
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  unit_cost_minor INTEGER NOT NULL CHECK (unit_cost_minor >= 0),
  unit_price_minor INTEGER NOT NULL CHECK (unit_price_minor >= 0),
  total_amount_minor INTEGER NOT NULL CHECK (total_amount_minor >= 0),
  net_profit_minor INTEGER NOT NULL,
  customer_note TEXT NOT NULL DEFAULT '',
  timestamp INTEGER NOT NULL
);

CREATE INDEX sales_timestamp_idx ON sales(timestamp);
CREATE INDEX sales_store_sync_id_idx ON sales(store_sync_id);

CREATE TABLE stock_movements (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sync_id TEXT NOT NULL UNIQUE,
  operation_id TEXT,
  store_sync_id TEXT NOT NULL REFERENCES stores(sync_id),
  store_name TEXT NOT NULL,
  product_sync_id TEXT NOT NULL REFERENCES products(sync_id),
  product_name TEXT NOT NULL,
  movement_type TEXT NOT NULL CHECK (
    movement_type IN ('ENTRADA', 'VENTA', 'AJUSTE_POSITIVO', 'AJUSTE_NEGATIVO', 'TRASPASO')
  ),
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  previous_stock INTEGER NOT NULL CHECK (previous_stock >= 0),
  new_stock INTEGER NOT NULL CHECK (new_stock >= 0),
  reason TEXT NOT NULL DEFAULT '',
  timestamp INTEGER NOT NULL
);

CREATE INDEX stock_movements_timestamp_idx ON stock_movements(timestamp);
CREATE INDEX stock_movements_product_sync_id_idx ON stock_movements(product_sync_id);

CREATE TABLE devices (
  device_id TEXT PRIMARY KEY,
  device_name TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  last_seen_at INTEGER,
  revoked_at INTEGER
);

CREATE TABLE pairing_codes (
  code_hash TEXT PRIMARY KEY,
  expires_at INTEGER NOT NULL,
  consumed_at INTEGER
);

CREATE TABLE operation_receipts (
  operation_id TEXT PRIMARY KEY,
  device_id TEXT NOT NULL REFERENCES devices(device_id),
  response_json TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE TABLE change_log (
  cursor INTEGER PRIMARY KEY AUTOINCREMENT,
  entity_type TEXT NOT NULL,
  entity_sync_id TEXT NOT NULL,
  operation TEXT NOT NULL CHECK (operation IN ('upsert', 'delete')),
  payload_json TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE INDEX change_log_entity_idx ON change_log(entity_type, entity_sync_id);
