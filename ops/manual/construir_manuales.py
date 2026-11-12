# Genera los dos manuales (JSystem y web) a partir de capítulos comunes y variantes.
# Reglas del lector: nada de viñetas con sangría; cada paso en una sola línea; los títulos «## » no se renombran (F1 los usa).
import io

RUTA_JS = r"C:\Users\PC\IdeaProjects\JSystem\src\main\resources\ayuda\manual.md"
RUTA_WEB = r"C:\Users\PC\Documents\skycel-backend\src\main\resources\static\app\ayuda\manual.md"

INTRO = """# Manual de usuario · Skycel

Este manual explica, paso a paso, cómo usar el sistema. Hay dos formas de entrar: **JSystem** (el programa de la computadora de la tienda) y la **versión web** (en el celular o en cualquier computadora: `https://skycelsys.ddns.net/app`). Las dos muestran la misma información. Cada pantalla tiene su capítulo, con el mismo nombre que en el menú.

**¿Tienes una duda?** En JSystem pulsa **F1** en cualquier pantalla y se abre la explicación de esa pantalla. En la web, toca el signo de interrogación de arriba.
"""

# ───────────────────────────── Primeros pasos ─────────────────────────────
PRIMEROS_COMUN = """## Primeros pasos

### Cómo funciona
Todo lo que se captura (ventas, inventario, caja) se guarda en un **servidor** en la oficina. Por eso lo que haces en tu tienda lo ven los demás al momento, según lo que cada rol puede ver. Si se va el internet, JSystem sigue funcionando en lo más importante (ver «Trabajar sin conexión»): se guarda en la computadora y se envía solo cuando regresa la conexión.

### Entrar al sistema
1. Escribe tu **usuario** y tu **contraseña**.
{entrar}
3. Solo verás las secciones que tu rol permite.
4. Al terminar, **cierra sesión** ({salir}). Así cada venta queda a nombre de quien la hizo.

> No compartas tu contraseña. Si la olvidas, un administrador la cambia en **Empleados**.

### Escribir montos y teléfonos
Los campos de dinero ponen solos el signo **$** y las comas: escribes `1299.5` y se ve **$1,299.50**. Solo aceptan números y **dos decimales**. Los teléfonos son de **10 dígitos** y no dejan escribir más. {formato}

### Si te cambian de sucursal
Solo el **encargado** y el **técnico** están ligados a una tienda; los vendedores (apoyos) y los administradores trabajan en la que se necesite. {cambio}

### Moverse por el programa
- El **menú de la izquierda** tiene todas las secciones: Ventas, Inventario, Finanzas, Clientes, Taller y Administración.
{moverse}
"""

PRIMEROS_JS = PRIMEROS_COMUN.format(
    entrar="2. Si es tu computadora, puedes marcar **Recordar mi contraseña en este equipo**. En una computadora compartida déjalo sin marcar.",
    salir="el botón de salida, arriba a la derecha",
    formato="Los teléfonos se ven agrupados, por ejemplo **757-123-4567**.",
    cambio="Si un administrador te cambia de sucursal o de puesto mientras trabajas, JSystem lo detecta en un minuto y te avisa: pulsa **Volver a entrar ahora**. Si estás cobrando, termina la venta y elige **Más tarde** (te vuelve a avisar en 5 minutos). Un vendedor o un administrador usa la **sucursal que tiene configurada la computadora** (ver «Configuración»).",
    moverse="""- **F1** abre esta ayuda en el tema de la pantalla donde estás. **Ctrl + 1 … 9** abre las primeras secciones del menú.
- Arriba a la derecha están **Ayuda**, **Avisos** (el número rojo son los que no has leído), **Configuración** de esta computadora, **Cambiar mi contraseña** (la llave) y **Cerrar sesión**. Para cambiar tu contraseña: pulsa la llave, escribe la actual y la nueva dos veces (mínimo 8 caracteres).
- Cuando hay una **actualización** de JSystem lista, sale un recuadro en la esquina: haz clic para instalarla en ese momento (se cierra y se vuelve a abrir solo) o déjala: se instala al cerrar el programa.
- Si arriba no aparece ningún aviso de color, todo va bien. Si aparece uno, se perdió la conexión o hay ventas esperando enviarse.
""")

PRIMEROS_WEB = PRIMEROS_COMUN.format(
    entrar="2. Pulsa **Entrar**.",
    salir="el botón de abajo del menú",
    formato="El signo $ aparece dentro del campo y el monto se completa a dos decimales al salir de él.",
    cambio="En la web, la sesión se pone al día sola (cada par de minutos y al volver a la pestaña). Un vendedor elige arriba la sucursal donde trabaja ese día.",
    moverse="""- En el celular hay un acceso rápido abajo: **Inicio, Vender, Inventario y Caja**.
- **Ajustes** sirve para cambiar tu contraseña; el administrador también define ahí las formas de pago y el logo del negocio.
- El indicador de conexión te dice si estás en línea o si hay cosas guardadas esperando enviarse.
""")

# ───────────────────────────── Roles ─────────────────────────────
ROLES = """## Roles y permisos

Cada persona tiene un rol, y el sistema solo le muestra lo que le corresponde.

| Puede… | Vendedor | Encargado | Administrador | Técnico |
|---|---|---|---|---|
| Vender y cobrar | Sí | Sí | Sí | No |
| Abrir y cerrar la caja de su tienda | Sí | Sí | Sí | No |
| Registrar entradas y salidas de efectivo | No | Sí | Sí | No |
| Pedir una devolución | Sí | Sí | Sí | No |
| Aprobar o rechazar devoluciones | No | Sí | Sí | No |
| Cancelar una venta | No | Sí | Sí | No |
| Recibir equipos para reparar y entregarlos | Sí | Sí | Sí | No |
| Trabajar en el taller (reparar, avisar que está lista) | No | No | Sí | Sí |
| Garantías | Sí | Sí | Sí | No |
| Clientes: ver | Sí | Sí | Sí | No |
| Clientes: crear y editar | No | Sí | Sí | No |
| Cuentas por cobrar | No | Sí | Sí | No |
| Compras y cuentas por pagar | No | No | Sí | No |
| Traspasos | No | Sí | Sí | Solo el **técnico encargado**, de su taller |
| Inventario físico | No | Sí | Sí | No |
| Reportes de ventas (por ticket y por artículo) | No | Sí | Sí | No |
| Reportes de Gerencia (resultados, utilidad, inventario valorizado) | No | No | Sí | No |
| Ver cuánto costó la mercancía y a qué proveedor se compró | No | No | Sí | No |
| Ajustar las existencias de accesorios | No | Sí | Sí | No |
| Registrar y editar productos y precios | No | No | Sí | No |
| Descuentos, nómina y comisiones | No | No | Sí | No |
| Configuración de la computadora (servidor, impresora, ticket) | No | Sí | Sí | No |
| Empleados | No | No | Sí | No |

> Un **encargado** solo ve la información de su tienda, y de fábrica **no ve costos ni proveedores** (eso es solo del administrador); el **técnico encargado** sí ve el costo de las refacciones de las órdenes que trabaja.
> Un **encargado** ve solo su tienda. Un **administrador** puede cambiar de sucursal o ver todas. Un **técnico** solo ve las órdenes del taller.
> El usuario **root** (y quien root autorice) puede cambiar lo que puede hacer cada rol en **Permisos** (sección Administración). La tabla de arriba es lo que trae el sistema de fábrica.
"""

# ───────────────────────────── Punto de Venta ─────────────────────────────
POS_JS = """## Punto de Venta

Aquí se cobra. A la izquierda está el **catálogo** (lo que vendes) y a la derecha el **carrito**, el **cliente** y el **pago**.

### Antes de empezar
La caja de tu tienda debe estar **abierta**. Si está cerrada, el sistema te pide abrirla con su fondo inicial. Sin caja abierta no se puede cobrar.

### Cómo vender
1. Pulsa **F2** y escribe parte del nombre o el código del artículo (o pasa el lector de códigos). También puedes elegir una categoría: **Todos, Equipos, Accesorios o Servicios**; con las flechas **←** y **→** del teclado cambias de categoría.
2. Pulsa **Enter** o haz clic en el producto: se agrega al carrito. Con el lector de códigos se agrega solo.
3. Elige la **forma de pago**: Efectivo, Transferencia o Mixto (el cliente paga una parte de cada forma: escribe cuánto va en cada una y solo deja cobrar cuando cuadra con el total). La tarjeta aparece solo si el administrador la habilita.
4. En efectivo escribe lo que **recibes**. El sistema muestra el **cambio**: en verde si alcanza, en rojo si falta dinero.
5. Pulsa **Procesar venta** (o **F9**) y entrega el ticket. Si das doble clic por error, el sistema cobra **una sola vez**.

Los precios ya **incluyen IVA**. Los descuentos que define el administrador se aplican solos.

### Equipos (celulares y tablets)
Cada equipo se vende por su **IMEI o número de serie**.
1. Al agregar un equipo, escribe o escanea su **IMEI**. El sistema revisa que esté disponible en tu tienda.
2. Elige si se lleva **al contado** o **a crédito (PayJoy)**.
3. Con PayJoy captura el **enganche**, el **plazo** (3, 6, 9 o 12 meses) y con qué se pagó el enganche. El sistema no calcula pagos semanales: eso lo da PayJoy.
4. Cada equipo es un renglón aparte; no se le puede subir la cantidad.

> **Un carrito es una sola venta**: un folio y un ticket con todos los artículos, aunque se paguen de varias formas (por ejemplo, un equipo con PayJoy y accesorios en efectivo). El ticket dice cuánto se pagó hoy y cuánto financia PayJoy. Solo el efectivo entra al cajón.

### Accesorios y servicios
Entran directo al carrito. Si agregas el mismo otra vez, sube la cantidad. El sistema no deja vender más de lo que hay en existencia. Los **servicios** no se agotan: en vez de existencia muestran **∞**.

### Pedir un descuento
1. En el renglón del carrito pulsa el botón de la **etiqueta** (Solicitar descuento).
2. Elige si pides el **monto a descontar** o el **precio final** (por ejemplo, «que quede en $450»), escribe la cifra **por pieza** y el **motivo**.
3. Un administrador recibe el aviso y la **aprueba** o la **rechaza**. Mientras tanto el renglón dice «esperando a un administrador».
4. Si la aprueba, el importe baja solo. La aprobación sirve **una vez**, para ese artículo y por unas horas; si cambias la cantidad o quitas el renglón, hay que pedirla de nuevo.

Pedir descuentos necesita conexión.

### Promoción al vender un equipo
Si hay una promoción activa, al agregar junto con un equipo un chip, una mica o un protector, el renglón dice **🎁 Promoción de equipo** y ya trae el precio rebajado. No tienes que capturar nada. Solo las primeras piezas reciben la promoción (ver «Descuentos»).

### Corregir el carrito
- **+** y **−** cambian la cantidad de la línea marcada (la última que agregaste se marca sola). Con **−** en la última pieza, la línea se quita.
- **Supr** quita la línea marcada. También sirve el bote de basura de la línea.
- **Limpiar carrito** borra todo.
- Mientras escribes en un campo, **+**, **−** y **Supr** son texto normal.

### Cliente
El cliente es **opcional**: sin marcar **Registrar cliente**, la venta queda de **mostrador**. Si lo marcas, escribe el **teléfono (10 dígitos)** y pulsa **Buscar**. Si ya existe, aparece su nombre; si no, escribe el nombre y pulsa **Registrar**. **F4** te lleva a esos campos.

### Historial de ventas
El botón **Historial de ventas** lista las ventas y permite **reimprimir** el ticket. Sale igual que el original (todos los artículos con su IMEI, el enganche y el plazo de PayJoy, la forma de pago), solo que sin el efectivo recibido ni el cambio.

Encargados y administradores también pueden **cancelar** una venta: se regresan las piezas y se liberan los IMEI. **No** se puede cancelar una venta que salió de una orden de servicio, que ya tiene una devolución, o que tiene una cuenta por cobrar con abonos. En esos casos se usa **Devoluciones**.

### Mensajes comunes
- **«La caja está cerrada»**: ábrela con su fondo inicial y vuelve a cobrar.
- **«Sin stock»** o **«Stock limitado»**: ya no hay más piezas de ese producto en la tienda.
- **«El IMEI no está disponible»**: el equipo ya se vendió, está en otra tienda o en traspaso, o está mal escrito. Búscalo en **Equipos**.

> En la **web** el punto de venta es más sencillo y necesita conexión. Para pagos mixtos, PayJoy o cambios, usa JSystem en la tienda.
"""

