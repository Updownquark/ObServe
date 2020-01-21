package org.observe.entity;

import org.qommons.collect.QuickSet.QuickMap;

public interface EntityUpdate<E> extends EntityModification<E> {
	static Object NOT_SET = new Object() {
		@Override
		public String toString() {
			return "Not Set";
		}
	};

	QuickMap<String, Object> getUpdateValues();
}
