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

> Un **encargado** solo ve la información de su propia tienda. Un **administrador** puede cambiar de sucursal o ver todas a la vez.
> Un **técnico** solo ve las órdenes de servicio del taller.

## Punto de Venta

Aquí se cobra. A la izquierda está el **catálogo**, en el centro el **carrito** y a la derecha los datos del **cliente** y el **pago**.

### Antes de empezar
La caja de tu tienda debe estar **abierta** (sección Caja). Si está cerrada, al entrar al punto de venta el sistema te pide abrirla con su fondo inicial. Sin caja abierta no se puede cobrar.

> Las ventas hechas **sin conexión** se aceptan siempre y entran al turno que esté abierto cuando se envían.

### Buscar y agregar productos
El catálogo **no se muestra completo**: aparece cuando escribes algo en el buscador o eliges una categoría (Equipos, Accesorios, Servicios).
1. Pulsa **F2** (o haz clic en el buscador) y escribe parte del nombre o el código del artículo.
2. Pulsa **Enter**: si el texto es el código exacto de un producto, o queda un solo resultado, se agrega al carrito y el buscador queda listo para el siguiente. Así funciona el lector de códigos de barras.
3. También puedes hacer clic sobre el producto de la lista.

### Equipos (celulares y tablets)
Cada equipo se vende por su **IMEI o número de serie**.
1. Al agregar un equipo se pide el IMEI. El sistema revisa que esté disponible en la tienda.
2. Después eliges la **forma de pago de ese equipo**: Efectivo, Tarjeta, Transferencia o **PayJoy**.
3. Con PayJoy se captura el **enganche**, el **plazo** (6, 12, 24, 36 o 52 semanas) y cómo se cobró el enganche (efectivo o transferencia). El sistema muestra el pago semanal.
4. El equipo entra al carrito con su IMEI. Cada equipo se agrega por separado.

### Accesorios y servicios
Entran directo al carrito. Si agregas el mismo otra vez, se suma la cantidad. El sistema no deja pasar de las piezas que hay en existencia.

### Corregir el carrito
- **+** y **−** cambian la cantidad de la línea marcada (la última que agregaste queda marcada sola). Con **−** en la última pieza, la línea se quita.
- **Supr** quita la línea marcada. También puedes usar el icono de bote de basura de la línea.
- **Limpiar carrito** borra todo.
- Mientras escribes en un campo, **+**, **−** y **Supr** se respetan como texto (por ejemplo, en un código con guion).
- Un equipo no se suma con **+**: cada uno lleva su propio IMEI. Con **−** se quita.

### Cliente
Captura el **nombre** y el **teléfono (10 dígitos)** del cliente; el correo es opcional. Pulsa **F4** para ir a esos campos. Si el cliente ya existe, se usa su registro.

### Cobrar
1. Elige la **forma de pago** del carrito: Efectivo, Tarjeta, Transferencia o Mixto.
2. En efectivo escribe el **monto recibido**; el sistema calcula el **cambio**.
3. Pulsa **Procesar venta** (o **F9**).
4. Se muestra el ticket, que puedes imprimir.

Los precios ya **incluyen IVA**. Los **descuentos** configurados por el administrador se aplican solos al cobrar; nadie los captura a mano.

### Historial de ventas
El botón **Historial de ventas** lista las ventas, permite **reimprimir** el ticket y, para encargados y administradores, **cancelar una venta**: se regresa el stock y se liberan los IMEI. No se puede cancelar una venta que:
- salió de una orden de servicio,
- ya tiene una devolución pedida o procesada,
- tiene una cuenta por cobrar con abonos.

En esos casos se usa **Devoluciones**.

### Mensajes comunes
- **«La caja está cerrada»**: abre la caja con su fondo inicial y vuelve a cobrar.
- **«Sin stock»** o **«Stock limitado»**: no hay más piezas de ese producto en la tienda.
- **«El IMEI no está disponible»**: el equipo ya se vendió, está en otra tienda o en traspaso, o está mal escrito. Búscalo en Inventario.

> En la **web** el punto de venta es más sencillo: cobra en efectivo, tarjeta o transferencia, el cliente es opcional y no maneja PayJoy, pagos mixtos ni cambios de producto. Para eso usa JSystem en la tienda.

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
   - **Cambio**: el cliente se lleva otro producto. Lo nuevo debe valer **igual o más** que lo devuelto; si vale más, se cobra la diferencia. El cambio se hace en JSystem.
6. Pulsa **Solicitar devolución**.

### Aprobar o rechazar
Un **encargado** o **administrador** revisa la solicitud: **Aprobar y ejecutar** mueve el inventario y el dinero, o **Rechazar** (con el motivo del rechazo). Hasta que se aprueba no cambia nada.

> Una venta con una devolución pedida o procesada ya no se puede cancelar.

> En la web solo se piden **reembolsos**; el cambio por otro producto se hace en JSystem.

## Inventario

Muestra los productos de **tu tienda**, su existencia, su precio y el mínimo que conviene tener.

### Qué muestra
Cada producto con su nombre, código, existencia y precios. Los productos con **poca existencia** aparecen marcados; el menú muestra cuántos son. **Valorización** (JSystem) da el valor del inventario a costo y a precio de venta, y **Exportar** lo saca a Excel.

