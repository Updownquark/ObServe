package org.observe.expresso;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.observe.Observable;
import org.observe.ObservableAction;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableSet;
import org.observe.expresso.Expression.ExpressoParseException;
import org.observe.expresso.ObservableExpression.Args;
import org.observe.util.ClassView;
import org.observe.util.ModelType.ModelInstanceType;
import org.observe.util.ModelType.ModelInstanceType.SingleTyped;
import org.observe.util.ModelTypes;
import org.observe.util.ObservableModelQonfigParser;
import org.observe.util.ObservableModelSet;
import org.observe.util.ObservableModelSet.ModelSetInstance;
import org.observe.util.ObservableModelSet.ModelValuePlaceholder;
import org.observe.util.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.observe.util.TypeTokens.TypeConverter;
import org.qommons.Identifiable;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigInterpreter.QonfigInterpretationException;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

public class DefaultExpressoParser implements ExpressoParser {
	@Override
	public ObservableExpression parse(String text) throws ExpressoParseException {
		if (text.trim().isEmpty())
			return ObservableExpression.EMPTY;
		Java8Lexer lexer = new Java8Lexer(CharStreams.fromString(text));
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		Java8Parser parser = new Java8Parser(tokens);
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

	protected ObservableExpression parse(Expression expression) throws ExpressoParseException {
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
		case "IntegerLiteral":
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
			} else if (expression.getText().startsWith("0")) {
				radix = 8;
				text = text.substring(2);
			} else
				radix = 10;
			if (isLong)
				return literalExpression(expression, Long.parseLong(text, radix));
			else
				return literalExpression(expression, Integer.parseInt(text, radix));
		case "FloatingPointLiteral":
		case "DecimalFloatingPointLiteral":
			Expression type = expression.search().get("FloatingTypeSuffix").findAny();
			text = expression.toString();
			if (type != null)
				text = text.substring(0, text.length() - 1);
			if (type == null || type.toString().equalsIgnoreCase("d"))
				return literalExpression(expression, Double.parseDouble(text));
			else
				return literalExpression(expression, Float.parseFloat(text));
		case "BooleanLiteral":
			return literalExpression(expression, "true".equals(expression.toString()));
		case "CharacterLiteral":
			Expression escaped = expression.search().get("EscapeSequence").findAny();
			if (escaped == null)
				return literalExpression(expression, expression.toString().charAt(0));
			else
				return literalExpression(expression, evaluateEscape(escaped));
		case "StringLiteral":
			return literalExpression(expression, compileString(expression.search().get("StringCharacters").find().getComponents()));
		case "NullLiteral":
		case "'null'": // Really? Very strange type name
			return literalExpression(expression, null);
		case "arrayAccess":
		case "arrayAccess_lfno_primary":
			//			context = evaluate(expression.getComponents().getFirst(), env, OBJECT);
			//			List<Expression> dimExps = expression.getComponents("expression");
			//			// Should I open this up to allow other index types besides int?
			//			List<TypedStatement<E, ? extends Integer>> indexes = new ArrayList<>(dimExps.size());
			//			for (Expression dimExp : dimExps) {
			//				indexes.add(//
			//					evaluate(dimExp, env, context, indexes);
			//		}
			//			return arrayAccess(expression, targetType, context, indexes);
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
				else // TODO Failed to account for static method invocation (with type context) here
					context = parse(target);
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
			return new MethodExpression(context, methodName, typeArgs, args);
		case "classInstanceCreationExpression":
		case "classInstanceCreationExpression_lfno_primary":
			//					target = expression.getComponents().getFirst();
			//					List<String> typeToCreate;
			//					if (target.getComponents().isEmpty() && "new".equals(target.toString())) {
			//						context = null;
			//						typeToCreate = expression.getComponents("Identifier").stream().map(ex -> ex.toString()).collect(Collectors.toList());
			//					} else if ("expressionName".equals(target.getType())) {
			//						BetterList<String> qualification = extractNameSequence(target, new BetterTreeList<>(false));
			//						context = evaluateExpression(expression, qualification, env, OBJECT, true);
			//						typeToCreate = Collections.singletonList(expression.getComponent("Identifier").toString());
			//					} else {
			//						context = evaluate(target, env, OBJECT);
			//						typeToCreate = Collections.singletonList(expression.getComponent("Identifier").toString());
			//					}
			//					if (expression.getComponent("typeArguments") != null) {
			//						List<Expression> typeArgExprs = expression.getComponents("typeArguments", "typeArgumentList", "typeArgument");
			//						typeArgs = new ArrayList<>(typeArgExprs.size());
			//						for (Expression tae : typeArgExprs) {
			//							for (Expression taeChild : tae.getComponents())
			//								typeArgs.add(//
			//									evaluateType(taeChild, env, OBJECT));
			//						}
			//					} else
			//						typeArgs = null;
			//					List<TypeToken<?>> constructorTypeArgs;
			//					if (expression.getComponent("typeArgumentsOrDiamond") != null) {
			//						List<Expression> typeArgExprs = expression.getComponents("typeArgumentsOrDiamond", "typeArguments", "typeArgumentList",
			//							"typeArgument");
			//						constructorTypeArgs = new ArrayList<>(typeArgExprs.size());
			//						for (Expression tae : typeArgExprs) {
			//							for (Expression taeChild : tae.getComponents())
			//								constructorTypeArgs.add(//
			//									evaluateType(taeChild, env, OBJECT));
			//						}
			//					} else
			//						constructorTypeArgs = null;
			//					if (expression.getComponent("classBody") != null)
			//						throw new CompilationException(expression, "Anonymous inner types not supported");
			//					return constructorInvocation(expression, context, typeToCreate, typeArgs, constructorTypeArgs,
			//						expression.getComponents("argumentList", "expression"), env);
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
			context = parse(target);
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
			// Other names?
			BetterList<String> nameSequence = extractNameSequence(expression, //
				BetterTreeList.<String> build().safe(false).build());
			return new NameExpression(null, nameSequence);
			// Expression types
		case "assignment":
			//					TypedStatement<E, ? extends X> leftHand = evaluate(//
			//						expression.getComponents().getFirst().getComponents().getFirst(), env);
			//					TypedStatement<E, ? extends X> rightHand = evaluate(//
			//						expression.getComponents().getLast(), env);
			//					return assign(expression, leftHand, rightHand, expression.getComponents().get(1).toString());
		case "castExpression":
			//					return cast(expression, //
			//						evaluate(expression.getComponents().getLast(), env, OBJECT), //
			//						evaluateType(expression.getComponents().get(1), env));
		case "primary":
			//					TypedStatement<E, ?> expr = evaluate(expression.getComponents().getFirst(), env, OBJECT);
			//					for (int i = 1; i < expression.getComponents().size(); i++) {
			//						if (i == expression.getComponents().size() - 1)
			//							expr = modifyPrimary(expression, expr, expression.getComponents().get(i), env);
			//						else
			//							expr = modifyPrimary(expression, expr, expression.getComponents().get(i), env, OBJECT);
			//					}
			//					return (TypedStatement<E, ? extends X>) expr;
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
					base = new MethodExpression(base, ex.getComponent("Identifier").getText(), typeArgs, args);
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
		case "primaryNoNewArray_lfno_arrayAccess":
		case "primaryNoNewArray_lfno_primary_lfno_arrayAccess_lfno_primary":
			//					String lastName = expression.getComponents().getLast().toString();
			//					switch (lastName) {
			//					case "class":
			//						Class<?> raw = TypeTokens.getRawType(targetType);
			//						if (!raw.isAssignableFrom(Class.class))
			//							throw new CompilationException(expression, expression + " is not valid for type " + targetType);
			//						TypeToken<?> classType;
			//						if (Class.class.isAssignableFrom(raw)) {
			//							classType = targetType.resolveType(Class.class.getTypeParameters()[0]);
			//							int dimCount = expression.search().text("[").findAll().size();
			//							for (int i = 0; i < dimCount; i++) {
			//								if (!classType.isArray())
			//									throw new CompilationException(expression, expression + " is not valid for type " + targetType);
			//								classType = classType.getComponentType();
			//							}
			//							classType = evaluateType(expression.getComponents().getFirst(), env, classType);
			//							classType = dimCount);
			//						} else
			//							classType = ? extends X>) typeExpression(expression, classType);
			//							return getThis(expression, //
			//								evaluateType(expression.getComponents().getFirst(), env, OBJECT));
			//case ")":
			//	return evaluate(expression.getComponents().get(1), env);
			//default:
			//	throw new CompilationException(expression, "Unrecognized primary expression");
			//					}
		case "arrayCreationExpression":
			//					dimExps = expression.getComponents("expression");
			//					int dimCount = dimExps.size() + expression.search().get("dims").children().text("[").findAll().size();
			//					TypeToken<?> arrayTargetType = targetType;
			//					for (int i = 0; i < dimCount; i++)
			//						arrayTargetType = arrayTargetType.getComponentType();
			//					TypeToken<?> arrayType = evaluateType(expression.getComponents().get(1), env, arrayTargetType);
			//					arrayType = dimCount);
			//					if (arrayType))
			//throw new CompilationException(expression, "Array of type " + arrayType + " is not valid for type " + targetType);
			//// Should I open this up to allow other index types besides int?
			//indexes = new ArrayList<>(dimExps.size());
			//for (Expression dimExp : dimExps) {
			//	indexes.add(//
			//		evaluate(dimExp, env, "variableInitializerList", "variableInitializer");
			//		List<TypedStatement<E, ?>> values = new ArrayList<>(initValueExps.size());
			//		if (!initValueExps.isEmpty()) {
			//			for (Expression ive : initValueExps)
			//				values.add(evaluate(ive, env, componentType));
			//		}
			//		return arrayCreate(expression, (TypeToken<? extends X>) arrayType, indexes, values);
		case "arrayInitializer":
			//		componentType = targetType.getComponentType();
			//		initValueExps = expression.getComponents("variableInitializerList", "variableInitializer");
			//		values = new ArrayList<>(initValueExps.size());
			//		if (!initValueExps.isEmpty()) {
			//			for (Expression ive : initValueExps)
			//				values.add(evaluate(ive, env, componentType));
			//		}
			//		return arrayCreate(expression, Collections.emptyList(), values);
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
		case "unaryExpression":
		case "preIncrementExpression":
		case "preDecrementExpression":
		case "unaryExpressionNotPlusMinus":
			//		if (expression.getComponents().size() != 2)
			//			throw new CompilationException(expression,
			//				"Unrecognized expression with " + expression.getComponents().size() + " components");
			//		operator = expression.getComponents().getFirst().toString();
			//		ex = null;
			//		for (TypeToken<?> unaryTargetType : theOperations.getUnaryTargetTypes(targetType, operator, true)) {
			//			try {
			//				TypedStatement<E, ?> operand = evaluate(expression.getComponents().getLast(), env, unaryTargetType);
			//				UnaryOperation<?, X> op = theOperations.unaryOperation(operand.getReturnType(), operator, true);
			//				return unaryOperation(expression, (UnaryOperation<Object, X>) op, (TypedStatement<E, Object>) operand);
			//			} catch (CompilationException e) {
			//				if (ex == null)
			//					ex = e;
			//			}
			//		}
			//		throw ex;
		case "postfixExpression":
		case "postIncrementExpression":
		case "postDecrementExpression":
			//		if (expression.getComponents().size() != 2)
			//			throw new CompilationException(expression,
			//				"Unrecognized expression with " + expression.getComponents().size() + " components");
			//		operator = expression.getComponents().getFirst().toString();
			//		ex = null;
			//		for (TypeToken<?> unaryTargetType : theOperations.getUnaryTargetTypes(targetType, operator, false)) {
			//			try {
			//				TypedStatement<E, ?> operand = evaluate(expression.getComponents().getLast(), env, unaryTargetType);
			//				UnaryOperation<?, X> op = theOperations.unaryOperation(operand.getReturnType(), operator, false);
			//				return unaryOperation(expression, (UnaryOperation<Object, X>) op, (TypedStatement<E, Object>) operand);
			//			} catch (CompilationException e) {
			//				if (ex == null)
			//					ex = e;
			//			}
			//		}
			//		throw ex;
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

	private static BetterList<String> extractNameSequence(Expression expression, BetterList<String> names) {
		if (expression.getType().equals("Identifier")) {
			names.add(expression.getText());
			return names;
		}
		for (Expression comp : expression.getComponents())
			extractNameSequence(comp, names);
		return names;
	}

	public static class BinaryOperator implements ObservableExpression {
		private final String theOperator;
		private final ObservableExpression theLeft;
		private final ObservableExpression theRight;

		public BinaryOperator(String operator, ObservableExpression left, ObservableExpression right) {
			theOperator = operator;
			theLeft = left;
			theRight = right;
		}

		public String getOperator() {
			return theOperator;
		}

		public ObservableExpression getLeft() {
			return theLeft;
		}

		public ObservableExpression getRight() {
			return theRight;
		}

		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			if (type.getModelType() != ModelTypes.Value)
				throw new QonfigInterpretationException("Cannot evaluate a binary operator as anything but a value: " + type);
			// & | && || ^ + - * / % == != < > <= >= << >> >>>
			Class<?> leftType;
			switch (theOperator) {
			case "&":
			case "|":
			case "^": {
				// int, long, or boolean args, return type same
				ValueContainer<SettableValue, SettableValue<?>> left = theLeft
					.evaluate((ModelInstanceType<SettableValue, SettableValue<?>>) type, models, classView);
				ValueContainer<SettableValue, SettableValue<?>> right = theRight
					.evaluate((ModelInstanceType<SettableValue, SettableValue<?>>) type, models, classView);
				TypeToken<?> resultType = TypeTokens.get().getCommonType(left.getType().getType(0), right.getType().getType(0));
				Class<?> rawResult = TypeTokens.get().unwrap(TypeTokens.getRawType(resultType));
				if (rawResult == boolean.class)
					return (ValueContainer<M, MV>) logicalOp((ValueContainer<SettableValue, SettableValue<Boolean>>) (Function<?, ?>) left,
						(ValueContainer<SettableValue, SettableValue<Boolean>>) (Function<?, ?>) right);
				else if (rawResult == long.class)
					return (ValueContainer<M, MV>) longBitwiseOp(
						(ValueContainer<SettableValue, SettableValue<Number>>) (Function<?, ?>) left,
						(ValueContainer<SettableValue, SettableValue<Number>>) (Function<?, ?>) right);
				else if (rawResult == int.class || rawResult == short.class || rawResult == char.class || rawResult == byte.class)
					return (ValueContainer<M, MV>) bitwiseOp((ValueContainer<SettableValue, SettableValue<Number>>) (Function<?, ?>) left,
						(ValueContainer<SettableValue, SettableValue<Number>>) (Function<?, ?>) right);
				else
					throw new QonfigInterpretationException("Cannot apply binary operator '" + theOperator + "' to arguments of type "
						+ left.getType().getType(0) + " and " + right.getType().getType(0));
			}
			case "&&":
			case "||":
				// boolean
				throw new QonfigInterpretationException("Binary operator '" + theOperator + "' is not implemented yet");
			case "+":
			case "-":
			case "*":
			case "/":
			case "%":
				// number
				ValueContainer<SettableValue, SettableValue<?>> left = theLeft
				.evaluate((ModelInstanceType<SettableValue, SettableValue<?>>) type, models, classView);
				ValueContainer<SettableValue, SettableValue<?>> right = theRight
					.evaluate((ModelInstanceType<SettableValue, SettableValue<?>>) type, models, classView);
				return (ValueContainer<M, MV>) mathOp((ValueContainer<SettableValue, SettableValue<Number>>) (Function<?, ?>) left,
					(ValueContainer<SettableValue, SettableValue<Number>>) (Function<?, ?>) right);
			case "==":
			case "!=":
			case "<":
			case ">":
			case "<=":
			case ">=":
				// number args, return type boolean
				throw new QonfigInterpretationException("Binary operator '" + theOperator + "' is not implemented yet");
			case "<<":
			case ">>":
			case ">>>":
				// int or long left, int right, return type same as left
				throw new QonfigInterpretationException("Binary operator '" + theOperator + "' is not implemented yet");
			default:
				throw new QonfigInterpretationException("Unrecognized operator: " + theOperator + " in expression " + this);
			}
		}

		private ValueContainer<SettableValue, SettableValue<Boolean>> logicalOp(ValueContainer<SettableValue, SettableValue<Boolean>> left,
			ValueContainer<SettableValue, SettableValue<Boolean>> right) throws QonfigInterpretationException {
			throw new QonfigInterpretationException("Binary operator '" + theOperator + "' is not immplemented yet");
		}

		private ValueContainer<SettableValue, SettableValue<? extends Number>> longBitwiseOp(
			ValueContainer<SettableValue, SettableValue<Number>> left, ValueContainer<SettableValue, SettableValue<Number>> right)
				throws QonfigInterpretationException {
			throw new QonfigInterpretationException("Binary operator '" + theOperator + "' is not immplemented yet");
		}

		private ValueContainer<SettableValue, SettableValue<? extends Number>> bitwiseOp(
			ValueContainer<SettableValue, SettableValue<Number>> left, ValueContainer<SettableValue, SettableValue<Number>> right)
				throws QonfigInterpretationException {
			throw new QonfigInterpretationException("Binary operator '" + theOperator + "' is not immplemented yet");
		}

		private ValueContainer<SettableValue, ? extends SettableValue<? extends Number>> mathOp(
			ValueContainer<SettableValue, SettableValue<Number>> left, ValueContainer<SettableValue, SettableValue<Number>> right)
				throws QonfigInterpretationException {
			Class<?> resultType = TypeTokens.get()
				.unwrap(TypeTokens.getRawType(TypeTokens.get().getCommonType(left.getType().getType(0), right.getType().getType(0))));
			if (resultType == char.class || resultType == short.class || resultType == byte.class)
				resultType = int.class;
			Function<ModelSetInstance, SettableValue<? extends Number>> left2, right2;
			if (!left.getType().getType(0).isPrimitive()
				|| TypeTokens.get().unwrap(TypeTokens.getRawType(left.getType().getType(0))) != resultType) {
				TypeConverter<Number, Number> leftConverter = (TypeConverter<Number, Number>) TypeTokens.get()
					.getCast(left.getType().getType(0), TypeTokens.get().of(resultType));
				TypeConverter<Number, Number> leftReverse = (TypeConverter<Number, Number>) TypeTokens.get()
					.getCast(TypeTokens.get().of(resultType), left.getType().getType(0));
				Class<?> fResultType = resultType;
				left2 = msi -> left.apply(msi).transformReversible((Class<Number>) fResultType, tx -> tx.cache(false)//
					.map(leftConverter).withReverse(leftReverse));
			} else
				left2 = (ValueContainer<SettableValue, SettableValue<? extends Number>>) (ValueContainer<?, ?>) left;
			if (!right.getType().getType(0).isPrimitive()
				|| TypeTokens.get().unwrap(TypeTokens.getRawType(right.getType().getType(0))) != resultType) {
				TypeConverter<Number, Number> rightConverter = (TypeConverter<Number, Number>) TypeTokens.get()
					.getCast(right.getType().getType(0), TypeTokens.get().of(resultType));
				TypeConverter<Number, Number> rightReverse = (TypeConverter<Number, Number>) TypeTokens.get()
					.getCast(TypeTokens.get().of(resultType), right.getType().getType(0));
				Class<?> fResultType = resultType;
				right2 = msi -> right.apply(msi).transformReversible((Class<Number>) fResultType, tx -> tx.cache(false)//
					.map(rightConverter).withReverse(rightReverse));
			} else
				right2 = (ValueContainer<SettableValue, SettableValue<? extends Number>>) (ValueContainer<?, ?>) right;
			if (resultType == double.class) {
				if (theOperator.equals("%")) { // This one is special
					return ObservableModelSet.container(msi -> {
						SettableValue<Double> leftV = (SettableValue<Double>) left2.apply(msi);
						SettableValue<Double> rightV = (SettableValue<Double>) right2.apply(msi);
						return leftV.transformReversible(double.class, tx -> tx.cache(false)//
							.combineWith(rightV)//
							.combine((l, r) -> l % r)//
							.replaceSourceWith((res, txv) -> {
								long div = (long) (txv.getCurrentSource() / txv.get(rightV));
								return div + res;
							}, rep -> {
								return rep.rejectWith((res, txv) -> {
									if (Math.abs(res) >= Math.abs(txv.get(rightV)))
										return "The result of a modulo expression must be less than the modulus";
									return null;
								}, true, true);
							}));
					}, ModelTypes.Value.forType(double.class));
				}
				BiFunction<Double, Double, Double> op, reverse;
				switch (theOperator) {
				case "+":
					op = (l, r) -> l + r;
					reverse = (res, r) -> res - r;
					break;
				case "-":
					op = (l, r) -> l - r;
					reverse = (res, r) -> res + r;
					break;
				case "*":
					op = (l, r) -> l * r;
					reverse = (res, r) -> res / r;
					break;
				case "/":
					op = (l, r) -> l / r;
					reverse = (res, r) -> res * r;
					break;
				default:
					throw new IllegalStateException("Unimplemented binary operator '" + theOperator + "'");
				}
				return ObservableModelSet.container(msi -> {
					SettableValue<Double> leftV = (SettableValue<Double>) left2.apply(msi);
					SettableValue<Double> rightV = (SettableValue<Double>) right2.apply(msi);
					return leftV.transformReversible(double.class, tx -> tx.cache(false)//
						.combineWith(rightV)//
						.combine(op)//
						.replaceSource(reverse, null));
				}, ModelTypes.Value.forType(double.class));
			} else if (resultType == long.class) {
				if (theOperator.equals("%")) { // This one is special
					return ObservableModelSet.container(msi -> {
						SettableValue<Long> leftV = (SettableValue<Long>) left2.apply(msi);
						SettableValue<Long> rightV = (SettableValue<Long>) right2.apply(msi);
						return leftV.transformReversible(long.class, tx -> tx.cache(false)//
							.combineWith(rightV)//
							.combine((l, r) -> l % r)//
							.replaceSourceWith((res, txv) -> {
								long div = txv.getCurrentSource() / txv.get(rightV);
								return div + res;
							}, rep -> {
								return rep.rejectWith((res, txv) -> {
									if (Math.abs(res) >= Math.abs(txv.get(rightV)))
										return "The result of a modulo expression must be less than the modulus";
									return null;
								}, true, true);
							}));
					}, ModelTypes.Value.forType(long.class));
				}
				BiFunction<Long, Long, Long> op, reverse;
				switch (theOperator) {
				case "+":
					op = (l, r) -> l + r;
					reverse = (res, r) -> res - r;
					break;
				case "-":
					op = (l, r) -> l - r;
					reverse = (res, r) -> res + r;
					break;
				case "*":
					op = (l, r) -> l * r;
					reverse = (res, r) -> res / r;
					break;
				case "/":
					op = (l, r) -> l / r;
					reverse = (res, r) -> res * r;
					break;
				default:
					throw new IllegalStateException("Unimplemented binary operator '" + theOperator + "'");
				}
				return ObservableModelSet.container(msi -> {
					SettableValue<Long> leftV = (SettableValue<Long>) left2.apply(msi);
					SettableValue<Long> rightV = (SettableValue<Long>) right2.apply(msi);
					return leftV.transformReversible(long.class, tx -> tx.cache(false)//
						.combineWith(rightV)//
						.combine(op)//
						.replaceSource(reverse, null));
				}, ModelTypes.Value.forType(long.class));
			} else if (resultType == int.class) {
				if (theOperator.equals("%")) { // This one is special
					return ObservableModelSet.container(msi -> {
						SettableValue<Integer> leftV = (SettableValue<Integer>) left2.apply(msi);
						SettableValue<Integer> rightV = (SettableValue<Integer>) right2.apply(msi);
						return leftV.transformReversible(int.class, tx -> tx.cache(false)//
							.combineWith(rightV)//
							.combine((l, r) -> l % r)//
							.replaceSourceWith((res, txv) -> {
								int div = txv.getCurrentSource() / txv.get(rightV);
								return div + res;
							}, rep -> {
								return rep.rejectWith((res, txv) -> {
									if (Math.abs(res) >= Math.abs(txv.get(rightV)))
										return "The result of a modulo expression must be less than the modulus";
									return null;
								}, true, true);
							}));
					}, ModelTypes.Value.forType(int.class));
				}
				BiFunction<Integer, Integer, Integer> op, reverse;
				switch (theOperator) {
				case "+":
					op = (l, r) -> l + r;
					reverse = (res, r) -> res - r;
					break;
				case "-":
					op = (l, r) -> l - r;
					reverse = (res, r) -> res + r;
					break;
				case "*":
					op = (l, r) -> l * r;
					reverse = (res, r) -> res / r;
					break;
				case "/":
					op = (l, r) -> l / r;
					reverse = (res, r) -> res * r;
					break;
				default:
					throw new IllegalStateException("Unimplemented binary operator '" + theOperator + "'");
				}
				return ObservableModelSet.container(msi -> {
					SettableValue<Integer> leftV = (SettableValue<Integer>) left2.apply(msi);
					SettableValue<Integer> rightV = (SettableValue<Integer>) right2.apply(msi);
					return leftV.transformReversible(int.class, tx -> tx.cache(false)//
						.combineWith(rightV)//
						.combine(op)//
						.replaceSource(reverse, null));
				}, ModelTypes.Value.forType(int.class));
			} else
				throw new QonfigInterpretationException("Cannot apply binary operator '" + theOperator + " to arguments of type "
					+ left.getType().getType(0) + " and " + right.getType().getType(0));
		}

		@Override
		public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			throw new QonfigInterpretationException("Not supported for binary operators");
		}

