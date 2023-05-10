package org.observe.quick;

import java.util.Set;
import java.util.function.BiFunction;

import org.observe.expresso.ExpressoQIS;
import org.observe.quick.style.StyleQIS;
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
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class, StyleQIS.class);
	}

	@Override
	public Builder configureInterpreter(Builder interpreter) {
		interpreter.createWith("quick", QuickDocument2.Def.class, session -> new QuickDocument2.Def(null, session.getElement()));
		interpreter.createWith("head", QuickDocument2.QuickHeadSection.Def.class,
			session -> new QuickDocument2.QuickHeadSection.Def((QuickDocument2.Def) session.get(QuickElement.SESSION_QUICK_ELEMENT),
				session.getElement()));
		interpreter.createWith("window", QuickWindow.Def.class,
			session -> interpretAddOn(session, (p, ao) -> new QuickWindow.Def(ao, (QuickDocument2.Def) p)));
		interpreter.createWith("line-border", QuickBorder.LineBorder.Def.class,
			session -> interpretQuick(session, QuickBorder.LineBorder.Def::new));
		interpreter.createWith("titled-border", QuickBorder.TitledBorder.Def.class,
			session -> interpretQuick(session, QuickBorder.TitledBorder.Def::new));

		interpreter.createWith("on-click", QuickMouseListener.QuickMouseButtonListener.Def.class, session -> interpretQuick(session,
			(p, el) -> new QuickMouseListener.QuickMouseButtonListener.Def(p, el, QuickMouseListener.MouseButtonEventType.Click)));
		interpreter.createWith("on-mouse-press", QuickMouseListener.QuickMouseButtonListener.Def.class, session -> interpretQuick(session,
			(p, el) -> new QuickMouseListener.QuickMouseButtonListener.Def(p, el, QuickMouseListener.MouseButtonEventType.Press)));
		interpreter.createWith("on-mouse-release", QuickMouseListener.QuickMouseButtonListener.Def.class, session -> interpretQuick(session,
			(p, el) -> new QuickMouseListener.QuickMouseButtonListener.Def(p, el, QuickMouseListener.MouseButtonEventType.Release)));
		interpreter.createWith("on-mouse-move", QuickMouseListener.QuickMouseMoveListener.Def.class, session -> interpretQuick(session,
			(p, el) -> new QuickMouseListener.QuickMouseMoveListener.Def(p, el, QuickMouseListener.MouseMoveEventType.Move)));
		interpreter.createWith("on-mouse-enter", QuickMouseListener.QuickMouseMoveListener.Def.class, session -> interpretQuick(session,
			(p, el) -> new QuickMouseListener.QuickMouseMoveListener.Def(p, el, QuickMouseListener.MouseMoveEventType.Enter)));
		interpreter.createWith("on-mouse-exit", QuickMouseListener.QuickMouseMoveListener.Def.class, session -> interpretQuick(session,
			(p, el) -> new QuickMouseListener.QuickMouseMoveListener.Def(p, el, QuickMouseListener.MouseMoveEventType.Exit)));
		interpreter.createWith("on-scroll", QuickMouseListener.QuickScrollListener.Def.class,
			session -> interpretQuick(session, QuickMouseListener.QuickScrollListener.Def::new));
		interpreter.createWith("on-type", QuickKeyListener.QuickKeyTypedListener.Def.class,
			session -> interpretQuick(session, QuickKeyListener.QuickKeyTypedListener.Def::new));
		interpreter.createWith("on-key-press", QuickKeyListener.QuickKeyCodeListener.Def.class,
			session -> interpretQuick(session, (p, el) -> new QuickKeyListener.QuickKeyCodeListener.Def(p, el, true)));
		interpreter.createWith("on-key-release", QuickKeyListener.QuickKeyCodeListener.Def.class,
			session -> interpretQuick(session, (p, el) -> new QuickKeyListener.QuickKeyCodeListener.Def(p, el, false)));
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
	public static <E extends QuickElement, D extends QuickElement.Def<E>> D interpretQuick(AbstractQIS<?> session,
		BiFunction<QuickElement.Def<?>, QonfigElement, D> element) {
		return element.apply((QuickElement.Def<?>) session.get(QuickElement.SESSION_QUICK_ELEMENT), session.getElement());
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
	public static <E extends QuickElement, AO extends QuickAddOn<E>, D extends QuickAddOn.Def<E, AO>> D interpretAddOn(
		AbstractQIS<?> session, BiFunction<QuickElement.Def<?>, QonfigAddOn, D> addOn) {
		return addOn.apply((QuickElement.Def<?>) session.get(QuickElement.SESSION_QUICK_ELEMENT), (QonfigAddOn) session.getFocusType());
	}
}