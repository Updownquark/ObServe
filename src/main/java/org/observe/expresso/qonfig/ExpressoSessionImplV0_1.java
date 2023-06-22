package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.Set;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoParser;
import org.observe.expresso.JavaExpressoParser;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ops.BinaryOperatorSet;
import org.observe.expresso.ops.UnaryOperatorSet;
import org.observe.util.TypeTokens;
import org.qommons.Version;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.config.SpecialSessionImplementation;

import com.google.common.reflect.TypeToken;

/** Supports Qonfig evaluation using the {@link ExpressoQIS} session type */
public class ExpressoSessionImplV0_1 implements SpecialSessionImplementation<ExpressoQIS> {
	/** The name of the core toolkit */
	public static final String TOOLKIT_NAME = "Expresso-Core";
	/** The Expresso version supported by this class */
	public static final Version VERSION = new Version(0, 1, 0);
	/**
	 * An extra convenience operator that is an OR operation (||) for objects. This operator returns the first argument if it is not null,
	 * otherwise it returns the second.
	 */
	public BinaryOperatorSet.BinaryOp<Object, Object, Object> OBJECT_OR = new BinaryOperatorSet.BinaryOp<Object, Object, Object>() {
		@Override
		public Class<Object> getTargetSuperType() {
			return Object.class;
		}

		@Override
		public TypeToken<Object> getTargetType(TypeToken<? extends Object> leftOpType, TypeToken<? extends Object> rightOpType) {
			return TypeTokens.get().getCommonType(leftOpType, rightOpType);
		}

		@Override
		public Object apply(Object source, Object other) {
			if (source != null)
				return source;
			else
				return other;
		}

		@Override
		public String canReverse(Object currentSource, Object other, Object value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public Object reverse(Object currentSource, Object other, Object value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public String getDescription() {
			return "Object OR operation--first non-null value";
		}

		@Override
		public String toString() {
			return "||";
		}
	};

	private QonfigToolkit theToolkit;
	private DynamicModelValue.Cache theDyamicValueCache;

	private QonfigAddOn theParseableEl;
	private QonfigChildDef.Declared theParserChild;
	private QonfigAttributeDef theInheritOpsAttr;
	private QonfigChildDef.Declared theUnaryOps;
	private QonfigChildDef.Declared theBinaryOps;

	@Override
	public String getToolkitName() {
		return TOOLKIT_NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public Class<ExpressoQIS> getProvidedAPI() {
		return ExpressoQIS.class;
	}

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return Collections.emptySet(); // No dependencies
	}

	/**
	 * @param dmvCache The dynamic value cache for this session implementation to use, instead of creating a fresh one
	 * @return This session implementation
	 */
	public ExpressoSessionImplV0_1 withDynamicValueCache(DynamicModelValue.Cache dmvCache) {
		theDyamicValueCache = dmvCache;
		return this;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
		theToolkit = toolkit;
		theParseableEl = toolkit.getAddOn("expresso-parseable");
		theParserChild = theParseableEl.getChild("expresso-parser").getDeclared();
		theInheritOpsAttr = theParseableEl.getAttribute("inherit-operators");
		theUnaryOps = theParseableEl.getChild("unary-operators").getDeclared();
		theBinaryOps = theParseableEl.getChild("binary-operators").getDeclared();
	}

	@Override
	public ExpressoQIS viewOfRoot(CoreSession coreSession, ExpressoQIS source) throws QonfigInterpretationException {
		if (coreSession.get(ExpressoQIS.DYNAMIC_VALUE_CACHE) == null) {
			if (theDyamicValueCache == null)
				theDyamicValueCache = new DynamicModelValue.Cache();
			coreSession.put(ExpressoQIS.DYNAMIC_VALUE_CACHE, theDyamicValueCache);
		}
		ExpressoQIS qis = new ExpressoQIS(coreSession);
		qis.setExpressoParser(new JavaExpressoParser());
		qis.setExpressoEnv(ExpressoEnv.STANDARD_JAVA//
			.at(coreSession.getElement().getFilePosition())//
			.withOperators(null, BinaryOperatorSet.STANDARD_JAVA.copy()//
				.with("||", Object.class, Object.class, OBJECT_OR)//
				.build()));
		configureExpressionParsing(qis);
		return qis;
	}

	@Override
	public ExpressoQIS viewOfChild(ExpressoQIS parent, CoreSession coreSession) throws QonfigInterpretationException {
		ExpressoQIS qis = new ExpressoQIS(coreSession);
		qis.setExpressoParser(parent.getExpressoParser());
		qis.setExpressoEnv(parent.getExpressoEnv());
		configureExpressionParsing(qis);
		return qis;
	}

	@Override
	public ExpressoQIS parallelView(ExpressoQIS parallel, CoreSession coreSession) {
		ExpressoQIS qis = new ExpressoQIS(coreSession);
		qis.setExpressoParser(parallel.getExpressoParser());
		qis.setExpressoEnv(parallel.getExpressoEnv());
		return qis;
	}

	@Override
	public void postInitRoot(ExpressoQIS session, ExpressoQIS source) throws QonfigInterpretationException {
	}

	@Override
	public void postInitChild(ExpressoQIS session, ExpressoQIS parent) throws QonfigInterpretationException {
	}

	@Override
	public void postInitParallel(ExpressoQIS session, ExpressoQIS parallel) throws QonfigInterpretationException {
	}

	/**
	 * Configures expression parsing for the session's element's configuration
	 *
	 * @param session The session to configure
	 * @throws QonfigInterpretationException If the session's element's parsing configuration cannot be set up for any reason
	 */
	protected void configureExpressionParsing(ExpressoQIS session) throws QonfigInterpretationException {
		if (!session.getElement().isInstance(theParseableEl))
			return;
		QonfigElement parserEl = session.getElement().getChildrenByRole().get(theParserChild).peekFirst();
		if (parserEl != null) {
			ExpressoParser newParser;
			try {
				newParser = parseValue(parserEl.getValueText(), ExpressoParser.class, session.getElement(),
					session.getExpressoEnv());
			} catch (ModelInstantiationException | ExpressoException | ExpressoInterpretationException | TypeConversionException e) {
				session.reporting().error("Could not interpret expresso parser for " + parserEl, e);
				throw new IllegalStateException(e); // Shouldn't get here
			}
			session.setExpressoParser(newParser);
		}
		boolean inherit = session.getElement().getAttribute(theInheritOpsAttr, boolean.class);
		BetterCollection<QonfigElement> unaryOps = session.getElement().getChildrenByRole().get(theUnaryOps);
		BetterCollection<QonfigElement> binaryOps = session.getElement().getChildrenByRole().get(theBinaryOps);
		if (!inherit || !unaryOps.isEmpty() || !binaryOps.isEmpty()) {
			UnaryOperatorSet.Builder unaryOpsBuilder = inherit ? session.getExpressoEnv().getUnaryOperators().copy()
				: UnaryOperatorSet.build();
			BinaryOperatorSet.Builder binaryOpsBuilder = inherit ? session.getExpressoEnv().getBinaryOperators().copy()
				: BinaryOperatorSet.build();
			for (QonfigElement uo : unaryOps) {
				UnaryOperatorSet.UnaryOperatorConfiguration ops;
				try {
					ops = parseValue(uo.getValueText(),
						UnaryOperatorSet.UnaryOperatorConfiguration.class, uo, session.getExpressoEnv());
				} catch (ModelInstantiationException | ExpressoException | ExpressoInterpretationException | TypeConversionException e) {
					session.reporting().error("Could not interpret unary operator for " + uo, e);
					throw new IllegalStateException(e); // Shouldn't get here
				}
				unaryOpsBuilder = ops.configure(unaryOpsBuilder);
			}
			for (QonfigElement bo : binaryOps) {
				BinaryOperatorSet.BinaryOperatorConfiguration ops;
				try {
					ops = parseValue(bo.getValueText(),
						BinaryOperatorSet.BinaryOperatorConfiguration.class, bo, session.getExpressoEnv());
				} catch (ModelInstantiationException | ExpressoException | ExpressoInterpretationException | TypeConversionException e) {
					session.reporting().error("Could not interpret binary operator for " + bo, e);
					throw new IllegalStateException(e); // Shouldn't get here
				}
				binaryOpsBuilder = ops.configure(binaryOpsBuilder);
			}
			session.setExpressoEnv(session.getExpressoEnv().withOperators(unaryOpsBuilder.build(), binaryOpsBuilder.build()));
		}
	}

	/**
	 * @param <T> The type of the value to parse
	 * @param parseText The text to parse
	 * @param type The type of the value to parse
	 * @param element The element to parse the value for
	 * @param sourceEnv The Expresso environment to use to evaluate the parsed expression
	 * @return The parsed and evaluated expression
	 * @throws ExpressoException If the expression could not be parsed
	 * @throws ExpressoInterpretationException If the expression could not be evaluated as a value of the given type
	 * @throws ModelInstantiationException If the expression's resulting value could not be instantiated
	 * @throws TypeConversionException If the expression's type does not match the given type
	 */
	protected <T> T parseValue(String parseText, Class<T> type, QonfigElement element, ExpressoEnv sourceEnv)
		throws ExpressoException, ExpressoInterpretationException, ModelInstantiationException, TypeConversionException {
		InterpretedModelSet models = ObservableModelSet
			.build(element.getType().getName() + "_value", ObservableModelSet.JAVA_NAME_CHECKER)//
			.with("toolkit", ModelTypes.Value.forType(QonfigToolkit.class),
				m -> SettableValue.of(QonfigToolkit.class, theToolkit, "Not modifiable"), null)//
			.with("element", ModelTypes.Value.forType(QonfigElement.class),
				m -> SettableValue.of(QonfigElement.class, element, "Not modifiable"), element.getPositionInFile())//
			.build()//
			.interpret();
		ExpressoEnv env = sourceEnv.with(models, null);

		return new JavaExpressoParser().parse(parseText)//
			.evaluate(ModelTypes.Value.forType(type), env, 0)//
			.get(models.createInstance(ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER).build(), Observable.empty())
				.build())//
			.get();
	}
}
