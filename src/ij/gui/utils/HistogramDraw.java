package ij.gui.utils;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.HistogramPlot;
import ij.plugin.filter.Analyzer;
import ij.process.*;
import java.awt.*;

public class HistogramDraw {

	public static String d2s(double d) {
		if ((int) d == d)
			return IJ.d2s(d, 0);
		else
			return IJ.d2s(d, 3, 8);
	}

	public static int getWidth(double d, ImageProcessor ip) {
		return ip.getStringWidth(HistogramDraw.d2s(d));
	}

	private IHistogramDraw plot;
	private HistogramDrawProperties dp;

	public HistogramDraw(IHistogramDraw plot) {
		this.plot = plot;
		this.dp = plot.getHistogramDrawProperties();
	}

	public void drawHistogram(ImagePlus imp, ImageProcessor ip, boolean fixedRange, double xMin,
			double xMax) {
		if (plot instanceof HistogramPlot)
			((HistogramPlot) plot).setTitle("Histogram of " + imp.getShortTitle());
		int x, y;
		long maxCount2 = 0;
		long saveModalCount;
		ip.setColor(Color.black);
		ip.setLineWidth(1);
		dp.decimalPlaces = Analyzer.getPrecision();
		dp.digits = dp.cal.calibrated() || dp.stats.binSize != 1.0 ? dp.decimalPlaces : 0;
		saveModalCount = dp.histogram[dp.stats.mode];
		for (int i = 0; i < dp.histogram.length; i++) {
			if ((dp.histogram[i] > maxCount2) && (i != dp.stats.mode)) {
				maxCount2 = dp.histogram[i];
			}
		}
		dp.newMaxCount = dp.histogram[dp.stats.mode];
		if ((dp.newMaxCount > (maxCount2 * 2)) && (maxCount2 != 0))
			dp.newMaxCount = (int) (maxCount2 * 1.5);
		if (plot.shouldDrawLogPlot())
			this.drawLogPlot(dp.yMax > 0 ? dp.yMax : dp.newMaxCount, ip);
		this.drawPlot(dp.yMax > 0 ? dp.yMax : dp.newMaxCount, ip);
		dp.histogram[dp.stats.mode] = saveModalCount;
		x = IHistogramDraw.XMARGIN + 1;
		y = IHistogramDraw.YMARGIN + IHistogramDraw.HIST_HEIGHT + 2;
		if (imp == null)
			dp.lut.drawUnscaledColorBar(ip, x - 1, y, IHistogramDraw.HIST_WIDTH, IHistogramDraw.BAR_HEIGHT);
		else
			plot.drawAlignedColorBar(imp, xMin, xMax, ip, x - 1, y, IHistogramDraw.HIST_WIDTH,
					IHistogramDraw.BAR_HEIGHT);
		y += IHistogramDraw.BAR_HEIGHT + (int) (15 * IHistogramDraw.SCALE);
		drawText(ip, x, y, fixedRange);
		dp.srcImageID = imp.getID();

	}

