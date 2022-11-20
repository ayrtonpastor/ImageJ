package ij.gui;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;
import java.awt.datatransfer.*;
import ij.*;
import ij.gui.utils.HistogramDraw;
import ij.gui.utils.HistogramDrawProperties;
import ij.gui.utils.IHistogramDraw;
import ij.process.*;
import ij.measure.*;
import ij.plugin.filter.Analyzer;

/** This class is an extended ImageWindow that displays histograms. */
public class HistogramWindow extends ImageWindow implements Measurements, ActionListener,
		ClipboardOwner, ImageListener, RoiListener, Runnable, IHistogramDraw {

	static final int INTENSITY1 = 0, INTENSITY2 = 1, RGB = 2, RED = 3, GREEN = 4, BLUE = 5;

	protected Rectangle frame = null;
	protected Button list, save, copy, log, live, rgb;
	protected Label value, count;
	protected static String defaultDirectory = null;
	protected int plotScale = 1;
	public static int nBins = 256;

	private ImagePlus srcImp; // source image for live histograms
	private Thread bgThread; // thread background drawing
	private boolean doUpdate; // tells background thread to update
	private String blankLabel;
	private boolean stackHistogram;

	private HistogramDrawProperties windowProps = new HistogramDrawProperties();
	private HistogramDraw histogramDraw = new HistogramDraw(this);

	public HistogramWindow(HistogramPlot plot, ImagePlus srcImp) {
		super(plot);
		this.windowProps = plot.getHistogramDrawProperties();
		if (list == null)
			setup(srcImp);
	}

	/** Displays a histogram using the title "Histogram of ImageName". */
	public HistogramWindow(ImagePlus imp) {
		super(NewImage.createRGBImage("Histogram of " + imp.getShortTitle(), WIN_WIDTH, WIN_HEIGHT, 1,
				NewImage.FILL_WHITE));
		showHistogram(imp, 256, 0.0, 0.0);
	}

	/**
	 * Displays a histogram using the specified title and number of bins.
	 * Currently, the number of bins must be 256 expect for 32 bit images.
	 */
	public HistogramWindow(String title, ImagePlus imp, int bins) {
		super(NewImage.createRGBImage(title, WIN_WIDTH, WIN_HEIGHT, 1, NewImage.FILL_WHITE));
		showHistogram(imp, bins, 0.0, 0.0);
	}

	/**
	 * Displays a histogram using the specified title, number of bins and histogram
	 * range.
	 * Currently, the number of bins must be 256 and the histogram range range must
	 * be the
	 * same as the image range expect for 32 bit images.
	 */
	public HistogramWindow(String title, ImagePlus imp, int bins, double histMin, double histMax) {
		super(NewImage.createRGBImage(title, WIN_WIDTH, WIN_HEIGHT, 1, NewImage.FILL_WHITE));
		showHistogram(imp, bins, histMin, histMax);
	}

	/**
	 * Displays a histogram using the specified title, number of bins, histogram
	 * range and yMax.
	 */
	public HistogramWindow(String title, ImagePlus imp, int bins, double histMin, double histMax, int yMax) {
		super(NewImage.createRGBImage(title, WIN_WIDTH, WIN_HEIGHT, 1, NewImage.FILL_WHITE));
		windowProps.yMax = yMax;
		showHistogram(imp, bins, histMin, histMax);
	}

	/** Displays a histogram using the specified title and ImageStatistics. */
	public HistogramWindow(String title, ImagePlus imp, ImageStatistics stats) {
		super(NewImage.createRGBImage(title, WIN_WIDTH, WIN_HEIGHT, 1, NewImage.FILL_WHITE));
		windowProps.yMax = stats.histYMax;
		showHistogram(imp, stats);
	}

	/**
	 * Draws the histogram using the specified title and number of bins.
	 * Currently, the number of bins must be 256 expect for 32 bit images.
	 */
	public void showHistogram(ImagePlus imp, int bins) {
		showHistogram(imp, bins, 0.0, 0.0);
	}

	/**
	 * Draws the histogram using the specified title, number of bins and histogram
	 * range.
	 * Currently, the number of bins must be 256 and the histogram range range must
	 * be
	 * the same as the image range expect for 32 bit images.
	 */
	public void showHistogram(ImagePlus imp, int bins, double histMin, double histMax) {
		boolean limitToThreshold = (Analyzer.getMeasurements() & LIMIT) != 0;
		ImageProcessor ip = imp.getProcessor();
		if (ip.getMinThreshold() != ImageProcessor.NO_THRESHOLD
				&& ip.getLutUpdateMode() == ImageProcessor.NO_LUT_UPDATE)
			limitToThreshold = false; // ignore invisible thresholds
		if (imp.isRGB() && windowProps.rgbMode < INTENSITY1)
			windowProps.rgbMode = INTENSITY1;
		if (windowProps.rgbMode == RED || windowProps.rgbMode == GREEN || windowProps.rgbMode == BLUE) {
			int channel = windowProps.rgbMode - 2;
			ColorProcessor cp = (ColorProcessor) imp.getProcessor();
			ip = cp.getChannel(channel, null);
			ImagePlus imp2 = new ImagePlus("", ip);
			imp2.setRoi(imp.getRoi());
			windowProps.stats = imp2.getStatistics(AREA + MEAN + MODE + MIN_MAX, bins, histMin, histMax);
		} else if (windowProps.rgbMode == RGB)
			windowProps.stats = RGBHistogram(imp, bins, histMin, histMax);
		else
			windowProps.stats = imp.getStatistics(AREA + MEAN + MODE + MIN_MAX + (limitToThreshold ? LIMIT : 0), bins,
					histMin,
					histMax);
		showHistogram(imp, windowProps.stats);
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
	public void showHistogram(ImagePlus srcImp, ImageStatistics stats) {
		if (srcImp.isRGB() && windowProps.rgbMode < INTENSITY1)
			windowProps.rgbMode = INTENSITY1;
		stackHistogram = stats.stackStatistics;
		if (list == null)
			setup(srcImp);
		windowProps.stats = stats;
		windowProps.cal = srcImp.getCalibration();
		boolean limitToThreshold = (Analyzer.getMeasurements() & LIMIT) != 0;
		srcImp.getMask();
		windowProps.histogram = stats.getHistogram();
		if (limitToThreshold && windowProps.histogram.length == 256) {
			ImageProcessor ip = srcImp.getProcessor();
			if (ip.getMinThreshold() != ImageProcessor.NO_THRESHOLD) {
				int lower = scaleDown(ip, ip.getMinThreshold());
				int upper = scaleDown(ip, ip.getMaxThreshold());
				for (int i = 0; i < lower; i++)
					windowProps.histogram[i] = 0L;
				for (int i = upper + 1; i < 256; i++)
					windowProps.histogram[i] = 0L;
			}
		}
		windowProps.lut = srcImp.createLut();
		int type = srcImp.getType();
		boolean fixedRange = type == ImagePlus.GRAY8 || type == ImagePlus.COLOR_256 || srcImp.isRGB();
		if (imp == null) {
			IJ.showStatus("imp==null");
			return;
		}
		ImageProcessor ip = imp.getProcessor();
		ip.setColor(Color.white);
		ip.resetRoi();
		ip.fill();
		drawHistogram(srcImp, ip, fixedRange, stats.histMin, stats.histMax);
		imp.updateAndDraw();
	}

	private void setup(ImagePlus imp) {
		boolean isRGB = imp.isRGB();
		Panel buttons = new Panel();
		int hgap = IJ.isMacOSX() || isRGB ? 1 : 5;
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT, hgap, 0));
		int trim = IJ.isMacOSX() ? 6 : 0;
		list = new TrimmedButton("List", trim);
		list.addActionListener(this);
		buttons.add(list);
		copy = new TrimmedButton("Copy", trim);
		copy.addActionListener(this);
		buttons.add(copy);
		log = new TrimmedButton("Log", trim);
		log.addActionListener(this);
		buttons.add(log);
		if (!stackHistogram) {
			live = new TrimmedButton("Live", trim);
			live.addActionListener(this);
			buttons.add(live);
		}
		if (imp != null && isRGB && !stackHistogram) {
			rgb = new TrimmedButton("RGB", trim);
			rgb.addActionListener(this);
			buttons.add(rgb);
		}
		value = new Label(" ");
		count = new Label(" ");
		add(buttons);
		GUI.scale(buttons);
		pack();
		if (IJ.isMacOSX() && IJ.isJava18()) {
			IJ.wait(50);
			pack();
		}
	}

	public void setup() {
		setup(null);
	}

	public void mouseMoved(int x, int y) {
		ImageProcessor ip = this.imp != null ? this.imp.getProcessor() : null;
		if (ip == null)
			return;
		if ((frame != null) && x >= frame.x && x <= (frame.x + frame.width)) {
			x = (x - frame.x);
			int index = (int) (x * (SCALE * windowProps.histogram.length) / HIST_WIDTH / SCALE);
			if (index >= windowProps.histogram.length)
				index = windowProps.histogram.length - 1;
			double value = windowProps.cal.getCValue(windowProps.stats.histMin + index * windowProps.stats.binSize);
			drawValueAndCount(ip, value, windowProps.histogram[index]);
		} else
			drawValueAndCount(ip, Double.NaN, -1);
		this.imp.updateAndDraw();
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
		if (windowProps.rgbMode >= INTENSITY1) {
			ipRamp = new FloatProcessor(width, height);
			if (windowProps.rgbMode == RED)
				ipRamp.setColorModel(LUT.createLutFromColor(Color.red));
			else if (windowProps.rgbMode == GREEN)
				ipRamp.setColorModel(LUT.createLutFromColor(Color.green));
			else if (windowProps.rgbMode == BLUE)
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
				if (windowProps.stats != null && windowProps.stats.pixelCount > ipSource.getPixelCount()) { // stack
					// histogram
					cm = LUT.createLutFromColor(Color.white);
					min = windowProps.stats.min;
					max = windowProps.stats.max;
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
	}

	public void drawLogPlot(long maxCount, ImageProcessor ip) {
		this.histogramDraw.drawLogPlot(maxCount, ip);
	}

	void drawText(ImageProcessor ip, int x, int y, boolean fixedRange) {
		histogramDraw.drawText(ip, x, y, fixedRange);
		drawValueAndCount(ip, Double.NaN, -1);
	}

	private void drawValueAndCount(ImageProcessor ip, double value, long count) {
		int y = windowProps.showBins ? windowProps.row4 : windowProps.row3;
		ip.setRoi(0, y, WIN_WIDTH, WIN_HEIGHT - y);
		ip.setColor(Color.white);
		ip.fill();
		ip.setColor(Color.black);
		String sValue = Double.isNaN(value) ? "---" : d2s(value);
		String sCount = count == -1 ? "---" : "" + count;
		int row = windowProps.showBins ? windowProps.row5 : windowProps.row4;
		ip.drawString("Value: " + sValue, windowProps.col1, row);
		ip.drawString("Count: " + sCount, windowProps.col2, row);
	}

	private String d2s(double d) {
		if ((int) d == d)
			return IJ.d2s(d, 0);
		else
			return IJ.d2s(d, 3, 8);
	}

	int getWidth(double d, ImageProcessor ip) {
		return ip.getStringWidth(d2s(d));
	}

	/** Returns the histogram values as a ResultsTable. */
	public ResultsTable getResultsTable() {
		int decimalPlaces = Analyzer.getPrecision();
		if (windowProps.digits == 0 && windowProps.stats.binSize != 1.0)
			windowProps.digits = decimalPlaces;
		ResultsTable rt = new ResultsTable();
		rt.setPrecision(windowProps.digits);
		String vheading = windowProps.stats.binSize == 1.0 ? "value" : "bin start";
		if (windowProps.cal.calibrated() && !windowProps.cal.isSigned16Bit()) {
			for (int i = 0; i < windowProps.stats.nBins; i++) {
				rt.setValue("level", i, i);
				rt.setValue(vheading, i,
						windowProps.cal.getCValue(windowProps.stats.histMin + i * windowProps.stats.binSize));
				rt.setValue("count", i, windowProps.histogram[i]);
			}
		} else {
			for (int i = 0; i < windowProps.stats.nBins; i++) {
				if (windowProps.stats.binSize != 1.0)
					rt.setValue("index", i, i);
				rt.setValue(vheading, i,
						windowProps.cal.getCValue(windowProps.stats.histMin + i * windowProps.stats.binSize));
				rt.setValue("count", i, windowProps.histogram[i]);
			}
		}
		return rt;
	}

	protected void showList() {
		ResultsTable rt = getResultsTable();
		rt.show(getTitle());
	}

	protected void copyToClipboard() {
		Clipboard systemClipboard = null;
		try {
			systemClipboard = getToolkit().getSystemClipboard();
		} catch (Exception e) {
			systemClipboard = null;
		}
		if (systemClipboard == null) {
			IJ.error("Unable to copy to Clipboard.");
			return;
		}
		IJ.showStatus("Copying histogram values...");
		CharArrayWriter aw = new CharArrayWriter(windowProps.stats.nBins * 4);
		PrintWriter pw = new PrintWriter(aw);
		for (int i = 0; i < windowProps.stats.nBins; i++)
			pw.print(ResultsTable.d2s(
					windowProps.cal.getCValue(windowProps.stats.histMin + i * windowProps.stats.binSize),
					windowProps.digits) + "\t" + windowProps.histogram[i]
					+ "\n");
		String text = aw.toString();
		pw.close();
		StringSelection contents = new StringSelection(text);
		systemClipboard.setContents(contents, this);
		IJ.showStatus(text.length() + " characters copied to Clipboard");
	}

	void replot() {
		ImageProcessor ip = this.imp.getProcessor();
		frame = new Rectangle(XMARGIN, YMARGIN, HIST_WIDTH, HIST_HEIGHT);
		ip.setColor(Color.white);
		ip.setRoi(frame.x - 1, frame.y, frame.width + 2, frame.height);
		ip.fill();
		ip.resetRoi();
		ip.setColor(Color.black);
		ip.setLineWidth(1);
		if (windowProps.logScale) {
			drawLogPlot(windowProps.yMax > 0 ? windowProps.yMax : windowProps.newMaxCount, ip);
			drawPlot(windowProps.yMax > 0 ? windowProps.yMax : windowProps.newMaxCount, ip);
		} else
			drawPlot(windowProps.yMax > 0 ? windowProps.yMax : windowProps.newMaxCount, ip);
		this.imp.updateAndDraw();
	}

	public void actionPerformed(ActionEvent e) {
		Object b = e.getSource();
		if (b == live)
			toggleLiveMode();
		else if (b == rgb)
			changeChannel();
		else if (b == list)
			showList();
		else if (b == copy)
			copyToClipboard();
		else if (b == log) {
			windowProps.logScale = !windowProps.logScale;
			replot();
		}
	}

	public void lostOwnership(Clipboard clipboard, Transferable contents) {
	}

	public int[] getHistogram() {
		int[] hist = new int[windowProps.histogram.length];
		for (int i = 0; i < windowProps.histogram.length; i++)
			hist[i] = (int) windowProps.histogram[i];
		return hist;
	}

	public long[] getLongHistogram() {
		return windowProps.histogram;
	}

	public double[] getXValues() {
		double[] values = new double[windowProps.stats.nBins];
		for (int i = 0; i < windowProps.stats.nBins; i++)
			values[i] = windowProps.cal.getCValue(windowProps.stats.histMin + i * windowProps.stats.binSize);
		return values;
	}

	private void toggleLiveMode() {
		if (liveMode())
			removeListeners();
		else
			enableLiveMode();
	}

	private void changeChannel() {
		ImagePlus imp = WindowManager.getImage(windowProps.srcImageID);
		if (imp == null || !imp.isRGB())
			return;
		else {
			windowProps.rgbMode++;
			if (windowProps.rgbMode > BLUE)
				windowProps.rgbMode = INTENSITY1;
			ColorProcessor cp = (ColorProcessor) imp.getProcessor();
			boolean weighted = cp.weightedHistogram();
			if (windowProps.rgbMode == INTENSITY2) {
				double[] weights = cp.getRGBWeights();
				if (weighted)
					cp.setRGBWeights(1d / 3d, 1d / 3d, 1d / 3d);
				else
					cp.setRGBWeights(0.299, 0.587, 0.114);
				showHistogram(imp, 256);
				cp.setRGBWeights(weights);
			} else
				showHistogram(imp, 256);
		}
	}

	private boolean liveMode() {
		return live != null && live.getForeground() == Color.red;
	}

	private void enableLiveMode() {
		if (bgThread == null) {
			srcImp = WindowManager.getImage(windowProps.srcImageID);
			if (srcImp == null)
				return;
			bgThread = new Thread(this, "Live Histogram");
			bgThread.setPriority(Math.max(bgThread.getPriority() - 3, Thread.MIN_PRIORITY));
			bgThread.start();
			imageUpdated(srcImp);
		}
		createListeners();
		if (srcImp != null)
			imageUpdated(srcImp);
	}

	// Unused
	public void imageOpened(ImagePlus imp) {
	}

	// This listener is called if the source image content is changed
	public synchronized void imageUpdated(ImagePlus imp) {
		if (imp == srcImp) {
			doUpdate = true;
			notify();
		}
	}

	public synchronized void roiModified(ImagePlus img, int id) {
		if (img == srcImp) {
			doUpdate = true;
			notify();
		}
	}

	// If either the source image or this image are closed, exit
	public void imageClosed(ImagePlus imp) {
		if (imp == srcImp || imp == this.imp) {
			if (bgThread != null)
				bgThread.interrupt();
			bgThread = null;
			removeListeners();
			srcImp = null;
		}
	}

	// the background thread for live plotting.
	public void run() {
		while (true) {
			if (doUpdate && srcImp != null) {
				if (srcImp.getRoi() != null)
					IJ.wait(50); // delay to make sure the roi has been updated
				if (srcImp != null) {
					if (srcImp.getBitDepth() == 16 && ImagePlus.getDefault16bitRange() != 0)
						showHistogram(srcImp, 256, 0, Math.pow(2, ImagePlus.getDefault16bitRange()) - 1);
					else
						showHistogram(srcImp, 256);
				}
			}
			synchronized (this) {
				if (doUpdate) {
					doUpdate = false; // and loop again
				} else {
					try {
						wait();
					} // notify wakes up the thread
					catch (InterruptedException e) { // interrupted tells the thread to exit
						return;
					}
				}
			}
		}
	}

	private void createListeners() {
		if (srcImp == null)
			return;
		ImagePlus.addImageListener(this);
		Roi.addRoiListener(this);
		if (live != null) {
			Font font = live.getFont();
			live.setFont(new Font(font.getName(), Font.BOLD, font.getSize()));
			live.setForeground(Color.red);
		}
	}

	private void removeListeners() {
		if (srcImp == null)
			return;
		ImagePlus.removeImageListener(this);
		Roi.removeRoiListener(this);
		if (live != null) {
			Font font = live.getFont();
			live.setFont(new Font(font.getName(), Font.PLAIN, font.getSize()));
			live.setForeground(Color.black);
		}
	}

	public boolean shouldDrawLogPlot() {
		return windowProps.logScale || IJ.shiftKeyDown() && !liveMode();
	}

	public HistogramDrawProperties getHistogramDrawProperties() {
		return windowProps;
	}
}
