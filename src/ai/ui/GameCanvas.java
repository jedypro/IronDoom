package ai.ui;

import team.domain.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import javax.swing.Timer;

/**
 * Renders the game world each frame.
 *
 * <p>This class is a <em>pure renderer</em>: it reads from {@link SceneData}
 * and {@link UiState} and paints.  It does <strong>not</strong> own any game
 * logic, navigation, or timers beyond its own repaint timer.</p>
 *
 * <p>Sub-renderers are private methods grouped by concern:
 * background, ground assets, threats, interceptors, civilians,
 * effects (explosions / floating text), gifts, and the aim line.</p>
 */
public class GameCanvas extends JPanel implements ActionListener {

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final SceneData  sceneData;
    private final UiState    uiState;
    private final ImageLoader imageLoader;

    // ── Repaint timer ─────────────────────────────────────────────────────────
    private final Timer repaintTimer = new Timer(33, this);

    // ── Visual effects ────────────────────────────────────────────────────────
    private final List<Explosion>    explosions    = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();

    // ── Screen shake ─────────────────────────────────────────────────────────
    private long screenShakeEndTime   = 0;
    private int  currentShakeMagnitude = 0;

    // ── Aim line ─────────────────────────────────────────────────────────────
    private boolean showAim = false;

    // ── Coordinate mapping ────────────────────────────────────────────────────
    private double scale   = 1.0;
    private int    offsetX = 0;
    private int    offsetY = 0;

    // ── Star field ────────────────────────────────────────────────────────────
    private static final int STAR_COUNT = 70;
    private final int[] starXs = new int[STAR_COUNT];
    private final int[] starYs = new int[STAR_COUNT];

    // ── Angle memory (for smooth missile rotation) ───────────────────────────
    private final Map<Integer, Point>  prevThreatPositions      = new HashMap<>();
    private final Map<Integer, Double> threatAngles             = new HashMap<>();
    private final Map<Integer, Point>  prevInterceptorPositions = new HashMap<>();
    private final Map<Integer, Double> interceptorAngles        = new HashMap<>();

    // =========================================================================
    // Construction
    // =========================================================================

    public GameCanvas(SceneData sceneData, UiState uiState, ImageLoader imageLoader) {
        this.sceneData   = sceneData;
        this.uiState     = uiState;
        this.imageLoader = imageLoader;

        setBackground(UIConstants.COLOR_BACKGROUND);
        Random rng = new Random();
        for (int i = 0; i < STAR_COUNT; i++) {
            starXs[i] = rng.nextInt(2000);
            starYs[i] = rng.nextInt(2000);
        }
    }

    // =========================================================================
    // Animation lifecycle
    // =========================================================================

    public void startAnimation()   { repaintTimer.start(); }
    public void pauseAnimation()   { repaintTimer.stop();  }
    public void resumeAnimation()  { repaintTimer.start(); }

    @Override
    public void actionPerformed(ActionEvent e) {
        explosions.removeIf(Explosion::isExpired);
        floatingTexts.removeIf(FloatingText::isExpired);
        repaint();
    }

    // =========================================================================
    // Effects API
    // =========================================================================

    public void addExplosion(int x, int y) {
        explosions.add(new Explosion(x, y));
    }

    public void addFloatingText(int x, int y, String text, Color color) {
        floatingTexts.add(new FloatingText(x, y, text, color));
    }

    public void addFloatingText(int x, int y, String text, Color color, long durationMs) {
        floatingTexts.add(new FloatingText(x, y, text, color, durationMs));
    }

    public void triggerScreenShake(int durationMs, int magnitude) {
        screenShakeEndTime    = System.currentTimeMillis() + durationMs;
        currentShakeMagnitude = magnitude;
    }

    // =========================================================================
    // Aim line toggle
    // =========================================================================

    public boolean isShowAim() { return showAim; }
    public void setShowAim(boolean show) { showAim = show; repaint(); }

    // =========================================================================
    // Coordinate helpers
    // =========================================================================
    
    private int toScreenX(double wx) { return offsetX + (int) Math.round(wx * scale); }
    private int toScreenY(double wy) { return offsetY + (int) Math.round(wy * scale); }
    private int toScreenLen(double wl) { return Math.max(1, (int) Math.round(wl * scale)); }
    private int toScreenDelta(double wd) { return (int) Math.round(wd * scale); }

    // =========================================================================
    // Main paint
    // =========================================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        List<Damageable>     dmg    = sceneData.getDamageables();
        List<AbstractThreat> thrs   = sceneData.getThreats();

        if (thrs.isEmpty() && dmg.isEmpty()) {
            drawMessage(g2d, "Waiting for scene data...");
            g2d.dispose();
            return;
        }

        // Compute viewport
        scale   = Math.min(getWidth() / UIConstants.WORLD_WIDTH, getHeight() / UIConstants.WORLD_HEIGHT);
        offsetX = (int) Math.round((getWidth()  - UIConstants.WORLD_WIDTH  * scale) / 2);
        offsetY = (int) Math.round((getHeight() - UIConstants.WORLD_HEIGHT * scale) / 2);

        if (System.currentTimeMillis() < screenShakeEndTime) {
            offsetX += (int) ((Math.random() * 2 - 1) * currentShakeMagnitude);
            offsetY += (int) ((Math.random() * 2 - 1) * currentShakeMagnitude);
        }

        boolean tick = (System.currentTimeMillis() / 100) % 2 == 0;
        int groundY  = computeGroundY(dmg);

