# Manual de usuario · Skycel

Este manual explica cómo usar el sistema en las tiendas, en el taller y en la oficina. Hay dos formas de entrar: **JSystem** (el programa de la computadora de la tienda) y la **versión web** (en el celular, la tableta o cualquier computadora, en `https://skycelsys.ddns.net/app`). Los dos usan la misma información. Cada tema del menú tiene su capítulo aquí con el mismo nombre; en cualquier pantalla pulsa **F1** para abrir la ayuda de lo que estás viendo.

## Primeros pasos

### Cómo funciona
Toda la información (ventas, inventario, clientes, caja) vive en un **servidor central** en la oficina. JSystem y la web se conectan a él, así que lo que se captura en una tienda lo ve al instante el resto, según el permiso de cada quien. Si una tienda se queda sin internet, el **Punto de Venta**, la **recepción de equipos**, el **corte de servicios** y los **abonos** siguen funcionando: se guardan en la computadora y se envían solos al volver la conexión (ver el tema «Trabajar sin conexión»).

### Entrar al sistema
1. Escribe tu **usuario** y tu **contraseña**.
2. Verás solo las secciones que tu rol permite usar.
3. Para cambiar de persona, cierra sesión (el botón de la parte de abajo del menú) antes de que alguien más use la computadora. Así cada venta queda a nombre de quien la hizo.

> No compartas tu contraseña. Si la olvidas, un administrador puede cambiarla en **Empleados**.

### Moverse por el programa
- El **menú de la izquierda** agrupa las secciones: Ventas, Inventario, Finanzas, Clientes, Taller y Administración.
- En JSystem, **Ctrl + 1 … 9** abre las secciones del menú en el orden en que aparecen (Inicio es la 1).
- **F1** abre esta ayuda en el tema de la pantalla donde estás.
- En el celular, la web también ofrece un acceso rápido abajo (Inicio, Vender, Inventario y Caja).
- El indicador de conexión te dice si estás en línea o si hay cosas guardadas esperando enviarse.

## Roles y permisos

Cada persona tiene un rol y el sistema solo le muestra lo que le corresponde.

| Puede… | Vendedor | Encargado de tienda | Administrador | Técnico |
|---|---|---|---|---|
| Vender y cobrar | Sí | Sí | Sí | No |
| Abrir y cerrar la caja de su tienda | Sí | Sí | Sí | No |
| Registrar entradas y salidas de efectivo | No | Sí | Sí | No |
| Pedir una devolución | Sí | Sí | Sí | No |
| Aprobar o rechazar devoluciones | No | Sí | Sí | No |
| Cancelar una venta | No | Sí | Sí | No |
| Recibir equipos para reparar, cobrar anticipos y entregar | Sí | Sí | Sí | No |
| Trabajar en el taller (reparar, avisar que está lista) | No | No | Sí | Sí |
| Garantías | Sí | Sí | Sí | No |
| Clientes: ver | Sí | Sí | Sí | No |
| Clientes: crear y editar | No | Sí | Sí | No |
| Cuentas por cobrar, compras y cuentas por pagar | No | Sí | Sí | No |
| Traspasos, inventario físico y reportes | No | Sí | Sí | No |
| Ajustar las existencias de accesorios | No | Sí | Sí | No |
| Registrar y editar productos y precios | No | No | Sí | No |
| Descuentos, nómina y comisiones | No | No | Sí | No |
| Configuración de la computadora (servidor, impresora, ticket) | No | Sí | Sí | No |
| Empleados | No | No | Sí | No |

> El usuario **root** puede cambiar qué puede hacer cada rol en **Permisos** (en la barra de la izquierda, sección Administración): marca o quita casillas y el cambio vale para la web y para JSystem. La tabla de arriba es lo que trae el sistema de fábrica.
>
> Un **encargado** solo ve la información de su propia tienda. Un **administrador** puede cambiar de sucursal o ver todas a la vez.
> Un **técnico** solo ve las órdenes de servicio del taller.

## Punto de Venta

Aquí se cobra. En la computadora, a la izquierda está el **catálogo** y a la derecha el **carrito**, el **cliente** y el **pago**; en el celular van una debajo de la otra.

### Antes de empezar
La caja de tu tienda debe estar **abierta** (sección Caja). Si está cerrada, al entrar al punto de venta aparece el formulario para abrirla con su fondo inicial. Sin caja abierta no se puede cobrar. Quien puede abrir y cerrar la caja (todos los vendedores y encargados) también ve el botón **Cerrar caja** arriba a la derecha.

### Buscar y agregar productos
El catálogo **no se muestra completo**: aparece cuando escribes algo en el buscador o eliges una categoría (Equipos, Accesorios...). Escribe parte del nombre, el código o el IMEI, y toca el producto para agregarlo. Los accesorios y servicios se agregan con un toque; si tocas el mismo otra vez, se suma la cantidad (sin pasar de las piezas que hay).

