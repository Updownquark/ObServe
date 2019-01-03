package org.observe.entity;

public interface EntityModification<E> extends EntitySetOperation<E> {
	long execute();
}
