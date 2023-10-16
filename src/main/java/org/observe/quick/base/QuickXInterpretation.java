package org.observe.quick.base;

import java.util.Set;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** {@link QonfigInterpretation} for the Quick-X toolkit */
public class QuickXInterpretation implements QonfigInterpretation {
	/** The name of the toolkit */
	public static final String NAME = "Quick-X";

	/** The version of the toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	/** {@link #NAME} and {@link #VERSION} combined */
	public static final String X = "Quick-X v0.1";

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class);
	}

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
	}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(Builder interpreter) {
		interpreter.createWith(CollapsePane.COLLAPSE_PANE, CollapsePane.Def.class, ExElement.creator(CollapsePane.Def::new));
		interpreter.createWith(QuickTreeTable.TREE_TABLE, QuickTreeTable.Def.class, ExElement.creator(QuickTreeTable.Def::new));
		interpreter.createWith(QuickComboButton.COMBO_BUTTON, QuickComboButton.Def.class, ExElement.creator(QuickComboButton.Def::new));
		interpreter.createWith(QuickMultiSlider.MULTI_SLIDER, QuickMultiSlider.Def.class, ExElement.creator(QuickMultiSlider.Def::new));
		interpreter.createWith(QuickMultiSlider.SLIDER_HANDLE_RENDERER, QuickMultiSlider.SliderHandleRenderer.Def.class,
			ExElement.creator(QuickMultiSlider.SliderHandleRenderer.Def::new));
		interpreter.createWith(QuickMultiSlider.SLIDER_BG_RENDERER, QuickMultiSlider.SliderBgRenderer.Def.class,
			ExElement.creator(QuickMultiSlider.SliderBgRenderer.Def::new));

		return interpreter;
	}
}
