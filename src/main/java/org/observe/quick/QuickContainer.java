package org.observe.quick;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.observe.collect.ObservableCollection;
import org.observe.expresso.ObservableModelSet;
import org.observe.quick.style.QuickElementStyle;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.config.QonfigElement;

public interface QuickContainer extends QuickComponentDef {
	List<QuickComponentDef> getChildren();

	QuickComponent installContainer(PanelPopulator<?, ?> container, QuickComponent.Builder builder,
		Consumer<PanelPopulator<?, ?>> populator);

	default ObservableCollection<QuickComponent> populateContainer(PanelPopulator<?, ?> thisContainer, QuickComponent.Builder builder) {
		List<QuickComponent> children = new ArrayList<>(getChildren().size());
		for (int c = 0; c < getChildren().size(); c++) {
			QuickComponentDef childDef = getChildren().get(c);
			QuickComponent.Builder childBuilder = QuickComponent.build(childDef, builder, builder.getModels());
			children.add(childDef.install(thisContainer, childBuilder));
		}
		return ObservableCollection.of(QuickComponent.class, children);
	}

	@Override
	default QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) {
		return installContainer(container, builder, //
			thisContainer -> populateContainer(thisContainer, builder));
	}

	public abstract class AbstractQuickContainer extends AbstractQuickComponentDef implements QuickContainer {
		private final List<QuickComponentDef> theChildren;

		public AbstractQuickContainer(QonfigElement element, ObservableModelSet.Wrapped models, QuickElementStyle style,
			List<QuickComponentDef> children) {
			super(element, models, style);
			theChildren = children;
		}

		@Override
		public List<QuickComponentDef> getChildren() {
			return theChildren;
		}
	}
}