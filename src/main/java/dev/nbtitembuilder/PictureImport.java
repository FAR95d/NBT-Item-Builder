package dev.nbtitembuilder;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Turns picture files into lore rows: one int per pixel (0xRRGGBB), drawn as
 * colored block characters. Pictures are read from the
 * {@code nbtitembuilder-pictures} folder inside the config folder, through an
 * in-game list, so no system file window is needed. No Minecraft classes are
 * used, so the conversion can be tested on its own.
 */
public final class PictureImport {
    private PictureImport() {}

    /** Width pictures are scaled to, in characters. */
    public static final int TARGET_WIDTH = 16;
    /** Tallest a picture may become, in lore lines. */
    public static final int MAX_HEIGHT = 12;

    private static Path dir;

    /** Called once at startup with the folder pictures are read from. */
    public static void setDir(Path folder) {
        dir = folder;
    }

    public static Path folder() {
        return dir;
    }

    private static boolean isPicture(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".gif") || n.endsWith(".bmp");
    }

    /** {file name, file name} pairs for the in-game picker, sorted A to Z. */
    public static List<String[]> imageOptions() {
        List<String[]> out = new ArrayList<>();
        if (dir == null) return out;
        try {
            Files.createDirectories(dir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                for (Path p : stream) {
                    if (Files.isRegularFile(p) && isPicture(p.getFileName().toString())) {
                        out.add(new String[] {p.getFileName().toString(), p.getFileName().toString()});
                    }
                }
            }
        } catch (IOException e) {
            return out;
        }
        out.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
        return out;
    }

    /** Reads one file from the pictures folder and converts it to lore rows. */
    public static List<int[]> load(String fileName) {
        if (dir == null) throw new IllegalArgumentException("The pictures folder is not set up.");
        Path file = dir.resolve(fileName).normalize();
        if (!file.startsWith(dir)) throw new IllegalArgumentException("That picture is not allowed.");
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null) throw new IllegalArgumentException("That file is not a picture.");
            return convert(img, TARGET_WIDTH, MAX_HEIGHT);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read that picture.");
        }
    }

    /** Scales an image down to character rows. Transparent parts turn dark like the tooltip. */
    public static List<int[]> convert(BufferedImage src, int targetWidth, int maxHeight) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw <= 0 || sh <= 0) throw new IllegalArgumentException("That picture is empty.");
        double scale = Math.min((double) targetWidth / sw, (double) maxHeight / sh);
        int dw = Math.max(1, (int) Math.round(sw * scale));
        int dh = Math.max(1, (int) Math.round(sh * scale));
        BufferedImage flat = new BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = flat.createGraphics();
        g.setColor(new java.awt.Color(0x14101C));
        g.fillRect(0, 0, dw, dh);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, dw, dh, null);
        g.dispose();
        List<int[]> rows = new ArrayList<>();
        for (int y = 0; y < dh; y++) {
            int[] row = new int[dw];
            for (int x = 0; x < dw; x++) row[x] = flat.getRGB(x, y) & 0xFFFFFF;
            rows.add(row);
        }
        return rows;
    }
}
