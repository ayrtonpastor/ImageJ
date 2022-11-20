package ij.gui;

import ij.*;
import ij.gui.utils.HistogramDraw;
import ij.gui.utils.IHistogramDraw;
import ij.process.*;
import ij.plugin.filter.Analyzer;
import ij.measure.*;
import ij.macro.Interpreter;
import java.awt.*;
import java.awt.image.*;

public class HistogramPlot extends ImagePlus implements IHistogramDraw {
	int rgbMode = -1;
	ImageStatistics stats;
	boolean stackHistogram;
	Calibration cal;
	long[] histogram;
	LookUpTable lut;
	int decimalPlaces;
	int digits;
	long newMaxCount;
	boolean logScale;
	int yMax;
	int srcImageID;
	Rectangle frame;
	Font font = new Font("SansSerif", Font.PLAIN, (int) (12 * SCALE));
	boolean showBins;
	int col1, col2, row1, row2, row3, row4, row5;
	HistogramDraw histogramDraw;

	public HistogramPlot() {
		histogramDraw = new HistogramDraw(this);
		setImage(NewImage.createRGBImage("Histogram", WIN_WIDTH, WIN_HEIGHT, 1, NewImage.FILL_WHITE));
	}

	/**
	 * Plots a histogram using the specified title and number of bins.
	 * Currently, the number of bins must be 256 expect for 32 bit images.
	 */
	public void draw(String title, ImagePlus imp, int bins) {
		draw(imp, bins, 0.0, 0.0, 0);
	}

	/**
	 * Plots a histogram using the specified title, number of bins and histogram
	 * range.
	 * Currently, the number of bins must be 256 and the histogram range range must
	 * be
	 * the same as the image range expect for 32 bit images.
	 */
	public void draw(ImagePlus imp, int bins, double histMin, double histMax, int yMax) {
		boolean limitToThreshold = (Analyzer.getMeasurements() & LIMIT) != 0;
		ImageProcessor ip = imp.getProcessor();
		if (ip.getMinThreshold() != ImageProcessor.NO_THRESHOLD
				&& ip.getLutUpdateMode() == ImageProcessor.NO_LUT_UPDATE)
			limitToThreshold = false; // ignore invisible thresholds
		if (imp.isRGB() && rgbMode < INTENSITY1)
			rgbMode = INTENSITY1;
		if (rgbMode == RED || rgbMode == GREEN || rgbMode == BLUE) {
			int channel = rgbMode - 2;
			ColorProcessor cp = (ColorProcessor) imp.getProcessor();
			ip = cp.getChannel(channel, null);
			ImagePlus imp2 = new ImagePlus("", ip);
			imp2.setRoi(imp.getRoi());
			stats = imp2.getStatistics(AREA + MEAN + MODE + MIN_MAX, bins, histMin, histMax);
		} else if (rgbMode == RGB)
			stats = RGBHistogram(imp, bins, histMin, histMax);
		else
			stats = imp.getStatistics(AREA + MEAN + MODE + MIN_MAX + (limitToThreshold ? LIMIT : 0), bins, histMin,
					histMax);
		draw(imp, stats);
	}

	private ImageStatistics RGBHistogram(ImagePlus imp, int bins, double histMin, double histMax) {
		ImageProcessor ip = (ColorProcessor) imp.getProcessor();
		ip = ip.crop();
		int w = ip.getWidth();
		int h = ip.getHeight();
		ImageProcessor ip2 = new ByteProcessor(w * 3, h);
		ByteProcessor temp = null;
		for (int i = 0; i < 3; i++) {
			temp = ((ColorProcessor) ip).getChannel(i + 1, temp);
			ip2.insert(temp, i * w, 0);
		}
		ImagePlus imp2 = new ImagePlus("imp2", ip2);
		return imp2.getStatistics(AREA + MEAN + MODE + MIN_MAX, bins, histMin, histMax);
	}

