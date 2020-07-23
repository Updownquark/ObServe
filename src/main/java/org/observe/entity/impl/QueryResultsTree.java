package org.observe.entity.impl;

import java.lang.ref.WeakReference;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.observe.entity.EntityChange;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQuery;
import org.observe.entity.EntityValueAccess;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.PreparedQuery;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.tree.BetterTreeList;

/**
 * Hierarchical query results structure that allow more specific queries to be populated with the results from more general queries that
 * have already been made.
 */
public class QueryResultsTree {
	/**
	 * Used to produce valid members of the entity set from (possibly) shell entity wrappers for query results
	 *
	 * @see QueryResults#handleChange(EntityChange, Map, EntityUsage)
	 */
	public interface EntityUsage {
		/**
		 * @param <E> The entity type
		 * @param entity The entity (possibly a shell) to insert into query results
		 * @return The bona-fide member entity to insert
		 */
		<E> ObservableEntity<E> use(ObservableEntity<E> entity);
	}

	private final ObservableEntityDataSetImpl theEntitySet;
	private final QueryResultNode theRoot;

	/** @param entitySet The entity set that this results tree is for */
	public QueryResultsTree(ObservableEntityDataSetImpl entitySet) {
		theEntitySet = entitySet;
		theRoot = new QueryResultNode(null, null, false);
	}

	/**
	 * Adds or gets results for a query
	 *
	 * @param query The query
	 * @param count Whether the results are to be in the form of a {@link EntityQuery#count() count} or a
	 *        {@link EntityQuery#collect(boolean) collection}
	 * @return The query results
	 * @throws EntityOperationException If the query execution fails immediately
	 */
	public <E> QueryResults<E> addQuery(EntityQuery<E> query, boolean count) throws EntityOperationException {
		EntityCondition<E> selection = query.getSelection();
		if (query instanceof PreparedQuery)
			selection = selection.satisfy(((PreparedQuery<E>) query).getVariableValues());
		return theRoot.addQuery(selection, count).getResults(query);
	}

	/**
	 * This method allows query results to specify what information they need to correctly account for a change to the entity set.
	 *
	 * @param change The change from the data source
	 * @param entities All entities in the change, mapped by ID. The entities here may
	 * @return Any fields needed to account for the change that are not already loaded
	 * @see QueryResults#specifyDataRequirements(EntityChange, Map, Set)
	 */
	public Set<EntityValueAccess<?, ?>> specifyDataRequirements(EntityChange<?> change,
		Map<EntityIdentity<?>, ObservableEntity<?>> entities) {
		Set<EntityValueAccess<?, ?>> fields = new LinkedHashSet<>();
		theRoot.specifyDataRequirements(change, entities, fields);
		return fields;
	}

	/**
	 * Handles a change from the data source, using entities populated with all data specified by
	 * {@link #specifyDataRequirements(EntityChange, Map)}.
	 *
	 * @param change The change to handle
	 * @param entities All entities referenced by the change, by ID
	 * @param usage The usage function to obtain insertable entities
	 * @see QueryResults#handleChange(EntityChange, Map, EntityUsage)
	 */
	public void handleChange(EntityChange<?> change, Map<EntityIdentity<?>, ObservableEntity<?>> entities, EntityUsage usage) {
		theRoot.handleChange(change, entities, usage);
	}

	/** Disposes of all results in this tree */
	public void dispose() {
		theRoot.dispose();
	}

	/** A tree node in a {@link QueryResultsTree} */
	public class QueryResultNode {
		final EntityCondition<?> theSelection;
		final boolean isCount;
		WeakReference<? extends QueryResults<?>> theResults;
		QueryResultNode parent;
		ElementId parentChildEl;
		final BetterList<QueryResultNode> children;

		QueryResultNode(EntityCondition<?> selection, QueryResultNode parent, boolean count) {
			theSelection = selection;
			isCount = count;
			this.parent = parent;
			children = BetterTreeList.<QueryResultNode> build().safe(false).build();
		}

		<E> QueryResults<E> getResults(EntityQuery<E> query) throws EntityOperationException {
			QueryResults<E> results = null;
			if (theResults != null)
				results = (QueryResults<E>) theResults.get();
			if (results == null) {
				results = new QueryResults<>(this, (EntityCondition<E>) theSelection, isCount);
				theResults = new WeakReference<>(results);
				QueryResults<?> parentResults = null;
				QueryResultNode p = parent;
				while ((parentResults == null || !parentResults.isActive()) && p.theSelection != null) {
					parentResults = p.theResults.get();
					p = p.parent;
				}
				if (parentResults != null)
					results.init(parentResults.getResults(), true);
				else
					theEntitySet.executeQuery(query, results);
			}
			return results;
		}

