package org.observe.quick;

import java.util.ArrayList;
import java.util.List;

import org.observe.collect.ObservableCollection;
import org.observe.quick.style.StyleQIS;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.config.QonfigEvaluationException;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.ex.ExConsumer;

public interface QuickContainer extends QuickComponentDef {
	List<QuickComponentDef> getChildren();

	QuickComponent installContainer(PanelPopulator<?, ?> container, QuickComponent.Builder builder,
		ExConsumer<PanelPopulator<?, ?>, QonfigEvaluationException> populator) throws QonfigEvaluationException;

	default ObservableCollection<QuickComponent> populateContainer(PanelPopulator<?, ?> thisContainer, QuickComponent.Builder builder)
		throws QonfigEvaluationException {
		List<QuickComponent> children = new ArrayList<>(getChildren().size());
		for (int c = 0; c < getChildren().size(); c++) {
			QuickComponentDef childDef = getChildren().get(c);
			QuickComponent.Builder childBuilder = QuickComponent.build(childDef, builder, builder.getModels());
			children.add(childDef.install(thisContainer, childBuilder));
		}
		return ObservableCollection.of(QuickComponent.class, children);
	}

	@Override
	default QuickComponent install(PanelPopulator<?, ?> container, QuickComponent.Builder builder) throws QonfigEvaluationException {
		return installContainer(container, builder, //
			thisContainer -> populateContainer(thisContainer, builder));
	}

	public abstract class AbstractQuickContainer extends AbstractQuickComponentDef implements QuickContainer {
		private final List<QuickComponentDef> theChildren;

		public AbstractQuickContainer(StyleQIS session, List<QuickComponentDef> children) throws QonfigInterpretationException {
			super(session);
			theChildren = children;
		}

		@Override
		public List<QuickComponentDef> getChildren() {
			return theChildren;
		}
	}
}