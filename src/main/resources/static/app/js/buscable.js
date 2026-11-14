/**
 * Búsqueda dentro de las listas desplegables: en cualquier <select> con varias opciones se puede escribir para filtrarlas
 * (sin importar mayúsculas ni acentos, y con varias palabras en cualquier orden). Atiende a todas las listas de la página,
 * las que ya están y las que se dibujen después; las listas cortas se abren como siempre.
 */
(() => {
  const MINIMO = 6;
  let abierta = null;   // { select, caja, cerrar }

  const norm = (t) => String(t || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const buscable = (s) => s && s.tagName === 'SELECT' && !s.multiple && !s.disabled && s.options.length >= MINIMO && s.size <= 1;

  function cerrar() { if (abierta) abierta.cerrar(); }

  function abrir(select, inicial) {
    cerrar();
    const opciones = Array.from(select.options).map((o, i) => ({ i, texto: o.textContent.trim(), n: norm(o.textContent), valor: o.value }));
    const caja = document.createElement('div');
    caja.className = 'buscable';
    caja.innerHTML = '<input type="text" class="buscable-q" placeholder="Escribe para buscar…" autocomplete="off"><div class="buscable-lista" role="listbox"></div>';
    document.body.appendChild(caja);
    const q = caja.querySelector('input'), lista = caja.querySelector('.buscable-lista');
    q.value = inicial || '';
    let visibles = [], marcada = 0;

    const pintar = () => {
      lista.innerHTML = visibles.length
        ? visibles.map((o, k) => `<div class="buscable-op${k === marcada ? ' marcada' : ''}${o.i === select.selectedIndex ? ' actual' : ''}" data-k="${k}" role="option">${o.texto.replace(/&/g, '&amp;').replace(/</g, '&lt;') || '&nbsp;'}</div>`).join('')
        : '<div class="buscable-vacio">Sin coincidencias</div>';
      const m = lista.querySelector('.marcada');
      if (m) m.scrollIntoView({ block: 'nearest' });
    };
    const filtrar = () => {
      const palabras = norm(q.value).split(/\s+/).filter(Boolean);
      visibles = opciones.filter(o => palabras.every(p => o.n.includes(p)));
      const actual = visibles.findIndex(o => o.i === select.selectedIndex);
      marcada = !q.value.trim() && actual >= 0 ? actual : 0;
      pintar();
    };
    const elegir = (k) => {
      const o = visibles[k];
      if (!o) return;
      cerrar();
      select.selectedIndex = o.i;
      select.dispatchEvent(new Event('input', { bubbles: true }));
      select.dispatchEvent(new Event('change', { bubbles: true }));
      select.focus();
    };

    // posición: debajo de la lista; si no cabe, arriba. En el celular el teclado le quita pantalla a la página: se usa el área
    // realmente visible (visualViewport) y la lista se achica para que siempre se vean las opciones.
    const colocar = () => {
      const r = select.getBoundingClientRect();
      const vv = window.visualViewport;
      const altoVisible = vv ? vv.height : window.innerHeight;
      const ancho = Math.max(240, r.width);
      caja.style.minWidth = ancho + 'px';
      caja.style.left = Math.max(8, Math.min(r.left, window.innerWidth - ancho - 8)) + 'px';
      const cabeBajo = altoVisible - r.bottom - 70, cabeArriba = r.top - 70;      // 70 = el cuadro de búsqueda y los márgenes
      const abajo = cabeBajo >= 150 || cabeBajo >= cabeArriba;
      const espacio = Math.max(110, Math.min(280, abajo ? cabeBajo : cabeArriba));
      lista.style.maxHeight = espacio + 'px';
      caja.style.top = abajo ? (r.bottom + 4) + 'px' : Math.max(8, r.top - espacio - 66) + 'px';
    };
    colocar();

    q.addEventListener('input', filtrar);
    q.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowDown') { e.preventDefault(); marcada = Math.min(visibles.length - 1, marcada + 1); pintar(); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); marcada = Math.max(0, marcada - 1); pintar(); }
      else if (e.key === 'Enter') { e.preventDefault(); elegir(marcada); }
      else if (e.key === 'Escape') { e.preventDefault(); cerrar(); select.focus(); }
      else if (e.key === 'Tab') { elegir(marcada); }
    });
    lista.addEventListener('mousemove', (e) => {
      const el = e.target.closest('.buscable-op');
      if (el && Number(el.dataset.k) !== marcada) { marcada = Number(el.dataset.k); lista.querySelectorAll('.marcada').forEach(x => x.classList.remove('marcada')); el.classList.add('marcada'); }
    });
    lista.addEventListener('mousedown', (e) => { e.preventDefault(); const el = e.target.closest('.buscable-op'); if (el) elegir(Number(el.dataset.k)); });

    const fuera = (e) => { if (!caja.contains(e.target)) cerrar(); };
    // Al aparecer el teclado del celular la página se mueve y cambia de tamaño: la lista NO se cierra, se acomoda;
    // solo se cierra si la lista de la que salió ya no se ve en la pantalla
    const acomodar = (e) => {
      if (e && e.target && caja.contains(e.target)) return;      // el desplazamiento de la propia lista
      const r = select.getBoundingClientRect();
      if (r.bottom < 0 || r.top > window.innerHeight) { cerrar(); return; }
      colocar();
    };
    setTimeout(() => document.addEventListener('mousedown', fuera, true), 0);
    window.addEventListener('scroll', acomodar, true);
    window.addEventListener('resize', acomodar);
    if (window.visualViewport) { window.visualViewport.addEventListener('resize', acomodar); window.visualViewport.addEventListener('scroll', acomodar); }
    abierta = { select, caja, cerrar() {
      document.removeEventListener('mousedown', fuera, true);
      window.removeEventListener('scroll', acomodar, true);
      window.removeEventListener('resize', acomodar);
      if (window.visualViewport) { window.visualViewport.removeEventListener('resize', acomodar); window.visualViewport.removeEventListener('scroll', acomodar); }
      caja.remove();
      abierta = null;
    } };
    filtrar();
    q.focus();
    q.setSelectionRange(q.value.length, q.value.length);
  }

  document.addEventListener('mousedown', (e) => {
    const s = e.target.closest && e.target.closest('select');
    if (!buscable(s) || e.button !== 0) return;
    e.preventDefault();      // evita que el navegador abra su lista
    s.focus();
    abrir(s, '');
  }, true);

  document.addEventListener('keydown', (e) => {
    const s = document.activeElement;
    if (!buscable(s) || abierta || e.ctrlKey || e.metaKey || e.altKey) return;
    if (e.key === ' ' || e.key === 'F4') { e.preventDefault(); abrir(s, ''); }
    else if (e.key.length === 1) { e.preventDefault(); abrir(s, e.key); }
  }, true);
})();