### Registrar un producto nuevo (administrador)
1. Pulsa **Nuevo producto**.
2. Elige la **categoría principal** y, en cascada, sus subcategorías. Escribe el nombre del producto; abajo verás la **vista previa del nombre** completo con que se registrará.
3. Captura precios y existencia. Los equipos se registran con su **IMEI** (uno por unidad).

### Cambiar precios y existencia
- Solo un **administrador** edita los datos y los precios de un producto.
- Un **encargado** o administrador puede ajustar la existencia de un **accesorio**. Los equipos no se ajustan por cantidad: cada unidad tiene su IMEI, y su existencia cambia por compra, venta, traspaso o inventario físico.
- Un encargado o administrador puede marcar como **rezagado** un producto que no se mueve.
- La existencia de un producto también sube al **registrar una compra** y cambia con cada venta, devolución y traspaso.

### Movimientos
**Movimientos** muestra el historial de cada cambio de existencia: ventas, devoluciones, traspasos, ajustes, con la fecha y quién lo hizo.

## Inventario físico

Es el conteo de lo que hay en el local para compararlo con el sistema. Está en la **web** y la usan encargados y administradores. Se hace por **secciones** y **no cambia las existencias hasta que tú lo decides**.

1. **Aperturar inventario**: abre la auditoría de la tienda. Solo puede haber una abierta a la vez.
2. **Inventariar**: escanea o captura cada artículo de la sección que estás contando. Cada lectura suma una pieza; los equipos se cuentan por IMEI.
3. **Cerrar el conteo** cuando terminaste de contar.
4. **Catalogar diferencias**: para cada artículo que no coincide, elige:
   - **Ajustar**: la existencia del sistema cambia a lo contado.
   - **Justificar**: la dejas como está, con un motivo escrito.
5. **Finalizar** la auditoría. Queda en el **historial**.

> Antes de contar, evita vender o mover mercancía de la sección. Contar por zonas cerradas da resultados más confiables.

## Traspasos

Mueven mercancía entre sucursales (y la bodega). Quienes los manejan son encargados y administradores.

### Enviar mercancía
1. Entra a **Traspasos** y pulsa **Nuevo envío**.
2. Elige la tienda destino y agrega los productos (la cantidad, o los IMEI si son equipos).
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
1. Entra a **Garantías** y pulsa **Nueva garantía**.
2. **Busca la venta** (por folio o por IMEI). El sistema revisa si el producto **sigue en garantía** según los días que cubre ese artículo.
3. Captura el motivo del reclamo y los datos de contacto, y pulsa **Recibir producto**. Se genera un folio **GAR-…**.

> Si el producto se vendió con el **sistema anterior**, marca «Se vendió con el sistema anterior» y captura la fecha de compra, el nombre del producto, los días de garantía (que aún deben estar vigentes) y, si los tienes, el IMEI, el folio y el proveedor.

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
3. Captura marca, modelo, IMEI, **accesorios que deja**, **estado físico** y la **falla reportada**.
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

Las **recargas, pagos de servicios, pines y pagos de PayJoy** se operan en los sistemas de los proveedores; no pasan por las ventas ni por la caja del sistema. Este corte los resume cada día para cuadrarlos contra los reportes de los proveedores.

1. El **encargado** abre **Corte del día** y captura, por servicio, cuántas operaciones hizo y el importe. La comisión se calcula sola. Los saldos son opcionales.
2. **Guardar corte** lo deja capturado. Puedes corregirlo mientras no esté confirmado.
3. Un **administrador** lo compara con los reportes del proveedor y pulsa **Confirmar con los reportes**. Si hay diferencias debe escribir un comentario.
4. Si hay que cambiar algo ya confirmado, un administrador usa **Reabrir para corregir**.

**Historial y resumen** muestra los cortes anteriores. **Servicios y comisiones** (administrador) permite dar de alta servicios y fijar su comisión.

El corte también se puede capturar **sin conexión**.

## Compras y Proveedores

### Proveedores
Directorio de los proveedores con los que se trabaja (**Nuevo proveedor**, editar). Los productos se registran con su proveedor.

### Compras y cuentas por pagar
Registra la **mercancía que llega** de un proveedor y lo que se le **debe**.
1. **Registrar compra**: elige el proveedor y agrega lo que llegó con su cantidad y costo. Al guardarla, la **existencia de esos productos sube**.
2. En el detalle de la compra puedes **registrar pagos** parciales o totales; el saldo que se le debe al proveedor baja con cada pago.
3. El **historial** de la compra muestra cada pago. Un pago de caja se registra con el motivo «Pago a proveedor».

Los encargados y administradores usan esta sección.

## Descuentos

El administrador define **reglas de descuento**. Cada regla puede limitarse a un **tipo de producto**, a un **producto** en particular o a una **tienda**; pedir un **monto mínimo** de compra; descontar un **porcentaje** o una **cantidad fija**; y valer solo ciertos **días de la semana** o entre dos **fechas**. El sistema las **aplica solo al cobrar**; el vendedor no captura descuentos a mano. Una regla se puede **activar o desactivar** sin borrarla.

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
- **Nómina**: períodos de pago con las **percepciones** y **deducciones** de cada empleado y su pago. **Historial de nómina** guarda los períodos anteriores.
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
- Cuando llegue mercancía, regístrala en **Compras** o recíbela con un **traspaso**.

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