### Equipos (celulares y tablets)
Cada equipo se vende por su **IMEI o número de serie**. En el catálogo cada modelo muestra sus unidades disponibles: toca la que se lleva el cliente (cada una dice su IMEI, su condición —nuevo, seminuevo o reacondicionado— y su precio).
1. El equipo entra al carrito y se pregunta solo si es **al contado** o **a crédito (PayJoy)**. Si es al contado se cobra con la **forma de pago del carrito**, igual que lo demás que lleve el cliente.
2. Con PayJoy captura el **enganche**, el **plazo** (**3, 6, 9 o 12 meses**) y con qué se pagó el enganche: por omisión es **igual que el resto de la venta** (si eliges Transferencia, el enganche va por transferencia y no pide efectivo); si el enganche se paga distinto, elígelo ahí mismo. El sistema no calcula saldo ni pago semanal: PayJoy agrega su interés y esas cifras salen de su plataforma.
3. Un equipo no lleva cantidad: cada uno es un renglón con su IMEI.

### Promoción al vender un equipo
Si hay una promoción activa (la configura un administrador en Descuentos), al agregar junto con un equipo un artículo de la promoción (por ejemplo un **chip**, una **mica** o un **protector**), el renglón muestra **Promoción de equipo** y su precio ya rebajado; no capturas nada. Si el cliente lleva más piezas de las que cubre la promoción, solo las primeras la reciben.

### Pedir un descuento a un administrador
Si un cliente pide rebaja, en el renglón toca **Pedir descuento**, elige cómo lo pides —el **monto a descontar** o el **precio con el descuento** (por ejemplo, «que quede en $450»)—, escribe la cifra **por pieza** y el **motivo**, y envía la solicitud. El sistema te muestra cuánto se descuenta y en cuánto queda. Un administrador la **aprueba** (con ese monto u otro) o la **rechaza**. Mientras tanto el renglón dice «Esperando respuesta del administrador»; cuando la resuelve verás **Descuento aprobado** (el importe ya baja) o **Descuento rechazado** con su motivo. La aprobación sirve **una sola vez**, para ese artículo, en esa sucursal y por unas horas; si cambias la cantidad o quitas el renglón, se cancela. Pedir descuentos requiere conexión.

### Cliente
El cliente es **opcional**: si no marcas **Registrar cliente**, la venta queda de **mostrador**. Si la marcas, escribe el **teléfono (10 dígitos)** y pulsa **Buscar**: si ya existe aparece su nombre; si no, escribe su nombre y pulsa **Registrar** (quien puede crear clientes).

### Cobrar
1. Elige la **forma de pago**: **Efectivo**, **Transferencia** o **Mixto**. La **tarjeta** no se acepta por ahora: solo aparece si un administrador la habilita en **Ajustes › Formas de pago**.
   - **Mixto**: el cliente paga una parte de cada forma. Escribe cuánto va en **efectivo**, **transferencia** (y **tarjeta**, si se acepta); el sistema te dice cuánto falta o cuánto se pasa, y solo deja cobrar cuando **cuadra** con el total y usas **al menos dos formas**. En el cajón entra únicamente la parte en efectivo.
2. En efectivo escribe el **monto recibido**; el sistema calcula el **cambio**. Si el carrito lleva un equipo a crédito, lo que se cobra hoy de ese equipo es solo su enganche.
3. Pulsa **Cobrar**.

Si el carrito mezcla formas de cobro (por ejemplo accesorios en efectivo y un equipo a crédito), el sistema lo parte en una venta por cada una, todas juntas, y la promoción cuenta el carrito completo. Los precios ya **incluyen IVA** y los **descuentos** configurados por el administrador se aplican solos.

### Mensajes comunes
- **«La caja está cerrada»**: abre la caja con su fondo inicial y vuelve a cobrar.
- **«Stock insuficiente»**: no hay más piezas de ese producto en la tienda.
- **«El IMEI no está disponible»**: el equipo ya se vendió, está en otra tienda, en traspaso o en garantía, o está mal escrito. Búscalo en Equipos.
- **«El precio de … es $X»**: los precios los define Inventario; el punto de venta no los cambia.

> En la **web** el punto de venta necesita conexión: si no hay internet, usa JSystem en la tienda.

## Caja

La caja es el **turno de trabajo**: se **abre** al empezar el día con un fondo, se vende, y se **cierra** al terminar contando el efectivo. Al cerrar se genera un **corte** que queda guardado con todo su detalle.

### Abrir la caja
1. Entra a **Caja** y pulsa **Abrir caja** (el punto de venta también lo ofrece si está cerrada).
2. Cuenta el efectivo que hay en el cajón y captúralo como **fondo inicial**. El sistema sugiere el fondo que se dejó al cerrar el turno anterior.
3. Si lo que contaste no coincide con lo que el sistema esperaba, la diferencia se registra como **ajuste de apertura**.

Cualquier empleado de la tienda puede abrir o cerrar la caja. Solo puede haber un turno abierto por caja.

