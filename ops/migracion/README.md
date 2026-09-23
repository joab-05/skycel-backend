# Migración del inventario desde el sistema anterior (`skyceldb`)

Alcance acordado con el usuario (2026-09-23): se migra **solo la existencia real** (filas con `existencia > 0`, unas
9,150). Catálogos, tiendas y empleados se crean **limpios** en el sistema nuevo; el historial de ventas se queda en
`skyceldb` como archivo de consulta. Las tiendas cerradas (3 Deportivo, 4 Hidalgo, 5 Guerrero) no se migran.

Todo lo que contiene datos del negocio (exportación, plan, Excel de revisión) vive **fuera de Git**, en
`C:\Users\PC\Documents\Migracion Skycel` (o la carpeta de `MIG_DIR`).

## Reglas

* **Accesorios**: se conserva el código viejo como `codpro` (es la etiqueta pegada al producto; no se reetiqueta).
* **Equipos** (celular, tablet, módem): entran por IMEI o número de serie, una unidad cada uno; si el costo o el
  precio de una unidad difiere del modelo, queda como costo/precio propio de esa unidad.
* Cada tienda conserva su costo, precio y proveedor. El mismo artículo lleva el mismo código en todas las tiendas.
* El nombre del artículo se arma como `{categoría} {marca} {modelo} {estilo}` con las reglas del usuario (mayúscula
  inicial, `mAh`, `Gb`, `GLASS`→`Cristal`, etc.) y luego el usuario lo revisa en `nombres_articulos.xlsx`.
* El color y el proveedor salen del Excel de revisión (hojas Colores y Proveedores), ya corregidas por el usuario.
* Correcciones de costo: las que el usuario anotó en la hoja *Datos a revisar* ("Corregir costo a $X").

## Pasos

1. **Exportar** el inventario viejo: `exportar_inventario.sql` en MySQL Workbench del servidor → guardar como
   `inventario_existente.tsv` (UTF-8, sin encabezado). Repetir esta exportación **el día de la carga** (el sistema
   viejo sigue vendiendo y la existencia cambia a diario).
2. `python preparar_plan.py` → genera `nombres_articulos.xlsx` y `plan_migracion.json`.
3. El usuario revisa la hoja **Nombres** (columna B amarilla). Un nombre corregido reemplaza al propuesto (la columna H
   guarda el original para saber a qué artículo corresponde).
4. **Preparar catálogos** (crea tiendas, categorías, colores y proveedores que falten; idempotente):
   `python cargar_inventario.py --url URL --usuario root --plan plan_migracion.json --nombres nombres_articulos.xlsx --solo-catalogos`
5. **Cargar**: mismo comando sin `--solo-catalogos` (puede limitarse con `--tienda N` y `--limite N`). Se auto-regula
   a ~90 peticiones/min (límite del servidor: 100/min por IP) y guarda su avance en `progreso_carga.json`: si se
   interrumpe, se vuelve a correr el mismo comando y continúa. Los rechazos quedan en `errores_carga.jsonl`.
   Tarda ~1.7 min por cada 150 filas (≈ 95 min para todo).
6. **Verificar**: `python verificar_carga.py --url URL --usuario root --plan plan_migracion.json --nombres nombres_articulos.xlsx`
   compara por tienda unidades, códigos, IMEI, costo, precio y nombre; sale con código 0 solo sin diferencias.

La contraseña nunca va como argumento: `--credenciales archivo` (2 líneas: usuario y contraseña, con o sin etiqueta),
variable `SKYCEL_PASSWORD` con `--password-file`, o se pide al ejecutar.

`preparar_plan.py` NO sobrescribe `nombres_articulos.xlsx` si ya existe (tiene las correcciones del usuario); para
regenerarla a propósito: `--forzar-hoja`.

## Antes de cargar en el servidor real

* La base debe estar **sin datos de prueba** (productos, traspasos, movimientos, clientes de las pruebas).
* Debe estar desplegado el jar con: código único por artículo entre sucursales (`4ceab27`), venta que elige el código
  con stock (`e2836fc`), alta de proveedor sin razón social, e índices/restricción única (`b0366ed`).
* Correr antes `ops/deploy/migracion-codigo-unico-articulo.sql` si quedan productos de prueba con códigos distintos.
* No se migra la fecha de ingreso original (el sistema nuevo pone la fecha de la carga) ni el indicador *rezagado*.