	/** Draws the histogram using the specified title and ImageStatistics. */
	public void draw(ImagePlus imp, ImageStatistics stats) {
		if (imp.isRGB() && rgbMode < INTENSITY1)
			rgbMode = INTENSITY1;
		stackHistogram = stats.stackStatistics;
		this.stats = stats;
		this.yMax = stats.histYMax;
		cal = imp.getCalibration();
		imp.getMask();
		histogram = stats.getHistogram();
		lut = imp.createLut();
		int type = imp.getType();
		boolean fixedRange = type == ImagePlus.GRAY8 || type == ImagePlus.COLOR_256 || imp.isRGB();
		ip.setColor(Color.white);
		ip.resetRoi();
		ip.fill();
		drawHistogram(imp, ip, fixedRange, stats.histMin, stats.histMax);
	}

	protected void drawHistogram(ImageProcessor ip, boolean fixedRange) {
		drawHistogram(null, ip, fixedRange, 0.0, 0.0);
	}

	void drawHistogram(ImagePlus imp, ImageProcessor ip, boolean fixedRange, double xMin, double xMax) {
		histogramDraw.drawHistogram(imp, ip, fixedRange, xMin, xMax);
	}

	public void drawAlignedColorBar(ImagePlus imp, double xMin, double xMax, ImageProcessor ip, int x, int y, int width,
			int height) {
		ImageProcessor ipSource = imp.getProcessor();
		float[] pixels = null;
		ImageProcessor ipRamp = null;
		if (rgbMode >= INTENSITY1) {
			ipRamp = new FloatProcessor(width, height);
			if (rgbMode == RED)
				ipRamp.setColorModel(LUT.createLutFromColor(Color.red));
			else if (rgbMode == GREEN)
				ipRamp.setColorModel(LUT.createLutFromColor(Color.green));
			else if (rgbMode == BLUE)
				ipRamp.setColorModel(LUT.createLutFromColor(Color.blue));
			pixels = (float[]) ipRamp.getPixels();
		} else
			pixels = new float[width * height];
		for (int j = 0; j < height; j++) {
			for (int i = 0; i < width; i++)
				pixels[i + width * j] = (float) (xMin + i * (xMax - xMin) / (width - 1));
		}
		double min = ipSource.getMin();
		double max = ipSource.getMax();
		if (ipSource.getNChannels() == 1) {
			ColorModel cm = null;
			if (imp.isComposite()) {
				if (stats != null && stats.pixelCount > ipSource.getPixelCount()) { // stack histogram
					cm = LUT.createLutFromColor(Color.white);
					min = stats.min;
					max = stats.max;
				} else
					cm = ((CompositeImage) imp).getChannelLut();
			} else if (ipSource.getMinThreshold() == ImageProcessor.NO_THRESHOLD)
				cm = ipSource.getColorModel();
			else
				cm = ipSource.getCurrentColorModel();
			ipRamp = new FloatProcessor(width, height, pixels, cm);
		}
		ipRamp.setMinAndMax(min, max);
		ImageProcessor bar = null;
		if (ip instanceof ColorProcessor)
			bar = ipRamp.convertToRGB();
		else
			bar = ipRamp.convertToByte(true);
		ip.insert(bar, x, y);
		ip.setColor(Color.black);
		ip.drawRect(x - 1, y, width + 2, height);
	}

	/** Scales a threshold level to the range 0-255. */
	int scaleDown(ImageProcessor ip, double threshold) {
		double min = ip.getMin();
		double max = ip.getMax();
		if (max > min)
			return (int) (((threshold - min) / (max - min)) * 255.0);
		else
			return 0;
	}

