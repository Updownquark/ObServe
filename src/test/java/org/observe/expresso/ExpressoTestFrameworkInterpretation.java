package org.observe.expresso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoTesting.ExpressoTest;
import org.observe.expresso.ExpressoTesting.TestAction;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ObservableModelSet.ValueCreator;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** Interpretation for types defined by the Expresso Test Framework */
public class ExpressoTestFrameworkInterpretation implements QonfigInterpretation {
	/** The name of the test toolkit */
	public static final String TOOLKIT_NAME = "Expresso-Testing";
	/** The version of the test toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class);
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
	public Builder configureInterpreter(Builder interpreter) {
		interpreter//
		.createWith("testing", ExpressoTesting.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			Expresso head = exS.forChildren("head").get(0).asElement("expresso").interpret(Expresso.class);
			exS.setModels(head.getModels(), head.getClassView());
			Map<String, ExpressoTest> tests = new LinkedHashMap<>();
			for (ExpressoTest test : exS.interpretChildren("test", ExpressoTest.class)) {
				if (tests.containsKey(test.getName()))
					session.error("Duplicate tests named " + test.getName());
				tests.put(test.getName(), test);
			}
			return new ExpressoTesting<>(head, Collections.unmodifiableMap(tests));
		})//
		.createWith("test", ExpressoTest.class, session -> {
			ExpressoQIS exS = session.as(ExpressoQIS.class);
			List<TestAction> actions = new ArrayList<>();
			for (ExpressoQIS actionS : exS.forChildren("test-action")) {
				ExpressoQIS testActionS = actionS.asElement("test-action");
				actions.add(new TestAction(testActionS.getAttributeText("name"),
					(ObservableModelSet.Built) exS.getExpressoEnv().getModels(), exS, //
					actionS.interpret(ValueCreator.class), //
					testActionS.getAttributeText("expect-throw"), testActionS.getAttribute("breakpoint", boolean.class)));
			}
				return new ExpressoTest(session.getAttributeText("name"), (ObservableModelSet.Built) exS.getExpressoEnv().getModels(), exS,
				Collections.unmodifiableList(actions));
		})//
		.createWith("watch", ValueCreator.class, session -> {
			QonfigExpression2 valueX = session.as(ExpressoQIS.class).getValueExpression();
			return () -> {
				ValueContainer<SettableValue<?>, SettableValue<?>> watching = valueX.evaluate(ModelTypes.Value.any());
				return ValueContainer.of(watching.getType(), msi -> {
					SettableValue<Object> value = (SettableValue<Object>) watching.get(msi);
					SettableValue<Object> copy = SettableValue.build(value.getType()).withValue(value.get()).build();
					value.noInitChanges().takeUntil(msi.getUntil()).act(evt -> copy.set(evt.getNewValue(), evt));
					return value.disableWith(ObservableValue.of("A watched value cannot be modified"));
				});
			};
		})//
		;
		return interpreter;
	}
}
