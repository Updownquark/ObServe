package org.observe.expresso;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.observe.Observable;
import org.observe.SettableValue;
import org.observe.expresso.Expression.ExpressoParseException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.SuppliedModelValue.Satisfier;
import org.observe.expresso.ops.BinaryOperatorSet;
import org.observe.expresso.ops.UnaryOperatorSet;
import org.observe.util.TypeTokens;
import org.qommons.ClassMap;
import org.qommons.Version;
import org.qommons.collect.BetterCollection;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigChildDef;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.config.SpecialSessionImplementation;

/** Supports Qonfig evaluation using the {@link ExpressoQIS} session type */
public class ExpressoSessionImplV0_1 implements SpecialSessionImplementation<ExpressoQIS>{
	/** The Expresso version supported by this class */
	public static final Version VERSION = new Version(0, 1, 0);
	private QonfigToolkit theToolkit;

	private QonfigAddOn theParseableEl;
	private QonfigChildDef.Declared theParserChild;
	private QonfigAttributeDef theInheritOpsAttr;
	private QonfigChildDef.Declared theUnaryOps;
	private QonfigChildDef.Declared theBinaryOps;

	@Override
	public String getToolkitName() {
		return ExpressoQIS.TOOLKIT_NAME;
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
		ExpressoQIS qis = new ExpressoQIS(coreSession);
		qis.setExpressoParser(new JavaExpressoParser());
		qis.setExpressoEnv(ExpressoEnv.STANDARD_JAVA);
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
		initElementModels(session, null);
	}

	@Override
	public void postInitChild(ExpressoQIS session, ExpressoQIS parent) throws QonfigInterpretationException {
		initElementModels(session, parent);
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
		if (!session.isInstance("expresso-parser"))
			return;
		QonfigElement parserEl = session.getElement().getChildrenByRole().get(theParserChild).peekFirst();
		if (parserEl != null) {
			ExpressoParser newParser = parseValue(parserEl.getValueText(), ExpressoParser.class, session.getElement(),
				session.getExpressoEnv());
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
				UnaryOperatorSet.UnaryOperatorConfiguration ops = parseValue(uo.getValueText(),
					UnaryOperatorSet.UnaryOperatorConfiguration.class, uo, session.getExpressoEnv());
				unaryOpsBuilder = ops.configure(unaryOpsBuilder);
			}
			for (QonfigElement bo : binaryOps) {
				BinaryOperatorSet.BinaryOperatorConfiguration ops = parseValue(bo.getValueText(),
					BinaryOperatorSet.BinaryOperatorConfiguration.class, bo, session.getExpressoEnv());
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
	 * @throws QonfigInterpretationException If the expression could not be parsed or evaluated
	 */
	protected <T> T parseValue(String parseText, Class<T> type, QonfigElement element, ExpressoEnv sourceEnv)
		throws QonfigInterpretationException {
		ObservableModelSet models = ObservableModelSet.build(ObservableModelSet.JAVA_NAME_CHECKER)//
			.with("toolkit", ModelTypes.Value.forType(QonfigToolkit.class),
				m -> SettableValue.of(QonfigToolkit.class, theToolkit, "Not modifiable"))//
			.with("element", ModelTypes.Value.forType(QonfigElement.class),
				m -> SettableValue.of(QonfigElement.class, element, "Not modifiable"))//
			.build();
		ExpressoEnv env = sourceEnv.with(models, null);

		try {
			return new JavaExpressoParser().parse(parseText)//
				.evaluate(ModelTypes.Value.forType(type), env)//
				.apply(env.getModels().createInstance(ObservableModelSet.buildExternal(ObservableModelSet.JAVA_NAME_CHECKER).build(),
					Observable.empty()))//
				.get();
		} catch (ExpressoParseException e) {
			throw new QonfigInterpretationException(e);
		}
	}

	private void initElementModels(ExpressoQIS session, ExpressoQIS parent) throws QonfigInterpretationException {
		QonfigElementOrAddOn withSuppliedModels=theToolkit.getAddOn(SuppliedModelOwner.WITH_ELEMENT_MODELS);
		if (withSuppliedModels.isAssignableFrom(session.getType())) {
			session.put(ExpressoQIS.MODEL_VALUE_OWNER_PROP, SuppliedModelOwner.of(session.getType(), session, theToolkit));
			ObservableModelSet.WrappedBuilder builder = session.getExpressoEnv().getModels().wrap();
			ClassMap<Map<SuppliedModelValue<?, ?>, ExpressoQIS.SatisfierHolder<?, ?, ?, ?>>> satisfierMap = new ClassMap<>();
			session.put(ExpressoQIS.SATISFIERS_KEY, satisfierMap);
			builder.with(SuppliedModelValue.SATISFIER_PLACEHOLDER_NAME,
				new ValueContainer<SettableValue<?>, SettableValue<Satisfier>>() {
				@Override
				public ModelInstanceType<SettableValue<?>, SettableValue<Satisfier>> getType() {
					return SuppliedModelValue.SATISFIER_PLACEHOLDER.getType();
				}

				@Override
				public SettableValue<Satisfier> get(ModelSetInstance models) {
					return new ExpressoQIS.OneTimeSettableValue<>(TypeTokens.get().of(Satisfier.class));
				}
			});
		}
	}
}
