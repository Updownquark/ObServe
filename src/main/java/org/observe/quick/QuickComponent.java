package org.observe.quick;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.assoc.ObservableMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickComponentDef.ModelValueSupport;
import org.observe.quick.style.QuickModelValue;
import org.observe.util.TypeTokens;
import org.qommons.BiTuple;
import org.qommons.config.QonfigAttributeDef;

import com.google.common.reflect.TypeToken;

public class QuickComponent {
	private final QuickComponentDef theDefinition;
	private final QuickComponent.Builder theParent;
	private final Component theComponent;
	private final Map<QonfigAttributeDef, Object> theAttributeValues;
	private final ObservableCollection<QuickComponent> theChildren;
	private ObservableMultiMap<QuickComponentDef, QuickComponent> theGroupedChildren;
	private ObservableValue<Point> theLocation;
	private ObservableValue<Dimension> theSize;

	public QuickComponent(QuickComponentDef definition, QuickComponent.Builder parent, Component component,
		Map<QonfigAttributeDef, Object> attributeValues, ObservableCollection<QuickComponent> children) {
		theDefinition = definition;
		theParent = parent;
		theComponent = component;
		theAttributeValues = attributeValues;
		theChildren = children;
	}

	public QuickComponentDef getDefinition() {
		return theDefinition;
	}

	public QuickComponent getParent() {
		return theParent == null ? null : theParent.getBuilt();
	}

	public Component getComponent() {
		return theComponent;
	}

	public Map<QonfigAttributeDef, Object> getAttributeValues() {
		return theAttributeValues;
	}

	public ObservableCollection<QuickComponent> getChildren() {
		return theChildren;
	}

	// TODO These might be needed for the debugger, but obviously I haven't figured out how to populate them
	// They also probably shouldn't be values, as they will be in a table and there's no good way for a table to listen to an observable
	// on a particular row
	// public ObservableValue<Point> getLocation() {
	// }
	//
	// public ObservableValue<Dimension> getMinimumSize() {
	// }
	//
	// public ObservableValue<Dimension> getPreferredSize() {
	// }
	//
	// public ObservableValue<Dimension> getMaximumSize() {
	// }
	//
	// public ObservableValue<Dimension> getSize() {
	// }
	//
	// public ObservableMultiMap<QuickComponentDef, QuickComponent> getGroupedChildren() {
	// }

	public static QuickComponent.Builder build(QuickComponentDef def, QuickComponent.Builder parent, ModelSetInstance models) {
		return new Builder(def, parent, models);
	}

	public static class Builder implements QuickModelValue.Satisfier {
		private final QuickComponentDef theDefinition;
		private final Builder theParent;
		private final ModelSetInstance theModelsInstance;
		private Component theComponent;
		private final Map<QonfigAttributeDef, Object> theAttributeValues;
		private final ObservableCollection<QuickComponent> theChildren;
		private final Map<QuickModelValue<?>, BiTuple<? extends Supplier<? extends ModelValueSupport<?>>, ? extends ModelValueSupport<?>>> theModelValues;
		private QuickComponent theBuilt;

		public Builder(QuickComponentDef definition, Builder parent, ModelSetInstance models) {
			theDefinition = definition;
			theParent = parent;
			theModelsInstance = theDefinition.getModels().wrap(models)//
				.withCustom(ExpressoQIS.PARENT_MODEL,
					SettableValue.of(ModelSetInstance.class, parent == null ? null : parent.getModels(), "Not Reversible"))//
				.withCustom(QuickModelValue.SATISFIER_PLACEHOLDER,
					SettableValue.of(QuickModelValue.Satisfier.class, this, "Not reversible"))//
				.build();
			theAttributeValues = new LinkedHashMap<>();
			theChildren = ObservableCollection.build(QuickComponent.class).build();
			theModelValues = new HashMap<>();
		}

		public ModelSetInstance getModels() {
			return theModelsInstance;
		}

		public ObservableCollection<QuickComponent> getChildren() {
			return theChildren;
		}

		public QuickComponent.Builder withAttribute(QonfigAttributeDef attr, Object value) {
			if (theBuilt != null)
				throw new IllegalStateException("Already built");
			theAttributeValues.put(attr, value);
			return this;
		}

		public QuickComponent.Builder withChild(QuickComponent component) {
			if (theBuilt != null)
				throw new IllegalStateException("Already built");
			theChildren.add(component);
			return this;
		}

		public <T> Supplier<ModelValueSupport<T>> getSupport(QuickModelValue<T> modelValue) {
			return _getSupport(modelValue).getValue1();
		}

		@Override
		public <T> ObservableValue<T> satisfy(QuickModelValue<T> modelValue) {
			return _getSupport(modelValue).getValue2();
		}

		private <T> BiTuple<Supplier<ModelValueSupport<T>>, ModelValueSupport<T>> _getSupport(QuickModelValue<T> modelValue) {
			BiTuple<Supplier<ModelValueSupport<T>>, ModelValueSupport<T>> tuple;
			tuple = (BiTuple<Supplier<ModelValueSupport<T>>, ModelValueSupport<T>>) theModelValues.get(modelValue);
			if (tuple == null) {
				Supplier<ModelValueSupport<T>> support = theDefinition.getSupport(modelValue);
				if (support == null) {
					if (!theDefinition.getElement().isInstance(modelValue.getStyle().getElement()))
						throw new IllegalArgumentException(
							"Model value " + modelValue + " is not applicable to this element (" + theDefinition.getElement() + ")");
					System.err.println("Model value " + modelValue + " has not been supported for " + theDefinition.getElement());
					tuple = new BiTuple<>(null, new DefaultModelSupport<>(modelValue.getValueType()));
				} else {
					ModelValueSupport<T> sv = support.get();
					if (theComponent != null)
						sv.install(theComponent);
					tuple = new BiTuple<>(support, sv);
				}
				theModelValues.put(modelValue, tuple);
			}
			return tuple;
		}

		public QuickComponent.Builder withComponent(Component component) {
			if (theComponent == component)
				return this;
			if (theBuilt != null)
				throw new IllegalStateException("Already built");
			theComponent = component;
			for (BiTuple<? extends Supplier<? extends ModelValueSupport<?>>, ? extends ModelValueSupport<?>> tuple : theModelValues
				.values())
				tuple.getValue2().install(theComponent);
			return this;
		}

		QuickComponent getBuilt() {
			return theBuilt;
		}

		public QuickComponent build() {
			if (theBuilt != null)
				throw new IllegalStateException("Already built");
			return new QuickComponent(theDefinition, theParent, theComponent, Collections.unmodifiableMap(theAttributeValues),
				theChildren.flow().unmodifiable().collect());
		}

		@Override
		public String toString() {
			return "Building " + theDefinition.toString();
		}
	}

	static class DefaultModelSupport<T> extends ObservableValue.ConstantObservableValue<T> implements ModelValueSupport<T> {
		public DefaultModelSupport(TypeToken<T> type) {
			super(type, TypeTokens.get().getPrimitiveDefault(type));
		}

		@Override
		public void install(Component component) {
		}
	}
}
