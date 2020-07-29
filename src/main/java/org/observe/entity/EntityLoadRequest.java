package org.observe.entity;

import java.util.List;
import java.util.Set;

import org.qommons.QommonsUtils;
import org.qommons.collect.BetterList;
import org.qommons.collect.QuickSet.QuickMap;

/**
 * A request for information about an entity
 *
 * @param <E> The super type of all entities about which information is being requested
 */
public class EntityLoadRequest<E> {
	private final EntityChange<E> theChange;
	private final ObservableEntityType<E> theType;
	private final BetterList<EntityIdentity<E>> theEntities;
	private final Set<EntityValueAccess<? extends E, ?>> theFields;

	/**
	 * Creates a request for information about entities as a response to a change in the data source
	 *
	 * @param change The change being responded to
	 * @param fields The set of fields requested about the entities in the change
	 */
	public EntityLoadRequest(EntityChange<E> change, Set<EntityValueAccess<? extends E, ?>> fields) {
		theChange = change;
		theType = change.getEntityType();
		theEntities = QommonsUtils.map2(change.getEntities(), e -> change.getEntityType().fromSubId(e));
		theFields = fields;
	}

	/**
	 * Creates a request for information about a single entity
	 *
	 * @param entity The entity to request information for
	 * @param fields The set of fields for which values are requested
	 */
	public EntityLoadRequest(EntityIdentity<E> entity, Set<EntityValueAccess<? extends E, ?>> fields) {
		theChange = null;
		theType = entity.getEntityType();
		theEntities = BetterList.of(entity);
		theFields = fields;
	}

	/** @return The change being responded to (may be null) */
	public EntityChange<E> getChange() {
		return theChange;
	}

	/** @return The super type of all entities about which information is being requested */
	public ObservableEntityType<E> getType() {
		return theType;
	}

	/** @return The identities of all entities for which data is requested */
	public BetterList<EntityIdentity<E>> getEntities() {
		return theEntities;
	}

	/** @return The fields for which values are being requested */
	public Set<EntityValueAccess<? extends E, ?>> getFields() {
		return theFields;
	}

	/**
	 * A response to a {@link EntityLoadRequest}
	 *
	 * @param <E> The super type of all entities about which information was requested
	 */
	public static class Fulfillment<E> {
		private final EntityLoadRequest<E> theRequest;
		private final List<QuickMap<String, Object>> theResults;

		/**
		 * @param request The request being fulfilled
		 * @param results The field values for each entity for which data was requested (ordered identically)
		 */
		public Fulfillment(EntityLoadRequest<E> request, List<QuickMap<String, Object>> results) {
			theRequest = request;
			theResults = results;
		}

		/** @return The request being fulfilled */
		public EntityLoadRequest<E> getRequest() {
			return theRequest;
		}

		/** @return The field values for each entity for which data was requested (ordered identically) */
		public List<QuickMap<String, Object>> getResults() {
			return theResults;
		}
	}
}
