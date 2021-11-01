package org.observe.expresso;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.Function;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.expresso.Expression.ExpressoParseException;
import org.observe.util.ClassView;
import org.observe.util.ModelType.ModelInstanceType;
import org.observe.util.ModelTypes;
import org.observe.util.ObservableModelSet;
import org.observe.util.ObservableModelSet.ModelSetInstance;
import org.observe.util.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

public class DefaultExpressoParser implements ExpressoParser {
	@Override
	public ObservableExpression parse(String text) throws ExpressoParseException {
		if (text.trim().isEmpty())
			return ObservableExpression.EMPTY;
		Java8Parser parser = new Java8Parser(null);
		ParseTree result;
		int max = 0;
		try {
			String input = "return " + text + ";";
			result = parseAntlr(parser, input, Java8Parser::returnStatement);
			result = result.getChild(1);
		} catch (RuntimeException e) {
			result = null;
		}
		RuntimeException ex = null;
		if (result == null) {
			try {
				String input = text + ";";
				result = parseAntlr(parser, input, Java8Parser::returnStatement);
				if (result.getText().length() == input.length())
					result = result.getChild(0);
				else {
					if (max < result.getText().length())
						max = result.getText().length();
					result = null;
				}
			} catch (RuntimeException e) {
				result = null;
				ex = e;
			}
		}
		if (result == null) {
			if (ex != null)
				throw ex;
			else
				throw new ExpressoParseException(max, text.length(), "?", text, "Unrecognized input");
		}
		Expression parsed = Expression.of(parser, result);
		return _parse(parsed);
	}

	private ParseTree parseAntlr(Java8Parser parser, String text, Function<Java8Parser, ParseTree> type) {
		Java8Lexer lexer = new Java8Lexer(CharStreams.fromString(text));
		CommonTokenStream tokens = new CommonTokenStream(lexer);

		// parser generates abstract syntax tree
		parser.setTokenStream(tokens);
		return type.apply(parser);
	}

