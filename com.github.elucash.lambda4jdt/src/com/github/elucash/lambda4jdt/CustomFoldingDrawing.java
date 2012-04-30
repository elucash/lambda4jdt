package com.github.elucash.lambda4jdt;

import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.source.projection.ProjectionAnnotationModel;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.StyledTextContent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;

@SuppressWarnings("unused")
public class CustomFoldingDrawing {

	static void drawCollapsedSummaryRegion(Integer moffset, ProjectionViewer viewer, GC gc,
	        StyledText textWidget, int offset, int length) throws Exception {

		if (moffset == null)
			return;

// if (0 == 0) {
//			
// return;
// }
//		
// viewer.m`getProjectionAnnotationModel();

		StyledTextContent content = textWidget.getContent();
		int line = content.getLineAtOffset(offset);
		int lineStart = content.getOffsetAtLine(line);
		String text = content.getLine(line);
		if (text == null)
			text = "";

		int lineLength = text.length();
		int lineEnd = lineStart + lineLength;

// ISourceRange sourceRange = type.getSourceRange();

// int endOffset = sourceRange.getOffset() + sourceRange.getLength();
//		
// IDocument document = viewer.getDocument();
//		
// IRegion lineRegion = document.getLineInformationOfOffset(endOffset);
// int lastLineEnd = lineRegion.getOffset() + lineRegion.getLength();
// String suffix = document.get(endOffset, lastLineEnd - endOffset);

		// lastLine

		// Cancel drawing on subsequent folded lines
		if (offset == lineStart) {
			// System.out.println(">>" + moffset);
			return;
		}
//
// if (content.getTextRange(offset, 1).charAt(0) != ' ') {
// System.out.println(">>" +moffset);
// return;
// }

		int widgetOffset = viewer.modelOffset2WidgetOffset(moffset);

		if (widgetOffset <= 0)
			return;
// ISourceRange sourceRange = type.getSourceRange();
// IRegion widgetRange = viewer.modelRange2WidgetRange(new Region(sourceRange.getOffset(),
// sourceRange.getLength()));
//
// String textRange = textWidget.getTextRange(widgetRange.getOffset(), widgetRange.getLength());
//		
// int modelOffset = viewer.widgetOffset2ModelOffset(offset);
// viewer.

// if (viewer.)

// if (offset != type.getSourceRange().getOffset())
// return;

// Point p = textWidget.getLocationAtOffset(lineStart);// SC
		Point pS = textWidget.getLocationAtOffset(widgetOffset);// SC
// Point pS =
		// textWidget.getLocationAtOffset(viewer.modelOffset2WidgetOffset(sourceRange.getOffset())
		// );// SC
// Point pEnd = textWidget.getLocationAtOffset(lineEnd);// SC

		Color c = gc.getForeground();
		Color gray = new Color(gc.getDevice(), 0xA0, 0xA0, 0xA0);
		Color gray1 = new Color(gc.getDevice(), 0xE3, 0xE3, 0xE3);
		Color black = new Color(gc.getDevice(), 0, 0, 0);
		Color wh = new Color(gc.getDevice(), 0xFF, 0xFF, 0xFF);

		gc.setForeground(gray);

		FontMetrics metrics = gc.getFontMetrics();

		int lineHeight = metrics.getHeight();

		// baseline: where the dots are drawn
		int baseline = textWidget.getBaseline(offset);
		// descent: number of pixels that the box extends over baseline
		// int descent= Math.min(2, textWidget.getLineHeight(offset) -
		// baseline);//SC
		int descent = 1;
		// ascent: so much does the box stand up from baseline
		int ascent = metrics.getAscent();
		// leading: free space from line top to box upper line
		int leading = baseline - ascent;
		// height: height of the box
		int height = ascent + descent;

		int width = metrics.getAverageCharWidth();

		// gc.fillRectangle(pS.x, p.y, width * 100, lineHeight + 1);

		int pos = pS.x;

		// gc.drawString("{", pos, pS.y);
		// gc.drawString("}", pS.x + width * (1 + source.length()), pS.y);
		// gc.setForeground(gray1);
		// gc.fillRectangle(pS.x, pS.y, width * 2, lineHeight);// SC

		// gc.setForeground(gray1);

		// gc.drawRectangle(pS.x, pS.y, width * 2, lineHeight);// SC

		gc.setForeground(black);

// gc.setForeground(black);

		gc.drawString("=>", pS.x, pS.y, true);// SC

// gc.drawString(suffix, pS.x + width * source.length(), pS.y);// SC

		// gc.drawRectangle(pS.x, pS.y + leading, width * 2, height);// SC
// int third = width / 3;
// int dotsVertical = p.y + baseline - 1;

		// gc.drawPoint(p.x + third, dotsVertical);//SC
		// gc.drawPoint(p.x + width - third, dotsVertical);//SC

		gc.setForeground(c);
	}

	public static void drawCollapsedHolder(Annotation annotation, GC gc, StyledText textWidget,
	        int offset, int length, Color color) {

		color = new Color(gc.getDevice(), 0xA0, 0xA0, 0xA0);

		StyledTextContent content = textWidget.getContent();
		int line = content.getLineAtOffset(offset);
		int lineStart = content.getOffsetAtLine(line);
		String text = content.getLine(line);
		int lineLength = text == null ? 0 : text.length();
		int lineEnd = lineStart + lineLength - 2;
		Point p = textWidget.getLocationAtOffset(lineEnd);

		Color c = gc.getForeground();
		gc.setForeground(color);

		FontMetrics metrics = gc.getFontMetrics();

		// baseline: where the dots are drawn
		int baseline = textWidget.getBaseline(offset);
		// descent: number of pixels that the box extends over baseline
		int descent = 1;// Math.min(1, textWidget.getLineHeight(offset) - baseline);
		// ascent: so much does the box stand up from baseline
		int ascent = metrics.getAscent();
		// leading: free space from line top to box upper line
		int leading = baseline - ascent;
		// height: height of the box
		int height = ascent + descent;

		int width = metrics.getAverageCharWidth() * 2;
		gc.drawRectangle(p.x, p.y + leading, width, height);
		int third = width / 3;
		int dotsVertical = p.y + baseline - 2;
		gc.drawPoint(p.x + third, dotsVertical);
		gc.drawPoint(p.x + width - third, dotsVertical);

		gc.setForeground(c);
	}
}