	public void drawText(ImageProcessor ip, int x, int y, boolean fixedRange) {
		ip.setFont(dp.font);
		ip.setAntialiasedText(true);
		double hmin = dp.cal.getCValue(dp.stats.histMin);
		double hmax = dp.cal.getCValue(dp.stats.histMax);
		double range = hmax - hmin;
		if (fixedRange && !dp.cal.calibrated() && hmin == 0 && hmax == 255)
			range = 256;
		ip.drawString(d2s(hmin), x - 4, y);
		ip.drawString(d2s(hmax), x + IHistogramDraw.HIST_WIDTH - getWidth(hmax, ip) + 10, y);
		if (dp.rgbMode >= IHistogramDraw.INTENSITY1) {
			x += IHistogramDraw.HIST_WIDTH / 2;
			y += 1;
			ip.setJustification(ImageProcessor.CENTER_JUSTIFY);
			boolean weighted = ((ColorProcessor) ip).weightedHistogram();

			final int rgbMode = dp.rgbMode;
			if (rgbMode == IHistogramDraw.INTENSITY1)
				ip.drawString((weighted ? "Intensity (weighted)" : "Intensity (unweighted)"), x, y);
			else if (rgbMode == IHistogramDraw.INTENSITY2)
				ip.drawString("Intensity (unweighted)", x, y);
			else if (rgbMode == IHistogramDraw.RGB)
				ip.drawString("RGB", x, y);
			else if (rgbMode == IHistogramDraw.RED)
				ip.drawString("Red", x, y);
			else if (rgbMode == IHistogramDraw.GREEN)
				ip.drawString("Green", x, y);
			else if (rgbMode == IHistogramDraw.BLUE)
				ip.drawString("Blue", x, y);
			ip.setJustification(ImageProcessor.LEFT_JUSTIFY);
		}
		double binWidth = range / dp.stats.nBins;
		binWidth = Math.abs(binWidth);
		dp.showBins = binWidth != 1.0 || !fixedRange;
		dp.col1 = IHistogramDraw.XMARGIN + 5;
		dp.col2 = IHistogramDraw.XMARGIN + IHistogramDraw.HIST_WIDTH / 2;
		dp.row1 = y + (int) (25 * IHistogramDraw.SCALE);
		if (dp.showBins)
			dp.row1 = dp.row1 - (int) (8 * IHistogramDraw.SCALE);
		dp.row2 = dp.row1 + (int) (15 * IHistogramDraw.SCALE);
		dp.row3 = dp.row2 + (int) (15 * IHistogramDraw.SCALE);
		dp.row4 = dp.row3 + (int) (15 * IHistogramDraw.SCALE);
		dp.row5 = dp.row4 + (int) (15 * IHistogramDraw.SCALE);
		long count = dp.stats.longPixelCount > 0 ? dp.stats.longPixelCount : dp.stats.pixelCount;
		String modeCount = " (" + dp.stats.maxCount + ")";
		if (modeCount.length() > 12)
			modeCount = "";

		ip.drawString("N: " + count, dp.col1, dp.row1);
		ip.drawString("Min: " + d2s(dp.stats.min), dp.col2, dp.row1);
		ip.drawString("Mean: " + d2s(dp.stats.mean), dp.col1, dp.row2);
		ip.drawString("Max: " + d2s(dp.stats.max), dp.col2, dp.row2);
		ip.drawString("StdDev: " + d2s(dp.stats.stdDev), dp.col1, dp.row3);
		ip.drawString("Mode: " + d2s(dp.stats.dmode) + modeCount, dp.col2, dp.row3);
		if (dp.showBins) {
			ip.drawString("Bins: " + d2s(dp.stats.nBins), dp.col1, dp.row4);
			ip.drawString("Bin Width: " + d2s(binWidth), dp.col2, dp.row4);
		}
	}

	public void drawLogPlot(long maxCount, ImageProcessor ip) {
		dp.frame = new Rectangle(IHistogramDraw.XMARGIN, IHistogramDraw.YMARGIN, IHistogramDraw.HIST_WIDTH,
				IHistogramDraw.HIST_HEIGHT);
		ip.drawRect(dp.frame.x - 1, dp.frame.y, dp.frame.width + 2, dp.frame.height + 1);
		double max = Math.log(maxCount);
		ip.setColor(Color.gray);
		if (dp.histogram.length == 256) {
			double scale2 = IHistogramDraw.HIST_WIDTH / 256.0;
			int barWidth = 1;
			if (IHistogramDraw.SCALE > 1)
				barWidth = 2;
			if (IHistogramDraw.SCALE > 2)
				barWidth = 3;
			for (int i = 0; i < 256; i++) {
				int x = (int) (i * scale2);
				int y = dp.histogram[i] == 0 ? 0 : (int) (IHistogramDraw.HIST_HEIGHT * Math.log(dp.histogram[i]) / max);
				if (y > IHistogramDraw.HIST_HEIGHT)
					y = IHistogramDraw.HIST_HEIGHT;
				for (int j = 0; j < barWidth; j++)
					ip.drawLine(x + j + IHistogramDraw.XMARGIN, IHistogramDraw.YMARGIN + IHistogramDraw.HIST_HEIGHT,
							x + j + IHistogramDraw.XMARGIN, IHistogramDraw.YMARGIN + IHistogramDraw.HIST_HEIGHT - y);
			}
		} else if (dp.histogram.length <= IHistogramDraw.HIST_WIDTH) {
			int index, y;
			for (int i = 0; i < IHistogramDraw.HIST_WIDTH; i++) {
				index = (int) (i * (double) dp.histogram.length / IHistogramDraw.HIST_WIDTH);
				y = dp.histogram[index] == 0 ? 0
						: (int) (IHistogramDraw.HIST_HEIGHT * Math.log(dp.histogram[index]) / max);
				if (y > IHistogramDraw.HIST_HEIGHT)
					y = IHistogramDraw.HIST_HEIGHT;
				ip.drawLine(i + IHistogramDraw.XMARGIN, IHistogramDraw.YMARGIN + IHistogramDraw.HIST_HEIGHT,
						i + IHistogramDraw.XMARGIN, IHistogramDraw.YMARGIN + IHistogramDraw.HIST_HEIGHT - y);
			}
		} else {
			double xscale = (double) IHistogramDraw.HIST_WIDTH / dp.histogram.length;
			for (int i = 0; i < dp.histogram.length; i++) {
				long value = dp.histogram[i];
				if (value > 0L) {
					int y = (int) (IHistogramDraw.HIST_HEIGHT * Math.log(value) / max);
					if (y > IHistogramDraw.HIST_HEIGHT)
						y = IHistogramDraw.HIST_HEIGHT;
					int x = (int) (i * xscale) + IHistogramDraw.XMARGIN;
					ip.drawLine(x, IHistogramDraw.YMARGIN + IHistogramDraw.HIST_HEIGHT, x,
							IHistogramDraw.YMARGIN + IHistogramDraw.HIST_HEIGHT - y);
				}
			}
		}
		ip.setColor(Color.black);
	}