### Durante el turno
- La pantalla muestra el **efectivo esperado**, las ventas del turno por forma de pago, las entradas y las salidas.
- **Nuevo movimiento** (encargado y administrador) registra dinero que entra o sale del cajón fuera de una venta: un gasto, un pago, un retiro. Elige siempre el **motivo** y escribe una nota clara; los reportes agrupan los gastos por motivo.
- Las devoluciones en efectivo, los abonos de clientes y los anticipos de órdenes de servicio también pasan por la caja. Para registrarlos la caja debe estar abierta.

### Cómo se calcula el efectivo esperado
Fondo inicial + ventas cobradas en efectivo + abonos y anticipos en efectivo + entradas − salidas (incluidas las devoluciones en efectivo).

> Las ventas con tarjeta o transferencia, y la parte de PayJoy que no es enganche en efectivo, **no** meten dinero al cajón.

### Caja de la bodega
La **bodega** también lleva su propia caja: ahí llega el **efectivo de las ventas de las tiendas**.
1. Un administrador entra a **Caja**, elige la **bodega** en el selector de sucursal y pulsa **Nueva caja** (la primera queda como principal).
2. Cada día se **abre** con su fondo, como cualquier caja.
3. Cuando una tienda entrega su efectivo, la tienda registra una **salida** con el motivo **Traslado a bodega** y la bodega registra una **entrada** con el motivo **Efectivo de sucursal**; en la nota escribe de qué tienda y de qué día es.
4. Al final del día se **cierra** con su corte, y el saldo de la bodega queda cuadrado con lo que entregó cada tienda.

### Cerrar la caja (corte)
1. Cuenta el efectivo del cajón (billetes y monedas) y pulsa **Cerrar caja (corte)**.
2. Captura el **efectivo contado**. El sistema **no te muestra cuánto debería haber** antes de que lo captures, para que el conteo sea honesto.
3. Escribe cuánto **se deja en el cajón para mañana** (por omisión, el fondo con que abriste). El resto se registra como retiro del cierre.
4. Agrega observaciones si hace falta y confirma.
5. Se abre el **corte**: ahí verás el efectivo esperado, lo contado y si **cuadra**, hay **faltante** o hay **sobrante**.

### Cortes anteriores
**Cortes** muestra el historial. Abre cualquiera para ver su detalle (ventas por forma de pago, movimientos por motivo, quién abrió y quién cerró) e **imprimirlo**. Si después de cerrar llegan ventas hechas sin conexión con fecha de ese turno, el corte avisa que fue **modificado después del cierre**; lo contado se conserva como se capturó.

**Resumen del período** (JSystem) saca el total de un rango de fechas y se puede exportar.

## Devoluciones

Sirven para regresar un producto vendido: se le **reembolsa** al cliente o se le da **otro producto a cambio**. Hay un plazo para devolver (por omisión 15 días; el administrador lo ajusta en Configuración).

### Pedir una devolución
1. Entra a **Devoluciones** y pulsa **Nueva devolución**.
2. **Busca la venta** (por folio) y elige los artículos que regresan y su cantidad. Para los equipos se elige cada IMEI.
3. Escribe el **motivo**.
4. Marca si el artículo está en **buen estado** (regresa a inventario) o **dañado** (se da de baja).
5. Elige cómo se resuelve:
   - **Reembolso**: se devuelve el dinero. Si es en efectivo, sale de la caja (debe estar abierta).
   - **Cambio**: el cliente se lleva otro producto. Busca lo que se lleva (en un equipo, toca su IMEI) y agrega las piezas. Lo nuevo debe valer **igual o más** que lo devuelto; si vale más, elige **cómo paga la diferencia**. Al aprobarse se genera la venta del cambio.
6. Pulsa **Solicitar devolución**.

### Aprobar o rechazar
Un **encargado** o **administrador** revisa la solicitud: **Aprobar y ejecutar** mueve el inventario y el dinero, o **Rechazar** (con el motivo del rechazo). Hasta que se aprueba no cambia nada.

> Una venta con una devolución pedida o procesada ya no se puede cancelar.

## Inventario

Muestra los productos de **tu tienda**, su existencia, su precio y el mínimo que conviene tener.

### Qué muestra
Cada producto con su nombre, código, existencia y precios. Los productos con **poca existencia** aparecen marcados; el menú muestra cuántos son. Al buscar o filtrar, el **conteo** se actualiza (por ejemplo «Mostrando 12 de 480 productos»). Con **En todas las tiendas** buscas el artículo en las demás sucursales —nombre, código o IMEI— y ves dónde hay existencia; con **Pedir traspaso** le mandas la solicitud a esa tienda. **Valorización** (JSystem) da el valor del inventario a costo y a precio de venta, y **Exportar** lo saca a Excel.

### Registrar un producto nuevo (administrador)
1. Pulsa **Nuevo producto**.
2. Elige la **categoría principal** y, en cascada, sus subcategorías. Escribe el nombre del producto; abajo verás la **vista previa del nombre** completo con que se registrará.
3. Captura precios y existencia. Los **equipos** (celulares y tablets) no llevan código: se registran con su **IMEI** (uno por línea) y se ven en **Equipos**.