		void fulfill(Iterable<? extends ObservableEntity<?>> entities) {
			_fulfill(entities, false);
		}

		private void _fulfill(Iterable<? extends ObservableEntity<?>> entities, boolean needsFilter) {
			QueryResults<?> results = theResults.get();
			if (results != null) {
				results.init(entities, needsFilter);
				entities = results.getResults();
			}
			for (QueryResultNode child : children)
				child._fulfill(entities, true);
			if (results == null)
				remove();
		}

		QueryResultNode addQuery(EntityCondition<?> selection, boolean count) {
			List<ElementId> contained = null;
			for (CollectionElement<QueryResultNode> child : children.elements()) {
				if (!child.get().isCount && child.get().theSelection.contains(selection))
					return child.get().addQuery(selection, count);
				else if (selection.contains(child.get().theSelection)) {
					if (contained == null)
						contained = new LinkedList<>();
					contained.add(child.getElementId());
				}
			}
			QueryResultNode newNode = new QueryResultNode(selection, this, count);
			if (contained != null) {
				for (ElementId childEl : contained) {
					MutableCollectionElement<QueryResultNode> child = children.mutableElement(childEl);
					newNode.addChild(child.get());
					child.remove();
				}
			}
			children.add(newNode);
			return newNode;
		}

		void specifyDataRequirements(EntityChange<?> change, Map<EntityIdentity<?>, ObservableEntity<?>> entities,
			Set<EntityValueAccess<?, ?>> fields) {
			QueryResults<?> results = null;
			if (theResults != null)
				results = theResults.get();
			boolean validResults = results != null && results.isActive();
			if (validResults)
				results.specifyDataRequirements(change, entities, fields);
			for (QueryResultNode child : children)
				child.specifyDataRequirements(change, entities, fields);
			if (theSelection != null && !validResults)
				remove();
		}

		void handleChange(EntityChange<?> change, Map<EntityIdentity<?>, ObservableEntity<?>> entities, EntityUsage usage) {
			QueryResults<?> results = null;
			if (theResults != null)
				results = theResults.get();
			boolean validResults = results != null && results.isActive();
			if (validResults)
				results.handleChange(change, entities, usage);
			for (QueryResultNode child : children)
				child.handleChange(change, entities, usage);
			if (theSelection != null && !validResults)
				remove();
		}

		void dispose() {
			QueryResults<?> results = theResults == null ? null : theResults.get();
			if (results != null)
				results.cancel(true);
		}

		private void addChild(QueryResultNode child) {
			child.parent = this;
			child.parentChildEl = children.addElement(child, false).getElementId();
		}

		void remove() {
			for (QueryResultNode child : children)
				parent.addChild(child);
			parent.children.mutableElement(parentChildEl).remove();
		}

		@Override
		public String toString() {
			if (theSelection == null)
				return "(placeholder)";
			return theSelection.getEntityType() + ": " + theSelection + (isCount ? " count" : "");
		}
	}

	static class EntityTypeFilteredIdentityList<E> extends AbstractCollection<EntityIdentity<E>> {
		private final List<EntityIdentity<?>> theChanges;
		private final ObservableEntityType<E> theType;

		private int theSize;

		EntityTypeFilteredIdentityList(List<EntityIdentity<?>> changes, ObservableEntityType<E> type) {
			theChanges = changes;
			theType = type;
			theSize = -1;
		}

		@Override
		public Iterator<EntityIdentity<E>> iterator() {
			Iterator<EntityIdentity<?>> changeIter = theChanges.iterator();
			return new Iterator<EntityIdentity<E>>() {
				private EntityIdentity<E> next;

				@Override
				public boolean hasNext() {
					while (next == null && changeIter.hasNext()) {
						EntityIdentity<?> change = changeIter.next();
						if (theType.isAssignableFrom(change.getEntityType()))
							next = (EntityIdentity<E>) change;
					}
					return next != null;
				}

				@Override
				public EntityIdentity<E> next() {
					if (!hasNext())
						throw new NoSuchElementException();
					EntityIdentity<E> ret = next;
					next = null;
					return ret;
				}
			};
		}

		@Override
		public int size() {
			if (theSize < 0) {
				theSize = 0;
				for (EntityIdentity<?> change : theChanges) {
					if (theType.isAssignableFrom(change.getEntityType()))
						theSize++;
				}
			}
			return theSize;
		}
	}
}
