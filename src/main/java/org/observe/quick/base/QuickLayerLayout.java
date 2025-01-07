package org.observe.quick.base;

import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigAddOn;

/** A simple layout in which each component of a container occupies the full area of the container, displaying on top of each other */
public class QuickLayerLayout extends QuickLayout.Abstract {
	/** The XML name of this add-on */
	public static final String LAYER_LAYOUT = "layer-layout";

	/** {@link QuickLayerLayout} definition */
	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = LAYER_LAYOUT,
		interpretation = Interpreted.class,
		instance = QuickLayerLayout.class)
	public static class Def extends QuickLayout.Def<QuickLayerLayout> {
		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The container widget whose contents to manage
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends QuickWidget> element) {
			super(type, element);
		}

		@Override
		public <E2 extends QuickWidget> Interpreted interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted(this, element);
		}
	}

	/** {@link QuickLayerLayout} interpretation */
	public static class Interpreted extends QuickLayout.Interpreted<QuickLayerLayout> {
		/**
		 * @param definition The definition to interpret
		 * @param element The container widget whose contents to manage
		 */
		protected Interpreted(Def definition, ExElement.Interpreted<? extends QuickWidget> element) {
			super(definition, element);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public Class<QuickLayerLayout> getInstanceType() {
			return QuickLayerLayout.class;
		}

		@Override
		public QuickLayerLayout create(ExElement element) {
			return new QuickLayerLayout(element);
		}
	}

	/** @param element The container whose contents to manage */
	protected QuickLayerLayout(ExElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}
}
