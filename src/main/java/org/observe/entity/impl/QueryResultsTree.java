package org.observe.entity.impl;

import java.lang.ref.WeakReference;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.observe.entity.EntityChange;
import org.observe.entity.EntityCondition;
import org.observe.entity.EntityIdentity;
import org.observe.entity.EntityLoadRequest;
import org.observe.entity.EntityOperationException;
import org.observe.entity.EntityQuery;
import org.observe.entity.ObservableEntity;
import org.observe.entity.ObservableEntityType;
import org.observe.entity.PreparedQuery;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.tree.BetterTreeList;

public class QueryResultsTree {
	private final ObservableEntityDataSetImpl theEntitySet;
	private final QueryResultNode theRoot;

	public QueryResultsTree(ObservableEntityDataSetImpl entitySet) {
		theEntitySet = entitySet;
		theRoot = new QueryResultNode(null, null, false);
	}

	public <E> QueryResults<E> addQuery(EntityQuery<E> query, boolean count) throws EntityOperationException {
		EntityCondition<E> selection = query.getSelection();
		if (query instanceof PreparedQuery)
			selection = selection.satisfy(((PreparedQuery<E>) query).getVariableValues());
		return theRoot.addQuery(selection, count).getResults(query);
	}

	public void addDataRequests(List<EntityChange<?>> changes, List<EntityLoadRequest<?>> loadRequests,
		Map<EntityIdentity<?>, ObservableEntityImpl<?>> entities) {
		theRoot.addDataRequests(changes, loadRequests, entities);
	}

	public void handleChange(EntityChange<?> change, Map<EntityIdentity<?>, ObservableEntityImpl<?>> entities) {
		theRoot.handleChange(change, entities);
	}

	public void dispose() {
		theRoot.dispose();
	}

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
				else {
					try {
						theEntitySet.executeQuery(query, results);
					} catch (EntityOperationException e) {
						results.failed(e);
						throw e;
					}
				}
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
			return newNode;
		}

		void addDataRequests(List<EntityChange<?>> changes, List<EntityLoadRequest<?>> loadRequests,
			Map<EntityIdentity<?>, ObservableEntityImpl<?>> entities) {
			QueryResults<?> results = null;
			if (theResults != null)
				results = theResults.get();
			boolean validResults = results != null && results.isActive();
			if (validResults)
				results.addDataRequests(changes, loadRequests, entities);
			for (QueryResultNode child : children)
				child.addDataRequests(changes, loadRequests, entities);
			if (theSelection != null && !validResults)
				remove();
		}

		void handleChange(EntityChange<?> change, Map<EntityIdentity<?>, ObservableEntityImpl<?>> entities) {
			QueryResults<?> results = null;
			if (theResults != null)
				results = theResults.get();
			boolean validResults = results != null && results.isActive();
			if (validResults)
				results.handleChange(change, entities);
			for (QueryResultNode child : children)
				child.handleChange(change, entities);
			if (theSelection != null && !validResults)
				remove();
		}

		void dispose() {
			QueryResults<?> results = theResults == null ? null : theResults.get();
			if (results != null) {
				results.cancel(true);
				results.dispose();
			}
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
