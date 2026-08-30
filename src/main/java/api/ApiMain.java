package api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import configuracion.AuditoriaContext;
import dao.ClienteDAO;
import dao.ProductoDAO;
import dao.UsuarioDAO;
import dao.VentaDAO;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.staticfiles.Location;
import io.javalin.plugin.json.JavalinJackson;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import modelos.LineaVenta;
import modelos.Usuario;
import modelos.VentaTicket;

/**
 * API para la version movil de Torven. Solo cubre lo esencial para vender
 * desde el celular: iniciar sesion, buscar productos y clientes, y registrar
 * una venta. La gestion de clientes/productos y el dashboard siguen siendo
 * exclusivos de la app de escritorio.
 *
 * Reutiliza los mismos DAO que la app de escritorio: la logica de negocio
 * (validar stock, precios, permisos) vive en un solo lugar.
 */
public final class ApiMain {
    private static final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private static final ClienteDAO clienteDAO = new ClienteDAO();
    private static final ProductoDAO productoDAO = new ProductoDAO();
    private static final VentaDAO ventaDAO = new VentaDAO();
    private static final TokenStore tokens = new TokenStore();

    private ApiMain() {
    }

    public static void main(String[] args) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        int puerto = puertoDesdeEntorno();
        Javalin app = Javalin.create(config -> {
            config.addStaticFiles("/public", Location.CLASSPATH);
            config.enableCorsForAllOrigins();
            config.jsonMapper(new JavalinJackson(mapper));
        }).start(puerto);

        app.post("/api/login", ApiMain::login);
        app.post("/api/logout", ApiMain::logout);
        app.get("/api/productos", ApiMain::listarProductos);
        app.get("/api/clientes", ApiMain::listarClientes);
        app.post("/api/ventas", ApiMain::registrarVenta);

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

    private static void login(Context ctx) throws SQLException {
        LoginRequest cuerpo = ctx.bodyAsClass(LoginRequest.class);
        if (cuerpo.usuario == null || cuerpo.usuario.trim().isEmpty()
                || cuerpo.clave == null || cuerpo.clave.isEmpty()) {
            throw new BadRequestResponse("Ingresa usuario y clave.");
        }
        Usuario usuario = usuarioDAO.autenticar(cuerpo.usuario, cuerpo.clave.toCharArray());
        if (usuario == null) {
            throw new UnauthorizedResponse("Usuario o clave incorrectos.");
        }
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

    private static void listarProductos(Context ctx) throws SQLException {
        autenticado(ctx);
        ctx.json(productoDAO.listar(ctx.queryParam("buscar")));
    }

    private static void listarClientes(Context ctx) throws SQLException {
        autenticado(ctx);
        ctx.json(clienteDAO.listar(ctx.queryParam("buscar")));
    }

    private static void registrarVenta(Context ctx) throws SQLException {
        Usuario usuario = autenticado(ctx);
        VentaRequest cuerpo = ctx.bodyAsClass(VentaRequest.class);
        if (cuerpo.idCliente <= 0) {
            throw new BadRequestResponse("Selecciona un cliente.");
        }
        if (cuerpo.lineas == null || cuerpo.lineas.isEmpty()) {
            throw new BadRequestResponse("Agrega al menos un producto al carrito.");
        }

        List<LineaVenta> carrito = new ArrayList<LineaVenta>();
        for (VentaRequest.LineaRequest linea : cuerpo.lineas) {
            // El precio y nombre reales los revalida VentaDAO contra la base de
            // datos; lo que venga del celular en esos campos no se usa nunca.
            carrito.add(new LineaVenta(linea.idProducto, "", BigDecimal.ZERO, linea.cantidad));
        }

        AuditoriaContext.establecer(usuario);
        try {
            VentaTicket ticket = ventaDAO.registrarVenta(cuerpo.idCliente, carrito);
            ctx.json(ticket);
        } finally {
            AuditoriaContext.limpiar();
        }
    }

    private static Map<String, String> mapaError(String mensaje) {
        Map<String, String> mapa = new HashMap<String, String>();
        mapa.put("error", mensaje == null ? "Error desconocido." : mensaje);
        return mapa;
    }
}