		@Override
		public String toString() {
			return theLeft + " " + theOperator + " " + theRight;
		}
	}

	public static class ConditionalExpression implements ObservableExpression {
		private final ObservableExpression theCondition;
		private final ObservableExpression thePrimary;
		private final ObservableExpression theSecondary;

		public ConditionalExpression(ObservableExpression condition, ObservableExpression primary, ObservableExpression secondary) {
			theCondition = condition;
			thePrimary = primary;
			theSecondary = secondary;
		}

		public ObservableExpression getCondition() {
			return theCondition;
		}

		public ObservableExpression getPrimary() {
			return thePrimary;
		}

		public ObservableExpression getSecondary() {
			return theSecondary;
		}

		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			if (type != null && (type.getModelType() == ModelTypes.Value || type.getModelType() == ModelTypes.Collection
				|| type.getModelType() == ModelTypes.Set)) {//
			} else
				throw new QonfigInterpretationException(
					"Conditional expressions not supported for model type " + type.getModelType() + " (" + this + ")");
			ValueContainer<SettableValue, SettableValue<Boolean>> conditionV = theCondition.evaluate(//
				ModelTypes.Value.forType(boolean.class), models, classView);
			ValueContainer<M, MV> primaryV = thePrimary.evaluate(type, models, classView);
			ValueContainer<M, MV> secondaryV = theSecondary.evaluate(type, models, classView);
			// TODO reconcile compatible model types, like Collection and Set
			if (primaryV.getType().getModelType() != secondaryV.getType().getModelType())
				throw new QonfigInterpretationException("Incompatible expressions: " + thePrimary + ", evaluated to " + primaryV.getType()
				+ " and " + theSecondary + ", evaluated to " + secondaryV.getType());
			if (primaryV.getType().getModelType() == ModelTypes.Value || primaryV.getType().getModelType() == ModelTypes.Collection
				|| primaryV.getType().getModelType() == ModelTypes.Set) {//
			} else
				throw new QonfigInterpretationException(
					"Conditional expressions not supported for model type " + primaryV.getType().getModelType() + " (" + this + ")");

			ModelInstanceType<M, MV> resultType;
			if (primaryV.getType().equals(secondaryV.getType()))
				resultType = primaryV.getType();
			else if (thePrimary instanceof LiteralExpression && ((LiteralExpression<?>) thePrimary).getValue() == null)
				resultType = secondaryV.getType();
			else if (theSecondary instanceof LiteralExpression && ((LiteralExpression<?>) theSecondary).getValue() == null)
				resultType = primaryV.getType();
			else {
				TypeToken<?>[] types = new TypeToken[primaryV.getType().getModelType().getTypeCount()];
				for (int i = 0; i < types.length; i++)
					types[i] = TypeTokens.get().getCommonType(primaryV.getType().getType(i), secondaryV.getType().getType(i));
				resultType = (ModelInstanceType<M, MV>) primaryV.getType().getModelType().forTypes(types);
			}
			return new ValueContainer<M, MV>() {
				@Override
				public ModelInstanceType<M, MV> getType() {
					return resultType;
				}

				@Override
				public MV get(ModelSetInstance msi) {
					SettableValue<Boolean> conditionX = conditionV.get(msi);
					Object[] values = new Object[2];
					if (primaryV.getType().getModelType() == ModelTypes.Value) {
						return (MV) SettableValue.flattenAsSettable(conditionX.map(
							TypeTokens.get().keyFor(ObservableValue.class).<SettableValue<Object>> parameterized(resultType.getType(0)),
							c -> {
								if (c) {
									if (values[0] == null)
										values[0] = primaryV.get(msi);
									return (SettableValue<Object>) values[0];
								} else {
									if (values[1] == null)
										values[1] = secondaryV.get(msi);
									return (SettableValue<Object>) values[1];
								}
							}), null);
					} else if (primaryV.getType().getModelType() == ModelTypes.Collection) {
						return (MV) ObservableCollection.flattenValue(conditionX.map(TypeTokens.get().keyFor(ObservableCollection.class)
							.<ObservableCollection<Object>> parameterized(resultType.getType(0)), c -> {
								if (c) {
									if (values[0] == null)
										values[0] = primaryV.get(msi);
									return (ObservableCollection<Object>) values[0];
								} else {
									if (values[1] == null)
										values[1] = secondaryV.get(msi);
									return (ObservableCollection<Object>) values[1];
								}
							}));
					} else if (primaryV.getType().getModelType() == ModelTypes.Set) {
						return (MV) ObservableSet.flattenValue(conditionX.map(
							TypeTokens.get().keyFor(ObservableSet.class).<ObservableSet<Object>> parameterized(resultType.getType(0)),
							c -> {
								if (c) {
									if (values[0] == null)
										values[0] = primaryV.get(msi);
									return (ObservableSet<Object>) values[0];
								} else {
									if (values[1] == null)
										values[1] = secondaryV.get(msi);
									return (ObservableSet<Object>) values[1];
								}
							}));
					} else
						throw new IllegalStateException(
							"Conditional expressions not supported for model type " + primaryV.getType().getModelType());
				}
			};
		}

		@Override
		public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			throw new QonfigInterpretationException("Not supported for conditionals");
		}

		@Override
		public String toString() {
			return theCondition + " ? " + thePrimary + " : " + theSecondary;
		}
	}

	public static class NameExpression implements ObservableExpression {
		private final ObservableExpression theContext;
		private final BetterList<String> theNames;

		public NameExpression(ObservableExpression ctx, BetterList<String> names) {
			theContext = ctx;
			theNames = names;
		}

		public ObservableExpression getContext() {
			return theContext;
		}

		public BetterList<String> getNames() {
			return theNames;
		}

		/* Order of operations:
		 * Model value
		 * Statically-imported variable
		 *
		 */

		@Override
		public <M, MV extends M> ObservableModelSet.ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type,
			ObservableModelSet models, ClassView classView) throws QonfigInterpretationException {
			ValueContainer<?, ?> mv = null;
			if (theContext != null)
				mv = theContext.evaluate(ModelTypes.Value.any(), models, classView);
			if (mv == null)
				mv = models.get(theNames.getFirst(), false);
			if (mv != null)
				return evaluateModel(//
					mv, 1, new StringBuilder(theNames.get(0)), type, models);
			// Allow unqualified enum value references
			if (theNames.size() == 1 && type.getModelType() == ModelTypes.Value) {
				Class<?> paramType = TypeTokens.getRawType(type.getType(0));
				if (paramType != null && paramType.isEnum()) {
					for (Enum<?> value : ((Class<? extends Enum<?>>) paramType).getEnumConstants()) {
						if (value.name().equals(theNames.getFirst())) {
							return new ValueContainer<M, MV>() {
								final ModelInstanceType<M, MV> retType = (ModelInstanceType<M, MV>) ModelTypes.Value.forType(paramType);
								final MV retValue = (MV) ObservableModelQonfigParser.literal(value, value.name());

								@Override
								public ModelInstanceType<M, MV> getType() {
									return retType;
								}

								@Override
								public MV get(ModelSetInstance extModels) {
									return retValue;
								}
							};
						}
					}
				}
			}
			Field field = classView.getImportedStaticField(theNames.getFirst());
			if (field != null)
				return evaluateField(field, TypeTokens.get().of(field.getGenericType()), null, 1, type);
			StringBuilder typeName = new StringBuilder().append(theNames.get(0));
			Class<?> clazz = classView.getType(typeName.toString());
			int i;
			for (i = 1; i < theNames.size() - 1; i++) {
				typeName.append(theNames.get(i));
				clazz = classView.getType(typeName.toString());
			}
			if (clazz == null)
				throw new QonfigInterpretationException("'" + theNames.get(0) + "' cannot be resolved to a variable ");
			try {
				field = clazz.getField(theNames.get(i));
			} catch (NoSuchFieldException e) {
				throw new QonfigInterpretationException("'" + theNames.get(i) + "' cannot be resolved or is not a field");
			} catch (SecurityException e) {
				throw new QonfigInterpretationException(clazz.getName() + "." + theNames.get(i) + " cannot be accessed", e);
			}
			return evaluateField(field, TypeTokens.get().of(field.getGenericType()), null, 1, type);
		}

		private <M, MV extends M> ObservableModelSet.ValueContainer<M, MV> evaluateModel(ValueContainer<?, ?> mv, int nameIndex,
			StringBuilder path, ModelInstanceType<M, MV> type, ObservableModelSet models) throws QonfigInterpretationException {
			if (nameIndex == theNames.size())
				return models.get(toString(), type);
			if (mv.getType().getModelType() == ModelTypes.Model) {
				path.append('.').append(theNames.get(nameIndex));
				ValueContainer<?, ?> nextMV = models.get(path.toString(), false);
				if (nextMV != null)
					return evaluateModel(nextMV, nameIndex + 1, path, type, models);
				throw new QonfigInterpretationException("'" + theNames.get(nameIndex) + "' cannot be resolved or is not a model value");
			} else if (mv.getType().getModelType() == ModelTypes.Value) {
				Field field;
				try {
					field = TypeTokens.getRawType(mv.getType().getType(0)).getField(theNames.get(nameIndex));
				} catch (NoSuchFieldException e) {
					throw new QonfigInterpretationException(getPath(nameIndex) + "' cannot be resolved or is not a field");
				} catch (SecurityException e) {
					throw new QonfigInterpretationException(getPath(nameIndex) + " cannot be accessed", e);
				}
				return evaluateField(field, mv.getType().getType(0).resolveType(field.getGenericType()), //
					msi -> (SettableValue<?>) mv.get(msi), nameIndex + 1, type);
			} else
				throw new QonfigInterpretationException(
					"Cannot evaluate field '" + theNames.get(nameIndex + 1) + "' against model of type " + mv.getType());
		}

		private <M, MV extends M, F> ValueContainer<M, MV> evaluateField(Field field, TypeToken<F> fieldType,
			Function<ModelSetInstance, ? extends SettableValue<?>> context, int nameIndex, ModelInstanceType<M, MV> type)
				throws QonfigInterpretationException {
			if (!field.isAccessible()) {
				try {
					field.setAccessible(true);
				} catch (SecurityException e) {
					throw new QonfigInterpretationException("Could not access field " + getPath(nameIndex), e);
				}
			}
			if (nameIndex == theNames.size() - 1) {
				if (type.getModelType() == ModelTypes.Value) {
					return ObservableModelSet.<M, MV> container(
						(Function<ModelSetInstance, MV>) getFieldValue(field, fieldType, context, type.getType(0)),
						(ModelInstanceType<M, MV>) ModelTypes.Value.forType(fieldType));
				} else
					throw new IllegalStateException("Only Value types supported by fields currently"); // TODO
			}
			Field newField;
			try {
				newField = TypeTokens.getRawType(fieldType).getField(theNames.get(nameIndex));
			} catch (NoSuchFieldException e) {
				throw new QonfigInterpretationException(getPath(nameIndex) + "' cannot be resolved or is not a field");
			} catch (SecurityException e) {
				throw new QonfigInterpretationException(getPath(nameIndex) + " cannot be accessed", e);
			}
			return evaluateField(newField, fieldType.resolveType(newField.getGenericType()), //
				getFieldValue(field, fieldType, context, null), nameIndex + 1, type);
		}

		String getPath(int upToIndex) {
			StringBuilder str = new StringBuilder();
			for (int i = 0; i <= upToIndex; i++) {
				if (i > 0)
					str.append('.');
				str.append(theNames.get(i));
			}
			return str.toString();
		}

		@Override
		public String toString() {
			return StringUtils.print(null, ".", theNames, StringBuilder::append).toString();
		}

		private <F, M> Function<ModelSetInstance, SettableValue<M>> getFieldValue(Field field, TypeToken<F> fieldType,
			Function<ModelSetInstance, ? extends SettableValue<?>> context, TypeToken<M> targetType) {
			if (targetType == null || fieldType.equals(targetType)) {
				if (context == null)
					return msi -> (SettableValue<M>) new FieldValue<>(field, fieldType, null, null);
					return msi -> (SettableValue<M>) context.apply(msi).transformReversible(fieldType, tx -> tx.nullToNull(true).map(ctx -> {
						try {
							return (F) field.get(ctx);
						} catch (IllegalAccessException e) {
							throw new IllegalStateException("Could not access field " + field.getName(), e);
						}
					}).modifySource((ctx, newFieldValue) -> {
						try {
							field.set(ctx, newFieldValue);
						} catch (IllegalAccessException e) {
							throw new IllegalStateException("Could not access field " + field.getName(), e);
						}
					}));
			} else if (TypeTokens.get().isAssignable(targetType, fieldType)) {
				Function<F, M> cast = TypeTokens.get().getCast(fieldType, targetType, true);
				if (TypeTokens.get().isAssignable(fieldType, targetType)) {
					Function<M, F> reverse = TypeTokens.get().getCast(targetType, fieldType, true);
					if (context == null)
						return msi -> (SettableValue<M>) new FieldValue<>(field, fieldType, cast, reverse);
						return msi -> context.apply(msi).transformReversible(targetType,
							tx -> tx.nullToNull(true).map(ctx -> {
								try {
									return cast.apply((F) field.get(ctx));
								} catch (IllegalAccessException e) {
									throw new IllegalStateException("Could not access field " + field.getName(), e);
								}
							}).modifySource((ctx, newFieldValue) -> {
								try {
									field.set(ctx, reverse.apply(newFieldValue));
								} catch (IllegalAccessException e) {
									throw new IllegalStateException("Could not access field " + field.getName(), e);
								}
							}));
				} else {
					if (context == null)
						return msi -> (SettableValue<M>) new FieldValue<>(field, fieldType, cast, null);
						return msi -> (SettableValue<M>) context.apply(msi).transform((TypeToken<Object>) targetType,
							tx -> tx.nullToNull(true).map(ctx -> {
								try {
									return cast.apply((F) field.get(ctx));
								} catch (IllegalAccessException e) {
									throw new IllegalStateException("Could not access field " + field.getName(), e);
								}
							}));
				}
			} else
				throw new IllegalStateException(
					"Cannot convert from SettableValue<" + fieldType + "> to SettableValue<" + targetType + ">");
		}

		@Override
		public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			boolean voidTarget = TypeTokens.get().unwrap(TypeTokens.getRawType(targetType)) == void.class;
			return new MethodFinder<P1, P2, P3, T>(targetType) {
				@Override
				public Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> find3() throws QonfigInterpretationException {
					ValueContainer<?, ?> mv = models.get(toString(), false);
					if (mv != null) {
						for (MethodOption option : theOptions) {
							switch (option.argTypes.length) {
							case 0:
								if (mv.getType().getModelType() == ModelTypes.Value) {
									if (targetType.isAssignableFrom(mv.getType().getType(0))) {
										// TODO resultType
										return msi -> (p1, p2, p3) -> ((SettableValue<T>) mv.apply(msi)).get();
									} else if (TypeTokens.get().isAssignable(targetType, mv.getType().getType(0))) {
										ValueContainer<?, SettableValue<T>> mv2 = models.get(toString(),
											ModelTypes.Value.forType(targetType));
										// TODO resultType
										return msi -> (p1, p2, p3) -> mv2.apply(msi).get();
									} else if (Supplier.class.isAssignableFrom(TypeTokens.getRawType(mv.getType().getType(0)))
										&& TypeTokens.get().isAssignable(targetType,
											mv.getType().getType(0).resolveType(Supplier.class.getTypeParameters()[0]))) {
										// TODO resultType
										return msi -> (p1, p2, p3) -> ((SettableValue<Supplier<? extends T>>) mv.apply(msi)).get().get();
									} else if (voidTarget
										&& Runnable.class.isAssignableFrom(TypeTokens.getRawType(mv.getType().getType(0)))) {
										// TODO resultType
										return msi -> (p1, p2, p3) -> {
											((SettableValue<? extends Runnable>) mv.apply(msi)).get().run();
											return null;
										};
									} else
										continue;
								} else if (targetType.isAssignableFrom(TypeTokens.get().keyFor(mv.getType().getModelType().modelType)
									.parameterized(mv.getType().getType(0)))) {
									// TODO resultType
									return msi -> (p1, p2, p3) -> (T) mv.apply(msi);
								} else
									continue;
							case 1:
								if (mv.getType().getModelType() == ModelTypes.Value) {
									if (Function.class.isAssignableFrom(TypeTokens.getRawType(mv.getType().getType(0)))
										&& TypeTokens.get().isAssignable(
											mv.getType().getType(0).resolveType(Function.class.getTypeParameters()[0]), option.argTypes[0])//
										&& TypeTokens.get().isAssignable(targetType,
											mv.getType().getType(0).resolveType(Function.class.getTypeParameters()[1]))) {
										// TODO resultType
										return msi -> (p1, p2, p3) -> {
											Object[] args = new Object[1];
											option.argMaker.makeArgs(p1, p2, p3, args, msi);
											return ((SettableValue<Function<Object, ? extends T>>) mv.apply(msi)).get().apply(args[0]);
										};
									} else if (voidTarget
										&& Consumer.class.isAssignableFrom(TypeTokens.getRawType(mv.getType().getType(0)))) {
										// TODO resultType
										return msi -> (p1, p2, p3) -> {
											Object[] args = new Object[1];
											option.argMaker.makeArgs(p1, p2, p3, args, msi);
											((SettableValue<? extends Consumer<Object>>) mv.apply(msi)).get().accept(args[0]);
											return null;
										};
									} else
										continue;
								} else
									continue;
							case 2:
							default:
								// TODO
							}
						}
					} else if (theNames.size() == 1) {
						for (Method m : classView.getImportedStaticMethods(theNames.getFirst())) {
							Method finalM = m;
							for (MethodOption option : theOptions) {
								switch (option.argTypes.length) {
								case 0:
									if (!Modifier.isStatic(m.getModifiers()))
										continue;
									switch (m.getParameterTypes().length) {
									case 0:
										if (voidTarget || targetType.isAssignableFrom(TypeTokens.get().of(m.getGenericReturnType()))) {
											// TODO resultType
											return msi -> (p1, p2, p3) -> {
												try {
													return voidTarget ? null : (T) finalM.invoke(null);
												} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
													throw new IllegalStateException("Could not invoke " + finalM, e);
												}
											};
										} else
											continue;
									default:
										continue;
									}
								case 1:
								case 2:
								default:
									// TODO
								}
							}
						}
					} else {
						// TODO evaluate model value for names.length-1, then use that context to find a method
						Class<?> type = classView.getType(getPath(theNames.size() - 2));
						if (type != null) {
							Method[] methods = type.getMethods();
							for (Method m : methods) {
								if (!m.getName().equals(theNames.getLast()))
									continue;
								if (!Modifier.isStatic(m.getModifiers()))
									continue;
								Method finalM = m;
								for (MethodOption option : theOptions) {
									switch (option.argTypes.length) {
									case 0:
										switch (m.getParameterTypes().length) {
										case 1:
											if (!m.isVarArgs())
												continue;
											//$FALL-THROUGH$
										case 0:
											if (voidTarget || targetType.isAssignableFrom(TypeTokens.get().of(m.getGenericReturnType()))) {
												// TODO resultType
												return msi -> (p1, p2, p3) -> {
													try {
														return voidTarget ? null : (T) finalM.invoke(null,
															m.isVarArgs() ? new Object[] { new Object[0] } : new Object[0]);
													} catch (IllegalAccessException | IllegalArgumentException
														| InvocationTargetException e) {
														throw new IllegalStateException("Could not invoke " + finalM, e);
													}
												};
											} else
												continue;
										default:
											continue;
										}
									case 1:
										switch (m.getParameterTypes().length) {
										case 0:
											continue;
										case 2:
											if (!m.isVarArgs())
												continue;
											//$FALL-THROUGH$
										case 1:
											if (TypeTokens.get().isAssignable(TypeTokens.get().of(m.getGenericParameterTypes()[0]),
												option.argTypes[0])//
												&& (voidTarget
													|| targetType.isAssignableFrom(TypeTokens.get().of(m.getGenericReturnType())))) {
												// TODO resultType
												return msi -> (p1, p2, p3) -> {
													try {
														return voidTarget ? null : (T) finalM.invoke(null,
															m.isVarArgs() ? new Object[] { p1, new Object[0] } : new Object[] { p1 });
													} catch (IllegalAccessException | IllegalArgumentException
														| InvocationTargetException e) {
														throw new IllegalStateException("Could not invoke " + finalM, e);
													}
												};
											} else
												continue;
										default:
											continue;
										}
									case 2:
									default:
										// TODO
									}
								}
							}
						}
					}
					throw new QonfigInterpretationException("Could not parse method from " + NameExpression.this.toString());
				}
			};
		}

		public static class FieldValue<M, F> extends Identifiable.AbstractIdentifiable implements SettableValue<F> {
			private final Field theField;
			private final TypeToken<F> theType;
			private final Function<F, M> theCast;
			private final Function<M, F> theReverse;
			private final SimpleObservable<ObservableValueEvent<F>> theChanges;
			private long theStamp;

			FieldValue(Field field, TypeToken<F> type, Function<F, M> cast, Function<M, F> reverse) {
				theField = field;
				theType = type;
				theCast = cast;
				theReverse = reverse;
				theChanges = SimpleObservable.build().safe(false).build();
			}

			@Override
			protected Object createIdentity() {
				return Identifiable.baseId(theField.getName(), theField);
			}

			@Override
			public TypeToken<F> getType() {
				return theType;
			}

			@Override
			public boolean isLockSupported() {
				return false;
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return Transaction.NONE;
			}

			@Override
			public Transaction tryLock(boolean write, Object cause) {
				return Transaction.NONE;
			}

			@Override
			public F get() {
				try {
					return (F) theField.get(null);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					throw new IllegalStateException("Could not access field " + theField.getName(), e);
				}
			}

			@Override
			public Observable<ObservableValueEvent<F>> noInitChanges() {
				return theChanges.readOnly();
			}

			@Override
			public long getStamp() {
				return theStamp;
			}

			@Override
			public ObservableValue<String> isEnabled() {
				if (Modifier.isFinal(theField.getModifiers()))
					return ObservableValue.of("Final field cannot be assigned");
				return SettableValue.ALWAYS_ENABLED;
			}

			@Override
			public <V extends F> String isAcceptable(V value) {
				if (Modifier.isFinal(theField.getModifiers()))
					return "Final field cannot be assigned";
				return null;
			}

			@Override
			public <V extends F> F set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
				F previous;
				try {
					previous = (F) theField.get(null);
					theField.set(null, value);
				} catch (IllegalAccessException e) {
					throw new IllegalStateException("Could not access field " + theField.getName(), e);
				}
				theChanges.onNext(this.createChangeEvent(previous, value, cause));
				theStamp++;
				return null;
			}
		}
	}

	public static class MethodExpression implements ObservableExpression {
		private final ObservableExpression theContext;
		private final String theMethodName;
		private final List<String> theTypeArguments;
		private final List<ObservableExpression> theArguments;

		public MethodExpression(ObservableExpression context, String methodName, List<String> typeArgs,
			List<ObservableExpression> arguments) {
			theContext = context;
			theMethodName = methodName;
			theTypeArguments = QommonsUtils.unmodifiableCopy(typeArgs);
			theArguments = QommonsUtils.unmodifiableCopy(arguments);
		}

		public ObservableExpression getContext() {
			return theContext;
		}

		public String getMethodName() {
			return theMethodName;
		}

		public List<String> getTypeArguments() {
			return theTypeArguments;
		}

		public List<ObservableExpression> getArguments() {
			return theArguments;
		}

		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			TypeToken<?> targetType;
			if (type.getModelType() == ModelTypes.Action || type.getModelType() == ModelTypes.Value)
				targetType = type.getType(0);
			else
				targetType = TypeTokens.get().keyFor(type.getModelType().modelType).parameterized(type.getTypeList());

			class ArgOption implements Args {
				final List<ValueContainer<SettableValue, SettableValue<?>>>[] args = new List[theArguments.size()];
				ValueContainer<SettableValue, SettableValue<?>> firstResolved;

				{
					for (int a = 0; a < theArguments.size(); a++)
						args[a] = new ArrayList<>(2);
				}

				@Override
				public int size() {
					return args.length;
				}

				@Override
				public boolean matchesType(int arg, TypeToken<?> paramType) {
					ValueContainer<SettableValue, SettableValue<?>> c;
					for (int i = 0; i < args[arg].size(); i++) {
						c = args[arg].get(i);
						if (TypeTokens.get().isAssignable(paramType, c.getType().getType(i))) {
							// Move to the beginning
							args[arg].remove(i);
							args[arg].add(0, c);
							return true;
						}
					}
					// Not found, try to evaluate it
					try {
						c = (ValueContainer<SettableValue, SettableValue<?>>) (ValueContainer<?, ?>) theArguments.get(arg)
							.evaluate(ModelTypes.Value.forType(paramType), models, classView);
					} catch (QonfigInterpretationException e) {
						return false;
					}
					args[arg].add(0, c);
					return true;
				}

				@Override
				public TypeToken<?> resolveFirst() throws QonfigInterpretationException {
					if (firstResolved == null)
						firstResolved = theArguments.get(0).evaluate(ModelTypes.Value.any(), models, classView);
					return firstResolved.getType().getType(0);
				}
			}
			ArgOption args = new ArgOption();
			if (theContext != null) {
				if (theContext instanceof NameExpression) {
					Class<?> clazz = classView.getType(theContext.toString());
					if (clazz != null) {
						MethodResult<?> result = DefaultExpressoParser.findMethod(clazz.getMethods(), theMethodName,
							TypeTokens.get().of(clazz), true, Arrays.asList(args), targetType, models, classView);
						if (result != null) {
							ValueContainer<SettableValue, SettableValue<?>>[] realArgs = new ValueContainer[theArguments.size()];
							for (int a = 0; a < realArgs.length; a++)
								realArgs[a] = args.args[a].get(0);
							if (type.getModelType() == ModelTypes.Value)
								return (ValueContainer<M, MV>) new MethodValueContainer<>(result, null, Arrays.asList(realArgs));
							else if (type.getModelType() == ModelTypes.Action)
								return (ValueContainer<M, MV>) new MethodActionContainer<>(result, null, Arrays.asList(realArgs));
							else {
								TypeToken<?>[] paramTypes = new TypeToken[type.getModelType().getTypeCount()];
								for (int i = 0; i < paramTypes.length; i++)
									paramTypes[i] = result.returnType.resolveType(type.getModelType().modelType.getTypeParameters()[i]);
								return new MethodThingContainer<>((MethodResult<MV>) result, null, Arrays.asList(realArgs),
									(ModelInstanceType<M, MV>) type.getModelType().forTypes(paramTypes));
							}
						}
						throw new QonfigInterpretationException("No such method " + printSignature() + " in class " + clazz.getName());
					}
				}
				ValueContainer<SettableValue, SettableValue<?>> ctx = theContext.evaluate(ModelTypes.Value.any(), models, classView);
				MethodResult<?> result = DefaultExpressoParser.findMethod(TypeTokens.getRawType(ctx.getType().getType(0)).getMethods(),
					theMethodName, ctx.getType().getType(0), false, Arrays.asList(args), targetType, models, classView);
				if (result != null) {
					ValueContainer<SettableValue, SettableValue<?>>[] realArgs = new ValueContainer[theArguments.size()];
					for (int a = 0; a < realArgs.length; a++)
						realArgs[a] = args.args[a].get(0);
					if (type.getModelType() == ModelTypes.Value)
						return (ValueContainer<M, MV>) new MethodValueContainer<>(result, ctx, Arrays.asList(realArgs));
					else if (type.getModelType() == ModelTypes.Action)
						return (ValueContainer<M, MV>) new MethodActionContainer<>(result, ctx, Arrays.asList(realArgs));
					else {
						TypeToken<?>[] paramTypes = new TypeToken[type.getModelType().getTypeCount()];
						for (int i = 0; i < paramTypes.length; i++)
							paramTypes[i] = result.returnType.resolveType(type.getModelType().modelType.getTypeParameters()[i]);
						return new MethodThingContainer<>((MethodResult<MV>) result, ctx, Arrays.asList(realArgs),
							(ModelInstanceType<M, MV>) type.getModelType().forTypes(paramTypes));
					}
				}
				throw new QonfigInterpretationException("No such method " + printSignature() + " in type " + ctx.getType().getType(0));
			} else {
				List<Method> methods = classView.getImportedStaticMethods(theMethodName);
				MethodResult<?> result = DefaultExpressoParser.findMethod(methods.toArray(new Method[methods.size()]), theMethodName, null,
					true, Arrays.asList(args), targetType, models, classView);
				if (result != null) {
					ValueContainer<SettableValue, SettableValue<?>>[] realArgs = new ValueContainer[theArguments.size()];
					for (int a = 0; a < realArgs.length; a++)
						realArgs[a] = args.args[a].get(0);
					if (type.getModelType() == ModelTypes.Value)
						return (ValueContainer<M, MV>) new MethodValueContainer<>(result, null, Arrays.asList(realArgs));
					else if (type.getModelType() == ModelTypes.Action)
						return (ValueContainer<M, MV>) new MethodActionContainer<>(result, null, Arrays.asList(realArgs));
					else {
						TypeToken<?>[] paramTypes = new TypeToken[type.getModelType().getTypeCount()];
						for (int i = 0; i < paramTypes.length; i++)
							paramTypes[i] = result.returnType.resolveType(type.getModelType().modelType.getTypeParameters()[i]);
						return new MethodThingContainer<>((MethodResult<MV>) result, null, Arrays.asList(realArgs),
							(ModelInstanceType<M, MV>) type.getModelType().forTypes(paramTypes));
					}
				}
				throw new QonfigInterpretationException("No such imported method " + printSignature());
			}
		}

		@Override
		public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			throw new QonfigInterpretationException("Not implemented.  Are you sure you didn't mean to use the resolution operator (::)?");
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			if (theContext != null)
				str.append(theContext).append('.');
			str.append(printSignature());
			return str.toString();
		}

		public String printSignature() {
			StringBuilder str = new StringBuilder(theMethodName).append('(');
			boolean first = true;
			for (ObservableExpression arg : theArguments) {
				if (first)
					first = false;
				else
					str.append(", ");
				str.append(arg);
			}
			return str.append(')').toString();
		}

		public static abstract class MethodContainer<M, T, MV extends M> implements ValueContainer<M, MV> {
			private final MethodResult<T> theMethod;
			private final ValueContainer<SettableValue, SettableValue<?>> theContext;
			private final List<ValueContainer<SettableValue, SettableValue<?>>> theArguments;
			private final ModelInstanceType<M, MV> theType;

			public MethodContainer(MethodResult<T> method, ValueContainer<SettableValue, SettableValue<?>> context,
				List<ValueContainer<SettableValue, SettableValue<?>>> arguments, ModelInstanceType<M, MV> type) {
				theMethod = method;
				theArguments = arguments;
				theType = type;
				if (Modifier.isStatic(theMethod.method.getModifiers())) {
					if (context != null)
						System.out.println("Info: " + method + " should be called statically");
					theContext = null;
				} else {
					if (context == null)
						throw new IllegalStateException(method + " cannot be called without context");
					theContext = context;
				}
			}

			@Override
			public ModelInstanceType<M, MV> getType() {
				return theType;
			}

			@Override
			public MV get(ModelSetInstance models) {
				SettableValue<?> ctxV = theContext == null ? null : theContext.get(models);
				SettableValue<?>[] argVs = new SettableValue[theArguments.size()];
				Observable<?>[] changeSources = new Observable[ctxV == null ? argVs.length : argVs.length + 1];
				if (ctxV != null)
					changeSources[changeSources.length - 1] = ctxV.noInitChanges();
				for (int i = 0; i < argVs.length; i++) {
					argVs[i] = theArguments.get(i).get(models);
					changeSources[i] = argVs[i].noInitChanges();
				}
				return createModelValue(ctxV, argVs, //
					Observable.or(changeSources));
			}

			protected abstract MV createModelValue(SettableValue<?> ctxV, SettableValue<?>[] argVs, Observable<Object> changes);

			protected T invoke(SettableValue<?> ctxV, SettableValue<?>[] argVs)
				throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
				Object ctx = ctxV == null ? null : ctxV.get();
				if (ctx == null && ctxV != null)
					throw new NullPointerException(ctxV + " is null, cannot call " + theMethod);
				Object[] args = new Object[argVs.length];
				for (int a = 0; a < args.length; a++)
					args[a] = argVs[a].get();
				return theMethod.invoke(ctx, args);
			}

			@Override
			public String toString() {
				StringBuilder str = new StringBuilder();
				if (theContext != null)
					str.append(theContext).append('.');
				str.append(theMethod.method.getName()).append('(');
				for (int i = 0; i < theArguments.size(); i++) {
					if (i > 0)
						str.append(", ");
					str.append(theArguments.get(i));
				}
				return str.append(')').toString();
			}
		}

		public static class MethodActionContainer<T> extends MethodContainer<ObservableAction, T, ObservableAction<T>> {
			public MethodActionContainer(MethodResult<T> method, ValueContainer<SettableValue, SettableValue<?>> context,
				List<ValueContainer<SettableValue, SettableValue<?>>> arguments) {
				super(method, context, arguments, ModelTypes.Action.forType(method.returnType));
			}

			@Override
			public ModelInstanceType.SingleTyped<ObservableAction, T, ObservableAction<T>> getType() {
				return (SingleTyped<ObservableAction, T, ObservableAction<T>>) super.getType();
			}

			@Override
			protected ObservableAction<T> createModelValue(SettableValue<?> ctxV, SettableValue<?>[] argVs, Observable<Object> changes) {
				return ObservableAction.of(getType().getValueType(), __ -> {
					try {
						return invoke(ctxV, argVs);
					} catch (Throwable e) {
						e.printStackTrace();
						return null;
					}
				});
			}
		}

		public static class MethodValueContainer<T> extends MethodContainer<SettableValue, T, SettableValue<T>> {
			public MethodValueContainer(MethodResult<T> method, ValueContainer<SettableValue, SettableValue<?>> context,
				List<ValueContainer<SettableValue, SettableValue<?>>> arguments) {
				super(method, context, arguments, ModelTypes.Value.forType(method.returnType));
			}

			@Override
			public ModelInstanceType.SingleTyped<SettableValue, T, SettableValue<T>> getType() {
				return (SingleTyped<SettableValue, T, SettableValue<T>>) super.getType();
			}

			@Override
			protected SettableValue<T> createModelValue(SettableValue<?> ctxV, SettableValue<?>[] argVs, Observable<Object> changes) {
				CachingSupplier<T> supplier = new CachingSupplier<T>() {
					@Override
					protected T compute() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
						return invoke(ctxV, argVs);
					}
				};
				return SettableValue.asSettable(//
					ObservableValue.of(getType().getValueType(), supplier, supplier::getStamp, changes), //
					__ -> "Methods are not reversible");
			}
		}

		public static class MethodThingContainer<M, MV extends M> extends MethodContainer<M, MV, MV> {
			public MethodThingContainer(MethodResult<MV> method, ValueContainer<SettableValue, SettableValue<?>> context,
				List<ValueContainer<SettableValue, SettableValue<?>>> arguments, ModelInstanceType<M, MV> type) {
				super(method, context, arguments, type);
			}

			@Override
			protected MV createModelValue(SettableValue<?> ctxV, SettableValue<?>[] argVs, Observable<Object> changes) {
				CachingSupplier<MV> supplier = new CachingSupplier<MV>() {
					@Override
					protected MV compute() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
						return invoke(ctxV, argVs);
					}
				};
				if (getType().getModelType() == ModelTypes.Collection) {
					ObservableValue<ObservableCollection<?>> value = ObservableValue.of(
						TypeTokens.get().keyFor(ObservableCollection.class).parameterized(getType().getType(0)),
						() -> (ObservableCollection<?>) supplier.get(), supplier::getStamp, changes);
					return (MV) ObservableCollection.flattenValue(value);
				} else if (getType().getModelType() == ModelTypes.Set) {
					ObservableValue<ObservableSet<?>> value = ObservableValue.of(
						TypeTokens.get().keyFor(ObservableSet.class).parameterized(getType().getType(0)),
						() -> (ObservableSet<?>) supplier.get(), supplier::getStamp, changes);
					return (MV) ObservableSet.flattenValue(value);
				} else {
					try {
						return supplier.compute();
					} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
						e.printStackTrace();
						return (MV) ObservableCollection.of(getType().getType(0));
					}
				}
			}
		}

		private static abstract class CachingSupplier<T> implements Supplier<T> {
			private T theValue;
			private boolean isDirty = true;
			private long theStamp;

			void dirty() {
				isDirty = true;
				theStamp++;
			}

			long getStamp() {
				return theStamp;
			}

			@Override
			public T get() {
				if (isDirty) {
					isDirty = false;
					try {
						theValue = compute();
					} catch (Throwable e) {
						e.printStackTrace();
						theValue = null;
					}
				}
				return theValue;
			}

			protected abstract T compute() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException;
		}
	}

	public static class MethodReferenceExpression implements ObservableExpression {
		private final ObservableExpression theContext;
		private final String theMethodName;
		private final List<String> theTypeArgs;

		public MethodReferenceExpression(ObservableExpression context, String methodName, List<String> typeArgs) {
			theContext = context;
			theMethodName = methodName;
			theTypeArgs = QommonsUtils.unmodifiableCopy(typeArgs);
		}

		public ObservableExpression getContext() {
			return theContext;
		}

		public String getMethodName() {
			return theMethodName;
		}

		public List<String> getTypeArgs() {
			return theTypeArgs;
		}

		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			throw new QonfigInterpretationException("Not implemented");
		}

		@Override
		public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			return new MethodFinder<P1, P2, P3, T>(targetType) {
				@Override
				public Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> find3() throws QonfigInterpretationException {
					boolean voidTarget = TypeTokens.get().unwrap(TypeTokens.getRawType(targetType)) == void.class;
					if (theContext instanceof NameExpression) {
						Class<?> type = classView.getType(theContext.toString());
						if (type != null) {
							MethodResult<? extends T> result = DefaultExpressoParser.findMethod(type.getMethods(), theMethodName,
								TypeTokens.get().of(type), true, theOptions, targetType, models, classView);
							if (result != null) {
								setResultType(result.returnType);
								MethodOption option = theOptions.get(result.argListOption);
								return msi -> (p1, p2, p3) -> {
									Object[] args = new Object[option.argTypes.length];
									option.argMaker.makeArgs(p1, p2, p3, args, msi);
									try {
										T val = result.invoke(null, args);
										return voidTarget ? null : val;
									} catch (InvocationTargetException e) {
										throw new IllegalStateException(MethodReferenceExpression.this + ": Could not invoke " + result,
											e.getTargetException());
									} catch (IllegalAccessException | IllegalArgumentException e) {
										throw new IllegalStateException(MethodReferenceExpression.this + ": Could not invoke " + result, e);
									} catch (NullPointerException e) {
										NullPointerException npe = new NullPointerException(MethodReferenceExpression.this.toString()//
											+ (e.getMessage() == null ? "" : ": " + e.getMessage()));
										npe.setStackTrace(e.getStackTrace());
										throw npe;
									}
								};
							}
							throw new QonfigInterpretationException(
								"No such method matching: " + MethodReferenceExpression.this + " on class " + type.getName());
						}
					}
					ValueContainer<SettableValue, SettableValue<?>> ctx = theContext.evaluate(ModelTypes.Value.any(), models, classView);
					MethodResult<? extends T> result = DefaultExpressoParser.findMethod(//
						TypeTokens.getRawType(ctx.getType().getType(0)).getMethods(), theMethodName, ctx.getType().getType(0), true,
						theOptions, targetType, models, classView);
					if (result != null) {
						setResultType(result.returnType);
						MethodOption option = theOptions.get(result.argListOption);
						return msi -> (p1, p2, p3) -> {
							Object ctxV = ctx.apply(msi).get();
							Object[] args = new Object[option.argTypes.length];
							option.argMaker.makeArgs(p1, p2, p3, args, msi);
							try {
								T val = result.invoke(ctxV, args);
								return voidTarget ? null : val;
							} catch (InvocationTargetException e) {
								throw new IllegalStateException(MethodReferenceExpression.this + ": Could not invoke " + result,
									e.getTargetException());
							} catch (IllegalAccessException | IllegalArgumentException e) {
								throw new IllegalStateException(MethodReferenceExpression.this + ": Could not invoke " + result, e);
							} catch (NullPointerException e) {
								NullPointerException npe = new NullPointerException(MethodReferenceExpression.this.toString()//
									+ (e.getMessage() == null ? "" : ": " + e.getMessage()));
								npe.setStackTrace(e.getStackTrace());
								throw npe;
							}
						};
					}
					throw new QonfigInterpretationException(
						"No such method matching: " + MethodReferenceExpression.this + " for type " + ctx.getType().getType(0));
				}
			};
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(theContext.toString()).append("::");
			if (!theTypeArgs.isEmpty()) {
				str.append('<');
				for (int i = 0; i < theTypeArgs.size(); i++) {
					if (i > 0)
						str.append(", ");
					str.append(theTypeArgs.get(i));
				}
				str.append('>');
			}
			str.append(theMethodName);
			return str.toString();
		}
	}

	public static boolean checkArgCount(int parameters, int args, boolean varArgs) {
		if (varArgs)
			return args >= parameters - 1;
			else
				return args == parameters;
	}

	static <T> MethodResult<? extends T> findMethod(Method[] methods, String methodName, TypeToken<?> contextType, boolean arg0Context,
		List<? extends Args> argOptions, TypeToken<T> targetType, ObservableModelSet models, ClassView classView)
			throws QonfigInterpretationException {
		Class<T> rawTarget = TypeTokens.get().wrap(TypeTokens.getRawType(targetType));
		boolean voidTarget = rawTarget == Void.class;
		for (Method m : methods) {
			if (methodName != null && !m.getName().equals(methodName))
				continue;
			boolean isStatic = Modifier.isStatic(m.getModifiers());
			if (!isStatic && !arg0Context && contextType == null)
				continue;
			TypeToken<?>[] paramTypes = null;
			for (int o = 0; o < argOptions.size(); o++) {
				Args option = argOptions.get(o);
				int methodArgCount = (!isStatic && arg0Context) ? option.size() - 1 : option.size();
				if (methodArgCount < 0 || !checkArgCount(m.getParameterTypes().length, methodArgCount, m.isVarArgs()))
					continue;
				boolean ok = true;
				if (isStatic) {
					if (paramTypes == null) {
						paramTypes = new TypeToken[m.getParameterTypes().length];
						for (int p = 0; p < paramTypes.length; p++)
							paramTypes[p] = TypeTokens.get().of(m.getGenericParameterTypes()[p]);
					}
					// All arguments are parameters
					for (int a = 0; ok && a < option.size(); a++) {
						int p = a;
						TypeToken<?> paramType = p < paramTypes.length ? paramTypes[p] : paramTypes[paramTypes.length - 1];
						if (!option.matchesType(a, paramType))
							ok = false;
					}
					if (ok) {
						TypeToken<?> returnType = TypeTokens.get().of(m.getGenericReturnType());
						if (!voidTarget && !TypeTokens.get().isAssignable(targetType, returnType))
							throw new QonfigInterpretationException("Return type " + returnType + " of method " + printSignature(m)
							+ " cannot be assigned to type " + targetType);
						return new MethodResult<>(m, o, false, (TypeToken<? extends T>) returnType);
					}
				} else {
					if (arg0Context) {
						// Use the first argument as context
						contextType = option.resolveFirst();
						if (paramTypes == null) {
							paramTypes = new TypeToken[m.getParameterTypes().length];
							for (int p = 0; p < paramTypes.length; p++) {
								paramTypes[p] = contextType.resolveType(m.getGenericParameterTypes()[p]);
							}
						}
						ok = true;
						if (!option.matchesType(0, contextType))
							continue;
						for (int a = 1; ok && a < option.size(); a++) {
							int p = a - 1;
							TypeToken<?> paramType = p < paramTypes.length ? paramTypes[p] : paramTypes[paramTypes.length - 1];
							if (!option.matchesType(a, paramType))
								ok = false;
						}
						if (ok) {
							TypeToken<?> returnType;
							if (!isStatic && contextType != null && !(contextType.getType() instanceof Class))
								returnType = contextType.resolveType(m.getGenericReturnType());
							else
								returnType = TypeTokens.get().of(m.getGenericReturnType());
							if (!voidTarget && !TypeTokens.get().isAssignable(targetType, returnType))
								throw new QonfigInterpretationException("Return type " + returnType + " of method " + printSignature(m)
								+ " cannot be assigned to type " + targetType);
							return new MethodResult<>(m, o, true, (TypeToken<? extends T>) returnType);
						}
					} else {
						// Ignore context (supplied by caller), all arguments are parameters
						if (paramTypes == null) {
							paramTypes = new TypeToken[m.getParameterTypes().length];
							for (int p = 0; p < paramTypes.length; p++) {
								if (!(contextType.getType() instanceof Class))
									paramTypes[p] = contextType.resolveType(m.getGenericParameterTypes()[p]);
								else
									paramTypes[p] = TypeTokens.get().of(m.getGenericParameterTypes()[p]);
							}
						}
						for (int a = 0; ok && a < option.size(); a++) {
							int p = a;
							TypeToken<?> paramType = p < paramTypes.length ? paramTypes[p] : paramTypes[paramTypes.length - 1];
							if (!option.matchesType(a, paramType))
								ok = false;
						}
						if (ok) {
							TypeToken<?> returnType;
							if (!(contextType.getType() instanceof Class))
								returnType = contextType.resolveType(m.getGenericReturnType());
							else
								returnType = TypeTokens.get().of(m.getGenericReturnType());
							if (!voidTarget && !TypeTokens.get().isAssignable(targetType, returnType))
								throw new QonfigInterpretationException("Return type " + returnType + " of method " + printSignature(m)
								+ " cannot be assigned to type " + targetType);
							return new MethodResult<>(m, o, false, (TypeToken<? extends T>) returnType);
						}
					}
				}
			}
		}
		return null;
	}

	public static class LambdaExpression implements ObservableExpression {
		private final List<String> theParameters;
		private final ObservableExpression theBody;

		public LambdaExpression(List<String> parameters, ObservableExpression body) {
			theParameters = parameters;
			theBody = body;
		}

		public List<String> getParameters() {
			return theParameters;
		}

		public ObservableExpression getBody() {
			return theBody;
		}

		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluateInternal(ModelInstanceType<M, MV> type, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			throw new QonfigInterpretationException("Not yet implemented");
		}

		@Override
		public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			return new MethodFinder<P1, P2, P3, T>(targetType) {
				@Override
				public Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> find3() throws QonfigInterpretationException {
					QonfigInterpretationException ex = null;
					String exMsg = null;
					for (MethodOption option : theOptions) {
						if (option.argTypes.length != theParameters.size()) {
							if (exMsg == null)
								exMsg = theParameters.size() + " parameter" + (theParameters.size() == 1 ? "" : "s") + " provided, not "
									+ option.argTypes.length;
							continue;
						}
						ObservableModelSet.WrappedBuilder wrappedModelsBuilder = ObservableModelSet.wrap(models);
						ObservableModelSet.ModelValuePlaceholder<?, ?>[] placeholders = new ObservableModelSet.ModelValuePlaceholder[theParameters
						                                                                                                             .size()];
						for (int i = 0; i < theParameters.size(); i++)
							placeholders[i] = configureParameter(wrappedModelsBuilder, theParameters.get(i), option.argTypes[i]);
						ObservableModelSet.Wrapped wrappedModels = wrappedModelsBuilder.build();
						ValueContainer<SettableValue, SettableValue<T>> body;
						try {
							body = theBody.evaluate(ModelTypes.Value.forType(targetType), wrappedModels, classView);
						} catch (QonfigInterpretationException e) {
							if (ex == null)
								ex = e;
							continue;
						}
						ArgMaker<P1, P2, P3> argMaker = option.argMaker;
						setResultType((TypeToken<T>) body.getType().getType(0));
						return msi -> (p1, p2, p3) -> {
							Object[] args = new Object[theParameters.size()];
							if (argMaker != null)
								argMaker.makeArgs(p1, p2, p3, args, msi);
							ObservableModelSet.WrappedInstanceBuilder instBuilder = wrappedModels.wrap(msi);
							for (int i = 0; i < theParameters.size(); i++)
								instBuilder.with((ModelValuePlaceholder<SettableValue, SettableValue<Object>>) placeholders[i],
									ObservableModelQonfigParser.literal((TypeToken<Object>) placeholders[i].getType().getType(0), args[i],
										String.valueOf(args[i])));
							ModelSetInstance wrappedMSI = instBuilder.build();
							return body.apply(wrappedMSI).get();
						};
					}
					if (ex != null)
						throw ex;
					else if (exMsg != null)
						throw new QonfigInterpretationException(exMsg);
					else
						throw new QonfigInterpretationException("No options given");
				}

				private <T> ModelValuePlaceholder<SettableValue, SettableValue<T>> configureParameter(
					ObservableModelSet.WrappedBuilder modelBuilder, String paramName, TypeToken<T> paramType) {
					ModelInstanceType<SettableValue, SettableValue<T>> type = ModelTypes.Value.forType(paramType);
					return modelBuilder.withPlaceholder(paramName, type);
				}
			};
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			if (theParameters.size() == 1)
				str.append(theParameters.get(0));
			else {
				str.append('(');
				for (int i = 0; i < theParameters.size(); i++) {
					if (i > 0)
						str.append(',');
					str.append(theParameters.get(i));
				}
				str.append(')');
			}
			str.append("->");
			str.append(theBody);
			return str.toString();
		}
	}

	public static class MethodResult<R> {
		public final Method method;
		public final int argListOption;
		private final boolean isArg0Context;
		public final TypeToken<R> returnType;

		public MethodResult(Method method, int argListOption, boolean arg0Context, TypeToken<R> returnType) {
			this.method = method;
			this.argListOption = argListOption;
			isArg0Context = arg0Context;
			this.returnType = returnType;
		}

		public R invoke(Object context, Object[] args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			Object[] parameters;
			if (isArg0Context || method.isVarArgs()) {
				parameters = new Object[method.getParameterCount()];
				if (isArg0Context) {
					context = args[0];
					if (method.isVarArgs()) {
						System.arraycopy(args, 1, parameters, 0, parameters.length - 1);
						int lastArgLen = args.length - parameters.length;
						Object lastArg = Array.newInstance(method.getParameterTypes()[parameters.length - 1], lastArgLen);
						System.arraycopy(args, parameters.length, lastArg, 0, lastArgLen);
						parameters[parameters.length - 1] = lastArg;
					} else
						System.arraycopy(args, 1, parameters, 0, parameters.length);
				} else { // var args
					System.arraycopy(args, 0, parameters, 0, parameters.length - 1);
					int lastArgLen = args.length - parameters.length + 1;
					Object lastArg = Array.newInstance(method.getParameterTypes()[parameters.length - 1], lastArgLen);
					System.arraycopy(args, parameters.length - 1, lastArg, 0, lastArgLen);
					parameters[parameters.length - 1] = lastArg;
				}
			} else
				parameters = args;
			return (R) method.invoke(context, parameters);
		}

		@Override
		public String toString() {
			return printSignature(method);
		}
	}

	public static String printSignature(Method method) {
		StringBuilder str = new StringBuilder(method.getName()).append('(');
		for (int i = 0; i < method.getParameterCount(); i++) {
			if (i > 0)
				str.append(", ");
			str.append(method.getParameterTypes()[i].getName());
		}
		return str.append(')').toString();
	}
}
