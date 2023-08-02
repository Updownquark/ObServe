package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.Set;

import org.qommons.BreakpointHere;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigInterpreterCore.QonfigValueModifier;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** Implementation of the Expresso-Debug toolkit */
public class ExpressoDebugV0_1 implements QonfigInterpretation {
	/** The name of the toolkit */
	public static final String TOOLKIT_NAME = "Expresso-Debug";
	/** The version of the toolkit */
	public static final Version TOOLKIT_VERSION = new Version(0, 1, 0);

	@Override
	public String getToolkitName() {
		return TOOLKIT_NAME;
	}

	@Override
	public Version getVersion() {
		return TOOLKIT_VERSION;
	}

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return Collections.singleton(ExpressoQIS.class);
	}

	@Override
	public void init(QonfigToolkit toolkit) {
	}

	@Override
	public Builder configureInterpreter(Builder interpreter) {
		interpreter.modifyWith("debug-value",
			(Class<ModelValueElement.CompiledSynth<?, ?>>) (Class<?>) ModelValueElement.CompiledSynth.class,
			new QonfigValueModifier<ModelValueElement.CompiledSynth<?, ?>>() {
			@Override
			public Object prepareSession(CoreSession session) throws QonfigInterpretationException {
				String breakpointType = session.getAttributeText("break-on");
				if ("compile".equals(breakpointType))
					BreakpointHere.breakpoint();
				return null;
			}

			@Override
			public ModelValueElement.CompiledSynth<?, ?> modifyValue(ModelValueElement.CompiledSynth<?, ?> value, CoreSession session,
				Object prepared) throws QonfigInterpretationException {
				String breakpointType = session.getAttributeText("break-on");
				if (breakpointType == null)
					return value;
				switch (breakpointType) {
				case "compile":
					return value;
				case "interpret":
					value.onInterpretation(interpreted -> {
						BreakpointHere.breakpoint();
					});
					return value;
				case "instantiate":
						// TODO
						throw new QonfigInterpretationException("Instantiation breakpoint is no longer implemented",
							session.reporting().getPosition(), 0);
				default:
					throw new QonfigInterpretationException("Unrecognized break-on value: " + breakpointType,
						session.reporting().getPosition(), 0);
				}
			}
		});
		return interpreter;
	}
}
