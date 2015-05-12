package org.observe.datastruct;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableElement;

import prisms.lang.Type;

/**
 * A graph implementation based on observable collections
 *
 * @param <N> The type of values stored in the nodes of the graph
 * @param <E> The type of values stored in the edges of the graph
 */
public interface ObservableGraph<N, E> {
	/**
	 * A node in a graph
	 *
	 * @param <N> The type of values stored in the nodes of the graph
	 * @param <E> The type of values stored in the edges of the graph
	 */
	interface Node<N, E> {
		/** @return All edges that go to or from this node */
		ObservableCollection<Edge<N, E>> getEdges();

		/** @return The value associated with this node */
		N getValue();

		/** @return The collection of edges going outward from this node */
		default ObservableCollection<Edge<N, E>> getOutward() {
			return getEdges().filter(edge -> edge.getStart() == Node.this);
		}

		/** @return The collection of edges going inward toward this node */
		default ObservableCollection<Edge<N, E>> getInward() {
			return getEdges().filter(edge -> edge.getEnd() == Node.this);
		}
	}

	/**
	 * An edge between two nodes in a graph
	 *
	 * @param <N> The type of values stored in the nodes of the graph
	 * @param <E> The type of values stored in the edges of the graph
	 */
	interface Edge<N, E> {
		/** @return The node that this edge starts from */
		Node<N, E> getStart();

		/** @return The node that this edge goes to */
		Node<N, E> getEnd();

		/**
		 * @return Whether this graph edge is to be interpreted as directional, i.e. if true, this edge does not represent a connection from
		 *         {@link #getEnd() end} to {@link #getStart() start}.
		 */
		boolean isDirected();

		/** @return The value associated with this edge */
		E getValue();
	}

	/** @return An observable collection containing all nodes stored in this graph */
	ObservableCollection<Node<N, E>> getNodes();

	/** @return An observable collection containing all edges stored in this graph */
	ObservableCollection<Edge<N, E>> getEdges();

	/**
	 * @return The observable value for the current session of this graph. The session allows listeners to retain state for the duration of
	 *         a unit of work (controlled by implementation-specific means), batching events where possible. Not all events on a graph will
	 *         have a session (the value may be null). In addition, the presence or absence of a session need not imply anything about the
	 *         threaded interactions with a session. A transaction may encompass events fired and received on multiple threads. In short,
	 *         the only thing guaranteed about sessions is that they will end. Therefore, if a session is present, observers may assume that
	 *         they can delay expensive results of graph events until the session completes. The {@link ObservableCollection#getSession()
	 *         sessions} of the {@link #getNodes() node} and {@link #getEdges() edge} collections should be the same as this one.
	 */
	ObservableValue<CollectionSession> getSession();

	/**
	 * @return An observable that fires a (null) value whenever anything in this collection changes. This observable will only fire 1 event
	 *         per transaction.
	 */
	default Observable<Void> changes() {
		return observer -> {
			boolean [] initialized = new boolean[1];
			Object key = new Object();
			java.util.function.Consumer<ObservableElement<?>> listener = element -> {
				element.subscribe(new Observer<ObservableValueEvent<?>>() {
					@Override
					public <V extends ObservableValueEvent<?>> void onNext(V value) {
						if(!initialized[0])
							return;
						CollectionSession session = getSession().get();
						if(session == null)
							observer.onNext(null);
						else
							session.put(key, "changed", true);
					}

					@Override
					public <V extends ObservableValueEvent<?>> void onCompleted(V value) {
						if(!initialized[0])
							return;
						CollectionSession session = getSession().get();
						if(session == null)
							observer.onNext(null);
						else
							session.put(key, "changed", true);
					}
				});
			};
			Subscription nodeSub = getNodes().onElement(listener);
			Subscription edgeSub = getEdges().onElement(listener);
			Subscription transSub = getSession().act(event -> {
				if(!initialized[0])
					return;
				if(event.getOldValue() != null && event.getOldValue().put(key, "changed", null) != null) {
					observer.onNext(null);
				}
			});
			initialized[0] = true;
			return () -> {
				nodeSub.unsubscribe();
				edgeSub.unsubscribe();
				transSub.unsubscribe();
			};
		};
	}

	/**
	 * @param nodeValue The value to get the node for
	 * @return An observable value containing the node in this graph whose value is equal to the argument. The value may be null.
	 */
	default ObservableValue<Node<N, E>> getNode(N nodeValue) {
		return getNodes().find(node -> node.getValue().equals(nodeValue));
	}

