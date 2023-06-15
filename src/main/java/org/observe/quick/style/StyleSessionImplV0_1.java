package org.observe.quick.style;

import java.util.Collections;
import java.util.Set;

import org.observe.expresso.ExpressoQIS;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.config.SpecialSessionImplementation;

/** Provides {@link StyleQIS} special sessions */
public class StyleSessionImplV0_1 implements SpecialSessionImplementation<StyleQIS> {
	/** The name of the Quick-Style toolkit */
	public static final String NAME = "Quick-Style";
	/** The supported version of the Quick-Style toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	private QonfigToolkit theToolkit;
	private QuickTypeStyle.TypeStyleSet theStyleTypes;

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public Class<StyleQIS> getProvidedAPI() {
		return StyleQIS.class;
	}

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return Collections.singleton(ExpressoQIS.class);
	}

	/**
	 * Configures this session implementation with a style set to use in place of a fresh one
	 *
	 * @param styleSet The style set to use
	 * @return This session implementation
	 */
	public StyleSessionImplV0_1 withStyleTypes(QuickTypeStyle.TypeStyleSet styleSet) {
		theStyleTypes = styleSet;
		return this;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
		theToolkit = toolkit;
	}

	@Override
	public StyleQIS viewOfRoot(CoreSession coreSession, StyleQIS source) throws QonfigInterpretationException {
		if (coreSession.get(StyleQIS.STYLE_SET_PROP) == null) {
			if (theStyleTypes == null)
				theStyleTypes = new QuickTypeStyle.TypeStyleSet();
			coreSession.put(StyleQIS.STYLE_SET_PROP, theStyleTypes);
		}
		StyleQIS qis = new StyleQIS(coreSession, theToolkit);
		checkStyled(qis);
		return qis;
	}

	@Override
	public StyleQIS viewOfChild(StyleQIS parent, CoreSession coreSession) throws QonfigInterpretationException {
		StyleQIS qis = new StyleQIS(coreSession, theToolkit);
		checkStyled(qis);
		return qis;
	}

	@Override
	public StyleQIS parallelView(StyleQIS parallel, CoreSession coreSession) {
		return new StyleQIS(coreSession, theToolkit);
	}

	private void checkStyled(StyleQIS qis) {
		boolean styled = qis.getElement().isInstance(theToolkit.getAddOn(QuickTypeStyle.STYLED));
		qis.put(StyleQIS.STYLED_PROP, styled);
	}

	@Override
	public void postInitRoot(StyleQIS session, StyleQIS source) throws QonfigInterpretationException {
		initStyleData(session, null);
	}

	@Override
	public void postInitChild(StyleQIS session, StyleQIS parent) throws QonfigInterpretationException {
		initStyleData(session, parent);
	}

	@Override
	public void postInitParallel(StyleQIS session, StyleQIS parallel) throws QonfigInterpretationException {
	}

	private void initStyleData(StyleQIS session, StyleQIS parentSession) throws QonfigInterpretationException {
		// This is all handled in interpretation now
		// if (!session.isStyled())
		// return;
		// ExpressoQIS exS = session.as(ExpressoQIS.class);
		//
		// // Parse style values, if any
		// session.put(StyleQIS.STYLE_ELEMENT, session.getElement());
		// List<QuickStyleValue<?>> declared = null;
		// for (StyleValues sv : session.getWrapped().interpretChildren("style", StyleValues.class)) {
		// sv.init();
		// if (declared == null)
		// declared = new ArrayList<>();
		// declared.addAll(sv);
		// }
		// if (declared == null)
		// declared = Collections.emptyList();
		// Collections.sort(declared);
		//
		// // Find parent
		// QuickInterpretedStyle parent = parentSession == null ? null : parentSession.getStyle();
		// // Create QuickInterpretedStyle and put into session
		// session.put(StyleQIS.STYLE_PROP, new QuickInterpretedStyle(Collections.unmodifiableList(declared), parent, session.getStyleSheet(),
		// session.getElement(), exS, theToolkit));
	}
}
