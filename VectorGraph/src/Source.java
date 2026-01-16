import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Stack;

public class Source extends JFrame {

    enum Mode { PAN, SELECT, RECTANGLE, LINE, POINT, CIRCLE, POLYGON }
    Mode currentMode = Mode.PAN;
    Color currentColor = Color.BLACK;
    float currentStroke = 2.0f;
    boolean snapToGrid = false;
    final int GRID_SIZE = 50;

    GraphCanvas canvas;
    UndoManager undoManager = new UndoManager();

    DefaultListModel<GraphObject> layerModel;
    JList<GraphObject> layerList;
    boolean ignoreLayerEvents = false;
    JPopupMenu contextMenu;

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(Source.class.getName());

    public Source() {
        initComponents();
        setupLogic();
    }

    private void setupLogic() {

        try {
            java.net.URL iconURL = getClass().getResource("/thumbnail.png");

            if (iconURL != null) {
                this.setIconImage(new ImageIcon(iconURL).getImage());
            } else {
                System.err.println("Icon not found! Check file location.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        canvasPnl.setLayout(new BorderLayout());
        canvas = new GraphCanvas();
        canvasPnl.add(canvas, BorderLayout.CENTER);

        PanningHandler panner = new PanningHandler();
        canvas.addMouseListener(panner);
        canvas.addMouseMotionListener(panner);

        DrawingHandler drawer = new DrawingHandler();
        canvas.addMouseListener(drawer);
        canvas.addMouseMotionListener(drawer);

        SelectHandler selector = new SelectHandler();
        canvas.addMouseListener(selector);
        canvas.addMouseMotionListener(selector);

        canvas.addMouseWheelListener(new ScaleHandler());

        setupLayerManager();

        Color iconColor = new Color(80, 80, 80);
        int s = 20;

        configureButton(panBtn, new PanIcon(s, iconColor), Mode.PAN, "Pan");
        configureButton(selectBtn, new SelectIcon(s, iconColor), Mode.SELECT, "Select");
        configureButton(pointBtn, new PointIcon(s, iconColor), Mode.POINT, "Point");
        configureButton(rectBtn, new RectIcon(s, iconColor), Mode.RECTANGLE, "Rectangle");
        configureButton(lineBtn, new LineIcon(s, iconColor), Mode.LINE, "Line");
        configureButton(circeBtn, new CircleIcon(s, iconColor), Mode.CIRCLE, "Circle");
        configureButton(polygonBtn, new PolyIcon(s, iconColor), Mode.POLYGON, "Polygon");

        JButton colorBtn = new JButton(new ColorSwatchIcon(s, currentColor));
        styleButton(colorBtn, "Color");
        colorBtn.addActionListener(e -> pickColor(colorBtn));
        editPnl.add(colorBtn);

        JButton strokeBtn = new JButton(new StrokeIcon(s, iconColor));
        styleButton(strokeBtn, "Line Width");
        strokeBtn.addActionListener(e -> changeStroke());
        editPnl.add(strokeBtn);

        JButton delBtn = new JButton(new TrashIcon(s, Color.RED));
        styleButton(delBtn, "Delete Selected");
        delBtn.addActionListener(e -> deleteSelected());
        editPnl.add(delBtn);

        JCheckBox snapBox = new JCheckBox("Grid Snap");
        snapBox.setFocusPainted(false);
        snapBox.setBackground(Color.WHITE);
        snapBox.addActionListener(e -> { snapToGrid = snapBox.isSelected(); });
        editPnl.add(snapBox);

        zoomSlider.addChangeListener(e -> {
            canvas.scale = zoomSlider.getValue() / 100.0;
            canvas.repaint();
        });

        zoomInBtn.setIcon(new ZoomInIcon(s, iconColor));
        zoomOutBtn.setIcon(new ZoomOutIcon(s, iconColor));

        setupContextMenu();
        setupMenus();
        setupKeyboardShortcuts();

        canvasPnl.revalidate(); canvasPnl.repaint();
        jPanel6.revalidate(); jPanel6.repaint();
        editPnl.revalidate(); editPnl.repaint();

        canvas.translateX = canvasPnl.getWidth() / 2;
        canvas.translateY = canvasPnl.getHeight() / 2;

    }

    private void configureButton(JButton btn, Icon icon, Mode mode, String tip) {
        btn.setText("");
        btn.setIcon(icon);
        btn.setToolTipText(tip);
        for(ActionListener al : btn.getActionListeners()) btn.removeActionListener(al);
        btn.addActionListener(e -> { currentMode = mode; });
    }

    private void styleButton(JButton btn, String tip) {
        btn.setPreferredSize(new Dimension(30, 30));
        btn.setBackground(new Color(245, 245, 245));
        btn.setFocusPainted(false);
        btn.setToolTipText(tip);
    }

    private void setupLayerManager() {
        objManagerPnl.setLayout(new BorderLayout());
        layerModel = new DefaultListModel<>();
        layerList = new JList<>(layerModel);
        layerList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        layerList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof GraphObject) {
                    GraphObject obj = (GraphObject) value;
                    setText(obj.name); setIcon(new ColorSwatchIcon(12, obj.color));
                } return this;
            }
        });
        layerList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || ignoreLayerEvents) return;
            for (GraphObject o : canvas.objects) o.isSelected = false;
            for (GraphObject sel : layerList.getSelectedValuesList()) sel.isSelected = true;
            canvas.repaint();
        });
        layerList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) renameSelected();
                if (SwingUtilities.isRightMouseButton(e)) {
                    layerList.setSelectedIndex(layerList.locationToIndex(e.getPoint()));
                    contextMenu.show(layerList, e.getX(), e.getY());
                }
            }
        });
        objManagerPnl.add(new JScrollPane(layerList), BorderLayout.CENTER);

        JPanel ctrl = new JPanel(new GridLayout(1, 2));
        JButton up = new JButton("Up"); up.addActionListener(e -> moveLayer(1));
        JButton dn = new JButton("Down"); dn.addActionListener(e -> moveLayer(-1));
        ctrl.add(up); ctrl.add(dn);
        objManagerPnl.add(ctrl, BorderLayout.SOUTH);
    }

    private void pickColor(JButton btn) {
        Color c = JColorChooser.showDialog(this, "Color", currentColor);
        if(c!=null) {
            currentColor = c;
            btn.setIcon(new ColorSwatchIcon(20, c));
            boolean changed = false;
            for(GraphObject o : canvas.objects) {
                if(o.isSelected) {
                    if(!changed) { undoManager.saveState(canvas.objects); changed=true; }
                    o.color = c;
                }
            }
            if(changed) { canvas.repaint(); refreshLayers(); }
        }
    }

    private void changeStroke() {
        String in = JOptionPane.showInputDialog(this, "Width:", currentStroke);
        try {
            float f = Float.parseFloat(in);
            if(f>0) {
                currentStroke = f;
                boolean changed = false;
                for(GraphObject o : canvas.objects) {
                    if(o.isSelected) {
                        if(!changed) { undoManager.saveState(canvas.objects); changed=true; }
                        o.strokeWidth=f;
                    }
                }
                if(changed) canvas.repaint();
            }
        } catch(Exception e){}
    }

    private void refreshLayers() {
        ignoreLayerEvents = true; layerModel.clear();
        for (int i = canvas.objects.size() - 1; i >= 0; i--) layerModel.addElement(canvas.objects.get(i));
        ignoreLayerEvents = false;
    }
    private void updateLayerSelection() {
        ignoreLayerEvents = true; layerList.clearSelection();
        for (GraphObject o : canvas.objects) if (o.isSelected) layerList.setSelectedValue(o, false);
        ignoreLayerEvents = false;
    }
    private void moveLayer(int dir) {
        GraphObject sel = getSelectedObject();
        if(sel == null) return;
        int idx = canvas.objects.indexOf(sel); int newIdx = idx + dir;
        if(newIdx >= 0 && newIdx < canvas.objects.size()) {
            undoManager.saveState(canvas.objects);
            Collections.swap(canvas.objects, idx, newIdx);
            refreshLayers(); updateLayerSelection(); canvas.repaint();
        }
    }
    private GraphObject getSelectedObject() { for(GraphObject o : canvas.objects) if(o.isSelected) return o; return null; }
    private void renameSelected() {
        GraphObject sel = getSelectedObject();
        if(sel != null) {
            String n = JOptionPane.showInputDialog(this, "Rename:", sel.name);
            if(n!=null) { undoManager.saveState(canvas.objects); sel.name=n; objManagerPnl.repaint(); }
        }
    }
    private void deleteSelected() {
        ArrayList<GraphObject> rem = new ArrayList<>();
        for(GraphObject o : canvas.objects) if(o.isSelected) rem.add(o);
        if(!rem.isEmpty()) {
            undoManager.saveState(canvas.objects);
            canvas.objects.removeAll(rem);
            canvas.repaint(); refreshLayers();
        }
    }
    private double snap(double v) { return snapToGrid ? Math.round(v/GRID_SIZE)*GRID_SIZE : v; }

    private void saveGraph() {
        File f = showNativeSaveDialog();
        if (f == null) return;
        if (!f.getName().toLowerCase().endsWith(".graph")) f = new File(f.getParent(), f.getName() + ".graph");
        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(new java.io.FileOutputStream(f))) {
            out.writeObject(canvas.objects);
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
    }
    private void loadGraph() {
        File f = showNativeLoadDialog();
        if (f == null) return;
        try (java.io.ObjectInputStream in = new java.io.ObjectInputStream(new java.io.FileInputStream(f))) {
            ArrayList<GraphObject> loaded = (ArrayList<GraphObject>) in.readObject();
            for (GraphObject o : loaded) o.rebuildShape();
            canvas.objects = loaded;
            undoManager = new UndoManager(); refreshLayers(); canvas.repaint();
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
    }
    private void exportImage() {
        // 1. Deselect everything temporarily for a clean image
        ArrayList<GraphObject> selectedCache = new ArrayList<>();
        for (GraphObject o : canvas.objects) {
            if (o.isSelected) {
                selectedCache.add(o);
                o.isSelected = false;
            }
        }

        // 2. Create the image
        BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();

        // 3. Paint the canvas onto the image
        canvas.paint(g2);
        g2.dispose();

        // 4. Restore selection
        for (GraphObject o : selectedCache) o.isSelected = true;

        // 5. Save Dialog
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Image");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG Image", "png"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getParentFile(), file.getName() + ".png");
            }
            try {
                ImageIO.write(image, "png", file);
                JOptionPane.showMessageDialog(this, "Image exported successfully!");
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error exporting image: " + ex.getMessage());
            }
        }

        canvas.repaint(); // Refresh to show selection again
    }

    private File showNativeLoadDialog() { FileDialog fd = new FileDialog(this, "Load", FileDialog.LOAD); fd.setFile("*.graph"); fd.setVisible(true); return (fd.getFile() == null) ? null : new File(fd.getDirectory(), fd.getFile()); }
    private File showNativeSaveDialog() { FileDialog fd = new FileDialog(this, "Save", FileDialog.SAVE); fd.setFile("Untitled.graph"); fd.setVisible(true); return (fd.getFile() == null) ? null : new File(fd.getDirectory(), fd.getFile()); }

    private void setupContextMenu() {
        contextMenu = new JPopupMenu();
        JMenuItem ren = new JMenuItem("Rename"); ren.addActionListener(e->renameSelected());
        JMenuItem del = new JMenuItem("Delete"); del.addActionListener(e->deleteSelected());
        contextMenu.add(ren); contextMenu.addSeparator(); contextMenu.add(del);
    }
    private void setupMenus() {
        // --- FILE MENU ---
        JMenuItem save = new JMenuItem("Save");
        save.addActionListener(e -> saveGraph());

        JMenuItem load = new JMenuItem("Load");
        load.addActionListener(e -> loadGraph());

        JMenuItem export = new JMenuItem("Export PNG"); // <--- NEW BUTTON
        export.addActionListener(e -> exportImage());   // <--- NEW ACTION

        jMenu1.add(save);
        jMenu1.add(load);
        jMenu1.addSeparator();
        jMenu1.add(export); // Add to menu

        // --- EDIT MENU ---
        JMenuItem undo = new JMenuItem("Undo");
        undo.addActionListener(e -> {
            ArrayList<GraphObject> s = undoManager.undo(canvas.objects);
            if(s != null) { canvas.objects = s; canvas.repaint(); refreshLayers(); }
        });

        JMenuItem redo = new JMenuItem("Redo");
        redo.addActionListener(e -> {
            ArrayList<GraphObject> s = undoManager.redo(canvas.objects);
            if(s != null) { canvas.objects = s; canvas.repaint(); refreshLayers(); }
        });

        jMenu2.add(undo);
        jMenu2.add(redo);
    }
    private void setupKeyboardShortcuts() {
        KeyStroke delKey = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(delKey, "del");
        getRootPane().getActionMap().put("del", new AbstractAction() { public void actionPerformed(ActionEvent e) { deleteSelected(); }});
    }


    @SuppressWarnings("unchecked")
    private void initComponents() {

        jToolBar1 = new JToolBar();
        jPanel1 = new JPanel();
        jSplitPane1 = new JSplitPane();
        leftPnl = new JPanel();
        jPanel7 = new JPanel();
        zoomOutBtn = new JButton();
        zoomSlider = new JSlider();
        zoomInBtn = new JButton();
        canvasPnl = new JPanel();
        jSplitPane2 = new JSplitPane();
        jScrollPane1 = new JScrollPane();
        controlPnl = new JPanel();
        jPanel5 = new JPanel();
        toolLbl = new JLabel();
        toolPnl = new JPanel();
        panBtn = new JButton();
        selectBtn = new JButton();
        pointBtn = new JButton();
        rectBtn = new JButton();
        lineBtn = new JButton();
        circeBtn = new JButton();
        polygonBtn = new JButton();
        jPanel6 = new JPanel();
        editLbl = new JLabel();
        editPnl = new JPanel();
        jScrollPane2 = new JScrollPane();
        objManagerPnl = new JPanel();
        jMenuBar1 = new JMenuBar();
        jMenu1 = new JMenu();
        jMenu2 = new JMenu();

        jToolBar1.setRollover(true);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1100, 750));

        jPanel1.setLayout(new BorderLayout());

        jSplitPane1.setDividerLocation(220);
        jSplitPane1.setMinimumSize(new Dimension(200, 100));

        leftPnl.setLayout(new BorderLayout());

        jPanel7.setBackground(new Color(255, 255, 255));
        jPanel7.setMaximumSize(new Dimension(32767, 40));
        jPanel7.setPreferredSize(new Dimension(500, 30));
        jPanel7.setLayout(new FlowLayout(FlowLayout.RIGHT, 3, 5));

        zoomOutBtn.setBackground(new Color(255, 255, 255));
        zoomOutBtn.setBorder(null);
        zoomOutBtn.setBorderPainted(false);
        zoomOutBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        zoomOutBtn.setPreferredSize(new Dimension(20, 20));
        zoomOutBtn.addActionListener(evt -> zoomOutBtnActionPerformed(evt));
        jPanel7.add(zoomOutBtn);

        zoomSlider.setMaximum(300);
        zoomSlider.setMinimum(10);
        zoomSlider.setCursor(new Cursor(Cursor.HAND_CURSOR));
        jPanel7.add(zoomSlider);

        zoomInBtn.setBackground(new Color(255, 255, 255));
        zoomInBtn.setBorder(null);
        zoomInBtn.setBorderPainted(false);
        zoomInBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        zoomInBtn.setPreferredSize(new Dimension(20, 20));
        zoomInBtn.addActionListener(evt -> zoomInBtnActionPerformed(evt));
        jPanel7.add(zoomInBtn);

        leftPnl.add(jPanel7, BorderLayout.SOUTH);

        canvasPnl.setLayout(new BorderLayout());
        leftPnl.add(canvasPnl, BorderLayout.CENTER);

        jSplitPane1.setRightComponent(leftPnl);

        jSplitPane2.setDividerLocation(300);
        jSplitPane2.setOrientation(JSplitPane.VERTICAL_SPLIT);

        jScrollPane1.setPreferredSize(new Dimension(200, 40));
        jScrollPane1.setBorder(null);
        jScrollPane1.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        jScrollPane1.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        jScrollPane1.getVerticalScrollBar().setUnitIncrement(5);
        jScrollPane1.getVerticalScrollBar().setUI(new BasicScrollBarUI(){
            @Override protected void configureScrollBarColors() { this.thumbColor = Color.GRAY; this.trackColor = Color.LIGHT_GRAY; }
            @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(thumbColor); g2.fillRoundRect(r.x, r.y, 10, r.height, 10, 10); g2.dispose();
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return new CustomScrollButton(orientation);
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return new CustomScrollButton(orientation);
            }
        });

        controlPnl.setBackground(new Color(255, 255, 255));
        controlPnl.setMinimumSize(new Dimension(200, 40));
        controlPnl.setLayout(new BoxLayout(controlPnl, BoxLayout.Y_AXIS));

        jPanel5.setBackground(new Color(255, 255, 255));
        jPanel5.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 5));
        toolLbl.setText("Tools");
        jPanel5.add(toolLbl);
        controlPnl.add(jPanel5);

        toolPnl.setBackground(new Color(255, 255, 255));
        toolPnl.setMaximumSize(new Dimension(20000, 100));
        toolPnl.setMinimumSize(new Dimension(40, 40));
        toolPnl.setLayout(new WrapLayout(FlowLayout.LEFT, 10, 10));

        panBtn.setPreferredSize(new Dimension(30, 30)); toolPnl.add(panBtn);
        selectBtn.setPreferredSize(new Dimension(30, 30)); toolPnl.add(selectBtn);
        pointBtn.setPreferredSize(new Dimension(30, 30)); toolPnl.add(pointBtn);
        rectBtn.setPreferredSize(new Dimension(30, 30)); toolPnl.add(rectBtn);
        lineBtn.setPreferredSize(new Dimension(30, 30)); toolPnl.add(lineBtn);
        circeBtn.setPreferredSize(new Dimension(30, 30)); toolPnl.add(circeBtn);
        polygonBtn.setPreferredSize(new Dimension(30, 30)); toolPnl.add(polygonBtn);
        controlPnl.add(toolPnl);

        jPanel6.setBackground(new Color(255, 255, 255));
        jPanel6.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 5));
        editLbl.setText("Style / Edit");
        jPanel6.add(editLbl);
        controlPnl.add(jPanel6);

        editPnl.setBackground(new Color(255, 255, 255));
        editPnl.setLayout(new WrapLayout(FlowLayout.LEFT, 10, 10));
        polygonBtn.setPreferredSize(new Dimension(30, 30));
        controlPnl.add(editPnl);

        jScrollPane1.setViewportView(controlPnl);
        jSplitPane2.setLeftComponent(jScrollPane1);

        jScrollPane2.setViewportView(objManagerPnl);
        jSplitPane2.setRightComponent(jScrollPane2);
        jScrollPane2.setBorder(null);
        jScrollPane2.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        jScrollPane2.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        jScrollPane2.getVerticalScrollBar().setUnitIncrement(5);
        jScrollPane2.getVerticalScrollBar().setUI(new BasicScrollBarUI(){
            @Override protected void configureScrollBarColors() { this.thumbColor = Color.GRAY; this.trackColor = Color.LIGHT_GRAY; }
            @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(thumbColor); g2.fillRoundRect(r.x, r.y, 10, r.height, 10, 10); g2.dispose();
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return new CustomScrollButton(orientation);
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return new CustomScrollButton(orientation);
            }
        });


        jSplitPane1.setLeftComponent(jSplitPane2);
        jPanel1.add(jSplitPane1, BorderLayout.CENTER);
        getContentPane().add(jPanel1, BorderLayout.CENTER);

        jMenu1.setText("File"); jMenuBar1.add(jMenu1);
        jMenu2.setText("Edit"); jMenuBar1.add(jMenu2);
        setJMenuBar(jMenuBar1);

        pack();
    }


    private void zoomInBtnActionPerformed(ActionEvent evt) { zoomSlider.setValue(zoomSlider.getValue() + 10); }
    private void zoomOutBtnActionPerformed(ActionEvent evt) { zoomSlider.setValue(zoomSlider.getValue() - 10); }
    private void panBtnActionPerformed(ActionEvent evt) {}
    private void pointBtnActionPerformed(ActionEvent evt) {}
    private void circeBtnActionPerformed(ActionEvent evt) {}
    private void selectBtnActionPerformed(ActionEvent evt) {}
    private void rectBtnActionPerformed(ActionEvent evt) {}
    private void lineBtnActionPerformed(ActionEvent evt) {}
    private void polygonBtnActionPerformed(ActionEvent evt) {}


    class UndoManager {
        Stack<ArrayList<GraphObject>> u = new Stack<>(), r = new Stack<>();
        public void saveState(ArrayList<GraphObject> c) { ArrayList<GraphObject> s = new ArrayList<>(); for(GraphObject o : c) s.add(o.copy()); u.push(s); r.clear(); }
        public ArrayList<GraphObject> undo(ArrayList<GraphObject> c) { if(u.isEmpty()) return null; ArrayList<GraphObject> s = new ArrayList<>(); for(GraphObject o : c) s.add(o.copy()); r.push(s); return u.pop(); }
        public ArrayList<GraphObject> redo(ArrayList<GraphObject> c) { if(r.isEmpty()) return null; ArrayList<GraphObject> s = new ArrayList<>(); for(GraphObject o : c) s.add(o.copy()); u.push(s); return r.pop(); }
    }

    class GraphCanvas extends JComponent {
        double translateX = 0, translateY = 0, scale = 1.0;
        ArrayList<GraphObject> objects = new ArrayList<>();
        GraphObject tempObject = null;
        Rectangle selectionRect = null;
        public GraphCanvas() { setBackground(Color.WHITE); }
        public AffineTransform getTransform() { AffineTransform at = new AffineTransform(); at.translate(translateX, translateY); at.scale(scale, scale); return at; }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g); Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            AffineTransform sys = g2d.getTransform();
            g2d.setTransform(new AffineTransform()); g2d.setColor(Color.WHITE); g2d.fillRect(0, 0, 2000, 2000); g2d.setTransform(sys);
            g2d.transform(getTransform());
            // Grid
            g2d.setColor(new Color(235, 235, 235)); g2d.setStroke(new BasicStroke(1));
            for (int x = -10000; x <= 10000; x += 50) g2d.drawLine(x, -10000, x, 10000);
            for (int y = -10000; y <= 10000; y += 50) g2d.drawLine(-10000, y, 10000, y);
            g2d.setColor(new Color(200, 200, 200)); g2d.drawLine(-10000, 0, 10000, 0); g2d.drawLine(0, -10000, 0, 10000);
            for(GraphObject o : objects) o.draw(g2d);
            if(tempObject != null) tempObject.draw(g2d);
            if(selectionRect != null) { g2d.setTransform(sys); g2d.setColor(new Color(0, 120, 255, 50)); g2d.fill(selectionRect); g2d.setColor(new Color(0, 120, 255)); g2d.draw(selectionRect); }
            g2d.setTransform(sys);
        }
    }

    enum Handle { TL, T, TR, R, BR, B, BL, L, P1, P2, RADIUS, VERTEX_0, VERTEX_1, VERTEX_2, VERTEX_3, VERTEX_4, VERTEX_5, VERTEX_6, VERTEX_7, VERTEX_8, VERTEX_9, NONE }

    static abstract class GraphObject implements Serializable {
        private static final long serialVersionUID = 1L;
        boolean isSelected = false; Color color; float strokeWidth; String name;
        public GraphObject(Color c, float s, String n) { this.color=c; this.strokeWidth=s; this.name=n; }
        abstract void drawShape(Graphics2D g2d); abstract boolean contains(Point2D p); abstract void move(double dx, double dy); abstract GraphObject copy(); abstract Shape getShape(); abstract void rebuildShape();
        abstract Handle getHandleAt(Point2D p); abstract void resize(Handle h, double dx, double dy); abstract void drawHandles(Graphics2D g2d);
        void draw(Graphics2D g2d) {
            g2d.setColor(color); g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); drawShape(g2d);
            if(isSelected) { g2d.setColor(new Color(50, 150, 255)); g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5}, 0)); drawShape(g2d); drawHandles(g2d); }
        }
        void drawHandle(Graphics2D g2d, double x, double y) { double s = 6.0; g2d.setColor(Color.WHITE); g2d.fill(new Rectangle2D.Double(x-s/2, y-s/2, s, s)); g2d.setColor(Color.BLACK); g2d.setStroke(new BasicStroke(1)); g2d.draw(new Rectangle2D.Double(x-s/2, y-s/2, s, s)); }
    }
    static class GRectangle extends GraphObject {
        transient Rectangle2D.Double rect; double x, y, w, h;
        public GRectangle(double x, double y, double w, double h, Color c, float s) { super(c, s, "Rectangle"); this.x=x; this.y=y; this.w=w; this.h=h; rebuildShape(); }
        void rebuildShape() { rect = new Rectangle2D.Double(x, y, w, h); }
        void drawShape(Graphics2D g2d) { g2d.draw(rect); } boolean contains(Point2D p) { return rect.contains(p); }
        void move(double dx, double dy) { rect.x+=dx; rect.y+=dy; x=rect.x; y=rect.y; } Shape getShape() { return rect; }
        GraphObject copy() { GRectangle o = new GRectangle(x, y, w, h, color, strokeWidth); o.isSelected=isSelected; o.name=name; return o; }
        void drawHandles(Graphics2D g) { drawHandle(g, x, y); drawHandle(g, x+w, y); drawHandle(g, x+w, y+h); drawHandle(g, x, y+h); }
        Handle getHandleAt(Point2D p) { if(p.distance(x,y)<4) return Handle.TL; if(p.distance(x+w,y+h)<4) return Handle.BR; return Handle.NONE; }
        void resize(Handle handle, double dx, double dy) { if(handle==Handle.BR){ w+=dx; h+=dy; } else if(handle==Handle.TL){ x+=dx; y+=dy; w-=dx; h-=dy; } rebuildShape(); }
    }
    static class GLine extends GraphObject {
        transient Line2D.Double line; double x1, y1, x2, y2;
        public GLine(Point2D s, Point2D e, Color c, float sw) { super(c, sw, "Line"); x1=s.getX(); y1=s.getY(); x2=e.getX(); y2=e.getY(); rebuildShape(); }
        void rebuildShape() { line = new Line2D.Double(x1, y1, x2, y2); }
        void drawShape(Graphics2D g2d) { g2d.draw(line); } boolean contains(Point2D p) { return line.ptSegDist(p)<5; }
        void move(double dx, double dy) { line.x1+=dx; line.y1+=dy; line.x2+=dx; line.y2+=dy; x1=line.x1; y1=line.y1; x2=line.x2; y2=line.y2; }
        Shape getShape() { return line; } GraphObject copy() { GLine o = new GLine(new Point2D.Double(x1,y1), new Point2D.Double(x2,y2), color, strokeWidth); o.isSelected=isSelected; o.name=name; return o; }
        void drawHandles(Graphics2D g) { drawHandle(g, x1, y1); drawHandle(g, x2, y2); }
        Handle getHandleAt(Point2D p) { if(p.distance(x1, y1)<5) return Handle.P1; if(p.distance(x2, y2)<5) return Handle.P2; return Handle.NONE; }
        void resize(Handle h, double dx, double dy) { if(h==Handle.P1){x1+=dx; y1+=dy;} if(h==Handle.P2){x2+=dx; y2+=dy;} rebuildShape(); }
    }
    static class GCircle extends GraphObject {
        double x, y, r;
        public GCircle(double x, double y, double r, Color c, float s) { super(c, s, "Circle"); this.x=x;this.y=y;this.r=r; }
        void rebuildShape() {} void drawShape(Graphics2D g2d) { g2d.draw(new Ellipse2D.Double(x-r, y-r, r*2, r*2)); }
        boolean contains(Point2D p) { return p.distance(x, y)<=r; } void move(double dx, double dy) { x+=dx; y+=dy; } Shape getShape() { return new Ellipse2D.Double(x-r, y-r, r*2, r*2); }
        GraphObject copy() { GCircle o = new GCircle(x, y, r, color, strokeWidth); o.isSelected=isSelected; o.name=name; return o; }
        void drawHandles(Graphics2D g) { drawHandle(g, x+r, y); } Handle getHandleAt(Point2D p) { if(p.distance(x+r, y)<6) return Handle.RADIUS; return Handle.NONE; }
        void resize(Handle h, double dx, double dy) { if(h==Handle.RADIUS){ r+=dx; if(r<2)r=2; } }
    }
    static class GPolygon extends GraphObject {
        transient Path2D.Double path; ArrayList<Double> xp=new ArrayList<>(), yp=new ArrayList<>(); transient ArrayList<Point2D.Double> pts;
        public GPolygon(Path2D.Double p, Color c, float s) { super(c, s, "Polygon"); this.path=p; sync(); }
        void sync() { xp.clear(); yp.clear(); PathIterator pi=path.getPathIterator(null); double[] c=new double[6]; while(!pi.isDone()){ if(pi.currentSegment(c)!=PathIterator.SEG_CLOSE){xp.add(c[0]); yp.add(c[1]);} pi.next(); } init(); }
        void init() { pts=new ArrayList<>(); for(int i=0; i<xp.size(); i++) pts.add(new Point2D.Double(xp.get(i), yp.get(i))); }
        void rebuildShape() { if(xp==null)return; init(); rebuildPath(); }
        void rebuildPath() { path=new Path2D.Double(); if(pts.size()>0){ path.moveTo(pts.get(0).x, pts.get(0).y); for(int i=1; i<pts.size(); i++) path.lineTo(pts.get(i).x, pts.get(i).y); path.closePath(); } xp.clear(); yp.clear(); for(Point2D.Double p:pts){xp.add(p.x); yp.add(p.y);} }
        void drawShape(Graphics2D g2d) { if(path==null) rebuildShape(); g2d.draw(path); } boolean contains(Point2D p) { if(path==null) rebuildShape(); return path.contains(p); }
        void move(double dx, double dy) { for(Point2D.Double p:pts){p.x+=dx; p.y+=dy;} rebuildPath(); } Shape getShape() { if(path==null)rebuildShape(); return path; }
        GraphObject copy() { GPolygon o = new GPolygon((Path2D.Double)path.clone(), color, strokeWidth); o.isSelected=isSelected; o.name=name; return o; }
        void drawHandles(Graphics2D g) { for(Point2D.Double p:pts) drawHandle(g, p.x, p.y); } Handle getHandleAt(Point2D p) { for(int i=0; i<pts.size(); i++) if(p.distance(pts.get(i))<6) return Handle.values()[Handle.VERTEX_0.ordinal()+i]; return Handle.NONE; }
        void resize(Handle h, double dx, double dy) { int i=h.ordinal()-Handle.VERTEX_0.ordinal(); if(i>=0 && i<pts.size()){ pts.get(i).x+=dx; pts.get(i).y+=dy; rebuildPath(); } }
    }
    static class GPoint extends GraphObject {
        double x, y; public GPoint(double x, double y, String l) { super(Color.RED, 2, l); this.x=x;this.y=y; } void rebuildShape(){}
        void drawShape(Graphics2D g2d) { AffineTransform t=g2d.getTransform(); g2d.translate(x,y); g2d.setColor(isSelected?Color.BLUE:Color.RED); g2d.drawLine(-4,-4,4,4); g2d.drawLine(-4,4,4,-4); g2d.setColor(Color.BLACK); g2d.drawString(name,6,-6); g2d.setTransform(t); }
        boolean contains(Point2D p) { return p.distance(x,y)<8; } void move(double dx, double dy) { x+=dx; y+=dy; } Shape getShape() { return new Rectangle2D.Double(x-4, y-4, 8, 8); }
        GraphObject copy() { GPoint o = new GPoint(x, y, name); o.isSelected=isSelected; return o; } void drawHandles(Graphics2D g){} Handle getHandleAt(Point2D p){return Handle.NONE;} void resize(Handle h, double dx, double dy){}
    }

    class PanningHandler extends MouseAdapter {
        int lastX, lastY; public void mousePressed(MouseEvent e) { if(currentMode==Mode.PAN || SwingUtilities.isMiddleMouseButton(e)) { lastX=e.getX(); lastY=e.getY(); }}
        public void mouseDragged(MouseEvent e) { if(currentMode==Mode.PAN || SwingUtilities.isMiddleMouseButton(e)) { canvas.translateX+=e.getX()-lastX; canvas.translateY+=e.getY()-lastY; lastX=e.getX(); lastY=e.getY(); canvas.repaint(); }}
    }
    class ScaleHandler implements MouseWheelListener {
        public void mouseWheelMoved(MouseWheelEvent e) {
            double wx = (e.getX()-canvas.translateX)/canvas.scale, wy = (e.getY()-canvas.translateY)/canvas.scale; double f = (e.getWheelRotation()<0) ? 1.1 : 0.9;
            canvas.scale = Math.max(0.1, Math.min(canvas.scale, 5.0)) * f; canvas.translateX = e.getX()-(wx*canvas.scale); canvas.translateY = e.getY()-(wy*canvas.scale); zoomSlider.setValue((int)(canvas.scale*100)); canvas.repaint();
        }
    }
    class DrawingHandler extends MouseAdapter {
        Point2D start; private GPoint findSnap(Point2D p) { for(int i=canvas.objects.size()-1; i>=0; i--) if(canvas.objects.get(i) instanceof GPoint && canvas.objects.get(i).contains(p)) return (GPoint)canvas.objects.get(i); return null; }
        public void mousePressed(MouseEvent e) {
            if(currentMode==Mode.PAN || currentMode==Mode.SELECT) return;
            try { Point2D raw = canvas.getTransform().inverseTransform(e.getPoint(), null); start = new Point2D.Double(snap(raw.getX()), snap(raw.getY()));
                if(currentMode==Mode.LINE) { GPoint s=findSnap(raw); if(s!=null) start=new Point2D.Double(s.x, s.y); }
                else if(currentMode==Mode.POINT) { undoManager.saveState(canvas.objects); canvas.objects.add(new GPoint(start.getX(), start.getY(), "P"+canvas.objects.size())); canvas.repaint(); refreshLayers(); }
                else if(currentMode==Mode.POLYGON) { undoManager.saveState(canvas.objects); createPoly(start.getX(), start.getY()); canvas.repaint(); refreshLayers(); }
            } catch(Exception ex){}
        }
        public void mouseDragged(MouseEvent e) {
            if(currentMode==Mode.PAN || currentMode==Mode.SELECT || currentMode==Mode.POINT || currentMode==Mode.POLYGON) return;
            try { Point2D raw = canvas.getTransform().inverseTransform(e.getPoint(), null); Point2D end = new Point2D.Double(snap(raw.getX()), snap(raw.getY()));
                if(currentMode==Mode.RECTANGLE) { double x=Math.min(start.getX(), end.getX()), y=Math.min(start.getY(), end.getY()); canvas.tempObject = new GRectangle(x, y, Math.abs(start.getX()-end.getX()), Math.abs(start.getY()-end.getY()), currentColor, currentStroke); }
                else if(currentMode==Mode.LINE) { GPoint s=findSnap(raw); if(s!=null) end=new Point2D.Double(s.x, s.y); canvas.tempObject = new GLine(start, end, currentColor, currentStroke); }
                else if(currentMode==Mode.CIRCLE) { canvas.tempObject = new GCircle(start.getX(), start.getY(), start.distance(end), currentColor, currentStroke); }
                canvas.repaint();
            } catch(Exception ex){}
        }
        public void mouseReleased(MouseEvent e) {
            if(canvas.tempObject != null) { undoManager.saveState(canvas.objects); if(currentMode==Mode.LINE && canvas.tempObject instanceof GLine) { try { Point2D r=canvas.getTransform().inverseTransform(e.getPoint(), null); GPoint s=findSnap(r); if(s!=null) ((GLine)canvas.tempObject).line.setLine(((GLine)canvas.tempObject).line.getP1(), new Point2D.Double(s.x, s.y)); }catch(Exception ex){}} canvas.objects.add(canvas.tempObject); canvas.tempObject=null; canvas.repaint(); refreshLayers(); }
        }
        private void createPoly(double cx, double cy) { try { int s = Integer.parseInt(JOptionPane.showInputDialog("Sides:")); double len = Double.parseDouble(JOptionPane.showInputDialog("Length:")); if(s<3)return; double r=len/(2*Math.sin(Math.PI/s)); Path2D.Double p=new Path2D.Double(); for(int i=0; i<s; i++) { double t=2*Math.PI*i/s-Math.PI/2; double px=cx+r*Math.cos(t), py=cy+r*Math.sin(t); if(i==0)p.moveTo(px,py); else p.lineTo(px,py); } p.closePath(); canvas.objects.add(new GPolygon(p, currentColor, currentStroke)); } catch(Exception ex){} }
    }
    class SelectHandler extends MouseAdapter {
        GraphObject targetObj; Point2D lastWorldPos; Point screenStart; boolean isDraggingObj=false, isMarquee=false, isResizing=false; Handle activeHandle=Handle.NONE;
        public void mousePressed(MouseEvent e) {
            if(currentMode!=Mode.SELECT) return;
            if(SwingUtilities.isRightMouseButton(e)) { handleRightClick(e); return; }
            try { screenStart = e.getPoint(); Point2D raw = canvas.getTransform().inverseTransform(e.getPoint(), null); Point2D clickP = raw;
                for(GraphObject o : canvas.objects) { if(o.isSelected) { Handle h=o.getHandleAt(clickP); if(h!=Handle.NONE){targetObj=o; activeHandle=h; isResizing=true; lastWorldPos=new Point2D.Double(snap(clickP.getX()), snap(clickP.getY())); return; }}}
                GraphObject clicked = null; for(int i=canvas.objects.size()-1; i>=0; i--) if(canvas.objects.get(i).contains(clickP)) { clicked=canvas.objects.get(i); break; }
                if(clicked!=null) { targetObj=clicked; isDraggingObj=true; lastWorldPos=new Point2D.Double(snap(clickP.getX()), snap(clickP.getY())); if(!e.isShiftDown()&&!targetObj.isSelected)for(GraphObject o:canvas.objects)o.isSelected=false; targetObj.isSelected=true; }
                else { if(!e.isShiftDown())for(GraphObject o:canvas.objects)o.isSelected=false; isMarquee=true; canvas.selectionRect=new Rectangle(e.getX(), e.getY(), 0, 0); }
                canvas.repaint(); updateLayerSelection();
            } catch(Exception ex){}
        }
        public void mouseDragged(MouseEvent e) {
            if(currentMode!=Mode.SELECT) return;
            try { Point2D raw = canvas.getTransform().inverseTransform(e.getPoint(), null); Point2D cur = new Point2D.Double(snap(raw.getX()), snap(raw.getY()));
                if(isResizing && targetObj!=null) { double dx=cur.getX()-lastWorldPos.getX(), dy=cur.getY()-lastWorldPos.getY(); if(dx!=0||dy!=0){ targetObj.resize(activeHandle, dx, dy); lastWorldPos=cur; canvas.repaint(); } }
                else if(isDraggingObj && targetObj!=null) { double dx=cur.getX()-lastWorldPos.getX(), dy=cur.getY()-lastWorldPos.getY(); if(dx!=0||dy!=0){ for(GraphObject o:canvas.objects)if(o.isSelected)o.move(dx, dy); lastWorldPos=cur; canvas.repaint(); } }
                else if(isMarquee) { int x=Math.min(screenStart.x, e.getX()), y=Math.min(screenStart.y, e.getY()); canvas.selectionRect=new Rectangle(x, y, Math.abs(screenStart.x-e.getX()), Math.abs(screenStart.y-e.getY())); AffineTransform at=canvas.getTransform(); for(GraphObject o:canvas.objects)if(at.createTransformedShape(o.getShape()).intersects(x, y, canvas.selectionRect.width, canvas.selectionRect.height))o.isSelected=true; canvas.repaint(); }
            } catch(Exception ex){}
        }
        public void mouseReleased(MouseEvent e) { if(isDraggingObj||isResizing)undoManager.saveState(canvas.objects); if(isMarquee){canvas.selectionRect=null; canvas.repaint(); updateLayerSelection();} isDraggingObj=false; isMarquee=false; isResizing=false; }
        private void handleRightClick(MouseEvent e) { try { Point2D p=canvas.getTransform().inverseTransform(e.getPoint(), null); GraphObject c=null; for(int i=canvas.objects.size()-1; i>=0; i--) if(canvas.objects.get(i).contains(p)){c=canvas.objects.get(i); break;} if(c!=null){ if(!c.isSelected){for(GraphObject o:canvas.objects)o.isSelected=false; c.isSelected=true;} canvas.repaint(); updateLayerSelection(); contextMenu.show(canvas, e.getX(), e.getY()); } else { for(GraphObject o:canvas.objects)o.isSelected=false; canvas.repaint(); updateLayerSelection(); } }catch(Exception ex){} }
    }
    // Variables declaration - do not modify
    private JPanel canvasPnl;
    private JButton panBtn;
    private JButton pointBtn;
    private JButton polygonBtn;
    private JButton circeBtn;
    private JButton selectBtn;
    private JButton rectBtn;
    private JButton lineBtn;
    private JLabel toolLbl;
    private JLabel editLbl;
    private JMenu jMenu1;
    private JMenu jMenu2;
    private JMenuBar jMenuBar1;
    private JPanel jPanel1;
    private JPanel controlPnl;
    private JPanel toolPnl;
    private JPanel jPanel5;
    private JPanel jPanel6;
    private JPanel jPanel7;
    private JPanel editPnl;
    private JScrollPane jScrollPane1;
    private JScrollPane jScrollPane2;
    private JSplitPane jSplitPane1;
    private JSplitPane jSplitPane2;
    private JToolBar jToolBar1;
    private JPanel leftPnl;
    private JPanel objManagerPnl;
    private JButton zoomInBtn;
    private JButton zoomOutBtn;
    private JSlider zoomSlider;
    // End of variables declaration
}