POS_WEB = """## Punto de Venta

Aquí se cobra. En la computadora, a la izquierda está el **catálogo** y a la derecha el **carrito**, el **cliente** y el **pago**; en el celular van una debajo de la otra. En la web el punto de venta necesita conexión: sin internet, usa JSystem en la tienda.

### Antes de empezar
La caja de tu tienda debe estar **abierta**. Si está cerrada, aparece el formulario para abrirla con su fondo inicial. Sin caja abierta no se puede cobrar.

### Cómo vender
1. Escribe en el buscador parte del nombre, el código o el IMEI, o elige una categoría (Equipos, Accesorios…).
2. Toca el producto para agregarlo al carrito. Si tocas el mismo accesorio otra vez, sube la cantidad (sin pasar de las piezas que hay).
3. Elige la **forma de pago**: **Efectivo**, **Transferencia** o **Mixto**. La tarjeta aparece solo si el administrador la habilita en **Ajustes**.
4. En efectivo escribe lo que **recibes**; el sistema calcula el **cambio**.
5. Pulsa **Cobrar**. Si tocas dos veces por error, el sistema cobra **una sola vez**.

Con pago **Mixto**, el cliente paga una parte de cada forma: escribe cuánto va en efectivo y cuánto en transferencia. El sistema te dice cuánto falta o cuánto se pasa y solo deja cobrar cuando **cuadra** con el total. En el cajón entra únicamente lo que se pagó en efectivo.

Los precios ya **incluyen IVA** y los descuentos del administrador se aplican solos.

### Equipos (celulares y tablets)
Cada equipo se vende por su **IMEI o número de serie**. En el catálogo cada modelo muestra sus unidades disponibles; toca la que se lleva el cliente (cada una dice su IMEI, su condición y su precio).
1. El equipo entra al carrito y se pregunta si es **al contado** o **a crédito (PayJoy)**.
2. Con PayJoy captura el **enganche**, el **plazo** (3, 6, 9 o 12 meses) y con qué se pagó el enganche. El sistema no calcula pagos semanales: eso lo da PayJoy.
3. Un equipo no lleva cantidad: cada uno es un renglón con su IMEI.

> **Un carrito es una sola venta**: un folio y un ticket con todos los artículos, aunque se paguen de varias formas (por ejemplo, un equipo con PayJoy y accesorios en efectivo). El ticket dice cuánto se pagó hoy y cuánto financia PayJoy.

### Promoción al vender un equipo
Si hay una promoción activa, al agregar junto con un equipo un chip, una mica o un protector, el renglón dice **Promoción de equipo** y ya trae el precio rebajado. No capturas nada. Solo las primeras piezas reciben la promoción (ver «Descuentos»).

### Pedir un descuento
1. En el renglón toca **Pedir descuento**.
2. Elige si pides el **monto a descontar** o el **precio final** (por ejemplo, «que quede en $450»), escribe la cifra **por pieza** y el **motivo**.
3. Un administrador la **aprueba** o la **rechaza**. Mientras tanto el renglón dice «Esperando respuesta del administrador».
4. Si la aprueba, el importe baja solo. La aprobación sirve **una vez**, para ese artículo y por unas horas.

### Cliente
El cliente es **opcional**: sin marcar **Registrar cliente**, la venta queda de **mostrador**. Si lo marcas, escribe el **teléfono (10 dígitos)** y pulsa **Buscar**. Si no existe, escribe su nombre y pulsa **Registrar**.

### Mensajes comunes
- **«La caja está cerrada»**: ábrela con su fondo inicial y vuelve a cobrar.
- **«Stock insuficiente»**: ya no hay más piezas de ese producto en la tienda.
- **«El IMEI no está disponible»**: el equipo ya se vendió, está en otra tienda, en traspaso o en garantía, o está mal escrito. Búscalo en **Equipos**.
- **«El precio de … es $X»**: los precios se definen en Inventario; el punto de venta no los cambia.
"""

# ───────────────────────────── Caja ─────────────────────────────
CAJA = """## Caja

La caja es tu **turno de trabajo**: se **abre** al empezar el día con un fondo, se vende, y se **cierra** al terminar contando el efectivo. Al cerrar queda guardado un **corte** con todo el detalle. Cualquier empleado de la tienda puede abrir o cerrar la caja; solo puede haber un turno abierto por caja.

### Abrir la caja
1. Entra a **Caja** y pulsa **Abrir caja** (el punto de venta también lo ofrece si está cerrada).
2. Cuenta el efectivo del cajón y captúralo como **fondo inicial**. El sistema sugiere lo que se dejó al cerrar ayer (con un enlace para usarlo).
3. Si lo que contaste no coincide con lo que esperaba el sistema, la diferencia queda como **ajuste de apertura**.


### Durante el turno
Con la caja abierta, arriba ves la cuenta del cajón: **fondo inicial + ventas en efectivo + entradas − salidas = efectivo esperado** (lo que debe haber en el cajón ahora).
- Las **entradas** incluyen abonos de clientes y anticipos; las **salidas** son gastos, retiros y devoluciones en efectivo.
- **Nuevo movimiento** (encargado y administrador) registra dinero que entra o sale del cajón fuera de una venta: un gasto, un pago, un retiro. Elige siempre el **motivo** y escribe una nota clara.
- Las ventas con tarjeta o transferencia, y la parte financiada por PayJoy, **no** meten dinero al cajón.

### Cerrar la caja (corte)
La ventana de cierre te guía con **tres cuadros numerados** y un resumen:
1. **Cuenta el efectivo del cajón** (billetes y monedas) y escribe el total en el primer campo. El sistema **no te dice cuánto debería haber** antes de que lo captures: así el conteo es honesto.
2. **¿Cuánto se queda para mañana?** Es el fondo del siguiente turno (por omisión, el fondo con que abriste; el enlace **Dejar el mismo fondo de hoy** lo vuelve a poner). Lo demás se retira del cajón.
3. **Observaciones** (opcional): por ejemplo, un billete roto o un retiro que no se anotó.
4. Mientras escribes, el cuadro de abajo te dice **cuánto se retira del cajón** y **cuánto se queda**. Si el fondo es mayor a lo que contaste, avisa en rojo y el botón **Cerrar caja y generar corte** no se activa. Antes de cerrar te pregunta una última vez con las cifras.
5. Se abre el **corte**: ahí ves el efectivo esperado, lo contado y si **cuadra**, hay **faltante** o hay **sobrante**.

### Cortes anteriores
**Cortes** guarda el historial. Abre cualquiera para ver su detalle (ventas por forma de pago, movimientos, quién abrió y quién cerró) e **imprimirlo**.{resumen}

### Revisar un corte a detalle (encargado y administrador)
Para comprobar que todo lo vendido y todo el efectivo quedó bien registrado, abre un corte y pulsa **Ver ventas y movimientos**. Muestra, de todo el turno:
- **Ventas:** una por una, con la hora, quién vendió, el cliente, la forma de pago y **todo lo que se llevó el cliente**. Cada equipo sale con su **IMEI** y si fue **al contado** o con **PayJoy** (enganche y plazo). Las canceladas aparecen marcadas y no suman.
- **⚠ Revisar:** el sistema compara el efectivo que cada venta debió dejar en el cajón con el que quedó registrado. Si no coinciden (por ejemplo, una venta en efectivo sin su entrada en la caja), la marca. Arriba aparece cuántas hay por revisar.
- **Equipos vendidos:** cada equipo del turno con su IMEI, para ver cuáles fueron al contado y cuáles con PayJoy.
- **Movimientos de efectivo:** cada entrada y salida con su motivo, su nota y quién la registró. Se pueden filtrar por persona o por tipo.
{exportar}Un encargado solo ve los cortes de su tienda; el administrador, los de todas. Necesita conexión.

### Caja de la bodega
La **bodega** también tiene su caja: ahí llega el **efectivo de las ventas de las tiendas**.
1. Un administrador entra a **Caja**, elige la **bodega** en el selector de sucursal y pulsa **Nueva caja**.
2. Cada día se **abre** con su fondo, como cualquier caja.
3. Cuando una tienda entrega su efectivo, la tienda registra una **salida** con el motivo **Traslado a bodega** y la bodega una **entrada** con el motivo **Efectivo de sucursal**. En la nota escribe de qué tienda y de qué día es.
4. Al final del día se **cierra** con su corte.
"""
CAJA_JS = CAJA.format(
    resumen=" Si después de cerrar llegan ventas hechas sin conexión con fecha de ese turno, el corte avisa que fue **modificado después del cierre**.\n\n**Resumen del período** saca el total de un rango de fechas y se puede exportar.",
    exportar="En JSystem se puede **exportar a Excel**. ")
CAJA_WEB = CAJA.format(
    resumen=" Si después de cerrar llegan ventas hechas sin conexión con fecha de ese turno, el corte avisa que fue **modificado después del cierre**.",
    exportar="")

# ───────────────────────────── Devoluciones ─────────────────────────────
DEVOLUCIONES = """## Devoluciones

Sirven para regresar un producto vendido: se le **reembolsa** al cliente o se le da **otro producto a cambio**. Hay un plazo para devolver (por omisión, 15 días; el administrador lo cambia en Configuración).

### Pedir una devolución
1. Entra a **Devoluciones** y pulsa **Nueva devolución**.
2. **Busca la venta** (por folio) y elige los artículos que regresan y la cantidad. En los equipos, elige cada IMEI.
3. Escribe el **motivo**.
4. Marca si el artículo está en **buen estado** (regresa a inventario) o **dañado** (se da de baja).
5. Elige cómo se resuelve. **Reembolso**: se devuelve el dinero; si es en efectivo, sale de la caja (debe estar abierta). **Cambio**: el cliente se lleva otro producto, que debe valer **igual o más**; si vale más, se cobra la diferencia.
6. Pulsa **Solicitar devolución**.

### Aprobar o rechazar
Un **encargado** o **administrador** revisa la solicitud y pulsa **Aprobar y ejecutar** (mueve el inventario y el dinero) o **Rechazar** (con el motivo). Hasta que se aprueba, no cambia nada.

> Una venta con una devolución pedida o ya hecha no se puede cancelar.
"""

