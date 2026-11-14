import { useCallback, useEffect, useMemo, useState } from "react";

type Store = { syncId: string; name: string; logoUrl?: string; address?: string; phone?: string; colorHex?: string };
type CatalogItem = { syncId: string; name: string; type: "CATEGORY" | "SUPPLIER" };
type Product = { syncId: string; storeSyncId: string; name: string; category: string; supplier: string; costPriceMinor: number; salePriceMinor: number; stock: number; minStockAlert: number; photoUrl?: string; saleLink?: string };
type Tab = "overview" | "stores" | "products" | "catalog";
type Editor = { kind: "store" | "category" | "supplier" | "product" | "adjust"; id?: string };
type Draft = Record<string, string>;
const TOKEN_KEY = "inventory_web_access_token";
const money = (minor: number) => new Intl.NumberFormat("es-DO", { style: "currency", currency: "DOP" }).format(minor / 100);
const blank = (value?: string | number) => value === undefined || value === null ? "" : String(value);
function createDeviceId() {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
  const hex = [...bytes].map(b => b.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
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
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [lastUpdated, setLastUpdated] = useState("");
  const [editor, setEditor] = useState<Editor | null>(null);
  const [draft, setDraft] = useState<Draft>({});

  const api = useCallback(async <T,>(path: string, init: RequestInit = {}): Promise<T> => {
    const response = await fetch(path, { ...init, headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}`, ...init.headers } });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || `Error HTTP ${response.status}`);
    return data as T;
  }, [token]);

  const loadData = useCallback(async () => {
    if (!token) return;
    setLoading(true); setError("");
    try {
      const [s, c, p] = await Promise.all([
        api<{items:Store[]}>("/api/v1/stores"), api<{items:CatalogItem[]}>("/api/v1/catalog"), api<{items:Product[]}>("/api/v1/products")
      ]);
      setStores(s.items); setCatalog(c.items); setProducts(p.items); setLastUpdated(new Date().toLocaleTimeString("es-DO"));
    } catch (e) {
      const message = e instanceof Error ? e.message : "No se pudieron cargar los datos.";
      setError(message);
      if (message.includes("token") || message.includes("dispositivo")) logout();
    } finally { setLoading(false); }
  }, [api, token]);

  function logout() {
    sessionStorage.removeItem(TOKEN_KEY); setToken(""); setStores([]); setCatalog([]); setProducts([]);
  }
  async function connect(event: React.FormEvent) {
    event.preventDefault(); setPairing(true); setError("");
    try {
      const response = await fetch("/api/v1/pairing/exchange", { method:"POST", headers:{"Content-Type":"application/json"}, body:JSON.stringify({pairingCode:pairingCode.trim(),deviceId:createDeviceId(),deviceName:"Panel web Inventory_App"}) });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || "No se pudo emparejar el panel.");
      sessionStorage.setItem(TOKEN_KEY,data.accessToken); setToken(data.accessToken); setPairingCode("");
    } catch(e) { setError(e instanceof Error ? e.message : "Error de conexión."); } finally { setPairing(false); }
  }
  useEffect(() => { if (token) void loadData(); }, [token,loadData]);

  const lowStock = useMemo(() => products.filter(p => p.stock <= p.minStockAlert),[products]);
  const inventoryValue = useMemo(() => products.reduce((sum,p) => sum+p.costPriceMinor*p.stock,0),[products]);
  const pageTitle:Record<Tab,string> = {overview:"Resumen general",stores:"Sucursales",products:"Productos",catalog:"Categorías y proveedores"};

  function openEditor(next:Editor, values:Draft = {}) { setError(""); setNotice(""); setEditor(next); setDraft(values); }
  function editStore(s:Store) { openEditor({kind:"store",id:s.syncId},{name:s.name,logoUrl:blank(s.logoUrl),address:blank(s.address),phone:blank(s.phone),colorHex:s.colorHex || "#0284C7"}); }
  function editCatalog(c:CatalogItem) { openEditor({kind:c.type==="CATEGORY"?"category":"supplier",id:c.syncId},{name:c.name}); }
  function editProduct(p:Product) { openEditor({kind:"product",id:p.syncId},{name:p.name,category:p.category,supplier:p.supplier,costPrice:blank(p.costPriceMinor/100),salePrice:blank(p.salePriceMinor/100),minStockAlert:blank(p.minStockAlert),photoUrl:blank(p.photoUrl),saleLink:blank(p.saleLink)}); }
  function newProduct() { openEditor({kind:"product"},{storeSyncId:stores[0]?.syncId ?? "",name:"",category:catalog.find(c=>c.type==="CATEGORY")?.name ?? "",supplier:"",costPrice:"0",salePrice:"0",stock:"0",minStockAlert:"5",photoUrl:"",saleLink:""}); }
  function newStore() { openEditor({kind:"store"},{name:"",address:"",phone:"",logoUrl:"",colorHex:"#0284C7"}); }
  function newCatalog(kind:"category"|"supplier") { openEditor({kind},{name:""}); }
  function setField(key:string,value:string) { setDraft(current=>({...current,[key]:value})); }

  async function saveEditor(event:React.FormEvent) {
    event.preventDefault(); if(!editor) return;
    setSaving(true); setError(""); setNotice("");
    try {
      let path="", method="POST", body:Record<string,unknown>={};
      if(editor.kind==="store") {
        path=editor.id? `/api/v1/stores/${editor.id}`:"/api/v1/stores"; method=editor.id?"PATCH":"POST";
        body={name:draft.name,logoUrl:draft.logoUrl??"",address:draft.address??"",phone:draft.phone??"",colorHex:draft.colorHex??"#0284C7"};
      } else if(editor.kind==="category"||editor.kind==="supplier") {
        path=editor.id?`/api/v1/catalog/${editor.id}`:"/api/v1/catalog"; method=editor.id?"PATCH":"POST";
        body=editor.id?{name:draft.name}:{name:draft.name,type:editor.kind==="category"?"CATEGORY":"SUPPLIER"};
      } else if(editor.kind==="product") {
        path=editor.id?`/api/v1/products/${editor.id}`:"/api/v1/products"; method=editor.id?"PATCH":"POST";
        body={name:draft.name,category:draft.category,supplier:draft.supplier,costPriceMinor:Math.round(Number(draft.costPrice)*100),salePriceMinor:Math.round(Number(draft.salePrice)*100),minStockAlert:Number(draft.minStockAlert),photoUrl:draft.photoUrl??"",saleLink:draft.saleLink??""};
        if(!editor.id) body={...body,storeSyncId:draft.storeSyncId,stock:Number(draft.stock)};
      } else {
        path="/api/v1/sync/operations";
        body={operations:[{operationId:createDeviceId(),type:"stock.adjust",payload:{storeSyncId:draft.storeSyncId,productSyncId:editor.id,delta:Number(draft.delta),reason:draft.reason}}]};
      }
      await api(path,{method,body:JSON.stringify(body)});
      setEditor(null); setNotice(editor.kind==="adjust"?"Ajuste de inventario registrado correctamente.":"Cambios guardados correctamente."); await loadData();
    } catch(e) { setError(e instanceof Error?e.message:"No se pudo guardar."); } finally { setSaving(false); }
  }

  async function removeItem(kind:"store"|"catalog"|"product",id:string,label:string) {
    if(!window.confirm(`¿Seguro que deseas eliminar "${label}"? Esta acción conserva el historial y puede estar sujeta a restricciones de inventario.`)) return;
    setError(""); setNotice("");
    try { await api(`/api/v1/${kind==="catalog"?"catalog":kind==="store"?"stores":"products"}/${id}`,{method:"DELETE"}); setNotice("Elemento eliminado correctamente."); await loadData(); }
    catch(e) { setError(e instanceof Error?e.message:"No se pudo eliminar."); }
  }

  async function submitAdjustment(event:React.FormEvent) { await saveEditor(event); }

  if(!token) return <main className="login-shell"><section className="login-card"><div className="brand-mark">I</div><p className="eyebrow">INVENTORY_APP · PANEL WEB</p><h1>Tu inventario,<br/>bajo control.</h1><p className="muted">Conecta este panel con el servidor de tu PC usando un código de emparejamiento de un solo uso.</p><form onSubmit={connect}><label htmlFor="pairing">Código de emparejamiento</label><input id="pairing" value={pairingCode} onChange={e=>setPairingCode(e.target.value)} placeholder="Pega aquí tu código" maxLength={20} required autoComplete="off"/><button className="primary full" disabled={pairing||pairingCode.trim().length!==20}>{pairing?"Conectando…":"Conectar panel"}</button></form>{error&&<p className="error">{error}</p>}<p className="hint">El código vence a los 5 minutos y solo puede utilizarse una vez. El token se conserva únicamente durante esta sesión del navegador.</p></section></main>;

  return <div className="app-shell">
    <aside className="sidebar"><div className="brand"><div className="brand-mark small">I</div><div><strong>INSERC</strong><span>INVENTORY_APP</span></div></div><div className="side-label">MENÚ PRINCIPAL</div><nav>{([["overview","▦","Resumen"],["stores","⌂","Sucursales"],["products","▤","Productos"],["catalog","◇","Catálogo"]] as [Tab,string,string][]).map(([key,icon,label])=><button key={key} className={tab===key?"nav-item active":"nav-item"} onClick={()=>setTab(key)}><span>{icon}</span>{label}</button>)}</nav><div className="sidebar-bottom"><span className="online-dot"/> Servidor conectado <button className="logout" onClick={logout}>Cerrar sesión</button></div></aside>
    <main className="main-content">
      <header className="topbar"><div><p className="eyebrow">PANEL DE ADMINISTRACIÓN</p><h1>{pageTitle[tab]}</h1></div><div className="top-actions"><span className="server-pill"><i/> En línea</span><button className="refresh" onClick={()=>void loadData()} disabled={loading}>↻ Actualizar</button></div></header>
      {error&&<div className="alert">{error}</div>}{notice&&<div className="success-alert">{notice}</div>}{loading&&<div className="loading-line">Actualizando información…</div>}
      {tab==="overview"&&<><section className="welcome"><div><span className="welcome-tag">VISTA GENERAL</span><h2>Todo en orden, todo bajo control.</h2><p>Consulta y administra tu inventario desde un solo lugar.</p></div><div className="welcome-symbol">▦</div></section><section className="stats-grid"><article className="stat-card"><span className="stat-icon blue">⌂</span><p>Sucursales</p><strong>{stores.length}</strong><small>Registradas en el sistema</small></article><article className="stat-card"><span className="stat-icon purple">▤</span><p>Productos</p><strong>{products.length}</strong><small>Productos activos</small></article><article className="stat-card"><span className="stat-icon orange">!</span><p>Stock bajo</p><strong>{lowStock.length}</strong><small>En o por debajo del mínimo</small></article><article className="stat-card"><span className="stat-icon green">$</span><p>Valor al costo</p><strong>{money(inventoryValue)}</strong><small>Existencias × costo unitario</small></article></section><section className="content-grid"><article className="panel"><div className="panel-heading"><div><h3>Productos con stock bajo</h3><p>Requieren revisión</p></div><button className="text-button" onClick={()=>setTab("products")}>Ver productos →</button></div>{lowStock.length===0?<div className="empty-state"><span>✓</span><strong>Sin alertas de stock</strong><p>No hay productos por debajo de su mínimo configurado.</p></div>:<div className="table-wrap"><table><thead><tr><th>Producto</th><th>Existencia</th><th>Mínimo</th></tr></thead><tbody>{lowStock.slice(0,6).map(p=><tr key={p.syncId}><td>{p.name}</td><td><span className="stock-badge">{p.stock}</span></td><td>{p.minStockAlert}</td></tr>)}</tbody></table></div>}</article><article className="panel"><div className="panel-heading"><div><h3>Sucursales</h3><p>Ubicaciones registradas</p></div><button className="text-button" onClick={()=>setTab("stores")}>Ver todas →</button></div>{stores.length===0?<div className="empty-state"><strong>Aún no hay sucursales</strong><p>Las sucursales aparecerán aquí cuando estén registradas.</p></div>:<div className="store-list">{stores.slice(0,5).map(s=><div className="store-row" key={s.syncId}><span className="store-avatar">{s.name.slice(0,1).toUpperCase()}</span><div><strong>{s.name}</strong><small>{s.address||"Sin dirección registrada"}</small></div></div>)}</div>}</article></section></>}
      {tab==="stores"&&<section className="panel"><div className="panel-heading"><div><h3>Todas las sucursales</h3><p>{stores.length} sucursal(es) activas</p></div><button className="primary compact" onClick={newStore}>＋ Nueva sucursal</button></div>{stores.length?<div className="table-wrap"><table><thead><tr><th>Nombre</th><th>Dirección</th><th>Teléfono</th><th>Acciones</th></tr></thead><tbody>{stores.map(s=><tr key={s.syncId}><td><strong>{s.name}</strong></td><td>{s.address||"—"}</td><td>{s.phone||"—"}</td><td><div className="row-actions"><button onClick={()=>editStore(s)}>Editar</button><button className="danger-text" onClick={()=>void removeItem("store",s.syncId,s.name)}>Eliminar</button></div></td></tr>)}</tbody></table></div>:<div className="empty-state">No hay sucursales registradas.</div>}</section>}
      {tab==="products"&&<section className="panel"><div className="panel-heading"><div><h3>Inventario de productos</h3><p>{products.length} producto(s) activos</p></div><button className="primary compact" onClick={newProduct} disabled={!stores.length}>＋ Nuevo producto</button></div>{products.length?<div className="table-wrap"><table><thead><tr><th>Producto</th><th>Categoría</th><th>Existencia</th><th>Precio venta</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>{products.map(p=><tr key={p.syncId}><td><strong>{p.name}</strong><small className="cell-sub">{stores.find(s=>s.syncId===p.storeSyncId)?.name||"Sucursal"}</small></td><td>{p.category}</td><td>{p.stock}</td><td>{money(p.salePriceMinor)}</td><td><span className={p.stock<=p.minStockAlert?"status warning":"status good"}>{p.stock<=p.minStockAlert?"Stock bajo":"Disponible"}</span></td><td><div className="row-actions"><button onClick={()=>editProduct(p)}>Editar</button><button onClick={()=>openEditor({kind:"adjust",id:p.syncId},{storeSyncId:p.storeSyncId,delta:"1",reason:"Ajuste desde panel web"})}>Ajustar</button><button className="danger-text" onClick={()=>void removeItem("product",p.syncId,p.name)}>Eliminar</button></div></td></tr>)}</tbody></table></div>:<div className="empty-state">No hay productos registrados.</div>}</section>}
      {tab==="catalog"&&<section className="catalog-grid">{(["category","supplier"] as const).map(kind=>{const list=catalog.filter(c=>c.type===(kind==="category"?"CATEGORY":"SUPPLIER"));return <article className="panel" key={kind}><div className="panel-heading"><div><h3>{kind==="category"?"Categorías":"Proveedores"}</h3><p>{kind==="category"?"Clasificación de productos":"Directorio de proveedores"}</p></div><button className="primary compact" onClick={()=>newCatalog(kind)}>＋ Agregar</button></div>{list.map(c=><div className="catalog-row" key={c.syncId}><span className="catalog-icon">{kind==="category"?"◇":"⌑"}</span><strong>{c.name}</strong><div className="row-actions"><button onClick={()=>editCatalog(c)}>Editar</button><button className="danger-text" onClick={()=>void removeItem("catalog",c.syncId,c.name)}>Eliminar</button></div></div>)}{!list.length&&<p className="muted">Todavía no hay elementos.</p>}</article>})}</section>}
      <footer className="footer"><span>INSERC · Inventory_App</span><span>Última actualización: {lastUpdated||"—"}</span></footer>
    </main>
    {editor&&<div className="modal-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget)setEditor(null);}}><section className="editor-modal"><div className="modal-heading"><div><p className="eyebrow">ADMINISTRACIÓN</p><h2>{editor.kind==="adjust"?"Ajustar inventario":editor.id?"Editar registro":editor.kind==="store"?"Nueva sucursal":editor.kind==="product"?"Nuevo producto":editor.kind==="category"?"Nueva categoría":"Nuevo proveedor"}</h2></div><button className="modal-close" onClick={()=>setEditor(null)}>×</button></div>
      <form onSubmit={editor.kind==="adjust"?submitAdjustment:saveEditor}>
        {editor.kind==="store"&&<><Field label="Nombre de la sucursal" value={draft.name} onChange={v=>setField("name",v)} required/><Field label="Dirección" value={draft.address} onChange={v=>setField("address",v)}/><Field label="Teléfono" value={draft.phone} onChange={v=>setField("phone",v)}/><Field label="URL del logo (opcional)" value={draft.logoUrl} onChange={v=>setField("logoUrl",v)}/><Field label="Color hexadecimal" value={draft.colorHex} onChange={v=>setField("colorHex",v)}/></>}
        {(editor.kind==="category"||editor.kind==="supplier")&&<Field label={editor.kind==="category"?"Nombre de la categoría":"Nombre del proveedor"} value={draft.name} onChange={v=>setField("name",v)} required/>}
        {editor.kind==="product"&&<><Field label="Nombre del producto" value={draft.name} onChange={v=>setField("name",v)} required/><label className="field-label">Sucursal<select value={draft.storeSyncId??""} disabled={!!editor.id} onChange={e=>setField("storeSyncId",e.target.value)} required>{stores.map(s=><option key={s.syncId} value={s.syncId}>{s.name}</option>)}</select></label><label className="field-label">Categoría<select value={draft.category??""} onChange={e=>setField("category",e.target.value)} required><option value="">Selecciona una categoría</option>{catalog.filter(c=>c.type==="CATEGORY").map(c=><option key={c.syncId} value={c.name}>{c.name}</option>)}</select></label><label className="field-label">Proveedor<select value={draft.supplier??""} onChange={e=>setField("supplier",e.target.value)}><option value="">Sin proveedor</option>{catalog.filter(c=>c.type==="SUPPLIER").map(c=><option key={c.syncId} value={c.name}>{c.name}</option>)}</select></label><div className="form-two"><Field label="Costo (RD$)" type="number" min="0" step="0.01" value={draft.costPrice} onChange={v=>setField("costPrice",v)} required/><Field label="Precio venta (RD$)" type="number" min="0" step="0.01" value={draft.salePrice} onChange={v=>setField("salePrice",v)} required/></div>{!editor.id&&<Field label="Stock inicial" type="number" min="0" step="1" value={draft.stock} onChange={v=>setField("stock",v)} required/>}<Field label="Alerta de stock mínimo" type="number" min="0" step="1" value={draft.minStockAlert} onChange={v=>setField("minStockAlert",v)} required/><Field label="URL de imagen (opcional)" value={draft.photoUrl} onChange={v=>setField("photoUrl",v)}/><Field label="Enlace de venta (opcional)" value={draft.saleLink} onChange={v=>setField("saleLink",v)}/>{!!editor.id&&<p className="hint inline-hint">El stock no se modifica desde la edición del producto. Usa «Ajustar» para registrar entradas o salidas.</p>}</>}
        {editor.kind==="adjust"&&<><p className="muted">Producto: <strong>{products.find(p=>p.syncId===editor.id)?.name}</strong>. Existencia actual: <strong>{products.find(p=>p.syncId===editor.id)?.stock}</strong>.</p><Field label="Cambio de unidades (+ entrada / − salida)" type="number" step="1" value={draft.delta} onChange={v=>setField("delta",v)} required/><Field label="Motivo del ajuste" value={draft.reason} onChange={v=>setField("reason",v)} required/></>}
        {error&&<p className="error">{error}</p>}<div className="modal-actions"><button type="button" className="secondary" onClick={()=>setEditor(null)}>Cancelar</button><button type="submit" className="primary" disabled={saving}>{saving?"Guardando…":editor.kind==="adjust"?"Registrar ajuste":"Guardar cambios"}</button></div>
      </form>
    </section></div>}
  </div>;
}

function Field({label,value,onChange,type="text",required=false,min,step}:{label:string;value?:string;onChange:(value:string)=>void;type?:string;required?:boolean;min?:string;step?:string}) {
  return <label className="field-label">{label}<input type={type} value={value??""} onChange={e=>onChange(e.target.value)} required={required} min={min} step={step}/></label>;
}
