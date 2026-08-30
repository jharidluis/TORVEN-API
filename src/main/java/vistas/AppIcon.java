package vistas;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

public final class AppIcon {
    private AppIcon() {
    }

    public static BufferedImage crear(int size) {
        BufferedImage desdeArchivo = cargarDesdeArchivo(size);
        if (desdeArchivo != null) {
            return desdeArchivo;
        }
        return crearFallback(size);
    }

    public static BufferedImage crearLogo(int width, int height) {
        Path path = buscarAsset("torven-logo.png");
        if (path == null) {
            return crear(width, height);
        }
        try {
            BufferedImage original = ImageIO.read(path.toFile());
            if (original == null) {
                return crear(width, height);
            }
            return escalar(original, width, height, true);
        } catch (IOException ex) {
            return crear(width, height);
        }
    }

    private static BufferedImage crearFallback(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(13, 33, 64));
        g.fillRoundRect(0, 0, size, size, size / 5, size / 5);

        g.setColor(new Color(20, 184, 166));
        g.fillOval(size / 8, size / 8, size * 3 / 4, size * 3 / 4);

        g.setColor(new Color(255, 255, 255, 235));
        int chip = size / 3;
        int x = (size - chip) / 2;
        int y = (size - chip) / 2;
        g.fillRoundRect(x, y, chip, chip, size / 12, size / 12);

        g.setColor(new Color(13, 33, 64));
        g.setStroke(new BasicStroke(Math.max(2, size / 24)));
        g.drawLine(size / 2, size / 5, size / 2, y);
        g.drawLine(size / 2, y + chip, size / 2, size * 4 / 5);
        g.drawLine(size / 5, size / 2, x, size / 2);
        g.drawLine(x + chip, size / 2, size * 4 / 5, size / 2);

        g.setColor(new Color(14, 116, 144));
        int dot = Math.max(3, size / 12);
        g.fillOval(size / 2 - dot / 2, size / 2 - dot / 2, dot, dot);
        g.dispose();
        return image;
    }

    private static BufferedImage cargarDesdeArchivo(int size) {
        Path path = buscarAsset("app-icon.png");
        if (path == null) {
            return null;
        }
        try {
            BufferedImage original = ImageIO.read(path.toFile());
            if (original == null) {
                return null;
            }
            return escalar(original, size, size, false);
        } catch (IOException ex) {
            return null;
        }
    }

    private static BufferedImage crear(int width, int height) {
        BufferedImage fallback = crearFallback(Math.min(width, height));
        return escalar(fallback, width, height, true);
    }

    private static BufferedImage escalar(BufferedImage original, int width, int height, boolean mantenerProporcion) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int drawX = 0;
        int drawY = 0;
        int drawW = width;
        int drawH = height;
        if (mantenerProporcion) {
            double scale = Math.min(width / (double) original.getWidth(), height / (double) original.getHeight());
            drawW = Math.max(1, (int) Math.round(original.getWidth() * scale));
            drawH = Math.max(1, (int) Math.round(original.getHeight() * scale));
            drawX = (width - drawW) / 2;
            drawY = (height - drawH) / 2;
        }
        g.drawImage(original, drawX, drawY, drawW, drawH, null);
        g.dispose();
        return image;
    }

    private static Path buscarAsset(String nombre) {
        Path cwd = Paths.get("").toAbsolutePath();
        Path path = siExiste(cwd.resolve("assets").resolve(nombre));
        if (path != null) {
            return path;
        }

        Path base = rutaAplicacion();
        while (base != null) {
            path = siExiste(base.resolve("assets").resolve(nombre));
            if (path != null) {
                return path;
            }
            path = siExiste(base.resolve("../assets").resolve(nombre).normalize());
            if (path != null) {
                return path;
            }
            base = base.getParent();
        }
        return null;
    }

    private static Path siExiste(Path path) {
        Path absoluto = path.toAbsolutePath().normalize();
        return Files.exists(absoluto) ? absoluto : null;
    }

    private static Path rutaAplicacion() {
        try {
            Path location = Paths.get(AppIcon.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                return location.getParent();
            }
            return location;
        } catch (URISyntaxException | SecurityException ex) {
            return Paths.get("").toAbsolutePath();
        }
    }
}