	/**
	 * @param nodeFilter The function to filter node values (may be null to not filter node values)
	 * @param edgeFilter The function to filter edge values (may be null to not filter edge values)
	 * @return A new graph based on a subset of this graph
	 */
	default ObservableGraph<N, E> filter(Predicate<? super N> nodeFilter, Predicate<? super E> edgeFilter) {
		if(nodeFilter == null && edgeFilter == null)
			return this;
		if(nodeFilter == null) {
			nodeFilter = node -> true;
		}
		if(edgeFilter == null) {
			edgeFilter = edge -> true;
		}
		final Predicate<? super N> nf = nodeFilter;
		final Predicate<? super E> ef = edgeFilter;
		ObservableGraph<N, E> outer = this;
		class FilteredNode implements Node<N, E> {
			private final Node<N, E> wrapped;
			private final Function<Edge<N, E>, Edge<N, E>> theEdgeMap;

			FilteredNode(Node<N, E> wrap, Function<Edge<N, E>, Edge<N, E>> edgeMap) {
				wrapped = wrap;
				theEdgeMap = edgeMap;
			}

			@Override
			public ObservableCollection<Edge<N, E>> getEdges() {
				return wrapped.getEdges().filterMap(theEdgeMap);
			}

			@Override
			public N getValue() {
				return wrapped.getValue();
			}
		}
		class FilteredEdge implements Edge<N, E> {
			private final Edge<N, E> wrapped;
			private final Node<N, E> start;
			private final Node<N, E> end;

			FilteredEdge(Edge<N, E> wrap, Node<N, E> s, Node<N, E> e) {
				wrapped = wrap;
				start = s;
				end = e;
			}

			@Override
			public Node<N, E> getStart() {
				return start;
			}

			@Override
			public Node<N, E> getEnd() {
				return end;
			}

			@Override
			public boolean isDirected() {
				return wrapped.isDirected();
			}

			@Override
			public E getValue() {
				return wrapped.getValue();
			}
		}
		class FilteredGraph implements ObservableGraph<N, E> {
			private final Map<Node<N, E>, FilteredNode> theNodeMap = new org.observe.util.ConcurrentIdentityHashMap<>();
			private final Map<Edge<N, E>, FilteredEdge> theEdgeMap = new org.observe.util.ConcurrentIdentityHashMap<>();
			private final ObservableCollection<Node<N, E>> theCachedNodes = outer.getNodes().cached();
			private final ObservableCollection<Edge<N, E>> theCachedEdges = outer.getEdges().cached();

			@Override
			public ObservableCollection<Node<N, E>> getNodes() {
				return theCachedNodes.filter(node -> nf.test(node.getValue())).map(node -> filter(node));
			}

			@Override
			public ObservableCollection<Edge<N, E>> getEdges() {
				return theCachedEdges.filter(edge -> ef.test(edge.getValue())).map(edge -> filter(edge));
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
			}

			private FilteredNode filter(Node<N, E> node) {
				// TODO Not completely thread-safe
				FilteredNode ret = theNodeMap.get(node);
				if(ret == null) {
					ret = new FilteredNode(node, edge -> {
						if(!ef.test(edge.getValue()))
							return null;
						if(edge.getStart() != node && !nf.test(edge.getStart().getValue()))
							return null;
						if(edge.getEnd() != node && !nf.test(edge.getEnd().getValue()))
							return null;
						return filter(edge);
					});
					theNodeMap.put(node, ret);
				}
				return ret;
			}

			private FilteredEdge filter(Edge<N, E> edge) {
				// TODO Not completely thread-safe
				FilteredEdge ret = theEdgeMap.get(edge);
				if(ret == null) {
					ret = new FilteredEdge(edge, filter(edge.getStart()), filter(edge.getEnd()));
					theEdgeMap.put(edge, ret);
				}
				return ret;
			}
		}
		return new FilteredGraph();
	}

	// default <N2, E2> ObservableGraph<N2, E2> map(Function<N, N2> nodeMap, Function<E, E2> edgeMap) {
	// }
	//
	// default <V, N2> ObservableGraph<N2, E> combineNodes(ObservableValue<V> other, BiFunction<N, V, N2> map) {
	// }
	//
	// default <V, E2> ObservableGraph<N, E2> combineEdges(ObservableValue<V> other, BiFunction<N, E, E2> map) {
	// }

	/** @return An immutable copy of this graph */
	default ObservableGraph<N, E> immutable() {
		ObservableGraph<N, E> outer = this;
		return new ObservableGraph<N, E>() {
			@Override
			public ObservableCollection<Node<N, E>> getNodes() {
				return outer.getNodes().immutable();
			}

			@Override
			public ObservableCollection<Edge<N, E>> getEdges() {
				return outer.getEdges().immutable();
			}

			@Override
			public ObservableValue<CollectionSession> getSession() {
				return outer.getSession();
			}
		};
	}

	// default ObservableCollection<Edge<N, E>> traverse(Node<N, E> start, Node<N, E> end, Function<Edge<N, E>, Double> cost) {
	// }

	/**
	 * @param nodeType The type for the {@link #getNodes() nodes} collection
	 * @param edgeType the type for the {@link #getEdges() edges} collection
	 * @return An empty graph
	 */
	@SuppressWarnings("rawtypes")
	static ObservableGraph empty(Type nodeType, Type edgeType) {
		return new ObservableGraph() {
			@Override
			public ObservableCollection getNodes() {
				return org.observe.collect.ObservableSet.constant(nodeType);
			}

			@Override
			public ObservableCollection getEdges() {
				return org.observe.collect.ObservableSet.constant(edgeType);
			}

			@Override
			public ObservableValue getSession() {
				return ObservableValue.constant(new Type(CollectionSession.class), null);
			}
		};
	}
}
