package com.simulation;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.Random;

public class LightPolarizerApp extends JFrame {
    
    // Theme colors
    private static final Color BG_COLOR = new Color(12, 15, 22);
    private static final Color PANEL_BG = new Color(20, 24, 36, 166); // Translucent
    private static final Color PANEL_BORDER = new Color(255, 255, 255, 25);
    private static final Color TEXT_MAIN = Color.WHITE;
    private static final Color TEXT_MUTED = new Color(142, 149, 170);
    private static final Color PRIMARY_COLOR = new Color(0, 229, 255);
    private static final Color ACTIVE_BG = new Color(0, 229, 255, 38);
    
    private static final Color COLOR_INCIDENT = new Color(255, 0, 255);
    private static final Color COLOR_POLARIZED = new Color(0, 255, 255);
    private static final Color COLOR_ANALYZED = new Color(255, 170, 0);

    // Simulation states
    private String mode = "linear"; // "linear", "circular", "unpolarized"
    private boolean useAnalyzer = false;
    private double speed = 1.0;
    private double frequency = 3.0;
    private double rotation1 = 0.0; // in degrees
    private double rotation2 = 90.0; // in degrees

    private long startTime;
    private double timeOffset = 0.0;
    private long lastTimeMillis;

    // Simulation settings & math constants
    private final double startX = -7.0;
    private final double endX = 7.0;
    private final double resolution = 0.05;
    private final double p1Path = -1.0;
    private final double p2Path = 3.5;
    private final double baseAmp = 1.7;

    // 3D Camera / OrbitControls state
    private double yaw = 0.45; // rotation around Y axis
    private double pitch = 0.25; // rotation around X axis
    private double cameraDistance = 15.0;
    private Point lastMousePos;

    // Pre-generated random properties for unpolarized light
    private static class UnpolRandom {
        double ry, rz, ay, az;
        UnpolRandom(Random rand) {
            ry = rand.nextDouble() * 2 * Math.PI;
            rz = rand.nextDouble() * 2 * Math.PI;
            ay = 0.3 + rand.nextDouble() * 0.7;
            az = 0.3 + rand.nextDouble() * 0.7;
        }
    }
    private UnpolRandom[] unpolRandoms;

    // UI elements that need references
    private StyledButton btnLinear;
    private StyledButton btnCircular;
    private StyledButton btnUnpolarized;
    private JCheckBox toggleAnalyzer;
    private JPanel analyzerControlsContainer;
    private JSlider sliderRot2;
    private JLabel lblRot2Value;

    public LightPolarizerApp() {
        super("Light Polarizer Simulation");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_COLOR);
        
        // Initialize math variables
        Random rand = new Random(42);
        unpolRandoms = new UnpolRandom[1000];
        for (int i = 0; i < unpolRandoms.length; i++) {
            unpolRandoms[i] = new UnpolRandom(rand);
        }
        startTime = System.currentTimeMillis();
        lastTimeMillis = startTime;

        // Create main layout
        setLayout(new BorderLayout());

        // Simulation rendering panel
        SimulationPanel simPanel = new SimulationPanel();
        add(simPanel, BorderLayout.CENTER);

        // Control Panel (Right side)
        JPanel rightPanel = createControlPanel();
        add(rightPanel, BorderLayout.EAST);