	public void drawPlot(long maxCount, ImageProcessor ip) {
		if (maxCount == 0)
			maxCount = 1;
		dp.frame = new Rectangle(IHistogramDraw.XMARGIN, IHistogramDraw.YMARGIN, IHistogramDraw.HIST_WIDTH,
				IHistogramDraw.HIST_HEIGHT);
		ip.drawRect(dp.frame.x - 1, dp.frame.y, dp.frame.width + 2, dp.frame.height + 1);
		if (dp.histogram.length == 256) {
			double scale2 = IHistogramDraw.HIST_WIDTH / 256.0;
			int barWidth = 1;
			if (IHistogramDraw.SCALE > 1)
				barWidth = 2;
			if (IHistogramDraw.SCALE > 2)
				barWidth = 3;
			for (int i = 0; i < 256; i++) {
				int x = (int) (i * scale2);
				int y = (int) (((double) IHistogramDraw.HIST_HEIGHT * (double) dp.histogram[i]) / maxCount);
				if (y > IHistogramDraw.HIST_HEIGHT)
					y = IHistogramDraw.HIST_HEIGHT;
				for (int j = 0; j < barWidth; j++)
					ip.drawLine(x + j + IHistogramDraw.XMARGIN, IHistogramDraw.YMARGIN + IHistogramDraw.HIST_HEIGHT,
							x + j + IHistogramDraw.XMARGIN, IHistogramDraw.YMARGIN + IHistogramDraw.HIST_HEIGHT - y);
			}
		} else if (dp.histogram.length <= IHistogramDraw.HIST_WIDTH) {
			int index, y;
			for (int i = 0; i < IHistogramDraw.HIST_WIDTH; i++) {
				index = (int) (i * (double) dp.histogram.length / IHistogramDraw.HIST_WIDTH);
				y = (int) (((double) IHistogramDraw.HIST_HEIGHT * (double) dp.histogram[index]) / maxCount);
				if (y > IHistogramDraw.HIST_HEIGHT)
					y = IHistogramDraw.HIST_HEIGHT;
				ip.drawLine(i + IHistogramDraw.XMARGIN, IHistogramDraw.YMARGIN + IHistogramDraw.HIST_HEIGHT,
						i + IHistogramDraw.XMARGIN, IHistogramDraw.YMARGIN + IHistogramDraw.HIST_HEIGHT - y);
			}
		} else {
			double xscale = (double) IHistogramDraw.HIST_WIDTH / dp.histogram.length;
			for (int i = 0; i < dp.histogram.length; i++) {
				long value = dp.histogram[i];
				if (value > 0L) {
					int y = (int) (((double) IHistogramDraw.HIST_HEIGHT * (double) value) / maxCount);
					if (y > IHistogramDraw.HIST_HEIGHT)
						y = IHistogramDraw.HIST_HEIGHT;
					int x = (int) (i * xscale) + IHistogramDraw.XMARGIN;
					ip.drawLine(x, IHistogramDraw.YMARGIN + IHistogramDraw.HIST_HEIGHT, x,
							IHistogramDraw.YMARGIN + IHistogramDraw.HIST_HEIGHT - y);
				}
			}
		}
	}
}
