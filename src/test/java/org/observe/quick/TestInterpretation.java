package org.observe.quick;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ExpressoTesting.ExpressoTest;
import org.observe.expresso.ExpressoTesting.TestAction;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.expresso.ObservableModelSet.RuntimeValuePlaceholder;
import org.observe.quick.QuickWidget.Interpreted;
import org.observe.quick.style.StyleQIS;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

import com.google.common.reflect.TypeToken;

/** Interpretation for {@link QuickTests} */
public class TestInterpretation implements QonfigInterpretation {
	/** The name of the test toolkit */
	public static final String TOOLKIT_NAME = "Quick-Testing";
	/** The version of the test toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

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
		return VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
	}

	@Override
	public Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter) {
		interpreter.modifyWith("quick-test", ExpressoTest.class, (value, session, prepared) -> wrap(value, session.as(ExpressoQIS.class)));
		return interpreter;
	}

	public static class QuickTest extends ExpressoTest {
		private final QuickWidget.Def<?> theWidget;
		private final RuntimeValuePlaceholder<SettableValue<?>, SettableValue<QuickWidget>> theBodyPlaceholder;
		private final QuickWidget.Interpreted<?>[] theInterpretedRoot;

		public QuickTest(String name, ObservableModelSet.Built model, ExpressoQIS session, List<TestAction> actions,
			QuickWidget.Def<?> widget, RuntimeValuePlaceholder<SettableValue<?>, SettableValue<QuickWidget>> bodyPlaceholder,
			Interpreted<?>[] interpretedRoot) throws QonfigInterpretationException {
			super(name, model, session, actions);
			theWidget = widget;
			theBodyPlaceholder = bodyPlaceholder;
			theInterpretedRoot = interpretedRoot;
		}

		public QuickWidget.Def<?> getWidget() {
			return theWidget;
		}

		@Override
		public ModelSetInstance createModelInstance(ModelSetInstance testingModel)
			throws ExpressoInterpretationException, ModelInstantiationException {
			theInterpretedRoot[0] = theWidget.interpret(null, new QuickWidget.QuickInterpretationCache());
			QuickWidget body = theInterpretedRoot[0].create(null, testingModel, new QuickWidget.QuickInstantiationCache());
			ModelSetInstance testModel = getModel().interpret().createInstance(testingModel.getUntil()).withAll(testingModel)
				.with(theBodyPlaceholder, SettableValue.of(QuickWidget.class, body, "Widgets are not settable"))//
				.build();
			return super.createModelInstance(testModel);
		}
	}

	public static QuickTest wrap(ExpressoTest test, ExpressoQIS session) throws QonfigInterpretationException {
		ExpressoQIS bodySession = session.forChildren("body").getFirst();
		QuickWidget.Def<?> body = bodySession.interpret(QuickWidget.Def.class);
		ObservableModelSet.Builder wrappedModel = test.getModel().wrap("quickModel");
		String quickModelName = bodySession.getAttributeText("name");
		ObservableModelSet.Builder quickModel = wrappedModel.createSubModel(quickModelName);
		RuntimeValuePlaceholder<SettableValue<?>, SettableValue<QuickWidget>> bodyPlaceholder = quickModel.withRuntimeValue("$body$",
			ModelTypes.Value.forType(QuickWidget.class));
		QuickWidget.Interpreted<?>[] interpretedRoot = new QuickWidget.Interpreted[1];
		populateQuickModel(quickModel, body, quickModelName, //
			(ModelComponentNode<SettableValue<?>, SettableValue<QuickWidget>>) quickModel.getComponentIfExists("$body$"), interpretedRoot,
			new ArrayList<>());
		return new QuickTest(test.getName(), wrappedModel.build(), session, test.getActions(), body, bodyPlaceholder, interpretedRoot);
	}

	private static <W extends QuickWidget> void populateQuickModel(ObservableModelSet.Builder quickModel, QuickWidget.Def<?> widget,
		String modelName, ModelComponentNode<SettableValue<?>, SettableValue<QuickWidget>> bodyValue,
		QuickWidget.Interpreted<?>[] interpretedRoot, List<QuickWidget.Def<?>> path) {
		if (widget.getParent() != null && widget.getName() != null) {
			QuickWidget.Def<?>[] widgetPath = path.toArray(new QuickWidget.Def[path.size()]);
			quickModel.withMaker(widget.getName(), CompiledModelValue.of(widget.getName(), ModelTypes.Value, () -> {
				InterpretedValueSynth<SettableValue<?>, SettableValue<QuickWidget>> bodySynth = bodyValue.interpret();
				TypeToken<W> widgetType = (TypeToken<W>) find(interpretedRoot[0], widgetPath, 0).getWidgetType();
				return ModelValueSynth.of(ModelTypes.Value.forType(widgetType), models -> {
					QuickWidget body = bodySynth.get(models).get();
					return SettableValue.of(widgetType, (W) find(body, widgetPath, 0), "Widgets are not settable");
				});
			}));
		}
	}

	private static QuickWidget.Interpreted<?> find(QuickWidget.Interpreted<?> parent, QuickWidget.Def<?>[] widgetPath, int pathIndex) {
		if (pathIndex == widgetPath.length)
			return parent;
		for (QuickWidget.Interpreted<?> child : ((QuickContainer2.Interpreted<?, ?>) parent).getContents()) {
			if (child.getDefinition() == widgetPath[pathIndex])
				return find(child, widgetPath, pathIndex + 1);
		}
		throw new IllegalStateException("Could not find widget synth for " + widgetPath[widgetPath.length - 1].getName());
	}

	private static QuickWidget find(QuickWidget parent, QuickWidget.Def<?>[] widgetPath, int pathIndex) {
		if (pathIndex == widgetPath.length)
			return parent;
		for (QuickWidget child : ((QuickContainer2<?>) parent).getContents()) {
			if (child.getInterpreted().getDefinition() == widgetPath[pathIndex])
				return find(child, widgetPath, pathIndex + 1);
		}
		throw new IllegalStateException("Could not find widget " + widgetPath[widgetPath.length - 1].getName());
	}
}
