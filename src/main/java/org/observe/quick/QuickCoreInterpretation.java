package org.observe.quick;

import java.util.Set;

import org.observe.expresso.ExpressoQIS;
import org.observe.quick.style.StyleQIS;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

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
		interpreter.createWith("quick", QuickDocument2.Def.class, session -> new QuickDocument2.Def(session));
		interpreter.createWith("head", QuickDocument2.QuickHeadSection.Def.class,
			session -> new QuickDocument2.QuickHeadSection.Def(session));
		interpreter.createWith("window", QuickWindow.Def.class, session -> new QuickWindow.Def());
		return interpreter;
	}
}