	public void drawPlot(long maxCount, ImageProcessor ip) {
		if (maxCount == 0)
			maxCount = 1;
		frame = new Rectangle(XMARGIN, YMARGIN, HIST_WIDTH, HIST_HEIGHT);
		if (histogram.length == 256) {
			double scale2 = HIST_WIDTH / 256.0;
			int barWidth = 1;
			if (SCALE > 1)
				barWidth = 2;
			if (SCALE > 2)
				barWidth = 3;
			for (int i = 0; i < 256; i++) {
				int x = (int) (i * scale2);
				int y = (int) (((double) HIST_HEIGHT * (double) histogram[i]) / maxCount);
				if (y > HIST_HEIGHT)
					y = HIST_HEIGHT;
				for (int j = 0; j < barWidth; j++)
					ip.drawLine(x + j + XMARGIN, YMARGIN + HIST_HEIGHT, x + j + XMARGIN, YMARGIN + HIST_HEIGHT - y);
			}
		} else if (histogram.length <= HIST_WIDTH) {
			int index, y;
			for (int i = 0; i < HIST_WIDTH; i++) {
				index = (int) (i * (double) histogram.length / HIST_WIDTH);
				y = (int) (((double) HIST_HEIGHT * (double) histogram[index]) / maxCount);
				if (y > HIST_HEIGHT)
					y = HIST_HEIGHT;
				ip.drawLine(i + XMARGIN, YMARGIN + HIST_HEIGHT, i + XMARGIN, YMARGIN + HIST_HEIGHT - y);
			}
		} else {
			double xscale = (double) HIST_WIDTH / histogram.length;
			for (int i = 0; i < histogram.length; i++) {
				long value = histogram[i];
				if (value > 0L) {
					int y = (int) (((double) HIST_HEIGHT * (double) value) / maxCount);
					if (y > HIST_HEIGHT)
						y = HIST_HEIGHT;
					int x = (int) (i * xscale) + XMARGIN;
					ip.drawLine(x, YMARGIN + HIST_HEIGHT, x, YMARGIN + HIST_HEIGHT - y);
				}
			}
		}
		ip.setColor(frameColor);
		ip.drawRect(frame.x - 1, frame.y, frame.width + 2, frame.height + 1);
		ip.setColor(Color.black);
	}

	public void drawLogPlot(long maxCount, ImageProcessor ip) {
		frame = new Rectangle(XMARGIN, YMARGIN, HIST_WIDTH, HIST_HEIGHT);
		ip.drawRect(frame.x - 1, frame.y, frame.width + 2, frame.height + 1);
		double max = Math.log(maxCount);
		ip.setColor(Color.gray);
		if (histogram.length == 256) {
			double scale2 = HIST_WIDTH / 256.0;
			int barWidth = 1;
			if (SCALE > 1)
				barWidth = 2;
			if (SCALE > 2)
				barWidth = 3;
			for (int i = 0; i < 256; i++) {
				int x = (int) (i * scale2);
				int y = histogram[i] == 0 ? 0 : (int) (HIST_HEIGHT * Math.log(histogram[i]) / max);
				if (y > HIST_HEIGHT)
					y = HIST_HEIGHT;
				for (int j = 0; j < barWidth; j++)
					ip.drawLine(x + j + XMARGIN, YMARGIN + HIST_HEIGHT, x + j + XMARGIN, YMARGIN + HIST_HEIGHT - y);
			}
		} else if (histogram.length <= HIST_WIDTH) {
			int index, y;
			for (int i = 0; i < HIST_WIDTH; i++) {
				index = (int) (i * (double) histogram.length / HIST_WIDTH);
				y = histogram[index] == 0 ? 0 : (int) (HIST_HEIGHT * Math.log(histogram[index]) / max);
				if (y > HIST_HEIGHT)
					y = HIST_HEIGHT;
				ip.drawLine(i + XMARGIN, YMARGIN + HIST_HEIGHT, i + XMARGIN, YMARGIN + HIST_HEIGHT - y);
			}
		} else {
			double xscale = (double) HIST_WIDTH / histogram.length;
			for (int i = 0; i < histogram.length; i++) {
				long value = histogram[i];
				if (value > 0L) {
					int y = (int) (HIST_HEIGHT * Math.log(value) / max);
					if (y > HIST_HEIGHT)
						y = HIST_HEIGHT;
					int x = (int) (i * xscale) + XMARGIN;
					ip.drawLine(x, YMARGIN + HIST_HEIGHT, x, YMARGIN + HIST_HEIGHT - y);
				}
			}
		}
		ip.setColor(Color.black);
	}

	void drawText(ImageProcessor ip, int x, int y, boolean fixedRange) {
		histogramDraw.drawText(ip, x, y, fixedRange);
	}

	int getWidth(double d, ImageProcessor ip) {
		return HistogramDraw.getWidth(d, ip);
	}

	public long[] getLongHistogram() {
		return histogram;
	}

	public double[] getXValues() {
		double[] values = new double[stats.nBins];
		for (int i = 0; i < stats.nBins; i++)
			values[i] = cal.getCValue(stats.histMin + i * stats.binSize);
		return values;
	}

