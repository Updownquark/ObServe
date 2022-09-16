package org.observe.expresso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.observe.expresso.Expression.ExpressoParseException;
import org.observe.expresso.ops.ArrayAccessExpression;
import org.observe.expresso.ops.AssignmentExpression;
import org.observe.expresso.ops.BinaryOperator;
import org.observe.expresso.ops.CastExpression;
import org.observe.expresso.ops.ClassInstanceExpression;
import org.observe.expresso.ops.ConditionalExpression;
import org.observe.expresso.ops.ConstructorInvocation;
import org.observe.expresso.ops.ExternalLiteral;
import org.observe.expresso.ops.InstanceofExpression;
import org.observe.expresso.ops.LambdaExpression;
import org.observe.expresso.ops.MethodInvocation;
import org.observe.expresso.ops.MethodReferenceExpression;
import org.observe.expresso.ops.NameExpression;
import org.observe.expresso.ops.UnaryOperator;
import org.qommons.StringUtils;
import org.qommons.collect.BetterList;
import org.qommons.tree.BetterTreeList;

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
		ExpressoAntlrLexer lexer = new ExpressoAntlrLexer(CharStreams.fromString(text));
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		ExpressoAntlrParser parser = new ExpressoAntlrParser(tokens);
		ParseTree result = parser.expressionFull();
		Expression parsed = Expression.of(parser, result);
		return _parse(parsed);
	}

	private ObservableExpression _parse(Expression expression) throws ExpressoParseException {
		ObservableExpression result;
		try {
			result = parse(expression);
		} catch (RuntimeException e) {
			e.printStackTrace();
			throw new ExpressoParseException(expression, "Expression parsing failed: " + e);
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
					return new UnaryOperator(expression.getComponents().getFirst().getText(), operand, true);
				case "new":
					Expression creator = expression.getComponents().get(1);
					if (creator.getComponent("nonWildcardTypeArguments") != null)
						throw new ExpressoParseException(creator, "Constructor invocation type parameters are not supported");
					else if (creator.getComponent("classCreatorRest", "classBody") != null)
						throw new ExpressoParseException(creator, "Anonymous inner classes are not supported");
					else {
						StringBuilder typeName = new StringBuilder();
						typeArgs = null;
						for (Expression ch : creator.getComponent("createdName").getComponents()) {
							if ("typeArgumentsOrDiamond".equals(ch.getType())) {
								typeArgs = new ArrayList<>();
								for (Expression t : ch.getComponents("typeArguments", "typeArgument"))
									typeArgs.add(t.getText());
							} else if (typeArgs != null)
								throw new ExpressoParseException(expression, "Non-static member constructors are not supported yet");
							else {
								if (typeName.length() > 0)
									typeName.append('.');
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
							return new ConstructorInvocation(typeName.toString(), typeArgs, args);
						}
					}
				}
				switch (expression.getComponents().getLast().getText()) {
				case "++":
				case "--":
					ObservableExpression operand = _parse(expression.getComponents().getFirst());
					return new UnaryOperator(expression.getComponents().getLast().getText(), operand, false);
				}
				throw new IllegalStateException("Unhandled expression type: " + expression.getType() + " " + expression);
			default:
				if ("(".equals(expression.getComponents().getFirst().getText())) {
					String type = parseType(expression);
					ObservableExpression value = _parse(expression.getComponents().getLast());
					return new CastExpression(value, type);
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
							names.add(child.getText());
							return new NameExpression(null, BetterList.of(names));
						} else
							return new NameExpression(context, BetterList.of(child.getText()));
					case "methodCall":
						String methodName = child.getComponents().getFirst().getText();
						args = new ArrayList<>();
						for (Expression arg : child.getComponents("expressionList", "expression"))
							args.add(_parse(arg));
						return new MethodInvocation(context, methodName, null, args);
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
					ObservableExpression value = _parse(expression.getComponents().getFirst());
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
					return new BinaryOperator(expression.getComponents().get(1).getText(), left, right);
				case "instanceof":
					left = _parse(expression.getComponents().getFirst());
					return new InstanceofExpression(left, parseType(expression.getComponents().getLast()));
				case "?":
					ObservableExpression condition = _parse(expression.getComponents().getFirst());
					ObservableExpression primary = _parse(expression.getComponents().get(2));
					ObservableExpression secondary = _parse(expression.getComponents().getLast());
					return new ConditionalExpression(condition, primary, secondary);
				case "[":
					ObservableExpression array = _parse(expression.getComponents().getFirst());
					ObservableExpression index = _parse(expression.getComponents().get(2));
					return new ArrayAccessExpression(array, index);
				case "::":
					context = _parse(expression.getComponents().getFirst());
					if (expression.getComponent("typeArguments") != null) {
						List<Expression> typeArgExprs = expression.getComponents("typeArguments", "typeArgumentList", "typeArgument");
						typeArgs = new ArrayList<>(typeArgExprs.size());
						for (Expression tae : typeArgExprs) {
							for (Expression taeChild : tae.getComponents())
								typeArgs.add(taeChild.getText());
						}
					} else
						typeArgs = null;
					return new MethodReferenceExpression(context, expression.getComponents().getLast().toString(), typeArgs);
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
					return new NameExpression(null, BetterList.of(child.getText()));
				}
				switch (child.getType()) {
				case "literal":
					return _parse(child.getComponents().getFirst());
				case "identifier":
					return new NameExpression(null, BetterList.of(child.getText()));
				}
				break;
			case 3:
				switch (expression.getComponents().get(2).getText()) {
				case ")":
					return _parse(expression.getComponents().get(1));
				case "class":
					child = expression.getComponents().getFirst();
					return new ClassInstanceExpression(parseType(child));
				}
				break;
			}
			throw new IllegalStateException("Unhandled " + expression.getType() + " expression: " + expression);
		case "methodCall":
			String methodName = expression.getComponents().getFirst().getText();
			args = new ArrayList<>();
			for (Expression arg : expression.getComponents("expressionList", "expression"))
				args.add(_parse(arg));
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
				return literalExpression(expression, Long.parseLong(text, radix));
			else
				return literalExpression(expression, Integer.parseInt(text, radix));
		case "floatLiteral":
			Expression type = expression.search().get("FloatingTypeSuffix").findAny();
			text = expression.toString();
			if (type != null)
				text = text.substring(0, text.length() - 1);
			if (type == null || type.toString().equalsIgnoreCase("d"))
				return literalExpression(expression, Double.parseDouble(text));
			else
				return literalExpression(expression, Float.parseFloat(text));
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
			String extText;
			if (extChars != null)
				extText = compileString(extChars.getComponents());
			else
				extText = parseString(expression.getText().substring(1, expression.getText().length() - 1));
			return new ExternalLiteral(expression, extText);
		case "NULL_LITERAL":
		case "'null'": // That's weird, but ok
			return literalExpression(expression, null);
		default:
			throw new IllegalStateException("Unrecognized expression type: " + expression.getType() + " " + expression);
		}
	}

	/**
	 * This method was implemented targeting the Java8 ANTLR grammar. I upgraded to the Java grammar. The Java grammar supports through Java
	 * version 15 (which I don't really care about) and is much faster. However, it is structured much differently than Java8, so a complete
	 * re-implementation was necessary. I'm leaving this here because I may not have implemented interpretation of some types of expressions
	 * the new way, so this code may be helpful as a reference until the new parsing does everything the old parsing did.
	 */
	@SuppressWarnings("unused")
	private ObservableExpression parseOld(Expression expression) throws ExpressoParseException {
		boolean checkPassThrough = true;
		while (checkPassThrough) {
			checkPassThrough = false;
			switch (expression.getType()) {
			// Some expressions may or may not be pass-through
			case "primary":
			case "primaryNoNewArray":
			case "primaryNoNewArray_lfno_arrayAccess":
			case "primaryNoNewArray_lfno_primary":
			case "primaryNoNewArray_lfno_primary_lfno_arrayAccess_lfno_primary":
			case "conditionalExpression":
			case "conditionalOrExpression":
			case "conditionalAndExpression":
			case "inclusiveOrExpression":
			case "exclusiveOrExpression":
			case "andExpression":
			case "equalityExpression":
			case "relationalExpression":
			case "shiftExpression":
			case "additiveExpression":
			case "multiplicativeExpression":
			case "unaryExpression":
			case "unaryExpressionNotPlusMinus":
			case "postfixExpression":
				if (expression.getComponents().size() != 1)
					break;
				// Pass-through types
				// $FALL-THROUGH$
			case "literal":
			case "localVariableDeclarationStatement":
			case "statement":
			case "statementWithoutTrailingSubstatement":
			case "statementExpression":
			case "statementNoShortIf":
			case "expressionFull":
			case "expression":
			case "assignmentExpression":
			case "constantExpression":
			case "forStatement":
			case "forStatementNoShortIf":
			case "variableInitializer":
				expression = expression.getComponents().getFirst();
				checkPassThrough = true;
				break;
			}
		}

		switch (expression.getType()) {
		// Statement structures
		case "block":
		case "emptyStatement":
		case "synchronizedStatement":
		case "tryStatement":
		case "tryWithResourcesStatement":
		case "labeledStatement":
		case "labeledStatementNoShortIf":
		case "breakStatement":
		case "continueStatement":
		case "returnStatement":
		case "throwStatement":
		case "assertStatement":
		case "ifThenStatement":
		case "ifThenElseStatement":
		case "ifThenElseStatementNoShortIf":
		case "switchStatement":
		case "doStatement":
		case "whileStatement":
		case "whileStatementNoShortIf":
		case "basicForStatement":
		case "basicForStatementNoShortIf":
		case "enhancedForStatement":
		case "enhancedForStatementNoShortIf":
		case "expressionStatement":
		case "statementExpressionList":
		case "HexadecimalFloatingPointLIteral":
		case "classDeclaration":
			// Fields, methods, and constructors
		case "localVariableDeclaration":
			throw new ExpressoParseException(expression, "Expression type '" + expression.getType() + "' not supported");
			// Literals
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
				return literalExpression(expression, Long.parseLong(text, radix));
			else
				return literalExpression(expression, Integer.parseInt(text, radix));
		case "floatingLiteral":
		case "DecimalFloatingPointLiteral":
			Expression type = expression.search().get("FloatingTypeSuffix").findAny();
			text = expression.toString();
			if (type != null)
				text = text.substring(0, text.length() - 1);
			if (type == null || type.toString().equalsIgnoreCase("d"))
				return literalExpression(expression, Double.parseDouble(text));
			else
				return literalExpression(expression, Float.parseFloat(text));
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
			String extText;
			if (extChars != null)
				extText = compileString(extChars.getComponents());
			else
				extText = parseString(expression.getText().substring(1, expression.getText().length() - 1));
			return new ExternalLiteral(expression, extText);
		case "NULL_LITERAL":
		case "'null'": // Really? Very strange type name
			return literalExpression(expression, null);
		case "arrayAccess":
		case "arrayAccess_lfno_primary":
			// context = evaluate(expression.getComponents().getFirst(), env, OBJECT);
			// List<Expression> dimExps = expression.getComponents("expression");
			// // Should I open this up to allow other index types besides int?
			// List<TypedStatement<E, ? extends Integer>> indexes = new ArrayList<>(dimExps.size());
			// for (Expression dimExp : dimExps) {
			// indexes.add(//
			// evaluate(dimExp, env, context, indexes);
			// }
			// return arrayAccess(expression, targetType, context, indexes);
			throw new ExpressoParseException(expression, "Expression type '" + expression.getType() + "' not implemented yet");
		case "methodInvocation":
		case "methodInvocation_lfno_primary":
			ObservableExpression context;
			Expression target = expression.getComponents().getFirst();
			String methodName;
			if ("methodName".equals(target.getType())) {
				context = null;
				methodName = target.toString();
			} else {
				methodName = expression.getComponent("Identifier").toString();
				if (target.getComponents().isEmpty() && "super".equals(target.toString()))
					throw new ExpressoParseException(target, "'super' is not allowed");
				else if ("super".equals(expression.getComponents().get(1).toString()))
					throw new ExpressoParseException(expression.getComponents().get(1), "'super' is not allowed");
				else // T O D O Failed to account for static method invocation (with type context) here
					context = parseOld(target);
			}
			List<String> typeArgs;
			if (expression.getComponent("typeArguments") != null) {
				List<Expression> typeArgExprs = expression.getComponents("typeArguments", "typeArgumentList", "typeArgument");
				typeArgs = new ArrayList<>(typeArgExprs.size());
				for (Expression tae : typeArgExprs) {
					for (Expression taeChild : tae.getComponents())
						typeArgs.add(taeChild.getText());
				}
			} else
				typeArgs = null;
			List<ObservableExpression> args;
			if (expression.getComponent("argumentList") != null) {
				List<Expression> argExprs = expression.getComponents("argumentList", "expression");
				args = new ArrayList<>(argExprs.size());
				for (Expression arg : argExprs)
					args.add(_parse(arg));
			} else
				args = null;
			return new MethodInvocation(context, methodName, typeArgs, args);
		case "classInstanceCreationExpression":
		case "classInstanceCreationExpression_lfno_primary":
			// target = expression.getComponents().getFirst();
			// List<String> typeToCreate;
			// if (target.getComponents().isEmpty() && "new".equals(target.toString())) {
			// context = null;
			// typeToCreate = expression.getComponents("Identifier").stream().map(ex -> ex.toString()).collect(Collectors.toList());
			// } else if ("expressionName".equals(target.getType())) {
			// BetterList<String> qualification = extractNameSequence(target, new BetterTreeList<>(false));
			// context = evaluateExpression(expression, qualification, env, OBJECT, true);
			// typeToCreate = Collections.singletonList(expression.getComponent("Identifier").toString());
			// } else {
			// context = evaluate(target, env, OBJECT);
			// typeToCreate = Collections.singletonList(expression.getComponent("Identifier").toString());
			// }
			// if (expression.getComponent("typeArguments") != null) {
			// List<Expression> typeArgExprs = expression.getComponents("typeArguments", "typeArgumentList", "typeArgument");
			// typeArgs = new ArrayList<>(typeArgExprs.size());
			// for (Expression tae : typeArgExprs) {
			// for (Expression taeChild : tae.getComponents())
			// typeArgs.add(//
			// evaluateType(taeChild, env, OBJECT));
			// }
			// } else
			// typeArgs = null;
			// List<TypeToken<?>> constructorTypeArgs;
			// if (expression.getComponent("typeArgumentsOrDiamond") != null) {
			// List<Expression> typeArgExprs = expression.getComponents("typeArgumentsOrDiamond", "typeArguments", "typeArgumentList",
			// "typeArgument");
			// constructorTypeArgs = new ArrayList<>(typeArgExprs.size());
			// for (Expression tae : typeArgExprs) {
			// for (Expression taeChild : tae.getComponents())
			// constructorTypeArgs.add(//
			// evaluateType(taeChild, env, OBJECT));
			// }
			// } else
			// constructorTypeArgs = null;
			// if (expression.getComponent("classBody") != null)
			// throw new CompilationException(expression, "Anonymous inner types not supported");
			// return constructorInvocation(expression, context, typeToCreate, typeArgs, constructorTypeArgs,
			// expression.getComponents("argumentList", "expression"), env);
			throw new ExpressoParseException(expression, "Expression type '" + expression.getType() + "' not implemented yet");
		case "lambdaExpression":
			Expression body = expression.getComponent("lambdaBody").getComponents().getFirst();
			if (!body.getType().equals("expression"))
				throw new ExpressoParseException(body, "Lambda expressions with block bodies are not supported");
			if (expression.getComponent("lambdaParameters", "formalParameterList") != null)
				throw new ExpressoParseException(body, "Lambda expressions with formal (typed) parameters are not supported");
			List<String> parameters;
			Expression params = expression.getComponent("lambdaParameters");
			if (params.getComponents().size() == 1)
				parameters = Collections.singletonList(params.getComponents().getFirst().getText());
			else {
				BetterList<Expression> paramExprs = params.getComponents("inferredParameterList", "Identifier");
				parameters = new ArrayList<>(paramExprs.size());
				for (int i = 0; i < paramExprs.size(); i++)
					parameters.add(paramExprs.get(i).getText());
				parameters = Collections.unmodifiableList(parameters);
			}
			return new LambdaExpression(parameters, //
				_parse(expression.getComponent("lambdaBody").getComponents().getFirst()));
		case "methodReference":
		case "methodReference_lfno_primary":
			target = expression.getComponents().getFirst();
			if (target.getComponents().isEmpty() && "super".equals(target.toString()))
				throw new ExpressoParseException(target, "'super' is not allowed");
			context = parseOld(target);
			if (expression.getComponent("typeArguments") != null) {
				List<Expression> typeArgExprs = expression.getComponents("typeArguments", "typeArgumentList", "typeArgument");
				typeArgs = new ArrayList<>(typeArgExprs.size());
				for (Expression tae : typeArgExprs) {
					for (Expression taeChild : tae.getComponents())
						typeArgs.add(taeChild.getText());
				}
			} else
				typeArgs = null;
			return new MethodReferenceExpression(context, expression.getComponents().getLast().toString(), typeArgs);
		case "this":
		case "fieldAccess":
		case "fieldAccess_lfno_primary":
		case "typeName":
		case "ambiguousName":
		case "expressionName":
		case "identifier":
			// Other names?
			BetterList<String> nameSequence = extractNameSequence(expression, //
				BetterTreeList.<String> build().build());
			return new NameExpression(null, nameSequence);
			// Expression types
		case "assignment":
			// TypedStatement<E, ? extends X> leftHand = evaluate(//
			// expression.getComponents().getFirst().getComponents().getFirst(), env);
			// TypedStatement<E, ? extends X> rightHand = evaluate(//
			// expression.getComponents().getLast(), env);
			// return assign(expression, leftHand, rightHand, expression.getComponents().get(1).toString());
		case "castExpression":
			// return cast(expression, //
			// evaluate(expression.getComponents().getLast(), env, OBJECT), //
			// evaluateType(expression.getComponents().get(1), env));
			throw new ExpressoParseException(expression, "Expression type '" + expression.getType() + "' not implemented yet");
		case "primary":
			// TypedStatement<E, ?> expr = evaluate(expression.getComponents().getFirst(), env, OBJECT);
			// for (int i = 1; i < expression.getComponents().size(); i++) {
			// if (i == expression.getComponents().size() - 1)
			// expr = modifyPrimary(expression, expr, expression.getComponents().get(i), env);
			// else
			// expr = modifyPrimary(expression, expr, expression.getComponents().get(i), env, OBJECT);
			// }
			// return (TypedStatement<E, ? extends X>) expr;
			ObservableExpression base = null;
			for (Expression ex : expression.getComponents()) {
				if (base == null) {
					base = _parse(expression.getComponents().getFirst());
					continue;
				}
				ex = ex.getComponents().getFirst();
				switch (ex.getType()) {
				case "classInstanceCreationExpression_lf_primary":
					throw new ExpressoParseException(expression, "Expression type '" + ex.getType() + "' not implemented yet");
				case "fieldAccess_lf_primary":
					base = new NameExpression(base, BetterList.of(ex.getComponents().getLast().getText()));
					break;
				case "arrayAccess_lf_primary":
					throw new ExpressoParseException(expression, "Expression type '" + ex.getType() + "' not implemented yet");
				case "methodInvocation_lf_primary":
					if (ex.getComponent("typeArguments") != null) {
						List<Expression> typeArgExprs = ex.getComponents("typeArguments", "typeArgumentList", "typeArgument");
						typeArgs = new ArrayList<>(typeArgExprs.size());
						for (Expression tae : typeArgExprs) {
							for (Expression taeChild : tae.getComponents())
								typeArgs.add(taeChild.getText());
						}
					} else
						typeArgs = null;
					if (ex.getComponent("argumentList") != null) {
						List<Expression> argExprs = ex.getComponents("argumentList", "expression");
						args = new ArrayList<>(argExprs.size());
						for (Expression arg : argExprs)
							args.add(_parse(arg));
					} else
						args = null;
					base = new MethodInvocation(base, ex.getComponent("Identifier").getText(), typeArgs, args);
					break;
				case "methodReference_lf_primary":
					if (ex.getComponent("typeArguments") != null) {
						List<Expression> typeArgExprs = ex.getComponents("typeArguments", "typeArgumentList", "typeArgument");
						typeArgs = new ArrayList<>(typeArgExprs.size());
						for (Expression tae : typeArgExprs) {
							for (Expression taeChild : tae.getComponents())
								typeArgs.add(taeChild.getText());
						}
					} else
						typeArgs = null;
					base = new MethodReferenceExpression(base, ex.getComponent("Identifier").getText(), typeArgs);
					break;
				default:
					throw new ExpressoParseException(expression, "Unrecognized primary operator: '" + ex.getType());
				}
			}
			return base;
		case "primaryNoNewArray":
		case "primaryNoNewArray_lfno_primary":
			if (expression.getComponents().size() == 3 && expression.getComponents().getFirst().getText().equals("(")) {
				// Parenthetical
				return _parse(expression.getComponents().get(1));
			}
			//$FALL-THROUGH$
		case "primaryNoNewArray_lfno_arrayAccess":
		case "primaryNoNewArray_lfno_primary_lfno_arrayAccess_lfno_primary":
			// String lastName = expression.getComponents().getLast().toString();
			// switch (lastName) {
			// case "class":
			// Class<?> raw = TypeTokens.getRawType(targetType);
			// if (!raw.isAssignableFrom(Class.class))
			// throw new CompilationException(expression, expression + " is not valid for type " + targetType);
			// TypeToken<?> classType;
			// if (Class.class.isAssignableFrom(raw)) {
			// classType = targetType.resolveType(Class.class.getTypeParameters()[0]);
			// int dimCount = expression.search().text("[").findAll().size();
			// for (int i = 0; i < dimCount; i++) {
			// if (!classType.isArray())
			// throw new CompilationException(expression, expression + " is not valid for type " + targetType);
			// classType = classType.getComponentType();
			// }
			// classType = evaluateType(expression.getComponents().getFirst(), env, classType);
			// classType = dimCount);
			// } else
			// classType = ? extends X>) typeExpression(expression, classType);
			// return getThis(expression, //
			// evaluateType(expression.getComponents().getFirst(), env, OBJECT));
			// case ")":
			// return evaluate(expression.getComponents().get(1), env);
			// default:
			// throw new CompilationException(expression, "Unrecognized primary expression");
			// }
		case "arrayCreationExpression":
			// dimExps = expression.getComponents("expression");
			// int dimCount = dimExps.size() + expression.search().get("dims").children().text("[").findAll().size();
			// TypeToken<?> arrayTargetType = targetType;
			// for (int i = 0; i < dimCount; i++)
			// arrayTargetType = arrayTargetType.getComponentType();
			// TypeToken<?> arrayType = evaluateType(expression.getComponents().get(1), env, arrayTargetType);
			// arrayType = dimCount);
			// if (arrayType))
			// throw new CompilationException(expression, "Array of type " + arrayType + " is not valid for type " + targetType);
			//// Should I open this up to allow other index types besides int?
			// indexes = new ArrayList<>(dimExps.size());
			// for (Expression dimExp : dimExps) {
			// indexes.add(//
			// evaluate(dimExp, env, "variableInitializerList", "variableInitializer");
			// List<TypedStatement<E, ?>> values = new ArrayList<>(initValueExps.size());
			// if (!initValueExps.isEmpty()) {
			// for (Expression ive : initValueExps)
			// values.add(evaluate(ive, env, componentType));
			// }
			// return arrayCreate(expression, (TypeToken<? extends X>) arrayType, indexes, values);
		case "arrayInitializer":
			// componentType = targetType.getComponentType();
			// initValueExps = expression.getComponents("variableInitializerList", "variableInitializer");
			// values = new ArrayList<>(initValueExps.size());
			// if (!initValueExps.isEmpty()) {
			// for (Expression ive : initValueExps)
			// values.add(evaluate(ive, env, componentType));
			// }
			// return arrayCreate(expression, Collections.emptyList(), values);
			// Ternary operation types
			throw new ExpressoParseException(expression, "Expression type '" + expression.getType() + "' not implemented yet");
		case "conditionalExpression":
			if (expression.getComponents().size() == 1)
				return _parse(expression.getComponents().getFirst());
			if (expression.getComponents().size() != 5)
				throw new ExpressoParseException(expression,
					"Unrecognized expression with " + expression.getComponents().size() + " components");
			ObservableExpression condition = _parse(expression.getComponents().getFirst());
			ObservableExpression primary = _parse(expression.getComponents().get(2));
			ObservableExpression secondary = _parse(expression.getComponents().getLast());
			return new ConditionalExpression(condition, primary, secondary);
			// Binary operation types
		case "conditionalOrExpression":
		case "conditionalAndExpression":
		case "inclusiveOrExpression":
		case "exclusiveOrExpression":
		case "andExpression":
		case "equalityExpression":
		case "relationalExpression":
		case "additiveExpression":
		case "multiplicativeExpression":
		case "shiftExpression":
			if (expression.getComponents().size() != 3)
				throw new ExpressoParseException(expression,
					"Unrecognized expression with " + expression.getComponents().size() + " components");
			String operator = expression.getComponents().get(1).toString();
			ObservableExpression left = _parse(expression.getComponents().getFirst());
			ObservableExpression right = _parse(expression.getComponents().getLast());
			return new BinaryOperator(operator, left, right);
			// Unary operation types
		case "'!'":
			operator = expression.getType().substring(1, expression.getType().length() - 1);
			left = _parse(expression.getComponents().getFirst());
			right = _parse(expression.getComponents().getLast());
			return new BinaryOperator(operator, left, right);
		case "unaryExpression":
		case "preIncrementExpression":
		case "preDecrementExpression":
		case "unaryExpressionNotPlusMinus":
			operator = expression.getComponents().getFirst().getText();
			ObservableExpression operand = _parse(expression.getComponents().getLast());
			return new UnaryOperator(operator, operand, true);
		case "postfixExpression":
		case "postIncrementExpression":
		case "postDecrementExpression":
			// if (expression.getComponents().size() != 2)
			// throw new CompilationException(expression,
			// "Unrecognized expression with " + expression.getComponents().size() + " components");
			// operator = expression.getComponents().getFirst().toString();
			// ex = null;
			// for (TypeToken<?> unaryTargetType : theOperations.getUnaryTargetTypes(targetType, operator, false)) {
			// try {
			// TypedStatement<E, ?> operand = evaluate(expression.getComponents().getLast(), env, unaryTargetType);
			// UnaryOperation<?, X> op = theOperations.unaryOperation(operand.getReturnType(), operator, false);
			// return unaryOperation(expression, (UnaryOperation<Object, X>) op, (TypedStatement<E, Object>) operand);
			// } catch (CompilationException e) {
			// if (ex == null)
			// ex = e;
			// }
			// }
			// throw ex;
			// Unsupported types
			throw new ExpressoParseException(expression, "Expression type '" + expression.getType() + "' not implemented yet");
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
					str.append('`');
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

	private static BetterList<String> extractNameSequence(Expression expression, BetterList<String> names) {
		if (expression.getType().equals("Identifier")) {
			names.add(expression.getText());
			return names;
		}
		for (Expression comp : expression.getComponents())
			extractNameSequence(comp, names);
		return names;
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