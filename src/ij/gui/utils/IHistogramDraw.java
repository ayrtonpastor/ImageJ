package ij.gui.utils;

import java.awt.*;

import ij.ImagePlus;
import ij.LookUpTable;
import ij.Prefs;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

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

    public int getSrcImageID();

    public void setSrcImageID(int srcImageID);

    public LookUpTable getLut();

    public void setLut(LookUpTable lut);

    public int getyMax();

    public void setyMax(int yMax);

    public boolean shouldDrawLogPlot();

    public boolean isLogScale();

    public void setLogScale(boolean logScale);

    public long getNewMaxCount();

    public void setNewMaxCount(long newMaxCount);

    public long[] getLongHistogram();

    public void setHistogram(long[] histogram);

    public int getDigits();

    public void setDigits(int digits);

    public int getDecimalPlaces();

    public void setDecimalPlaces(int decimalPlaces);

    public Font getFont();

    public void setFont(Font font);

    public Calibration getCal();

    public void setCal(Calibration cal);

    public ImageStatistics getStats();

    public void setStats(ImageStatistics stats);

    public int getRgbMode();

    public void setRgbMode(int rgbMode);

    public boolean isShowBins();

    public void setShowBins(boolean showBins);

    public int getCol1();

    public void setCol1(int col1);

    public int getCol2();

    public void setCol2(int col2);

    public int getRow1();

    public void setRow1(int row1);

    public int getRow2();

    public void setRow2(int row2);

    public int getRow3();

    public void setRow3(int row3);

    public int getRow4();

    public void setRow4(int row4);

    public int getRow5();

    public void setRow5(int row5);

    public void drawAlignedColorBar(ImagePlus imp, double xMin, double xMax, ImageProcessor ip, int x, int y,
            int width,
            int height);

    public void drawLogPlot(long maxCount, ImageProcessor ip);

    public void drawPlot(long maxCount, ImageProcessor ip);
}