	@Override
	public void show() {
		if (IJ.isMacro() && Interpreter.isBatchMode())
			super.show();
		else
			new HistogramWindow(this, WindowManager.getImage(srcImageID));
	}

	public static double getScale() {
		return SCALE;
	}

	public static int getHistWidth() {
		return HIST_WIDTH;
	}

	public static int getHistHeight() {
		return HIST_HEIGHT;
	}

	public static int getXmargin() {
		return XMARGIN;
	}

	public static int getYmargin() {
		return YMARGIN;
	}

	public static int getWinWidth() {
		return WIN_WIDTH;
	}

	public static int getWinHeight() {
		return WIN_HEIGHT;
	}

	public static int getBarHeight() {
		return BAR_HEIGHT;
	}

	public static int getIntensity1() {
		return INTENSITY1;
	}

	public static int getIntensity2() {
		return INTENSITY2;
	}

	public static int getRgb() {
		return RGB;
	}

	public static int getRed() {
		return RED;
	}

	public static int getGreen() {
		return GREEN;
	}

	public static int getBlue() {
		return BLUE;
	}

	public static Color getFramecolor() {
		return frameColor;
	}

	public int getRgbMode() {
		return rgbMode;
	}

	public ImageStatistics getStats() {
		return stats;
	}

	public boolean isStackHistogram() {
		return stackHistogram;
	}

	public Calibration getCal() {
		return cal;
	}

	public LookUpTable getLut() {
		return lut;
	}

	public int getDecimalPlaces() {
		return decimalPlaces;
	}

	public int getDigits() {
		return digits;
	}

	public long getNewMaxCount() {
		return newMaxCount;
	}

	public boolean isLogScale() {
		return logScale;
	}

	public int getyMax() {
		return yMax;
	}

	public int getSrcImageID() {
		return srcImageID;
	}

	public Font getFont() {
		return font;
	}

	public boolean isShowBins() {
		return showBins;
	}

	public int getCol1() {
		return col1;
	}

	public int getCol2() {
		return col2;
	}

	public int getRow1() {
		return row1;
	}

	public int getRow2() {
		return row2;
	}

	public int getRow3() {
		return row3;
	}

	public int getRow4() {
		return row4;
	}

	public int getRow5() {
		return row5;
	}

	public void setRgbMode(int rgbMode) {
		this.rgbMode = rgbMode;
	}

	public void setStats(ImageStatistics stats) {
		this.stats = stats;
	}

	public void setStackHistogram(boolean stackHistogram) {
		this.stackHistogram = stackHistogram;
	}

	public void setCal(Calibration cal) {
		this.cal = cal;
	}

	public void setHistogram(long[] histogram) {
		this.histogram = histogram;
	}

	public void setLut(LookUpTable lut) {
		this.lut = lut;
	}

	public void setDecimalPlaces(int decimalPlaces) {
		this.decimalPlaces = decimalPlaces;
	}

	public void setDigits(int digits) {
		this.digits = digits;
	}

	public void setNewMaxCount(long newMaxCount) {
		this.newMaxCount = newMaxCount;
	}

	public void setLogScale(boolean logScale) {
		this.logScale = logScale;
	}

	public void setyMax(int yMax) {
		this.yMax = yMax;
	}

	public void setSrcImageID(int srcImageID) {
		this.srcImageID = srcImageID;
	}

	public void setFrame(Rectangle frame) {
		this.frame = frame;
	}

	public void setFont(Font font) {
		this.font = font;
	}

	public void setShowBins(boolean showBins) {
		this.showBins = showBins;
	}

	public void setCol1(int col1) {
		this.col1 = col1;
	}

	public void setCol2(int col2) {
		this.col2 = col2;
	}

	public void setRow1(int row1) {
		this.row1 = row1;
	}

	public void setRow2(int row2) {
		this.row2 = row2;
	}

	public void setRow3(int row3) {
		this.row3 = row3;
	}

	public void setRow4(int row4) {
		this.row4 = row4;
	}

	public void setRow5(int row5) {
		this.row5 = row5;
	}

	public boolean shouldDrawLogPlot() {
		return logScale;
	}
}
