package org.observe.assoc;

import java.util.Collection;
import java.util.Objects;

import org.observe.Eventable;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.qommons.Lockable.CoreId;
import org.qommons.Transaction;
import org.qommons.collect.Graph;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.TransactableGraph;

/**
 * A graph implementation based on observable collections
 *
 * @param <N> The type of values stored in the nodes of the graph
 * @param <E> The type of values stored in the edges of the graph
 */
public interface ObservableGraph<N, E> extends TransactableGraph<N, E>, Eventable {
	/**
	 * A node in a graph
	 *
	 * @param <N> The type of values stored in the nodes of the graph
	 * @param <E> The type of values stored in the edges of the graph
	 */
	interface Node<N, E> extends Graph.Node<N, E>, SettableValue<N> {
		@Override
		ObservableCollection<? extends Edge<N, E>> getEdges();

		@Override
		ObservableCollection<? extends Edge<N, E>> getOutward();

		@Override
		ObservableCollection<? extends Edge<N, E>> getInward();

		@Override
		default ObservableGraph.Node<N, E> unsettable() {
			ObservableGraph.Node<N, E> source = this;
			return new ObservableGraph.Node<N, E>() {
				@Override
				public long getStamp() {
					return source.getStamp();
				}

				@Override
				public Object getIdentity() {
					return source.getIdentity();
				}

				@Override
				public boolean isLockSupported() {
					return source.isLockSupported();
				}

				@Override
				public Transaction lock(boolean write, Object cause) {
					return source.lock(write, cause);
				}

				@Override
				public Transaction tryLock(boolean write, Object cause) {
					return source.tryLock(write, cause);
				}

				@Override
				public Collection<Cause> getCurrentCauses() {
					return source.getCurrentCauses();
				}

				@Override
				public N get() {
					return source.get();
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION);
				}

				@Override
				public <V extends N> String isAcceptable(V value) {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public <V extends N> N set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}

				@Override
				public Observable<ObservableValueEvent<N>> noInitChanges() {
					return source.noInitChanges();
				}

				@Override
				public Transaction lock() {
					return source.lock();
				}

				@Override
				public ObservableCollection<? extends Edge<N, E>> getEdges() {
					return source.getEdges().flow().<Edge<N, E>> transform(tx -> tx.cache(false).map(Edge::unsettable)).unmodifiable()
						.collectPassive();
				}

				@Override
				public ObservableCollection<? extends Edge<N, E>> getOutward() {
					return source.getOutward().flow().<Edge<N, E>> transform(tx -> tx.cache(false).map(Edge::unsettable)).unmodifiable()
						.collectPassive();
				}

				@Override
				public ObservableCollection<? extends Edge<N, E>> getInward() {
					return source.getInward().flow().<Edge<N, E>> transform(tx -> tx.cache(false).map(Edge::unsettable)).unmodifiable()
						.collectPassive();
				}

				@Override
				public Node<N, E> unsettable() {
					return this;
				}
			};
		}
	}

	/**
	 * An edge between two nodes in a graph
	 *
	 * @param <N> The type of values stored in the nodes of the graph
	 * @param <E> The type of values stored in the edges of the graph
	 */
	interface Edge<N, E> extends Graph.Edge<N, E>, SettableValue<E> {
		@Override
		Node<N, E> getStart();

		@Override
		Node<N, E> getEnd();

		@Override
		boolean isDirected();

		@Override
		default ObservableGraph.Edge<N, E> unsettable() {
			ObservableGraph.Edge<N, E> source = this;
			return new ObservableGraph.Edge<N, E>() {
				@Override
				public long getStamp() {
					return source.getStamp();
				}

				@Override
				public Object getIdentity() {
					return source.getIdentity();
				}

				@Override
				public boolean isLockSupported() {
					return source.isLockSupported();
				}

				@Override
				public Transaction lock(boolean write, Object cause) {
					return source.lock(write, cause);
				}

				@Override
				public Transaction tryLock(boolean write, Object cause) {
					return source.tryLock(write, cause);
				}

				@Override
				public Collection<Cause> getCurrentCauses() {
					return source.getCurrentCauses();
				}

				@Override
				public E get() {
					return source.get();
				}

				@Override
				public ObservableValue<String> isEnabled() {
					return ObservableValue.of(StdMsg.UNSUPPORTED_OPERATION);
				}

				@Override
				public <V extends E> String isAcceptable(V value) {
					return StdMsg.UNSUPPORTED_OPERATION;
				}

				@Override
				public <V extends E> E set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
				}

				@Override
				public Observable<ObservableValueEvent<E>> noInitChanges() {
					return source.noInitChanges();
				}

				@Override
				public Transaction lock() {
					return source.lock();
				}

				@Override
				public Node<N, E> getStart() {
					return source.getStart().unsettable();
				}

				@Override
				public Node<N, E> getEnd() {
					return source.getEnd().unsettable();
				}

				@Override
				public boolean isDirected() {
					return source.isDirected();
				}

				@Override
				public Edge<N, E> unsettable() {
					return this;
				}
			};
		}
	}

	@Override
	ObservableCollection<? extends Node<N, E>> getNodes();

	@Override
	ObservableCollection<N> getNodeValues();

	@Override
	ObservableCollection<? extends Edge<N, E>> getEdges();

	@Override
	default Node<N, E> nodeFor(N value) {
		for (Node<N, E> node : getNodes())
			if (Objects.equals(node.get(), value))
				return node;
		return null;
	}

	/**
	 * @return An observable that fires a (null) value whenever anything in this collection changes. This observable will only fire 1 event
	 *         per transaction.
	 */
	default Observable<Object> changes() {
		return Observable.or(getNodes().simpleChanges(), getEdges().simpleChanges());
	}

	/**
	 * @param nodeValue The value to get the node for
	 * @return An observable value containing the node in this graph whose value is equal to the argument. The value may be null.
	 */
	default ObservableValue<? extends Node<N, E>> getNode(N nodeValue) {
		return getNodes().observeFind(node -> node.get().equals(nodeValue)).first().find();
	}

	/** @return A representation of this graph that is not modifiable */
	default ObservableGraph<N, E> unmodifiable() {
		ObservableGraph<N, E> source = this;
		return new ObservableGraph<N, E>() {
			@Override
			public boolean isEventing() {
				return source.isEventing();
			}

			@Override
			public ObservableCollection<? extends ObservableGraph.Node<N, E>> getNodes() {
				return source.getNodes().flow().<Node<N, E>> transform(tx -> tx.cache(false).map(Node::unsettable)).unmodifiable()
					.collectPassive();
			}

			@Override
			public ObservableCollection<N> getNodeValues() {
				return source.getNodeValues().flow().unmodifiable().collectPassive();
			}

			@Override
			public ObservableCollection<? extends ObservableGraph.Edge<N, E>> getEdges() {
				return source.getEdges().flow().<Edge<N, E>> transform(tx -> tx.cache(false).map(Edge::unsettable)).unmodifiable()
					.collectPassive();
			}

			@Override
			public ObservableGraph<N, E> unmodifiable() {
				return this;
			}
		};
	}

	/**
	 * @param <N> The type of node values in the graph
	 * @param <E> The type of edge values in the graph
	 * @param nodeType The node type for the graph
	 * @param edgeType The edge type for the graph
	 * @return An empty graph
	 */
	static <N, E> ObservableGraph<N, E> empty() {
		ObservableCollection<Node<N, E>> nodes = ObservableCollection.of();
		ObservableCollection<Edge<N, E>> edges = ObservableCollection.of();
		ObservableCollection<N> nodeValues = ObservableCollection.of();
		return new ObservableGraph<N, E>() {
			@Override
			public boolean isEventing() {
				return nodes.isEventing() || edges.isEventing();
			}

			@Override
			public ObservableCollection<Node<N, E>> getNodes() {
				return nodes;
			}

			@Override
			public ObservableCollection<N> getNodeValues() {
				return nodeValues;
			}

			@Override
			public ObservableCollection<Edge<N, E>> getEdges() {
				return edges;
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return Transaction.NONE;
			}

			@Override
			public Transaction tryLock(boolean write, Object cause) {
				return Transaction.NONE;
			}

			@Override
			public CoreId getCoreId() {
				return CoreId.EMPTY;
			}
		};
	}
}
