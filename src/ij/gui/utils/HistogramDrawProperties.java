package ij.gui.utils;

import ij.LookUpTable;
import ij.measure.Calibration;
import ij.process.ImageStatistics;

import java.awt.*;

public class HistogramDrawProperties {
    public int srcImageID; // ID of source image

    public LookUpTable lut;

    public int yMax;

    public boolean logScale;

    public long newMaxCount;

    public long[] histogram;

    public int digits;

    public int decimalPlaces;

    public Font font = new Font("SansSerif", Font.PLAIN, (int) (12 * IHistogramDraw.SCALE));

    public Calibration cal;

    public ImageStatistics stats;

    public int rgbMode = -1;

    public boolean showBins;

    public int col1, col2, row1, row2, row3, row4, row5;

    public Rectangle frame;

    public HistogramDrawProperties() {
    }
}