        // Render loop Timer (~60 FPS)
        Timer timer = new Timer(16, e -> {
            long now = System.currentTimeMillis();
            double dt = (now - lastTimeMillis) / 1000.0;
            lastTimeMillis = now;
            timeOffset += dt * speed;
            simPanel.repaint();
        });
        timer.start();
    }

    // Helper 3D vector class
    private static class Vec3 {
        double x, y, z;
        Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    // Custom 3D Projection panel
    private class SimulationPanel extends JPanel {
        public SimulationPanel() {
            setBackground(BG_COLOR);
            setDoubleBuffered(true);

            // Setup OrbitControls mouse events
            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    lastMousePos = e.getPoint();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (lastMousePos != null) {
                        int dx = e.getX() - lastMousePos.x;
                        int dy = e.getY() - lastMousePos.y;
                        yaw -= dx * 0.007;
                        pitch -= dy * 0.007;
                        // Limit pitch to prevent flipping
                        pitch = Math.max(-Math.PI / 2 + 0.05, Math.min(Math.PI / 2 - 0.05, pitch));
                        lastMousePos = e.getPoint();
                        repaint();
                    }
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    cameraDistance += e.getPreciseWheelRotation() * 0.5;
                    cameraDistance = Math.max(5.0, Math.min(30.0, cameraDistance));
                    repaint();
                }
            };
            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
            addMouseWheelListener(mouseAdapter);
        }

        // Project a 3D coordinate onto the 2D screen plane
        private Point2D project(Vec3 p, int width, int height) {
            // Translate origin to targets (offsetting the camera target point x=1.0)
            double dx = p.x - 1.0;
            double dy = p.y;
            double dz = p.z;

            // Rotate around Y axis (yaw)
            double rx1 = dx * Math.cos(yaw) - dz * Math.sin(yaw);
            double rz1 = dx * Math.sin(yaw) + dz * Math.cos(yaw);

            // Rotate around X axis (pitch)
            double ry2 = dy * Math.cos(pitch) - rz1 * Math.sin(pitch);
            double rz2 = dy * Math.sin(pitch) + rz1 * Math.cos(pitch);

            // Perspective Projection
            double fovScale = Math.min(width, height) * 0.95;
            double sz = rz2 + cameraDistance;

            double sx = width / 2.0 + (rx1 * fovScale) / sz;
            double sy = height / 2.0 - (ry2 * fovScale) / sz;

            return new Point2D.Double(sx, sy);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = getWidth();
            int h = getHeight();

            double t1 = rotation1 * Math.PI / 180.0;
            double t2 = rotation2 * Math.PI / 180.0;

            // Prepare paths to draw
            java.util.List<Point2D> pts1 = new java.util.ArrayList<>();
            java.util.List<Point2D> pts2 = new java.util.ArrayList<>();
            java.util.List<Point2D> pts3 = new java.util.ArrayList<>();

            for (double x = startX; x <= endX + resolution; x += resolution) {
                double phase = (x * frequency) - (timeOffset * 6.0);

                boolean isPath1 = (x <= p1Path + resolution / 2.0);
                boolean isPath2 = useAnalyzer
                        ? (x >= p1Path - resolution / 2.0 && x <= p2Path + resolution / 2.0)
                        : (x >= p1Path - resolution / 2.0);
                boolean isPath3 = useAnalyzer ? (x >= p2Path - resolution / 2.0) : false;

                double y1 = 0, z1 = 0;
                double y2 = 0, z2 = 0;
                double y3 = 0, z3 = 0;

                // Path 1 (Incident Light)
                if (isPath1) {
                    if (mode.equals("linear")) {
                        y1 = Math.sin(phase);
                    } else if (mode.equals("circular")) {
                        y1 = Math.sin(phase);
                        z1 = Math.cos(phase);
                    } else if (mode.equals("unpolarized")) {
                        int idx = (int) (Math.floor(Math.abs((x - timeOffset * 2.0) * 5.0))) % unpolRandoms.length;
                        UnpolRandom r = unpolRandoms[idx];
                        y1 = Math.sin(phase * 1.3 + r.ry) * r.ay;
                        z1 = Math.cos(phase * 1.1 + r.rz) * r.az;
                    }
                    pts1.add(project(new Vec3(x, y1 * baseAmp, z1 * baseAmp), w, h));
                }

                // Mathematical Projection amplitude rules
                double amp1 = 0.0;
                double p1Phase = phase;

                if (mode.equals("linear")) {
                    amp1 = Math.cos(t1);
                } else if (mode.equals("circular")) {
                    amp1 = 1.0 / Math.sqrt(2.0);
                    p1Phase = phase + t1;
                } else if (mode.equals("unpolarized")) {
                    amp1 = 1.0 / Math.sqrt(2.0);
                }

                // Path 2: Between Polarizer 1 and Polarizer 2
                if (isPath2) {
                    y2 = amp1 * Math.sin(p1Phase) * Math.cos(t1);
                    z2 = amp1 * Math.sin(p1Phase) * Math.sin(t1);
                    pts2.add(project(new Vec3(x, y2 * baseAmp, z2 * baseAmp), w, h));
                }

                // Path 3: After Analyzer (Polarizer 2)
                if (isPath3) {
                    double amp2 = amp1 * Math.cos(t2 - t1);
                    y3 = amp2 * Math.sin(p1Phase) * Math.cos(t2);
                    z3 = amp2 * Math.sin(p1Phase) * Math.sin(t2);
                    pts3.add(project(new Vec3(x, y3 * baseAmp, z3 * baseAmp), w, h));
                }
            }

            // Draw objects: standard painters
            // To ensure correct overlapping (since we don't do a full Z-buffer, we just draw things in order or with transparency):
            // Generally Polarizer 1 is at x = -1, Polarizer 2 is at x = 3.5.
            // Let's draw Polarizer 1 first if we are looking from the positive X axis, etc., but a fixed ordering:
            // 1. Draw Polarizer 1
            // 2. Draw Polarizer 2 (if active)
            // 3. Draw Wave paths on top (with glow effect)

            drawPolarizer(g2, p1Path, t1, w, h);
            if (useAnalyzer) {
                drawPolarizer(g2, p2Path, t2, w, h);
            }

            // Draw wave 1 (Incident) - glowing magenta
            drawGlowingPath(g2, pts1, COLOR_INCIDENT);

            // Draw wave 2 (Polarized) - glowing cyan
            drawGlowingPath(g2, pts2, COLOR_POLARIZED);

            // Draw wave 3 (Analyzed) - glowing orange
            if (useAnalyzer) {
                drawGlowingPath(g2, pts3, COLOR_ANALYZED);
            }
        }

        // Draws a polarizer disc in 3D projection
        private void drawPolarizer(Graphics2D g2, double xCenter, double theta, int w, int h) {
            double radius = 2.8;

            // Compute border vertices
            Path2D.Double discPath = new Path2D.Double();
            int segments = 64;
            for (int i = 0; i < segments; i++) {
                double angle = (i * 2.0 * Math.PI) / segments;
                Point2D p = project(new Vec3(xCenter, radius * Math.cos(angle), radius * Math.sin(angle)), w, h);
                if (i == 0) {
                    discPath.moveTo(p.getX(), p.getY());
                } else {
                    discPath.lineTo(p.getX(), p.getY());
                }
            }
            discPath.closePath();

            // Fill Polarizer base (Translucent Dark Blue-grey)
            g2.setColor(new Color(20, 30, 40, 76));
            g2.fill(discPath);

            // Draw Slits (Parallel lines inside the circle, rotated)
            g2.setStroke(new BasicStroke(2.0f));
            g2.setColor(new Color(100, 220, 255, 180));
            for (double zLocal = -radius + 0.2; zLocal <= radius - 0.2; zLocal += 0.24) {
                // Find intersections with boundary: yLocal = sqrt(R^2 - zLocal^2)
                double yMax = Math.sqrt(radius * radius - zLocal * zLocal);
                
                // Endpoints in local space
                double yL1 = -yMax, zL1 = zLocal;
                double yL2 = yMax, zL2 = zLocal;

                // Rotate local coords by theta around X axis
                double yR1 = yL1 * Math.cos(theta) - zL1 * Math.sin(theta);
                double zR1 = yL1 * Math.sin(theta) + zL1 * Math.cos(theta);
                
                double yR2 = yL2 * Math.cos(theta) - zL2 * Math.sin(theta);
                double zR2 = yL2 * Math.sin(theta) + zL2 * Math.cos(theta);

                // Project endpoints
                Point2D pt1 = project(new Vec3(xCenter, yR1, zR1), w, h);
                Point2D pt2 = project(new Vec3(xCenter, yR2, zR2), w, h);

                g2.draw(new Line2D.Double(pt1, pt2));
            }

            // Draw Bolder Central Axis Slit
            g2.setStroke(new BasicStroke(4.0f));
            g2.setColor(new Color(0, 255, 255, 255));
            double yL1 = -radius, zL1 = 0;
            double yL2 = radius, zL2 = 0;
            double yR1 = yL1 * Math.cos(theta) - zL1 * Math.sin(theta);
            double zR1 = yL1 * Math.sin(theta) + zL1 * Math.cos(theta);
            double yR2 = yL2 * Math.cos(theta) - zL2 * Math.sin(theta);
            double zR2 = yL2 * Math.sin(theta) + zL2 * Math.cos(theta);
            Point2D ptAxis1 = project(new Vec3(xCenter, yR1, zR1), w, h);
            Point2D ptAxis2 = project(new Vec3(xCenter, yR2, zR2), w, h);
            g2.draw(new Line2D.Double(ptAxis1, ptAxis2));

            // Draw outer border ring
            g2.setStroke(new BasicStroke(3.0f));
            g2.setColor(new Color(68, 68, 68));
            g2.draw(discPath);
        }

        // Draw path with a layered glow effect (MeshLine equivalent in AWT)
        private void drawGlowingPath(Graphics2D g2, java.util.List<Point2D> pts, Color color) {
            if (pts.size() < 2) return;

            Path2D.Double path = new Path2D.Double();
            path.moveTo(pts.get(0).getX(), pts.get(0).getY());
            for (int i = 1; i < pts.size(); i++) {
                path.lineTo(pts.get(i).getX(), pts.get(i).getY());
            }

            // Outer wide blur glow
            g2.setStroke(new BasicStroke(8.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
            g2.draw(path);

            // Medium core glow
            g2.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 120));
            g2.draw(path);

            // Sharp inner core
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 255));
            g2.draw(path);
        }
    }

    // Create styled Control Panel
    private JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setPreferredSize(new Dimension(340, 800));
        panel.setBackground(BG_COLOR);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, PANEL_BORDER),
                BorderFactory.createEmptyBorder(24, 20, 24, 20)
        ));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Title
        JLabel titleLabel = new JLabel("Light Polarization");
        titleLabel.setFont(new Font("Outfit", Font.BOLD, 22));
        titleLabel.setForeground(TEXT_MAIN);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(titleLabel);
        
        JLabel subtitleLabel = new JLabel("Physics Simulation");
        subtitleLabel.setFont(new Font("Outfit", Font.PLAIN, 12));
        subtitleLabel.setForeground(TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(subtitleLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 24)));

        // 1. Incident Light Mode Selection
        JPanel modeGroup = createSectionPanel("INCIDENT LIGHT SOURCE");
        JPanel btnGroup = new JPanel(new GridLayout(3, 1, 0, 8));
        btnGroup.setOpaque(false);
        
        btnLinear = new StyledButton("Linear", true);
        btnCircular = new StyledButton("Circular", false);
        btnUnpolarized = new StyledButton("Unpolarized", false);

        btnLinear.addActionListener(e -> setPolarizationMode("linear", btnLinear));
        btnCircular.addActionListener(e -> setPolarizationMode("circular", btnCircular));
        btnUnpolarized.addActionListener(e -> setPolarizationMode("unpolarized", btnUnpolarized));

        btnGroup.add(btnLinear);
        btnGroup.add(btnCircular);
        btnGroup.add(btnUnpolarized);
        modeGroup.add(btnGroup);
        panel.add(modeGroup);
        panel.add(Box.createRigidArea(new Dimension(0, 16)));
        
        // 2. Wave Properties
        JPanel waveGroup = createSectionPanel("WAVE PROPERTIES");
        
        // Speed Slider
        JLabel lblSpeed = new JLabel("Speed");
        lblSpeed.setForeground(TEXT_MAIN);
        lblSpeed.setFont(new Font("Outfit", Font.PLAIN, 13));
        JLabel lblSpeedValue = new JLabel("1.00");
        lblSpeedValue.setForeground(PRIMARY_COLOR);
        lblSpeedValue.setFont(new Font("Outfit", Font.BOLD, 13));
        JPanel speedHeader = new JPanel(new BorderLayout());
        speedHeader.setOpaque(false);
        speedHeader.add(lblSpeed, BorderLayout.WEST);
        speedHeader.add(lblSpeedValue, BorderLayout.EAST);
        waveGroup.add(speedHeader);
        
        JSlider sliderSpeed = createStyledSlider(0, 500, 100); // represents 0.00 to 5.00
        sliderSpeed.addChangeListener(e -> {
            speed = sliderSpeed.getValue() / 100.0;
            lblSpeedValue.setText(String.format("%.2f", speed));
        });
        waveGroup.add(sliderSpeed);
        waveGroup.add(Box.createRigidArea(new Dimension(0, 12)));

        // Frequency Slider
        JLabel lblFreq = new JLabel("Frequency");
        lblFreq.setForeground(TEXT_MAIN);
        lblFreq.setFont(new Font("Outfit", Font.PLAIN, 13));
        JLabel lblFreqValue = new JLabel("3.00");
        lblFreqValue.setForeground(PRIMARY_COLOR);
        lblFreqValue.setFont(new Font("Outfit", Font.BOLD, 13));
        JPanel freqHeader = new JPanel(new BorderLayout());
        freqHeader.setOpaque(false);
        freqHeader.add(lblFreq, BorderLayout.WEST);
        freqHeader.add(lblFreqValue, BorderLayout.EAST);
        waveGroup.add(freqHeader);
        
        JSlider sliderFreq = createStyledSlider(10, 500, 300); // represents 0.10 to 5.00
        sliderFreq.addChangeListener(e -> {
            frequency = sliderFreq.getValue() / 100.0;
            lblFreqValue.setText(String.format("%.2f", frequency));
        });
        waveGroup.add(sliderFreq);

        panel.add(waveGroup);
        panel.add(Box.createRigidArea(new Dimension(0, 16)));

        // 3. Primary Polarizer
        JPanel pol1Group = createSectionPanel("PRIMARY POLARIZER");
        JLabel lblRot1 = new JLabel("Rotation Angle");
        lblRot1.setForeground(TEXT_MAIN);
        lblRot1.setFont(new Font("Outfit", Font.PLAIN, 13));
        JLabel lblRot1Value = new JLabel("0°");
        lblRot1Value.setForeground(PRIMARY_COLOR);
        lblRot1Value.setFont(new Font("Outfit", Font.BOLD, 13));
        JPanel rot1Header = new JPanel(new BorderLayout());
        rot1Header.setOpaque(false);
        rot1Header.add(lblRot1, BorderLayout.WEST);
        rot1Header.add(lblRot1Value, BorderLayout.EAST);
        pol1Group.add(rot1Header);

        JSlider sliderRot1 = createStyledSlider(0, 360, 0);
        sliderRot1.addChangeListener(e -> {
            rotation1 = sliderRot1.getValue();
            lblRot1Value.setText(sliderRot1.getValue() + "°");
        });
        pol1Group.add(sliderRot1);
        panel.add(pol1Group);
        panel.add(Box.createRigidArea(new Dimension(0, 16)));

        // 4. Analyzer Panel
        JPanel analyzerGroup = createSectionPanel("ANALYZER (SECOND POLARIZER)");
        JPanel toggleContainer = new JPanel(new BorderLayout());
        toggleContainer.setOpaque(false);
        JLabel lblToggle = new JLabel("Enable Analyzer");
        lblToggle.setForeground(TEXT_MAIN);
        lblToggle.setFont(new Font("Outfit", Font.PLAIN, 13));
        toggleAnalyzer = new JCheckBox();
        toggleAnalyzer.setOpaque(false);
        toggleAnalyzer.setFocusPainted(false);
        toggleAnalyzer.addActionListener(e -> {
            useAnalyzer = toggleAnalyzer.isSelected();
            analyzerControlsContainer.setVisible(useAnalyzer);
            panel.revalidate();
            panel.repaint();
        });
        toggleContainer.add(lblToggle, BorderLayout.WEST);
        toggleContainer.add(toggleAnalyzer, BorderLayout.EAST);
        analyzerGroup.add(toggleContainer);

        analyzerControlsContainer = new JPanel();
        analyzerControlsContainer.setLayout(new BoxLayout(analyzerControlsContainer, BoxLayout.Y_AXIS));
        analyzerControlsContainer.setOpaque(false);
        analyzerControlsContainer.setVisible(false);
        analyzerControlsContainer.add(Box.createRigidArea(new Dimension(0, 12)));

        JLabel lblRot2 = new JLabel("Rotation Angle");
        lblRot2.setForeground(TEXT_MAIN);
        lblRot2.setFont(new Font("Outfit", Font.PLAIN, 13));
        lblRot2Value = new JLabel("90°");
        lblRot2Value.setForeground(PRIMARY_COLOR);
        lblRot2Value.setFont(new Font("Outfit", Font.BOLD, 13));
        JPanel rot2Header = new JPanel(new BorderLayout());
        rot2Header.setOpaque(false);
        rot2Header.add(lblRot2, BorderLayout.WEST);
        rot2Header.add(lblRot2Value, BorderLayout.EAST);
        analyzerControlsContainer.add(rot2Header);

        sliderRot2 = createStyledSlider(0, 360, 90);
        sliderRot2.addChangeListener(e -> {
            rotation2 = sliderRot2.getValue();
            lblRot2Value.setText(sliderRot2.getValue() + "°");
        });
        analyzerControlsContainer.add(sliderRot2);
        analyzerGroup.add(analyzerControlsContainer);
        panel.add(analyzerGroup);

        panel.add(Box.createVerticalGlue());

        // Legend / Info panel at the bottom
        JPanel legendPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            }
        };
        legendPanel.setOpaque(false);
        legendPanel.setLayout(new BoxLayout(legendPanel, BoxLayout.Y_AXIS));
        legendPanel.setBackground(new Color(0, 0, 0, 80));
        legendPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PANEL_BORDER, 1),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        
        legendPanel.add(createLegendItem("Incident Light", COLOR_INCIDENT));
        legendPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        legendPanel.add(createLegendItem("Polarized Light", COLOR_POLARIZED));
        legendPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        legendPanel.add(createLegendItem("Analyzed Light", COLOR_ANALYZED));

        panel.add(legendPanel);

        return panel;
    }

    // Helper to create a legend row
    private JPanel createLegendItem(String text, Color color) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        
        // Color block with rounded corners
        JPanel block = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            }
        };
        block.setOpaque(false);
        block.setPreferredSize(new Dimension(16, 16));
        
        JLabel label = new JLabel("  " + text);
        label.setForeground(TEXT_MAIN);
        label.setFont(new Font("Outfit", Font.PLAIN, 13));

        p.add(block);
        p.add(label);
        return p;
    }

    // Helper to create styled sections inside the panel
    private JPanel createSectionPanel(String headerText) {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            }
        };
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(new Color(0, 0, 0, 60));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PANEL_BORDER, 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
        
        JLabel label = new JLabel(headerText);
        label.setFont(new Font("Outfit", Font.BOLD, 10));
        label.setForeground(TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(label);
        p.add(Box.createRigidArea(new Dimension(0, 10)));
        return p;
    }

    // Style helper for JSliders
    private JSlider createStyledSlider(int min, int max, int value) {
        JSlider slider = new JSlider(min, max, value);
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setForeground(PRIMARY_COLOR);
        slider.setBackground(new Color(255, 255, 255, 38));
        return slider;
    }

    // Set the simulation mode & toggle button active states
    private void setPolarizationMode(String mode, StyledButton activeButton) {
        this.mode = mode;
        btnLinear.setActive(btnLinear == activeButton);
        btnCircular.setActive(btnCircular == activeButton);
        btnUnpolarized.setActive(btnUnpolarized == activeButton);
    }

    // Custom styled glass-theme button
    private static class StyledButton extends JButton {
        private boolean active;

        public StyledButton(String text, boolean active) {
            super(text);
            this.active = active;
            setOpaque(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setForeground(TEXT_MAIN);
            setFont(new Font("Outfit", Font.BOLD, 13));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        public void setActive(boolean active) {
            this.active = active;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (active) {
                // Glow border and translucent cyan background
                g2.setColor(ACTIVE_BG);
                g2.fillRoundRect(0, 0, w, h, 8, 8);
                g2.setColor(PRIMARY_COLOR);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
                setForeground(PRIMARY_COLOR);
            } else {
                // Standard button background
                g2.setColor(new Color(255, 255, 255, 13));
                g2.fillRoundRect(0, 0, w, h, 8, 8);
                g2.setColor(new Color(255, 255, 255, 25));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
                setForeground(TEXT_MAIN);
            }
            super.paintComponent(g);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LightPolarizerApp app = new LightPolarizerApp();
            app.setVisible(true);
        });
    }
}
