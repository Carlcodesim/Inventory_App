import { useCallback, useEffect, useMemo, useState } from "react";

type Store = { syncId: string; name: string; address?: string; phone?: string; colorHex?: string };
type CatalogItem = { syncId: string; name: string; type: "CATEGORY" | "SUPPLIER" };
type Product = { syncId: string; storeSyncId: string; name: string; category: string; supplier: string; costPriceMinor: number; salePriceMinor: number; stock: number; minStockAlert: number };
type Tab = "overview" | "stores" | "products" | "catalog";

const TOKEN_KEY = "inventory_web_access_token";
const money = (minor: number) => new Intl.NumberFormat("es-DO", { style: "currency", currency: "DOP" }).format(minor / 100);

function createDeviceId() {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export default function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem(TOKEN_KEY) ?? "");
  const [pairingCode, setPairingCode] = useState("");
  const [pairing, setPairing] = useState(false);
  const [tab, setTab] = useState<Tab>("overview");
  const [stores, setStores] = useState<Store[]>([]);
  const [catalog, setCatalog] = useState<CatalogItem[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [lastUpdated, setLastUpdated] = useState("");

  const api = useCallback(async <T,>(path: string, init: RequestInit = {}): Promise<T> => {
    const response = await fetch(path, {
      ...init,
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}`, ...init.headers }
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || `Error HTTP ${response.status}`);
    return data as T;
  }, [token]);

  const loadData = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError("");
    try {
      const [storeResult, catalogResult, productResult] = await Promise.all([
        api<{ items: Store[] }>("/api/v1/stores"),
        api<{ items: CatalogItem[] }>("/api/v1/catalog"),
        api<{ items: Product[] }>("/api/v1/products")
      ]);
      setStores(storeResult.items);
      setCatalog(catalogResult.items);
      setProducts(productResult.items);
      setLastUpdated(new Date().toLocaleTimeString("es-DO"));
    } catch (e) {
      const message = e instanceof Error ? e.message : "No se pudieron cargar los datos.";
      setError(message);
      if (message.includes("token") || message.includes("dispositivo")) logout();
    } finally {
      setLoading(false);
    }
  }, [api, token]);

  function logout() {
    sessionStorage.removeItem(TOKEN_KEY);
    setToken("");
    setStores([]);
    setCatalog([]);
    setProducts([]);
  }

  async function connect(event: React.FormEvent) {
    event.preventDefault();
    setPairing(true);
    setError("");
    try {
      const response = await fetch("/api/v1/pairing/exchange", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ pairingCode: pairingCode.trim(), deviceId: createDeviceId(), deviceName: "Panel web Inventory_App" })
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || "No se pudo emparejar el panel.");
      sessionStorage.setItem(TOKEN_KEY, data.accessToken);
      setToken(data.accessToken);
      setPairingCode("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Error de conexión.");
    } finally {
      setPairing(false);
    }
  }

  useEffect(() => { if (token) void loadData(); }, [token, loadData]);

  const lowStock = useMemo(() => products.filter((p) => p.stock <= p.minStockAlert), [products]);
  const inventoryValue = useMemo(() => products.reduce((sum, p) => sum + p.costPriceMinor * p.stock, 0), [products]);
  const pageTitle: Record<Tab, string> = { overview: "Resumen general", stores: "Sucursales", products: "Productos", catalog: "Categorías y proveedores" };

  if (!token) return <main className="login-shell">
    <section className="login-card">
      <div className="brand-mark">I</div>
      <p className="eyebrow">INVENTORY_APP · PANEL WEB</p>
      <h1>Tu inventario,<br />bajo control.</h1>
      <p className="muted">Conecta este panel con el servidor de tu PC usando un código de emparejamiento de un solo uso.</p>
      <form onSubmit={connect}>
        <label htmlFor="pairing">Código de emparejamiento</label>
        <input id="pairing" value={pairingCode} onChange={(e) => setPairingCode(e.target.value)} placeholder="Pega aquí tu código" maxLength={20} required autoComplete="off" />
        <button className="primary full" disabled={pairing || pairingCode.trim().length !== 20}>{pairing ? "Conectando…" : "Conectar panel"}</button>
      </form>
      {error && <p className="error">{error}</p>}
      <p className="hint">El código vence a los 5 minutos y solo puede utilizarse una vez. El token se conserva únicamente durante esta sesión del navegador.</p>
    </section>
  </main>;

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark small">I</div><div><strong>INSERC</strong><span>INVENTORY_APP</span></div></div>
      <div className="side-label">MENÚ PRINCIPAL</div>
      <nav>
        {([
          ["overview", "▦", "Resumen"],
          ["stores", "⌂", "Sucursales"],
          ["products", "▤", "Productos"],
          ["catalog", "◇", "Catálogo"]
        ] as [Tab, string, string][]).map(([key, icon, label]) =>
          <button key={key} className={tab === key ? "nav-item active" : "nav-item"} onClick={() => setTab(key)}><span>{icon}</span>{label}</button>
        )}
      </nav>
      <div className="sidebar-bottom"><span className="online-dot" /> Servidor conectado <button className="logout" onClick={logout}>Cerrar sesión</button></div>
    </aside>
    <main className="main-content">
      <header className="topbar"><div><p className="eyebrow">PANEL DE ADMINISTRACIÓN</p><h1>{pageTitle[tab]}</h1></div><div className="top-actions"><span className="server-pill"><i /> En línea</span><button className="refresh" onClick={() => void loadData()} disabled={loading}>↻ Actualizar</button></div></header>
      {error && <div className="alert">{error}</div>}
      {loading && <div className="loading-line">Actualizando información…</div>}
      {tab === "overview" && <>
        <section className="welcome"><div><span className="welcome-tag">VISTA GENERAL</span><h2>Todo en orden, todo bajo control.</h2><p>Consulta el estado de tu inventario desde un solo lugar.</p></div><div className="welcome-symbol">▦</div></section>
        <section className="stats-grid">
          <article className="stat-card"><span className="stat-icon blue">⌂</span><p>Sucursales</p><strong>{stores.length}</strong><small>Registradas en el sistema</small></article>
          <article className="stat-card"><span className="stat-icon purple">▤</span><p>Productos</p><strong>{products.length}</strong><small>Productos activos</small></article>
          <article className="stat-card"><span className="stat-icon orange">!</span><p>Stock bajo</p><strong>{lowStock.length}</strong><small>En o por debajo del mínimo</small></article>
          <article className="stat-card"><span className="stat-icon green">$</span><p>Valor al costo</p><strong>{money(inventoryValue)}</strong><small>Existencias × costo unitario</small></article>
        </section>
        <section className="content-grid">
          <article className="panel"><div className="panel-heading"><div><h3>Productos con stock bajo</h3><p>Requieren revisión</p></div><button className="text-button" onClick={() => setTab("products")}>Ver productos →</button></div>
            {lowStock.length === 0 ? <div className="empty-state"> <span>✓</span><strong>Sin alertas de stock</strong><p>No hay productos por debajo de su mínimo configurado.</p></div> : <div className="table-wrap"><table><thead><tr><th>Producto</th><th>Existencia</th><th>Mínimo</th></tr></thead><tbody>{lowStock.slice(0, 6).map(p => <tr key={p.syncId}><td>{p.name}</td><td><span className="stock-badge">{p.stock}</span></td><td>{p.minStockAlert}</td></tr>)}</tbody></table></div>}
          </article>
          <article className="panel"><div className="panel-heading"><div><h3>Sucursales</h3><p>Ubicaciones registradas</p></div><button className="text-button" onClick={() => setTab("stores")}>Ver todas →</button></div>
            {stores.length === 0 ? <div className="empty-state"><strong>Aún no hay sucursales</strong><p>Las sucursales aparecerán aquí cuando estén registradas.</p></div> : <div className="store-list">{stores.slice(0, 5).map(s => <div className="store-row" key={s.syncId}><span className="store-avatar">{s.name.slice(0,1).toUpperCase()}</span><div><strong>{s.name}</strong><small>{s.address || "Sin dirección registrada"}</small></div><span className="chevron">›</span></div>)}</div>}
          </article>
        </section>
      </>}
      {tab === "stores" && <section className="panel"><div className="panel-heading"><div><h3>Todas las sucursales</h3><p>{stores.length} sucursal(es) activas</p></div></div>{stores.length ? <div className="table-wrap"><table><thead><tr><th>Nombre</th><th>Dirección</th><th>Teléfono</th></tr></thead><tbody>{stores.map(s => <tr key={s.syncId}><td><strong>{s.name}</strong></td><td>{s.address || "—"}</td><td>{s.phone || "—"}</td></tr>)}</tbody></table></div> : <div className="empty-state">No hay sucursales registradas.</div>}</section>}
      {tab === "products" && <section className="panel"><div className="panel-heading"><div><h3>Inventario de productos</h3><p>{products.length} producto(s) activos</p></div></div>{products.length ? <div className="table-wrap"><table><thead><tr><th>Producto</th><th>Categoría</th><th>Existencia</th><th>Precio venta</th><th>Estado</th></tr></thead><tbody>{products.map(p => <tr key={p.syncId}><td><strong>{p.name}</strong><small className="cell-sub">{stores.find(s => s.syncId === p.storeSyncId)?.name || "Sucursal"}</small></td><td>{p.category}</td><td>{p.stock}</td><td>{money(p.salePriceMinor)}</td><td><span className={p.stock <= p.minStockAlert ? "status warning" : "status good"}>{p.stock <= p.minStockAlert ? "Stock bajo" : "Disponible"}</span></td></tr>)}</tbody></table></div> : <div className="empty-state">No hay productos registrados.</div>}</section>}
      {tab === "catalog" && <section className="catalog-grid"><article className="panel"><div className="panel-heading"><div><h3>Categorías</h3><p>Clasificación de productos</p></div></div>{catalog.filter(c => c.type === "CATEGORY").map(c => <div className="catalog-row" key={c.syncId}><span className="catalog-icon">◇</span>{c.name}</div>)}{catalog.filter(c => c.type === "CATEGORY").length === 0 && <p className="muted">Sin categorías.</p>}</article><article className="panel"><div className="panel-heading"><div><h3>Proveedores</h3><p>Directorio de proveedores</p></div></div>{catalog.filter(c => c.type === "SUPPLIER").map(c => <div className="catalog-row" key={c.syncId}><span className="catalog-icon">⌑</span>{c.name}</div>)}{catalog.filter(c => c.type === "SUPPLIER").length === 0 && <p className="muted">Sin proveedores.</p>}</article></section>}
      <footer className="footer"><span>INSERC · Inventory_App</span><span>Última actualización: {lastUpdated || "—"}</span></footer>
    </main>
  </div>;
}