### Cambiar precios y existencia
- Solo un **administrador** edita los datos y los precios de un producto.
- Un **encargado** o administrador puede ajustar la existencia de un **accesorio**. Los equipos no se ajustan por cantidad: cada unidad tiene su IMEI, y su existencia cambia por compra, venta, traspaso o inventario físico.
- Un encargado o administrador puede marcar como **rezagado** un producto que no se mueve.
- La existencia de un producto sube cuando **ingresas mercancía** (entrada de existencia o alta de equipos) y cambia con cada venta, devolución y traspaso.

### Movimientos
**Movimientos** muestra el historial de cada cambio de existencia: ventas, devoluciones, traspasos, ajustes, con la fecha y quién lo hizo.

## Equipos

Los **celulares y las tablets** no tienen un código por modelo: cada equipo se identifica por su **IMEI** (un celular) o por su **IMEI o número de serie** (una tablet), y apunta al artículo al que pertenece. La existencia de un artículo son los equipos **disponibles** que hay de él.

### Qué muestra
Cada equipo con su IMEI o serie, el artículo, su condición (**nuevo**, **seminuevo** o **reacondicionado**), su precio (con el descuento vigente, si lo hay), su costo (si tu rol lo puede ver), los días que lleva en existencia, su **sección** y su estado. **Por artículo** resume cuántos hay de cada modelo. Un administrador ve y elige la sucursal; los demás, la suya.

### Registrar equipos
1. Pulsa **Registrar equipos** y elige el **artículo** (puedes escribir para buscarlo). Si es un modelo o un color nuevo, se registra en Inventario › Nuevo producto, con la categoría Equipos.
2. Escribe los IMEI, **uno por línea**. Para un seminuevo con su propio costo y precio: `350000000000029; seminuevo; 800; 1500`.
3. Captura el costo y el precio de venta de los equipos y pulsa **Registrar**.

### Copiar, corregir y dar de baja
- Solo quien puede editar artículos (un **administrador**) corrige un equipo: el **IMEI** o la serie, el **artículo**, el **precio**, el **costo**, la **condición**, el **proveedor** y la **sección** (el IMEI y el artículo mientras no haya sido vendido, traspasado ni mandado a garantía).
- **Rezagado** marca un equipo que no se mueve; **dar de baja** lo saca de la existencia (robo, daño o error de captura) y pide el motivo.
- **Por artículo** permite poner **el mismo precio** a todas las unidades disponibles y un **mínimo de existencias** que avisa cuando quedan pocas.

### Vender, traspasar y devolver
En el **Punto de Venta** busca el equipo por su nombre o su IMEI y toca la unidad. En **Traspasos**, los equipos viajan por IMEI. Una **devolución** de un equipo en buen estado lo regresa a la existencia.

## Inventario físico

Es el conteo de lo que hay en el local para compararlo con el sistema. Lo hacen **dos personas**: el **encargado cuenta** y el **administrador confirma**. Contar **no cambia las existencias**: estas solo cambian cuando el administrador cierra el inventario. Está en **Inventario físico** (menú de la izquierda).

### El encargado cuenta
1. **Aperturar inventario**: abre el conteo de la sucursal y pregunta **qué se va a inventariar**: **todo lo que se cuente** (como siempre, se revisan las secciones contadas) o **solo ciertas categorías** —por ejemplo solo **equipos**, solo **micas**, **cargadores** o **protectores**—. Al marcar una categoría se marcan sus subcategorías (y al desmarcarla, también se desmarcan). Los **servicios** no aparecen: no tienen existencias que contar. Solo puede haber un inventario en curso a la vez.
2. **Escanea** o escribe el código de cada producto (cada lectura suma una pieza; puedes poner otra cantidad) y el **IMEI** de cada equipo, uno por uno. El lápiz corrige una cantidad y, en un equipo, la lista de IMEI permite quitar el que escaneaste por error.
3. **Enviar a revisión** cuando termines. Se genera el **resumen**: lo contado contra lo que dice el sistema. Si elegiste categorías, lo que hay en el sistema dentro de ellas y no escaneaste queda como **faltante** (y escanear algo de otra categoría se rechaza); si no, lo que no escaneaste en las secciones que contaste. Se avisa a los administradores. La lista de inventarios muestra qué cubrió cada uno.

> Cuenta por zonas cerradas y evita vender o mover mercancía de la sección mientras tanto: el resultado es más confiable.

### El administrador confirma
1. Abre el inventario (arriba aparece qué sucursales tienen uno **por revisar**) y revisa el resumen: diferencias, valor al costo y, en equipos, qué IMEI no se encontraron.
2. Para cada diferencia elige:
   - **Ajustar**: las existencias se corregirán al cerrar.
   - **Justificar**: no se ajusta; queda escrito el motivo.
   Un equipo que **sobra** no se da de alta solo (no se sabe su costo ni su precio): se justifica o se registra en **Equipos**. **Ajustar todo lo pendiente** decide de una vez las que falten.
3. Si algo no cuadra, **Devolver para recontar**: el conteo regresa al encargado y las decisiones se borran.
4. **Confirmar y cerrar inventario**: se aplican los ajustes (a un accesorio se le suma o resta la diferencia; los equipos que no se encontraron se dan de baja) y las existencias cambian. Queda en el **historial**.

