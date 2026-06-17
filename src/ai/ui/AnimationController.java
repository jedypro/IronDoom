package ai.ui;

import javax.swing.Timer;

/**
 * Controls the two animation timers used during gameplay:
 * <ol>
 *   <li>The <em>canvas repaint timer</em> (owned by {@link GameCanvas}).</li>
 *   <li>The <em>aim timer</em> that drives continuous angle changes when an
 *       arrow key is held down.</li>
 * </ol>
 *
 * <p>Both timers are paused and resumed together whenever the game
 * enters or leaves a non-game screen.</p>
 */
public class AnimationController {

    private GameCanvas canvas;
    private Timer      aimTimer;

    public void setCanvas(GameCanvas canvas) {
        this.canvas = canvas;
    }

    public void setAimTimer(Timer timer) {
        this.aimTimer = timer;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        if (canvas   != null) canvas.startAnimation();
        if (aimTimer != null) aimTimer.start();
    }

    public void pause() {
        if (canvas   != null) canvas.pauseAnimation();
        if (aimTimer != null) aimTimer.stop();
    }

    public void resume() {
        if (canvas   != null) canvas.resumeAnimation();
        if (aimTimer != null) aimTimer.start();
    }
}
