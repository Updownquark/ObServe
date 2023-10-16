package org.observe.quick;

import java.awt.Image;
import java.awt.MediaTracker;
import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.function.BiFunction;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.TypeConversionException;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.util.TypeTokens;
import org.observe.util.swing.ObservableSwingUtils;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.ex.ExceptionHandler;
import org.qommons.ex.NeverThrown;

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
	public Builder configureInterpreter(Builder interpreter) {
		interpreter.createWith(QuickDocument.QUICK, QuickDocument.Def.class, ExElement.creator(QuickDocument.Def::new));
		interpreter.createWith(QuickDocument.QuickHeadSection.HEAD, QuickDocument.QuickHeadSection.Def.class,
			session -> new QuickDocument.QuickHeadSection.Def((QuickDocument.Def) session.getElementRepresentation(),
				session.getFocusType()));
		interpreter.createWith(QuickAbstractWindow.ABSTRACT_WINDOW, QuickAbstractWindow.Def.Default.class,
			session -> interpretAddOn(session, (p, ao) -> new QuickAbstractWindow.Def.Default<>(ao, p)));
		interpreter.createWith(QuickWindow.WINDOW, QuickWindow.Def.class,
			session -> interpretAddOn(session, (p, ao) -> new QuickWindow.Def(ao, p)));
		interpreter.createWith(QuickBorder.LineBorder.LINE_BORDER, QuickBorder.LineBorder.Def.class,
			ExElement.creator(QuickBorder.LineBorder.Def::new));
		interpreter.createWith(QuickBorder.TitledBorder.TITLED_BORDER, QuickBorder.TitledBorder.Def.class,
			ExElement.creator(QuickBorder.TitledBorder.Def::new));

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

	/**
	 * Evaluates an icon in Quick
	 *
	 * @param expression The expression to parse
	 * @param env The expresso environment in which to parse the expression
	 * @param sourceDocument The location of the document that the icon source may be relative to
	 * @return The ModelValueSynth to produce the icon value
	 * @throws ExpressoInterpretationException If the icon could not be evaluated
	 */
	public static InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> evaluateIcon(CompiledExpression expression,
		InterpretedExpressoEnv env, String sourceDocument) throws ExpressoInterpretationException {
		if (expression != null) {
			ExceptionHandler.Double<ExpressoInterpretationException, TypeConversionException, NeverThrown, NeverThrown> tce = ExceptionHandler
				.holder2();
			InterpretedValueSynth<SettableValue<?>, SettableValue<Icon>> iconV = expression.interpret(ModelTypes.Value.forType(Icon.class),
				env, tce);
			if (iconV != null)
				return iconV;
			InterpretedValueSynth<SettableValue<?>, SettableValue<Image>> imageV = expression
				.interpret(ModelTypes.Value.forType(Image.class), env, tce.clear());
			if (imageV != null)
				return imageV.map(ModelTypes.Value.forType(Icon.class), mvi -> mvi
					.map(sv -> SettableValue.asSettable(sv.map(img -> img == null ? null : new ImageIcon(img)), __ -> "Unsettable")));
			InterpretedValueSynth<SettableValue<?>, SettableValue<URL>> urlV = expression.interpret(ModelTypes.Value.forType(URL.class),
				env, tce.clear());
			if (urlV != null)
				return urlV.map(ModelTypes.Value.forType(Icon.class), mvi -> mvi
					.map(sv -> SettableValue.asSettable(sv.map(url -> url == null ? null : new ImageIcon(url)), __ -> "unsettable")));
			InterpretedValueSynth<SettableValue<?>, SettableValue<String>> stringV = expression
				.interpret(ModelTypes.Value.forType(String.class), env, tce.clear());
			if (stringV != null) {
				return stringV.map(ModelTypes.Value.forType(Icon.class), mvi -> mvi.map(sv -> SettableValue.asSettable(sv.map(loc -> {
					if (loc == null)
						return null;
					String relLoc;
					try {
						relLoc = QommonsConfig.resolve(loc, sourceDocument);
					} catch (IOException e) {
						env.reporting().at(expression.getFilePosition())
						.error("Could not resolve icon location '" + loc + "' relative to document " + sourceDocument);
						e.printStackTrace();
						return null;
					}
					Icon icon = ObservableSwingUtils.getFixedIcon(null, relLoc, 16, 16);
					if (icon == null)
						icon = ObservableSwingUtils.getFixedIcon(null, loc, 16, 16);
					if (icon == null)
						env.reporting().at(expression.getFilePosition()).error("Icon file not found: '" + loc);
					else if (icon instanceof ImageIcon && ((ImageIcon) icon).getImageLoadStatus() == MediaTracker.ERRORED)
						env.reporting().at(expression.getFilePosition()).error("Icon file could not be loaded: '" + loc);
					return icon;
				}), __ -> "unsettable")));
			}
			env.reporting().warn("Cannot evaluate '" + expression + "' as an icon");
			return InterpretedValueSynth.literalValue(TypeTokens.get().of(Icon.class), null, "Icon not provided");
		} else
			return InterpretedValueSynth.literalValue(TypeTokens.get().of(Icon.class), null, "None provided");
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
