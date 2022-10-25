package org.observe.quick;

import java.awt.Component;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.observe.collect.ObservableCollection;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.StyleQIS;
import org.qommons.config.QonfigAttributeDef;
import org.qommons.config.QonfigInterpretationException;

public class QuickComponent {
	private final QuickComponentDef theDefinition;
	private final QuickComponent.Builder theParent;
	private final Component theComponent;
	private final Map<QonfigAttributeDef, Object> theAttributeValues;
	private final ObservableCollection<QuickComponent> theChildren;
	// private ObservableMultiMap<QuickComponentDef, QuickComponent> theGroupedChildren;

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

	public static class Builder {
		private final QuickComponentDef theDefinition;
		private final Builder theParent;
		private final ModelSetInstance theModelsInstance;
		private Component theComponent;
		private final Map<QonfigAttributeDef, Object> theAttributeValues;
		private final ObservableCollection<QuickComponent> theChildren;
		private QuickComponent theBuilt;

		public Builder(QuickComponentDef definition, Builder parent, ModelSetInstance models) {
			theDefinition = definition;
			theParent = parent;
			ExpressoQIS exSession;
			StyleQIS.installParentModels(models, parent == null ? null : parent.getModels());
			try {
				exSession = definition.getSession().as(ExpressoQIS.class);
			} catch (QonfigInterpretationException e) {
				throw new IllegalStateException("Should have happened earlier", e);
			}
			theModelsInstance = exSession.wrapLocal(models);
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
			if (theComponent == component)
				return this;
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

		@Override
		public String toString() {
			return "Building " + theDefinition.toString();
		}
	}
}
