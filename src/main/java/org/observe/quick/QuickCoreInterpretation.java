package org.observe.quick;

import java.util.Set;
import java.util.function.BiFunction;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** {@link QonfigInterpretation} for the Quick-Core toolkit */
public class QuickCoreInterpretation implements QonfigInterpretation {
	/** The name of the Quick-Core toolkit */
	public static final String NAME = "Quick-Core";
	/** The supported version of the Quick-Core toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

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
		interpreter.createWith("window", QuickWindow.Def.class, session -> interpretAddOn(session, (p, ao) -> new QuickWindow.Def(ao, p)));
		interpreter.createWith(QuickBorder.LineBorder.LINE_BORDER, QuickBorder.LineBorder.Def.class,
			ExElement.creator(QuickBorder.LineBorder.Def::new));
		interpreter.createWith(QuickBorder.TitledBorder.TITLED_BORDER, QuickBorder.TitledBorder.Def.class,
			ExElement.creator(QuickBorder.TitledBorder.Def::new));

		interpreter.createWith(QuickMouseListener.QuickMouseClickListener.ON_MOUSE_CLICK,
			QuickMouseListener.QuickMouseClickListener.Def.class, ExElement.creator(QuickMouseListener.QuickMouseClickListener.Def::new));
		interpreter.createWith(QuickMouseListener.QuickMousePressedListener.ON_MOUSE_PRESSED,
			QuickMouseListener.QuickMousePressedListener.Def.class, ExElement.creator(QuickMouseListener.QuickMousePressedListener.Def::new));
		interpreter.createWith(QuickMouseListener.QuickMouseReleasedListener.ON_MOUSE_RELEASED,
			QuickMouseListener.QuickMouseReleasedListener.Def.class, ExElement.creator(QuickMouseListener.QuickMouseReleasedListener.Def::new));
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
