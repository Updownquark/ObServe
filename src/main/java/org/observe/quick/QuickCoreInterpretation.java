package org.observe.quick;

import java.awt.Image;
import java.text.ParseException;
import java.util.Set;
import java.util.function.BiFunction;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.NonStructuredParser;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.style.QuickStyleUtils;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

import com.google.common.reflect.TypeToken;

/** {@link QonfigInterpretation} for the Quick-Core toolkit */
public class QuickCoreInterpretation implements QonfigInterpretation {
	/** The name of the Quick-Core toolkit */
	public static final String NAME = "Quick-Core";
	/** The supported version of the Quick-Core toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	/** {@link #NAME} and {@link #VERSION} combined */
	public static final String CORE = "Quick-Core v0.1";

	@SuppressWarnings("unused")
	private QonfigToolkit theCoreToolkit;

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
		theCoreToolkit = toolkit;
	}

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class);
	}

	@Override
	public Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter) {
		// General setup
		interpreter.modifyWith(QuickDocument.QUICK, QuickDocument.Def.class,
			new QonfigInterpreterCore.QonfigValueModifier<QuickDocument.Def>() {
			@Override
			public Object prepareSession(CoreSession session) throws QonfigInterpretationException {
				ExpressoQIS exS = session.as(ExpressoQIS.class);
				CompiledExpressoEnv env = exS.getExpressoEnv();
				env = env.withNonStructuredParser(Image.class, new ImageParser());
				exS.setExpressoEnv(env);
				return null;
			}

			@Override
			public QuickDocument.Def modifyValue(QuickDocument.Def value, CoreSession session, Object prepared)
				throws QonfigInterpretationException {
				return value;
			}
		});

		interpreter.createWith(QuickDocument.QUICK, QuickDocument.Def.class, ExElement.creator(QuickDocument.Def::new));
		interpreter.createWith(QuickAbstractWindow.ABSTRACT_WINDOW, QuickAbstractWindow.Def.Default.class,
			session -> interpretAddOn(session, (p, ao) -> new QuickAbstractWindow.Def.Default<>(ao, p)));
		interpreter.createWith(QuickWindow.WINDOW, QuickWindow.Def.class,
			session -> interpretAddOn(session, (p, ao) -> new QuickWindow.Def(ao, p)));
		interpreter.createWith(QuickBorder.LineBorder.LINE_BORDER, QuickBorder.LineBorder.Def.class,
			ExElement.creator(QuickBorder.LineBorder.Def::new));
		interpreter.createWith(QuickBorder.TitledBorder.TITLED_BORDER, QuickBorder.TitledBorder.Def.class,
			ExElement.creator(QuickBorder.TitledBorder.Def::new));
		interpreter.createWith(Iconized.ICONIZED, Iconized.Def.class, ExAddOn.creator(Iconized.Def::new));

		interpreter.createWith(QuickEventListener.EventFilter.EVENT_FILTER, QuickEventListener.EventFilter.Def.class,
			ExElement.creator(QuickEventListener.EventFilter.Def::new));
		interpreter.createWith(QuickMouseListener.QuickMouseClickListener.ON_MOUSE_CLICK,
			QuickMouseListener.QuickMouseClickListener.Def.class, ExElement.creator(QuickMouseListener.QuickMouseClickListener.Def::new));
		interpreter.createWith(QuickMouseListener.QuickMousePressedListener.ON_MOUSE_PRESSED,
			QuickMouseListener.QuickMousePressedListener.Def.class,
			ExElement.creator(QuickMouseListener.QuickMousePressedListener.Def::new));
		interpreter.createWith(QuickMouseListener.QuickMouseReleasedListener.ON_MOUSE_RELEASED,
			QuickMouseListener.QuickMouseReleasedListener.Def.class,
			ExElement.creator(QuickMouseListener.QuickMouseReleasedListener.Def::new));
		interpreter.createWith(QuickMouseListener.MouseMoveEventType.Move.elementName, QuickMouseListener.QuickMouseMoveListener.Def.class,
			session -> new QuickMouseListener.QuickMouseMoveListener.Def(session.as(ExpressoQIS.class).getElementRepresentation(),
				session.getFocusType(), QuickMouseListener.MouseMoveEventType.Move));
		interpreter.createWith(QuickMouseListener.MouseMoveEventType.Enter.elementName, QuickMouseListener.QuickMouseMoveListener.Def.class,
			session -> new QuickMouseListener.QuickMouseMoveListener.Def(session.as(ExpressoQIS.class).getElementRepresentation(),
				session.getFocusType(), QuickMouseListener.MouseMoveEventType.Enter));
		interpreter.createWith(QuickMouseListener.MouseMoveEventType.Exit.elementName, QuickMouseListener.QuickMouseMoveListener.Def.class,
			session -> new QuickMouseListener.QuickMouseMoveListener.Def(session.as(ExpressoQIS.class).getElementRepresentation(),
				session.getFocusType(), QuickMouseListener.MouseMoveEventType.Exit));
		interpreter.createWith(QuickMouseListener.QuickScrollListener.SCROLL_LISTENER, QuickMouseListener.QuickScrollListener.Def.class,
			ExElement.creator(QuickMouseListener.QuickScrollListener.Def::new));
		interpreter.createWith(QuickKeyListener.QuickKeyTypedListener.KEY_TYPED_LISTENER, QuickKeyListener.QuickKeyTypedListener.Def.class,
			ExElement.creator(QuickKeyListener.QuickKeyTypedListener.Def::new));
		interpreter.createWith(QuickKeyListener.QuickKeyCodeListener.KEY_PRESSED_LISTENER, QuickKeyListener.QuickKeyCodeListener.Def.class,
			session -> new QuickKeyListener.QuickKeyCodeListener.Def(session.as(ExpressoQIS.class).getElementRepresentation(),
				session.getFocusType(), true));
		interpreter.createWith(QuickKeyListener.QuickKeyCodeListener.KEY_RELEASED_LISTENER, QuickKeyListener.QuickKeyCodeListener.Def.class,
			session -> new QuickKeyListener.QuickKeyCodeListener.Def(session.as(ExpressoQIS.class).getElementRepresentation(),
				session.getFocusType(), false));

		interpreter.createWith("renderer", QuickRenderer.Def.class,
			session -> interpretAddOn(session, (p, ao) -> new QuickRenderer.Def(ao, (QuickWidget.Def<?>) p)));
		return interpreter;
	}

	static class ImageParser extends NonStructuredParser.Simple<Image> {
		public ImageParser() {
			super(TypeTokens.get().of(Image.class), TypeTokens.get().of(Image.class));
		}

		@Override
		public String getDescription() {
			return "Parses images for application icons";
		}

		@Override
		public boolean checkText(String text, InterpretedExpressoEnv env) {
			// The check here is to ensure there's a file extension that might be recognized. Allow up to 5 characters.
			int dotIdx = text.lastIndexOf('.');
			return dotIdx > 0 && dotIdx > text.length() - 5;
		}

		@Override
		protected <T2 extends Image> T2 parseValue(TypeToken<T2> type, String text, InterpretedExpressoEnv env) throws ParseException {
			return (T2) QuickStyleUtils.parseIcon(text, env);
		}
	}

	/**
	 * Evaluates an icon in Quick
	 *
	 * @param expression The expression to parse
	 * @param element The interpreted element containing the environment in which to parse the expression
	 * @param sourceDocument The location of the document that the icon source may be relative to
	 * @return The ModelValueSynth to produce the icon value
	 * @throws ExpressoInterpretationException If the icon could not be evaluated
	 */
	public static InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> evaluateIcon(CompiledExpression expression,
		ExElement.Interpreted<?> element, String sourceDocument) throws ExpressoInterpretationException {
		return QuickStyleUtils.evaluateIcon(expression, element.getEnvironmentFor(expression));
	}

	/**
	 * Utility for supporting interpretation of elements
	 *
	 * @param <E> The type of the element to interpret
	 * @param <D> The type of the element definition to interpret
	 * @param session The session to interpret for
	 * @param element Produces the definition
	 * @return The definition
	 */
	public static <E extends ExElement, D extends ExElement.Def<E>> D interpretQuick(AbstractQIS<?> session,
		BiFunction<ExElement.Def<?>, QonfigElement, D> element) {
		return element.apply((ExElement.Def<?>) session.getElementRepresentation(), session.getElement());
	}

	/**
	 * Utility for supporting interpretation of add-ons
	 *
	 * @param <E> The type of the add-on to interpret
	 * @param <D> The type of the add-on definition to interpret
	 * @param session The session to interpret for
	 * @param addOn Produces the definition
	 * @return The definition
	 */
	public static <E extends ExElement, AO extends ExAddOn<E>, D extends ExAddOn.Def<E, AO>> D interpretAddOn(AbstractQIS<?> session,
		BiFunction<ExElement.Def<?>, QonfigAddOn, D> addOn) {
		return addOn.apply((ExElement.Def<?>) session.getElementRepresentation(), (QonfigAddOn) session.getFocusType());
	}
}
