package org.observe.util.swing;

public interface ModelPath<T> extends ModelRow<T> {
	ModelPath<T> getParent();
}