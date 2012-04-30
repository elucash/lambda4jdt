/*******************************************************************************
 * Copyright (c) 2006, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.github.elucash.lambda4jdt;

import static org.eclipse.jdt.core.compiler.ITerminalSymbols.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import com.github.elucash.lambda4jdt.RegionDemarkator.RegionHandle;
import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IImportContainer;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.SourceRange;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.text.DocumentCharacterIterator;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.folding.IJavaFoldingStructureProvider;
import org.eclipse.jdt.ui.text.folding.IJavaFoldingStructureProviderExtension;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.AnnotationPainter;
import org.eclipse.jface.text.source.projection.IProjectionListener;
import org.eclipse.jface.text.source.projection.IProjectionPosition;
import org.eclipse.jface.text.source.projection.ProjectionAnnotation;
import org.eclipse.jface.text.source.projection.ProjectionAnnotationModel;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Updates the projection model of a class file or compilation unit.
 * <p>
 * Clients may instantiate or subclass. Subclasses must make sure to always call the superclass'
 * code when overriding methods that are marked with "subclasses may extend".
 * </p>
 * @since 3.0 (internal)
 * @since 3.2 (API)
 */
@SuppressWarnings({"restriction","unused"})
public class CutomizedJavaFoldingStructureProvider implements IJavaFoldingStructureProvider,
        IJavaFoldingStructureProviderExtension {

	private static final String FUNCTION_RELATION_TOKEN = "/* => */";

	private static final String FUNCTION_RELATION_TOKEN_ONELINE = "// => ";

	/**
	 * A context that contains the information needed to compute the folding structure of an
	 * {@link ICompilationUnit} or an {@link IClassFile}. Computed folding regions are collected via
	 * {@linkplain #addProjectionRange(CutomizedJavaFoldingStructureProvider.JavaProjectionAnnotation, Position)
	 * addProjectionRange}.
	 */
	protected final class FoldingStructureComputationContext {
		private final ProjectionAnnotationModel fModel;
		private final IDocument fDocument;

		private final boolean fAllowCollapsing;

		private IType fFirstType;
		private boolean fHasHeaderComment;
		private LinkedHashMap<JavaProjectionAnnotation, Position> fMap = new LinkedHashMap<JavaProjectionAnnotation, Position>();
		private IScanner fScanner;
		boolean initial;

		private FoldingStructureComputationContext(IDocument document,
		        ProjectionAnnotationModel model, boolean allowCollapsing, IScanner scanner) {
			Assert.isNotNull(document);
			Assert.isNotNull(model);
			fDocument = document;
			fModel = model;
			fAllowCollapsing = allowCollapsing;
			fScanner = scanner;
		}

		private void setFirstType(IType type) {
			if (hasFirstType())
				throw new IllegalStateException();
			fFirstType = type;
		}

		boolean hasFirstType() {
			return fFirstType != null;
		}

		private IType getFirstType() {
			return fFirstType;
		}

		private boolean hasHeaderComment() {
			return fHasHeaderComment;
		}

		private void setHasHeaderComment() {
			fHasHeaderComment = true;
		}

		/**
		 * Returns <code>true</code> if newly created folding regions may be collapsed,
		 * <code>false</code> if not. This is usually <code>false</code> when updating the folding
		 * structure while typing; it may be <code>true</code> when computing or restoring the
		 * initial folding structure.
		 * @return <code>true</code> if newly created folding regions may be collapsed,
		 *         <code>false</code> if not
		 */
		public boolean allowCollapsing() {
			return fAllowCollapsing;
		}

		/**
		 * Returns the document which contains the code being folded.
		 * @return the document which contains the code being folded
		 */
		private IDocument getDocument() {
			return fDocument;
		}

		private ProjectionAnnotationModel getModel() {
			return fModel;
		}

		private IScanner getScanner() {
			if (fScanner == null)
				fScanner = ToolFactory.createScanner(true, false, false, false);
			return fScanner;
		}

		/**
		 * Adds a projection (folding) region to this context. The created annotation / position
		 * pair will be added to the {@link ProjectionAnnotationModel} of the
		 * {@link ProjectionViewer} of the editor.
		 * @param annotation the annotation to add
		 * @param position the corresponding position
		 */
		public void addProjectionRange(JavaProjectionAnnotation annotation, Position position) {
			fMap.put(annotation, position);
		}

		/**
		 * Returns <code>true</code> if header comments should be collapsed.
		 * @return <code>true</code> if header comments should be collapsed
		 */
		public boolean collapseHeaderComments() {
			return fAllowCollapsing && fCollapseHeaderComments;
		}

		/**
		 * Returns <code>true</code> if import containers should be collapsed.
		 * @return <code>true</code> if import containers should be collapsed
		 */
		public boolean collapseImportContainer() {
			return fAllowCollapsing && fCollapseImportContainer;
		}

		/**
		 * Returns <code>true</code> if inner types should be collapsed.
		 * @return <code>true</code> if inner types should be collapsed
		 */
		public boolean collapseInnerTypes() {
			return fAllowCollapsing && fCollapseInnerTypes;
		}

		/**
		 * Returns <code>true</code> if javadoc comments should be collapsed.
		 * @return <code>true</code> if javadoc comments should be collapsed
		 */
		public boolean collapseJavadoc() {
			return fAllowCollapsing && fCollapseJavadoc;
		}

		/**
		 * Returns <code>true</code> if methods should be collapsed.
		 * @return <code>true</code> if methods should be collapsed
		 */
		public boolean collapseMembers() {
			return fAllowCollapsing && fCollapseMembers;
		}
	}

	private Map<IType, Integer> annotationDecorationDrawingOffsets = new WeakHashMap<IType, Integer>();

	/**
	 * A {@link ProjectionAnnotation} for java code.
	 */
	protected final class JavaProjectionAnnotation extends ProjectionAnnotation implements
	        AnnotationPainter.IDrawingStrategy {

		private IJavaElement fJavaElement;
		private boolean fIsComment;
		private final boolean isCollapsedByDefault;

		/**
		 * Creates a new projection annotation.
		 * @param isCollapsed <code>true</code> to set the initial state to collapsed,
		 *            <code>false</code> to set it to expanded
		 * @param ctx
		 * @param element the java element this annotation refers to
		 * @param isComment <code>true</code> for a foldable comment, <code>false</code> for a
		 *            foldable code element
		 */
		public JavaProjectionAnnotation(boolean isCollapsed,
		        FoldingStructureComputationContext ctx, IJavaElement element, boolean isComment) {
			super(isCollapsed);
			this.isCollapsedByDefault = isCollapsed;
			setElement(element);
			fIsComment = isComment;
		}

		IJavaElement getElement() {
			return fJavaElement;
		}

		void setElement(IJavaElement element) {
			fJavaElement = element;
			closurable = findLambdaMethodIn(fJavaElement) != null;
		}

		boolean isComment() {
			return fIsComment;
		}

		void setIsComment(boolean isComment) {
			fIsComment = isComment;
		}

		/*
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
			return "JavaProjectionAnnotation:\n" + //$NON-NLS-1$
			        "\telement: \t" + fJavaElement.toString() + "\n" + //$NON-NLS-1$ //$NON-NLS-2$
			        "\tcollapsed: \t" + isCollapsed() + "\n" + //$NON-NLS-1$ //$NON-NLS-2$
			        "\tcomment: \t" + isComment() + "\n"; //$NON-NLS-1$ //$NON-NLS-2$
		}

		public void draw(Annotation annotation, GC gc, StyledText textWidget, int offset,
		        int length, Color color) {

			if (fIsComment || !closurable)
				throw new UnsupportedOperationException();

			// TEMP
			if (isCollapsed() || isCollapsedByDefault) {
				if (gc != null) {
					// Do not draw anything
				} else
					textWidget.redrawRange(offset, length, true);
			}
		}
		boolean closurable;

	}

	static IMethod findLambdaMethodIn(IJavaElement element) {
		try {
			if (element.getElementType() == IJavaElement.TYPE) {
				IType t = (IType) element;
				if (t.isAnonymous()) {
					IJavaElement[] c = t.getChildren();
					if (c.length == 1 && c[0].getElementType() == IJavaElement.METHOD) {
						IMethod m = (IMethod) c[0];
						String source = m.getSource();
						boolean hasToken1 = source.contains(FUNCTION_RELATION_TOKEN);
						boolean hasToken2 = source.contains(FUNCTION_RELATION_TOKEN_ONELINE);
						if (hasToken1 ^ hasToken2)
							return m;
// IAnnotation[] annotations = m.getAnnotations();
// if (annotations != null) {
// for (IAnnotation annotation : annotations) {
// if (annotation.getElementName().endsWith("LambdaFolded"))
// return m;
// }
// }
// }
					}
				}
			}
		} catch (Exception e) {}
		return null;
	}

/*	private static final IProgressMonitor noMonitor = new NoProgress();*/

	private static final class Tuple {
		JavaProjectionAnnotation annotation;
		Position position;

		Tuple(JavaProjectionAnnotation annotation, Position position) {
			this.annotation = annotation;
			this.position = position;
		}
	}

	/**
	 * Filter for annotations.
	 */
	private static interface Filter {
		boolean match(JavaProjectionAnnotation annotation);
	}

	/**
	 * Matches comments.
	 */
	private static final class CommentFilter implements Filter {
		public boolean match(JavaProjectionAnnotation annotation) {
			if (annotation.isComment() && !annotation.isMarkedDeleted()) {
				return true;
			}
			return false;
		}
	}

	/**
	 * Matches members.
	 */
	private static final class MemberFilter implements Filter {
		public boolean match(JavaProjectionAnnotation annotation) {
			if (!annotation.isComment() && !annotation.isMarkedDeleted()) {
				IJavaElement element = annotation.getElement();
				if (element instanceof IMember) {
					if (element.getElementType() != IJavaElement.TYPE ||
					        ((IMember) element).getDeclaringType() != null) {
						return true;
					}
				}
			}
			return false;
		}
	}

	/**
	 * Matches java elements contained in a certain set.
	 */
	private static final class JavaElementSetFilter implements Filter {
		private final Set<? extends IJavaElement> set;
		private final boolean matchCollapsed;

		private JavaElementSetFilter(Set<? extends IJavaElement> set, boolean matchCollapsed) {
			this.set = set;
			this.matchCollapsed = matchCollapsed;
		}

		public boolean match(JavaProjectionAnnotation annotation) {
			boolean stateMatch = matchCollapsed == annotation.isCollapsed();
			if (stateMatch && !annotation.isComment() && !annotation.isMarkedDeleted()) {
				IJavaElement element = annotation.getElement();
				if (set.contains(element)) {
					return true;
				}
			}
			return false;
		}
	}

	private class ElementChangedListener implements IElementChangedListener {

		/*
		 * @see org.eclipse.jdt.core.IElementChangedListener#elementChanged(org.eclipse.jdt.core.ElementChangedEvent)
		 */
		public void elementChanged(ElementChangedEvent e) {
			IJavaElementDelta delta = findElement(fInput, e.getDelta());
			if (delta != null &&
			        (delta.getFlags() & (IJavaElementDelta.F_CONTENT | IJavaElementDelta.F_CHILDREN)) != 0) {

				CompilationUnit unitAST = e.getDelta().getCompilationUnitAST();
				if (shouldIgnoreDelta(unitAST, delta))
					return;

				fUpdatingCount++;
				try {
					update(createContext(false));
				} finally {
					fUpdatingCount--;
				}
			}
		}

		/**
		 * Ignore the delta if there are errors on the caret line.
		 * <p>
		 * We don't ignore the delta if an import is added and the caret isn't inside the import
		 * container.
		 * </p>
		 * @param ast the compilation unit AST
		 * @param delta the Java element delta for the given AST element
		 * @return <code>true</code> if the delta should be ignored
		 * @since 3.3
		 */
		private boolean shouldIgnoreDelta(CompilationUnit ast, IJavaElementDelta delta) {
			if (ast == null)
				return false; // can't compute

			IDocument document = getDocument();
			if (document == null)
				return false; // can't compute

			JavaEditor editor = fEditor;
			Point selectedRange = null;
			if (editor == null || (selectedRange = editor.getCachedSelectedRange()) == null)
				return false; // can't compute

			try {
				IJavaElementDelta[] affectedChildren = delta.getAffectedChildren();
				if (affectedChildren.length == 1 &&
				        affectedChildren[0].getElement() instanceof IImportContainer) {
					IJavaElement elem = SelectionConverter.getElementAtOffset(ast.getTypeRoot(),
					        new TextSelection(selectedRange.x, selectedRange.y));
					if (!(elem instanceof IImportDeclaration))
						return false;

				}
			} catch (JavaModelException e) {
				return false; // can't compute
			}

			int caretLine = 0;
			try {
				caretLine = document.getLineOfOffset(selectedRange.x) + 1;
			} catch (BadLocationException x) {
				return false; // can't compute
			}

			if (caretLine > 0) {
				IProblem[] problems = ast.getProblems();
				for (int i = 0; i < problems.length; i++) {
					if (problems[i].isError() && caretLine == problems[i].getSourceLineNumber())
						return true;
				}
			}

			return false;
		}

		private IJavaElementDelta findElement(IJavaElement target, IJavaElementDelta delta) {

			if (delta == null || target == null)
				return null;

			IJavaElement element = delta.getElement();

			if (element.getElementType() > IJavaElement.CLASS_FILE)
				return null;

			if (target.equals(element))
				return delta;

			IJavaElementDelta[] children = delta.getAffectedChildren();

			for (int i = 0; i < children.length; i++) {
				IJavaElementDelta d = findElement(target, children[i]);
				if (d != null)
					return d;
			}

			return null;
		}
	}

	/**
	 * Projection position that will return two foldable regions: one folding away the region from
	 * after the '/**' to the beginning of the content, the other from after the first content line
	 * until after the comment.
	 */
	private static final class JavaCommentPosition extends Position implements IProjectionPosition {
		JavaCommentPosition(int offset, int length) {
			super(offset, length);
		}

		/*
		 * @see org.eclipse.jface.text.source.projection.IProjectionPosition#computeFoldingRegions(org.eclipse.jface.text.IDocument)
		 */
		public IRegion[] computeProjectionRegions(IDocument document) throws BadLocationException {
// if (0 == 0)
// return new IRegion[] { new Region(offset, length) };
//			
			DocumentCharacterIterator sequence = new DocumentCharacterIterator(document, offset,
			        offset + length);
			int prefixEnd = 0;
			int contentStart = findFirstContent(sequence, prefixEnd);

			int firstLine = document.getLineOfOffset(offset + prefixEnd);
			int captionLine = document.getLineOfOffset(offset + contentStart);
			int lastLine = document.getLineOfOffset(offset + length);

			Assert.isTrue(firstLine <= captionLine,
			        "first folded line is greater than the caption line"); //$NON-NLS-1$
			Assert.isTrue(captionLine <= lastLine,
			        "caption line is greater than the last folded line"); //$NON-NLS-1$

			IRegion preRegion = null, postRegion = null;
			if (firstLine < captionLine) {
// preRegion= new Region(offset + prefixEnd, contentStart - prefixEnd);
				int preOffset = document.getLineOffset(firstLine);
				IRegion preEndLineInfo = document.getLineInformation(captionLine);
				int preEnd = preEndLineInfo.getOffset();
				preRegion = new Region(preOffset, preEnd - preOffset);
			}

			if (captionLine < lastLine) {
				int postOffset = document.getLineOffset(captionLine + 1);
				postRegion = new Region(postOffset, offset + length - postOffset);
			}

			return combineRegions(preRegion, postRegion);
		}

		/**
		 * Finds the offset of the first identifier part within <code>content</code>. Returns 0 if
		 * none is found.
		 * @param content the content to search
		 * @param prefixEnd the end of the prefix
		 * @return the first index of a unicode identifier part, or zero if none can be found
		 */
		private int findFirstContent(final CharSequence content, int prefixEnd) {
			int lenght = content.length();
			for (int i = prefixEnd; i < lenght; i++) {
				if (Character.isUnicodeIdentifierPart(content.charAt(i)))
					return i;
			}
			return 0;
		}

// /**
// * Finds the offset of the first identifier part within <code>content</code>.
// * Returns 0 if none is found.
// *
// * @param content the content to search
// * @return the first index of a unicode identifier part, or zero if none can
// * be found
// */
// private int findPrefixEnd(final CharSequence content) {
// // return the index after the leading '/*' or '/**'
// int len= content.length();
// int i= 0;
// while (i < len && isWhiteSpace(content.charAt(i)))
// i++;
// if (len >= i + 2 && content.charAt(i) == '/' && content.charAt(i + 1) == '*')
// if (len >= i + 3 && content.charAt(i + 2) == '*')
// return i + 3;
// else
// return i + 2;
// else
// return i;
// }
//
// private boolean isWhiteSpace(char c) {
// return c == ' ' || c == '\t';
// }

		/*
		 * @see org.eclipse.jface.text.source.projection.IProjectionPosition#computeCaptionOffset(org.eclipse.jface.text.IDocument)
		 */
		public int computeCaptionOffset(IDocument document) {
// return offset;
			try {
				DocumentCharacterIterator sequence = new DocumentCharacterIterator(document,
				        offset, offset + length);

				return findFirstContent(sequence, 0);
			} catch (BadLocationException e) {
				return offset;
			}
		}
	}

	protected static IRegion[] combineRegions(IRegion preRegion, IRegion postRegion) {
		if (preRegion != null && postRegion != null)
			return new IRegion[] { preRegion, postRegion };

		if (preRegion != null)
			return new IRegion[] { preRegion };

		if (postRegion != null)
			return new IRegion[] { postRegion };

		return null;
	}

	/**
	 * Projection position that will return two foldable regions: one folding away the lines before
	 * the one containing the simple name of the java element, one folding away any lines after the
	 * caption.
	 */
	private final class JavaElementPosition extends Position implements IProjectionPosition {

		private IMember fMember;
		private IMethod lambdaMethod;

// private IRegion[] cachedStructure;
// private String cachedSourceForCheck;

		public JavaElementPosition(int offset, int length, IMember member, IMethod lambdaMethod) {
			super(offset, length);
			Assert.isNotNull(member);
			fMember = member;
			this.lambdaMethod = lambdaMethod;
		}

		public void setMember(IMember member) {
			Assert.isNotNull(member);
			fMember = member;
		}

		/*
		 * @see org.eclipse.jface.text.source.projection.IProjectionPosition#computeFoldingRegions(org.eclipse.jface.text.IDocument)
		 */
		public IRegion[] computeProjectionRegions(IDocument document) throws BadLocationException {
			if (!fMember.exists())
				return null;

			int nameStart = computeCaptionOffset(document) + offset;

			try {
				ISourceRange sourceRange = fMember.getSourceRange();
				IMethod foldedMethod = lambdaMethod;

				if (foldedMethod != null && !foldedMethod.exists()) {
					foldedMethod = findLambdaMethodIn(fMember);
					if (foldedMethod != null) {
						lambdaMethod = foldedMethod;
					}
				}

				if (foldedMethod != null && sourceRange != null)
					return computeFunctionRegions(document, sourceRange, null);
			} catch (IllegalStateException e) {
				System.out.println(e.getMessage());
			} catch (UnsupportedOperationException e) {

			} catch (Exception e) {
				e.printStackTrace();
				return null;
			} catch (AssertionError e) {
				e.printStackTrace();
			}

			int firstLine = document.getLineOfOffset(offset);
			int captionLine = document.getLineOfOffset(nameStart);
			int lastLine = document.getLineOfOffset(offset + length);

			/* see comment above - adjust the caption line to be inside the
			 * entire folded region, and rely on later element deltas to correct
			 * the name range. */
			if (captionLine < firstLine)
				captionLine = firstLine;
			if (captionLine > lastLine)
				captionLine = lastLine;

			IRegion preRegion = null, postRegion = null;
			if (firstLine < captionLine) {
				int preOffset = document.getLineOffset(firstLine);
				IRegion preEndLineInfo = document.getLineInformation(captionLine);
				int preEnd = preEndLineInfo.getOffset();
				preRegion = new Region(preOffset, preEnd - preOffset);
			}

			if (captionLine < lastLine) {
				int postOffset = document.getLineOffset(captionLine + 1);
				postRegion = new Region(postOffset, offset + length - postOffset);
			}

			return combineRegions(preRegion, postRegion);
		}

		private IRegion[] computeFunctionRegions(IDocument document, ISourceRange sourceRange,
		        RegionDemarkator editables) throws BadLocationException {

// String sat = null;
//			
// try {
// sat = ((IType) foldedMethod.getParent()).getSource();
// sourceRange = ((IType) foldedMethod.getParent()).getSourceRange();
// } catch (JavaModelException e1) {}
//
			int anonymousTypeOffset = sourceRange.getOffset();
			int anonymousTypeLength = sourceRange.getLength();

			String anomymousTypeSource = document.get(anonymousTypeOffset, anonymousTypeLength);
			int functionTokenIndex = anomymousTypeSource.indexOf(FUNCTION_RELATION_TOKEN);
			boolean isOneLinerToken = false;
			if (functionTokenIndex < 0) {
				functionTokenIndex = anomymousTypeSource.indexOf(FUNCTION_RELATION_TOKEN_ONELINE);
				isOneLinerToken = true;
			}

			// String anomymousTypeSource = foldedMethod.getSource();
// if (cachedStructure != null && anomymousTypeSource.equals(cachedSourceForCheck))
// return cachedStructure;

// int functionMethodOffset = foldedMethod.getSourceRange().getOffset();

			if (editables != null) {
				editables.initialOffset = anonymousTypeOffset;
			}

			RegionDemarkator marker = new RegionDemarkator();
			marker.initialOffset = anonymousTypeOffset;
			marker.start(0);

			ScannerHelper scan = new ScannerHelper(anomymousTypeSource);

			scan.seekCorresponding(TokenNameLBRACE);
			int f = scan.offset;

			marker.end(f);
			marker.start(f);

			RegionHandle openingBraceRegion = marker.end(++f);
			marker.start(f);

/*			// 'public'
			scan.seek(TokenNamepublic);
			f = scan.endOffset + 1;
			marker.end(f);
			marker.start(f);
			RegionHandle afterOpeningBraceWhitespace = marker.end(++f);
*/
// show(document, afterOpeningBraceWhitespace);
			// Seek parenthese opening parameter list
			scan.seekCorresponding(TokenNameLPAREN);

			int parameterListOpenParenOffset = scan.offset;

			marker.end(parameterListOpenParenOffset);
			marker.start(parameterListOpenParenOffset);
			RegionHandle parameterListOpenParen = marker.end(parameterListOpenParenOffset + 1);
			marker.start(parameterListOpenParenOffset + 1);
// assert scan.identifier != null;
//
// int methodNameIdentifierOffset = scan.identifierOffset;
//
// marker.end(methodNameIdentifierOffset - 1);
// // marker.start(methodNameIdentifierOffset - 1);
// // marker.end(methodNameIdentifierOffset);
//
// marker.start(methodNameIdentifierOffset);

			int paramsCount = 0;

			for (;;) {
				int t = scan.seekCorrespondingWithTypeParameterBrackets(TokenNameCOMMA,
				        TokenNameRPAREN);
				if (scan.identifier != null) {
					paramsCount++;
					marker.end(scan.identifierOffset);

					if (editables != null) {
						editables.start(scan.identifierOffset);
						editables.end(scan.identifierOffset + scan.identifier.length);
					}

					if (t == TokenNameCOMMA) {
						scan.seekAnyExcept(TokenNameWHITESPACE);
						marker.start(scan.offset);
					}
				}

				if (t == TokenNameRPAREN || t < 0) {
					break;
				}
			}

			int rparenOffset = scan.offset;
			boolean allWhitespaceBetweenRParenAndFunctionToken = anomymousTypeSource.substring(
			        rparenOffset + 1, functionTokenIndex).trim().length() == 0;

			if (!allWhitespaceBetweenRParenAndFunctionToken)
				throw new UnsupportedOperationException("Token " + FUNCTION_RELATION_TOKEN +
				        " after parameter list right parenthese");

			if (!marker.started)
				marker.start(rparenOffset);

			RegionHandle parameterListClosingParen = marker.end(rparenOffset + 1);

			marker.start(rparenOffset + 1);

			// To show functional arrow `=>`
			int arrowIndex = functionTokenIndex + 2;
			RegionHandle preArrowRegion = marker.end(arrowIndex);
			marker.start(arrowIndex);

			RegionHandle arrowRegion = marker.end(functionTokenIndex + 6);
			marker.start(functionTokenIndex + 6);

// annotationDecorationDrawingOffsets.put((IType) fMember, anonymousTypeOffset +
// functionTokenIndex + 3);

			scan.seekCorresponding(TokenNameLBRACE);

// // Start searching for lines a new
// scan.lineEnds.clear();

			int methodOpeningBraceOffset = scan.offset;
			int lastPreMethodOffset = functionTokenIndex + FUNCTION_RELATION_TOKEN.length();

			// find where we go past all throws declaration if any
			if (scan.identifierOffset > lastPreMethodOffset) {
				lastPreMethodOffset = scan.identifierOffset + scan.identifier.length;
				while (anomymousTypeSource.charAt(lastPreMethodOffset) == ' ') {
					lastPreMethodOffset++;
				}
			}

			// and hide it

			boolean hasPreBraceRegion = lastPreMethodOffset < methodOpeningBraceOffset;
			if (hasPreBraceRegion) {
				marker.end(lastPreMethodOffset);
				marker.start(lastPreMethodOffset);
// preBraceRegion = marker.end(methodOpeningBraceOffset);
// marker.start(methodOpeningBraceOffset);
			}

			RegionHandle preBraceRegion = marker.end(methodOpeningBraceOffset);
			marker.start(methodOpeningBraceOffset);
			RegionHandle methodOpeningBrace = marker.end(methodOpeningBraceOffset + 1);

			int methodCloseBraceOffset = -1;
			int returnOffset = -1;
			boolean singleStatement = true;
			int statementTerminator = -1;
			int firstNonWhitespace = -1;
			int lastBraceBlockEnd = -1;

			bodyScanLoop: for (;;) {
				int t = scan
				        .seekCorresponding(TokenNameSEMICOLON, TokenNameRBRACE, TokenNamereturn);

				if (scan.wasFlowControlStatement)
					singleStatement = false;

				lastBraceBlockEnd = scan.lastBraceBlockEnd;

				if (firstNonWhitespace < 0)
					firstNonWhitespace = scan.firstNonWhitespace;

				swicher: switch (t) {
				case TokenNameSEMICOLON:
					if (statementTerminator > 0 && singleStatement)
						singleStatement = false;

					statementTerminator = scan.offset;
					continue bodyScanLoop;

				case TokenNamereturn:
					returnOffset = scan.offset;
					continue bodyScanLoop;

				case TokenNameRBRACE:
					methodCloseBraceOffset = scan.offset;
				}

				break;
			}

			if (lastBraceBlockEnd > statementTerminator) {
				statementTerminator = lastBraceBlockEnd;
				singleStatement = false;
			}

			boolean useClauseFolding = false;
			if (!singleStatement) {
				useClauseFolding = wheretherAnonymousTypeIsSingleParameterToHigherOrderMethod(
				        document, anonymousTypeOffset, anonymousTypeLength);
			}
			// case 1: single statement returning value function with no or one parameter
			// No curly braces, no return keyword, no semicolon at end
			if (singleStatement && returnOffset > 0 && paramsCount <= 1) {
				// afterOpeningBraceWhitespace.remove();
				marker.start(methodOpeningBraceOffset); // from after opening of method body

// cutTab(anomymousTypeSource, marker, returnOffset - 1);

				marker.end(returnOffset + 7); // to end of return keyword
				if (!(statementTerminator > returnOffset + 7)) {
					System.out.println(anomymousTypeSource);
				}
				marker.start(statementTerminator);// From before last semicolon
				marker.end(anonymousTypeLength);// to end on anonymous type definition

				if (editables != null) {
					editables.start(returnOffset + 7);
					editables.end(statementTerminator);
				}
			}

			// case 2: single statement returning value function with more than one parameter
			// With parameter parentheses, no return keyword, no semicolon at end
			if (singleStatement && returnOffset > 0 && paramsCount > 1) {
				parameterListOpenParen.reveal();// Show opening parameter paren
				parameterListClosingParen.reveal();// Show closing parameter paren

				// openingBraceRegion.reveal();// Show up openingBrace

				marker.start(methodOpeningBraceOffset); // from after opening of method body
				// brace
// cutTab(anomymousTypeSource, marker, returnOffset - 1);
				marker.end(returnOffset + 7); // to end of return keyword
				if (statementTerminator <= returnOffset + 7)
					throw new UnsupportedOperationException("Statement terminator offset " +
					        statementTerminator + "; return end at  " + (returnOffset + 7));

				marker.start(statementTerminator);// From before last semicolon
				marker.end(anonymousTypeLength);// to end on anonymous type definition

				if (editables != null) {
					editables.start(returnOffset + 7);
					editables.end(statementTerminator);
				}
			}

			// case 3: single statement void function with any parameters.
			// With or without parameter parentheses and no semicolon at end
			if (singleStatement && returnOffset < 0) {
				if (paramsCount > 1) {
					parameterListOpenParen.reveal();// Show opening parameter paren
					parameterListClosingParen.reveal();// Show closing parameter paren
				}

				boolean emptyBody = lastBraceBlockEnd < 0 && statementTerminator < 0;

				boolean singleEmptyStatement = lastBraceBlockEnd < 0 &&
				        statementTerminator > 0 &&
				        anomymousTypeSource.substring(methodOpeningBraceOffset + 1,
				                methodCloseBraceOffset).trim().equals(";");

				if (emptyBody) {
					methodOpeningBrace.reveal();// Show up openingBrace

					marker.start(methodOpeningBraceOffset + 1); // from after opening of method body
					marker.end(anonymousTypeLength - 1);// to end on anonymous type definition

				} else if (singleEmptyStatement) {
					
					methodOpeningBrace.reveal();// Show up openingBrace
					marker.start(methodOpeningBraceOffset + 1); // from after opening of method body
					// cutTab(anomymousTypeSource, marker, fnw - 1);
					marker.end(statementTerminator);// to begin of statement

					marker.start(statementTerminator + 1);// From before last semicolon
					marker.end(anonymousTypeLength - 1);// to end on anonymous type definition
				
				} else {

					marker.start(methodOpeningBraceOffset); // from after opening of method body
					// cutTab(anomymousTypeSource, marker, fnw - 1);
					marker.end(firstNonWhitespace);// to begin of statement

					marker.start(statementTerminator);// From before last semicolon
					marker.end(anonymousTypeLength);// to end on anonymous type definition
// marker.start(statementTerminator + 1);// From after last semicolon
// marker.end(anonymousTypeLength - 1); // to before final closing brace

					if (editables != null) {
						editables.start(firstNonWhitespace);
						editables.end(statementTerminator);
					}
				}
			}

			// case 4; multiline function with any parameters, with preserved body
			// (maybe? with return keyword hidden and no semicolon at and for non-void function)
			if (!singleStatement) {
				if (paramsCount > 0) {
					parameterListOpenParen.reveal();// Show opening parameter paren
					parameterListClosingParen.reveal();// Show closing parameter paren
				}

				if (hasPreBraceRegion && !isOneLinerToken) {
					preBraceRegion.reveal();
				}

				// Show up method openingBrace
				methodOpeningBrace.reveal();

// marker.start(methodOpeningBraceOffset); // from before opening of method body
// marker.end(methodOpeningBraceOffset + 1);

// int methodRightBraceLine = document.getLineOfOffset(anonymousTypeOffset +
// methodCloseBraceOffset);
//
// int anonymousTypeBraceLine = document.getLineOfOffset(anonymousTypeOffset +
// anonymousTypeLength);

				if (statementTerminator < 0) {
					statementTerminator = methodCloseBraceOffset - 2;
				}

				if (editables != null) {
					editables.start(methodOpeningBraceOffset + 1);
					editables.end(statementTerminator + 1);
				}

				marker.start(statementTerminator + 1); // hide lines from body end
				// to anonymous class end (exclusive)
				RegionHandle closureEnd = marker.end(methodCloseBraceOffset + 1);

				eatUpExtraTabsOnEachLine(anomymousTypeSource, marker, scan, lastPreMethodOffset,
				        methodCloseBraceOffset);

				try {
					if (useClauseFolding) {
						arrowRegion.offset++;
						arrowRegion.length--;
						// arrowRegion.reveal();// ?

// if (editables != null)
// editables.clearAll();

// if (paramsCount == 0) {
// parameterListOpenParen.reveal();// Show opening parameter paren
// parameterListClosingParen.reveal();// Show closing parameter paren
// }
						// marker.printRegionHandles(document);

						Region prefix = new Region(anonymousTypeOffset - 1, 1);
						Region suffix = new Region(anonymousTypeOffset + anonymousTypeLength, 2);
						return marker.toProcessedArray(prefix, suffix);
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			// eat leading space before arror `=>` when no parameters
			if (paramsCount == 0) {
				preArrowRegion.length++;
				arrowRegion.offset++;
				arrowRegion.length--;
			}

			arrowRegion.reveal();

			IRegion[] resultingArray = marker.toProcessedArray();

			// System.out.println("__ " + Arrays.toString(resultingArray));

// Cache for later use
// this.cachedStructure = resultingArray;
// this.cachedSourceForCheck = anomymousTypeSource;

			return resultingArray;
		}

		private void eatUpExtraTabsOnEachLine(String anomymousTypeSource, RegionDemarkator marker,
		        ScannerHelper scan, int startOffset, int endOffset) {
			for (int index : scan.lineEnds()) {
				if (index < startOffset || index > endOffset)
					continue;
				char startingNextLine = anomymousTypeSource.charAt(index + 1);
				if (startingNextLine == '\t') {
					marker.start(index + 1);
					marker.end(index + 2);
				} else {
					boolean hasTabInFormOf4Spaces = true;
					for (int i = index + 1; i < index + 5; i++) {
						if (anomymousTypeSource.charAt(i) != ' ') {
							hasTabInFormOf4Spaces = false;
							break;
						}
					}
					if (hasTabInFormOf4Spaces) {
						marker.start(index + 1);
						marker.end(index + 5);
					}
				}
			}
		}

		/*
		 * @see org.eclipse.jface.text.source.projection.IProjectionPosition#computeCaptionOffset(org.eclipse.jface.text.IDocument)
		 */
		public int computeCaptionOffset(IDocument document) throws BadLocationException {
			int nameStart = offset;
			try {
				/* The member's name range may not be correct. However,
				 * reconciling would trigger another element delta which would
				 * lead to reentrant situations. Therefore, we optimistically
				 * assume that the name range is correct, but double check the
				 * received lines below. */
				ISourceRange nameRange = fMember.getNameRange();
				if (nameRange != null)
					nameStart = nameRange.getOffset();

			} catch (JavaModelException e) {
				// ignore and use default
			}

			return nameStart - offset;
		}

		/**
		 * Not a real comparator, just ugly hack. Not used now
		 */
		@SuppressWarnings("unchecked")
		public int compare(Object o1, Object o2) {
			IDocument document = (IDocument) o1;
			Collection<IRegion> editableRegions = (Collection<IRegion>) o2;

			if (!fMember.exists())
				return 0;

			try {
				ISourceRange sourceRange = fMember.getSourceRange();
				IMethod foldedMethod = lambdaMethod;

				if (foldedMethod != null && !foldedMethod.exists()) {
					foldedMethod = findLambdaMethodIn(fMember);
					if (foldedMethod != null) {
						lambdaMethod = foldedMethod;
					}
				}

				if (foldedMethod != null && sourceRange != null) {
					// RegionDemarkator editableRegionDemarkator = new RegionDemarkator();
					computeFunctionRegions(document, sourceRange, null);

// String s = "EDITABLE ";
// for (IRegion region : editableRegionDemarkator.toProcessedArray()) {
// s += "[" + document.get(region.getOffset(), region.getLength()) + "]";
// editableRegions.add(region);
// }
//
// System.out.println(s);
				}
			} catch (UnsupportedOperationException e) {} catch (Exception e) {
				e.printStackTrace();
			} catch (AssertionError e) {
				e.printStackTrace();
			}

			return editableRegions.size();
		}

	}

	private static boolean wheretherAnonymousTypeIsSingleParameterToHigherOrderMethod(
	        IDocument document, int declarationOffset, int declarationLength)
	        throws BadLocationException {
		return document.get(declarationOffset - 1, 4).equals("(new") &&
		        document.get(declarationOffset + declarationLength - 1, 3).equals("});");
	}

	/**
	 * Internal projection listener.
	 */
	private final class ProjectionListener implements IProjectionListener {
		private ProjectionViewer fViewer;

		/**
		 * Registers the listener with the viewer.
		 * @param viewer the viewer to register a listener with
		 */
		public ProjectionListener(ProjectionViewer viewer) {
			Assert.isLegal(viewer != null);
			fViewer = viewer;
			fViewer.addProjectionListener(this);
		}

		/**
		 * Disposes of this listener and removes the projection listener from the viewer.
		 */
		public void dispose() {
			if (fViewer != null) {
				fViewer.removeProjectionListener(this);
				fViewer = null;
			}
		}

		/*
		 * @see org.eclipse.jface.text.source.projection.IProjectionListener#projectionEnabled()
		 */
		public void projectionEnabled() {
			handleProjectionEnabled();
		}

		/*
		 * @see org.eclipse.jface.text.source.projection.IProjectionListener#projectionDisabled()
		 */
		public void projectionDisabled() {
			handleProjectionDisabled();
		}
	}

	/* context and listeners */
	private JavaEditor fEditor;
	private ProjectionListener fProjectionListener;
	private IJavaElement fInput;
	private IElementChangedListener fElementListener;

	/* preferences */
	private boolean fCollapseJavadoc = false;
	private boolean fCollapseImportContainer = true;
	private boolean fCollapseInnerTypes = true;
	private boolean fCollapseMembers = false;
	private boolean fCollapseHeaderComments = true;

	/* filters */
	/** Member filter, matches nested members (but not top-level types). */
	private final Filter fMemberFilter = new MemberFilter();
	/** Comment filter, matches comments. */
	private final Filter fCommentFilter = new CommentFilter();

	/**
	 * Reusable scanner.
	 * @since 3.3
	 */
	private IScanner fSharedScanner = ToolFactory.createScanner(true, false, false, false);

	private volatile int fUpdatingCount = 0;
	private ProjectionViewer viewer;

	/**
	 * Creates a new folding provider. It must be {@link #install(ITextEditor, ProjectionViewer)
	 * installed} on an editor/viewer pair before it can be used, and {@link #uninstall()
	 * uninstalled} when not used any longer.
	 * <p>
	 * The projection state may be reset by calling {@link #initialize()}.
	 * </p>
	 */
	public CutomizedJavaFoldingStructureProvider() {}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Subclasses may extend.
	 * </p>
	 * @param editor {@inheritDoc}
	 * @param viewer {@inheritDoc}
	 */
	public void install(ITextEditor editor, ProjectionViewer viewer) {
		this.viewer = viewer;
		Assert.isLegal(editor != null);
		Assert.isLegal(viewer != null);

		internalUninstall();

		if (editor instanceof JavaEditor) {
			fProjectionListener = new ProjectionListener(viewer);
			fEditor = (JavaEditor) editor;
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Subclasses may extend.
	 * </p>
	 */
	public void uninstall() {
		internalUninstall();
	}

	/**
	 * Internal implementation of {@link #uninstall()}.
	 */
	private void internalUninstall() {
		if (isInstalled()) {
			handleProjectionDisabled();
			fProjectionListener.dispose();
			fProjectionListener = null;
			fEditor = null;
		}
	}

	/**
	 * Returns <code>true</code> if the provider is installed, <code>false</code> otherwise.
	 * @return <code>true</code> if the provider is installed, <code>false</code> otherwise
	 */
	protected final boolean isInstalled() {
		return fEditor != null;
	}

	/**
	 * Called whenever projection is enabled, for example when the viewer issues a
	 * {@link IProjectionListener#projectionEnabled() projectionEnabled} message. When the provider
	 * is already enabled when this method is called, it is first
	 * {@link #handleProjectionDisabled() disabled}.
	 * <p>
	 * Subclasses may extend.
	 * </p>
	 */
	protected void handleProjectionEnabled() {
		// http://home.ott.oti.com/teams/wswb/anon/out/vms/index.html
		// projectionEnabled messages are not always paired with projectionDisabled
		// i.e. multiple enabled messages may be sent out.
		// we have to make sure that we disable first when getting an enable
		// message.
		handleProjectionDisabled();

		if (isInstalled()) {
			initialize();
			fElementListener = new ElementChangedListener();
			JavaCore.addElementChangedListener(fElementListener);
		}
	}

	/**
	 * Called whenever projection is disabled, for example when the provider is {@link #uninstall()
	 * uninstalled}, when the viewer issues a {@link IProjectionListener#projectionDisabled()
	 * projectionDisabled} message and before {@link #handleProjectionEnabled() enabling} the
	 * provider. Implementations must be prepared to handle multiple calls to this method even if
	 * the provider is already disabled.
	 * <p>
	 * Subclasses may extend.
	 * </p>
	 */
	protected void handleProjectionDisabled() {
		if (fElementListener != null) {
			JavaCore.removeElementChangedListener(fElementListener);
			fElementListener = null;
		}
	}

	/*
	 * @see org.eclipse.jdt.ui.text.folding.IJavaFoldingStructureProvider#initialize()
	 */
	public final void initialize() {
		fUpdatingCount++;
		try {
			update(createInitialContext());
		} finally {
			fUpdatingCount--;
		}
	}

	private FoldingStructureComputationContext createInitialContext() {
		initializePreferences();
		fInput = getInputElement();
		if (fInput == null)
			return null;

		FoldingStructureComputationContext context = createContext(true);
		context.initial = true;
		return context;
	}

	private FoldingStructureComputationContext createContext(boolean allowCollapse) {
		if (!isInstalled())
			return null;
		ProjectionAnnotationModel model = getModel();
		if (model == null)
			return null;
		IDocument doc = getDocument();
		if (doc == null)
			return null;

		IScanner scanner = null;
		if (fUpdatingCount == 1)
			scanner = fSharedScanner; // reuse scanner

		return new FoldingStructureComputationContext(doc, model, allowCollapse, scanner);
	}

	private IJavaElement getInputElement() {
		if (fEditor == null)
			return null;
		return EditorUtility.getEditorInputJavaElement(fEditor, false);
	}

	private void initializePreferences() {
		IPreferenceStore store = JavaPlugin.getDefault().getPreferenceStore();
		fCollapseInnerTypes = store.getBoolean(PreferenceConstants.EDITOR_FOLDING_INNERTYPES);
		fCollapseImportContainer = store.getBoolean(PreferenceConstants.EDITOR_FOLDING_IMPORTS);
		fCollapseJavadoc = store.getBoolean(PreferenceConstants.EDITOR_FOLDING_JAVADOC);
		fCollapseMembers = store.getBoolean(PreferenceConstants.EDITOR_FOLDING_METHODS);
		fCollapseHeaderComments = store.getBoolean(PreferenceConstants.EDITOR_FOLDING_HEADERS);
	}

	private void update(FoldingStructureComputationContext ctx) {
		if (ctx == null)
			return;

		Map<Annotation, Position> additions = new HashMap<Annotation, Position>();
		List<Annotation> deletions = new ArrayList<Annotation>();
		List<Annotation> updates = new ArrayList<Annotation>();

		computeFoldingStructure(ctx);
		Map newStructure = ctx.fMap;
		Map oldStructure = computeCurrentStructure(ctx);

		Iterator e = newStructure.keySet().iterator();
		while (e.hasNext()) {
			JavaProjectionAnnotation newAnnotation = (JavaProjectionAnnotation) e.next();
			Position newPosition = (Position) newStructure.get(newAnnotation);

			IJavaElement element = newAnnotation.getElement();
			/*
			 * See https://bugs.eclipse.org/bugs/show_bug.cgi?id=130472 and
			 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=127445 In the presence of syntax
			 * errors, anonymous types may have a source range offset of 0. When such a situation is
			 * encountered, we ignore the proposed folding range: if no corresponding folding range
			 * exists, it is silently ignored; if there *is* a matching folding range, we ignore the
			 * position update and keep the old range, in order to keep the folding structure
			 * stable.
			 */
			boolean isMalformedAnonymousType = newPosition.getOffset() == 0 &&
			        element.getElementType() == IJavaElement.TYPE && isInnerType((IType) element);
			List annotations = (List) oldStructure.get(element);
			if (annotations == null) {
				if (!isMalformedAnonymousType)
					additions.put(newAnnotation, newPosition);
			} else {
				Iterator x = annotations.iterator();
				boolean matched = false;
				while (x.hasNext()) {
					Tuple tuple = (Tuple) x.next();
					JavaProjectionAnnotation existingAnnotation = tuple.annotation;
					Position existingPosition = tuple.position;
					if (newAnnotation.isComment() == existingAnnotation.isComment()) {
						boolean updateCollapsedState = ctx.allowCollapsing() &&
						        existingAnnotation.isCollapsed() != newAnnotation.isCollapsed();
						if (!isMalformedAnonymousType && existingPosition != null &&
						        (!newPosition.equals(existingPosition) || updateCollapsedState)) {
							existingPosition.setOffset(newPosition.getOffset());
							existingPosition.setLength(newPosition.getLength());
							if (updateCollapsedState)
								if (newAnnotation.isCollapsed())
									existingAnnotation.markCollapsed();
								else
									existingAnnotation.markExpanded();
							updates.add(existingAnnotation);
						}
						matched = true;
						x.remove();
						break;
					}
				}
				if (!matched)
					additions.put(newAnnotation, newPosition);

				if (annotations.isEmpty())
					oldStructure.remove(element);
			}
		}

		e = oldStructure.values().iterator();
		while (e.hasNext()) {
			List list = (List) e.next();
			int size = list.size();
			for (int i = 0; i < size; i++)
				deletions.add(((Tuple) list.get(i)).annotation);
		}

		match(deletions, additions, updates, ctx);

		Annotation[] deletedArray = deletions.toArray(new Annotation[deletions.size()]);
		Annotation[] changedArray = updates.toArray(new Annotation[updates.size()]);
		ctx.getModel().modifyAnnotations(deletedArray, additions, changedArray);

		ctx.fScanner.setSource(null);
	}

	private void computeFoldingStructure(FoldingStructureComputationContext ctx) {
		IParent parent = (IParent) fInput;
		try {
			if (!(fInput instanceof ISourceReference))
				return;
			String source = ((ISourceReference) fInput).getSource();
			if (source == null)
				return;

			ctx.getScanner().setSource(source.toCharArray());
			computeFoldingStructure(parent.getChildren(), ctx);
		} catch (JavaModelException x) {}
	}

	private void computeFoldingStructure(IJavaElement[] elements,
	        FoldingStructureComputationContext ctx) throws JavaModelException {
		computeFoldingStructure(elements, ctx, false);
	}

	private void computeFoldingStructure(IJavaElement[] elements,
	        FoldingStructureComputationContext ctx, boolean isInAnonymousType)
	        throws JavaModelException {
		for (int i = 0; i < elements.length; i++) {
			IJavaElement element = elements[i];

			if (isInAnonymousType) {
				if (isAnomymousType(element))
					computeFoldingStructure(element, ctx);
			} else
				computeFoldingStructure(element, ctx);

			if (element instanceof IParent)
				computeFoldingStructure(((IParent) element).getChildren(), ctx,
				        isAnomymousType(element));
		}
	}

	private boolean isAnomymousType(IJavaElement element) throws JavaModelException {
		return element instanceof IType && ((IType) element).isAnonymous();
	}

	public static class JavaImportPosition extends Position implements IProjectionPosition {

		private static final int IMPORT_KEYWORD_LENGTH = "import".length();
		private final IImportContainer importContainer;

		public JavaImportPosition(IRegion projectionRegion, IImportContainer importContainer) {
			super(projectionRegion.getOffset(), projectionRegion.getLength());
			this.importContainer = importContainer;
		}

		public int computeCaptionOffset(IDocument document) throws BadLocationException {
			return IMPORT_KEYWORD_LENGTH;
		}

		public IRegion[] computeProjectionRegions(IDocument document) throws BadLocationException {
			try {
				if (SourceRange.isAvailable(importContainer.getSourceRange()) &&
				        importContainer.getSource() != null) {

					Region hiddenRegion = new Region(getOffset() + IMPORT_KEYWORD_LENGTH,
					        getLength() - IMPORT_KEYWORD_LENGTH);
					return new IRegion[] { hiddenRegion };
				}
			} catch (Exception e) {}

			return null;
		}
	}

	/**
	 * Computes the folding structure for a given {@link IJavaElement java element}. Computed
	 * projection annotations are
	 * {@link CutomizedJavaFoldingStructureProvider.FoldingStructureComputationContext#addProjectionRange(CutomizedJavaFoldingStructureProvider.JavaProjectionAnnotation, Position)
	 * added} to the computation context.
	 * <p>
	 * Subclasses may extend or replace. The default implementation creates projection annotations
	 * for the following elements:
	 * <ul>
	 * <li>true members (not for top-level types)</li>
	 * <li>the javadoc comments of any member</li>
	 * <li>header comments (javadoc or multi-line comments appearing before the first type's javadoc
	 * or before the package or import declarations).</li>
	 * </ul>
	 * </p>
	 * @param element the java element to compute the folding structure for
	 * @param ctx the computation context
	 */
	protected void computeFoldingStructure(IJavaElement element,
	        FoldingStructureComputationContext ctx) {

		IMethod lambdaMethod = null;
/*		boolean importContainer = false;*/
		boolean collapse = false;
		boolean collapseCode = true;
		switch (element.getElementType()) {

		case IJavaElement.IMPORT_CONTAINER: {

			IImportContainer importContainer = (IImportContainer) element;
			IRegion projectionRegion = computeImportProjectionRanges(importContainer, ctx);

			if (projectionRegion != null) {
				JavaImportPosition importPosition = new JavaImportPosition(projectionRegion,
				        importContainer);

				ctx.addProjectionRange(new JavaProjectionAnnotation(ctx.collapseImportContainer(),
				        ctx, element, true), importPosition);
			}

			return;
		}
		case IJavaElement.TYPE:
			collapseCode = isInnerType((IType) element) && !isAnonymousEnum((IType) element);
			collapse = ctx.collapseInnerTypes() && collapseCode;

			lambdaMethod = findLambdaMethodIn(element);

			if (lambdaMethod != null) {
				// Let collapse initially by default
				collapse = ctx.initial;
			} else {
				try {
					if (((IType) element).isAnonymous())
						return;
				} catch (Exception e) {}
			}
			break;
		case IJavaElement.METHOD:
		case IJavaElement.FIELD:
		case IJavaElement.INITIALIZER:
			collapse = ctx.collapseMembers();
			collapseCode = false;
			break;
		default:
			return;
		}

		IRegion[] regions = computeProjectionRanges((ISourceReference) element, ctx);

		if (regions.length == 0)
			return;

		// comments
		for (int i = 0; i < regions.length - 1; i++) {
			IRegion normalized = alignRegion(regions[i], ctx);
			if (normalized != null) {
				Position position = createCommentPosition(normalized);
				if (position != null) {
					boolean commentCollapse;
					if (i == 0 && (regions.length > 2 || ctx.hasHeaderComment()) &&
					        element == ctx.getFirstType()) {
						commentCollapse = ctx.collapseHeaderComments();
					} else {
						commentCollapse = ctx.collapseJavadoc();
					}
					ctx.addProjectionRange(new JavaProjectionAnnotation(commentCollapse, ctx,
					        element, true), position);
				}
			}
		}
		// code
		if (!collapseCode)
			return;

// IRegion lastRegion = regions[regions.length - 1];

		IRegion codeRegion = regions[regions.length - 1];

// if (lambdaMethod != null) {
// normalized = alignRegion(lastRegion, ctx);
// }

		if (codeRegion != null) {
			Position position = element instanceof IMember ? createMemberPosition(codeRegion,
			        (IMember) element, lambdaMethod) : createCommentPosition(codeRegion);

			if (position != null)
				ctx.addProjectionRange(new JavaProjectionAnnotation(collapse, ctx, element, false),
				        position);
		}
	}

	private IRegion computeImportProjectionRanges(IImportContainer element,
	        FoldingStructureComputationContext ctx) {
		try {
			ISourceRange range = element.getSourceRange();
			String contents = null;
			if (SourceRange.isAvailable(range) && (contents = element.getSource()) != null) {
				Region importRegion = new Region(range.getOffset(), range.getLength());
				return importRegion;
				/*return alignRegion(importRegion, ctx);*/
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;

	}

	/**
	 * Returns <code>true</code> if <code>type</code> is an anonymous enum declaration,
	 * <code>false</code> otherwise. See also https://bugs.eclipse.org/bugs/show_bug.cgi?id=143276
	 * @param type the type to test
	 * @return <code>true</code> if <code>type</code> is an anonymous enum declaration
	 * @since 3.3
	 */
	private boolean isAnonymousEnum(IType type) {
		try {
			return type.isEnum() && type.isAnonymous();
		} catch (JavaModelException x) {
			return false; // optimistically
		}
	}

	/**
	 * Returns <code>true</code> if <code>type</code> is not a top-level type, <code>false</code> if
	 * it is.
	 * @param type the type to test
	 * @return <code>true</code> if <code>type</code> is an inner type
	 */
	private boolean isInnerType(IType type) {
		return type.getDeclaringType() != null;
	}

	/**
	 * Computes the projection ranges for a given <code>ISourceReference</code>. More than one range
	 * or none at all may be returned. If there are no foldable regions, an empty array is returned.
	 * <p>
	 * The last region in the returned array (if not empty) describes the region for the java
	 * element that implements the source reference. Any preceding regions describe javadoc comments
	 * of that java element.
	 * </p>
	 * @param reference a java element that is a source reference
	 * @param ctx the folding context
	 * @return the regions to be folded
	 */
	protected final IRegion[] computeProjectionRanges(ISourceReference reference,
	        FoldingStructureComputationContext ctx) {
		try {
			ISourceRange range = reference.getSourceRange();
			if (!SourceRange.isAvailable(range))
				return new IRegion[0];

			String contents = reference.getSource();
			if (contents == null)
				return new IRegion[0];

			List<IRegion> regions = new ArrayList<IRegion>();
			if (!ctx.hasFirstType() && reference instanceof IType) {
				ctx.setFirstType((IType) reference);
				IRegion headerComment = computeHeaderComment(ctx);
				if (headerComment != null) {
					regions.add(headerComment);
					ctx.setHasHeaderComment();
				}
			}

			final int shift = range.getOffset();
			IScanner scanner = ctx.getScanner();
			scanner.resetTo(shift, shift + range.getLength());

			int start = shift;
			while (true) {

				int token = scanner.getNextToken();
				start = scanner.getCurrentTokenStartPosition();

				switch (token) {
				case ITerminalSymbols.TokenNameCOMMENT_JAVADOC:
				case ITerminalSymbols.TokenNameCOMMENT_BLOCK: {
					int end = scanner.getCurrentTokenEndPosition() + 1;
					regions.add(new Region(start, end - start));
					continue;
				}
				case ITerminalSymbols.TokenNameCOMMENT_LINE:
					continue;
				}

				break;
			}

			regions.add(new Region(start, shift + range.getLength() - start));

			return regions.toArray(new IRegion[regions.size()]);
		} catch (JavaModelException e) {} catch (InvalidInputException e) {}

		return new IRegion[0];
	}

	private IRegion computeHeaderComment(FoldingStructureComputationContext ctx)
	        throws JavaModelException {
		// search at most up to the first type
		ISourceRange range = ctx.getFirstType().getSourceRange();
		if (range == null)
			return null;
		int start = 0;
		int end = range.getOffset();

		/* code adapted from CommentFormattingStrategy:
		 * scan the header content up to the first type. Once a comment is
		 * found, accumulate any additional comments up to the stop condition.
		 * The stop condition is reaching a package declaration, import container,
		 * or the end of the input.
		 */
		IScanner scanner = ctx.getScanner();
		scanner.resetTo(start, end);

		int headerStart = -1;
		int headerEnd = -1;
		try {
			boolean foundComment = false;
			int terminal = scanner.getNextToken();
			while (terminal != ITerminalSymbols.TokenNameEOF &&
			        !(terminal == ITerminalSymbols.TokenNameclass ||
			                terminal == ITerminalSymbols.TokenNameinterface ||
			                terminal == ITerminalSymbols.TokenNameenum || (foundComment && (terminal == ITerminalSymbols.TokenNameimport || terminal == ITerminalSymbols.TokenNamepackage)))) {

				if (terminal == ITerminalSymbols.TokenNameCOMMENT_JAVADOC ||
				        terminal == ITerminalSymbols.TokenNameCOMMENT_BLOCK ||
				        terminal == ITerminalSymbols.TokenNameCOMMENT_LINE) {
					if (!foundComment)
						headerStart = scanner.getCurrentTokenStartPosition();
					headerEnd = scanner.getCurrentTokenEndPosition();
					foundComment = true;
				}
				terminal = scanner.getNextToken();
			}

		} catch (InvalidInputException ex) {
			return null;
		}

		if (headerEnd != -1) {
			return new Region(headerStart, headerEnd - headerStart);
		}
		return null;
	}

	/**
	 * Creates a comment folding position from an
	 * {@link #alignRegion(IRegion, CutomizedJavaFoldingStructureProvider.FoldingStructureComputationContext)
	 * aligned} region.
	 * @param aligned an aligned region
	 * @return a folding position corresponding to <code>aligned</code>
	 */
	protected final Position createCommentPosition(IRegion aligned) {
		return new JavaCommentPosition(aligned.getOffset(), aligned.getLength());
	}

	/**
	 * Creates a folding position that remembers its member from an
	 * {@link #alignRegion(IRegion, CutomizedJavaFoldingStructureProvider.FoldingStructureComputationContext)
	 * aligned} region.
	 * @param aligned an aligned region
	 * @param member the member to remember
	 * @return a folding position corresponding to <code>aligned</code>
	 */
	protected final Position createMemberPosition(IRegion aligned, IMember member,
	        IMethod lambdaMethod) {
		return new JavaElementPosition(aligned.getOffset(), aligned.getLength(), member,
		        lambdaMethod);
	}

	/**
	 * Aligns <code>region</code> to start and end at a line offset. The region's start is decreased
	 * to the next line offset, and the end offset increased to the next line start or the end of
	 * the document. <code>null</code> is returned if <code>region</code> is <code>null</code>
	 * itself or does not comprise at least one line delimiter, as a single line cannot be folded.
	 * @param region the region to align, may be <code>null</code>
	 * @param ctx the folding context
	 * @return a region equal or greater than <code>region</code> that is aligned with line offsets,
	 *         <code>null</code> if the region is too small to be foldable (e.g. covers only one
	 *         line)
	 */
	protected final IRegion alignRegion(IRegion region, FoldingStructureComputationContext ctx) {
		if (region == null)
			return null;

		IDocument document = ctx.getDocument();

		try {

			int start = document.getLineOfOffset(region.getOffset());
			int end = document.getLineOfOffset(region.getOffset() + region.getLength());
			if (start >= end)
				return null;

			int offset = document.getLineOffset(start);
			int endOffset;
			if (document.getNumberOfLines() > end + 1)
				endOffset = document.getLineOffset(end + 1);
			else
				endOffset = document.getLineOffset(end) + document.getLineLength(end);

			return new Region(offset, endOffset - offset);

		} catch (BadLocationException x) {
			// concurrent modification
			return null;
		}
	}

	private ProjectionAnnotationModel getModel() {
		return (ProjectionAnnotationModel) fEditor.getAdapter(ProjectionAnnotationModel.class);
	}

	private IDocument getDocument() {
		JavaEditor editor = fEditor;
		if (editor == null)
			return null;

		IDocumentProvider provider = editor.getDocumentProvider();
		if (provider == null)
			return null;

		return provider.getDocument(editor.getEditorInput());
	}

	/**
	 * Matches deleted annotations to changed or added ones. A deleted annotation/position tuple
	 * that has a matching addition / change is updated and marked as changed. The matching tuple is
	 * not added (for additions) or marked as deletion instead (for changes). The result is that
	 * more annotations are changed and fewer get deleted/re-added.
	 * @param deletions list with deleted annotations
	 * @param additions map with position to annotation mappings
	 * @param changes list with changed annotations
	 * @param ctx the context
	 */
	private void match(List<Annotation> deletions, Map<Annotation, Position> additions,
	        List<Annotation> changes, FoldingStructureComputationContext ctx) {
		if (deletions.isEmpty() || (additions.isEmpty() && changes.isEmpty()))
			return;

		List<Annotation> newDeletions = new ArrayList<Annotation>();
		List<Annotation> newChanges = new ArrayList<Annotation>();

		Iterator deletionIterator = deletions.iterator();
		while (deletionIterator.hasNext()) {
			JavaProjectionAnnotation deleted = (JavaProjectionAnnotation) deletionIterator.next();
			Position deletedPosition = ctx.getModel().getPosition(deleted);
			if (deletedPosition == null)
				continue;

			Tuple deletedTuple = new Tuple(deleted, deletedPosition);

			Tuple match = findMatch(deletedTuple, changes, null, ctx);
			boolean addToDeletions = true;
			if (match == null) {
				match = findMatch(deletedTuple, additions.keySet(), additions, ctx);
				addToDeletions = false;
			}

			if (match != null) {
				IJavaElement element = match.annotation.getElement();
				deleted.setElement(element);
				deletedPosition.setLength(match.position.getLength());
				if (deletedPosition instanceof JavaElementPosition && element instanceof IMember) {
					JavaElementPosition jep = (JavaElementPosition) deletedPosition;
					jep.setMember((IMember) element);
				}

				deletionIterator.remove();
				newChanges.add(deleted);

				if (addToDeletions)
					newDeletions.add(match.annotation);
			}
		}

		deletions.addAll(newDeletions);
		changes.addAll(newChanges);
	}

	/**
	 * Finds a match for <code>tuple</code> in a collection of annotations. The positions for the
	 * <code>JavaProjectionAnnotation</code> instances in <code>annotations</code> can be found in
	 * the passed <code>positionMap</code> or <code>fCachedModel</code> if <code>positionMap</code>
	 * is <code>null</code>.
	 * <p>
	 * A tuple is said to match another if their annotations have the same comment flag and their
	 * position offsets are equal.
	 * </p>
	 * <p>
	 * If a match is found, the annotation gets removed from <code>annotations</code>.
	 * </p>
	 * @param tuple the tuple for which we want to find a match
	 * @param annotations collection of <code>JavaProjectionAnnotation</code>
	 * @param positionMap a <code>Map&lt;Annotation, Position&gt;</code> or <code>null</code>
	 * @param ctx the context
	 * @return a matching tuple or <code>null</code> for no match
	 */
	private Tuple findMatch(Tuple tuple, Collection annotations, Map positionMap,
	        FoldingStructureComputationContext ctx) {
		Iterator it = annotations.iterator();
		while (it.hasNext()) {
			JavaProjectionAnnotation annotation = (JavaProjectionAnnotation) it.next();
			if (tuple.annotation.isComment() == annotation.isComment()) {

				Position position = positionMap == null ? ctx.getModel().getPosition(annotation)
				        : (Position) positionMap.get(annotation);
				if (position == null)
					continue;

				if (tuple.position.getOffset() == position.getOffset()) {
					it.remove();
					return new Tuple(annotation, position);
				}
			}
		}

		return null;
	}

	private Map computeCurrentStructure(FoldingStructureComputationContext ctx) {
		Map<IJavaElement, List<Tuple>> map = new HashMap<IJavaElement, List<Tuple>>();
		ProjectionAnnotationModel model = ctx.getModel();
		Iterator e = model.getAnnotationIterator();
		while (e.hasNext()) {
			Object annotation = e.next();
			if (annotation instanceof JavaProjectionAnnotation) {
				JavaProjectionAnnotation java = (JavaProjectionAnnotation) annotation;
				Position position = model.getPosition(java);
				Assert.isNotNull(position);
				List<Tuple> list = map.get(java.getElement());
				if (list == null) {
					list = new ArrayList<Tuple>(2);
					map.put(java.getElement(), list);
				}
				list.add(new Tuple(java, position));
			}
		}

		Comparator<Tuple> comparator = new Comparator<Tuple>() {
			public int compare(Tuple o1, Tuple o2) {
				return o1.position.getOffset() - o2.position.getOffset();
			}
		};
		for (List<Tuple> list : map.values())
			Collections.sort(list, comparator);

		return map;
	}

	/*
	 * @see IJavaFoldingStructureProviderExtension#collapseMembers()
	 * @since 3.2
	 */
	public final void collapseMembers() {
		modifyFiltered(fMemberFilter, false);
	}

	/*
	 * @see IJavaFoldingStructureProviderExtension#collapseComments()
	 * @since 3.2
	 */
	public final void collapseComments() {
		modifyFiltered(fCommentFilter, false);
	}

	/*
	 * @see org.eclipse.jdt.ui.text.folding.IJavaFoldingStructureProviderExtension#collapseElements(org.eclipse.jdt.core.IJavaElement[])
	 */
	public final void collapseElements(IJavaElement[] elements) {
		Set<IJavaElement> set = new HashSet<IJavaElement>(Arrays.asList(elements));
		modifyFiltered(new JavaElementSetFilter(set, false), false);
	}

	/*
	 * @see org.eclipse.jdt.ui.text.folding.IJavaFoldingStructureProviderExtension#expandElements(org.eclipse.jdt.core.IJavaElement[])
	 */
	public final void expandElements(IJavaElement[] elements) {
		Set<IJavaElement> set = new HashSet<IJavaElement>(Arrays.asList(elements));
		modifyFiltered(new JavaElementSetFilter(set, true), true);
	}

	/**
	 * Collapses or expands all annotations matched by the passed filter.
	 * @param filter the filter to use to select which annotations to collapse
	 * @param expand <code>true</code> to expand the matched annotations, <code>false</code> to
	 *            collapse them
	 */
	private void modifyFiltered(Filter filter, boolean expand) {
		if (!isInstalled())
			return;

		ProjectionAnnotationModel model = getModel();
		if (model == null)
			return;

		List<Annotation> modified = new ArrayList<Annotation>();
		Iterator iter = model.getAnnotationIterator();
		while (iter.hasNext()) {
			Object annotation = iter.next();
			if (annotation instanceof JavaProjectionAnnotation) {
				JavaProjectionAnnotation java = (JavaProjectionAnnotation) annotation;

				if (expand == java.isCollapsed() && filter.match(java)) {
					if (expand)
						java.markExpanded();
					else
						java.markCollapsed();
					modified.add(java);
				}

			}
		}

		model.modifyAnnotations(null, null, modified.toArray(new Annotation[modified.size()]));
	}
}
