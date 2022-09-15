package org.observe.quick;

import java.util.function.BiConsumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.QuickElementStyle;
import org.observe.quick.style.StyleQIS;
import org.observe.util.swing.PanelPopulation.ComponentEditor;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.config.QonfigElement;

public interface QuickComponentDef {
	QonfigElement getElement();

	StyleQIS getSession();

	QuickElementStyle getStyle();

	Function<ModelSetInstance, ? extends ObservableValue<String>> getFieldName();

	void setFieldName(Function<ModelSetInstance, ? extends ObservableValue<String>> fieldName);

	QuickComponentDef modify(BiConsumer<ComponentEditor<?, ?>, QuickComponent.Builder> modification);

	QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder);

	// interface ModelValueSupport<T> extends ObservableValue<T> {
	// void install(Component component);
	// }
	//
	// static class ProducerModelValueSupport<T> extends ObservableValue.FlattenedObservableValue<T> implements ModelValueSupport<T> {
	// private final Function<Component, ObservableValue<T>> theCreator;
	//
	// public ProducerModelValueSupport(Class<T> type, Function<Component, ObservableValue<T>> creator, T defaultValue) {
	// this(TypeTokens.get().of(type), creator, defaultValue);
	// }
	//
	// public ProducerModelValueSupport(TypeToken<T> type, Function<Component, ObservableValue<T>> creator, T defaultValue) {
	// super(SettableValue.build((Class<ObservableValue<T>>) (Class<?>) ObservableValue.class).build(),
	// LambdaUtils.constantSupplier(defaultValue, () -> String.valueOf(defaultValue), defaultValue));
	// theCreator=creator;
	// }
	//
	// @Override
	// public void install(Component component) {
	// ((SettableValue<ObservableValue<T>>) getWrapped()).set(theCreator.apply(component), null);
	// }
	//
	// public ObservableValue<T> getValue() {
	// return (ObservableValue<T>) getWrapped().get();
	// }
	// }

	// static <T> SettableValue<T> getIfSupported(ObservableValue<?> value, SimpleModelValueSupport<T> support) {
	// if (value != null && value.getIdentity() instanceof SimpleModelValueIdentity
	// && ((SimpleModelValueIdentity<?>) value.getIdentity()).getSupport() == support)
	// return ((SimpleModelValueIdentity<T>) value.getIdentity()).theValue.getSettable();
	// else
	// return null;
	// }

	// static class SimpleModelValueSupport<T> implements Supplier<ModelValueSupport<T>> {
	// private final TypeToken<T> theType;
	// private final T theInitialValue;
	//
	// public SimpleModelValueSupport(Class<T> type, T initialValue) {
	// this(TypeTokens.get().of(type), initialValue);
	// }
	//
	// public SimpleModelValueSupport(TypeToken<T> type, T initialValue) {
	// theType = type;
	// theInitialValue = initialValue;
	// }
	//
	// public TypeToken<T> getType() {
	// return theType;
	// }
	//
	// public T getInitialValue() {
	// return theInitialValue;
	// }
	//
	// @Override
	// public ModelValueSupport<T> get() {
	// return new SimpleModelValue<>(this);
	// }
	// }

	// static class SimpleModelValueIdentity<T> {
	// final SimpleModelValue<T> theValue;
	//
	// SimpleModelValueIdentity(SimpleModelValue<T> value) {
	// theValue = value;
	// }
	//
	// public SimpleModelValueSupport<T> getSupport() {
	// return theValue.getSupport();
	// }
	// }
	//
	// static class SimpleModelValue<T> extends AbstractIdentifiable implements ModelValueSupport<T> {
	// private final SimpleModelValueSupport<T> theSupport;
	// private SettableValue<T> theValue;
	//
	// public SimpleModelValue(SimpleModelValueSupport<T> support) {
	// theSupport = support;
	// }
	//
	// public SimpleModelValueSupport<T> getSupport() {
	// return theSupport;
	// }
	//
	// SettableValue<T> getSettable() {
	// return theValue;
	// }
	//
	// private SettableValue<T> init() {
	// if (theValue == null)
	// theValue = SettableValue.build(theSupport.getType()).withValue(theSupport.getInitialValue()).build();
	// return theValue;
	// }
	//
	// @Override
	// public void install(Component component) {
	// }
	//
	// @Override
	// public T get() {
	// return init().get();
	// }
	//
	// @Override
	// public Observable<ObservableValueEvent<T>> noInitChanges() {
	// return init().noInitChanges();
	// }
	//
	// @Override
	// public TypeToken<T> getType() {
	// return theSupport.getType();
	// }
	//
	// @Override
	// protected Object createIdentity() {
	// return new SimpleModelValueIdentity<>(this);
	// }
	//
	// @Override
	// public long getStamp() {
	// return init().getStamp();
	// }
	// }
}