# ───────────────────────────── Inventario ─────────────────────────────
INVENTARIO_JS = """## Inventario

Muestra los productos de **tu tienda**: existencia, precio y el mínimo que conviene tener.

### Qué muestra
Cada producto con su nombre, código, existencia y precios (el costo y el proveedor solo los ve un administrador). Los de **poca existencia** aparecen marcados y el menú dice cuántos son. Los **servicios** no tienen existencia: muestran **∞** y no entran en «Sin stock» ni «Stock bajo». Al pie de la tabla ves el conteo de lo que se muestra. **Valorización** da el valor del inventario a costo y a precio de venta, y **Exportar** lo saca a Excel.

### Buscar en todas las tiendas
Activa **Todas las tiendas** junto al buscador y escribe el nombre, el código o el IMEI. La tabla muestra en qué otras sucursales (y en la bodega) hay. Con **Solicitar traspaso** eliges la cantidad y la solicitud le llega a esa tienda. Necesita conexión.

### Registrar un producto nuevo (administrador)
1. Pulsa **Nuevo producto**.
2. Elige la **categoría principal** y, en cascada, sus subcategorías. Escribe el nombre; abajo verás la **vista previa** del nombre completo.
3. Captura precios y existencia. Los **equipos** no llevan código: se registran con su **IMEI** (uno por línea) y se ven en **Equipos**.
4. En los **accesorios** puedes dejar marcada la casilla **Imprimir las etiquetas con el código al registrar**: al guardar sale una etiqueta por cada pieza.

### Imprimir etiquetas
Cualquier empleado puede imprimir las etiquetas de un **accesorio**: en la columna **Acciones** pulsa la **impresora**, escribe cuántas etiquetas (por omisión, la existencia) y pulsa **Imprimir**. La etiqueta lleva el nombre, el **código de barras**, el código y el precio. La impresora y el tamaño del papel se configuran en **Configuración › Etiquetas** (ver «Configuración»).

### Secciones del local
Las **secciones** son los lugares donde se acomoda la mercancía (vitrina, mostrador, bodega…). Con ellas se hace el inventario físico. El administrador las crea, renombra, vacía hacia otra y elimina en **Inventario › Catálogos › Secciones**. A cada artículo se le asigna su sección al **editarlo** (en **Inventario** para accesorios, en **Equipos** para equipos).

### Cambiar precios y existencia
- Solo un **administrador** edita los datos de un producto (código, nombre, precios, mínimo, proveedor, color, garantía). Al cambiar el código o el nombre se corrige en todas las sucursales y documentos.
- **Los precios son generales**: al guardar, la casilla **Aplicar el precio en todas las sucursales y en la bodega** viene marcada, así que el nuevo precio llega a todas las tiendas (también a los **servicios**). Si la quitas, solo cambia en la sucursal que estás viendo. El costo solo se cambia en las demás sucursales si tú lo modificaste.
- Un **encargado** o administrador puede ajustar la existencia de un **accesorio**. **Siempre se pide el motivo** (robo, daño, error de captura…). Si lo hace un encargado, a los administradores les llega un aviso.
- Los **equipos** no se ajustan por cantidad: cada uno tiene su IMEI, y su existencia cambia con una compra, venta, traspaso o inventario físico.
- Un encargado o administrador puede marcar un producto como **rezagado** si no se mueve.

### Cambiar precios de varios artículos (Precios, administrador)
En **Finanzas › Precios** el administrador ve **todos los artículos** (accesorios, servicios y equipos) con su precio actual, su costo y su existencia. Busca por nombre o categoría y filtra por tipo.
1. Elige uno o varios artículos (con **Ctrl** o **Shift**).
2. **Cambiar precio**: escribe un precio nuevo para todos, o sube o baja un **porcentaje** o una **cantidad en pesos**; puedes **redondear** (al peso, a $5 o a $10). Antes de aplicar ves cómo queda cada artículo y el sistema avisa si alguno quedaría **por debajo de su costo**.
3. El precio llega a **todas las sucursales y a la bodega** (las existencias y los equipos disponibles). Las ventas ya hechas no cambian.
4. **Descuento o liquidación**: pon un descuento (porcentaje o monto fijo) a los artículos elegidos, **hasta que lo quites**, **hasta una fecha** o **entre dos fechas**. Se aplica solo al cobrar y la columna **Descuento vigente** lo muestra. **Quitar descuento** lo desactiva.

### Movimientos
**Movimientos** muestra el historial de cada cambio de existencia (ventas, devoluciones, traspasos, ajustes), con la fecha y quién lo hizo.

### Copiar el código
En la columna **Acciones**, el botón de copiar deja el **código** del producto listo para pegarlo.
"""

INVENTARIO_WEB = """## Inventario

Muestra los productos de **tu tienda**: existencia, precio y el mínimo que conviene tener.

### Qué muestra
Cada producto con su nombre, código, existencia y precios (el costo y el proveedor solo los ve un administrador). Los de **poca existencia** aparecen marcados y el menú dice cuántos son. Los **servicios** no tienen existencia: muestran **∞**. Con **En todas las tiendas** buscas un artículo (por nombre, código o IMEI) en las demás sucursales y ves dónde hay; con **Pedir traspaso** le mandas la solicitud a esa tienda. **Exportar** lo saca a Excel.

### Registrar un producto nuevo (administrador)
1. Pulsa **Nuevo producto**.
2. Elige la **categoría principal** y, en cascada, sus subcategorías. Escribe el nombre; abajo verás la **vista previa** del nombre completo.
3. Captura precios y existencia. Los **equipos** no llevan código: se registran con su **IMEI** (uno por línea) y se ven en **Equipos**.

### Cambiar precios y existencia
- Solo un **administrador** edita los datos y los precios de un producto.
- **Los precios son generales**: al guardar los precios, la casilla **Aplicar el precio de venta en todas las sucursales y en la bodega** viene marcada, así que el cambio llega a todas las tiendas (accesorios y servicios). Si la quitas, solo cambia en esa sucursal. El costo solo se cambia en las demás sucursales si tú lo modificaste.
- Un **encargado** o administrador puede ajustar la existencia de un **accesorio**. **Siempre se pide el motivo** (robo, daño, error de captura…). Si lo hace un encargado, a los administradores les llega un aviso.
- Los **equipos** no se ajustan por cantidad: cada uno tiene su IMEI, y su existencia cambia con una compra, venta, traspaso o inventario físico.
- Un encargado o administrador puede marcar un producto como **rezagado** si no se mueve.

### Movimientos
**Movimientos** muestra el historial de cada cambio de existencia (ventas, devoluciones, traspasos, ajustes), con la fecha y quién lo hizo.
"""

# ───────────────────────────── Equipos ─────────────────────────────
EQUIPOS = """## Equipos

Los **celulares y las tablets** no tienen un código por modelo: cada equipo se identifica por su **IMEI** (o su **número de serie**, en una tablet) y pertenece a un artículo. La existencia de un artículo son los equipos **disponibles** que hay de él.

### Qué muestra
Cada equipo con su IMEI, el artículo, su condición (**nuevo**, **seminuevo** o **reacondicionado**), su precio, su costo (si tu rol lo puede ver), los días que lleva en existencia, su **sección** y su estado. **Por artículo** resume cuántos hay de cada modelo.

### Registrar equipos
1. Pulsa **Registrar equipos** y elige el **artículo**. Si es un modelo o color nuevo, primero se registra en Inventario › Nuevo producto, con la categoría Equipos.
2. Escribe los IMEI, **uno por línea**. Para un seminuevo con su propio costo y precio: `350000000000029; seminuevo; 800; 1500`.
3. Captura el costo y el precio de venta y pulsa **Registrar**.

### Corregir y dar de baja
- Solo un **administrador** corrige un equipo (IMEI, artículo, precio, costo, condición, proveedor y sección). El IMEI y el artículo solo se cambian mientras no se haya vendido, traspasado ni mandado a garantía.
- **Rezagado** marca un equipo que no se mueve. **Dar de baja** lo saca de la existencia (robo, daño o error de captura) y **pide el motivo**.
- **Por artículo** permite poner el mismo precio a todas las unidades disponibles y un **mínimo de existencias** que avisa cuando quedan pocas.{precio_general}
{copiar}
### Vender, traspasar y devolver
En el **Punto de Venta** busca el equipo por su nombre o IMEI {escanear}. En **Traspasos**, los equipos viajan por IMEI. Una **devolución** de un equipo en buen estado lo regresa a la existencia.
"""
EQUIPOS_JS = EQUIPOS.format(copiar="- El botón de copiar deja el **IMEI** listo para pegarlo.\n", escanear="(o escanéalo)",
    precio_general=" En **Mismo precio para todos**, la casilla **En todas las sucursales y en la bodega** (solo administrador, marcada por omisión) pone el precio a las unidades disponibles de ese modelo en **todas** las tiendas, porque los precios son generales.")
EQUIPOS_WEB = EQUIPOS.format(copiar="", escanear="y toca la unidad", precio_general="")

# ───────────────────────────── Inventario físico ─────────────────────────────
INVFIS = """## Inventario físico

Es el conteo de lo que hay en el local, para compararlo con lo que dice el sistema. Lo hacen **dos personas**: el **encargado cuenta** y el **administrador confirma**. Contar **no cambia las existencias**: solo cambian cuando el administrador cierra el inventario. Está en **{donde}**.

### Qué se va a inventariar
Al aperturar, el sistema pregunta qué cubre el inventario:
- **Toda la tienda** (recomendado para un conteo completo o una entrega de tienda): todo lo que hay en el sistema y no escaneas queda como faltante.
- **Solo las secciones que cuente**: conteo parcial; solo se revisan las secciones donde escaneaste algo.
- **Solo ciertas categorías**: por ejemplo solo equipos, solo micas o solo cargadores. Al marcar una categoría se marcan sus subcategorías.

### El encargado cuenta
1. Pulsa **Aperturar inventario** y elige qué se va a inventariar (ver arriba).
2. **Escanea** o escribe el código de cada producto (cada lectura suma una pieza) y el **IMEI** de cada equipo, uno por uno. Puedes pasar el **mismo código** varias veces seguidas y a la velocidad que quieras: no hay que esperar la respuesta entre una lectura y otra, y cada una se cuenta en orden. El lápiz corrige una cantidad; en un equipo, la lista de IMEI permite quitar el que escaneaste por error.
3. Pulsa **Enviar a revisión** cuando termines.{previa} Se genera el **resumen** y se avisa a los administradores.

> Mientras cuentas, el sistema **no te dice cuántas piezas debería haber**: las diferencias se ven hasta que lo envías a revisión. Cuenta por zonas y evita vender o mover mercancía de esa zona mientras tanto.

### El administrador confirma
1. Abre el inventario (arriba aparece qué sucursales tienen uno **por revisar**). El resumen muestra, por artículo, su **código**, lo que dice el sistema, lo contado, la diferencia y su valor a costo. En los equipos, **Ver equipos** lista los IMEI que no se encontraron.
2. Para cada diferencia elige **Ajustar** (las existencias se corrigen al cerrar) o **Justificar** (no se ajusta y queda escrito el motivo). **Ajustar todo lo pendiente** decide las que falten. Un equipo que **sobra** no se da de alta solo: se justifica o se registra en **Equipos**.
3. Si el encargado se equivocó en pocas piezas, pulsa **Corregir lo contado** (en un equipo, **Ver y corregir equipos** y marca **Sí estaba** o **No estaba**). Escribe la cantidad que hay **en físico ahora** y el **motivo**: queda registrado quién lo corrigió y cuánto había contado el encargado. Esa cantidad se compara con lo que el sistema tiene **hoy**, así que lo que se vendió desde que se contó no sale como faltante. Si el sistema cambió desde que se escaneó (se vendió algo), la columna **Sistema** muestra los dos datos, por ejemplo «5 (hoy 3)»; si ya habías corregido la cantidad antes de esas ventas, pulsa **Poner al día** (las flechas circulares) para compararla con las existencias de hoy.
4. Si hay muchos errores, **Devolver para recontar**: el conteo regresa al encargado y las decisiones se borran.
5. Pulsa **Confirmar y cerrar inventario**. Se aplican los ajustes (a un accesorio se le suma o resta la diferencia; los equipos no encontrados se dan de baja) y queda en el **historial**.

El resumen se puede **imprimir** (con renglones de firma) y **exportar** a Excel. Un inventario sin terminar se puede **cancelar**: no cambia nada.
"""
INVFIS_JS = INVFIS.format(donde="Inventario › Inventario físico",
    previa=" Antes de enviarlo, el sistema **revisa lo que se movió en la tienda mientras contabas** (ventas, envíos y lo que llegó). Lo vendido o enviado después de contarlo no es problema; pero **lo que llegó y no se vendió debe estar en el estante**: si ese artículo no lo contaste (o lo contaste antes de que llegara), aparece marcado en ámbar para que lo cuentes. Puedes **Volver a contar** o **Enviar a revisión de todos modos**.")
