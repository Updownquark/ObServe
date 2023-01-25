package org.observe.quick;

import java.util.List;

import org.observe.quick.style.StyleQIS;
import org.observe.util.swing.PanelPopulation.PanelPopulator;
import org.qommons.config.QonfigEvaluationException;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.ex.ExConsumer;

public class QuickBox extends QuickContainer.AbstractQuickContainer {
	private final String theLayoutName;
	private QuickLayout theLayout;

	public QuickBox(StyleQIS session, List<QuickComponentDef> children, String layoutName) throws QonfigInterpretationException {
		super(session, children);
		theLayoutName = layoutName;
	}

	public QuickBox setLayout(QuickLayout layout) {
		theLayout = layout;
		return this;
	}

	@Override
	public QuickComponent installContainer(PanelPopulator<?, ?> container, QuickComponent.Builder builder,
		ExConsumer<PanelPopulator<?, ?>, QonfigEvaluationException> populator) throws QonfigEvaluationException {
		if (theLayout == null)
			throw new IllegalStateException("No interpreter configured for layout " + theLayoutName);
		String fieldName = getFieldName() == null ? null : getFieldName().apply(builder.getModels()).get();
		try {
			container.addHPanel(fieldName, theLayout.create(), thisContainer -> {
				modify(thisContainer, builder);
				try {
					populator.accept(thisContainer);
				} catch (QonfigEvaluationException e) {
					throw new CheckedExceptionWrapper(e);
				}
			});
		} catch (CheckedExceptionWrapper e) {
			throw (QonfigEvaluationException) e.getCause();
		}
		return builder.build();
	}
}
