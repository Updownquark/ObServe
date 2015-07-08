package org.observe.datastruct.impl;

import java.util.Collection;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.ObservableValue;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableList;
import org.observe.collect.impl.ObservableArrayList;
import org.observe.datastruct.ObservableGraph;
import org.observe.util.DefaultTransactable;
import org.observe.util.Transaction;

import prisms.lang.Type;

/**
 * Default mutable implementation of {@link ObservableGraph}
 *
 * @param <N> The type of values associated with nodes
 * @param <E> The type of values associated with edges
 */
public class DefaultObservableGraph<N, E> implements ObservableGraph<N, E> {
	private class DefaultNode implements Node<N, E> {
		private final N theValue;

		DefaultNode(N value) {
			theValue = value;
		}

		@Override
		public ObservableCollection<Edge<N, E>> getEdges() {
			return DefaultObservableGraph.this.getEdges().filter(edge -> edge.getStart() == this || edge.getEnd() == this);
		}

		@Override
		public N getValue() {
			return theValue;
		}
	}

	private class DefaultEdge implements Edge<N, E> {
		private final Node<N, E> theStart;
		private final Node<N, E> theEnd;
		private final boolean isDirected;
		private final E theValue;

		DefaultEdge(Node<N, E> start, Node<N, E> end, boolean directed, E value) {
			theStart = start;
			theEnd = end;
			isDirected = directed;
			theValue = value;
		}

		@Override
		public Node<N, E> getStart() {
			return theStart;
		}

		@Override
		public Node<N, E> getEnd() {
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

	private ReentrantReadWriteLock theLock;

	private DefaultTransactable theSessionController;

	private ObservableList<Node<N, E>> theNodes;
	private ObservableList<Edge<N, E>> theEdges;

	private ObservableList<Node<N, E>> theNodeController;
	private ObservableList<Edge<N, E>> theEdgeController;

	/**
	 * @param nodeType The type of values associated with nodes
	 * @param edgeType The type of value associated with edges
	 */
	public DefaultObservableGraph(Type nodeType, Type edgeType) {
		theLock = new ReentrantReadWriteLock();

		theSessionController = new DefaultTransactable(theLock);

		theNodeController = new ObservableArrayList<>(new Type(Node.class, nodeType, edgeType), theLock, theSessionController.getSession(),
			theSessionController);
		theNodes = theNodeController.immutable();
		theEdgeController = new ObservableArrayList<>(new Type(Edge.class, nodeType, edgeType), theLock, theSessionController.getSession(),
			theSessionController);
		theEdges = theEdgeController.immutable();
	}

	@Override
	public ObservableCollection<Node<N, E>> getNodes() {
		return theNodes;
	}

	@Override
	public ObservableCollection<Edge<N, E>> getEdges() {
		return theEdges;
	}

	@Override
	public ObservableValue<CollectionSession> getSession() {
		return theSessionController.getSession();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theSessionController.lock(write, cause);
	}

	/**
	 * Adds a node to the graph
	 *
	 * @param value The value for the node to have
	 * @return The node that was created and added
	 */
	public Node<N, E> addNode(N value) {
		DefaultNode node = new DefaultNode(value);
		theNodeController.add(node);
		return node;
	}

	/**
	 * Adds a collection of nodes to a graph
	 *
	 * @param values The node values to add
	 */
	public void addNodes(Collection<? extends N> values) {
		for(N value : values)
			addNode(value);
	}

	/**
	 * Adds an edge between two nodes which must already be in the graph. The nodes cannot be the same.
	 *
	 * @param start The node for the edge to start at
	 * @param end The node for the edge to end at
	 * @param directed Whether the edge is directed (i.e. one-way)
	 * @param value The value to associate with the new edge
	 * @return The edge that was created and added
	 */
	public Edge<N, E> addEdge(Node<N, E> start, Node<N, E> end, boolean directed, E value) {
		if(!theNodes.contains(start) || !theNodes.contains(end))
			throw new IllegalArgumentException("Edges may only be created between nodes already present in the graph");
		if(start == end)
			throw new IllegalArgumentException("An edge may not start and end at the same node");
		DefaultEdge edge = new DefaultEdge(start, end, directed, value);
		theEdgeController.add(edge);
		return edge;
	}

	/**
	 * @param node The node to remove from the graph along with all its edges
	 * @return Whether the node was found in the graph
	 */
	public boolean removeNode(Node<N, E> node) {
		if(!theNodes.contains(node))
			return false;
		try (Transaction trans = lock(true, null)) {
			java.util.Iterator<Edge<N, E>> edgeIter = theEdgeController.iterator();
			while(edgeIter.hasNext()) {
				Edge<N, E> edge = edgeIter.next();
				if(edge.getStart() == node || edge.getEnd() == node)
					edgeIter.remove();
			}
			theNodeController.remove(node);
			return true;
		}
	}

	/**
	 * @param edge The edge to remove from the graph
	 * @return Whether the edge was found in the graph
	 */
	public boolean removeEdge(Edge<N, E> edge) {
		return theEdgeController.remove(edge);
	}

	/**
	 * Replaces a node in the graph with a new node having a different value. This method is useful because the value of a node cannot be
	 * directly modified. All edges referring to the given node will be replaced with equivalent edges referring to the new node.
	 *
	 * @param node The node to replace
	 * @param newValue The value for the new node to have
	 * @return The node that was created and added
	 */
	public Node<N, E> replaceNode(Node<N, E> node, N newValue) {
		DefaultNode newNode = new DefaultNode(newValue);
		int index = theNodeController.indexOf(node);
		if(index < 0)
			return null;
		try (Transaction trans = lock(true, null)) {
			theNodeController.add(index + 1, newNode);
			java.util.ListIterator<Edge<N, E>> edgeIter = theEdgeController.listIterator();
			while(edgeIter.hasNext()) {
				Edge<N, E> edge = edgeIter.next();
				if(edge.getStart() == node)
					edgeIter.set(new DefaultEdge(newNode, edge.getEnd(), edge.isDirected(), edge.getValue()));
				else if(edge.getEnd() == node)
					edgeIter.set(new DefaultEdge(edge.getStart(), newNode, edge.isDirected(), edge.getValue()));
			}
			theNodeController.remove(node);
			return newNode;
		}
	}

	/**
	 * Fires a set event on the given node, perhaps signifying that its internal value has changed
	 *
	 * @param node The node to fire the event on
	 */
	public void reset(Node<N, E> node) {
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			int index = theNodeController.indexOf(node);
			if(index < 0)
				return;
			theNodeController.set(index, node);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Fires a set event on the given edge, perhaps signifying that its internal value has changed
	 *
	 * @param edge The edge to fire the event on
	 */
	public void reset(Edge<N, E> edge) {
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

	/** Removes all nodes and edges from this graph */
	public void clear() {
		try (Transaction trans = lock(true, null)) {
			theEdgeController.clear();
			theNodeController.clear();
		}
	}

	/** Removes all edges from this graph */
	public void clearEdges() {
		try (Transaction trans = lock(true, null)) {
			theEdgeController.clear();
		}
	}
}