INVFIS_WEB = INVFIS.format(donde="Inventario físico (menú de la izquierda)", previa="")

# ───────────────────────────── Control de mermas ─────────────────────────────
MERMAS = """## Control de mermas

Junta en un solo lugar todo lo que **cambió las existencias fuera de una venta, una devolución o un traspaso**: salidas y ajustes manuales de accesorios, bajas de equipos y ajustes de inventario físico. Para cada uno muestra **quién**, **cuándo**, **qué** (con su código o IMEI), **cuántas piezas**, **por qué** y **cuánto vale a costo**. Está en **Inventario › Control de mermas**.

- **Período y tienda:** el administrador elige la tienda (o todas) y el período (últimos 7 o 30 días, este mes o el mes pasado). Un encargado ve solo la suya.
- **Vistas:** **Por motivo**, **Por persona**, **Por tienda** y **Detalle**. Arriba aparecen la **pérdida a costo**, los **ajustes al alza** y la **principal causa**.
- **Todo cambio manual lleva su motivo:** al sacar, ajustar o dar de baja existencias hay que elegir un motivo. Sin motivo el sistema no lo registra.
- **Aviso al administrador:** cuando un encargado saca o ajusta existencias, o da de baja un equipo, a los administradores les llega un aviso con quién, qué, cuánto y por qué.
"""

# ───────────────────────────── Traspasos ─────────────────────────────
TRASPASOS = """## Traspasos

Mueven mercancía entre sucursales (y la bodega). Los manejan encargados y administradores; en el taller, también el **técnico encargado** (para pedir y recibir refacciones). Un técnico que no es el encargado no ve este módulo.

Cada traspaso tiene **un solo folio**: cuando una solicitud se acepta y se genera su envío, los dos llevan el mismo número, así que se siguen como uno solo.

### Enviar mercancía
1. Entra a **Traspasos** y pulsa **Nuevo envío**.
2. Elige la tienda destino y agrega los productos{buscar}. Escribe la cantidad o, en equipos, elige los **IMEI** que viajan. Si **escaneas** (o escribes completo) el IMEI de un equipo, se **agrega solo** a la lista: ya no hay que buscarlo entre los IMEI disponibles.
3. Al confirmar, la mercancía **sale del inventario** de tu tienda y queda «en tránsito». En el detalle de un traspaso, cada equipo aparece en **su propio renglón con su IMEI completo**: quien recibe puede revisar uno por uno los que le llegan.
4. Si te equivocas antes de que la reciban, puedes **Anular** el envío.

### Pedir mercancía
Con **Nueva solicitud** le pides a otra tienda que te surta algo. En equipos eliges los **IMEI** concretos; en accesorios, la cantidad. **No se puede pedir más de lo que la otra tienda tiene.** La otra tienda la **acepta** (**Aceptar y enviar**, que genera el envío) o la **rechaza**.{editar}

### Recibir mercancía
1. Abre el traspaso que llegó y pulsa **Recibir**.
2. Compara lo que llegó con lo que dice el traspaso. Si todo llegó, pulsa **Confirmar recepción**.
3. Si falta algo, captura lo que sí llegó: lo que faltó queda como **faltante**.

### Faltantes
**Faltantes por resolver** lista lo que no llegó. Cada faltante se resuelve de una de estas formas:
- **Recibido tarde**: apareció y se da de alta en la tienda.
- **Reintegrado al origen**: regresó a la tienda que lo envió.
- **Baja**: se da por perdido.
"""
TRASPASOS_JS = TRASPASOS.format(buscar=" (escribe parte del nombre, el código o el IMEI y elige de la lista)",
    editar="""

Quien pidió puede **Editar solicitud** mientras la otra tienda no la haya aceptado ni rechazado: cambia la tienda, agrega productos o quita los que ya estaban, y la solicitud vuelve a quedar como nueva para quien surte.

Un **administrador** puede corregir un traspaso aunque ya se haya completado: en su detalle, **Editar (reabre el traspaso)** o **Reabrir envío**. El sistema **revierte lo que se movió** (la mercancía regresa a la tienda de origen, siempre que la otra tienda no la haya usado todavía) y la solicitud queda nueva para cambiarla. Siempre pide confirmación.""")
TRASPASOS_WEB = TRASPASOS.format(buscar="", editar="")

# ───────────────────────────── Clientes y cuentas ─────────────────────────────
CLIENTES = """## Clientes

Guarda el nombre, el teléfono y el correo de quien compra, y su **historial de compras**.

- **Nuevo cliente** y **editar**: encargados y administradores. Los vendedores solo consultan.
- Un cliente se **desactiva** (no se borra) para conservar su historial; solo lo hace un administrador.
- Al vender, escribir el teléfono de un cliente existente trae su registro.

## Cuentas por Cobrar

Control de lo que los clientes le deben a la tienda y de sus pagos.

### Registrar una cuenta
Pulsa **Nueva cuenta** y captura el cliente, el monto y el **número de factura** (hasta 20 caracteres).

### Cobrar un abono
1. Abre la cuenta con el icono del ojo y pulsa **abonar**.
2. Escribe el monto y la forma de pago, y pulsa **Confirmar pago**.
3. El abono queda en el historial de la cuenta (icono del reloj). Si es en efectivo, entra a la caja del turno.

Un abono hecho **sin conexión** se guarda en la computadora y se envía solo después.
"""

# ───────────────────────────── Garantías ─────────────────────────────
GARANTIAS_COMUN = """## Garantías

Registra los reclamos de garantía de productos vendidos y su viaje hasta el proveedor y de regreso.

### Registrar una garantía
{pasos}

> Un **equipo de vitrina** (aún sin vender) con una falla también va con el proveedor. {vitrina} No hay venta ni cliente. Mientras está fuera **no cuenta en la existencia**, y vuelve cuando la garantía se entrega. Si el proveedor lo cambia por otro, captura el IMEI del equipo nuevo.

> Si el producto se vendió con el **sistema anterior**, {anterior} captura la fecha de compra, el nombre del producto y los días de garantía (que aún deben estar vigentes) y, si los tienes, el IMEI, el folio y el proveedor.

### El recorrido
| Paso | Quién lo registra |
|---|---|
| Recibido en sucursal → En tránsito a bodega | La sucursal |
| En tránsito a bodega → Recibido en bodega | Administrador |
| Recibido en bodega → En tránsito al proveedor → En el proveedor | Administrador |
| En el proveedor → Reparado, Cambio físico o Rechazado | Administrador |
| Reparado o Cambio físico → Viaje de retorno | Administrador |
| Viaje de retorno → Listo para entrega | La sucursal |
| Listo para entrega → Entregado | La sucursal |

Una garantía **rechazada** puede volver al cliente directamente (Listo para entrega) o con un viaje de retorno. El plazo para resolver una garantía es de **30 días hábiles** (lunes a viernes); la que se pasa aparece como vencida.
"""
GARANTIAS_JS = GARANTIAS_COMUN.format(
    pasos="""1. Entra a **Garantías** y pulsa **Nueva garantía**.
2. **Busca la venta** (por folio o por IMEI). El sistema revisa si el producto **sigue en garantía**.
3. Escribe el motivo del reclamo y los datos de contacto, y pulsa **Recibir producto**. Se genera un folio **GAR-…**.""",
    vitrina="En **Nueva garantía** marca «Equipo de vitrina» y escribe su IMEI.",
    anterior="marca «Se vendió con el sistema anterior» y")
GARANTIAS_WEB = GARANTIAS_COMUN.format(
    pasos="""1. Entra a **Garantías** y pulsa **Recibir garantía**.
2. Elige de dónde viene el producto: **Venta de este sistema** (escribe el número de venta y el IMEI o código; **Revisar si todavía tiene garantía** te dice hasta cuándo cubre), **Venta anterior** o **Equipo de vitrina**.
3. Escribe la **falla reportada**, cómo se recibe y los datos de contacto, y pulsa **Recibir garantía**. Se genera un folio **GAR-…**.""",
    vitrina="Elige «Equipo de vitrina» y escribe su IMEI.",
    anterior="elige «Venta anterior» y")

