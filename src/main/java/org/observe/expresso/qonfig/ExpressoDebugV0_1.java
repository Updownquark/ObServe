package org.observe.expresso.qonfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.qommons.BreakpointHere;
import org.qommons.Version;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElementOrAddOn;
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
		interpreter.createWith("debug-tag", DebugTag.Def.class, ExElement.creator(DebugTag.Def::new));
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
			private final List<DebugTag.Def> theTags;

			/**
			 * @param type The Qonfig type of this add-on
			 * @param element The element to break for
			 */
			public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
				super(type, element);
				theTags = new ArrayList<>();
			}

			@Override
			public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
				return (Set<Class<ExAddOn.Def<?, ?>>>) (Set<?>) Collections.singleton(ExModelAugmentation.Def.class);
			}

			/** @return The type of breakpoint to catch */
			@QonfigAttributeGetter("break-on")
			public BreakType getBreakType() {
				return theBreakType;
			}

			/** @return The tags of this debug value */
			@QonfigChildGetter("tag")
			public List<DebugTag.Def> getTags() {
				return Collections.unmodifiableList(theTags);
			}

			@Override
			public void preUpdate(ExpressoQIS session, ExElement.Def<?> addOnElement) throws QonfigInterpretationException {
				String breakType = session.getAttributeText("break-on");
				try {
					theBreakType = breakType == null ? null : BreakType.valueOf(breakType);
				} catch (IllegalArgumentException e) {
					addOnElement.reporting().error("Unrecognized break type: " + breakType, e);
				}
				if (theBreakType == BreakType.compile)
					BreakpointHere.breakpoint();
				super.preUpdate(session, addOnElement);
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
				super.update(session, element);
				element.syncChildren(DebugTag.Def.class, theTags, session.forChildren("tag"));
			}

			@Override
			public <E2 extends ExElement> Interpreted interpret(ExElement.Interpreted<E2> element) {
				return new Interpreted(this, element);
			}
		}

		/** Interpretation for a {@link DebugValue} */
		public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, DebugValue> {
			private final List<DebugTag.Interpreted> theTags;

			Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
				super(definition, element);
				theTags = new ArrayList<>();
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			/** @return The tags of this debug value */
			public List<DebugTag.Interpreted> getTags() {
				return Collections.unmodifiableList(theTags);
			}

			@Override
			public void update(ExElement.Interpreted<? extends ExElement> element) throws ExpressoInterpretationException {
				super.update(element);
				element.syncChildren(getDefinition().getTags(), theTags, def -> def.interpret(element), def -> def.update());
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
		private final List<DebugTag> theTags;

		DebugValue(ExElement element, BreakType breakType) {
			super(element);
			theBreakType = breakType;
			theTags = new ArrayList<>();
		}

		@Override
		public Class<Interpreted> getInterpretationType() {
			return Interpreted.class;
		}

		/** @return The type of breakpoint to catch */
		public BreakType getBreakType() {
			return theBreakType;
		}

		/** @return The tags of this debug value */
		public List<DebugTag> getTags() {
			return Collections.unmodifiableList(theTags);
		}

		@Override
		public void update(ExAddOn.Interpreted<? super ExElement, ?> interpreted, ExElement element) throws ModelInstantiationException {
			super.update(interpreted, element);
			Interpreted myInterpreted = (Interpreted) interpreted;
			CollectionUtils.synchronize(theTags, myInterpreted.getTags(), (inst, interp) -> inst.getIdentity() == interp.getIdentity())//
			.<ModelInstantiationException> simpleX(DebugTag.Interpreted::create)//
			.adjust();
		}

		@Override
		public void preInstantiate() {
			if (theBreakType == BreakType.instantiate)
				BreakpointHere.breakpoint();
			super.preInstantiate();
		}
	}

	/** The &lt;dbug-tag> element */
	public static class DebugTag extends ExElement.Abstract {
		/** Definition for {@link DebugTag} */
		@ExElementTraceable(toolkit = DEBUG, qonfigType = "debug-tag", interpretation = Interpreted.class, instance = DebugTag.class)
		public static class Def extends ExElement.Def.Abstract<DebugTag> {
			/**
			 * @param parent The parent of this element
			 * @param qonfigType The Qonfig type of this element
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			/**
			 * @param parent The parent of the interpreted element
			 * @return The interpreted element
			 */
			public Interpreted interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted(this, parent);
			}
		}

		/** Interpretation for {@link DebugTag} */
		public static class Interpreted extends ExElement.Interpreted.Abstract<DebugTag> {
			Interpreted(DebugTag.Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public DebugTag.Def getDefinition() {
				return (DebugTag.Def) super.getDefinition();
			}

			/** @return The instantiated element */
			public DebugTag create() {
				return new DebugTag(getIdentity());
			}
		}

		DebugTag(Object id) {
			super(id);
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);
		}
	}
}
