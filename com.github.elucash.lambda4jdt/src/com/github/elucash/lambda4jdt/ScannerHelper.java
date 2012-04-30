package com.github.elucash.lambda4jdt;

import static org.eclipse.jdt.core.compiler.ITerminalSymbols.*;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.InvalidInputException;

class ScannerHelper {

	final IScanner scanner;
	boolean eof;
	int token = -1;
	int offset = -1;
	int endOffset = -1;
	char[] identifier;
	int firstNonWhitespace = -1;
	int identifierOffset = -1;
	boolean wasFlowControlStatement;
	int lastBraceBlockEnd = -1;

	ScannerHelper(String source) {
		this.scanner = ToolFactory.createScanner(false, false, true, true);
		char[] charArraySource = source.toCharArray();
		scanner.setSource(charArraySource);
		scanner.resetTo(0, charArraySource.length);
	}

	private int updateFields(int lastToken) {
		if (lastToken == TokenNameEOF) {
			eof = true;
			token = -1;
			endOffset = -1;
			offset = -1;
		} else {
			token = lastToken;
			offset = scanner.getCurrentTokenStartPosition();
			endOffset = scanner.getCurrentTokenEndPosition();
			eof = false;
		}
		return token;
	}

	private void resetFieldsBeforeSeek() {
		wasFlowControlStatement = false;
		lastBraceBlockEnd = -1;
		firstNonWhitespace = -1;
		identifier = null;
		identifierOffset = -1;
	}

	private int nextToken() throws InvalidInputException {
		int t = scanner.getNextToken();
		if (t == TokenNameIdentifier) {
			identifier = scanner.getCurrentTokenSource();
			identifierOffset = scanner.getCurrentTokenStartPosition();
		}
		if (t != TokenNameWHITESPACE && firstNonWhitespace < 0) {
			firstNonWhitespace = scanner.getCurrentTokenStartPosition();
		}
		if (t == TokenNamewhile || t == TokenNamedo || t == TokenNameif || t == TokenNamefor ||
		        t == TokenNametry) {
			wasFlowControlStatement = true;
		}
		return t;
	}

	int[] lineEnds() {
		return scanner.getLineEnds();
	}

	int seek(int... tokensToSeek) {
		resetFieldsBeforeSeek();
		try {
			for (;;) {
				int t = nextToken();
				if (t == TokenNameEOF)
					return updateFields(t);

				for (int i : tokensToSeek) {
					if (i == t)
						return updateFields(t);

				}
			}
		} catch (InvalidInputException e) {}

		return -1;
	}

	int seekAnyExcept(int... tokensToSeekExcept) {
		resetFieldsBeforeSeek();
		try {
			for (;;) {
				int t = nextToken();
				if (t == TokenNameEOF)
					return updateFields(t);

				for (int i : tokensToSeekExcept)
					if (i == t)
						continue;

				return updateFields(t);
			}
		} catch (InvalidInputException e) {}

		return -1;
	}
	
	int seekCorrespondingWithTypeParameterBrackets(int... tokensToSeek) {
		resetFieldsBeforeSeek();
		try {
			for (int nestingLevel = 0;;) {
				int t = nextToken();
				if (t == TokenNameEOF)
					return updateFields(t);

				if (nestingLevel <= 0)
					for (int i : tokensToSeek)
						if (i == t)
							return updateFields(t);

				switch (t) {
				case TokenNameLPAREN:
				case TokenNameLBRACE:
				case TokenNameLESS:
					nestingLevel++;
					break;
				case TokenNameRIGHT_SHIFT:
					nestingLevel-=2;
					break;
				case TokenNameUNSIGNED_RIGHT_SHIFT:
					nestingLevel-=3;
					break;
				case TokenNameGREATER:
					nestingLevel--;
					break;
				case TokenNameRPAREN:
					nestingLevel--;
					break;
				case TokenNameRBRACE:
					nestingLevel--;
					lastBraceBlockEnd = scanner.getCurrentTokenStartPosition();
					break;
				}
			}
		} catch (InvalidInputException e) {}

		return -1;
	}

	int seekCorresponding(int... tokensToSeek) {
		resetFieldsBeforeSeek();
		try {
			for (int nestingLevel = 0;;) {
				int t = nextToken();
				if (t == TokenNameEOF)
					return updateFields(t);

				if (nestingLevel <= 0)
					for (int i : tokensToSeek)
						if (i == t)
							return updateFields(t);

				switch (t) {
				case TokenNameLPAREN:
				case TokenNameLBRACE:
					nestingLevel++;
					break;

				case TokenNameRPAREN:
					nestingLevel--;
					break;
				case TokenNameRBRACE:
					nestingLevel--;
					lastBraceBlockEnd = scanner.getCurrentTokenStartPosition();
					break;
				}
			}
		} catch (InvalidInputException e) {}

		return -1;
	}

}
