package org.observe.quick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExpressoBaseV0_1;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigAddOn;
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
	public Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("quick-test", ExAddOn.Def.class, ExpressoBaseV0_1.addOnCreator(QuickTest.Def::new));
		interpreter.createWith("quick-test-container", ExAddOn.Def.class, ExpressoBaseV0_1.addOnCreator(QuickTestContainer.Def::new));
		return interpreter;
	}

	static abstract class QuickTestWidgetContainer extends ExFlexibleElementModelAddOn<ExElement> {
		static abstract class Def extends ExFlexibleElementModelAddOn.Def<ExElement, QuickTestWidgetContainer> {
			Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
				super(type, element);
			}

			@Override
			public Map<String, WidgetModelValue> getElementValues() {
				return (Map<String, WidgetModelValue>) super.getElementValues();
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
				super.update(session, element);
				// Need to ensure that we create the builder, because if we create it in the postUpdate method,
				// the tests and all have already been created, and creating a new model set for the test won't trickle down
				createBuilder(session);
			}

			@Override
			public void postUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.postUpdate(session);
				List<? extends QuickWidget.Def<?>> contents = getContents();
				if (!contents.isEmpty()) {
					ObservableModelSet.Builder builder = createBuilder(session);
					for (QuickWidget.Def<?> widget : contents) {
						String name = widget.getName();
						if (name == null)
							continue;
						addElementValue(name, new WidgetModelValue(widget), builder, widget.getElement().getPositionInFile());
						satisfyElementValueType(name, ModelTypes.Value, (interp, env) -> {
							List<? extends QuickWidget.Interpreted<?>> interpContents = interp.getAddOn(Interpreted.class).getContents();
							for (QuickWidget.Interpreted<?> interpWidget : interpContents) {
								if (interpWidget.getIdentity() == widget.getIdentity())
									return ModelTypes.Value.forType(interpWidget.getWidgetType());
							}
							throw new IllegalStateException("Widget interpretation not found: " + widget);
						});
					}
				}
			}

			protected abstract List<? extends QuickWidget.Def<?>> getContents();

			@Override
			public abstract Interpreted interpret(ExElement.Interpreted<? extends ExElement> element);
		}

		static abstract class Interpreted extends ExFlexibleElementModelAddOn.Interpreted<ExElement, QuickTestWidgetContainer> {
			public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
				super(definition, element);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			protected abstract List<? extends QuickWidget.Interpreted<?>> getContents();

			@Override
			public abstract QuickTestWidgetContainer create(ExElement element);
		}

		static class WidgetModelValue extends ExFlexibleElementModelAddOn.PlaceholderModelValue<SettableValue<?>> {
			private final QuickWidget.Def<?> theWidget;

			public WidgetModelValue(QuickWidget.Def<?> widget) {
				super(widget.getName());
				theWidget = widget;
			}

			public QuickWidget.Def<?> getWidget() {
				return theWidget;
			}

			@Override
			public ModelType<SettableValue<?>> getModelType() {
				return ModelTypes.Value;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, ?> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				return create(env, ModelTypes.Value.forType(QuickWidget.class));
			}
		}

		public QuickTestWidgetContainer(Interpreted interpreted, ExElement element) {
			super(interpreted, element);
		}

		protected abstract List<? extends QuickWidget> getContents();

		@Override
		public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
			super.update(interpreted, models);
			Interpreted myInterpreted = (Interpreted) interpreted;
			List<? extends QuickWidget> widgets = getContents();
			for (Map.Entry<String, WidgetModelValue> widgetDef : myInterpreted.getDefinition().getElementValues().entrySet()) {
				boolean found = false;
				for (QuickWidget widget : widgets)
					if (widget.getIdentity() == widgetDef.getValue().getWidget().getIdentity())
						satisfyElementValue(widgetDef.getKey(), SettableValue.of(//
							(TypeToken<QuickWidget>) myInterpreted.getElementValues().get(widgetDef.getKey()).getType().getType(0), widget,
							"Not settable"));
				if (!found)
					throw new IllegalStateException("Widget instance not found: " + widgetDef.getValue());
			}
		}
	}

	static class QuickTest extends QuickTestWidgetContainer {
		static class Def extends QuickTestWidgetContainer.Def {
			private final List<QuickWidget.Def<?>> theWidgets;

			public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
				super(type, element);
				theWidgets = new ArrayList<>();
			}

			@Override
			protected List<? extends QuickWidget.Def<?>> getContents() {
				return Collections.unmodifiableList(theWidgets);
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
				super.update(session, element);
				ExElement.syncDefs(QuickWidget.Def.class, theWidgets, session.forChildren("body"));
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
				return new Interpreted(this, element);
			}
		}

		static class Interpreted extends QuickTestWidgetContainer.Interpreted {
			private final List<QuickWidget.Interpreted<?>> theWidgets;

			public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
				super(definition, element);
				theWidgets = new ArrayList<>();
			}

			@Override
			protected List<? extends QuickWidget.Interpreted<?>> getContents() {
				return Collections.unmodifiableList(theWidgets);
			}

			@Override
			public void update(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				CollectionUtils.synchronize(theWidgets, getDefinition().getContents(), (i, d) -> i.getIdentity() == d.getIdentity())//
				.<ExpressoInterpretationException> simpleE(d -> d.interpret(getElement()))//
				.onLeftX(el -> el.getLeftValue().destroy())//
				.onRightX(el -> el.getLeftValue().updateElement(getElement().getExpressoEnv()))//
				.onCommonX(el -> el.getLeftValue().updateElement(getElement().getExpressoEnv()))//
				.adjust();
				super.update(env);
			}

			@Override
			public QuickTest create(ExElement element) {
				return new QuickTest(this, element);
			}
		}

		private final List<QuickWidget> theWidgets;

		public QuickTest(Interpreted interpreted, ExElement element) {
			super(interpreted, element);
			theWidgets = new ArrayList<>();
		}

		@Override
		protected List<? extends QuickWidget> getContents() {
			return Collections.unmodifiableList(theWidgets);
		}

		@Override
		public void update(ExAddOn.Interpreted<?, ?> interpreted, ModelSetInstance models) throws ModelInstantiationException {
			Interpreted myInterpreted = (Interpreted) interpreted;
			CollectionUtils.synchronize(theWidgets, myInterpreted.getContents(), (w, i) -> w.getIdentity() == i.getIdentity())//
			.<ModelInstantiationException> simpleE(i -> i.create(getElement()))//
			.onRightX(el -> el.getLeftValue().update(el.getRightValue(), models))//
			.onCommonX(el -> el.getLeftValue().update(el.getRightValue(), models))//
			.adjust();
			super.update(interpreted, models);
		}
	}

	static class QuickTestContainer extends QuickTestWidgetContainer {
		static class Def extends QuickTestWidgetContainer.Def {
			public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
				super(type, element);
			}

			@Override
			protected List<? extends QuickWidget.Def<?>> getContents() {
				return ((QuickContainer.Def<?, ?>) getElement()).getContents();
			}

			@Override
			public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
				return new Interpreted(this, element);
			}
		}

		static class Interpreted extends QuickTestWidgetContainer.Interpreted {
			public Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
				super(definition, element);
			}

			@Override
			protected List<? extends QuickWidget.Interpreted<?>> getContents() {
				return ((QuickContainer.Interpreted<?, ?>) getElement()).getContents();
			}

			@Override
			public QuickTestWidgetContainer create(ExElement element) {
				return new QuickTestContainer(this, element);
			}
		}

		public QuickTestContainer(Interpreted interpreted, ExElement element) {
			super(interpreted, element);
		}

		@Override
		protected List<? extends QuickWidget> getContents() {
			return ((QuickContainer<?>) getElement()).getContents();
		}
	}
}
