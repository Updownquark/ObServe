package org.observe.util.swing;

public interface CellDecorator<R, C> {
	public static final CellDecorator<Object, Object> DEFAULT = (cell, decorator) -> {};

	public static <R, C> CellDecorator<R, C> decorator() {
		return (CellDecorator<R, C>) DEFAULT;
	}

	void decorate(ModelCell<R, C> cell, ComponentDecorator decorator);

	default CellDecorator<R, C> modify(CellDecorator<R, C> other) {
		return (cell, decorator) -> {
			decorate(cell, decorator);
			other.decorate(cell, decorator);
		};
	}
}