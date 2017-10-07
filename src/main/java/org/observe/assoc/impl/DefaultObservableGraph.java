package org.observe.assoc.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;

import org.observe.assoc.ObservableGraph;
import org.observe.collect.ObservableCollection;
import org.qommons.Transaction;
import org.qommons.collect.Graph;
import org.qommons.collect.MutableGraph;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * Default mutable implementation of {@link ObservableGraph}
 *
 * @param <N> The type of values associated with nodes
 * @param <E> The type of values associated with edges
 */
public class DefaultObservableGraph<N, E> implements ObservableGraph<N, E>, MutableGraph<N, E> {
	private class DefaultNode implements ObservableGraph.Node<N, E> {
		private final N theValue;
		private final ObservableCollection<ObservableGraph.Edge<N, E>> theEdges;

		DefaultNode(N value, ObservableCollection<ObservableGraph.Edge<N, E>> edges) {
			theValue = value;
			theEdges = edges;
		}

		@Override
		public ObservableCollection<ObservableGraph.Edge<N, E>> getEdges() {
			return theEdges;
		}

		@Override
		public N getValue() {
			return theValue;
		}
	}

	private class DefaultEdge implements ObservableGraph.Edge<N, E> {
		private final ObservableGraph.Node<N, E> theStart;

		private final ObservableGraph.Node<N, E> theEnd;

		private final boolean isDirected;

		private final E theValue;

		DefaultEdge(ObservableGraph.Node<N, E> start, ObservableGraph.Node<N, E> end, boolean directed, E value) {
			theStart = start;
			theEnd = end;
			isDirected = directed;
			theValue = value;
		}

		@Override
		public ObservableGraph.Node<N, E> getStart() {
			return theStart;
		}

		@Override
		public ObservableGraph.Node<N, E> getEnd() {
			return theEnd;
		}

		@Override
		public boolean isDirected() {
			return isDirected;
		}

		@Override
		public E getValue() {
			return theValue;
		}
	}

	private final ObservableCollection<ObservableGraph.Node<N, E>> theNodes;
	private final ObservableCollection<ObservableGraph.Node<N, E>> theExposedNodes;

	private final Function<TypeToken<ObservableGraph.Edge<N, E>>, ? extends ObservableCollection<ObservableGraph.Edge<N, E>>> theEdgeCreator;
	private final ObservableCollection<ObservableGraph.Edge<N, E>> theEdges;

	/**
	 * @param nodeType The type of values associated with nodes
	 * @param edgeType The type of value associated with edges
	 */
	public DefaultObservableGraph(TypeToken<N> nodeType, TypeToken<E> edgeType) {
		this(nodeType, edgeType, ObservableCollection::create, ObservableCollection::create);
	}

	/**
	 * @param nodeType The type of values associated with nodes
	 * @param edgeType The type of value associated with edges
	 * @param nodeList Creates the list of nodes
	 * @param edgeList Creates the list of edges
	 */
	public DefaultObservableGraph(TypeToken<N> nodeType, TypeToken<E> edgeType,
		Function<TypeToken<ObservableGraph.Node<N, E>>, ? extends ObservableCollection<ObservableGraph.Node<N, E>>> nodeList,
		Function<TypeToken<ObservableGraph.Edge<N, E>>, ? extends ObservableCollection<ObservableGraph.Edge<N, E>>> edgeList) {

		TypeToken<ObservableGraph.Node<N, E>> nodesType = new TypeToken<ObservableGraph.Node<N, E>>() {}//
		.where(new TypeParameter<N>() {}, nodeType)//
		.where(new TypeParameter<E>() {}, edgeType);
		TypeToken<ObservableGraph.Edge<N, E>> edgesType = new TypeToken<ObservableGraph.Edge<N, E>>() {}//
		.where(new TypeParameter<N>() {}, nodeType)//
		.where(new TypeParameter<E>() {}, edgeType);

		theNodes = nodeList.apply(nodesType);
		theExposedNodes = theNodes.flow().immutable().collect();
		theEdgeCreator = edgeList;
		theEdges = theNodes.flow().flatMapC(edgesType, n -> n.getEdges()).collect();
	}