# ───────────────────────────── Órdenes de servicio ─────────────────────────────
ORDENES = """## Órdenes de Servicio

Cubre las reparaciones: se **recibe** el equipo en la tienda, se **repara** en el taller y se **entrega** al cliente.

### Recibir un equipo
1. Entra a **{entrada}** y pulsa **Nueva orden**.
2. Busca o captura al **cliente** (nombre y teléfono).
3. Captura marca, modelo, IMEI, **accesorios que deja**, **estado físico** y la **falla reportada**.
   - **Revisión rápida:** marca en cada punto (enciende, pantalla y táctil, botones, cámaras, bocina y micrófono, carga, señal y Wi-Fi, humedad) si está **Bien**, **Falla** o **No se pudo probar**. Es tu constancia de cómo llegó el equipo y sale impresa en el comprobante.
   - **Código de desbloqueo:** si el cliente te da su PIN o contraseña para poder probarlo, anótalo aquí. Si es un **patrón** de puntos, pulsa **Dibujar patrón** (en JSystem, el botón **Patrón**): toca el primer punto y arrastra sin soltar por los demás (mínimo 4); queda guardado como «Patrón: 1-5-9-8» y, al consultarlo, se ve **dibujado**. **Solo lo ve el taller**: no se imprime, no se manda por WhatsApp y se **borra solo** al entregar o cancelar la orden.
4. Escribe el **tiempo estimado**, en horas o días (por omisión, 3 horas; máximo 30 días). El sistema dice «Se le promete para el…» con día y hora.
5. Si el cliente deja un **anticipo**, regístralo con su forma de pago. En efectivo entra a la caja del turno.
6. Pulsa **Recibir equipo**. Se genera el folio **OS-…** que se le entrega al cliente.
7. **Imprime el comprobante** y entrégaselo al cliente (el programa te lo ofrece al terminar; también está en el botón **Comprobante** o **Imprimir comprobante** de la orden). Lleva el folio, el equipo, la falla, el anticipo, la fecha prometida, un **código QR** para seguir la reparación desde su celular y las condiciones del servicio. Que el cliente lo **firme**. Sale bien en impresoras de **58 mm** y de **80 mm**: en la web elige el papel arriba del comprobante (se recuerda en esa computadora); si el ticket es largo, JSystem lo reparte en varias hojas sin cortar el QR.
8. **Toma fotos del equipo** en la misma orden (sección **Fotos del equipo**): golpes, rayones o la pantalla, antes de trabajarlo. Puedes subir hasta 8; en el celular hay un botón para **tomar la foto** y otro para **elegirla de la galería**. Se borran solas 90 días después de que la orden se entrega o cancela.

La recepción también funciona **sin conexión** en JSystem: el comprobante sale con un folio provisional y sin QR; el definitivo (OS-…) se le da cuando se envíe al servidor.

### Avisar al cliente por WhatsApp
En cualquier orden pulsa **Avisar por WhatsApp**. El sistema escribe el mensaje por ti y abre WhatsApp con el teléfono del cliente: solo revisa y pulsa **enviar**. Por omisión elige el aviso que corresponde al momento, o puedes escoger otro de la lista:
- **Equipo recibido:** con su folio, la fecha prometida y el enlace para seguir la reparación.
- **Cotización para autorizar:** el diagnóstico y el costo, pidiéndole que responda SÍ o NO.
- **Cambio de precio:** de cuánto a cuánto cambió, pidiendo su autorización.
- **Equipo listo:** cuánto queda por pagar y dónde recogerlo.
- **Esperando una refacción:** le explica que la reparación espera una pieza y que le avisarás cuando llegue.
- **No se pudo reparar:** le explica con el motivo que el equipo no tuvo reparación y que pase a recogerlo.
- **Recordatorio de recoger** y **Gracias y garantía** al entregar.

Cada aviso queda anotado en la **bitácora** de la orden (qué se avisó y quién lo hizo). El teléfono del cliente debe tener 10 dígitos; si no, corrígelo en **Clientes**. El cliente también puede seguir su reparación solo, con el QR del comprobante o en la página de seguimiento, con su folio y los últimos 4 dígitos de su teléfono.

### Autorización del cliente al precio
Para que no haya sorpresas al cobrar, el costo se **cotiza y el cliente lo autoriza**:
1. El técnico revisa el equipo, agrega los servicios y refacciones con su precio y pulsa **Pedir autorización**. La orden queda «Esperando la respuesta del cliente».
2. En la tienda se le avisa al cliente (WhatsApp o llamada). Cuando responde, se pulsa **Respuesta del cliente**, se marca si **autorizó** o **no**, **cómo** lo dijo (en persona, llamada, WhatsApp) y, si quieres, una nota. Queda registrado quién lo anotó y cuándo.
3. Mientras falte la autorización, **no se puede marcar la orden como lista ni entregarla**. Si el cliente no autoriza, se cancela la orden o se cambia la cotización y se vuelve a pedir.
4. Si **después de autorizado** se agrega o quita un servicio y el total cambia, la autorización **vuelve a quedar pendiente** y el sistema avisa en la tienda («cambió el precio de $A a $B»): hay que avisarle al cliente del cambio y registrar su respuesta.

### El camino de un equipo
1. La **sucursal** recibe el equipo, lo registra y, si el cliente decide dejar un **anticipo**, lo cobra ahí mismo.
2. La sucursal **manda el equipo al taller**; el técnico lo registra con **Recibir en el taller**.
3. En el **taller** se diagnostica, se cotiza y se cambia la pieza que haga falta.
4. Al terminar (**Marcar lista**), el equipo **regresa a la sucursal que lo mandó**. En cuanto llega, la sucursal pulsa **Regresó a la tienda**: hasta entonces la orden aparece como «Lista · en el taller», **no se puede cobrar** y el sistema **no deja avisar al cliente** que ya puede recogerlo.
5. La **sucursal cobra** el saldo y lo **entrega** al cliente.
Si el equipo se repara en la misma sucursal que lo recibió, no hay viaje y este paso no aparece.

### En el taller
Los técnicos trabajan en un solo taller y atienden las órdenes de **todas las tiendas**.
1. Un técnico **recibe en el taller** una orden libre (el equipo ya llegó de la tienda) o el técnico encargado se la **asigna**.
2. **Iniciar reparación** cambia la orden a «En reparación».
3. Agrega **renglones** (refacciones y servicios con su precio) y **observaciones**, que quedan en el historial. El **técnico encargado** ve el **costo** de cada refacción; los demás ven solo el precio. Las **refacciones salen del inventario del taller** (la sucursal del técnico), no del de la sucursal que recibió el equipo; cada renglón dice de dónde sale.
4. **Pedir autorización** al cliente cuando el costo esté listo (ver arriba).
5. **Marcar lista** avisa a la sucursal que el equipo ya está listo en el taller y debe regresar. El botón **no se puede pulsar mientras la orden no tenga servicios ni refacciones**: agrega al menos un renglón. Si hace falta corregir algo, **Reabrir** (el equipo vuelve a quedar en reparación).

### Cuando falta una refacción
Si la reparación no puede seguir porque hay que esperar una pieza, pulsa **Esperar refacción** y escribe cuál es (por ejemplo, «pantalla original, llega el jueves»).
- La orden se ve como **Esperando refacción** en el taller y en las listas, y al cliente le aparece en su seguimiento que se espera una pieza. Desde **Avisar por WhatsApp** puedes mandarle el aviso ya escrito.
- Mientras espera, no se puede iniciar ni marcar lista.
- Cuando llega la pieza, pulsa **Llegó la refacción**: la reparación continúa. El sistema anota cuánto esperó para **no contarlo como tardanza del taller** en el reporte.

### Cuando la reparación no queda
Si el técnico revisa el equipo y la reparación **no es posible** (no se consigue la pieza, daño en la placa, humedad, no conviene repararlo, el cliente desistió…), pulsa **No quedó**, elige el **motivo** de la lista y, si quiere, cuenta qué pasó (si elige «otro motivo», es obligatorio). Solo se puede reportar mientras la orden está recibida o en reparación.
- La orden pasa a **Sin reparación** (aparece en rojo en las listas): el equipo regresa a la sucursal igual que uno reparado, la sucursal recibe un aviso con el motivo y todo queda en la bitácora.
- Al llegar a la tienda se pulsa **Regresó a la tienda**. Desde **Avisar por WhatsApp** se manda al cliente el aviso **No se pudo reparar**, ya escrito con el motivo.
- Para devolverle el equipo **sin cobrar**, pulsa **Solo entregar**: la orden queda como «Entregada sin reparar» y **no se genera ninguna venta** (una venta en $0 solo ensucia los reportes). Si la orden tiene un anticipo, el de **efectivo** se devuelve en ese momento de la caja; uno con tarjeta o transferencia se devuelve por el mismo medio. Si se acuerda cobrar algo (por ejemplo la revisión), déjalo como renglón y usa **Entregar y cobrar**.
- Si fue un error, **Reabrir** deja la orden otra vez en reparación y borra el reporte.
- El **reporte de tiempos del taller** cuenta cuántas **no quedaron** y por qué motivo.

### Ver las órdenes de todas las sucursales
El **administrador** ve por omisión las órdenes de **todas las sucursales**; con el selector de sucursal puede quedarse con una sola. Para ver, por ejemplo, **lo que se recibió hoy**, deja el estado en **Todas** y elige **Recibidas hoy** en el filtro de fecha (también hay **Últimos 7 días** y **Este mes**). Cada orden indica en qué sucursal se recibió.

### Equipos listos que nadie recoge
Entra a **{por_recoger}**: muestra las reparaciones **listas que esperan al cliente**, las más viejas primero, con **dónde está** el equipo (en el taller o ya en la tienda), los **días sin recoger** (cuentan desde que regresó a la tienda), cuándo fue el último recordatorio y cuánto queda por pagar. Con **Avisar / recordar por WhatsApp** mandas el mensaje ya escrito.
- A los **7 días** conviene recordárselo al cliente; a los **30 días** vence el plazo que dice su comprobante.
- Cada mañana el sistema avisa a cada sucursal (en sus avisos) cuántos equipos llevan una semana o más sin recogerse y a quién todavía no se le ha recordado. **No manda nada al cliente por sí solo**: el aviso siempre lo envía una persona.

### Reporte de tiempos del taller
{reporte_entrada} (para administradores, encargados y el técnico encargado) muestra, del periodo que elijas (por omisión los últimos 30 días): equipos recibidos y entregados, el **tiempo promedio de taller**, cuántos quedaron **listos a tiempo** según la fecha prometida, las reparaciones **retrasadas** (con cuántas horas), el desempeño **por técnico** y los equipos que esperan en la tienda. El tiempo de taller no cuenta las horas que estuvo detenida esperando una refacción.

### Entregar al cliente
1. En la tienda, abre la orden **Lista** (con el equipo ya de regreso: **Regresó a la tienda**) y pulsa **Entregar y cobrar**.
2. El sistema calcula el total, **descuenta el anticipo** y cobra la diferencia. Se genera la venta y su ticket.
3. La venta es de la **sucursal que cobra**, pero las refacciones se descuentan del **inventario del taller**, que es donde estaban.
4. Una orden se puede **cancelar** antes de entregarse; si tenía anticipo, se devuelve.
{consultar}"""
ORDENES_JS = ORDENES.format(entrada="Órdenes de Servicio", consultar="", por_recoger="Órdenes de Servicio → Por recoger", reporte_entrada="El botón **Reporte de tiempos** de Órdenes de Servicio")
ORDENES_WEB = ORDENES.format(entrada="Recibir equipo", por_recoger="Equipos por recoger", reporte_entrada="**Reporte del taller**", consultar="""
### Consultar una orden
**Consultar orden** busca por folio o muestra las más recientes, para decirle al cliente en qué va su equipo.
""")

