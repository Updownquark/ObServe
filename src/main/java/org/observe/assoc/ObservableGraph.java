package org.observe.assoc;

import java.util.Objects;
import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.qommons.Causable;
import org.qommons.Transaction;
import org.qommons.collect.Graph;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.collect.TransactableGraph;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;

/**
 * A graph implementation based on observable collections
 *
 * @param <N> The type of values stored in the nodes of the graph
 * @param <E> The type of values stored in the edges of the graph
 */
public interface ObservableGraph<N, E> extends TransactableGraph<N, E> {
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
				public TypeToken<N> getType() {
					return source.getType();
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
				public Observable<ObservableValueEvent<N>> changes() {
					return source.changes();
				}

				@Override
				public Transaction lock() {
					return source.lock();
				}

				@Override
				public ObservableCollection<? extends Edge<N, E>> getEdges() {
					return source.getEdges().flow().map((TypeToken<ObservableGraph.Edge<N, E>>) source.getEdges().getType(),
						e -> e.unsettable(), options -> options.cache(false)).immutable().collectPassive();
				}

				@Override
				public ObservableCollection<? extends Edge<N, E>> getOutward() {
					return source.getEdges().flow().map((TypeToken<ObservableGraph.Edge<N, E>>) source.getOutward().getType(),
						e -> e.unsettable(), options -> options.cache(false)).immutable().collectPassive();
				}

				@Override
				public ObservableCollection<? extends Edge<N, E>> getInward() {
					return source.getEdges().flow().map((TypeToken<ObservableGraph.Edge<N, E>>) source.getOutward().getType(),
						e -> e.unsettable(), options -> options.cache(false)).immutable().collectPassive();
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
				public TypeToken<E> getType() {
					return source.getType();
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
				public Observable<ObservableValueEvent<E>> changes() {
					return source.changes();
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
		Observable<Object> nodeChanges = getNodes().simpleChanges();
		Observable<Object> edgeChanges = getEdges().simpleChanges();
		return new Observable<Object>() {
			@Override
			public boolean isSafe() {
				return true;
			}

			@Override
			public Subscription subscribe(Observer<? super Object> observer) {
				Causable.CausableKey key = Causable.key((cause, data) -> observer.onNext(cause));
				Consumer<Object> action = v -> {
					if (v instanceof Causable)
						((Causable) v).getRootCausable().onFinish(key);
					else
						observer.onNext(v);
				};
				Subscription nodeSub = nodeChanges.act(action);
				Subscription edgeSub = edgeChanges.act(action);
				return Subscription.forAll(nodeSub, edgeSub);
			}
		};
	}

	/**
	 * @param nodeValue The value to get the node for
	 * @return An observable value containing the node in this graph whose value is equal to the argument. The value may be null.
	 */
	default ObservableValue<? extends Node<N, E>> getNode(N nodeValue) {
		return getNodes().observeFind(node -> node.get().equals(nodeValue), () -> null, true);
	}

	/** @return A representation of this graph that is not modifiable */
	default ObservableGraph<N, E> immutable() {
		ObservableGraph<N, E> source = this;
		return new ObservableGraph<N, E>() {
			@Override
			public ObservableCollection<? extends ObservableGraph.Node<N, E>> getNodes() {
				return source.getNodes().flow()
					.map((TypeToken<ObservableGraph.Node<N, E>>) source.getNodes().getType(), n -> n.unsettable(),
						options -> options.cache(false))
					.immutable().collectPassive();
			}

			@Override
			public ObservableCollection<N> getNodeValues() {
				return source.getNodeValues().flow().immutable().collectPassive();
			}

			@Override
			public ObservableCollection<? extends ObservableGraph.Edge<N, E>> getEdges() {
				return source.getEdges().flow()
					.map((TypeToken<ObservableGraph.Edge<N, E>>) source.getEdges().getType(), e -> e.unsettable(),
						options -> options.cache(false))
					.immutable().collectPassive();
			}

			@Override
			public ObservableGraph<N, E> immutable() {
				return this;
			}
		};
	}

	// default <N2, E2> ObservableGraph<N2, E2> map(Function<N, N2> nodeMap, Function<E, E2> edgeMap) {
	// }
	//
	// default <V, N2> ObservableGraph<N2, E> combineNodes(ObservableValue<V> other, BiFunction<N, V, N2> map) {
	// }
	//
	// default <V, E2> ObservableGraph<N, E2> combineEdges(ObservableValue<V> other, BiFunction<N, E, E2> map) {
	// }

	// default ObservableCollection<Edge<N, E>> traverse(Node<N, E> start, Node<N, E> end, Function<Edge<N, E>, Double> cost) {
	// }

	/**
	 * @param <N> The type of node values in the graph
	 * @param <E> The type of edge values in the graph
	 * @param nodeType The node type for the graph
	 * @param edgeType The edge type for the graph
	 * @return An empty graph
	 */
	static <N, E> ObservableGraph<N, E> empty(TypeToken<N> nodeType, TypeToken<E> edgeType) {
		TypeToken<Node<N, E>> nodeType2 = new TypeToken<Node<N, E>>() {}.where(new TypeParameter<N>() {}, nodeType.wrap())
			.where(new TypeParameter<E>() {}, edgeType.wrap());
		TypeToken<Edge<N, E>> edgeType2 = new TypeToken<Edge<N, E>>() {}.where(new TypeParameter<N>() {}, nodeType.wrap())
			.where(new TypeParameter<E>() {}, edgeType.wrap());
		ObservableCollection<Node<N, E>> nodes = ObservableCollection.of(nodeType2);
		ObservableCollection<Edge<N, E>> edges = ObservableCollection.of(edgeType2);
		ObservableCollection<N> nodeValues = ObservableCollection.of(nodeType);
		return new ObservableGraph<N, E>() {
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
		};
	}
}
