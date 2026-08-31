# Torven Sistema de Ventas

Esta es la version fuente reconstruida y mejorada del proyecto. Incluye productos, clientes, reservas/ventas con control de stock, voucher imprimible/exportable y configuracion de base de datos.

## Abrir en NetBeans

1. Abre esta carpeta como proyecto Maven.
2. Revisa `config/database.properties`. La edicion para clientes usa la conexion cifrada a Railway y no requiere MySQL local.
3. Ejecuta el proyecto. Las migraciones en produccion permanecen desactivadas y se realizan solo durante la preparacion administrativa.

Si lo ejecutas desde un `.jar`, deja la carpeta `config` junto al proyecto o junto al ejecutable. Tambien puedes usar variables del sistema: `DB_NAME`, `DB_USER` y `DB_PASSWORD`.

## Base de datos

Tambien puedes crearla manualmente desde MySQL Workbench, phpMyAdmin o la consola de MySQL con `database/tienda.sql`.

## Accesos iniciales

- Administrador: usuario `admin`, clave `admin123`.
- Vendedor: usuario `vendedor`, clave `venta123`.

El administrador ve Dashboard, Ventas, Productos y Clientes. El vendedor solo ve Ventas.
En el primer ingreso de cada usuario, el sistema pedira cambiar el usuario y la clave; luego debera ingresar nuevamente con esos nuevos datos.

## Ejecutar

Con Maven:

```bash
mvn clean package
java -jar target/sistema-venta-tienda-1.0.2.jar
```

NetBeans descargara automaticamente MySQL Connector/J si la PC tiene Internet. Si no, agrega el jar de MySQL Connector/J al proyecto.

## Crear el instalador de Windows

El proyecto incluye `crear-instalador.ps1`, que compila el JAR, genera una version portable y crea un instalador `.exe` con Java incluido.

Abre PowerShell en la carpeta del proyecto y ejecuta:

```powershell
powershell -ExecutionPolicy Bypass -File .\crear-instalador.ps1
```

Los resultados quedan en:

- `salida/portable/Torven Sistema de Ventas/`
- `salida/instalador/Torven Sistema de Ventas-1.2.0.exe`

El instalador es por usuario, crea un acceso directo en el escritorio y menu Inicio, permite elegir la carpeta de instalacion y registra el desinstalador de Windows. WiX Toolset se descarga de su repositorio oficial la primera vez que haga falta.

La edicion para clientes se conecta por Internet a MySQL en Railway. La PC de destino no necesita instalar Java, MySQL ni una VPN. La cuenta incluida es exclusiva de la aplicacion, exige TLS y no tiene permisos administrativos. La cuenta `root` nunca debe incluirse en el instalador.

## Licencia manual

La licencia se controla desde la tabla `railway.licencia`. Torven permite el acceso mientras el estado sea `ACTIVA` y bloquea el inicio de sesion solamente cuando el propietario cambia manualmente el estado a `SUSPENDIDA`. La fecha de vencimiento es informativa: no genera avisos ni suspensiones automaticas. Las consultas de administracion estan en `database/licencia_manual.sql`.

## Rendimiento con Railway

La version 1.2.0 reutiliza conexiones, carga Dashboard y Ventas en segundo plano, espera brevemente antes de consultar los buscadores y solo actualiza la pestana visible. Los procedimientos e indices remotos se pueden recrear con `database/rendimiento_procedimientos.sql`; deben ejecutarse con la cuenta administradora de Railway, nunca con la cuenta limitada incluida en el instalador.

## Mejoras incluidas

- Conexion configurable, sin clave fija dentro del codigo.
- Creacion automatica de tablas.
- Login con perfiles de administrador y vendedor.
- Dashboard real con ventas del dia, ventas semanales, ventas mensuales, clientes, productos, stock bajo, ventas filtradas por calendario desplegable y grafica por distrito.
- Dashboard con tabla de ventas vendidas por defecto y boton "Canceladas" para consultar ventas canceladas dentro del mismo rango de fechas.
- Reserva con estados: en proceso, vendida o cancelada. Al reservar descuenta stock; al cancelar lo devuelve. Para vendedores, la pantalla Ventas separa el flujo en "Nueva reserva" y "Reservas en proceso"; para administradores, Ventas queda directa porque ya tienen el dashboard.
- Venta/reserva con transaccion: si algo falla, no queda operacion a medias.
- Validacion de productos, clientes, stock y carrito.
- Venta con cantidad editable desde el resumen, validando enteros y stock disponible.
- Venta con cliente compacto: al seleccionar un cliente, la busqueda se oculta para dejar mas espacio al resumen.
- DNI/RUC y distrito como datos del cliente; ventas usa esos datos para el voucher.
- Eliminacion logica de clientes y productos: se deshabilitan sin borrar el historial de ventas.
- Voucher mas elaborado con vista previa, impresion y exportacion a PDF.
- Busqueda en productos, clientes y ventas.
- Icono e identidad visual de Torven. Los archivos de marca estan en `assets/app-icon.png`, `assets/torven-logo.png` y `assets/torven-mark.png`.
- Auditoria por triggers para registrar tabla, operacion, usuario de la app, usuario de base de datos, fecha/hora y valores anteriores/nuevos.
- Estructura fuente mantenible para seguir agregando reportes, usuarios y cierre de caja.

## Guia 24/7

El documento `docs/Guia_24_7_Torven.docx` explica como lanzar el sistema para que pueda funcionar todo el dia en un servidor o VPS.
