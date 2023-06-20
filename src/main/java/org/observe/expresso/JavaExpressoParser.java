package org.observe.expresso;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.observe.expresso.ops.*;
import org.qommons.StringUtils;
import org.qommons.collect.BetterList;

/**
 * <p>
 * An expresso parser that interprets java expressions as {@link ObservableExpression}s.
 * </p>
 * <p>
 * This class uses <a href="https://github.com/antlr/grammars-v4/blob/master/java/java/JavaParser.g4">this</a> grammar, modified slightly
 * for my use, including:
 * <ol>
 * <li>"Literal Expressions" are supported, which are sequences similar to strings, but bounded by grave accents (`). The content inside
 * these sequences may be parsed by a {@link NonStructuredParser} depending on the expected type of the expression. This allows for easier
 * specification of dates, durations, enums, etc., including context-sensitive parsing.</li>
 * </ol>
 * </p>
 */
public class JavaExpressoParser implements ExpressoParser {
	@Override
	public ObservableExpression parse(String text) throws ExpressoParseException {
		if (text.trim().isEmpty())
			return ObservableExpression.EMPTY;
		// The ANTLR system writes to standard err in the background, so we have to deliberately ignore it
		PrintStream oldErr = System.err;
		System.setErr(new PrintStream(new OutputStream() {
			@Override
			public void write(int b) throws IOException { // Suppress ANTLR printout
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException { // Suppress ANTLR printout
			}
		}));
		ExpressoAntlrParser parser;
		ParseTree result;
		try {
			ExpressoAntlrLexer lexer = new ExpressoAntlrLexer(CharStreams.fromString(text));
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			parser = new ExpressoAntlrParser(tokens);
			result = parser.expressionFull();
		} finally {
			System.setErr(oldErr);
		}
		Expression parsed = Expression.of(parser, result);
		return _parse(parsed, text);
	}

	private ObservableExpression _parse(Expression expression, String fullText) throws ExpressoParseException {
		ObservableExpression result;
		try {
			result = parse(expression, fullText);
		} catch (RuntimeException e) {
			throw new ExpressoParseException(expression, "Expression parsing failed", e);
		}
		return result;
	}

	/**
	 * @param expression The expression, pre-parsed with ANTLR, to interpret
	 * @param fullText The text that the expression was parsed from
	 * @return The {@link ObservableExpression} represented by the expression
	 * @throws ExpressoParseException If the expression cannot be interpreted
	 */
	protected ObservableExpression parse(Expression expression, String fullText) throws ExpressoParseException {
		List<BufferedType> typeArgs;
		List<ObservableExpression> args;
		Expression firstChild = expression.getComponents().peekFirst();
		switch (expression.getType()) {
		case "expressionFull":
			return _parse(firstChild, fullText);
		case "expression":
			switch (expression.getComponents().size()) {
			case 1:
				return _parse(firstChild, fullText);
			case 2:
				switch (firstChild.getText()) {
				case "!":
				case "+":
				case "-":
				case "~":
				case "++":
				case "--":
					ObservableExpression operand = _parse(expression.getComponents().getLast(), fullText);
					int ws = getWhiteSpaceAt(fullText, firstChild.getStartIndex() + operand.getExpressionLength());
					return new UnaryOperator(firstChild.getText(), BufferedExpression.buffer(ws, operand, 0), true);
				case "new":
					Expression creator = expression.getComponents().get(1);
					if (creator.getComponent("nonWildcardTypeArguments") != null)
						throw new ExpressoParseException(creator, "Constructor invocation type parameters are not supported");
					else if (creator.getComponent("classCreatorRest", "classBody") != null)
						throw new ExpressoParseException(creator, "Anonymous inner classes are not supported");
					else {
						List<BufferedName> typeName = new ArrayList<>();
						int typeOffset = -1;
						typeArgs = null;
						for (Expression ch : creator.getComponent("createdName").getComponents()) {
							if ("typeArgumentsOrDiamond".equals(ch.getType())) {
								typeArgs = new ArrayList<>();
								for (Expression t : ch.getComponents("typeArguments", "typeArgument"))
									typeArgs.add(parseType(t));
							} else if (typeArgs != null)
								throw new ExpressoParseException(expression, "Non-static member constructors are not supported yet");
							else {
								if (typeOffset < 0)
									typeOffset = ch.getStartIndex();
								typeName.add(BufferedName.buffer(//
									getWhiteSpaceBefore(fullText, ch.getStartIndex(), expression.getStartIndex()), //
									ch.getText(), //
									getWhiteSpaceAt(fullText, ch.getEndIndex())));
							}
						}
						if (creator.getComponent("arrayCreatorRest") != null) {
							// TODO
							throw new ExpressoParseException(expression, "Array creation is not yet implemented");
						} else {
							args = new ArrayList<>();
							for (Expression arg : creator.getComponents("classCreatorRest", "arguments", "expressionList", "expression")) {
								ObservableExpression argEx = _parse(arg, fullText);
								argEx = BufferedExpression.buffer(getWhiteSpaceBefore(fullText, arg.getStartIndex(), 0), argEx,
									getWhiteSpaceAt(fullText, arg.getStartIndex() + argEx.getExpressionLength()));
								args.add(argEx);
							}
							return new ConstructorInvocation(new BufferedType(Collections.unmodifiableList(typeName)),
								typeArgs == null ? null : Collections.unmodifiableList(typeArgs), args);
						}
					}
				default:
					switch (expression.getComponents().getLast().getText()) {
					case "++":
					case "--":
						operand = _parse(firstChild, fullText);
						operand = BufferedExpression.buffer(0, operand,
							getWhiteSpaceAt(fullText, firstChild.getStartIndex() + operand.getExpressionLength()));
						return new UnaryOperator(expression.getComponents().getLast().getText(), operand, false);
					default:
						throw new IllegalStateException("Unhandled expression type: " + expression.getType() + " " + expression);
					}
				}
			default:
				if ("(".equals(firstChild.getText())) {
					BufferedType type = parseType(expression);
					Expression valueX = expression.getComponents().getLast();
					ObservableExpression value = _parse(valueX, fullText);
					value = BufferedExpression.buffer(getWhiteSpaceBefore(fullText, valueX.getStartIndex(), 0), value, 0);
					return new CastExpression(value, type);
				}
				switch (expression.getComponents().get(1).getText()) {
				case ".":
					ObservableExpression context = _parse(firstChild, fullText);
					int buffer = getWhiteSpaceAt(fullText, firstChild.getStartIndex() + context.getExpressionLength());
					if (buffer > 0) {
						if (context instanceof NameExpression) {
							List<BufferedName> names = new ArrayList<>(((NameExpression) context).getNames());
							BufferedName last = names.get(names.size() - 1);
							names.set(names.size() - 1, BufferedName.buffer(last.getBefore(), last.getName(), last.getAfter() + buffer));
							context = new NameExpression(((NameExpression) context).getContext(), BetterList.of(names));
						} else
							context = BufferedExpression.buffer(0, context, buffer);
					}
					Expression child = expression.getComponents().getLast();
					switch (child.getType()) {
					case "identifier":
					case "THIS":
					case "SUPER":
						BufferedName newName = BufferedName.buffer(//
							getWhiteSpaceBefore(fullText, child.getStartIndex(), expression.getStartIndex()), //
							child.getText(), getWhiteSpaceAt(fullText, child.getEndIndex()));
						if (context instanceof NameExpression) {
							List<BufferedName> names = new ArrayList<>(((NameExpression) context).getNames());
							names.add(newName);
							return new NameExpression(null, BetterList.of(names));
						} else
							return new NameExpression(context, BetterList.of(newName));
					case "methodCall":
						Expression methodNameX = child.getComponents().getFirst();
						BufferedName methodName = BufferedName.buffer(//
							getWhiteSpaceBefore(fullText, methodNameX.getStartIndex(), expression.getStartIndex()), //
							methodNameX.getText(), //
							getWhiteSpaceAt(fullText, methodNameX.getEndIndex()));
						args = new ArrayList<>();
						for (Expression arg : child.getComponents("expressionList", "expression")) {
							ObservableExpression argEx = _parse(arg, fullText);
							argEx = BufferedExpression.buffer(getWhiteSpaceBefore(fullText, arg.getStartIndex(), 0), //
								argEx, getWhiteSpaceAt(fullText, arg.getStartIndex() + argEx.getExpressionLength()));
							args.add(argEx);
						}
						return new MethodInvocation(context, methodName, null, args);
					case "NEW":
						throw new ExpressoParseException(expression, "Non-static member constructors are not supported yet");
					case "explicitGenericInvocation":
						throw new ExpressoParseException(expression, expression.getType() + " expressions are not supported yet");
					default:
						break;
					}
					break;
				case "=":
					context = _parse(firstChild, fullText);
					if (!(context instanceof NameExpression))
						throw new ExpressoParseException(expression,
							"Expression of type " + context.getClass().getName() + " cannot be assigned a value");
					context = BufferedExpression.buffer(0, context,
						getWhiteSpaceAt(fullText, firstChild.getStartIndex() + context.getExpressionLength()));
					Expression valueX = expression.getComponents().getLast();
					ObservableExpression value = _parse(valueX, fullText);
					value = BufferedExpression.buffer(getWhiteSpaceBefore(fullText, valueX.getStartIndex(), 0), //
						value, getWhiteSpaceAt(fullText, valueX.getStartIndex() + value.getExpressionLength()));
					return new AssignmentExpression(context, value);
				case "+":
				case "-":
				case "*":
				case "/":
				case "%":
				case "==":
				case "!=":
				case "<":
				case ">":
				case "<=":
				case ">=":
				case "||":
				case "&&":
				case "^":
				case "|":
				case "&":
				case "<<":
				case ">>":
				case ">>>":
				case "+=":
				case "-=":
				case "*=":
				case "/=":
				case "%=":
				case "&=":
				case "|=":
				case "^=":
				case "<<=":
				case ">>=":
				case ">>>=":
					ObservableExpression left = _parse(firstChild, fullText);
					left = BufferedExpression.buffer(0, left,
						getWhiteSpaceAt(fullText, firstChild.getStartIndex() + left.getExpressionLength()));
					Expression rightX = expression.getComponents().getLast();
					ObservableExpression right = _parse(rightX, fullText);
					right = BufferedExpression.buffer(getWhiteSpaceBefore(fullText, rightX.getStartIndex(), 0), right, 0);
					return new BinaryOperator(expression.getComponents().get(1).getText(), left, right);
				case "instanceof":
					left = _parse(firstChild, fullText);
					left = BufferedExpression.buffer(0, left,
						getWhiteSpaceAt(fullText, firstChild.getStartIndex() + left.getExpressionLength()));
					return new InstanceofExpression(left, parseType(expression.getComponents().getLast()));
				case "?":
					ObservableExpression condition = _parse(firstChild, fullText);
					condition = BufferedExpression.buffer(0, condition,
						getWhiteSpaceAt(fullText, firstChild.getStartIndex() + condition.getExpressionLength()));
					Expression primaryX = expression.getComponents().get(2);
					ObservableExpression primary = _parse(primaryX, fullText);
					primary = BufferedExpression.buffer(getWhiteSpaceBefore(fullText, primaryX.getStartIndex(), 0), primary, //
						getWhiteSpaceAt(fullText, primaryX.getStartIndex() + primary.getExpressionLength()));
					Expression secondaryX = expression.getComponents().getLast();
					ObservableExpression secondary = _parse(secondaryX, fullText);
					secondary = BufferedExpression.buffer(getWhiteSpaceBefore(fullText, secondaryX.getStartIndex(), 0), secondary, 0);
					return new ConditionalExpression(condition, primary, secondary);
				case "[":
					ObservableExpression array = _parse(firstChild, fullText);
					array = BufferedExpression.buffer(0, array,
						getWhiteSpaceAt(fullText, firstChild.getStartIndex() + array.getExpressionLength()));
					Expression indexX = expression.getComponents().get(2);
					ObservableExpression index = _parse(indexX, fullText);
					index = BufferedExpression.buffer(getWhiteSpaceBefore(fullText, indexX.getStartIndex(), 0), index, //
						getWhiteSpaceAt(fullText, indexX.getStartIndex() + index.getExpressionLength()));
					return new ArrayAccessExpression(array, index);
				case "::":
					throw new ExpressoParseException(expression, "Method references are not supported");
				default:
					break;
				}
			}
			// TODO
			throw new IllegalStateException("Unhandled expression type: " + expression.getType() + " " + expression);
		case "primary":
			if (expression.getComponents().size() == 1) {
				Expression child = firstChild;
				switch (child.getText()) {
				case "this":
				case "super":
					BufferedName name = BufferedName.buffer(
						getWhiteSpaceBefore(fullText, child.getStartIndex(), expression.getStartIndex()), //
						child.getText(), //
						getWhiteSpaceAt(fullText, child.getEndIndex()));
					return new NameExpression(null, BetterList.of(name));
				default:
					break;
				}
				switch (child.getType()) {
				case "literal":
					return _parse(child.getComponents().getFirst(), fullText);
				case "identifier":
					BufferedName name = BufferedName.buffer(
						getWhiteSpaceBefore(fullText, child.getStartIndex(), expression.getStartIndex()), //
						child.getText(), //
						getWhiteSpaceAt(fullText, child.getEndIndex()));
					return new NameExpression(null, BetterList.of(name));
				default:
					break;
				}
			} else if (expression.getComponents().size() == 3) {
				switch (expression.getComponents().get(2).getText()) {
				case ")":
					Expression valueX = expression.getComponents().get(1);

					ObservableExpression value = _parse(valueX, fullText);
					value = BufferedExpression.buffer(getWhiteSpaceBefore(fullText, valueX.getStartIndex(), 0), //
						value, //
						getWhiteSpaceAt(fullText, valueX.getStartIndex() + value.getExpressionLength()));
					return new ParentheticExpression(value);
				case "class":
					Expression child = firstChild;
					int ws = getWhiteSpaceBefore(fullText, expression.getComponents().get(2).getStartIndex(), expression.getStartIndex());
					return new ClassInstanceExpression(parseType(child), ws);
				default:
					break;
				}
			}
			throw new IllegalStateException("Unhandled " + expression.getType() + " expression: " + expression);
		case "methodCall":
			BufferedName methodName = BufferedName.buffer(0, firstChild.getText(), getWhiteSpaceAt(fullText, firstChild.getEndIndex()));
			args = new ArrayList<>();
			for (Expression arg : expression.getComponents("expressionList", "expression")) {
				ObservableExpression argX = _parse(arg, fullText);
				args.add(BufferedExpression.buffer(getWhiteSpaceBefore(fullText, arg.getStartIndex(), 0), //
					argX, //
					getWhiteSpaceAt(fullText, arg.getStartIndex() + argX.getExpressionLength())));
			}
			return new MethodInvocation(null, methodName, null, args);
		case "integerLiteral":
			String text = expression.getText();
			char lastChar = text.charAt(text.length() - 1);
			boolean isLong = lastChar == 'l' || lastChar == 'L';
			if (isLong)
				text = text.substring(0, text.length() - 1);
			int radix;
			if (expression.getText().startsWith("0x")) {
				radix = 16;
				text = text.substring(2);
			} else if (expression.getText().startsWith("0b")) {
				radix = 2;
				text = text.substring(1);
			} else if (expression.getText().length() > 1 && expression.getText().startsWith("0")) {
				radix = 8;
				text = text.substring(1);
			} else
				radix = 10;
			if (isLong)
				return literalExpression(expression, Long.parseLong(text.replace("_", ""), radix));
			else
				return literalExpression(expression, Integer.parseInt(text.replace("_", ""), radix));
		case "floatLiteral":
			Expression type = expression.search().get("FloatingTypeSuffix").findAny();
			text = expression.toString();
			if (type != null)
				text = text.substring(0, text.length() - 1);
			if (type == null || type.toString().equalsIgnoreCase("d"))
				return literalExpression(expression, Double.parseDouble(text.replace("_", "")));
			else
				return literalExpression(expression, Float.parseFloat(text.replace("_", "")));
		case "BOOL_LITERAL":
			return literalExpression(expression, "true".equals(expression.toString()));
		case "CHAR_LITERAL":
			Expression escaped = expression.search().get("EscapeSequence").findAny();
			if (escaped == null)
				return literalExpression(expression, expression.toString().charAt(0));
			else
				return literalExpression(expression, evaluateEscape(escaped));
		case "STRING_LITERAL":
			Expression stringChars = expression.search().get("StringCharacters").findAny();
			String stringText;
			if (stringChars != null)
				stringText = compileString(stringChars.getComponents());
			else
				stringText = parseString(expression.getText().substring(1, expression.getText().length() - 1));
			return literalExpression(expression, stringText);
		case "EXTERNAL_LITERAL":
			Expression extChars = expression.search().get("StringCharacters").findAny();
			if (extChars != null) {
				String extText = compileString(extChars.getComponents());
				return new ExternalLiteral(extText);
			} else {
				String extText = parseString(expression.getText().substring(1, expression.getText().length() - 1));
				return new ExternalLiteral(extText);
			}
		case "NULL_LITERAL":
		case "'null'": // That's weird, but ok
			return literalExpression(expression, null);
		default:
			throw new IllegalStateException("Unrecognized expression type: " + expression.getType() + " " + expression);
		}
	}

	private ObservableExpression literalExpression(Expression expression, Object value) {
		return new ObservableExpression.LiteralExpression<>(expression.toString(), value);
	}

	private static char evaluateEscape(Expression escaped) {
		Expression check = escaped.search().get("OctalEscape").findAny();
		if (check != null)
			return (char) Integer.parseInt(check.toString().substring(1), 8);
		check = escaped.search().get("UnicodeEscape").findAny();
		if (check != null)
			return (char) Integer.parseInt(check.toString().substring(2), 16);
		int codeChar = escaped.toString().charAt(1);
		switch (codeChar) {
		case 'n':
			return '\n';
		case 't':
			return '\t';
		case '\\':
			return '\\';
		case 'r':
			return '\r';
		case 'b':
			return '\b';
		case 'f':
			return '\f';
		default:
			throw new IllegalArgumentException("Unrecognized escape sequence: " + escaped);
		}
	}

	private static String compileString(List<Expression> contents) {
		StringBuilder str = new StringBuilder(contents.size());
		for (Expression c : contents) {
			if (c.getComponents().isEmpty() || !"EscapeSequence".equals(c.getComponents().getFirst().getType()))
				str.append(c.toString());
			else
				str.append(evaluateEscape(c.getComponents().getFirst()));
		}
		return str.toString();
	}

	private static String parseString(String content) {
		StringBuilder str = null;
		boolean escape = false;
		int unicodeCh = 0;
		int unicodeLen = -1;
		for (int c = 0; c < content.length(); c++) {
			char ch = content.charAt(c);
			if (unicodeLen >= 0) {
				unicodeCh = (unicodeCh << 4) | StringUtils.hexDigit(ch);
				if (unicodeLen == 3) {
					str.append((char) unicodeCh);
					unicodeCh = 0;
					unicodeLen = -1;
				} else
					unicodeLen++;
			} else if (escape) {
				escape = false;
				switch (ch) {
				case 'n':
					str.append('\n');
					break;
				case 't':
					str.append('\t');
					break;
				case 'r':
					str.append('\r');
					break;
				case '\\':
					str.append('\\');
					break;
				case '`':
				case '\'':
				case '"':
					str.append(ch);
					break;
				case 'u':
					unicodeLen = 0;
					break;
				case 'b':
					str.append('\b');
					break;
				case 'f':
					str.append('\f');
					break;
				default:
					throw new IllegalStateException("Unrecognized escaped character: \\" + ch);
				}
			} else if (ch == '\\') {
				if (str == null)
					str = new StringBuilder().append(content, 0, c);
				escape = true;
			} else if (str != null)
				str.append(ch);
		}
		return str == null ? content : str.toString();
	}

	private static BufferedType parseType(Expression expression) throws ExpressoParseException {
		BetterList<Expression> typeType = expression.getComponents("typeType");
		if (typeType.isEmpty())
			typeType = expression.getComponents("pattern", "typeType");
		if (typeType.isEmpty())
			throw new ExpressoParseException(expression, "Unrecognized type expression");
		else if (typeType.size() > 1)
			throw new ExpressoParseException(expression, "Unsupported multi-type expression");
		Expression typeName = typeType.getFirst().getComponent("classOrInterfaceType");
		if (typeName == null)
			typeName = typeType.getFirst().getComponent("primitiveType");
		if (typeName == null)
			throw new ExpressoParseException(expression, "Unrecognized type expression");
		int arrayDim = 0;
		for (Expression child : typeType.getFirst().getComponents()) {
			if ("[".equals(child.getText()))
				arrayDim++;
		}
		if (arrayDim == 0)
			return BufferedType.parse(typeName.getText());
		StringBuilder type = new StringBuilder(typeName.getText());
		for (int i = 0; i < arrayDim; i++)
			type.append("[]");
		return BufferedType.parse(type.toString());
	}

	private static int getWhiteSpaceAt(String text, int index) {
		int end = index;
		while (end < text.length() && Character.isWhitespace(text.charAt(end)))
			end++;
		return end - index;
	}

	private static int getWhiteSpaceBefore(String text, int index, int limit) {
		int start = index;
		while (start > limit && Character.isWhitespace(text.charAt(start - 1)))
			start--;
		return index - start;
	}
}
