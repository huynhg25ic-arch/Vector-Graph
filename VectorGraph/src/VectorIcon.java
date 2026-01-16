import javax.swing.*;
import java.awt.*;

abstract class VectorIcon implements Icon {
    protected int size;
    protected Color color;

    public VectorIcon(int size, Color color) {
        this.size = size;
        this.color = color;
    }

    @Override
    public void paintIcon(Component cp, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.5f));
        draw(g2, x, y);
        g2.dispose();
    }

    abstract void draw(Graphics2D g2, int x, int y);

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}

class PanIcon extends VectorIcon {
    public PanIcon(int size, Color color) {
        super(size, color);
    }

    @Override
    void draw(Graphics2D g, int x, int y) {
        g.drawLine(x + 4, y + size / 2, x + size - 4, y + size / 2);
        g.drawLine(x + size / 2, y + 4, x + size / 2, y + size - 4);
        g.drawLine(x + 4, y + size / 2, x + 8, y + size / 2 - 4);
        g.drawLine(x + 4, y + size / 2, x + 8, y + size / 2 + 4);
    }
}

class SelectIcon extends VectorIcon {
    public SelectIcon(int size, Color color) {
        super(size, color);
    }

    @Override
    void draw(Graphics2D g, int x, int y) {
        g.drawRect(x + 5, y + 5, size - 10, size - 10);
        g.drawLine(x + size, y + size, x + size - 6, y + size - 6);
    }
}

class RectIcon extends VectorIcon {
    public RectIcon(int size, Color color) {
        super(size, color);
    }

    @Override
    void draw(Graphics2D g, int x, int y) {
        g.drawRect(x + 4, y + 6, size - 8, size - 12);
    }
}

class LineIcon extends VectorIcon {
    public LineIcon(int size, Color color) {
        super(size, color);
    }

    @Override
    void draw(Graphics2D g, int x, int y) {
        g.drawLine(x + 4, y + size - 4, x + size - 4, y + 4);
    }
}

class CircleIcon extends VectorIcon {
    public CircleIcon(int size, Color color) {
        super(size, color);
    }

    @Override
    void draw(Graphics2D g, int x, int y) {
        g.drawOval(x + 4, y + 4, size - 8, size - 8);
    }
}

class PolyIcon extends VectorIcon {
    public PolyIcon(int size, Color color) {
        super(size, color);
    }

    @Override
    void draw(Graphics2D g, int x, int y) {
        int[] px = {x + size / 2, x + size - 4, x + 4};
        int[] py = {y + 4, y + size - 4, y + size - 4};
        g.drawPolygon(px, py, 3);
    }
}

class ZoomInIcon extends VectorIcon {
    public ZoomInIcon(int size, Color color) {
        super(size, color);
    }

    @Override
    void draw(Graphics2D g, int x, int y) {
        int r = (int) (size * 0.35);
        int cx = x + size / 2 - (int) (size * 0.1);
        int cy = y + size / 2 - (int) (size * 0.1);
        g.drawOval(cx - r, cy - r, r * 2, r * 2);
        g.drawLine(cx + (int) (r * 0.7), cy + (int) (r * 0.7), x + size - 2, y + size - 2);
        g.drawLine(cx, cy - (int) (r * 0.6), cx, cy + (int) (r * 0.6));
        g.drawLine(cx - (int) (r * 0.6), cy, cx + (int) (r * 0.6), cy);
    }
}

class ZoomOutIcon extends VectorIcon {
    public ZoomOutIcon(int size, Color color) {
        super(size, color);
    }

    @Override
    void draw(Graphics2D g, int x, int y) {
        int r = (int) (size * 0.35);
        int cx = x + size / 2 - (int) (size * 0.1);
        int cy = y + size / 2 - (int) (size * 0.1);
        g.drawOval(cx - r, cy - r, r * 2, r * 2);
        g.drawLine(cx + (int) (r * 0.7), cy + (int) (r * 0.7), x + size - 2, y + size - 2);
        g.drawLine(cx - (int) (r * 0.6), cy, cx + (int) (r * 0.6), cy);
    }
}

class ColorSwatchIcon extends VectorIcon {
    public ColorSwatchIcon(int size, Color color) {
        super(size, color);
    }

    @Override
    void draw(Graphics2D g, int x, int y) {
        g.setColor(color);
        g.fillRect(x + 2, y + 2, size - 4, size - 4);
        g.setColor(Color.GRAY);
        g.drawRect(x + 2, y + 2, size - 4, size - 4);
    }
}

class StrokeIcon extends VectorIcon {
    public StrokeIcon(int size, Color color) {
        super(size, color);
    }

    @Override
    void draw(Graphics2D g, int x, int y) {
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(3));
        g.drawLine(x + 2, y + size / 2, x + size - 2, y + size / 2);
    }
}

class TrashIcon extends VectorIcon {
    public TrashIcon(int size, Color color) {
        super(size, color);
    }

    @Override
    void draw(Graphics2D g, int x, int y) {
        g.drawLine(x + 6, y + 6, x + size - 6, y + size - 6);
        g.drawLine(x + 6, y + size - 6, x + size - 6, y + 6);
    }
}

class PointIcon extends VectorIcon {
    public PointIcon(int size, Color color) {
        super(size, color);
    }

    @Override
    void draw(Graphics2D g, int x, int y) {
        g.drawLine(x + size / 2 - 3, y + size / 2 - 3, x + size / 2 + 3, y + size / 2 + 3);
        g.drawLine(x + size / 2 - 3, y + size / 2 + 3, x + size / 2 + 3, y + size / 2 - 3);
    }
}