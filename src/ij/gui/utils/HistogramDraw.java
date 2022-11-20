package ij.gui.utils;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.HistogramPlot;
import ij.gui.HistogramWindow;
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

	public HistogramDraw(IHistogramDraw plot) {
		this.plot = plot;
	}

	public void drawHistogram(ImagePlus imp, ImageProcessor ip, boolean fixedRange, double xMin,
			double xMax) {
		if (plot instanceof HistogramPlot)
			((HistogramPlot) plot).setTitle("Histogram of " + imp.getShortTitle());
		int x, y;
		long maxCount2 = 0;
		int mode2 = 0;
		long saveModalCount;
		ip.setColor(Color.black);
		ip.setLineWidth(1);
		plot.setDecimalPlaces(Analyzer.getPrecision());
		plot.setDigits(plot.getCal().calibrated() || plot.getStats().binSize != 1.0 ? plot.getDecimalPlaces() : 0);
		saveModalCount = plot.getLongHistogram()[plot.getStats().mode];
		for (int i = 0; i < plot.getLongHistogram().length; i++) {
			if ((plot.getLongHistogram()[i] > maxCount2) && (i != plot.getStats().mode)) {
				maxCount2 = plot.getLongHistogram()[i];
				mode2 = i;
			}
		}
		plot.setNewMaxCount(plot.getLongHistogram()[plot.getStats().mode]);
		if ((plot.getNewMaxCount() > (maxCount2 * 2)) && (maxCount2 != 0))
			plot.setNewMaxCount((int) (maxCount2 * 1.5));
		if (plot.shouldDrawLogPlot())
			plot.drawLogPlot(plot.getyMax() > 0 ? plot.getyMax() : plot.getNewMaxCount(), ip);
		plot.drawPlot(plot.getyMax() > 0 ? plot.getyMax() : plot.getNewMaxCount(), ip);
		plot.getLongHistogram()[plot.getStats().mode] = saveModalCount;
		x = plot.getXMARGIN() + 1;
		y = plot.getYMARGIN() + plot.getHIST_HEIGHT() + 2;
		if (imp == null)
			plot.getLut().drawUnscaledColorBar(ip, x - 1, y, plot.getHIST_WIDTH(), plot.getBAR_HEIGHT());
		else
			plot.drawAlignedColorBar(imp, xMin, xMax, ip, x - 1, y, plot.getHIST_WIDTH(), plot.getBAR_HEIGHT());
		y += plot.getBAR_HEIGHT() + (int) (15 * plot.getSCALE());
		drawText(ip, x, y, fixedRange);
		plot.setSrcImageID(imp.getID());

	}

	public void drawText(ImageProcessor ip, int x, int y, boolean fixedRange) {
		ip.setFont(plot.getFont());
		ip.setAntialiasedText(true);
		double hmin = plot.getCal().getCValue(plot.getStats().histMin);
		double hmax = plot.getCal().getCValue(plot.getStats().histMax);
		double range = hmax - hmin;
		if (fixedRange && !plot.getCal().calibrated() && hmin == 0 && hmax == 255)
			range = 256;
		ip.drawString(d2s(hmin), x - 4, y);
		ip.drawString(d2s(hmax), x + plot.getHIST_WIDTH() - getWidth(hmax, ip) + 10, y);
		if (plot.getRgbMode() >= plot.getINTENSITY1()) {
			x += plot.getHIST_WIDTH() / 2;
			y += 1;
			ip.setJustification(ImageProcessor.CENTER_JUSTIFY);
			boolean weighted = ((ColorProcessor) ip).weightedHistogram();

			final int rgbMode = plot.getRgbMode();

			final int INTENSITY1 = plot.getINTENSITY1();
			final int INTENSITY2 = plot.getINTENSITY2();
			final int RGB = plot.getRGB();
			final int RED = plot.getRED();
			final int GREEN = plot.getGREEN();
			final int BLUE = plot.getBLUE();
			if (rgbMode == INTENSITY1)
				ip.drawString((weighted ? "Intensity (weighted)" : "Intensity (unweighted)"), x, y);
			else if (rgbMode == INTENSITY2)
				ip.drawString("Intensity (unweighted)", x, y);
			else if (rgbMode == RGB)
				ip.drawString("RGB", x, y);
			else if (rgbMode == RED)
				ip.drawString("Red", x, y);
			else if (rgbMode == GREEN)
				ip.drawString("Green", x, y);
			else if (rgbMode == BLUE)
				ip.drawString("Blue", x, y);
			ip.setJustification(ImageProcessor.LEFT_JUSTIFY);
		}
		double binWidth = range / plot.getStats().nBins;
		binWidth = Math.abs(binWidth);
		plot.setShowBins(binWidth != 1.0 || !fixedRange);
		plot.setCol1(plot.getXMARGIN() + 5);
		plot.setCol2(plot.getXMARGIN() + plot.getHIST_WIDTH() / 2);
		plot.setRow1(y + (int) (25 * plot.getSCALE()));
		if (plot.isShowBins())
			plot.setRow1(plot.getRow1() - (int) (8 * plot.getSCALE()));
		plot.setRow2(plot.getRow1() + (int) (15 * plot.getSCALE()));
		plot.setRow3(plot.getRow2() + (int) (15 * plot.getSCALE()));
		plot.setRow4(plot.getRow3() + (int) (15 * plot.getSCALE()));
		plot.setRow5(plot.getRow4() + (int) (15 * plot.getSCALE()));
		long count = plot.getStats().longPixelCount > 0 ? plot.getStats().longPixelCount : plot.getStats().pixelCount;
		String modeCount = " (" + plot.getStats().maxCount + ")";
		if (modeCount.length() > 12)
			modeCount = "";

		ip.drawString("N: " + count, plot.getCol1(), plot.getRow1());
		ip.drawString("Min: " + d2s(plot.getStats().min), plot.getCol2(), plot.getRow1());
		ip.drawString("Mean: " + d2s(plot.getStats().mean), plot.getCol1(), plot.getRow2());
		ip.drawString("Max: " + d2s(plot.getStats().max), plot.getCol2(), plot.getRow2());
		ip.drawString("StdDev: " + d2s(plot.getStats().stdDev), plot.getCol1(), plot.getRow3());
		ip.drawString("Mode: " + d2s(plot.getStats().dmode) + modeCount, plot.getCol2(), plot.getRow3());
		if (plot.isShowBins()) {
			ip.drawString("Bins: " + d2s(plot.getStats().nBins), plot.getCol1(), plot.getRow4());
			ip.drawString("Bin Width: " + d2s(binWidth), plot.getCol2(), plot.getRow4());
		}
	}
}