# ───────────────────────────── Corte de Servicios ─────────────────────────────
SERV = """## Corte de Servicios

Los **saldos (recargas), pagos de servicios, pines y pagos de PayJoy** se operan en los sistemas de los proveedores: no pasan por las ventas ni por la caja. Este corte sirve para saber **cuánto se vende** en cada servicio y **cuánto te queda de ganancia**, por día, por sucursal y mes con mes.

### Hacer el corte del día
1. El **encargado** abre **Corte de servicios**{dia}.
2. Captura, por cada servicio, cuántas **operaciones** hizo y el **importe** vendido. La **ganancia se calcula sola**.{flechas}
3. Pulsa **Guardar corte**. Puedes corregirlo mientras no esté confirmado.
4. Un **administrador** lo compara con los reportes del proveedor, {confirmar}. Si hay diferencias, debe escribir un comentario.
5. Si hay que cambiar algo ya confirmado, un administrador usa **Reabrir para corregir**.

### Cómo se calcula la ganancia
- **Servicel** y **Tae Recargas**: un **porcentaje de lo vendido** (8.1 % y 7.6 %). Si vendes $10,000 en Servicel, ganas $810.
- **Pagos de servicios**: se cobran **$15 al cliente** y el proveedor cobra **$4** por operación; quedan **$11** por cada pago.
- **Pagos PayJoy**: solo se cuentan los **clientes que pagaron**. La comisión de PayJoy llega por separado y no se calcula aquí; si quieres, anota el importe recibido.

### Cuánto se vende y cuánto queda
{resumen}

El administrador da de alta los servicios y define cómo se gana con cada uno (porcentaje, comisión al cliente, lo que cobra el proveedor, o si solo se cuentan las operaciones) en **{donde}**. Un cambio aplica a los cortes nuevos; los ya capturados conservan lo que valía cuando se capturaron.{offline}
"""
SERV_JS = SERV.format(
    dia="",
    flechas=" Con las flechas **↑ ↓ ← →** (o **Enter**) pasas de un campo a otro, como en Excel.",
    confirmar="y pulsa **Confirmar con los reportes**",
    resumen="**Historial y resumen** muestra los cortes anteriores y, del periodo que elijas, **lo vendido y la ganancia** por servicio, por sucursal y **mes con mes** (últimos 6 meses). Lo de PayJoy aparece en su renglón, pero no suma a lo vendido.",
    donde="Servicios y comisiones",
    offline=" El corte también se puede capturar **sin conexión**.")
SERV_WEB = SERV.format(
    dia=", elige el **día** (y la sucursal, si es administrador)",
    flechas="",
    confirmar="escribe el importe de cada uno y pulsa **Confirmar corte**",
    resumen="Debajo del corte está el resumen del periodo (este mes, el mes pasado, los últimos 30 días o este año): **lo vendido y la ganancia** por servicio, por sucursal (administrador) y **mes con mes**. Lo de PayJoy aparece en su renglón, pero no suma a lo vendido.",
    donde="Servicios",
    offline=" Para capturar el corte **sin conexión**, usa JSystem.")

# ───────────────────────────── Compras ─────────────────────────────
COMPRAS = """## Compras y Proveedores

### Proveedores
El directorio de los proveedores con los que se trabaja (**Nuevo proveedor**, editar). Los productos se registran con su proveedor.

### Compras y cuentas por pagar
Registra lo que se le **debe** a un proveedor por una compra. {donde}
1. Pulsa **Registrar compra**: elige el proveedor, escribe el **monto total** y la sucursal. Si quieres, anota el folio de su factura y la fecha de vencimiento (si no la pones, se calcula con los días de crédito del proveedor). **No hace falta capturar los productos**: la mercancía se ingresa en **Inventario** o **Equipos**, donde queda registrado el proveedor, la fecha y la hora.
2. En la lista ves lo que se debe y los **días** que faltan (en rojo si ya venció). Abre una compra para **registrar pagos**, parciales o totales; el saldo baja con cada pago.
3. {pago}

Esta sección la usan encargados y administradores.
{catalogos}"""
COMPRAS_JS = COMPRAS.format(
    donde="Está en **Finanzas › Compras**.",
    pago="Un pago en **efectivo** sale de la caja que elijas (por omisión, la principal de la sucursal); con tarjeta o transferencia no toca la caja.",
    catalogos="""
### Catálogos
En **Inventario › Catálogos** el administrador mantiene los **colores** (renombrar uno lo cambia en todos los artículos que lo llevan), las **secciones** de cada sucursal y las **descripciones** de cada categoría. No se puede eliminar lo que algún artículo usa.

### Categorías
En **Inventario › Categorías** el administrador ve el árbol completo: cada **categoría principal** con su tipo (celular, tablet, accesorio o servicio) y, debajo, sus subcategorías. Los botones de cada fila permiten **agregar una subcategoría**, **editar** o **eliminar** (solo si no tiene subcategorías ni artículos activos).
""")
COMPRAS_WEB = COMPRAS.format(
    donde="",
    pago="Se paga en efectivo o transferencia (y con tarjeta solo si el negocio la acepta). El historial de la compra muestra cada pago; un pago de caja se registra con el motivo «Pago a proveedor».",
    catalogos="")

# ───────────────────────────── Descuentos ─────────────────────────────
DESCUENTOS = """## Descuentos

El administrador define **reglas de descuento**. Cada regla puede limitarse a una **categoría** (una principal, que vale también para sus subcategorías, o solo una subcategoría), a un **tipo de producto**, a un **artículo específico** (un modelo de celular, un accesorio o un servicio) o a una **tienda**. También puede pedir un **monto mínimo** de compra, descontar un **porcentaje** o una **cantidad fija**, y valer solo ciertos **días de la semana** o entre dos **fechas**. El sistema las **aplica solo al cobrar**; el vendedor no captura descuentos a mano. Una regla se puede **activar o desactivar** sin borrarla.

{articulo}

### Solicitudes de descuento (administrador)
Arriba de la pantalla aparecen las **solicitudes** que piden vendedores y encargados desde el punto de venta: sucursal, quién la pidió, el artículo, su precio, cuánto piden y el motivo. Puedes **aprobar** lo pedido, **aprobar con otro monto** o **rechazar** (con un motivo opcional). La lista se actualiza sola y también llega un aviso.

### Promoción al vender un equipo
Por cada equipo (celular o tablet) que se lleva el cliente, ciertos artículos (un **chip**, una **mica**, un **protector**) reciben descuento. En **Configurar** el administrador arma una lista de reglas; cada una dice:
- a qué **categoría** de artículos aplica (incluye sus subcategorías);
- un **rango de precio de lista** (por ejemplo, protectores de hasta $249), o sin rango;
- cuántas **piezas por cada equipo** reciben el descuento;
- el **descuento**: de regalo, un porcentaje, un monto en pesos o un precio final fijo;
- el **redondeo** del precio que queda (al peso o a múltiplos de $5 o $10, hacia arriba o hacia abajo).

Ejemplo: *protectores de hasta $249, 50 % de descuento, redondeado al peso hacia arriba* → un protector de $249 paga **$125** y uno de $199 paga **$100**. Mientras escribes la regla, el sistema te muestra cuánto pagaría un artículo del precio que quieras probar.

Las reglas se revisan **en orden** (con las flechas las subes o bajas): a cada pieza le toca la primera que le corresponde y todavía tiene cupo. Cada regla se puede **apagar** sin borrarla, y el interruptor **Promoción activa** apaga todas.

En el punto de venta se aplica sola: el renglón muestra **🎁 Promoción de equipo** con el precio ya rebajado. Si el cliente lleva más piezas de las que cubre la promoción, solo las primeras la reciben.{offline}
"""
DESCUENTOS_JS = DESCUENTOS.format(articulo="Para una regla de un artículo, en **Nueva regla** pulsa **Elegir artículo**, busca por nombre y elígelo; por omisión vale en **todas las sucursales y la bodega**. Para cambiar el **precio** de algo (no solo descontarlo), usa Inventario o Equipos: los precios son generales.", offline=" Funciona también sin conexión, con la última configuración descargada. Las tiendas con un JSystem anterior deben actualizarse antes de usar descuentos en porcentaje o con redondeo.")
DESCUENTOS_WEB = DESCUENTOS.format(articulo="La regla por artículo específico se arma desde JSystem. Para cambiar el **precio** de algo, usa Inventario: los precios son generales.", offline="")

# ───────────────────────────── Reportes ─────────────────────────────
FINANZAS_TALLER_JS = """## Finanzas del taller

Aquí está lo que antes se llevaba en la libreta cada mes: cuánto dejaron las reparaciones, cuánto se gastó, cuánto queda y cuánto le toca a cada técnico. **Solo lo ve la administración.** Se abre en **Finanzas del taller** y se elige el mes arriba. Casi todo se calcula solo con lo que ya se capturó; lo único que se captura aquí son los **gastos** y los **descuentos** de los técnicos.

### Qué muestra
- **Ventas de reparaciones:** lo cobrado en las órdenes **entregadas** ese mes (cuenta por la fecha de entrega), con las del mes anterior para comparar.
- **Inversión en piezas:** lo que costaron las refacciones usadas en esas órdenes.
- **Ganancia bruta:** ventas menos piezas.
- **Ganancia neta:** la ganancia bruta menos los gastos fijos (sueldos) y los operativos (renta, gasolina, herramienta…). Es la base de las comisiones.
- **Lo que dejó cada sucursal:** órdenes, ventas, piezas y ganancia de cada tienda que recibió reparaciones.

### Capturar un gasto
1. Pulsa **Capturar gasto**.
2. Escribe la **fecha**, el **concepto**, elige la **categoría** y el **monto**. La sucursal es opcional (si no eliges, es un gasto general).
3. Si es algo que se paga **cada mes** (la renta, el internet), marca **Se repite cada mes**: cuenta solo en todos los meses siguientes. Si un día deja de pagarse, edítalo y escribe en qué mes **deja de contar**.
4. Pulsa **Guardar gasto**. Para corregirlo o quitarlo, elígelo en la tabla y usa **Editar** o **Quitar**.

Los **sueldos** cuentan como gasto fijo; todo lo demás, como operativo. La **nómina de los técnicos que se paga desde el sistema** (Nómina) aparece sola en la tabla de gastos y no se vuelve a capturar. Esas filas no se editan aquí: si algo está mal, elige la fila y pulsa **Abrir Nómina** para corregirlo allá. La nómina del personal de las tiendas no entra en este reporte. Los sueldos que se pagan por fuera sí se capturan, con la categoría **Sueldos**.

### Comisiones de los técnicos
Cada técnico gana un **porcentaje de la ganancia neta** del mes (por ejemplo, el técnico encargado 10 % y los auxiliares 4 %).
1. Elige al técnico en la tabla y pulsa **Porcentaje** para fijar el suyo.
2. Para **descontarle** algo de su comisión (una cuenta pendiente, algo que compró), pulsa **Descuentos del mes**, escribe el concepto y el monto y pulsa **Agregar descuento**. Puedes quitar los que te equivoques.
3. La tabla muestra para cada uno la **comisión**, sus **descuentos** y **lo que se le paga**. Si los descuentos son mayores que la comisión, queda en rojo: es lo que el técnico todavía debe.

**Generado** es lo que dejaron las reparaciones de ese técnico (lo cobrado menos las piezas); sirve para ver cómo trabajan, pero la comisión no depende de él sino de la ganancia neta de todo el taller. Si el mes termina en pérdida, no hay comisión.

> El reporte se calcula en vivo: si corriges una venta, un gasto o un porcentaje, las cifras cambian al actualizar. Cambiar el porcentaje de un técnico afecta también a los meses pasados cuando los consultes.
"""

