package api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import configuracion.AuditoriaContext;
import dao.DashboardDAO;
import dao.LugarEntregaDAO;
import dao.ProductoDAO;
import dao.UsuarioDAO;
import dao.VentaDAO;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpResponseException;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.staticfiles.Location;
import io.javalin.plugin.json.JavalinJackson;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import modelos.Distrito;
import modelos.LineaVenta;
import modelos.LugarEntrega;
import modelos.Usuario;
import modelos.VentaEstado;
import modelos.VentaTicket;

/**
 * API para la version movil de Torven. Solo cubre lo esencial para vender
 * desde el celular: iniciar sesion, buscar productos y registrar una venta
 * (con su lugar de entrega). La gestion de productos y el dashboard siguen
 * siendo exclusivos de la app de escritorio.
 *
 * Reutiliza los mismos DAO que la app de escritorio: la logica de negocio
 * (validar stock, precios, permisos) vive en un solo lugar.
 */
public final class ApiMain {
    private static final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private static final LugarEntregaDAO lugarEntregaDAO = new LugarEntregaDAO();
    private static final ProductoDAO productoDAO = new ProductoDAO();
    private static final VentaDAO ventaDAO = new VentaDAO();
    private static final DashboardDAO dashboardDAO = new DashboardDAO();
    private static final TokenStore tokens = new TokenStore();
    private static final LoginThrottle loginThrottle = new LoginThrottle();

    // Version mas reciente del instalador de escritorio, para el aviso de
    // actualizacion (ver servicios.ActualizacionService). Actualizar estos dos
    // valores cada vez que se suba un instalador nuevo a GitHub Releases.
    private static final String VERSION_ESCRITORIO = "1.2.2";
    private static final String VERSION_ESCRITORIO_URL =
            "https://github.com/jharidluis/TORVEN-API/releases/download/v1.2.2/Torven.Sistema.de.Ventas-1.2.2.exe";

    private ApiMain() {
    }

    public static void main(String[] args) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        int puerto = puertoDesdeEntorno();
        Javalin app = Javalin.create(config -> {
            config.addStaticFiles("/public", Location.CLASSPATH);
            config.jsonMapper(new JavalinJackson(mapper));
            // Sin CORS: la PWA se sirve desde este mismo servidor, no necesita
            // que otros origenes puedan llamar a la API.
        }).start(puerto);

        app.before(ApiMain::aplicarCabecerasSeguridad);

        app.post("/api/login", ApiMain::login);
        app.post("/api/logout", ApiMain::logout);
        app.get("/api/version", ApiMain::obtenerVersion);
        app.get("/api/productos", ApiMain::listarProductos);
        app.post("/api/lugares-entrega", ApiMain::crearLugarEntrega);
        app.get("/api/distritos", ApiMain::listarDistritos);
        app.post("/api/ventas", ApiMain::registrarVenta);
        app.get("/api/reservas", ApiMain::listarReservas);
        app.get("/api/reservas/{id}", ApiMain::obtenerReserva);
        app.post("/api/reservas/{id}/estado", ApiMain::actualizarEstadoReserva);

