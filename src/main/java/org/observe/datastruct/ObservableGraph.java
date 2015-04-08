package org.observe.datastruct;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.observe.ObservableValue;
import org.observe.collect.ObservableCollection;

public interface ObservableGraph<N, E> {
	interface Node<N, E> {
		ObservableCollection<Edge<N, E>> getEdges();

		ObservableValue<N> getValue();
	}

	interface Edge<N, E> {
		Node<N, E> getStart();
		Node<N, E> getEnd();
		boolean isDirected();

		ObservableValue<E> getValue();
	}

	ObservableCollection<Node<N, E>> getNodes();
	ObservableCollection<Edge<N, E>> getEdges();

	default ObservableGraph<N, E> filter(Predicate<N> nodeFilter, Predicate<E> edgeFilter) {
		if(nodeFilter==null && edgeFilter==null)
			return this;
		if(nodeFilter==null)
			nodeFilter=node->true;
		if(edgeFilter==null)
			edgeFilter=edge->true;
		ObservableGraph<N, E> outer=this;
		class FilteredNode implements Node<N, E>{
			private final Node<N, E> wrapped;
			
			FilteredNode(Node<N, E> wrap){
				wrapped=wrap;
			}
			
			@Override
			public ObservableCollection<Edge<N, E>> getEdges() {
				return wrapped.getEdges().filter(edge->edgeFilter.test(edge.get
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public ObservableValue<N> getValue() {
				// TODO Auto-generated method stub
				return null;
			}
		}
		class FilteredGraph implements ObservableGraph<N, E>{
			@Override
			public ObservableCollection<Node<N, E>> getNodes() {
				
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public ObservableCollection<Edge<N, E>> getEdges() {
				// TODO Auto-generated method stub
				return null;
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
