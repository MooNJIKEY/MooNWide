package haven;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class MoonUiIcons {
    private static final Map<String, BufferedImage> raw = new HashMap<>();
    private static final Map<String, BufferedImage> scaled = new HashMap<>();

    public static BufferedImage image(String key, Coord size) {
        if(key == null || size == null)
            return null;
        String ck = key + ":" + size.x + "x" + size.y;
        synchronized(scaled) {
            BufferedImage img = scaled.get(ck);
            if(img != null)
                return img;
            BufferedImage src = load(key);
            if(src == null)
                return null;
            img = PUtils.uiscale(src, Coord.of(Math.max(8, size.x), Math.max(8, size.y)));
            scaled.put(ck, img);
            return img;
        }
    }

    private static BufferedImage load(String key) {
        synchronized(raw) {
            if(raw.containsKey(key))
                return raw.get(key);
            BufferedImage img = null;
            String file = key.replace('.', '-') + ".png";
            String[] paths = {
                "/haven/res/moon/ui/icons/" + file,
                "/res/moon/ui/icons/" + file,
                "res/moon/ui/icons/" + file,
            };
            for(String path : paths) {
                try(InputStream in = MoonUiIcons.class.getResourceAsStream(path)) {
                    if(in != null) {
                        img = ImageIO.read(in);
                        if(img != null)
                            break;
                    }
                } catch(Exception ignored) {
                }
            }
            if(img == null)
                img = fallbackIcon(key);
            raw.put(key, img);
            return img;
        }
    }

    private static BufferedImage fallbackIcon(String key) {
        BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(38, 28, 58, 255));
            g.fillRect(0, 0, 24, 24);
            g.setColor(new Color(92, 74, 132, 255));
            g.drawRect(0, 0, 23, 23);
            g.setColor(new Color(225, 196, 132, 230));
            if("slot.empty".equals(key)) {
                g.drawOval(5, 5, 14, 14);
                g.drawLine(12, 4, 12, 9);
            } else if("moon.macros".equals(key)) {
                g.drawRect(5, 5, 5, 5);
                g.drawRect(14, 5, 5, 5);
                g.drawRect(5, 14, 5, 5);
                g.drawRect(14, 14, 5, 5);
            } else if("moon.passivegate".equals(key)) {
                g.drawLine(7, 5, 7, 19);
                g.drawLine(17, 5, 17, 19);
                g.drawLine(7, 8, 17, 8);
                g.drawLine(12, 8, 12, 19);
            } else {
                g.fillOval(9, 9, 6, 6);
            }
        } finally {
            g.dispose();
        }
        return img;
    }
}
