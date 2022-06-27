package org.observe.quick;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.ObservableValue;
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
	private final Map<QuickModelValue<?>, Supplier<? extends ModelValueSupport<?>>> theModelImplementations;

	public AbstractQuickComponentDef(QuickSession<?> session) {
		theElement = session.getElement();
		theModels = (ObservableModelSet.Wrapped) session.getExpressoEnv().getModels();
		theStyle = session.getStyle();
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
	public <T> QuickComponentDef support(QuickModelValue<T> modelValue, Supplier<ModelValueSupport<T>> value)
		throws QonfigInterpretationException {
		if (!theElement.isInstance(modelValue.getStyle().getElement()))
			throw new QonfigInterpretationException("Model value " + modelValue + " does not apply to this element (" + theElement + ")");
		theModelImplementations.put(modelValue, value);
		return this;
	}

	@Override
	public <T> Supplier<ModelValueSupport<T>> getSupport(QuickModelValue<T> modelValue) {
		return (Supplier<ModelValueSupport<T>>) theModelImplementations.get(modelValue);
	}

	public void modify(ComponentEditor<?, ?> component, QuickComponent.Builder builder) {
		if (theModifications != null)
			theModifications.accept(component, builder);
	}

	@Override
	public String toString() {
		return theElement.toString();
	}
}