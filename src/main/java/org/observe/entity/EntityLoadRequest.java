package org.observe.entity;

import java.util.List;

import org.qommons.collect.QuickSet.QuickMap;

public class EntityLoadRequest<E> {
	public static final Object LOAD = new Object() {
		@Override
		public String toString() {
			return "LOAD";
		}
	};
	private final ObservableEntityType<E> theType;
	private final List<EntityIdentity<E>> theEntities;
	private final QuickMap<String, Object> theFields;

	public EntityLoadRequest(ObservableEntityType<E> type, List<EntityIdentity<E>> entities, QuickMap<String, Object> fields) {
		theType = type;
		theEntities = entities;
		theFields = fields;
	}

	public ObservableEntityType<E> getType() {
		return theType;
	}

	public List<EntityIdentity<E>> getEntities() {
		return theEntities;
	}

	public QuickMap<String, Object> getFields() {
		return theFields;
	}

	public static class Fulfillment<E> {
		private final EntityLoadRequest<E> theRequest;
		private final List<QuickMap<String, ?>> theResults;

		public Fulfillment(EntityLoadRequest<E> request, List<QuickMap<String, ?>> results) {
			theRequest = request;
			theResults = results;
		}
	}
}