Un inventario sin terminar se puede **cancelar**: no cambia nada. El resumen se puede **exportar** a Excel (CSV).

## Traspasos

Mueven mercancía entre sucursales (y la bodega). Quienes los manejan son encargados y administradores.

### Enviar mercancía
1. Entra a **Traspasos** y pulsa **Nuevo envío**.
2. Elige la tienda destino y agrega los productos (la cantidad, o los **IMEI** si son equipos: eliges las unidades que viajan). En una **solicitud** de equipos pides el artículo y la cantidad; quien surte elige los IMEI al aceptar.
3. Al confirmar, la mercancía **sale del inventario** de tu tienda y queda «en tránsito».
4. Si te equivocas antes de que la reciban, puedes **Anular** el envío.

### Pedir mercancía
Con **Nueva solicitud** le pides a otra tienda que te surta algo. La otra tienda la **lee** y la **acepta** (**Aceptar y enviar**, que genera el envío) o la **rechaza**.

### Recibir mercancía
1. Abre el traspaso que llegó y pulsa **Recibir**.
2. Compara lo que llegó con lo que dice el traspaso. Si todo llegó, **Confirmar recepción**.
3. Si falta algo, captura lo que sí llegó: lo que faltó queda como **faltante**.

### Faltantes
**Faltantes por resolver** lista lo que no llegó. Cada faltante se resuelve de una de tres formas:
- **Recibido tarde**: apareció y se da de alta en la tienda.
- **Reintegrado al origen**: regresó a la tienda que lo envió.
- **Baja**: se da por perdido.

## Clientes

Guarda el nombre, teléfono y correo de quien compra, y su **historial de compras**.

- **Nuevo cliente** y **editar** los usan encargados y administradores; los vendedores solo consultan.
- Un cliente se **desactiva** (no se borra) para conservar su historial; solo un administrador puede hacerlo.
- Al vender, escribir el teléfono de un cliente existente trae su registro.

## Cuentas por Cobrar

Control de lo que los clientes deben a la tienda y de sus pagos.

### Registrar una cuenta
**Nueva cuenta** pide el cliente, el monto y el **número de factura** (hasta 20 caracteres).

### Cobrar un abono
1. Abre la cuenta con el icono del ojo y pulsa el botón de **abonar**.
2. Escribe el monto y la forma de pago, y **Confirmar pago**.
3. El abono se suma al historial de la cuenta. Si es en efectivo, entra a la caja del turno.

El **historial** (icono del reloj) muestra cada abono. Un abono hecho **sin conexión** se guarda en el equipo y se envía solo después.

## Garantías

Registra los reclamos de garantía de los productos vendidos y su viaje hasta el proveedor y de regreso.

### Registrar una garantía
1. Entra a **Garantías** y pulsa **Recibir garantía**.
2. Elige de dónde viene el producto: **Venta de este sistema** (escribe el número de venta y el IMEI o el código; **Revisar si todavía tiene garantía** te dice hasta cuándo cubre), **Venta anterior** o **Equipo de vitrina**.
3. Escribe la **falla reportada**, cómo se recibe y los datos de contacto, y pulsa **Recibir garantía**. Se genera un folio **GAR-…**.

> Si el producto se vendió con el **sistema anterior**, elige «Venta anterior» y captura la fecha de compra, el nombre del producto y los días de garantía (que aún deben estar vigentes) y, si los tienes, el IMEI y el ticket.

> Un **equipo de vitrina** (aún sin vender) al que le detectas una falla también va con el proveedor: elige «Equipo de vitrina» y escribe su IMEI. No hay venta ni cliente; mientras está fuera **no cuenta en la existencia** y vuelve a ella cuando la garantía se entrega. Si el proveedor lo cambia por otro, captura el IMEI del equipo nuevo en el paso «Cambio físico».

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

Una garantía **rechazada** puede regresar al cliente directamente (Listo para entrega) o con un viaje de retorno. El plazo para resolver una garantía es de **30 días hábiles** (lunes a viernes). La que se pasa de ese plazo aparece como vencida.

## Órdenes de Servicio

Cubre las reparaciones: se **recibe** el equipo en la tienda, se **repara** en el taller y se **entrega** al cliente.

### Recibir un equipo
1. Entra a **Órdenes de Servicio** (en la web: **Recibir equipo**) y pulsa **Nueva orden**.
2. Busca o captura al **cliente** (nombre y teléfono).
3. Captura marca, modelo, IMEI, **accesorios que deja**, **estado físico** y la **falla reportada**, y el **tiempo estimado**, en **horas o días** (por omisión, **3 horas**): el sistema dice «Se le promete para el…» con día y hora. El máximo es 30 días.
4. Si el cliente deja un **anticipo**, regístralo con su forma de pago. En efectivo entra a la caja del turno.
5. Pulsa **Recibir equipo**. Se genera el folio **OS-…** que se le entrega al cliente.

La recepción también funciona **sin conexión**.

