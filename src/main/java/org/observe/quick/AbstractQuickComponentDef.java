package org.observe.quick;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.QuickElementStyle;
import org.observe.quick.style.StyleQIS;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public abstract class AbstractQuickComponentDef implements QuickComponentDef {
	private final StyleQIS theSession;
	private final QuickElementStyle theStyle;
	private Function<ModelSetInstance, ? extends ObservableValue<String>> theFieldName;
	private BiConsumer<ComponentEditor<?, ?>, QuickComponent.Builder> theModifications;

	public AbstractQuickComponentDef(StyleQIS session) throws QonfigInterpretationException {
		theSession = session;
		theStyle = session.getStyle();
	}

	@Override
	public QonfigElement getElement() {
		return theSession.getElement();
	}

	@Override
	public StyleQIS getSession() {
		return theSession;
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

	public void modify(ComponentEditor<?, ?> component, QuickComponent.Builder builder) {
		if (theModifications != null)
			theModifications.accept(component, builder);
	}

	@Override
	public String toString() {
		return getElement().toString();
	}
}