package org.observe.quick.base;

import java.util.Set;

import org.observe.expresso.ExpressoQIS;
import org.observe.quick.style.StyleQIS;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

public class QuickBaseInterpretation implements QonfigInterpretation {
	public static final String TOOLKIT_NAME="Quick-Base";

	public static final Version TOOLKIT_VERSION=new Version(0, 1, 0);

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
		interpreter.createWith("box", QuickBox.Def.class, session -> new QuickBox.Def<>(session));
		interpreter.createWith("label", QuickLabel.Def.class, session -> new QuickLabel.Def<>(session));
		interpreter.createWith("inline", InlineLayout.Def.class, session -> new InlineLayout.Def(session));
		interpreter.createWith("text-field", QuickTextField.Def.class, session -> new QuickTextField.Def<>(session));
		// TODO Field
		return interpreter;
	}
}
