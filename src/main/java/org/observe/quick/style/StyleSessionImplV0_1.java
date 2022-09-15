package org.observe.quick.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.quick.style.QuickStyle.StyleValues;
import org.observe.util.TypeTokens;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.config.SpecialSessionImplementation;

/** Provides {@link StyleQIS} special sessions */
public class StyleSessionImplV0_1 implements SpecialSessionImplementation<StyleQIS> {
	/** The name of the Quick-Core toolkit */
	public static final String NAME = "Quick-Core";
	/** The supported version of the Quick-Core toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	private QonfigToolkit theToolkit;

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

	@Override
	public void init(QonfigToolkit toolkit) {
		theToolkit = toolkit;
	}

	@Override
	public StyleQIS viewOfRoot(CoreSession coreSession, StyleQIS source) throws QonfigInterpretationException {
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
		boolean styled = qis.getElement().isInstance(theToolkit.getAddOn(QuickStyleType.STYLED));
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
		if (!session.isStyled())
			return;
		ExpressoQIS exS = session.as(ExpressoQIS.class);

		ObservableModelSet.WrappedBuilder builder = exS.getExpressoEnv().getModels().wrap();
		builder.with(StyleQIS.PARENT_MODEL_NAME, new ValueContainer<SettableValue<?>, SettableValue<ModelSetInstance>>() {
			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<ModelSetInstance>> getType() {
				return ModelTypes.Value.forType(ModelSetInstance.class);
			}

			@Override
			public SettableValue<ModelSetInstance> get(ModelSetInstance models) {
				return new ExpressoQIS.OneTimeSettableValue<>(TypeTokens.get().of(ModelSetInstance.class));
			}
		});
		exS.setModels(builder.build(), null);

		// Parse style values, if any
		session.put(StyleQIS.STYLE_ELEMENT, session.getElement());
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
		session.put(StyleQIS.STYLE_PROP, new QuickElementStyle(Collections.unmodifiableList(declared), parent, session.getStyleSheet(),
			session.getElement(), exS, theToolkit));
	}
}
