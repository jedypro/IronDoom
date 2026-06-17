package ai.ui;

import java.awt.Image;
import javax.swing.ImageIcon;

/**
 * Loads and caches all static image assets used by the UI.
 *
 * <p>Extracted from {@code Ui} to honour the Single-Responsibility
 * Principle: asset loading is completely decoupled from rendering.</p>
 */
public class ImageLoader {

    private static final String[] CIVILIAN_FILES = { "oz.png", "yedidya.png", "gimdani.png" };
    private static final String   BACKGROUND_FILE = "open_pic.png";
    private static final String   IMAGE_BASE_PATH  = "/ai/ui/Images/";
    private static final String   IMAGE_FALLBACK_PATH = "src/ai/ui/Images/";

    private final Image   backgroundImage;
    private final Image[] civilianImages;

    public ImageLoader() {
        backgroundImage = loadImage(BACKGROUND_FILE);
        civilianImages  = new Image[CIVILIAN_FILES.length];
        for (int i = 0; i < CIVILIAN_FILES.length; i++) {
            civilianImages[i] = loadImage(CIVILIAN_FILES[i]);
        }
    }

    /** Returns the menu / settings background, or {@code null} if unavailable. */
    public Image getBackgroundImage() {
        return backgroundImage;
    }

    /**
     * Returns the civilian sprite for the given index.
     * Index is expected to be {@code civilian.getId() % 3}.
     */
    public Image getCivilianImage(int index) {
        return civilianImages[index % civilianImages.length];
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Image loadImage(String fileName) {
        java.net.URL resource = ImageLoader.class.getResource(IMAGE_BASE_PATH + fileName);
        if (resource != null) {
            return new ImageIcon(resource).getImage();
        }
        // Fallback for IDE / non-jar execution
        return new ImageIcon(IMAGE_FALLBACK_PATH + fileName).getImage();
    }
}