	private ObservableExpression _parse(Expression expression) throws ExpressoParseException {
		ObservableExpression result;
		try {
			result = parse(expression);
		} catch (RuntimeException e) {
			throw new ExpressoParseException(expression, "Expression parsing failed");
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
			case "IntegerLiteral":
			case "FloatingPointLiteral":
			case "localVariableDeclarationStatement":
			case "statement":
			case "statementWithoutTrailingSubstatement":
			case "statementExpression":
			case "statementNoShortIf":
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
			throw new ExpressoParseException(expression, "Expression type not supported");
		case "this":
			return ThisExpression.INSTANCE;
			// Literals
		case "DecimalIntegerLiteral":
			boolean isLong = expression.search().get("IntegerTypeSuffix").findAny() != null;
			if (isLong)
				return literalExpression(expression, Long.parseLong(expression.toString()));
			else
				return literalExpression(expression, Integer.parseInt(expression.toString()));
		case "HexIntegerLiteral":
			isLong = expression.search().get("IntegerTypeSuffix").findAny() != null;
			String text = expression.search().get("HexDigits").find().toString();
			if (isLong)
				return literalExpression(expression, Long.parseLong(text, 16));
			else
				return literalExpression(expression, Integer.parseInt(text, 16));
		case "OctalIntegerLiteral":
			isLong = expression.search().get("IntegerTypeSuffix").findAny() != null;
			text = expression.search().get("OctalDigits").find().toString();
			if (isLong)
				return literalExpression(expression, Long.parseLong(text, 8));
			else
				return literalExpression(expression, Integer.parseInt(text, 8));
		case "BinaryIntegerLiteral":
			isLong = expression.search().get("IntegerTypeSuffix").findAny() != null;
			text = expression.search().get("BinaryDigits").find().toString();
			if (isLong)
				return literalExpression(expression, Long.parseLong(text, 2));
			else
				return literalExpression(expression, Integer.parseInt(text, 2));
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
			return literalExpression(expression, null);
		case "fieldAccess":
		case "fieldAccess_lfno_primary":
			//			target = expression.getComponents().getFirst();
			//			TypedStatement<E, ?> context;
			//			if (target.getComponents().isEmpty() && "super".equals(target.toString()))
			//				context = getSuper(expression, null);
			//			else if ("super".equals(expression.getComponents().get(1).toString()))
			//				context = getSuper(expression, //
			//					evaluateType(expression.getComponents().getFirst(), env, OBJECT));
			//			else
			//				context = evaluate(expression.getComponents().getFirst(), env, OBJECT);
			//			return fieldAccess(expression, context, expression.getComponents().getLast().toString(), env);
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
		case "methodInvocation":
		case "methodInvocation_lfno_primary":
			//					target = expression.getComponents().getFirst();
			//					String methodName;
			//					if ("methodName".equals(target.getType())) {
			//						context = null;
			//						methodName = target.toString();
			//					} else {
			//						methodName = expression.getComponent("Identifier").toString();
			//						if (target.getComponents().isEmpty() && "super".equals(target.toString()))
			//							context = getSuper(expression, null);
			//						else if ("super".equals(expression.getComponents().get(1).toString()))
			//							context = getSuper(expression, //
			//								evaluateType(target, env, OBJECT));
			//						else // TODO Failed to account for static method invocation (with type context) here
			//							context = evaluate(target, env, OBJECT);
			//					}
			//					List<TypeToken<?>> typeArgs;
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
			//					return methodInvocation(expression, context, typeArgs, methodName,
			//						expression.getComponents("argumentList", "expression"), env);
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
		case "lambdaExpression":
			//					throw new CompilationException(expression, "Lambda expressions are not supported");
		case "methodReference":
		case "methodReference_lfno_primary":
			//					target = expression.getComponents().getFirst();
			//					TypeToken<?> ctxType;
			//					if (target.getComponents().isEmpty() && "super".equals(target.toString())) {
			//						context = getSuper(expression, null);
			//						ctxType = null;
			//					} else {
			//						switch (target.getType()) {
			//						case "expressionName":
			//						case "referenceType":
			//						case "classType":
			//							Object ctx = evaluateExpressionOrType(expression, env, OBJECT);
			//							if (ctx instanceof TypeToken) {
			//								context = null;
			//								ctxType = (TypeToken<?>) ctx;
			//							} else {
			//								context = (TypedStatement<E, ?>) ctx;
			//								ctxType = null;
			//							}
			//							break;
			//						case "typeName":
			//							context = getSuper(expression, evaluateType(target, env, OBJECT));
			//							ctxType = null;
			//							break;
			//						case "arrayType":
			//							context = null;
			//							ctxType = evaluateType(target, env, OBJECT);
			//							break;
			//						default:
			//							throw new CompilationException(expression, "Unrecognized method reference context: " + target.getType());
			//						}
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
			//					return methodReference(expression, (TypedStatement<E, Object>) context, (TypeToken<Object>) ctxType, typeArgs,
			//						expression.getComponents().getLast().toString());
		case "ambiguousName":
		case "expressionName":
			// Other names?
			BetterList<String> nameSequence = extractNameSequence(expression, //
				BetterTreeList.<String> build().safe(false).build());
			return new NameExpression(nameSequence);
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
		case "conditionalExpression":
			//		if (expression.getComponents().size() != 5)
			//			throw new CompilationException(expression,
			//				"Unrecognized expression with " + expression.getComponents().size() + " components");
			//		CompilationException ex = null;
			//		String firstOp = expression.getComponents().get(1).toString();
			//		String secondOp = expression.getComponents().get(3).toString();
			//		for (TypeToken<?> leftTargetType : theOperations.getTernaryLeftTargetTypes(targetType, firstOp, secondOp)) {
			//			TypedStatement<E, ?> leftOperand;
			//			try {
			//				leftOperand = evaluate(expression.getComponents().getFirst(), env, leftTargetType);
			//			} catch (CompilationException e) {
			//				if (ex == null)
			//					ex = e;
			//				continue;
			//			}
			//			for (TypeToken<?> middleTargetType : theOperations.getTernaryMiddleTargetTypes(leftOperand.getReturnType(),
			//				firstOp, secondOp)) {
			//				TypedStatement<E, ?> middleOperand;
			//				try {
			//					middleOperand = evaluate(expression.getComponents().get(2), env, middleTargetType);
			//				} catch (CompilationException e) {
			//					if (ex == null)
			//						ex = e;
			//					continue;
			//				}
			//				for (TypeToken<?> rightTargetType : theOperations.getTernaryRightTargetTypes(leftOperand.getReturnType(),
			//					middleOperand.getReturnType(), firstOp, secondOp)) {
			//					TypedStatement<E, ?> rightOperand;
			//					try {
			//						rightOperand = evaluate(expression.getComponents().getLast(), env, rightTargetType);
			//					} catch (CompilationException e) {
			//						if (ex == null)
			//							ex = e;
			//						continue;
			//					}
			//					TernaryOperation<?, ?, ?, ? extends X> op = theOperations.ternaryOperation(targetType, leftOperand.getReturnType(),
			//						middleOperand.getReturnType(), rightOperand.getReturnType(), firstOp, secondOp);
			//					return ternaryOperation(expression, (TernaryOperation<Object, Object, Object, X>) op,
			//						(TypedStatement<E, Object>) leftOperand, (TypedStatement<E, Object>) middleOperand,
			//						(TypedStatement<E, Object>) rightOperand);
			//				}
			//			}
			//		}
			//		throw ex;
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
			//		if (expression.getComponents().size() != 3)
			//			throw new CompilationException(expression,
			//				"Unrecognized expression with " + expression.getComponents().size() + " components");
			//		String operator = expression.getComponents().get(1).toString();
			//		ex = null;
			//		for (TypeToken<?> leftTargetType : theOperations.getBinaryLeftTargetTypes(targetType, operator)) {
			//			TypedStatement<E, ?> leftOperand;
			//			try {
			//				leftOperand = evaluate(expression.getComponents().getFirst(), env, leftTargetType);
			//			} catch (CompilationException e) {
			//				if (ex == null)
			//					ex = e;
			//				continue;
			//			}
			//			for (TypeToken<?> rightTargetType : theOperations.getBinaryRightTargetTypes(leftOperand.getReturnType(),
			//				operator)) {
			//				TypedStatement<E, ?> rightOperand;
			//				try {
			//					rightOperand = evaluate(expression.getComponents().getLast(), env, rightTargetType);
			//				} catch (CompilationException e) {
			//					if (ex == null)
			//						ex = e;
			//					continue;
			//				}
			//				BinaryOperation<?, ?, ? extends X> op = theOperations.binaryOperation(targetType, leftOperand.getReturnType(),
			//					rightOperand.getReturnType(), operator);
			//				return binaryOperation(expression, (BinaryOperation<Object, Object, X>) op, (TypedStatement<E, Object>) leftOperand,
			//					(TypedStatement<E, Object>) rightOperand);
			//			}
			//		}
			//		throw ex;
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
			throw new IllegalStateException("Unimplemented expression type: " + expression.getType() + " " + expression);
		default:
			throw new IllegalStateException("Unrecognized expression type: " + expression.getType() + " " + expression);
		}
	}

