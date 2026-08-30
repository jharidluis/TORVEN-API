package configuracion;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class AppConfig {
    private static final String CONFIG_FILE = "config/database.properties";
    private static AppConfig instance;

    private final Properties properties = new Properties();
    private Path loadedConfigPath;

    private AppConfig() {
        cargarDefectos();
        cargarArchivo();
        cargarVariablesEntorno();
    }

    public static synchronized AppConfig get() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    private void cargarDefectos() {
        properties.setProperty("db.host", "localhost");
        properties.setProperty("db.port", "3306");
        properties.setProperty("db.name", "tienda");
        properties.setProperty("db.user", "root");
        properties.setProperty("db.password", "");
        properties.setProperty("db.autoCreate", "true");
        properties.setProperty("db.autoMigrate", "true");
        properties.setProperty("db.sslMode", "PREFERRED");
        properties.setProperty("store.name", "Torven");
        properties.setProperty("store.ruc", "");
        properties.setProperty("store.address", "");
        properties.setProperty("store.phone", "");
        properties.setProperty("voucher.folder", "vouchers");
    }

    private void cargarArchivo() {
        Path path = buscarArchivoConfiguracion();
        if (path == null) {
            return;
        }
        try (InputStream in = new FileInputStream(path.toFile())) {
            properties.load(in);
            loadedConfigPath = path;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer " + path + ": " + ex.getMessage(), ex);
        }
    }

    private Path buscarArchivoConfiguracion() {
        Path path = siExiste(Paths.get(CONFIG_FILE));
        if (path != null) {
            return path;
        }

        Path base = rutaAplicacion();
        while (base != null) {
            path = siExiste(base.resolve(CONFIG_FILE));
            if (path != null) {
                return path;
            }
            base = base.getParent();
        }
        return null;
    }

    private Path siExiste(Path path) {
        Path absoluto = path.toAbsolutePath().normalize();
        return Files.exists(absoluto) ? absoluto : null;
    }

    private Path rutaAplicacion() {
        try {
            Path location = Paths.get(AppConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                return location.getParent();
            }
            return location;
        } catch (URISyntaxException | SecurityException ex) {
            return Paths.get("").toAbsolutePath();
        }
    }

    private void cargarVariablesEntorno() {
        Map<String, String> variables = new LinkedHashMap<String, String>();
        variables.put("DB_HOST", "db.host");
        variables.put("DB_PORT", "db.port");
        variables.put("DB_NAME", "db.name");
        variables.put("DB_USER", "db.user");
        variables.put("DB_PASSWORD", "db.password");
        variables.put("DB_AUTO_CREATE", "db.autoCreate");
        variables.put("DB_AUTO_MIGRATE", "db.autoMigrate");
        variables.put("DB_SSL_MODE", "db.sslMode");
        variables.put("STORE_NAME", "store.name");
        variables.put("STORE_RUC", "store.ruc");
        variables.put("STORE_ADDRESS", "store.address");
        variables.put("STORE_PHONE", "store.phone");
        variables.put("VOUCHER_FOLDER", "voucher.folder");

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = System.getenv(entry.getKey());
            if (value != null && !value.trim().isEmpty()) {
                properties.setProperty(entry.getValue(), value.trim());
            }
        }
    }

    public String get(String key) {
        return properties.getProperty(key, "").trim();
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    public String databaseName() {
        return get("db.name");
    }

    public String user() {
        return get("db.user");
    }

    public String password() {
        return get("db.password");
    }

    public String configPathDescription() {
        if (loadedConfigPath == null) {
            return CONFIG_FILE;
        }
        return loadedConfigPath.toString();
    }

    public String jdbcServerUrl() {
        return "jdbc:mysql://" + get("db.host") + ":" + get("db.port")
                + "/?" + jdbcOptions();
    }

    public String jdbcDatabaseUrl() {
        return "jdbc:mysql://" + get("db.host") + ":" + get("db.port") + "/" + databaseName()
                + "?" + jdbcOptions();
    }

    private String jdbcOptions() {
        String sslMode = get("db.sslMode").toUpperCase();
        if (!("DISABLED".equals(sslMode)
                || "PREFERRED".equals(sslMode)
                || "REQUIRED".equals(sslMode)
                || "VERIFY_CA".equals(sslMode)
                || "VERIFY_IDENTITY".equals(sslMode))) {
            sslMode = "PREFERRED";
        }
        return "sslMode=" + sslMode
                + "&serverTimezone=America/Lima"
                + "&connectTimeout=10000"
                + "&socketTimeout=30000";
    }
}
