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

	public static class DebugValue extends ExAddOn.Abstract<ExElement> {
		public enum BreakType {
			compile, interpret, instantiate
		}

		@ExElementTraceable(toolkit = DEBUG, qonfigType = "debug-value", interpretation = Interpreted.class, instance = DebugValue.class)
		public static class Def extends ExAddOn.Def.Abstract<ExElement, DebugValue> {
			private BreakType theBreakType;

			public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
				super(type, element);
			}

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

		public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, DebugValue> {
			public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
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

		public DebugValue(ExElement element, BreakType breakType) {
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