	private ObservableExpression literalExpression(Expression expression, Object value) {
		return new LiteralExpression<>(expression, value);
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

	public static class LiteralExpression<T> implements ObservableExpression {
		private final Expression theExpression;
		private final T theValue;

		public LiteralExpression(Expression exp, T value) {
			theExpression = exp;
			theValue = value;
		}

		public Expression getExpression() {
			return theExpression;
		}

		public T getValue() {
			return theValue;
		}

		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type, ObservableModelSet models,
			ClassView classView) throws IllegalArgumentException {
			if (type.getModelType() != ModelTypes.Value)
				throw new IllegalArgumentException("'" + theExpression.getText() + "' cannot be evaluated as a " + type);
			if (theValue == null) {
				if (type.getType(0).isPrimitive())
					throw new IllegalArgumentException("Cannot assign null to a primitive type (" + type.getType(0));
				MV value = (MV) createValue(type.getType(0), null);
				return ObservableModelSet.container(LambdaUtils.constantFn(value, theExpression.getText(), null), type);
			} else if (TypeTokens.get().isInstance(type.getType(0), theValue)) {
				MV value = (MV) createValue(type.getType(0), theValue);
				return ObservableModelSet.container(LambdaUtils.constantFn(value, theExpression.getText(), null), type);
			} else if (TypeTokens.get().isAssignable(type.getType(0), TypeTokens.get().of(theValue.getClass()))) {
				MV value = (MV) createValue(type.getType(0), TypeTokens.get().cast(type.getType(0), theValue));
				return ObservableModelSet.container(LambdaUtils.constantFn(value, theExpression.getText(), null), type);
			} else
				throw new IllegalArgumentException("'" + theExpression.getText() + "' cannot be evaluated as a " + type);
		}

		SettableValue<?> createValue(TypeToken<?> type, Object value) {
			return SettableValue.asSettable(ObservableValue.of((TypeToken<Object>) type, value), //
				__ -> "Literal value '" + theExpression.getText() + "'");
		}

		@Override
		public String toString() {
			return String.valueOf(theValue);
		}
	}

