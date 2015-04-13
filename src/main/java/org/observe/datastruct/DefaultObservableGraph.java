package org.observe.datastruct;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.DefaultObservableValue;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.collect.CollectionSession;
import org.observe.collect.DefaultCollectionSession;
import org.observe.collect.DefaultObservableList;
import org.observe.collect.ObservableCollection;
import org.observe.util.Transactable;
import org.observe.util.Transaction;

import prisms.lang.Type;

/**
 * Default mutable implementation of {@link ObservableGraph}
 *
 * @param <N> The type of values associated with nodes
 * @param <E> The type of values associated with edges
 */
public class DefaultObservableGraph<N, E> implements ObservableGraph<N, E>, Transactable {
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

	private CollectionSession theSession;
	private DefaultObservableValue<CollectionSession> theSessionObservable;
	private org.observe.Observer<ObservableValueEvent<CollectionSession>> theSessionController;

	private DefaultObservableList<Node<N, E>> theNodes;
	private DefaultObservableList<Edge<N, E>> theEdges;

	private List<Node<N, E>> theNodeController;
	private List<Edge<N, E>> theEdgeController;

	/**
	 * @param nodeType The type of values associated with nodes
	 * @param edgeType The type of value associated with edges
	 */
	public DefaultObservableGraph(Type nodeType, Type edgeType) {
		theLock = new ReentrantReadWriteLock();

		theSessionObservable = new DefaultObservableValue<CollectionSession>() {
			private final Type theSessionType = new Type(CollectionSession.class);

			@Override
			public Type getType() {
				return theSessionType;
			}

			@Override
			public CollectionSession get() {
				return theSession;
			}
		};
		theSessionController = theSessionObservable.control(null);

		theNodes = new DefaultObservableList<Node<N, E>>(new Type(Node.class, nodeType, edgeType), theLock, theSessionObservable) {
		};
		theNodeController = theNodes.control(null);
		theEdges = new DefaultObservableList<Edge<N, E>>(new Type(Edge.class, nodeType, edgeType), theLock, theSessionObservable) {
		};
		theEdgeController = theEdges.control(null);
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
		return theSessionObservable;
	}

	@Override
	public Transaction startTransaction(Object cause) {
		Lock lock = theLock.writeLock();
		lock.lock();
		theSession = new DefaultCollectionSession(cause);
		theSessionController.onNext(new ObservableValueEvent<>(theSessionObservable, null, theSession, cause));
		return new org.observe.util.Transaction() {
			@Override
			public void close() {
				if(theLock.getWriteHoldCount() != 1) {
					lock.unlock();
					return;
				}
				CollectionSession session = theSession;
				theSession = null;
				theSessionController.onNext(new ObservableValueEvent<>(theSessionObservable, session, null, cause));
				lock.unlock();
			}
		};
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
		try (Transaction trans = startTransaction(null);) {
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
		try (Transaction trans = startTransaction(null);) {
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
}
