package org.observe.quick.base;

import org.observe.expresso.qonfig.ExElement;
import org.qommons.config.QonfigElementOrAddOn;

public abstract class QuickAbstractMenuItem<T> extends QuickLabel<T> {
	public static abstract class Def<MI extends QuickAbstractMenuItem<?>> extends QuickLabel.Def<MI> {
		protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@Override
		public abstract Interpreted<?, ? extends MI> interpret(ExElement.Interpreted<?> parent);
	}

	public static abstract class Interpreted<T, MI extends QuickAbstractMenuItem<T>> extends QuickLabel.Interpreted<T, MI> {
		protected Interpreted(Def<? super MI> definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def<? super MI> getDefinition() {
			return (Def<? super MI>) super.getDefinition();
		}

		@Override
		public abstract MI create();
	}

	protected QuickAbstractMenuItem(Object id) {
		super(id);
	}

	@Override
	public QuickAbstractMenuItem<T> copy(ExElement parent) {
		return (QuickAbstractMenuItem<T>) super.copy(parent);
	}
}