        drawBackground(g2d, groundY);
        drawGroundAssets(g2d, groundY, tick);
        drawCivilians(g2d, tick);
        drawThreats(g2d, tick);
        drawGifts(g2d);
        drawInterceptors(g2d, tick);
        if (showAim) drawAimingLine(g2d);
        drawExplosions(g2d);
        drawFloatingTexts(g2d);

        g2d.dispose();
    }

    // =========================================================================
    // Ground Y computation
    // =========================================================================

    private int computeGroundY(List<Damageable> damageables) {
        int groundY = sceneData.getDamageables().isEmpty()
                ? 650
                : 650; // fallback; normally comes from GameState

        // Use tallest GroundAsset bottom edge
        for (Damageable d : damageables) {
            if (d instanceof GroundAsset) {
                GroundAsset a = (GroundAsset) d;
                groundY = Math.max(groundY, a.getY() + a.getHeight());
            }
        }
        return groundY;
    }

    // =========================================================================
    // Background
    // =========================================================================

    private void drawBackground(Graphics2D g, int groundY) {
        int level = uiState.getCurrentLevel();
        int sgy   = toScreenY(groundY);

        if (level >= UIConstants.THEME_ARCTIC_MIN) {
            // Arctic
            g.setPaint(new GradientPaint(0, 0, new Color(180, 220, 255), 0, getHeight(), new Color(220, 240, 255)));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(255, 255, 240, 200));
            g.fillOval(toScreenX(850), toScreenY(100), toScreenLen(100), toScreenLen(100));
            g.setColor(new Color(240, 245, 255));
            g.fillRect(0, sgy, getWidth(), Math.max(10, getHeight() - sgy));
            drawIcebergs(g, sgy);
            g.setColor(new Color(250, 250, 255));
            g.fillRect(0, sgy, getWidth(), toScreenLen(8));

        } else if (level >= UIConstants.THEME_DESERT_MIN) {
            // Desert
            g.setPaint(new GradientPaint(0, 0, new Color(135, 206, 235), 0, getHeight(), new Color(240, 240, 220)));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(255, 220, 100));
            g.fillOval(toScreenX(900), toScreenY(600), toScreenLen(80), toScreenLen(80));
            g.setColor(new Color(210, 180, 140));
            g.fillRect(0, sgy, getWidth(), Math.max(10, getHeight() - sgy));
            drawDesertDunes(g, sgy);
            g.setColor(new Color(230, 200, 150));
            g.fillRect(0, sgy, getWidth(), toScreenLen(8));

        } else {
            // Default night
            g.setColor(UIConstants.COLOR_BACKGROUND);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(255, 255, 220, 180));
            for (int i = 0; i < STAR_COUNT; i++) {
                g.fillRect(starXs[i] % getWidth(), starYs[i] % getHeight(), 2, 2);
            }
            g.setColor(new Color(50, 35, 20));
            g.fillRect(0, sgy, getWidth(), Math.max(10, getHeight() - sgy));
            g.setColor(new Color(30, 85, 30));
            g.fillRect(0, sgy, getWidth(), toScreenLen(8));
        }
    }

    private void drawIcebergs(Graphics2D g, int screenGroundY) {
        g.setColor(new Color(200, 220, 240, 150));
        g.fillPolygon(
                new int[]{ toScreenX(100), toScreenX(300), toScreenX(200) },
                new int[]{ screenGroundY, screenGroundY, screenGroundY - toScreenLen(150) }, 3);
        g.setColor(new Color(210, 230, 250, 180));
        g.fillPolygon(
                new int[]{ toScreenX(700), toScreenX(950), toScreenX(800) },
                new int[]{ screenGroundY, screenGroundY, screenGroundY - toScreenLen(200) }, 3);
    }

    private void drawDesertDunes(Graphics2D g, int screenGroundY) {
        g.setColor(new Color(190, 160, 120));
        g.fillRoundRect(-50, screenGroundY - toScreenLen(10), getWidth() / 2, toScreenLen(40), toScreenLen(80), toScreenLen(80));
        g.fillRoundRect(getWidth() / 2 - 50, screenGroundY - toScreenLen(20), getWidth() / 2, toScreenLen(50), toScreenLen(100), toScreenLen(100));
    }

    // =========================================================================
    // Ground Assets
    // =========================================================================

    private void drawGroundAssets(Graphics g, int groundY, boolean tick) {
        ThemeColors tc = ThemeColors.forLevel(uiState.getCurrentLevel());

        for (Damageable d : sceneData.getDamageables()) {
            if (!(d instanceof GroundAsset) && !(d instanceof InterceptorBattery) && !(d instanceof LaserBattery)) continue;

            if (d instanceof InterceptorBattery) {
                drawInterceptorBattery(g, (InterceptorBattery) d, groundY, tc);
            } else if (d instanceof LaserBattery) {
                drawLaserBattery(g, (LaserBattery) d, groundY);
            } else {
                drawGroundAssetBuilding(g, (GroundAsset) d, groundY, tick, tc);
            }
        }
    }

    private void drawGroundAssetBuilding(Graphics g, GroundAsset asset, int groundY, boolean tick, ThemeColors tc) {
        int sx = toScreenX(asset.getX()), sy = toScreenY(asset.getY());
        int sw = toScreenLen(asset.getWidth()), sh = toScreenLen(asset.getHeight());
        int level = uiState.getCurrentLevel();

        if (asset.getName().startsWith("Factory")) {
            drawFactory(g, sx, sy, sw, sh, tick, tc, level);
        } else if (asset.getName().startsWith("Military Base")) {
            drawMilitaryBase(g, sx, sy, sw, sh, tick, tc, level);
        } else {
            drawCity(g, asset, sx, sy, sw, sh, tc, level);
        }
        drawAssetLabel(g, asset.getName(), sx, sy, level);
    }

    private void drawFactory(Graphics g, int sx, int sy, int sw, int sh, boolean tick, ThemeColors tc, int level) {
        g.setColor(tc.blockBg); g.fillRect(sx, sy, sw, sh);
        g.setColor(Color.BLACK); g.drawRect(sx, sy, sw, sh);

        int stackW = Math.max(toScreenLen(20), sw / 3);
        int stackH = sh + toScreenLen(30);
        int stackX = sx + sw / 2 - stackW / 2;
        int stackY = sy - toScreenLen(30);

        g.setColor(tc.buildingBg); g.fillRect(stackX, stackY, stackW, stackH);
        g.setColor(Color.BLACK);   g.drawRect(stackX, stackY, stackW, stackH);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int alpha = tick ? 150 : 140;
        g2.setColor(new Color(150, 150, 150, alpha));
        g2.fillOval(stackX, stackY - toScreenLen(tick ? 15 : 18), stackW, toScreenLen(tick ? 20 : 18));
        g2.dispose();
    }

    private void drawMilitaryBase(Graphics g, int sx, int sy, int sw, int sh, boolean tick, ThemeColors tc, int level) {
        g.setColor(tc.blockBg); g.fillRect(sx, sy, sw, sh);
        g.setColor(Color.BLACK); g.drawRect(sx, sy, sw, sh);

        g.setColor(tc.buildingBg);
        for (int i = 0; i < sw; i += toScreenLen(30)) g.fillRect(sx + i, sy, toScreenLen(15), sh);

        int dishX = sx + sw / 2 - toScreenLen(15);
        int dishY = sy - toScreenLen(20);
        g.setColor(tc.tubes); g.fillArc(dishX, dishY, toScreenLen(30), toScreenLen(30), 30, 120);
        g.setColor(Color.BLACK); g.drawArc(dishX, dishY, toScreenLen(30), toScreenLen(30), 30, 120);

        int antX = sx + sw - toScreenLen(20), antY = sy - toScreenLen(30);
        g.setColor(Color.DARK_GRAY); g.fillRect(antX, antY, toScreenLen(4), toScreenLen(30));
        if (tick) { g.setColor(Color.RED); g.fillOval(antX, antY - toScreenLen(4), toScreenLen(4), toScreenLen(4)); }
    }

    private void drawCity(Graphics g, GroundAsset city, int sx, int sy, int sw, int sh, ThemeColors tc, int level) {
        g.setColor(tc.buildingBg);  g.fillRect(sx, sy, sw, sh);
        g.setColor(tc.buildingInner); g.fillRect(sx + toScreenLen(4), sy + toScreenLen(4), Math.max(1, sw - toScreenLen(8)), Math.max(1, sh - toScreenLen(8)));
        g.setColor(Color.BLACK); g.drawRect(sx, sy, sw, sh);

        int blockW = Math.max(toScreenLen(20), sw / 5);
        for (int i = 0; i < sw; i += blockW) {
            int bw = Math.min(blockW, sw - i);
            int seed = i / blockW;
            drawCityBlock(g, sx + i, sy + sh, bw, seed, city.getHeight(), tc, level);
        }
    }

    private void drawCityBlock(Graphics g, int bx, int baseY, int bw, int seed, int citySeed, ThemeColors tc, int level) {
        int bh = toScreenLen(18 + (seed % 3) * 10 + (citySeed % 7));
        int by = baseY - bh;
        g.setColor(tc.blockBg); g.fillRect(bx, by, bw, bh);
        g.setColor(Color.BLACK); g.drawRect(bx, by, bw, bh);
        drawWindows(g, bx, by, bw, bh, tc.windowColor);
    }

    private void drawWindows(Graphics g, int bx, int by, int bw, int bh, Color color) {
        for (int wx = bx + toScreenLen(4); wx < bx + bw - toScreenLen(4); wx += toScreenLen(8)) {
            for (int wy = by + toScreenLen(4); wy < by + bh - toScreenLen(4); wy += toScreenLen(8)) {
                g.setColor(color);
                g.fillRect(wx, wy, toScreenLen(3), toScreenLen(3));
            }
        }
    }

    private void drawAssetLabel(Graphics g, String name, int sx, int sy, int level) {
        g.setColor(level >= UIConstants.THEME_ARCTIC_MIN ? Color.BLACK : new Color(255, 255, 220));
        g.drawString(name, sx + toScreenLen(6), sy + toScreenLen(16));
    }

    // ── Interceptor battery ───────────────────────────────────────────────────

    private void drawInterceptorBattery(Graphics g, InterceptorBattery battery, int groundY, ThemeColors tc) {
        int bx = toScreenX(battery.getX()), by = toScreenY(groundY);
        int baseW = toScreenLen(60), baseH = toScreenLen(15), halfBase = toScreenLen(30);
        boolean selected = battery.getId() == uiState.getSelectedBatteryId();
        int level = uiState.getCurrentLevel();

        if (selected) drawBatteryHighlight(g, bx, by, baseW, baseH, halfBase, level);

        g.setColor(selected ? tc.batterySelected : tc.batteryBase);
        g.fillRect(bx - halfBase, by - baseH, baseW, baseH);
        g.setColor(Color.BLACK); g.drawRect(bx - halfBase, by - baseH, baseW, baseH);

        g.setColor(Color.DARK_GRAY); g.fillRect(bx - toScreenLen(15), by - toScreenLen(20), toScreenLen(30), toScreenLen(5));
        g.setColor(Color.BLACK); g.drawRect(bx - toScreenLen(15), by - toScreenLen(20), toScreenLen(30), toScreenLen(5));

        Graphics2D gR = (Graphics2D) g.create();
        gR.translate(bx, by - toScreenLen(20));
        gR.rotate(Math.toRadians(uiState.getCurrentSliderAngle() - 90));
        drawInterceptorTubes(gR, tc, level);
        gR.dispose();

        drawBatteryLabel(g, "Battery", battery.getMissilesAvailable(), bx, by, level, 20);
        if (!battery.isActive()) drawDamagedOverlay(g, bx, by);
    }

    private void drawLaserBattery(Graphics g, LaserBattery lb, int groundY) {
        int bx = toScreenX(lb.getX()), by = toScreenY(groundY);
        int baseW = toScreenLen(60), baseH = toScreenLen(20), halfBase = toScreenLen(30);
        boolean selected = lb.getId() == uiState.getSelectedBatteryId();
        int level = uiState.getCurrentLevel();

        Color base     = new Color(50, 60, 90);
        Color sel      = new Color(70, 150, 230);

        if (selected) {
            Graphics2D gH = (Graphics2D) g.create();
            gH.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int pad = toScreenLen(10);
            gH.setColor(new Color(80, 130, 255, 90));
            gH.fillRoundRect(bx - halfBase - pad, by - baseH - pad, baseW + pad * 2, baseH + pad * 2, toScreenLen(24), toScreenLen(24));
            gH.setStroke(new BasicStroke(toScreenLen(4), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gH.setColor(new Color(150, 195, 255, 180));
            gH.drawRoundRect(bx - halfBase - pad + toScreenLen(3), by - baseH - pad + toScreenLen(3),
                    baseW + pad * 2 - toScreenLen(6), baseH + pad * 2 - toScreenLen(6), toScreenLen(24), toScreenLen(24));
            gH.dispose();
        }

        g.setColor(selected ? sel : base);
        g.fillRoundRect(bx - halfBase, by - baseH, baseW, baseH, toScreenLen(10), toScreenLen(10));
        g.setColor(Color.BLACK);
        g.drawRoundRect(bx - halfBase, by - baseH, baseW, baseH, toScreenLen(10), toScreenLen(10));

        g.setColor(new Color(100, 200, 255, 150));
        g.fillArc(bx - toScreenLen(20), by - baseH - toScreenLen(15), toScreenLen(40), toScreenLen(30), 0, 180);
        g.setColor(Color.BLACK);
        g.drawArc(bx - toScreenLen(20), by - baseH - toScreenLen(15), toScreenLen(40), toScreenLen(30), 0, 180);

        g.setColor(new Color(255, 255, 255, 200));
        g.fillOval(bx - toScreenLen(8), by - baseH - toScreenLen(10), toScreenLen(16), toScreenLen(16));

        Graphics2D gR = (Graphics2D) g.create();
        gR.translate(bx, by - baseH - toScreenLen(5));
        gR.rotate(Math.toRadians(uiState.getCurrentSliderAngle() - 90));
        gR.setColor(new Color(200, 220, 255));
        gR.fillRect(-toScreenLen(4), -toScreenLen(25), toScreenLen(8), toScreenLen(25));
        gR.setColor(Color.BLACK);
        gR.drawRect(-toScreenLen(4), -toScreenLen(25), toScreenLen(8), toScreenLen(25));
        gR.setColor(Color.RED);
        gR.fillRect(-toScreenLen(2), -toScreenLen(25), toScreenLen(4), toScreenLen(5));
        gR.dispose();

        drawBatteryLabel(g, "Laser", lb.getLaserChargesAvailable(), bx, by, level, 20);
        if (!lb.isActive()) drawDamagedOverlay(g, bx, by);
    }

    private void drawInterceptorTubes(Graphics2D g, ThemeColors tc, int level) {
        boolean blockStyle = level <= 3 || level >= UIConstants.THEME_ARCTIC_MIN;
        for (int i = 0; i < 4; i++) {
            int tx = toScreenDelta(-25 + i * 14);
            g.setColor(tc.tubes);
            if (blockStyle) {
                g.fillRect(tx, toScreenDelta(-25), toScreenLen(10), toScreenLen(25));
                g.setColor(Color.BLACK);
                g.drawRect(tx, toScreenDelta(-25), toScreenLen(10), toScreenLen(25));
            } else {
                int[] lx = { tx, tx + toScreenLen(10), tx + toScreenLen(8), tx + toScreenLen(2) };
                int[] ly = { 0, 0, toScreenDelta(-28), toScreenDelta(-28) };
                g.fillPolygon(lx, ly, 4); g.setColor(Color.BLACK); g.drawPolygon(lx, ly, 4);
            }
        }
    }

    private void drawBatteryHighlight(Graphics g, int bx, int by, int baseW, int baseH, int halfBase, int level) {
        Graphics2D gH = (Graphics2D) g.create();
        gH.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int pad = toScreenLen(10);
        boolean arctic = level >= UIConstants.THEME_ARCTIC_MIN;
        Color gc1 = arctic ? new Color(100, 255, 100, 90) : new Color(80, 230, 255, 90);
        Color gc2 = arctic ? new Color(150, 255, 150, 180) : new Color(150, 245, 255, 180);
        gH.setColor(gc1);
        gH.fillRoundRect(bx - halfBase - pad, by - baseH - pad, baseW + pad * 2, baseH + pad * 2, toScreenLen(24), toScreenLen(24));
        gH.setStroke(new BasicStroke(toScreenLen(4), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        gH.setColor(gc2);
        gH.drawRoundRect(bx - halfBase - pad + toScreenLen(3), by - baseH - pad + toScreenLen(3),
                baseW + pad * 2 - toScreenLen(6), baseH + pad * 2 - toScreenLen(6), toScreenLen(24), toScreenLen(24));
        gH.dispose();
    }

    private void drawBatteryLabel(Graphics g, String name, int ammo, int bx, int by, int level, int yOffset) {
        boolean arctic = level >= UIConstants.THEME_ARCTIC_MIN;
        g.setColor(arctic ? Color.BLACK : Color.WHITE);
        g.drawString(name, bx - toScreenLen(20), by - toScreenLen(45));
        g.setColor(ammo < 20 ? Color.RED : (arctic ? Color.BLACK : Color.WHITE));
        g.drawString("Ammo: " + ammo, bx - toScreenLen(20), by + toScreenLen(yOffset));
    }

    private void drawDamagedOverlay(Graphics g, int bx, int by) {
        g.setColor(new Color(255, 0, 0, 128));
        g.fillOval(bx - toScreenLen(15), by - toScreenLen(15), toScreenLen(30), toScreenLen(30));
        drawDamageSmoke((Graphics2D) g, bx, by - toScreenLen(10));
    }

    private void drawDamageSmoke(Graphics2D g, int cx, int cy) {
        long time = System.currentTimeMillis();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < 4; i++) {
            long t = (time + i * 375L) % 1500L;
            double progress = t / 1500.0;
            int size  = toScreenLen(15) + (int)(progress * toScreenLen(35));
            int smokeX = cx - size / 2 + (int)(Math.sin(time / 250.0 + i) * toScreenLen(10));
            int smokeY = cy - (int)(progress * toScreenLen(80));
            int alpha  = (int)(200 * (1.0 - progress));
            g2.setColor(new Color(30, 30, 30, Math.max(0, alpha)));
            g2.fillOval(smokeX, smokeY, size, size);
        }
        g2.dispose();
    }

    // =========================================================================
    // Civilians
    // =========================================================================

    private void drawCivilians(Graphics2D g, boolean tick) {
        long time = System.currentTimeMillis();
        for (Civilian c : sceneData.getCivilians()) {
            if (c.getState() == Civilian.State.HIDING) continue;

            int cx = toScreenX(c.getX());
            int cy = toScreenY(c.getY()) - computeJumpOffset(c, time, tick);

            int w = toScreenLen(24), h = toScreenLen(36);
            Image img = imageLoader.getCivilianImage(c.getId() % 3);

            if (img != null) {
                int iw = img.getWidth(null), ih = img.getHeight(null);
                if (iw > 0 && ih > 0) {
                    w = toScreenLen(iw); h = toScreenLen(ih);
                    if (ih > 60) { h = toScreenLen(36); w = (int)(h * ((double) iw / ih)); }
                }
                g.drawImage(img, cx - w / 2, cy - h, w, h, null);
            } else {
                g.setColor(new Color(255, 200, 200)); g.fillRect(cx - w / 2, cy - h, w, h);
                g.setColor(Color.BLACK); g.drawRect(cx - w / 2, cy - h, w, h);
            }

            if (c.getState() == Civilian.State.FLEEING) {
                int fs = toScreenLen(12);
                g.setColor(tick ? Color.RED : Color.ORANGE);
                g.fillOval(cx - fs / 2, cy - h - fs, fs, fs);
                g.setColor(Color.YELLOW);
                g.fillOval(cx - fs / 4, cy - h - fs + toScreenLen(2), fs / 2, fs / 2);
            }
        }
    }

    private int computeJumpOffset(Civilian c, long now, boolean tick) {
        if (c.getState() == Civilian.State.FLEEING) {
            long t = (now + c.getId() * 50L) % 250L;
            return (int)(Math.sin(t / 250.0 * Math.PI) * toScreenLen(10));
        }
        long cycle = 1500 + (c.getId() * 311L) % 1500;
        long phase = (now + c.getId() * 100L) % cycle;
        if (phase < 350) return (int)(Math.sin(phase / 350.0 * Math.PI) * toScreenLen(12));
        return 0;
    }

    // =========================================================================
    // Threats
    // =========================================================================

    private void drawThreats(Graphics2D g, boolean tick) {
        int level = uiState.getCurrentLevel();
        for (AbstractThreat threat : sceneData.getThreats()) {
            int tx = toScreenX(threat.getX()), ty = toScreenY(threat.getY());
            int id = threat.getId();
            double angle = computeAngle(id, tx, ty, prevThreatPositions, threatAngles, Math.PI / 2);

            Graphics2D gR = (Graphics2D) g.create();
            gR.translate(tx, ty);
            gR.rotate(angle - Math.PI / 2);
            drawThreatShape(gR, threat, tick, level);
            gR.dispose();

            g.setColor(level >= UIConstants.THEME_ARCTIC_MIN ? Color.BLACK : Color.WHITE);
            g.drawString(threat instanceof UAV ? "UAV" : "Threat", tx + toScreenLen(8), ty);
        }
    }

    private void drawThreatShape(Graphics2D g, AbstractThreat threat, boolean tick, int level) {
        boolean blocky = level <= 3 || level >= UIConstants.THEME_ARCTIC_MIN;
        if (blocky) {
            if (threat instanceof UAV) {
                Color body = level >= UIConstants.THEME_ARCTIC_MIN ? new Color(110, 100, 90) : new Color(80, 180, 220);
                Color cock = level >= UIConstants.THEME_ARCTIC_MIN ? new Color(100, 255, 100) : new Color(30, 90, 120);
                g.setColor(body); g.fillRoundRect(-toScreenLen(12), -toScreenLen(10), toScreenLen(24), toScreenLen(16), toScreenLen(6), toScreenLen(6));
                g.setColor(Color.BLACK); g.drawRoundRect(-toScreenLen(12), -toScreenLen(10), toScreenLen(24), toScreenLen(16), toScreenLen(6), toScreenLen(6));
                g.setColor(cock); g.fillOval(-toScreenLen(6), -toScreenLen(8), toScreenLen(12), toScreenLen(10));
            } else {
                Color body = level >= UIConstants.THEME_ARCTIC_MIN ? new Color(80, 75, 70) : Color.RED;
                Color fire = level >= UIConstants.THEME_ARCTIC_MIN ? new Color(255, 120, 0) : Color.ORANGE;
                if (tick) { g.setColor(fire); g.fillRect(-toScreenLen(5), -toScreenLen(19), toScreenLen(10), toScreenLen(9)); }
                else       { g.setColor(fire); g.fillRect(-toScreenLen(4), -toScreenLen(17), toScreenLen(8), toScreenLen(7)); }
                g.setColor(body); g.fillRect(-toScreenLen(6), -toScreenLen(10), toScreenLen(12), toScreenLen(25));
                g.setColor(Color.BLACK); g.drawRect(-toScreenLen(6), -toScreenLen(10), toScreenLen(12), toScreenLen(25));
            }
        } else {
            // Sharp design for levels 4-6
            Color bodyC = new Color(160, 140, 110), cockC = new Color(255, 180, 50, 200);
            if (threat instanceof UAV) {
                int[] ux = { 0, toScreenLen(-8), toScreenLen(-14), toScreenLen(-8), 0, toScreenLen(8), toScreenLen(14), toScreenLen(8) };
                int[] uy = { toScreenLen(-15), toScreenLen(-5), toScreenLen(8), toScreenLen(5), toScreenLen(10), toScreenLen(5), toScreenLen(8), toScreenLen(-5) };
                g.setColor(bodyC); g.fillPolygon(ux, uy, 8);
                g.setColor(Color.BLACK); g.drawPolygon(ux, uy, 8);
                g.setColor(cockC); g.fillOval(-toScreenLen(2), -toScreenLen(12), toScreenLen(4), toScreenLen(6));
            } else {
                int[] mx = { 0, toScreenLen(-5), toScreenLen(-5), toScreenLen(-9), toScreenLen(-3), 0, toScreenLen(3), toScreenLen(9), toScreenLen(5), toScreenLen(5) };
                int[] my = { toScreenLen(20), toScreenLen(12), toScreenLen(-6), toScreenLen(-14), toScreenLen(-12), 0, toScreenLen(-12), toScreenLen(-14), toScreenLen(-6), toScreenLen(12) };
                g.setColor(new Color(180, 120, 90)); g.fillPolygon(mx, my, 10);
                g.setColor(Color.BLACK); g.drawPolygon(mx, my, 10);
                int fy = -toScreenLen(20), fw = toScreenLen(10), fh = tick ? toScreenLen(12) : toScreenLen(10);
                g.setColor(Color.ORANGE); g.fillOval(-toScreenLen(4), fy, fw, fh);
                g.setColor(Color.YELLOW); g.fillOval(-toScreenLen(2), fy + toScreenLen(2), toScreenLen(6), fh - toScreenLen(2));
            }
        }
    }

    // =========================================================================
    // Interceptors
    // =========================================================================

    private void drawInterceptors(Graphics2D g, boolean tick) {
        int level = uiState.getCurrentLevel();
        for (DefenseEntity interceptor : sceneData.getInterceptors()) {
            if (interceptor instanceof LightShield) {
                drawLaser(g, (LightShield) interceptor);
            } else if (interceptor instanceof InterceptorMissile) {
                drawInterceptorMissile(g, (InterceptorMissile) interceptor, tick, level);
            }
        }
    }

    private void drawLaser(Graphics2D g, LightShield laser) {
        if (!laser.isActive()) return;
        int sx = toScreenX(laser.getX()), sy = toScreenY(laser.getY());
        int ex = toScreenX(laser.getEndX()), ey = toScreenY(laser.getEndY());
        double pulse = 1.0 + 0.2 * Math.sin(System.currentTimeMillis() / 40.0);

        g.setColor(new Color(0, 100, 255, 60));
        g.setStroke(new BasicStroke((float)(toScreenLen(34) * pulse), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(sx, sy, ex, ey);

        g.setColor(new Color(0, 200, 255, 150));
        g.setStroke(new BasicStroke((float)(toScreenLen(16) * pulse), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(sx, sy, ex, ey);

        g.setColor(new Color(220, 255, 255, 255));
        g.setStroke(new BasicStroke((float)(toScreenLen(6) * pulse), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(sx, sy, ex, ey);

        g.setStroke(new BasicStroke(1));
    }

    private void drawInterceptorMissile(Graphics2D g, InterceptorMissile missile, boolean tick, int level) {
        int ix = toScreenX(missile.getX()), iy = toScreenY(missile.getY());
        int id = missile.getId();
        double angle = computeAngle(id, ix, iy, prevInterceptorPositions, interceptorAngles, -Math.PI / 2);

        Graphics2D gR = (Graphics2D) g.create();
        gR.translate(ix, iy);
        gR.rotate(angle + Math.PI / 2);
        drawInterceptorShape(gR, tick, level);
        gR.dispose();
    }

    private void drawInterceptorShape(Graphics2D g, boolean tick, int level) {
        boolean blocky = level <= 3 || level >= UIConstants.THEME_ARCTIC_MIN;
        if (blocky) {
            Color body = level >= UIConstants.THEME_ARCTIC_MIN ? new Color(90, 90, 85) : Color.LIGHT_GRAY;
            Color cock = level >= UIConstants.THEME_ARCTIC_MIN ? new Color(100, 255, 100) : Color.BLUE;
            if (tick) { g.setColor(Color.CYAN); g.fillRect(-toScreenLen(4), toScreenLen(10), toScreenLen(8), toScreenLen(10)); }
            else       { g.setColor(Color.CYAN); g.fillRect(-toScreenLen(3), toScreenLen(10), toScreenLen(6), toScreenLen(8)); }
            g.setColor(body); g.fillRect(-toScreenLen(4), -toScreenLen(10), toScreenLen(8), toScreenLen(20));
            g.setColor(cock); g.fillRect(-toScreenLen(5), -toScreenLen(14), toScreenLen(6), toScreenLen(4));
        } else {
            int[] ix = { 0, toScreenLen(-4), toScreenLen(-4), toScreenLen(-8), toScreenLen(-3), 0, toScreenLen(3), toScreenLen(8), toScreenLen(4), toScreenLen(4) };
            int[] iy = { toScreenLen(-20), toScreenLen(-12), toScreenLen(6), toScreenLen(14), toScreenLen(12), toScreenLen(18), toScreenLen(12), toScreenLen(14), toScreenLen(6), toScreenLen(-12) };
            g.setColor(new Color(200, 190, 170)); g.fillPolygon(ix, iy, 10);
            g.setColor(Color.BLACK); g.drawPolygon(ix, iy, 10);
            int fy = toScreenLen(20), fw = toScreenLen(10), fh = tick ? toScreenLen(12) : toScreenLen(10);
            g.setColor(new Color(100, 255, 255)); g.fillOval(-toScreenLen(4), fy, fw, fh);
            g.setColor(new Color(190, 255, 255)); g.fillOval(-toScreenLen(2), fy + toScreenLen(2), toScreenLen(6), fh - toScreenLen(2));
        }
    }

    // =========================================================================
    // Gifts
    // =========================================================================

    private void drawGifts(Graphics2D g) {
        for (Gift gift : sceneData.getGifts()) {
            int gx = toScreenX(gift.getX()), gy = toScreenY(gift.getY());
            int gw = toScreenLen(gift.getWidth()), gh = toScreenLen(gift.getHeight());

            g.setColor(Color.WHITE);
            g.drawLine(gx, gy, gx + gw / 2, gy - gh);
            g.drawLine(gx + gw, gy, gx + gw / 2, gy - gh);
            g.drawArc(gx - gw / 2, gy - gh - gh / 2, gw * 2, gh, 0, 180);

            boolean isBattery = gift.getGiftType() == GiftType.NEW_BATTERY
                             || gift.getGiftType() == GiftType.BATTERY_REPAIR;
            g.setColor(isBattery ? UIConstants.COLOR_SELECTED_BATTERY : UIConstants.COLOR_BATTERY);
            g.fillRect(gx, gy, gw, gh);

            g.setColor(Color.WHITE);
            g.fillRect(gx + gw / 2 - 2, gy + 5, 4, gh - 10);
            g.fillRect(gx + 5, gy + gh / 2 - 2, gw - 10, 4);
        }
    }

    // =========================================================================
    // Aim line
    // =========================================================================

    private void drawAimingLine(Graphics2D g) {
        int id = uiState.getSelectedBatteryId();
        if (id == -1) return;

        Damageable selected = null;
        for (Damageable d : sceneData.getDamageables()) {
            if (d.getId() == id) { selected = d; break; }
        }
        if (selected == null) return;

        Stroke orig = g.getStroke();
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{ 10f, 10f }, 0f));
        g.setColor(new Color(255, 255, 255, 150));

        int startX = toScreenX(selected.getX()) + toScreenLen(5 / 2);
        int startY = toScreenY(selected.getY());
        double rad = Math.toRadians(180 - uiState.getCurrentSliderAngle());
        int endX = startX + (int)(350 * Math.cos(rad));
        int endY = startY - (int)(350 * Math.sin(rad));

        g.drawLine(startX, startY, endX, endY);
        g.setStroke(orig);
    }

    // =========================================================================
    // Explosions and floating text
    // =========================================================================

    private void drawExplosions(Graphics g) {
        for (Explosion exp : explosions) {
            long age   = System.currentTimeMillis() - exp.createdAt;
            int  alpha = (int) Math.max(0, 255 - age * 255 / UIConstants.EXPLOSION_DURATION_MS);
            int  base  = toScreenLen(20);
            int  size  = Math.max(2, base + (int) Math.round(age / 10.0 * scale));
            int  cx    = toScreenX(exp.x), cy = toScreenY(exp.y);

            g.setColor(new Color(255, 100, 0, alpha));
            g.fillRect(cx - size / 2, cy - size / 2, size, size);
            g.setColor(new Color(255, 220, 40, alpha));
            g.fillRect(cx - size / 3, cy - size / 3, size * 2 / 3, size * 2 / 3);
            g.setColor(new Color(255, 255, 200, alpha));
            g.fillRect(cx - size / 6, cy - size / 6, size / 3, size / 3);
        }
    }

    private void drawFloatingTexts(Graphics2D g) {
        long now = System.currentTimeMillis();
        for (FloatingText ft : floatingTexts) {
            long age    = now - ft.createdAt;
            int  alpha  = (int) Math.max(0, 255 - age * 255L / ft.duration);
            int  floatY = (int)(age / 20.0);
            int  cx     = toScreenX(ft.x);
            int  cy     = toScreenY(ft.y) - floatY - toScreenLen(20);

            g.setFont(g.getFont().deriveFont(Font.BOLD, Math.max(16f, (float) toScreenLen(24))));
            g.setColor(new Color(ft.color.getRed(), ft.color.getGreen(), ft.color.getBlue(), alpha));
            g.drawString(ft.text, cx - g.getFontMetrics().stringWidth(ft.text) / 2, cy);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void drawMessage(Graphics g, String msg) {
        g.setColor(uiState.getCurrentLevel() >= UIConstants.THEME_ARCTIC_MIN ? Color.BLACK : Color.WHITE);
        g.drawString(msg, 20, 20);
    }

    /**
     * Computes the orientation angle of a moving entity from its
     * position history.
     */
    private double computeAngle(
            int id, int x, int y,
            Map<Integer, Point>  prevPositions,
            Map<Integer, Double> angles,
            double defaultAngle) {

        double angle;
        if (prevPositions.containsKey(id)) {
            Point prev = prevPositions.get(id);
            if (prev.x != x || prev.y != y) {
                angle = Math.atan2(y - prev.y, x - prev.x);
                angles.put(id, angle);
            } else {
                angle = angles.getOrDefault(id, defaultAngle);
            }
        } else {
            angle = defaultAngle;
            angles.put(id, angle);
        }
        prevPositions.put(id, new Point(x, y));
        return angle;
    }

    // =========================================================================
    // Inner value types
    // =========================================================================

    static class Explosion {
        final int  x, y;
        final long createdAt = System.currentTimeMillis();
        Explosion(int x, int y) { this.x = x; this.y = y; }
        boolean isExpired() { return System.currentTimeMillis() - createdAt > UIConstants.EXPLOSION_DURATION_MS; }
    }

    static class FloatingText {
        final int    x, y;
        final String text;
        final Color  color;
        final long   createdAt = System.currentTimeMillis();
        final long   duration;
        FloatingText(int x, int y, String text, Color color) {
            this(x, y, text, color, UIConstants.FLOATING_TEXT_DEFAULT_DURATION_MS);
        }
        FloatingText(int x, int y, String text, Color color, long duration) {
            this.x = x; this.y = y; this.text = text; this.color = color; this.duration = duration;
        }
        boolean isExpired() { return System.currentTimeMillis() - createdAt > duration; }
    }

    // ── Theme color helper ────────────────────────────────────────────────────

    private static class ThemeColors {
        Color buildingBg, buildingInner, blockBg, windowColor, batteryBase, batterySelected, tubes;

        static ThemeColors forLevel(int level) {
            ThemeColors tc = new ThemeColors();
            if (level >= UIConstants.THEME_ARCTIC_MIN) {
                tc.buildingBg       = new Color(110, 100, 90);
                tc.buildingInner    = new Color(130, 120, 110);
                tc.blockBg          = new Color(80, 75, 70);
                tc.windowColor      = new Color(100, 255, 100);
                tc.batteryBase      = new Color(90, 90, 85);
                tc.batterySelected  = new Color(100, 255, 100);
                tc.tubes            = new Color(70, 70, 65);
            } else if (level >= UIConstants.THEME_DESERT_MIN) {
                tc.buildingBg       = new Color(180, 150, 110);
                tc.buildingInner    = new Color(200, 170, 130);
                tc.blockBg          = new Color(190, 160, 120);
                tc.windowColor      = new Color(40, 50, 90);
                tc.batteryBase      = new Color(120, 110, 90);
                tc.batterySelected  = new Color(120, 200, 250);
                tc.tubes            = new Color(100, 90, 80);
            } else {
                tc.buildingBg       = new Color(30, 45, 55);
                tc.buildingInner    = new Color(70, 95, 110);
                tc.blockBg          = new Color(45, 60, 75);
                tc.windowColor      = new Color(170, 210, 255);
                tc.batteryBase      = UIConstants.COLOR_BATTERY;
                tc.batterySelected  = UIConstants.COLOR_SELECTED_BATTERY;
                tc.tubes            = new Color(100, 130, 80);
            }
            return tc;
        }
    }
}
