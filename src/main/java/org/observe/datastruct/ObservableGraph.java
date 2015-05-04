package org.observe.datastruct;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.collect.CollectionChangeEvent;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;

import prisms.lang.Type;

public interface ObservableGraph<N, E> {
	interface Node<N, E> {
		ObservableCollection<Edge<N, E>> getEdges();

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

	interface Edge<N, E> {
		Node<N, E> getStart();

		Node<N, E> getEnd();

		boolean isDirected();

		E getValue();
	}

	ObservableCollection<Node<N, E>> getNodes();

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

	default Observable<CollectionChangeEvent<?>> changes() {
		return Observable.or(getNodes().changes(), getEdges().changes());
	}

	default ObservableValue<Node<N, E>> getNode(N nodeValue) {
		return getNodes().find(node -> node.getValue().equals(nodeValue));
	}

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
