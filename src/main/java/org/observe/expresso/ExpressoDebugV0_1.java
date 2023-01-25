package org.observe.expresso;

import java.util.Collections;
import java.util.Set;

import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ObservableModelSet.ValueCreator;
import org.qommons.BreakpointHere;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigEvaluationException;
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
		interpreter//
		.modifyWith("debug-value", (Class<ValueCreator<?, ?>>) (Class<?>) ValueCreator.class,
			new QonfigValueModifier<ValueCreator<?, ?>>() {
			@Override
			public Object prepareSession(CoreSession session) throws QonfigInterpretationException {
				String breakpointType = session.getAttributeText("break-on");
				if ("parse".equals(breakpointType))
					BreakpointHere.breakpoint();
				return null;
			}

			@Override
			public ValueCreator<?, ?> modifyValue(ValueCreator<?, ?> value, CoreSession session, Object prepared)
				throws QonfigInterpretationException {
				String breakpointType = session.getAttributeText("break-on");
				if (breakpointType == null || "parse".equals(breakpointType))
					return value;
				return new ValueCreator<Object, Object>() {
					@Override
					public ValueContainer<Object, Object> createContainer() throws QonfigEvaluationException {
						if ("createContainer".equals(breakpointType)) {
							BreakpointHere.breakpoint();
							return (ValueContainer<Object, Object>) value.createContainer();
						} else {
							ValueContainer<Object, Object> wrapped = (ValueContainer<Object, Object>) value.createContainer();
							return new ValueContainer<Object, Object>() {
								@Override
								public ModelInstanceType<Object, Object> getType() throws QonfigEvaluationException {
									return wrapped.getType();
								}

								@Override
								public Object get(ModelSetInstance models) throws QonfigEvaluationException {
									if ("createValue".equals(breakpointType))
										BreakpointHere.breakpoint();
									return wrapped.get(models);
								}

								@Override
								public Object forModelCopy(Object oldValue, ModelSetInstance sourceModels,
									ModelSetInstance newModels) throws QonfigEvaluationException {
									return wrapped.forModelCopy(value, sourceModels, newModels);
								}

								@Override
								public BetterList<ValueContainer<?, ?>> getCores() throws QonfigEvaluationException {
									return wrapped.getCores();
								}

								@Override
								public String toString() {
									return wrapped.toString();
								}
							};
						}
					}

					@Override
					public String toString() {
						return value.toString();
					}
				};
			}
		})//
		;
		return interpreter;
	}
}
