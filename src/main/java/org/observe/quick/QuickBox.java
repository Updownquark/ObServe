package org.observe.quick;

import java.util.List;
import java.util.function.Consumer;

import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.config.QonfigElement;

public class QuickBox extends QuickContainer.AbstractQuickContainer {
	private final String theLayoutName;
	private QuickLayout theLayout;

	public QuickBox(QonfigElement element, List<QuickComponentDef> children, String layoutName) {
		super(element, children);
		theLayoutName = layoutName;
	}

	public QuickBox setLayout(QuickLayout layout) {
		theLayout = layout;
		return this;
	}

	@Override
	public QuickComponent installContainer(PanelPopulator<?, ?> container, QuickComponent.Builder builder,
		Consumer<PanelPopulator<?, ?>> populator) {
		if (theLayout == null)
			throw new IllegalStateException("No interpreter configured for layout " + theLayoutName);
		String fieldName = getFieldName() == null ? null : getFieldName().apply(builder.getModels()).get();
		container.addHPanel(fieldName, theLayout.create(), thisContainer -> {
			modify(thisContainer, builder);
			populator.accept(thisContainer);
		});
		return builder.build();
	}
}