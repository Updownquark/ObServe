package org.observe.quick;

import java.util.Collections;
import java.util.Set;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExModelAugmentation;
import org.qommons.config.QonfigAddOn;

/** Tag for a widget that is only used as a renderer, instead of being a first class child of its parent */
public class QuickRenderer extends ExAddOn.Abstract<QuickWidget> {
	/** Definition for a renderer */
	public static class Def extends ExAddOn.Def.Abstract<QuickWidget, QuickRenderer> {
		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The element this add-on affects
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends QuickWidget> element) {
			super(type, element);
		}

		@Override
		public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
			return (Set<Class<ExAddOn.Def<?, ?>>>) (Set<?>) Collections.singleton(ExModelAugmentation.Def.class);
		}

		@Override
		public <E2 extends QuickWidget> Interpreted interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted(this, (QuickWidget.Interpreted<?>) element);
		}
	}

	/** Interpretation of a renderer */
	public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickWidget, QuickRenderer> {
		private boolean isVirtual;

		Interpreted(Def def, QuickWidget.Interpreted<?> element) {
			super(def, element);
			isVirtual = true;
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		public QuickWidget.Interpreted<?> getElement() {
			return (QuickWidget.Interpreted<?>) super.getElement();
		}

		@Override
		public Class<QuickRenderer> getInstanceType() {
			return QuickRenderer.class;
		}

		/**
		 * @return Whether the element should be treated as a virtual component, which has consequences for style and visibility, among
		 *         other things
		 */
		public boolean isVirtual() {
			return isVirtual;
		}

		/** @param virtual whether the element should be treated as a virtual component */
		public void setVirtual(boolean virtual) {
			this.isVirtual = virtual;
		}

		@Override
		public QuickRenderer create(ExElement element) {
			return new QuickRenderer(element);
		}
	}

	QuickRenderer(ExElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}
}
