package org.observe.assoc;

import java.util.Objects;
import java.util.function.Consumer;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Observer;
import org.observe.Subscription;
import org.observe.collect.ObservableCollection;
import org.qommons.Causable;
import org.qommons.Transaction;
import org.qommons.collect.Graph;
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
	interface Node<N, E> extends Graph.Node<N, E> {
		@Override
		ObservableCollection<? extends Edge<N, E>> getEdges();

		@Override
		N getValue();
	}

	/**
	 * An edge between two nodes in a graph
	 *
	 * @param <N> The type of values stored in the nodes of the graph
	 * @param <E> The type of values stored in the edges of the graph
	 */
	interface Edge<N, E> extends Graph.Edge<N, E> {
		@Override
		Node<N, E> getStart();

		@Override
		Node<N, E> getEnd();

		@Override
		boolean isDirected();

		@Override
		E getValue();
	}

	@Override
	ObservableCollection<? extends Node<N, E>> getNodes();

	@Override
	ObservableCollection<? extends Edge<N, E>> getEdges();

	@Override
	default Node<N, E> nodeFor(N value) {
		for (Node<N, E> node : getNodes())
			if (Objects.equals(node.getValue(), value))
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
				Consumer<Object> action = v -> {
					if (v instanceof Causable)
						((Causable) v).getRootCausable().onFinish(this, (cause, data) -> observer.onNext(cause));
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
		return getNodes().observeFind(node -> node.getValue().equals(nodeValue), () -> null, true);
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
		TypeToken<Node<N, E>> nodeType2 = new TypeToken<Node<N, E>>() {}.where(new TypeParameter<N>() {}, nodeType)
			.where(new TypeParameter<E>() {}, edgeType);
		TypeToken<Edge<N, E>> edgeType2 = new TypeToken<Edge<N, E>>() {}.where(new TypeParameter<N>() {}, nodeType)
			.where(new TypeParameter<E>() {}, edgeType);
		ObservableCollection<Node<N, E>> nodes = ObservableCollection.of(nodeType2);
		ObservableCollection<Edge<N, E>> edges = ObservableCollection.of(edgeType2);
		return new ObservableGraph<N, E>() {
			@Override
			public ObservableCollection<Node<N, E>> getNodes() {
				return nodes;
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
