package org.observe.quick;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.observe.ObservableValue;
import org.observe.assoc.ObservableMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.qommons.config.QonfigAttributeDef;

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
		super();
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

	public static class Builder {
		private final QuickComponentDef theDefinition;
		private final QuickComponent.Builder theParent;
		private final ModelSetInstance theModelsInstance;
		private Component theComponent;
		private final Map<QonfigAttributeDef, Object> theAttributeValues;
		private final ObservableCollection<QuickComponent> theChildren;
		private QuickComponent theBuilt;

		public Builder(QuickComponentDef definition, QuickComponent.Builder parent, ModelSetInstance models) {
			theDefinition = definition;
			theParent = parent;
			theModelsInstance = models;
			theAttributeValues = new LinkedHashMap<>();
			theChildren = ObservableCollection.build(QuickComponent.class).build();
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

		public QuickComponent.Builder withComponent(Component component) {
			if (theBuilt != null)
				throw new IllegalStateException("Already built");
			theComponent = component;
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
	}
}