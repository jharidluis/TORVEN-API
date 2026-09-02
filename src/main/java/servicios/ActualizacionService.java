package servicios;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Scanner;

/**
 * Revisa contra la API de Torven (la misma que usa la web) si hay una
 * version de escritorio mas nueva que la que esta corriendo, para avisarle
 * al usuario en vez de tener que entregarle el instalador manualmente.
 */
public class ActualizacionService {
    private static final String URL_VERSION = "https://torven-api-production.up.railway.app/api/version";
    private static final int TIMEOUT_MS = 5000;

    public static final class InfoActualizacion {
        public final String versionDisponible;
        public final String urlDescarga;

        InfoActualizacion(String versionDisponible, String urlDescarga) {
            this.versionDisponible = versionDisponible;
            this.urlDescarga = urlDescarga;
        }
    }

    private static final class VersionRespuesta {
        public String version;
        public String url;
    }

    public String versionActual() {
        Properties propiedades = new Properties();
        try (InputStream in = ActualizacionService.class.getResourceAsStream("/version.properties")) {
            if (in != null) {
                propiedades.load(in);
            }
        } catch (IOException ignored) {
            // Si no se puede leer, se asume version desconocida y no se avisa nada.
        }
        return propiedades.getProperty("version", "0.0.0");
    }

    /**
     * Devuelve null si no hay conexion, la version no se pudo revisar, o ya
     * se tiene la ultima version disponible.
     */
    public InfoActualizacion buscarActualizacion() {
        try {
            HttpURLConnection conexion = (HttpURLConnection) new URL(URL_VERSION).openConnection();
            conexion.setRequestMethod("GET");
            conexion.setConnectTimeout(TIMEOUT_MS);
            conexion.setReadTimeout(TIMEOUT_MS);
            if (conexion.getResponseCode() != 200) {
                return null;
            }

            String cuerpo;
            try (InputStream in = conexion.getInputStream();
                 Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                scanner.useDelimiter("\\A");
                cuerpo = scanner.hasNext() ? scanner.next() : "";
            }

            VersionRespuesta respuesta = new ObjectMapper().readValue(cuerpo, VersionRespuesta.class);
            if (respuesta.version == null || !esMasNueva(respuesta.version, versionActual())) {
                return null;
            }
            return new InfoActualizacion(respuesta.version, respuesta.url);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean esMasNueva(String remota, String local) {
        String[] partesRemota = remota.split("\\.");
        String[] partesLocal = local.split("\\.");
        int longitud = Math.max(partesRemota.length, partesLocal.length);
        for (int i = 0; i < longitud; i++) {
            int numRemota = parteNumerica(partesRemota, i);
            int numLocal = parteNumerica(partesLocal, i);
            if (numRemota != numLocal) {
                return numRemota > numLocal;
            }
        }
        return false;
    }

    private int parteNumerica(String[] partes, int indice) {
        if (indice >= partes.length) {
            return 0;
        }
        try {
            return Integer.parseInt(partes[indice].trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