### En el taller
Los técnicos trabajan en un solo taller y atienden las órdenes de **todas las tiendas**.
1. Un técnico **toma** una orden libre (recoge el equipo en la tienda) o el técnico encargado se la **asigna**.
2. **Iniciar reparación** cambia la orden a «En reparación».
3. Se agregan **renglones** (refacciones y servicios con su precio) y **observaciones** que quedan en el historial.
4. **Marcar lista** avisa que el equipo ya puede recogerse.
5. Si hace falta corregir algo, **Reabrir**.

### Entregar al cliente
1. En la tienda, abre la orden **Lista** y pulsa **Entregar y cobrar**.
2. El sistema calcula el total con los renglones, **descuenta el anticipo** y cobra la diferencia. Se genera la venta y su ticket.
3. Una orden puede **cancelarse** antes de entregarse; si tenía anticipo, se devuelve.

### Consultar una orden
**Consultar orden** (web) busca por folio o muestra las más recientes, para decirle al cliente en qué va su equipo.

## Corte de Servicios

Los **saldos (recargas), pagos de servicios, pines y pagos de PayJoy** se operan en los sistemas de los proveedores; no pasan por las ventas ni por la caja del sistema. Este corte sirve para saber **cuánto se vende** en cada servicio y **cuánto te queda de ganancia**, por día, por sucursal y mes con mes.

1. El **encargado** abre **Corte de servicios**, elige el **día** (y la sucursal, si es administrador) y captura, por servicio, cuántas **operaciones** hizo y el **importe** vendido. La **ganancia se calcula sola**.
2. **Guardar corte** lo deja capturado. Puedes corregirlo mientras no esté confirmado.
3. Un **administrador** lo compara con los reportes del proveedor, escribe el importe de cada uno y pulsa **Confirmar corte**. Si hay diferencias debe escribir un comentario.
4. Si hay que cambiar algo ya confirmado, un administrador usa **Reabrir para corregir**.

### Cómo se calcula la ganancia
- **Servicel** y **Tae Recargas**: un **porcentaje de lo vendido** (8.1 % y 7.6 %). Si vendes $10,000 en Servicel, ganas $810.
- **Pagos de servicios**: se le cobran **$15 al cliente** y el proveedor cobra **$4** por operación; quedan **$11** por cada pago.
- **Pagos PayJoy**: solo se cuentan los **clientes que pagaron** (cuántas personas pagan con nosotros). La comisión de PayJoy llega por separado y no se calcula aquí; si quieres, anota el importe recibido.

### Cuánto se vende y cuánto queda
Debajo del corte está el resumen del periodo (este mes, el mes pasado, los últimos 30 días o este año): **lo vendido y la ganancia** por servicio, por sucursal (administrador) y **mes con mes** de los últimos 6 meses. Lo de PayJoy aparece en su renglón, pero no suma a lo vendido.

En **Servicios** (administrador) se dan de alta los servicios y se define cómo se gana con cada uno: el **porcentaje de ganancia**, la **comisión que se cobra al cliente** por operación, **lo que cobra el proveedor** por operación, o si **solo se cuentan** las operaciones. Un cambio aplica a los cortes nuevos; los ya capturados conservan lo que valía al capturarlos. El corte también se puede capturar **sin conexión** en JSystem.

## Compras y Proveedores

### Proveedores
Directorio de los proveedores con los que se trabaja (**Nuevo proveedor**, editar). Los productos se registran con su proveedor.

### Compras y cuentas por pagar
Registra lo que se le **debe** a un proveedor por una compra.
1. **Registrar compra**: elige el proveedor, escribe el **monto total** y la sucursal; opcionalmente el folio de su factura y la fecha de vencimiento (si no la pones, se calcula con los días de crédito del proveedor). **No hace falta capturar los productos**: la mercancía se ingresa en **Inventario** o **Equipos**, donde queda registrado el proveedor, la fecha y la hora de cada entrada.
2. En el detalle de la compra puedes **registrar pagos** parciales o totales; el saldo que se le debe al proveedor baja con cada pago. Se paga en efectivo o transferencia (y con tarjeta solo si el negocio la acepta).
3. El **historial** de la compra muestra cada pago. Un pago de caja se registra con el motivo «Pago a proveedor».

Los encargados y administradores usan esta sección.

## Descuentos

El administrador define **reglas de descuento**. Cada regla puede limitarse a una **categoría** (una **principal**, que vale también para todas sus subcategorías, o solo una **subcategoría**), a un **tipo de producto**, a un **producto** en particular o a una **tienda**; pedir un **monto mínimo** de compra; descontar un **porcentaje** o una **cantidad fija**; y valer solo ciertos **días de la semana** o entre dos **fechas**. El sistema las **aplica solo al cobrar**; el vendedor no captura descuentos a mano. Una regla se puede **activar o desactivar** sin borrarla.

### Solicitudes de descuento (administrador)
Arriba de la pantalla de Descuentos aparecen las **solicitudes** que piden los vendedores y encargados desde el punto de venta: sucursal, quién la pidió, el artículo, su precio, cuánto descuento piden y el motivo. Con los botones puedes **aprobar** lo pedido, **aprobar con otro monto** o **rechazar** (con un motivo opcional). La lista se actualiza sola y también llega un aviso.

