package org.observe.quick.style;

import java.util.Collections;
import java.util.Set;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelSetInstanceBuilder;
import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExModelAugmentation;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.base.MultiValueWidget;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigInterpretationException;

/** An add on for an element that may have a style sheet */
public class ExWithStyleSheet extends ExAddOn.Abstract<ExElement> {
	/** The XML name of this type */
	public static final String QUICK_STYLE_SHEET = "Quick.Style.Sheet";

	/** Definition for {@link ExWithStyleSheet} */
	@ExElementTraceable(toolkit = QuickStyleInterpretation.STYLE,
		qonfigType = "with-style-sheet",
		interpretation = Interpreted.class,
		instance = MultiValueWidget.class)
	public static class Def extends ExAddOn.Def.Abstract<ExElement, ExWithStyleSheet> {
		private QuickStyleSheet theStyleSheet;

		/**
		 * @param type This add-on's Qonfig type
		 * @param element The element this add-on affects
		 */
		public Def(QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
		}

		@Override
		public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
			return Collections.singleton((Class<? extends ExAddOn.Def<?, ?>>) ExModelAugmentation.Def.class);
		}

		/** @return The style sheet for the element */
		@QonfigChildGetter("style-sheet")
		public QuickStyleSheet getStyleSheet() {
			return theStyleSheet;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<?> element) throws QonfigInterpretationException {
			super.update(session, element);

			theStyleSheet = element.syncChild(QuickStyleSheet.class, theStyleSheet, session, "style-sheet");
			session.put(QUICK_STYLE_SHEET, theStyleSheet);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<? extends ExElement> element) {
			return new Interpreted(this, element);
		}
	}

	/** Interpretation for {@link ExWithStyleSheet} */
	public static class Interpreted extends ExAddOn.Interpreted.Abstract<ExElement, ExWithStyleSheet> {
		private QuickStyleSheet.Interpreted theStyleSheet;

		Interpreted(Def definition, ExElement.Interpreted<? extends ExElement> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		/** @return The style sheet for the element */
		public QuickStyleSheet.Interpreted getStyleSheet() {
			return theStyleSheet;
		}

		@Override
		public Class<ExWithStyleSheet> getInstanceType() {
			return ExWithStyleSheet.class;
		}

		@Override
		public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
			super.update(element);

			if (theStyleSheet != null && (getDefinition().getStyleSheet() == null
				|| theStyleSheet.getIdentity() != getDefinition().getStyleSheet().getIdentity())) {
				theStyleSheet.destroy();
				theStyleSheet = null;
			}
			if (theStyleSheet == null && getDefinition().getStyleSheet() != null)
				theStyleSheet = getDefinition().getStyleSheet().interpret(getElement());
			if (theStyleSheet != null)
				theStyleSheet.updateStyleSheet(getElement().getExpressoEnv());
		}

		@Override
		public ExWithStyleSheet create(ExElement element) {
			return new ExWithStyleSheet(element);
		}
	}

	private QuickStyleSheet.StyleSheetModels theStyleSheetModels;

	ExWithStyleSheet(ExElement element) {
		super(element);
	}

	@Override
	public Class<? extends Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	@Override
	public void update(ExAddOn.Interpreted<? extends ExElement, ?> interpreted, ExElement element) throws ModelInstantiationException {
		super.update(interpreted, element);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theStyleSheetModels = myInterpreted.getStyleSheet() == null ? null : myInterpreted.getStyleSheet().instantiateModels();
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();

		if (theStyleSheetModels != null)
			theStyleSheetModels.instantiate();
	}

	@Override
	public void addRuntimeModels(ModelSetInstanceBuilder builder, ModelSetInstance elementModels) throws ModelInstantiationException {
		super.addRuntimeModels(builder, elementModels);
		if (theStyleSheetModels != null)
			theStyleSheetModels.populate(builder);
	}
}