REPORTES_JS = """## Reportes

En JSystem hay **dos** pantallas: **Reportes** (para encargados y administradores) y **Reportes de Gerencia** (solo administradores).

### Reportes (ventas)
Abre las ventas **por ticket** (cada venta, para reimprimir o revisar) o **por artículo vendido**: cuántas piezas de cada artículo se vendieron y por cuánto, con **Resumen por artículo** y el desglose renglón por renglón (como en el sistema anterior). Elige el periodo y la sucursal (un encargado solo ve la suya) y se puede imprimir o exportar.

### Reportes de Gerencia
Para administradores (todas las sucursales o la que elijan). Arriba eliges el **periodo** (Hoy, 7 días, Este mes, Mes pasado o un rango de fechas) y la **sucursal**; cada pestaña responde con eso. **Historial de ventas** abre el detalle de cada ticket.

| Pestaña | Para qué sirve |
|---|---|
| Resumen | Ventas, utilidad y margen; comparativo por sucursal, lo que más se vende y el valor del inventario |
| Estado de resultados | Ventas, devoluciones, costo, utilidad bruta, gastos y utilidad neta, comparados con el periodo anterior |
| Ventas por día | Cuánto se vendió cada día, con gráfica |
| Por categoría | Qué categorías venden más y cuánto dejan |
| Sin movimiento | Accesorios con existencia que no se han vendido en los días que elijas, y el dinero que tienen parado |
| Equipos rezagados | Equipos que llevan mucho tiempo sin venderse, con su costo |
| Caja de todas las sucursales (administrador) | El efectivo y los movimientos de hoy de todas las cajas |

> Los **gastos** del estado de resultados son las **salidas de caja** del periodo, agrupadas por motivo (renta, flete, nómina…). Los retiros del dueño, los traslados, los pagos a proveedores, los préstamos a empleados y la apertura y cierre de caja **no** cuentan como gasto. Es una guía para decidir, no un documento contable: no incluye lo que no pasa por la caja.
"""
REPORTES_WEB = """## Reportes

**Ventas** es para encargados (su tienda) y administradores. Los demás reportes (Resultados, Gerencial, Rezagados y Caja) son **solo para administradores**.

| Reporte | Para qué sirve |
|---|---|
| Ventas | Lo vendido hoy, en 7 días o en el mes, con el detalle de cada venta |
| Resultados | Estado de resultados, ventas por día, ventas por categoría y artículos sin movimiento |
| Gerencial | Comparativo de ventas y utilidad entre sucursales, lo más vendido y el valor del inventario |
| Rezagados | Equipos que llevan mucho tiempo sin venderse |
| Caja (administrador) | El saldo y los movimientos de hoy de todas las cajas |

### Resultados
Elige la **sucursal** (si eres administrador) y el **periodo** (Hoy, Ayer, 7 días, Este mes, Mes pasado u otras fechas).
- **Estado de resultados**: ventas, menos devoluciones, menos el costo de lo vendido, igual a la **utilidad bruta**; menos los **gastos de caja**, igual a la **utilidad neta**. Se compara con el periodo anterior.
- **Por día**: cuánto se vendió cada día, con gráfica.
- **Por categoría**: qué categorías venden más y cuánto dejan.
- **Sin movimiento**: accesorios con existencia que no se han vendido en los días que elijas, y el dinero que tienen parado.
- Cada vista tiene **Descargar para Excel**.

> Los **gastos** son las **salidas de caja** del periodo, agrupadas por motivo (renta, flete, nómina…). Los retiros del dueño, los traslados, los pagos a proveedores, los préstamos a empleados y la apertura y cierre de caja **no** cuentan como gasto. Es una guía para decidir, no un documento contable.
"""

# ───────────────────────────── Empleados ─────────────────────────────
EMPLEADOS_JS = """## Empleados, nómina y comisiones

Sección de administración (los descansos también los ve el encargado).

### Empleados
Aquí se da de alta a quien va a usar el sistema: su sucursal, su **rol** y su contraseña (también se cambia aquí si la olvidó). La sucursal solo **liga** al **encargado** (y al técnico, que trabaja en su taller); para un vendedor (apoyo) o un administrador es su **«sucursal base»**, de referencia (nómina): pueden trabajar en cualquiera. **Si cambias la sucursal de alguien**, JSystem le avisa y le pide volver a entrar. Los técnicos se marcan con su rol; uno de ellos puede ser el **técnico encargado**, que reparte las órdenes del taller.

### Nómina
1. **Abrir período**: elige desde, hasta y la fecha de pago. Cada empleado activo entra con su sueldo.
2. Abre la línea de cada empleado (doble clic) y agrega **percepciones** (bonos) o **deducciones** (faltas). El abono a un préstamo se descuenta solo.
3. Los **faltantes de caja** también se descuentan solos: el sistema agrega como deducción el faltante de cada corte cerrado en el período, a quien cerró el turno (si lo cerró un administrador, a quien lo abrió). **Traer faltantes de caja** los actualiza. Si quitas esa deducción, el faltante queda **perdonado**. Los sobrantes no se compensan.
4. **Pagar**: elige de qué caja sale el dinero y confirma.
5. Cuando todos estén pagados, **Cerrar período**. **Períodos anteriores** guarda el historial.

### Comisiones
Elige el **mes**. **Encargados** muestra su comisión (un porcentaje de lo que vendió su sucursal, sin PayJoy) y **PayJoy** la de cada equipo, para quien lo vendió. **Marcar pagada** la congela. **Tasas** cambia los porcentajes.

### Descansos y préstamos
Cada empleado acumula días de descanso con un **máximo**; la lista avisa a quien lo excede. Por empleado puedes **registrar un descanso**, ver su historial y (administrador) **fijar su saldo inicial** o hacer un **ajuste**. Ahí mismo se otorga un **préstamo**: sale del efectivo de una caja, solo puede haber uno activo por empleado y se descuenta en la nómina.
"""
EMPLEADOS_WEB = """## Empleados, nómina y comisiones

Sección de administración (los descansos también los ve el encargado).

- **Empleados**: personal, sucursal y **rol**. Aquí se da de alta a quien va a usar el sistema y se cambia su contraseña si la olvidó. La sucursal solo **liga** al encargado (y al técnico); para un vendedor o un administrador es su «sucursal base» y pueden trabajar en cualquiera. Los técnicos se marcan con su rol; uno de ellos puede ser el **técnico encargado**, que reparte las órdenes del taller.
- **Nómina**: períodos de pago con las **percepciones** y **deducciones** de cada empleado y su pago. **Historial de nómina** guarda los períodos anteriores. Los **faltantes de caja** se descuentan solos: el sistema agrega como deducción el faltante de cada corte cerrado en el período, a quien cerró el turno. **Traer faltantes de caja** los actualiza. Si quitas esa deducción, el faltante queda **perdonado**. Los sobrantes no se compensan.
- **Comisiones**: comisiones por ventas del periodo y su pago.
- **Descansos**: descansos y vacaciones del personal, con su saldo.
"""

# ───────────────────────────── Configuración (solo JSystem) ─────────────────────────────
CONFIG_JS = """## Configuración

Los datos de **esta computadora** (servidor, sucursal, impresoras) y del negocio. Se abre con el engrane de arriba a la derecha. Encargados y administradores pueden cambiarla. Al terminar pulsa **Guardar configuración**.

- **Este equipo:** la dirección del servidor y la **sucursal y caja** de esta computadora. Un **administrador** o un **vendedor** que entra aquí trabaja con esa sucursal y esa caja; un encargado o un técnico siempre trabaja con la suya.
- **Ticket e impresora:** la impresora de tickets y el texto del ticket.
- **Etiquetas:** la impresora y el tamaño de las etiquetas con código de barras (ver abajo).
- **Negocio** (administrador): logo, datos del negocio, pie del ticket, plazo de **devoluciones** y **formas de pago** (por ejemplo, habilitar la tarjeta).
- **Sucursales:** nombre, domicilio y teléfono que salen en el ticket de cada sucursal. Solo un administrador los cambia.
- **Sistema:** versión del programa y datos para soporte. El administrador ve además **Computadoras con JSystem**: cada computadora con su última sesión, su **versión** y si está **al día**, con la actualización **descargada** (falta cerrar el programa), **desactualizada**, con **error** o **sin reportar** (más de un día). Cada computadora reporta sola cada pocos minutos con la sesión abierta; si alguna aparece mal, ábrela y pulsa **Buscar actualizaciones**. Aquí también está **Actualizaciones**: el programa se actualiza solo (revisa al abrirse y cada 4 horas, baja la versión nueva y la instala **al cerrar** el programa). Si quieres instalarla de inmediato, pulsa **Buscar actualizaciones** y luego **Cerrar e instalar la actualización**; el programa se vuelve a abrir solo.

### Respaldo y limpieza automáticos (los hace el servidor)
- **Respaldo diario:** cada noche a las 11:30 pm el servidor guarda una copia completa de la base de datos en dos discos. Se conservan las últimas 14 copias y una por mes. Si falla, llega un aviso a los administradores. Como el servidor se apaga a medianoche, si al encenderlo en la mañana no hay un respaldo de las últimas 26 horas, lo hace solo a los pocos minutos.
- **Limpieza:** cada noche, después del respaldo, se borran las sesiones de entrada de más de 30 días, los avisos ya leídos de más de 90 días y el historial de cambios del catálogo de más de 24 meses. Las ventas, la caja, las compras y la nómina nunca se borran. Si la noche anterior no hubo respaldo, la limpieza no corre.

### Configurar y probar la impresora de etiquetas
1. En **Etiquetas** elige la **impresora** y el **tipo de papel**: **rollo continuo** (como el ticket) o **etiqueta de tamaño fijo** con su ancho y alto en milímetros (por ejemplo, 30 × 14).
2. Elige qué lleva la etiqueta: el nombre, el código escrito y el precio. A la derecha ves una **vista previa**: si algo se corta ahí, también se corta al imprimir.
3. En **Imprimir etiquetas** (a la derecha) escribe el **código**, el nombre y el precio si quieres, y la **cantidad**; pulsa **Imprimir etiquetas** y léela con el lector para comprobar que el código se entiende. Sirve también para imprimir cualquier código aparte de la prueba. Luego **Guardar configuración**.

Las etiquetas bajas (20 mm de alto o menos, como las de 30 × 14) salen en versión compacta: el nombre en un renglón, las barras con todo el alto posible y, abajo, el código a la izquierda y el precio a la derecha. Si el código es largo, las barras salen más finas: prueba con el lector; si no lee, quita el nombre o el código escrito.

### Si la etiqueta sale corrida o cortada
1. En **Etiquetas › Calibración de la impresión**, pulsa **Imprimir regla de calibración**: sale una etiqueta con un borde y marcas cada milímetro.
2. Mira qué números alcanzaron a salir. Si todo sale corrido, usa **Correr y escalar**: milímetros a la derecha y hacia abajo, y el porcentaje de tamaño. Un número negativo corre a la izquierda o hacia arriba. (Por ejemplo, si todo sale pegado a la izquierda, prueba con **11** mm a la derecha.)
3. Si el papel del controlador no es el de tu etiqueta, elígelo en **Papel del controlador**.

> La calibración se guarda **en cada computadora**: hay que hacerla en cada tienda que imprima etiquetas.
"""