### Promoción al vender un equipo
Arriba de las reglas está la **promoción al vender un equipo** (celular o tablet). Por cada equipo que se lleva el cliente, ciertos artículos (por ejemplo un **chip**, una **mica** o un **protector**) reciben un descuento. En **Configurar** el administrador arma una **lista de reglas**; cada regla dice:

- a qué **categoría** de artículos aplica (vale también para sus subcategorías);
- un **rango de precio de lista** (por ejemplo, protectores de hasta $249), o sin rango;
- cuántas **piezas por cada equipo** reciben el descuento;
- el **descuento**: **de regalo** (gratis), un **porcentaje**, un **monto en pesos** o un **precio final fijo**;
- el **redondeo** del precio que queda: al peso o a múltiplos de $5 o $10, hacia arriba o hacia abajo.

Ejemplo: *protectores de hasta $249, 50 % de descuento, redondeado al peso hacia arriba* → un protector de $249 paga **$125** y uno de $199 paga **$100**. Mientras escribes la regla, el sistema te muestra cuánto pagaría un artículo del precio que quieras probar.

Las reglas se revisan **en orden** (con las flechas las subes o bajas): a cada pieza le toca la primera regla que le corresponde y todavía tiene cupo. Cada regla se puede **apagar** sin borrarla, y el interruptor **Promoción activa** apaga todas. Las tiendas con un JSystem anterior deben actualizarse antes de usar descuentos en porcentaje o con redondeo.

En el punto de venta se aplica sola: al agregar el artículo junto con un equipo, el renglón muestra **🎁 Promoción de equipo** y el precio ya rebajado; el vendedor no captura nada. Si el cliente lleva más piezas de las que cubre la promoción, solo las primeras la reciben. Si el carrito se parte en varias ventas por la forma de pago, la promoción cuenta el carrito completo: el equipo de una venta da derecho al descuento de la otra. También funciona sin conexión con la última configuración descargada.

## Reportes

Disponibles para encargados (su tienda) y administradores (todas o la que elijan).

| Reporte | Para qué sirve |
|---|---|
| Ventas | Lo vendido hoy, en 7 días o en el mes, con el detalle de cada venta |
| Resultados | Estado de resultados, ventas por día, ventas por categoría y artículos sin movimiento |
| Gerencial | Comparativo de ventas y utilidad entre sucursales, más vendidos y valor del inventario |
| Rezagados | Equipos que llevan mucho tiempo sin venderse |
| Caja (administrador) | El saldo y los movimientos de hoy de todas las cajas |

### Resultados
Elige la **sucursal** (si eres administrador) y el **periodo** (Hoy, Ayer, 7 días, Este mes, Mes pasado u otras fechas).
- **Estado de resultados**: ventas, menos devoluciones, menos el costo de lo vendido, igual a la **utilidad bruta**; menos los **gastos de caja** por motivo, igual a la **utilidad neta**. Se compara con el **periodo anterior** de la misma duración y muestra cuánto cambió.
- **Por día**: cuánto se vendió cada día, con gráfica.
- **Por categoría**: qué categorías venden más y cuánto dejan.
- **Sin movimiento**: accesorios con existencia que no se han vendido en los últimos días que elijas y el dinero que tienen parado a costo.
- Cada vista tiene **Descargar para Excel**.

> Los gastos son las **salidas de caja** del periodo, agrupadas por su motivo (renta, flete, nómina, etc.). Los retiros del dueño, los traslados, los pagos a proveedores (que ya van en el costo), los préstamos a empleados y los movimientos de apertura y cierre de caja **no** se cuentan como gasto. Es una referencia para tomar decisiones, no un documento contable: no incluye lo que no pase por la caja.

## Empleados, nómina y comisiones

Sección de administración (los descansos también los ve el encargado).

- **Empleados**: personal, sucursal y **rol de acceso**. Aquí se da de alta a quien va a usar el sistema, se le asigna su rol y se cambia su contraseña si la olvidó. Los técnicos se marcan con su rol, y uno de ellos puede ser el **técnico encargado**, que reparte las órdenes del taller.
- **Nómina**: períodos de pago con las **percepciones** y **deducciones** de cada empleado y su pago. **Historial de nómina** guarda los períodos anteriores. Los **faltantes de caja** se descuentan solos: al abrir el período y al pagar, el sistema agrega como deducción el faltante de cada corte cerrado en el período ("Faltante de caja del 10/10/2026 · Caja 1"), a quien cerró el turno (si lo cerró un administrador, a quien lo abrió). **Traer faltantes de caja** los actualiza en cualquier momento. Si quitas esa deducción, el faltante queda **perdonado** y no se vuelve a cargar. Los sobrantes no se compensan.
- **Comisiones**: comisiones por ventas del periodo y su pago.
- **Descansos**: descansos y vacaciones del personal, con su saldo.

## Trabajar sin conexión

Si se cae el internet o el servidor, estas funciones **siguen trabajando**:
- **Punto de Venta** (JSystem).
- **Recepción de equipos** para reparar.
- **Corte de Servicios**.
- **Abonos** a cuentas por cobrar.

