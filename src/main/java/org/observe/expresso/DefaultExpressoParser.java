package org.observe.expresso;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
import org.observe.util.ObservableModelQonfigParser;
import org.observe.util.ObservableModelSet;
import org.observe.util.ObservableModelSet.ModelSetInstance;
import org.observe.util.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.Identifiable;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.Transaction;
import org.qommons.TriFunction;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigInterpreter.QonfigInterpretationException;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

import main.antlr.Java8Lexer;
import main.antlr.Java8Parser;

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
		case "lambdaExpression":
			//					throw new CompilationException(expression, "Lambda expressions are not supported");
			throw new ExpressoParseException(expression, "Expression type '" + expression.getType() + "' not implemented yet");
		case "methodReference":
		case "methodReference_lfno_primary":
			target = expression.getComponents().getFirst();
			TypeToken<?> ctxType;
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
			ClassView classView) throws QonfigInterpretationException {
			ValueContainer<?, ?> mv = models.get(theNames.getFirst(), false);
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
		public <M, MV extends M> ValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			if (theContext != null) {
				// TODO
			} else {
				List<Method> methods = classView.getImportedStaticMethods(theMethodName);
				// for(Method m : methods) {
				// ValueContainer<M, MV>
				// }
			}
			throw new QonfigInterpretationException("Method is not fully implemented yet");
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
		public <M, MV extends M> ValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			throw new QonfigInterpretationException("Not implemented");
		}

		@Override
		public <P1, P2, P3, T> MethodFinder<P1, P2, P3, T> findMethod(TypeToken<T> targetType, ObservableModelSet models,
			ClassView classView) throws QonfigInterpretationException {
			return new MethodFinder<P1, P2, P3, T>(targetType) {
				@Override
				public Function<ModelSetInstance, TriFunction<P1, P2, P3, T>> find3() throws QonfigInterpretationException {
					if (theContext instanceof NameExpression) {
						Class<?> type = classView.getType(theContext.toString());
						if (type != null) {
							Method[] methods = type.getMethods();
							for (Method m : methods) {
								if (!m.getName().equals(theMethodName))
									continue;
								Method finalM = m;
								for (MethodOption option : theOptions) {
									switch (option.argTypes.length) {
									case 0:
										continue; // TODO
									case 1:
										switch (m.getParameterTypes().length) {
										case 0:
											if (Modifier.isStatic(m.getModifiers()))
												continue;
											else if (!type.isAssignableFrom(TypeTokens.getRawType(option.argTypes[0])))
												continue;
											TypeToken<?> resultType = TypeTokens.get().of(m.getGenericReturnType());
											if (!TypeTokens.get().isAssignable(targetType, resultType))
												continue;
											setResultType((TypeToken<? extends T>) resultType);
											return msi -> (p1, p2, p3) -> {
												try {
													return (T) finalM.invoke(p1);
												} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
													throw new IllegalStateException("Could not invoke " + finalM, e);
												}
											};
										case 1:
											if (!Modifier.isStatic(m.getModifiers()))
												continue;
											else if (!TypeTokens.get().isAssignable(TypeTokens.get().of(m.getGenericParameterTypes()[0]),
												option.argTypes[0]))
												continue;
											resultType = TypeTokens.get().of(m.getGenericReturnType());
											if (!TypeTokens.get().isAssignable(targetType, resultType))
												continue;
											setResultType((TypeToken<? extends T>) resultType);
											return msi -> (p1, p2, p3) -> {
												try {
													return (T) finalM.invoke(null, new Object[] { p1 });
												} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
													throw new IllegalStateException("Could not invoke " + finalM, e);
												}
											};
										case 2:
										default:
											// TODO
											continue;
										}
									}
								}
							}
						}
					}
					// ValueContainer<?, ?> ctx=theContext.evaluate(null, models, classView)
					// ValueContainer<?, ?> mv=models.get(theMethodName, false)
					throw new QonfigInterpretationException("Could not evaluate method from " + MethodReferenceExpression.this.toString());
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
}