        app.exception(HttpResponseException.class, (ex, ctx) -> {
            ctx.status(ex.getStatus()).json(mapaError(ex.getMessage()));
        });
        app.exception(SQLException.class, (ex, ctx) -> {
            ctx.status(400).json(mapaError(ex.getMessage()));
        });
        app.exception(Exception.class, (ex, ctx) -> {
            ctx.status(500).json(mapaError("No se pudo conectar con la base de datos. Intenta de nuevo en unos minutos."));
        });
    }

    private static int puertoDesdeEntorno() {
        String valor = System.getenv("PORT");
        if (valor == null || valor.trim().isEmpty()) {
            return 7000;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException ex) {
            return 7000;
        }
    }

    private static void aplicarCabecerasSeguridad(Context ctx) {
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.header("X-Frame-Options", "DENY");
        ctx.header("Referrer-Policy", "no-referrer");
        ctx.header("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        ctx.header("Content-Security-Policy",
                "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                        + "script-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'");
    }

    private static String ipCliente(Context ctx) {
        String reenviada = ctx.header("X-Forwarded-For");
        if (reenviada != null && !reenviada.trim().isEmpty()) {
            return reenviada.split(",")[0].trim();
        }
        return ctx.ip();
    }

    private static void login(Context ctx) throws SQLException {
        String ip = ipCliente(ctx);
        if (loginThrottle.bloqueado(ip)) {
            throw new ForbiddenResponse("Demasiados intentos fallidos. Espera unos minutos e intenta de nuevo.");
        }

        LoginRequest cuerpo = ctx.bodyAsClass(LoginRequest.class);
        if (cuerpo.usuario == null || cuerpo.usuario.trim().isEmpty()
                || cuerpo.clave == null || cuerpo.clave.isEmpty()) {
            throw new BadRequestResponse("Ingresa usuario y clave.");
        }
        Usuario usuario = usuarioDAO.autenticar(cuerpo.usuario, cuerpo.clave.toCharArray());
        if (usuario == null) {
            loginThrottle.registrarFallo(ip);
            throw new UnauthorizedResponse("Usuario o clave incorrectos.");
        }
        loginThrottle.registrarExito(ip);
        String token = tokens.crear(usuario);
        ctx.json(new LoginResponse(token, usuario));
    }

    private static void logout(Context ctx) {
        tokens.invalidar(tokens.extraerToken(ctx.header("Authorization")));
        ctx.status(204);
    }

    private static Usuario autenticado(Context ctx) {
        Usuario usuario = tokens.validar(tokens.extraerToken(ctx.header("Authorization")));
        if (usuario == null) {
            throw new UnauthorizedResponse("Sesion invalida o expirada. Inicia sesion de nuevo.");
        }
        return usuario;
    }

    private static void obtenerVersion(Context ctx) {
        Map<String, String> mapa = new HashMap<String, String>();
        mapa.put("version", VERSION_ESCRITORIO);
        mapa.put("url", VERSION_ESCRITORIO_URL);
        ctx.json(mapa);
    }

    private static void listarProductos(Context ctx) throws SQLException {
        autenticado(ctx);
        ctx.json(productoDAO.listar(ctx.queryParam("buscar")));
    }

    private static void listarDistritos(Context ctx) throws SQLException {
        autenticado(ctx);
        ctx.json(lugarEntregaDAO.listarDistritos());
    }

    private static void crearLugarEntrega(Context ctx) throws SQLException {
        Usuario usuario = autenticado(ctx);
        LugarEntregaRequest cuerpo = ctx.bodyAsClass(LugarEntregaRequest.class);
        if (cuerpo.direccion == null || cuerpo.direccion.trim().isEmpty()) {
            throw new BadRequestResponse("Ingresa la direccion de entrega.");
        }
        if (cuerpo.idDistrito <= 0) {
            throw new BadRequestResponse("Selecciona el distrito de entrega.");
        }

        LugarEntrega lugarEntrega = new LugarEntrega();
        lugarEntrega.setNumero(cuerpo.numero == null ? "" : cuerpo.numero);
        lugarEntrega.setDireccion(cuerpo.direccion);
        lugarEntrega.setIdDistrito(cuerpo.idDistrito);

        AuditoriaContext.establecer(usuario);
        try {
            lugarEntregaDAO.crear(lugarEntrega);
            ctx.json(lugarEntregaDAO.obtenerPorId(lugarEntrega.getId()));
        } finally {
            AuditoriaContext.limpiar();
        }
    }

    private static void registrarVenta(Context ctx) throws SQLException {
        Usuario usuario = autenticado(ctx);
        VentaRequest cuerpo = ctx.bodyAsClass(VentaRequest.class);
        if (cuerpo.idLugarEntrega <= 0) {
            throw new BadRequestResponse("Falta el lugar de entrega.");
        }
        if (cuerpo.lineas == null || cuerpo.lineas.isEmpty()) {
            throw new BadRequestResponse("Agrega al menos un producto al carrito.");
        }
        if (cuerpo.horaEntregaPactada == null || cuerpo.horaEntregaPactada.trim().isEmpty()) {
            throw new BadRequestResponse("Ingresa la hora de entrega pactada.");
        }
        LocalDateTime horaEntregaPactada;
        try {
            horaEntregaPactada = LocalDateTime.parse(cuerpo.horaEntregaPactada.trim());
        } catch (DateTimeParseException ex) {
            throw new BadRequestResponse("La hora de entrega pactada no es valida.");
        }

        List<LineaVenta> carrito = new ArrayList<LineaVenta>();
        for (VentaRequest.LineaRequest linea : cuerpo.lineas) {
            // El precio y nombre reales los revalida VentaDAO contra la base de
            // datos; lo que venga del celular en esos campos no se usa nunca.
            carrito.add(new LineaVenta(linea.idProducto, "", BigDecimal.ZERO, linea.cantidad));
        }

        AuditoriaContext.establecer(usuario);
        try {
            VentaTicket ticket = ventaDAO.registrarReserva(cuerpo.idLugarEntrega, carrito, horaEntregaPactada);
            ctx.json(ticket);
        } finally {
            AuditoriaContext.limpiar();
        }
    }

    private static void listarReservas(Context ctx) throws SQLException {
        autenticado(ctx);
        ctx.json(ventaDAO.listarReservasWeb());
    }

    private static void obtenerReserva(Context ctx) throws SQLException {
        autenticado(ctx);
        long idVenta = idDesdePath(ctx);
        ctx.json(dashboardDAO.obtenerVentaTicket(idVenta));
    }

    private static void actualizarEstadoReserva(Context ctx) throws SQLException {
        Usuario usuario = autenticado(ctx);
        long idVenta = idDesdePath(ctx);
        EstadoRequest cuerpo = ctx.bodyAsClass(EstadoRequest.class);
        String estado = VentaEstado.normalizar(cuerpo.estado);
        if (!VentaEstado.VENDIDA.equals(estado) && !VentaEstado.CANCELADA.equals(estado)) {
            throw new BadRequestResponse("El estado debe ser VENDIDA o CANCELADA.");
        }

        AuditoriaContext.establecer(usuario);
        try {
            ventaDAO.cambiarEstadoReserva(idVenta, estado);
            ctx.json(dashboardDAO.obtenerVentaTicket(idVenta));
        } finally {
            AuditoriaContext.limpiar();
        }
    }

    private static long idDesdePath(Context ctx) {
        try {
            return Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException ex) {
            throw new BadRequestResponse("Numero de venta invalido.");
        }
    }

    private static Map<String, String> mapaError(String mensaje) {
        Map<String, String> mapa = new HashMap<String, String>();
        mapa.put("error", mensaje == null ? "Error desconocido." : mensaje);
        return mapa;
    }
}