	@Override
	public ObservableCollection<ObservableGraph.Node<N, E>> getNodes() {
		return theExposedNodes;
	}

	@Override
	public ObservableCollection<ObservableGraph.Edge<N, E>> getEdges() {
		return theEdges;
	}

	@Override
	public ObservableGraph.Node<N, E> addNode(N value) {
		ObservableCollection<ObservableGraph.Edge<N, E>> nodeEdges = theEdgeCreator.apply(theEdges.getType());
		DefaultNode node = new DefaultNode(value, nodeEdges);
		theNodes.add(node);
		return node;
	}

	@Override
	public List<? extends ObservableGraph.Node<N, E>> addNodes(Collection<? extends N> values) {
		ArrayList<ObservableGraph.Node<N, E>> ret = new ArrayList<>(values.size());
		for(N value : values)
			ret.add(addNode(value));
		return ret;
	}

	@Override
	public ObservableGraph.Edge<N, E> addEdge(Graph.Node<N, E> start, Graph.Node<N, E> end, boolean directed, E value) {
		if (!theExposedNodes.contains(start) || !theExposedNodes.contains(end))
			throw new IllegalArgumentException("Edges may only be created between nodes already present in the graph");
		if(start == end)
			throw new IllegalArgumentException("An edge may not start and end at the same node");
		DefaultEdge edge = new DefaultEdge((ObservableGraph.Node<N, E>) start, (ObservableGraph.Node<N, E>) end, directed, value);
		theEdgeController.add(edge);
		return edge;
	}

	@Override
	public boolean removeNode(Graph.Node<N, E> node) {
		if (!theExposedNodes.contains(node))
			return false;
		try (Transaction trans = lock(true, null)) {
			java.util.Iterator<ObservableGraph.Edge<N, E>> edgeIter = theEdgeController.iterator();
			while(edgeIter.hasNext()) {
				ObservableGraph.Edge<N, E> edge = edgeIter.next();
				if(edge.getStart() == node || edge.getEnd() == node)
					edgeIter.remove();
			}
			theNodes.remove(node);
			return true;
		}
	}

	@Override
	public boolean removeEdge(Graph.Edge<N, E> edge) {
		return theEdgeController.remove(edge);
	}

	@Override
	public ObservableGraph.Node<N, E> replaceNode(Graph.Node<N, E> node, N newValue) {
		DefaultNode newNode = new DefaultNode(newValue);
		int index = theNodes.indexOf(node);
		if(index < 0)
			return null;
		try (Transaction trans = lock(true, null)) {
			theNodes.add(index + 1, newNode);
			java.util.ListIterator<ObservableGraph.Edge<N, E>> edgeIter = theEdgeController.listIterator();
			while(edgeIter.hasNext()) {
				ObservableGraph.Edge<N, E> edge = edgeIter.next();
				if(edge.getStart() == node)
					edgeIter.set(new DefaultEdge(newNode, edge.getEnd(), edge.isDirected(), edge.getValue()));
				else if(edge.getEnd() == node)
					edgeIter.set(new DefaultEdge(edge.getStart(), newNode, edge.isDirected(), edge.getValue()));
			}
			theNodes.remove(node);
			return newNode;
		}
	}

	/**
	 * Fires a set event on the given node, perhaps signifying that its internal value has changed
	 *
	 * @param node The node to fire the event on
	 */
	public void reset(ObservableGraph.Node<N, E> node) {
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			int index = theNodes.indexOf(node);
			if(index < 0)
				return;
			theNodes.set(index, node);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Fires a set event on the given edge, perhaps signifying that its internal value has changed
	 *
	 * @param edge The edge to fire the event on
	 */
	public void reset(ObservableGraph.Edge<N, E> edge) {
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			int index = theEdgeController.indexOf(edge);
			if(index < 0)
				return;
			theEdgeController.set(index, edge);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void clear() {
		try (Transaction trans = lock(true, null)) {
			theEdgeController.clear();
			theNodes.clear();
		}
	}

	@Override
	public void clearEdges() {
		try (Transaction trans = lock(true, null)) {
			theEdgeController.clear();
		}
	}

	@Override
	public ObservableGraph<N, E> immutable() {
		return ObservableGraph.super.immutable();
	}
}