# ───────────────────────────── Avisos (solo JSystem) ─────────────────────────────
AVISOS_JS = """## Avisos e historial de cambios

### Avisos
En el menú de la izquierda, **Avisos** muestra lo que el sistema te informa: un traspaso que llegó, el avance de una garantía, un inventario listo para revisar, un ajuste de inventario hecho por un encargado… El número rojo son los que no has leído. Haz clic en uno para abrir la pantalla a la que se refiere; **Marcar todas como leídas** los limpia.

**Llegan solos:** mientras tengas la sesión abierta, el sistema revisa cada 20 segundos. Cuando llega un aviso nuevo **suena una campanita**, sale un recuadro en la esquina de la pantalla (haz clic en él para abrir los avisos) y el número rojo se actualiza en todas las pantallas. Puedes **elegir el sonido** (Campana, Cristal, Suave, Din-don u OK) en esa misma pantalla: al escogerlo suena, y **Probar** lo repite. Si no quieres sonido, quita la casilla **Sonido al llegar un aviso** (cada computadora guarda su propia elección). En la versión web pasa lo mismo, con la página abierta; el celular solo suena si el navegador ya permitió el sonido (basta con haber tocado la pantalla una vez).

### Historial de cambios (administrador)
En Inventario, Descuentos y Empleados, el botón del reloj abre el **historial** de ese registro: quién lo cambió, cuándo y qué valor tenía antes y después.
"""

# ───────────────────────────── Sin conexión ─────────────────────────────
OFFLINE_JS = """## Trabajar sin conexión

Si se cae el internet o el servidor, JSystem **sigue trabajando** en esto:
- **Punto de Venta**.
- **Caja**: abrir el turno, mover efectivo y cerrar con su corte.
- **Recepción de equipos** para reparar.
- **Corte de Servicios**.
- **Abonos** a cuentas por cobrar.

Lo que captures se **guarda en la computadora** y se envía solo, **en orden**, cuando regresa la conexión. Arriba aparece un aviso de color, y en **Guardado en este equipo** ves cuántas cosas están pendientes.

- Las ventas conservan su **fecha y hora reales**.
- Las existencias se descuentan al enviarse. Si ya no había piezas, la venta se acepta igual porque ya ocurrió, y el stock puede quedar en negativo para revisarse.
- Si algo **no se acepta** (por ejemplo, un IMEI que ya se vendió), queda marcado con **error** en la lista de pendientes para que el encargado lo revise.
- El corte de caja sin conexión se calcula e imprime en el momento, y al volver la conexión se envía con sus fechas y horas reales.
- Una salida de efectivo no se deja si no alcanza el saldo guardado en la computadora.
- **Inventario** y **Equipos** se pueden **consultar** con la última copia guardada (el aviso rojo bajo el título dice de cuándo es). Dar de alta, editar, contar o ajustar sí necesitan conexión.

> Para trabajar así, la computadora debe haber abierto **Caja** o el **Punto de Venta** con conexión al menos una vez, y la persona debe haber iniciado sesión antes en esa computadora. El resto de las secciones (traspasos, compras, reportes…) necesitan conexión: si intentas entrar sin ella, el sistema avisa.
"""
OFFLINE_WEB = """## Trabajar sin conexión

La **versión web** necesita internet. Si se cae, usa **JSystem** en la computadora de la tienda: ahí siguen funcionando el Punto de Venta, la Caja, la recepción de equipos, el Corte de Servicios y los abonos. Lo que captures se guarda en esa computadora y se envía solo, en orden, cuando regresa la conexión.

- Las ventas hechas sin conexión conservan su **fecha y hora reales**.
- Si algo **no se acepta** al enviarse (por ejemplo, un IMEI que ya se vendió), queda marcado con **error** en **Guardado en este equipo** para que el encargado lo revise.
- Para entrar sin conexión, la persona debe haber iniciado sesión antes en esa computadora.
"""

# ───────────────────────────── Atajos ─────────────────────────────
ATAJOS_JS = """## Atajos de teclado

| Dónde | Tecla | Qué hace |
|---|---|---|
| Todo el programa | F1 | Abre esta ayuda en el tema de la pantalla actual |
| Todo el programa | Ctrl + 1 … 9 | Abre las primeras secciones del menú |
| Punto de Venta | F2 | Lleva el cursor al buscador (o lector de códigos) |
| Punto de Venta | Enter | Agrega el producto cuyo código escribiste o que quedó solo en la lista |
| Punto de Venta | ← → | Cambia de categoría: Todos, Equipos, Accesorios, Servicios |
| Punto de Venta | F4 | Lleva el cursor a los datos del cliente |
| Punto de Venta | F9 | Procesa la venta |
| Punto de Venta | + / − | Suma o resta una pieza a la línea marcada |
| Punto de Venta | Supr | Quita la línea marcada |
| Punto de Venta | Esc | Limpia el buscador |
| Corte de Servicios | ↑ ↓ ← → o Enter | Pasa de un campo a otro, como en Excel |
| Listas desplegables con varias opciones | Escribir | Abre un buscador: escribe parte del nombre (sin importar mayúsculas ni acentos) y pulsa Enter para elegir |

Con texto escrito en un campo, **+**, **−** y **Supr** son texto normal.

> En las listas desplegables con varias opciones (colores, categorías, proveedores…) también puedes **hacer clic y escribir** para buscar, en lugar de recorrer toda la lista.
"""
ATAJOS_WEB = """## Atajos de teclado

En la web casi todo se hace tocando. Los atajos de teclado están en **JSystem**: **F1** (ayuda), **F2** (buscar), **F4** (cliente), **F9** (cobrar), **← →** (cambiar de categoría) y **Ctrl + 1 … 9** (secciones del menú).

En las listas desplegables con varias opciones (colores, categorías, proveedores…) puedes **tocar y escribir** para buscar, en lugar de recorrer toda la lista.
"""

# ───────────────────────────── Rutina y preguntas ─────────────────────────────
RUTINA = """## Rutina diaria sugerida

### Al abrir
1. Inicia sesión con tu usuario.
2. En **Caja**, abre la caja con el fondo contado.
3. Revisa que no haya avisos de color arriba (conexión o ventas pendientes).
4. Encargados: revisa en **Inventario** los productos con poca existencia.

### Durante el día
- Cobra en el **Punto de Venta**.
- Registra los gastos y retiros como **movimientos de caja**, siempre con su motivo.
- Recibe equipos para reparar en **Órdenes de Servicio** y cobra los abonos en **Cuentas por Cobrar**.
- Cuando llegue mercancía, ingrésala en **Inventario** o **Equipos** (o recíbela con un **traspaso**) y registra lo que debes al proveedor en **Compras**.

### Al cerrar
1. Cuenta el efectivo y haz el **corte** en **Caja**.
2. Encargado: captura el **Corte de Servicios** del día.
3. Revisa que no queden pendientes por enviar.
4. Cierra tu sesión.

### De vez en cuando
- **Inventario físico**: una vez al mes, por temporada y cuando cambia el encargado de la tienda.
- **Control de mermas** y **Reportes**: para ver cómo va el negocio y qué está parado.
"""

FAQ_COMUN = """## Preguntas frecuentes

### Me dice «La caja está cerrada» al cobrar
Abre la caja en **Caja** con el fondo inicial y vuelve a cobrar.

### No encuentro un producto en el punto de venta
El catálogo solo se muestra al buscar. Escribe parte del nombre o el código, o elige una categoría. Si no aparece, revisa en **Inventario** si tiene existencia en tu tienda.

### El corte me salió con faltante o sobrante
El corte compara lo que contaste con lo que el sistema esperaba. Revisa que no falte registrar un gasto, un retiro o un abono en efectivo, vuelve a contar y escribe una observación. El corte ya guardado no se cambia: queda como evidencia.

### Me equivoqué en una venta
Si fue hace poco y no tiene devoluciones ni abonos, un encargado o administrador puede **cancelarla** en **Historial de ventas**. Si el cliente ya se llevó el producto y quiere regresarlo, usa **Devoluciones**.

### Capturé mal un IMEI
Avisa a un administrador: un IMEI registrado no se edita desde el punto de venta. Con un inventario físico o un ajuste se corrige.

### Olvidé mi contraseña
Un administrador la cambia en **Empleados**. Si te la sabes y quieres cambiarla, usa **Cambiar mi contraseña** (en JSystem, la llave de arriba a la derecha; en la web, **Ajustes**).

### Me aparece otra sucursal de la que debería
Si te cambiaron de sucursal hace poco, **cierra sesión y vuelve a entrar**. Un encargado siempre trabaja con la tienda de su usuario; un vendedor o un administrador, con la sucursal que tenga configurada la computadora (JSystem) o la que elija arriba (web).
{extra}
## Acerca de y soporte

Cuando pidas ayuda, explica **qué estabas haciendo** y **qué mensaje apareció**; una captura de pantalla ayuda mucho. Anota también tu usuario y la tienda desde donde trabajas.
"""
FAQ_JS = FAQ_COMUN.format(extra="""
### Una venta o un abono quedó «con error» en pendientes
El servidor lo rechazó al enviarlo (por ejemplo, un IMEI ya vendido). Abre **Guardado en este equipo**, lee el motivo y avisa al encargado.

### El ticket sale cortado o no imprime
En **Configuración › Ticket e impresora** elige la impresora de esta computadora y revisa el texto del ticket. Prueba con una venta de muestra. Si usas papel de **58 mm**, elige ese ancho: los tickets largos salen en varias hojas en lugar de cortarse.

### ¿Cómo sé si estoy en línea?
Si todo está bien, no aparece ningún indicador. Si se pierde la conexión o hay ventas pendientes, arriba aparece un aviso de color (rojo sin conexión, amarillo mientras se envían). Se envían solas al reconectar.
""")
FAQ_WEB = FAQ_COMUN.format(extra="""
### ¿Cómo sé si estoy en línea?
La barra de conexión de arriba lo indica. Si dice que hay pendientes, se envían solos al reconectar.
""")


def armar(*partes):
    texto = "\n".join(p.strip("\n") + "\n" for p in partes)
    return texto.replace("\r\n", "\n")


JS = armar(INTRO, PRIMEROS_JS, ROLES, POS_JS, CAJA_JS, DEVOLUCIONES, INVENTARIO_JS, EQUIPOS_JS, INVFIS_JS, MERMAS, TRASPASOS_JS,
           CLIENTES, GARANTIAS_JS, ORDENES_JS, SERV_JS, COMPRAS_JS, DESCUENTOS_JS, REPORTES_JS, FINANZAS_TALLER_JS, EMPLEADOS_JS, CONFIG_JS, AVISOS_JS,
           OFFLINE_JS, ATAJOS_JS, RUTINA, FAQ_JS)
WEB = armar(INTRO, PRIMEROS_WEB, ROLES, POS_WEB, CAJA_WEB, DEVOLUCIONES, INVENTARIO_WEB, EQUIPOS_WEB, INVFIS_WEB, MERMAS, TRASPASOS_WEB,
            CLIENTES, GARANTIAS_WEB, ORDENES_WEB, SERV_WEB, COMPRAS_WEB, DESCUENTOS_WEB, REPORTES_WEB, EMPLEADOS_WEB,
            OFFLINE_WEB, ATAJOS_WEB, RUTINA, FAQ_WEB)

for ruta, texto in ((RUTA_JS, JS), (RUTA_WEB, WEB)):
    with io.open(ruta, "w", encoding="utf-8", newline="\n") as f:
        f.write(texto)
    print(ruta, len(texto), "caracteres,", texto.count("\n"), "líneas")
