package org.observe.expresso;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.observe.expresso.ops.ArrayAccessExpression;
import org.observe.expresso.ops.AssignmentExpression;
import org.observe.expresso.ops.BinaryOperator;
import org.observe.expresso.ops.CastExpression;
import org.observe.expresso.ops.ClassInstanceExpression;
import org.observe.expresso.ops.ConditionalExpression;
import org.observe.expresso.ops.ConstructorInvocation;
import org.observe.expresso.ops.ExternalLiteral;
import org.observe.expresso.ops.InstanceofExpression;
import org.observe.expresso.ops.MethodInvocation;
import org.observe.expresso.ops.NameExpression;
import org.observe.expresso.ops.ParentheticExpression;
import org.observe.expresso.ops.UnaryOperator;
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
			public void write(int b) throws IOException {
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
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
		return _parse(parsed);
	}

	private ObservableExpression _parse(Expression expression) throws ExpressoParseException {
		ObservableExpression result;
		try {
			result = parse(expression);
		} catch (RuntimeException e) {
			throw new ExpressoParseException(expression, "Expression parsing failed", e);
		}
		return result;
	}

	/**
	 * @param expression The expression, pre-parsed with ANTLR, to interpret
	 * @return The {@link ObservableExpression} represented by the expression
	 * @throws ExpressoParseException If the expression cannot be interpreted
	 */
	protected ObservableExpression parse(Expression expression) throws ExpressoParseException {
		List<String> typeArgs;
		List<ObservableExpression> args;
		switch (expression.getType()) {
		case "expressionFull":
			return _parse(expression.getComponents().getFirst());
		case "expression":
			switch (expression.getComponents().size()) {
			case 1:
				return _parse(expression.getComponents().getFirst());
			case 2:
				switch (expression.getComponents().getFirst().getText()) {
				case "!":
				case "+":
				case "-":
				case "~":
				case "++":
				case "--":
					ObservableExpression operand = _parse(expression.getComponents().getLast());
					return new UnaryOperator(expression.getComponents().getFirst().getText(), operand, true,
						expression.getComponents().getLast().getStartIndex() - expression.getComponents().getFirst().getEndIndex());
				case "new":
					Expression creator = expression.getComponents().get(1);
					if (creator.getComponent("nonWildcardTypeArguments") != null)
						throw new ExpressoParseException(creator, "Constructor invocation type parameters are not supported");
					else if (creator.getComponent("classCreatorRest", "classBody") != null)
						throw new ExpressoParseException(creator, "Anonymous inner classes are not supported");
					else {
						StringBuilder typeName = new StringBuilder();
						int typeOffset = -1;
						typeArgs = null;
						for (Expression ch : creator.getComponent("createdName").getComponents()) {
							if ("typeArgumentsOrDiamond".equals(ch.getType())) {
								typeArgs = new ArrayList<>();
								for (Expression t : ch.getComponents("typeArguments", "typeArgument"))
									typeArgs.add(t.getText());
							} else if (typeArgs != null)
								throw new ExpressoParseException(expression, "Non-static member constructors are not supported yet");
							else {
								if (typeOffset < 0)
									typeOffset = ch.getStartIndex();
								typeName.append(ch.getText());
							}
						}
						if (creator.getComponent("arrayCreatorRest") != null) {
							throw new ExpressoParseException(expression, "Array creation is not yet implemented"); // TODO
						} else {
							args = new ArrayList<>();
							for (Expression arg : creator.getComponents("classCreatorRest", "arguments", "expressionList", "expression")) {
								args.add(_parse(arg));
							}
							return new ConstructorInvocation(typeName.toString(), typeArgs, args, expression.getStartIndex(),
								expression.getEndIndex(), typeOffset);
						}
					}
				}
				switch (expression.getComponents().getLast().getText()) {
				case "++":
				case "--":
					ObservableExpression operand = _parse(expression.getComponents().getFirst());
					return new UnaryOperator(expression.getComponents().getLast().getText(), operand, false,
						expression.getComponents().getLast().getStartIndex() - expression.getComponents().getFirst().getEndIndex());
				}
				throw new IllegalStateException("Unhandled expression type: " + expression.getType() + " " + expression);
			default:
				if ("(".equals(expression.getComponents().getFirst().getText())) {
					String type = parseType(expression);
					ObservableExpression value = _parse(expression.getComponents().getLast());
					return new CastExpression(value, type, expression.getStartIndex());
				}
				switch (expression.getComponents().get(1).getText()) {
				case ".":
					ObservableExpression context = _parse(expression.getComponents().getFirst());
					Expression child = expression.getComponents().getLast();
					switch (child.getType()) {
					case "identifier":
					case "THIS":
					case "SUPER":
						if (context instanceof NameExpression) {
							List<String> names = new ArrayList<>(((NameExpression) context).getNames());
							int[] nameOffsets = new int[names.size() + 1];
							System.arraycopy(((NameExpression) context).getNameOffsets(), 0, nameOffsets, 0, names.size());
							nameOffsets[names.size()] = child.getStartIndex();
							names.add(child.getText());
							return new NameExpression(null, BetterList.of(names), nameOffsets);
						} else
							return new NameExpression(context, BetterList.of(child.getText()), new int[] { child.getStartIndex() });
					case "methodCall":
						String methodName = child.getComponents().getFirst().getText();
						args = new ArrayList<>();
						for (Expression arg : child.getComponents("expressionList", "expression"))
							args.add(_parse(arg));
						return new MethodInvocation(context, methodName, null, args, expression.getStartIndex(), expression.getEndIndex(),
							expression.getStartIndex());
					case "NEW":
						throw new ExpressoParseException(expression, "Non-static member constructors are not supported yet");
					case "explicitGenericInvocation":
						throw new ExpressoParseException(expression, expression.getType() + " expressions are not supported yet");
					}
					break;
				case "=":
					context = _parse(expression.getComponents().getFirst());
					if (!(context instanceof NameExpression))
						throw new ExpressoParseException(expression,
							"Expression of type " + context.getClass().getName() + " cannot be assigned a value");
					ObservableExpression value = _parse(expression.getComponents().getLast());
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
					ObservableExpression left = _parse(expression.getComponents().getFirst());
					ObservableExpression right = _parse(expression.getComponents().getLast());
					return new BinaryOperator(expression.getComponents().get(1).getText(),
						expression.getComponents().get(1).getStartIndex(), left, right);
				case "instanceof":
					left = _parse(expression.getComponents().getFirst());
					return new InstanceofExpression(left, parseType(expression.getComponents().getLast()),
						expression.getComponents().getLast().getStartIndex());
				case "?":
					ObservableExpression condition = _parse(expression.getComponents().getFirst());
					ObservableExpression primary = _parse(expression.getComponents().get(2));
					ObservableExpression secondary = _parse(expression.getComponents().getLast());
					return new ConditionalExpression(condition, primary, secondary);
				case "[":
					ObservableExpression array = _parse(expression.getComponents().getFirst());
					ObservableExpression index = _parse(expression.getComponents().get(2));
					return new ArrayAccessExpression(array, index, expression.getEndIndex());
				case "::":
					throw new ExpressoParseException(expression, "Method references are not supported");
					/*context = _parse(expression.getComponents().getFirst());
					if (expression.getComponent("typeArguments") != null) {
						List<Expression> typeArgExprs = expression.getComponents("typeArguments", "typeArgumentList", "typeArgument");
						typeArgs = new ArrayList<>(typeArgExprs.size());
						for (Expression tae : typeArgExprs) {
							for (Expression taeChild : tae.getComponents())
								typeArgs.add(taeChild.getText());
						}
					} else
						typeArgs = null;
					return new MethodReferenceExpression(context, expression.getComponents().getLast().toString(), typeArgs);*/
				}
			}
			// TODO
			throw new IllegalStateException("Unhandled expression type: " + expression.getType() + " " + expression);
		case "primary":
			switch (expression.getComponents().size()) {
			case 1:
				Expression child = expression.getComponents().getFirst();
				switch (child.getText()) {
				case "this":
				case "super":
					return new NameExpression(null, BetterList.of(child.getText()), new int[] { child.getStartIndex() });
				}
				switch (child.getType()) {
				case "literal":
					return _parse(child.getComponents().getFirst());
				case "identifier":
					return new NameExpression(null, BetterList.of(child.getText()), new int[] { child.getStartIndex() });
				}
				break;
			case 3:
				switch (expression.getComponents().get(2).getText()) {
				case ")":
					return new ParentheticExpression(_parse(expression.getComponents().get(1)), expression.getStartIndex(),
						expression.getEndIndex());
				case "class":
					child = expression.getComponents().getFirst();
					return new ClassInstanceExpression(parseType(child), expression.getStartIndex(), expression.getEndIndex());
				}
				break;
			}
			throw new IllegalStateException("Unhandled " + expression.getType() + " expression: " + expression);
		case "methodCall":
			String methodName = expression.getComponents().getFirst().getText();
			args = new ArrayList<>();
			for (Expression arg : expression.getComponents("expressionList", "expression"))
				args.add(_parse(arg));
			return new MethodInvocation(null, methodName, null, args, expression.getStartIndex(), expression.getEndIndex(),
				expression.getStartIndex());
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
				return literalExpression(expression, Long.parseLong(text.replaceAll("_", ""), radix));
			else
				return literalExpression(expression, Integer.parseInt(text.replaceAll("_", ""), radix));
		case "floatLiteral":
			Expression type = expression.search().get("FloatingTypeSuffix").findAny();
			text = expression.toString();
			if (type != null)
				text = text.substring(0, text.length() - 1);
			if (type == null || type.toString().equalsIgnoreCase("d"))
				return literalExpression(expression, Double.parseDouble(text.replaceAll("_", "")));
			else
				return literalExpression(expression, Float.parseFloat(text.replaceAll("_", "")));
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
				return new ExternalLiteral(expression, extText, extChars.getStartIndex(), extChars.getEndIndex());
			} else {
				String extText = parseString(expression.getText().substring(1, expression.getText().length() - 1));
				return new ExternalLiteral(expression, extText, 1, expression.getText().length() - 1);
			}
		case "NULL_LITERAL":
		case "'null'": // That's weird, but ok
			return literalExpression(expression, null);
		default:
			throw new IllegalStateException("Unrecognized expression type: " + expression.getType() + " " + expression);
		}
	}

	private ObservableExpression literalExpression(Expression expression, Object value) {
		return new ObservableExpression.LiteralExpression<>(expression, value);
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
		}
		throw new IllegalArgumentException("Unrecognized escape sequence: " + escaped);
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

	private static String parseType(Expression expression) throws ExpressoParseException {
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
			return typeName.getText();
		StringBuilder type = new StringBuilder(typeName.getText());
		for (int i = 0; i < arrayDim; i++)
			type.append("[]");
		return type.toString();
	}
}