	public static class NameExpression implements ObservableExpression {
		private final BetterList<String> theNames;

		public NameExpression(BetterList<String> names) {
			theNames = names;
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
		public <M, MV extends M> ObservableModelSet.ValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type, ObservableModelSet models,
			ClassView classView) throws IllegalArgumentException {
			ValueContainer<?, ?> mv = models.get(theNames.getFirst(), false);
			if (mv != null)
				return evaluateModel(mv, 1, new StringBuilder(theNames.get(0)), type, models);
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
				throw new IllegalArgumentException("'" + theNames.get(0) + "' cannot be resolved to a variable ");
			try {
				field = clazz.getField(theNames.get(i));
			} catch (NoSuchFieldException e) {
				throw new IllegalArgumentException("'" + theNames.get(i) + "' cannot be resolved or is not a field");
			} catch (SecurityException e) {
				throw new IllegalArgumentException(clazz.getName() + "." + theNames.get(i) + " cannot be accessed", e);
			}
			return evaluateField(field, TypeTokens.get().of(field.getGenericType()), null, 1, type);
		}

		private <M, MV extends M> ObservableModelSet.ValueContainer<M, MV> evaluateModel(ValueContainer<?, ?> mv, int nameIndex,
			StringBuilder path, ModelInstanceType<M, MV> type, ObservableModelSet models) {
			if (nameIndex == theNames.size())
				throw new IllegalArgumentException("'" + path + "' is a model, not a " + type);
			if (nameIndex == theNames.size() - 1)
				return models.get(toString(), type);
			if (mv.getType().getModelType() == ModelTypes.Model) {
				path.append('.').append(theNames.get(nameIndex));
				ValueContainer<?, ?> nextMV = models.get(path.toString(), false);
				if (nextMV != null)
					return evaluateModel(nextMV, nameIndex + 1, path, type, models);
				throw new IllegalArgumentException("'" + theNames.get(nameIndex) + "' cannot be resolved or is not a model value");
			} else if (mv.getType().getModelType() == ModelTypes.Value) {
				Field field;
				try {
					field = TypeTokens.getRawType(mv.getType().getType(0)).getField(theNames.get(nameIndex));
				} catch (NoSuchFieldException e) {
					throw new IllegalArgumentException(getPath(nameIndex) + "' cannot be resolved or is not a field");
				} catch (SecurityException e) {
					throw new IllegalArgumentException(getPath(nameIndex) + " cannot be accessed", e);
				}
				return evaluateField(field, mv.getType().getType(0).resolveType(field.getGenericType()), //
					msi -> (SettableValue<?>) mv.get(msi), nameIndex + 1, type);
			} else
				throw new IllegalArgumentException(
					"Cannot evaluate field '" + theNames.get(nameIndex + 1) + "' against model of type " + mv.getType());
		}

		private <M, MV extends M, F> ValueContainer<M, MV> evaluateField(Field field, TypeToken<F> fieldType,
			Function<ModelSetInstance, ? extends SettableValue<?>> context, int nameIndex, ModelInstanceType<M, MV> type) {
			if (!field.isAccessible()) {
				try {
					field.setAccessible(true);
				} catch (SecurityException e) {
					throw new IllegalArgumentException("Could not access field " + getPath(nameIndex), e);
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
				throw new IllegalArgumentException(getPath(nameIndex) + "' cannot be resolved or is not a field");
			} catch (SecurityException e) {
				throw new IllegalArgumentException(getPath(nameIndex) + " cannot be accessed", e);
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

	public static class ThisExpression implements ObservableExpression {
		public static final ThisExpression INSTANCE = new ThisExpression();

		private ThisExpression() {
		}

		@Override
		public <M, MV extends M> ValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type, ObservableModelSet models,
			ClassView classView) throws IllegalArgumentException {
			// TODO Auto-generated method stub
		}

		@Override
		public String toString() {
			return "this";
		}
	}
}
