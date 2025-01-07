package org.observe.quick.base;

import java.util.Collections;
import java.util.Set;

import javax.swing.Box;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExModelAugmentation;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigAddOn;

/** Represents a layout that will control how widget contents are laid out in a container, like a {@link Box} */
public interface QuickLayout extends ExAddOn<QuickWidget> {
	/**
	 * {@link QuickLayout} definition
	 *
	 * @param <L> The type of the layout
	 */
	public abstract class Def<L extends QuickLayout> extends ExAddOn.Def.Abstract<QuickWidget, L> {
		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The container widget whose contents to manage
		 */
		protected Def(QonfigAddOn type, ExElement.Def<? extends QuickWidget> element) {
			super(type, element);
		}

		@Override
		public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
			return (Set<Class<ExAddOn.Def<?, ?>>>) (Set<?>) Collections.singleton(ExModelAugmentation.Def.class);
		}

		@Override
		public abstract <E2 extends QuickWidget> Interpreted<L> interpret(ExElement.Interpreted<E2> element);
	}

	/**
	 * {@link QuickLayout} interpretation
	 *
	 * @param <L> The type of the layout
	 */
	public abstract class Interpreted<L extends QuickLayout> extends ExAddOn.Interpreted.Abstract<QuickWidget, L> {
		/**
		 * @param definition The definition to interpret
		 * @param element The container widget whose contents to manage
		 */
		protected Interpreted(Def<L> definition, ExElement.Interpreted<? extends QuickWidget> element) {
			super(definition, element);
		}

		@Override
		public Def<L> getDefinition() {
			return (Def<L>) super.getDefinition();
		}
	}

	/** {@link QuickLayout} abstract implementation */
	public abstract class Abstract extends ExAddOn.Abstract<QuickWidget> implements QuickLayout {
		/** @param element The container widget whose contents to manage */
		protected Abstract(ExElement element) {
			super(element);
		}
	}
}
