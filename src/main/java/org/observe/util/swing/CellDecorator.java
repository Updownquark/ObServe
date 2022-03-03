package org.observe.util.swing;

import java.util.function.Consumer;

public interface CellDecorator<R, C> {
	public static final CellDecorator<Object, Object> DEFAULT = (cell, decorator) -> {};

	public static <R, C> CellDecorator<R, C> decorator() {
		return (CellDecorator<R, C>) DEFAULT;
	}

	public static <R, C> CellDecorator<R, C> constant(Consumer<ComponentDecorator> decorator) {
		return (cell, deco) -> decorator.accept(deco);
	}

	void decorate(ModelCell<? extends R, ? extends C> cell, ComponentDecorator decorator);

	default CellDecorator<R, C> modify(CellDecorator<R, C> other) {
		return (cell, decorator) -> {
			decorate(cell, decorator);
			other.decorate(cell, decorator);
		};
	}
}