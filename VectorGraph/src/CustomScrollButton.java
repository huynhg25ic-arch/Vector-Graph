import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

public class CustomScrollButton extends JButton {
    private int orientation;

    public CustomScrollButton(int orientation) {
        this.orientation = orientation;

        this.setContentAreaFilled(false);
        this.setBorder(BorderFactory.createEmptyBorder());
        this.setFocusable(false);
        this.setBackground(new Color(0, 0, 0, 100));
        this.setOpaque(false);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(16, 20);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (getModel().isPressed()) {
            g2.setColor(Color.GRAY);
        } else if (getModel().isRollover()) {
            g2.setColor(Color.BLACK);
        } else {
            g2.setColor(new Color(150, 150, 150));
        }

        int w = getWidth();
        int h = getHeight();
        int size = 3;

        int cx = w / 2;
        int cy = h / 2+4;

        g2.setStroke(new BasicStroke(2f));



        switch (orientation) {
            case SwingConstants.NORTH:
                Path2D.Double pathU = new Path2D.Double();
                pathU.moveTo(cx - size, cy + size-8);
                pathU.lineTo(cx, cy - size-8);
                pathU.lineTo(cx + size, cy + size-8);
                pathU.lineTo(cx - size, cy + size-8);
                g2.fill(pathU);
                break;
            case SwingConstants.SOUTH:
                Path2D.Double pathD = new Path2D.Double();
                pathD.moveTo(cx, cy + size);
                pathD.lineTo(cx - size, cy - size);
                pathD.lineTo(cx + size, cy - size);
                pathD.lineTo(cx, cy + size);
                g2.fill(pathD);
                break;
        }

        g2.dispose();
    }
}