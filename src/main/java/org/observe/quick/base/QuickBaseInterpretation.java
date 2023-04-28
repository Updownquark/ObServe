package org.observe.quick.base;

import java.util.Set;

import org.observe.expresso.ExpressoQIS;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickWidget;
import org.observe.quick.style.StyleQIS;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** {@link QonfigInterpretation} for the Quick-Base toolkit */
public class QuickBaseInterpretation implements QonfigInterpretation {
	/** The name of the toolkit */
	public static final String TOOLKIT_NAME = "Quick-Base";

	/** The version of the toolkit */
	public static final Version TOOLKIT_VERSION = new Version(0, 1, 0);

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class, StyleQIS.class);
	}

	@Override
	public String getToolkitName() {
		return TOOLKIT_NAME;
	}

	@Override
	public Version getVersion() {
		return TOOLKIT_VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
	}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(Builder interpreter) {
		interpreter.createWith("box", QuickBox.Def.class, session -> QuickCoreInterpretation.interpretQuick(session, QuickBox.Def::new));
		interpreter.createWith("label", QuickLabel.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickLabel.Def::new));
		interpreter.createWith("inline", InlineLayout.Def.class, session -> QuickCoreInterpretation.interpretAddOn(session,
			(p, ao) -> new InlineLayout.Def(ao, (QuickBox.Def<?>) p)));
		interpreter.createWith("text-field", QuickTextField.Def.class,
			session -> QuickCoreInterpretation.interpretQuick(session, QuickTextField.Def::new));
		interpreter.createWith("field", QuickField.Def.class,
			session -> QuickCoreInterpretation.interpretAddOn(session, (p, ao) -> new QuickField.Def(ao, (QuickWidget.Def<?>) p)));
		return interpreter;
	}
}
