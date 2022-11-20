package ij.gui;

import ij.*;
import ij.gui.utils.HistogramDraw;
import ij.gui.utils.HistogramDrawProperties;
import ij.gui.utils.IHistogramDraw;
import ij.process.*;
import ij.plugin.filter.Analyzer;
import ij.macro.Interpreter;
import java.awt.*;
import java.awt.image.*;

public class HistogramPlot extends ImagePlus implements IHistogramDraw {
	boolean stackHistogram;
	boolean logScale;
	HistogramDraw histogramDraw;
	HistogramDrawProperties plotProps = new HistogramDrawProperties();

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
		if (imp.isRGB() && plotProps.rgbMode < INTENSITY1)
			plotProps.rgbMode = INTENSITY1;
		if (plotProps.rgbMode == RED || plotProps.rgbMode == GREEN || plotProps.rgbMode == BLUE) {
			int channel = plotProps.rgbMode - 2;
			ColorProcessor cp = (ColorProcessor) imp.getProcessor();
			ip = cp.getChannel(channel, null);
			ImagePlus imp2 = new ImagePlus("", ip);
			imp2.setRoi(imp.getRoi());
			plotProps.stats = imp2.getStatistics(AREA + MEAN + MODE + MIN_MAX, bins, histMin, histMax);
		} else if (plotProps.rgbMode == RGB)
			plotProps.stats = RGBHistogram(imp, bins, histMin, histMax);
		else
			plotProps.stats = imp.getStatistics(AREA + MEAN + MODE + MIN_MAX + (limitToThreshold ? LIMIT : 0), bins,
					histMin,
					histMax);
		draw(imp, plotProps.stats);
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
		if (imp.isRGB() && plotProps.rgbMode < INTENSITY1)
			plotProps.rgbMode = INTENSITY1;
		stackHistogram = stats.stackStatistics;
		this.plotProps.stats = stats;
		this.plotProps.yMax = stats.histYMax;
		plotProps.cal = imp.getCalibration();
		imp.getMask();
		plotProps.histogram = stats.getHistogram();
		plotProps.lut = imp.createLut();
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
		if (plotProps.rgbMode >= INTENSITY1) {
			ipRamp = new FloatProcessor(width, height);
			if (plotProps.rgbMode == RED)
				ipRamp.setColorModel(LUT.createLutFromColor(Color.red));
			else if (plotProps.rgbMode == GREEN)
				ipRamp.setColorModel(LUT.createLutFromColor(Color.green));
			else if (plotProps.rgbMode == BLUE)
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
				if (plotProps.stats != null && plotProps.stats.pixelCount > ipSource.getPixelCount()) { // stack
																										// histogram
					cm = LUT.createLutFromColor(Color.white);
					min = plotProps.stats.min;
					max = plotProps.stats.max;
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
		this.histogramDraw.drawPlot(maxCount, ip);
		ip.setColor(frameColor);
		ip.drawRect(plotProps.frame.x - 1, plotProps.frame.y, plotProps.frame.width + 2, plotProps.frame.height + 1);
		ip.setColor(Color.black);
	}

	public void drawLogPlot(long maxCount, ImageProcessor ip) {
		this.histogramDraw.drawLogPlot(maxCount, ip);
	}

	void drawText(ImageProcessor ip, int x, int y, boolean fixedRange) {
		histogramDraw.drawText(ip, x, y, fixedRange);
	}

	int getWidth(double d, ImageProcessor ip) {
		return HistogramDraw.getWidth(d, ip);
	}

	public long[] getLongHistogram() {
		return plotProps.histogram;
	}

	public double[] getXValues() {
		double[] values = new double[plotProps.stats.nBins];
		for (int i = 0; i < plotProps.stats.nBins; i++)
			values[i] = plotProps.cal.getCValue(plotProps.stats.histMin + i * plotProps.stats.binSize);
		return values;
	}

	@Override
	public void show() {
		if (IJ.isMacro() && Interpreter.isBatchMode())
			super.show();
		else
			new HistogramWindow(this, WindowManager.getImage(plotProps.srcImageID));
	}

	public boolean shouldDrawLogPlot() {
		return logScale;
	}

	public HistogramDrawProperties getHistogramDrawProperties() {
		return plotProps;
	}
}