Lo que captures se **guarda en la computadora** y se envía solo, en orden, cuando regresa la conexión. El sistema avisa en la barra de conexión y en **Guardado en este equipo** cuántas cosas están pendientes.

- Las ventas hechas sin conexión conservan su **fecha y hora reales**.
- Las existencias se descuentan al enviarse; si el servidor ve que ya no había piezas, la venta se acepta igual porque ya ocurrió, y el stock puede quedar en negativo para revisarse.
- Si una captura **no se acepta** (por ejemplo, un IMEI que ya se vendió), queda marcada con **error** en la lista de pendientes para que el encargado la revise.
- Para entrar sin conexión hay que haber iniciado sesión antes en esa computadora.

> El resto de las secciones necesitan conexión con el servidor. Si intentas entrar a una sin conexión, el sistema te avisa.

## Atajos de teclado

| Dónde | Tecla | Qué hace |
|---|---|---|
| JSystem, todo el programa | F1 | Abre esta ayuda en el tema de la pantalla actual |
| JSystem, todo el programa | Ctrl + 1 … 9 | Abre las secciones del menú en el orden en que aparecen |
| Punto de Venta | F2 | Lleva el cursor al buscador (o lector de códigos) |
| Punto de Venta | Enter | Agrega el producto cuyo código escribiste o que quedó solo en la lista |
| Punto de Venta | F4 | Lleva el cursor a los datos del cliente |
| Punto de Venta | F9 | Procesa la venta |
| Punto de Venta | + / − | Suma o resta una pieza a la línea marcada |
| Punto de Venta | Supr | Quita la línea marcada |
| Punto de Venta | Esc | Limpia el buscador |

Con texto escrito en un campo, **+**, **−** y **Supr** se respetan como texto.

## Rutina diaria sugerida

### Al abrir
1. Inicia sesión con tu usuario.
2. En **Caja**, abre la caja con el fondo contado.
3. Revisa que la barra de conexión esté en línea y que no haya pendientes por enviar.
4. Encargados: revisa en **Inventario** los productos con poca existencia.

### Durante el día
- Cobra en el **Punto de Venta**; usa **F2** y el lector de códigos.
- Registra los gastos y retiros como **movimientos de caja**, siempre con su motivo.
- Recibe equipos para reparar en **Órdenes de Servicio** y cobra los abonos en **Cuentas por Cobrar**.
- Cuando llegue mercancía, ingrésala en **Inventario** o **Equipos** (o recíbela con un **traspaso**) y registra lo que debes al proveedor en **Compras**.

### Al cerrar
1. Cuenta el efectivo del cajón y haz el **corte** en **Caja**.
2. Captura el **Corte de Servicios** del día (encargado).
3. Revisa que no queden pendientes por enviar.
4. Cierra tu sesión.

### De vez en cuando
- **Inventario físico** una vez al mes o por temporada.
- Revisar en **Reportes → Resultados** cómo va el negocio frente al periodo anterior y qué accesorios están parados.

## Preguntas frecuentes

### Me dice «La caja está cerrada» al cobrar
Abre la caja en **Caja** (o desde el aviso del punto de venta) con el fondo inicial. Las ventas hechas sin conexión no se rechazan por esto.

### No encuentro un producto en el punto de venta
El catálogo solo se muestra al buscar. Escribe parte del nombre o el código, o elige una categoría. Si no aparece, revisa en **Inventario** si tiene existencia en tu tienda.

### El corte me salió con faltante o sobrante
El corte compara lo que contaste contra lo que el sistema esperaba. Revisa que no falte registrar un gasto, un retiro o un abono en efectivo, vuelve a contar y escribe una observación. El corte ya guardado no se cambia: queda como evidencia.

### Me equivoqué en una venta
Si fue hace poco y no tiene devoluciones ni abonos, un encargado o administrador puede **cancelarla** en **Historial de ventas**. Si el cliente ya se llevó el producto y quiere regresarlo, usa **Devoluciones**.

### Capturé mal un IMEI
Avisa a un administrador: un IMEI registrado no se edita desde el punto de venta. Con inventario físico o un ajuste se corrige.

### Una venta o un abono quedó «con error» en pendientes
El servidor la rechazó al enviarla (por ejemplo, un IMEI ya vendido). Abre **Guardado en este equipo**, lee el motivo y avisa al encargado para resolverlo.

### Olvidé mi contraseña
Un administrador la cambia en **Empleados**.

### El ticket sale cortado o no imprime
En **Configuración** (administrador o encargado) elige la impresora de este equipo y revisa el texto del ticket. Prueba con una venta de muestra.

### ¿Cómo sé si estoy en línea?
La barra de conexión en la parte de arriba lo indica. Si dice que hay pendientes, se envían solos al reconectar.

## Acerca de y soporte

Cuando pidas ayuda, explica **qué estabas haciendo** y **qué mensaje apareció**; una captura de pantalla ayuda mucho. Anota también tu usuario y la tienda desde donde trabajas.
