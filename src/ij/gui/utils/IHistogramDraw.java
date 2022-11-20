package ij.gui.utils;

import java.awt.*;

import ij.ImagePlus;
import ij.Prefs;
import ij.process.ImageProcessor;

public interface IHistogramDraw {
    static final double SCALE = Prefs.getGuiScale();
    static final int HIST_WIDTH = (int) (SCALE * 256);
    static final int HIST_HEIGHT = (int) (SCALE * 128);
    static final int XMARGIN = (int) (20 * SCALE);
    static final int YMARGIN = (int) (10 * SCALE);
    static final int WIN_WIDTH = HIST_WIDTH + (int) (44 * SCALE);
    static final int WIN_HEIGHT = HIST_HEIGHT + (int) (118 * SCALE);
    static final int BAR_HEIGHT = (int) (SCALE * 12);
    static final int INTENSITY1 = 0, INTENSITY2 = 1, RGB = 2, RED = 3, GREEN = 4, BLUE = 5;
    static final Color frameColor = new Color(30, 60, 120);

    public boolean shouldDrawLogPlot();
    public HistogramDrawProperties getHistogramDrawProperties();

    public void drawAlignedColorBar(ImagePlus imp, double xMin, double xMax, ImageProcessor ip, int x, int y,
            int width,
            int height);
}
