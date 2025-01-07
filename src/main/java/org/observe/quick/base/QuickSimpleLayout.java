package org.observe.quick.base;

import java.util.Collections;
import java.util.Set;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExModelAugmentation;
import org.observe.quick.QuickWidget;
import org.qommons.config.QonfigAddOn;

/**
 * <p>
 * Not really all that "simple", but I couldn't think of a better name.
 * </p>
 * <p>
 * This class supports dynamic constraints for each component's edges, center, and size independently, and positions can be specified
 * relative to the container's edges or its size.
 * </p>
 * <p>
 * While this class provides extreme control for positioning each widget, it does not provide any ability to position elements relative to
 * each other.
 * </p>
 */
public class QuickSimpleLayout extends QuickLayout.Abstract {
	/** The XML name of this add-on */
	public static final String SIMPLE_LAYOUT = "simple-layout";
	/** The XML name of the add on inherited by children of a container managed by a simple-layout */
	public static final String SIMPLE_LAYOUT_CHILD = "simple-layout-child";

	/** {@link QuickSimpleLayout} definition */
	public static class Def extends QuickLayout.Def<QuickSimpleLayout> {
		/**
		 * @param type The Qonfig type of this add-on
		 * @param element The container widget whose contents to manage
		 */
		public Def(QonfigAddOn type, ExElement.Def<? extends QuickWidget> element) {
			super(type, element);
		}

		@Override
		public QuickBox.Def<?> getElement() {
			return (QuickBox.Def<?>) super.getElement();
		}

		@Override
		public <E2 extends QuickWidget> Interpreted interpret(ExElement.Interpreted<E2> element) {
			return new Interpreted(this, element);
		}
	}

	/** {@link QuickSimpleLayout} interpretation */
	public static class Interpreted extends QuickLayout.Interpreted<QuickSimpleLayout> {
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
		public QuickBox.Interpreted<?> getElement() {
			return (QuickBox.Interpreted<?>) super.getElement();
		}

		@Override
		public Class<QuickSimpleLayout> getInstanceType() {
			return QuickSimpleLayout.class;
		}

		@Override
		public QuickSimpleLayout create(ExElement element) {
			return new QuickSimpleLayout(element);
		}
	}

	/** @param element The container whose contents to manage */
	protected QuickSimpleLayout(ExElement element) {
		super(element);
	}

	@Override
	public Class<Interpreted> getInterpretationType() {
		return Interpreted.class;
	}

	/** An add-on automatically inherited by children of a container managed by a {@link QuickSimpleLayout} */
	public static class Child extends ExAddOn.Abstract<QuickWidget> {
		/** {@link QuickSimpleLayout} {@link Child} definition */
		public static class Def extends ExAddOn.Def.Abstract<QuickWidget, Child> {
			/**
			 * @param type The Qonfig type of this add-on
			 * @param element The widget in the {@link QuickSimpleLayout}-managed container
			 */
			public Def(QonfigAddOn type, ExElement.Def<? extends QuickWidget> element) {
				super(type, element);
			}

			@Override
			public Set<? extends Class<? extends ExAddOn.Def<?, ?>>> getDependencies() {
				return Collections.singleton((Class<ExAddOn.Def<?, ?>>) (Class<?>) ExModelAugmentation.Def.class);
			}

			@Override
			public <E2 extends QuickWidget> Interpreted interpret(ExElement.Interpreted<E2> element) {
				return new Interpreted(this, element);
			}
		}

		/** {@link QuickSimpleLayout} {@link Child} interpretation */
		public static class Interpreted extends ExAddOn.Interpreted.Abstract<QuickWidget, Child> {
			/**
			 * @param definition The definition to interpret
			 * @param element The widget in the {@link QuickSimpleLayout}-managed container
			 */
			protected Interpreted(Def definition, ExElement.Interpreted<? extends QuickWidget> element) {
				super(definition, element);
			}

			@Override
			public Class<Child> getInstanceType() {
				return Child.class;
			}

			@Override
			public Child create(ExElement element) {
				return new Child(element);
			}
		}

		/** @param element The widget in the {@link QuickSimpleLayout}-managed container */
		protected Child(ExElement element) {
			super(element);
		}

		@Override
		public Class<Interpreted> getInterpretationType() {
			return Interpreted.class;
		}
	}
}
