package org.observe.quick;

import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickInterpreter.QuickSession;
import org.observe.quick.style.QuickElementStyle;
import org.observe.quick.style.QuickModelValue;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public abstract class AbstractQuickComponentDef implements QuickComponentDef {
	private final QonfigElement theElement;
	private final ObservableModelSet.Wrapped theModels;
	private final QuickElementStyle theStyle;
	private Function<ModelSetInstance, ? extends ObservableValue<String>> theFieldName;
	private BiConsumer<ComponentEditor<?, ?>, QuickComponent.Builder> theModifications;
	private final ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<QuickModelValue.Satisfier>> theSatisfierPlaceholder;
	private final Map<QuickModelValue<?>, Function<Component, ? extends ObservableValue<?>>> theModelImplementations;

	public AbstractQuickComponentDef(QuickSession<?> session) {
		theElement = session.getElement();
		theModels = session.getLocalModels();
		theStyle = session.getStyle();
		theSatisfierPlaceholder = session.getSatisfierPlaceholder();
		theModelImplementations = new HashMap<>();
	}

	@Override
	public QonfigElement getElement() {
		return theElement;
	}

	@Override
	public ObservableModelSet.Wrapped getModels() {
		return theModels;
	}

	@Override
	public QuickElementStyle getStyle() {
		return theStyle;
	}

	@Override
	public Function<ModelSetInstance, ? extends ObservableValue<String>> getFieldName() {
		return theFieldName;
	}

	@Override
	public void setFieldName(Function<ModelSetInstance, ? extends ObservableValue<String>> fieldName) {
		theFieldName = fieldName;
	}

	@Override
	public AbstractQuickComponentDef modify(BiConsumer<ComponentEditor<?, ?>, QuickComponent.Builder> fieldModification) {
		if (theModifications == null)
			theModifications = fieldModification;
		else {
			BiConsumer<ComponentEditor<?, ?>, QuickComponent.Builder> old = theModifications;
			theModifications = (field, builder) -> {
				old.accept(field, builder);
				fieldModification.accept(field, builder);
			};
		}
		return this;
	}

	@Override
	public ObservableModelSet.RuntimeValuePlaceholder<SettableValue<?>, SettableValue<QuickModelValue.Satisfier>> getSatisfierPlaceholder() {
		return theSatisfierPlaceholder;
	}

	@Override
	public <T> QuickComponentDef support(QuickModelValue<T> modelValue, Function<Component, ObservableValue<T>> value)
		throws QonfigInterpretationException {
		if (!theElement.isInstance(modelValue.getStyle().getElement()))
			throw new QonfigInterpretationException("Model value " + modelValue + " does not apply to this element (" + theElement + ")");
		theModelImplementations.put(modelValue, value);
		return this;
	}

	@Override
	public <T> Function<Component, ObservableValue<T>> getSupport(QuickModelValue<T> modelValue) {
		return (Function<Component, ObservableValue<T>>) theModelImplementations.get(modelValue);
	}

	public void modify(ComponentEditor<?, ?> component, QuickComponent.Builder builder) {
		if (theModifications != null)
			theModifications.accept(component, builder);
	}
}