package org.observe.quick;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ObservableModelSet;
import org.observe.quick.QuickCore.StyleValues;
import org.observe.quick.style.QuickElementStyle;
import org.observe.quick.style.QuickModelValue;
import org.observe.quick.style.QuickStyleSet;
import org.observe.quick.style.QuickStyleValue;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.config.SpecialSessionImplementation;

/** Provides {@link QuickQIS} special sessions */
public class QuickSessionImplV0_1 implements SpecialSessionImplementation<QuickQIS> {
	/** The name of the Quick-Core toolkit */
	public static final String NAME = "Quick-Core";
	/** The supported version of the Quick-Core toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	private QuickStyleSet theStyleSet;

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public Class<QuickQIS> getProvidedAPI() {
		return QuickQIS.class;
	}

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return Collections.singleton(ExpressoQIS.class);
	}

	@Override
	public void init(QonfigToolkit toolkit) {
		theStyleSet = new QuickStyleSet(toolkit);
	}

	@Override
	public QuickQIS viewOfRoot(CoreSession coreSession, QuickQIS source) throws QonfigInterpretationException {
		QuickQIS qis = new QuickQIS(coreSession, theStyleSet);
		checkStyled(qis);
		return qis;
	}

	@Override
	public QuickQIS viewOfChild(QuickQIS parent, CoreSession coreSession) throws QonfigInterpretationException {
		QuickQIS qis = new QuickQIS(coreSession, theStyleSet);
		checkStyled(qis);
		return qis;
	}

	@Override
	public QuickQIS parallelView(QuickQIS parallel, CoreSession coreSession) {
		return new QuickQIS(coreSession, theStyleSet);
	}

	private void checkStyled(QuickQIS qis) {
		boolean styled = qis.getElement().isInstance(theStyleSet.getStyled());
		qis.put(QuickQIS.STYLED_PROP, styled);
		qis.put(QuickQIS.WIDGET_PROP, styled && qis.getElement().isInstance(theStyleSet.getWidget()));
		if (styled) {
			qis.put(QuickQIS.MODEL_VALUES_PROP, new HashSet<>());
		} else
			qis.put(QuickQIS.MODEL_VALUES_PROP, Collections.emptySet());
	}

	@Override
	public void postInitRoot(QuickQIS session, QuickQIS source) throws QonfigInterpretationException {
		initStyleData(session, null);
	}

	@Override
	public void postInitChild(QuickQIS session, QuickQIS parent) throws QonfigInterpretationException {
		initStyleData(session, parent);
	}

	@Override
	public void postInitParallel(QuickQIS session, QuickQIS parallel) throws QonfigInterpretationException {
	}

	private void initStyleData(QuickQIS session, QuickQIS parentSession) throws QonfigInterpretationException {
		if (!session.isStyled())
			return;
		ExpressoQIS exS = session.as(ExpressoQIS.class);
		ObservableModelSet.WrappedBuilder builder = exS.getExpressoEnv().getModels().wrap();
		builder.withCustomValue(ExpressoQIS.PARENT_MODEL_NAME, ExpressoQIS.PARENT_MODEL);
		Collection<QuickModelValue<?>> modelValues = session.getStyleSet().getModelValues(session.getElement(), exS);
		Set<QuickModelValue<?>> mvs = (Set<QuickModelValue<?>>) session.get(QuickQIS.MODEL_VALUES_PROP);
		if (!modelValues.isEmpty()) {
			builder.withCustomValue(QuickModelValue.SATISFIER_PLACEHOLDER_NAME, QuickModelValue.SATISFIER_PLACEHOLDER);
			for (QuickModelValue<?> mv : modelValues) {
				builder.withCustomValue(mv.getName(), mv);
				mvs.add(mv);
			}
		}
		if (session.isWidget()) {
			ExpressoQIS modelSession = exS.forChildren("model").peekFirst();
			if (modelSession != null) {
				modelSession.setModels(builder, null);
				modelSession.interpret(ObservableModelSet.class);
			}
		}
		ObservableModelSet.Wrapped built = builder.build();
		exS.setModels(built, null);

		// Parse style values, if any
		session.put(QuickQIS.STYLE_ELEMENT, session.getElement());
		List<QuickStyleValue<?>> declared = null;
		for (StyleValues sv : session.getWrapped().interpretChildren("style", StyleValues.class)) {
			sv.init();
			if (declared == null)
				declared = new ArrayList<>();
			declared.addAll(sv);
		}
		if (declared == null)
			declared = Collections.emptyList();
		Collections.sort(declared);

		// Find parent
		QuickElementStyle parent = parentSession == null ? null : parentSession.getStyle();
		// Create QuickElementStyle and put into session
		session.put(QuickQIS.STYLE_PROP, new QuickElementStyle(session.getStyleSet(), Collections.unmodifiableList(declared), parent,
			session.getStyleSheet(), session.getElement(), exS));
	}
}
