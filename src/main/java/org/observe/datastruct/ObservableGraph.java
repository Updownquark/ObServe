package org.observe.datastruct;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection;

public interface ObservableGraph<N, E> {
	interface Node<N, E> {
		ObservableCollection<Edge<N, E>> getEdges();

		N getValue();
	}

	interface Edge<N, E> {
		Node<N, E> getStart();

		Node<N, E> getEnd();

		boolean isDirected();

		E getValue();
	}

	ObservableCollection<Node<N, E>> getNodes();

	ObservableCollection<Edge<N, E>> getEdges();

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

			FilteredNode(Node<N, E> wrap) {
				wrapped = wrap;
			}

			@Override
			public ObservableCollection<Edge<N, E>> getEdges() {
				return wrapped.getEdges().filter(edge -> {
					if(!ef.test(edge.getValue()))
						return false;
					if(edge.getStart() != wrapped && !nf.test(edge.getStart().getValue()))
						return false;
					if(edge.getEnd() != wrapped && !nf.test(edge.getEnd().getValue()))
						return false;
					return true;
				});
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

			FilteredEdge(Edge<N, E> wrap, Node<N, E> start, Node<N, E> end) {
				wrapped = wrap;
				this.start = start;
				this.end = end;
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
			// TODO Not threadsafe
			private final Map<Node<N, E>, FilteredNode> theNodeMap = new java.util.IdentityHashMap<>();

			@Override
			public ObservableCollection<Node<N, E>> getNodes() {
				return outer.getNodes().filter(node -> nf.test(node.getValue())).map(node -> filter(node));
			}

			@Override
			public ObservableCollection<Edge<N, E>> getEdges() {
				return outer.getEdges().filter(edge -> ef.test(edge.getValue())).map(edge -> filter(edge));
			}

			private FilteredNode filter(Node<N, E> node) {
				FilteredNode ret = theNodeMap.get(node);
				if(ret == null) {
					ret = new FilteredNode(node);
					theNodeMap.put(node, ret);

				}
			}

			private FilteredEdge filter(Edge<N, E> edge) {

			}
		}
		return new FilteredGraph();
	}

	default <N2, E2> ObservableGraph<N2, E2> map(Function<N, N2> nodeMap, Function<E, E2> edgeMap) {
	}

	default <V, N2> ObservableGraph<N2, E> combineNodes(ObservableValue<V> other, BiFunction<N, V, N2> map) {
	}

	default <V, E2> ObservableGraph<N, E2> combineEdges(ObservableValue<V> other, BiFunction<N, E, E2> map) {
	}

	default ObservableCollection<Edge<N, E>> traverse(Node<N, E> start, Node<N, E> end, Function<Edge<N, E>, Double> cost) {
	}
}
