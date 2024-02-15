package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.Set;

import org.observe.expresso.ExpressoInterpretationException;
import org.qommons.BreakpointHere;
import org.qommons.Version;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** Implementation of the Expresso-Debug toolkit */
public class ExpressoDebugV0_1 implements QonfigInterpretation {
	/** The name of the toolkit */
	public static final String NAME = "Expresso-Debug";
	/** The version of the toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	/** {@link #NAME} and {@link #VERSION} combined */
	public static final String DEBUG = "Expresso-Debug v0.1";

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
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
		interpreter.createWith("debug-value", DebugValue.Def.class, ExAddOn.creator(DebugValue.Def::new));
		return interpreter;
	}

	/** The &lt;debug-value> add-on */
	public static class DebugValue extends ExAddOn.Abstract<ExElement> {
		/** The type of the breakpoint to catch */
		public enum BreakType {
			/** Catches a break point when the value is being compiled */
			compile,
			/** Catches a break point when the value is being interpreted */
			interpret,
			/** Catches a break point when the value is being instantiated */
			instantiate
		}

		/** Definition for a {@link DebugValue} */
		@ExElementTraceable(toolkit = DEBUG, qonfigType = "debug-value", interpretation = Interpreted.class, instance = DebugValue.class)
		public static class Def extends ExAddOn.Def.Abstract<ExElement, DebugValue> {
			private BreakType theBreakType;

			/**
			 * @param type The Qonfig type of this add-on
			 * @param element The element to break for
			 */
			public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
				super(type, element);
			}

			/** @return The type of breakpoint to catch */
			@QonfigAttributeGetter("break-on")
			public BreakType getBreakType() {
				return theBreakType;
			}

			@Override
			public void preUpdate(ExpressoQIS session, ExElement.Def<?> addOnElement) throws QonfigInterpretationException {
				if (theBreakType == BreakType.compile)
					BreakpointHere.breakpoint();
				super.preUpdate(session, addOnElement);
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
				super.update(session, element);
				String breakType = session.getAttributeText("break-on");
				try {
					theBreakType = theBreakType == null ? null : BreakType.valueOf(breakType);
				} catch (IllegalArgumentException e) {
					element.reporting().error("Unrecognized break type: " + breakType, e);
				}
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
				return new Interpreted(this, element);
			}
		}

		/** Interpretation for a {@link DebugValue} */
		public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, DebugValue> {
			Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
				super(definition, element);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public void preUpdate(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
				if (getDefinition().getBreakType() == BreakType.interpret)
					BreakpointHere.breakpoint();
				super.preUpdate(element);
			}

			@Override
			public Class<DebugValue> getInstanceType() {
				return DebugValue.class;
			}

			@Override
			public DebugValue create(ExElement element) {
				return new DebugValue(element, getDefinition().getBreakType());
			}
		}

		private BreakType theBreakType;

		DebugValue(ExElement element, BreakType breakType) {
			super(element);
			theBreakType = breakType;
		}

		@Override
		public Class<Interpreted> getInterpretationType() {
			return Interpreted.class;
		}

		@Override
		public void preInstantiate() {
			if (theBreakType == BreakType.instantiate)
				BreakpointHere.breakpoint();
			super.preInstantiate();
		}
	}
}
