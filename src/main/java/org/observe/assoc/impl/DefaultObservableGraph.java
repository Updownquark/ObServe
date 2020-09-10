package org.observe.assoc.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.observe.SimpleSettableValue;
import org.observe.assoc.ObservableGraph;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.qommons.Transaction;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.Graph;
import org.qommons.collect.MutableCollectionElement.StdMsg;
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
	private class DefaultNode extends SimpleSettableValue<N> implements ObservableGraph.Node<N, E> {
		private final ObservableCollection<ObservableGraph.Edge<N, E>> theOutgoingEdges;
		private ObservableCollection<ObservableGraph.Edge<N, E>> theIncomingEdges;
		private ObservableCollection<ObservableGraph.Edge<N, E>> theBiEdges;

		DefaultNode(N value, ObservableCollection<ObservableGraph.Edge<N, E>> outEdges) {
			super(theNodeType, false);
			set(value, null);
			theOutgoingEdges = outEdges;
		}

		@Override
		public ObservableCollection<? extends ObservableGraph.Edge<N, E>> getOutward() {
			return theOutgoingEdges;
		}

		@Override
		public ObservableCollection<? extends ObservableGraph.Edge<N, E>> getInward() {
			if (theIncomingEdges == null)
				theIncomingEdges = theEdges.flow().filter(edge -> edge.getEnd() == this ? null : StdMsg.WRONG_GROUP).collect();
			return theIncomingEdges;
		}

		@Override
		public ObservableCollection<ObservableGraph.Edge<N, E>> getEdges() {
			if (theBiEdges == null)
				theBiEdges = ObservableCollection.flattenCollections(theEdgeHolderType, theOutgoingEdges, getInward()).collect();
			return theBiEdges;
		}

		@Override
		public String toString() {
			return "Graph Node " + get();
		}
	}

	private class DefaultEdge extends SimpleSettableValue<E> implements ObservableGraph.Edge<N, E> {
		private final ObservableGraph.Node<N, E> theStart;
		private final ObservableGraph.Node<N, E> theEnd;

		private final boolean isDirected;

		DefaultEdge(ObservableGraph.Node<N, E> start, ObservableGraph.Node<N, E> end, boolean directed, E value) {
			super(theEdgeType, true);
			set(value, null);
			theStart = start;
			theEnd = end;
			isDirected = directed;
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
		public String toString() {
			return theStart + "->" + theEnd + ": " + get();
		}
	}

	private final TypeToken<N> theNodeType;
	private final TypeToken<E> theEdgeType;
	private final TypeToken<ObservableGraph.Edge<N, E>> theEdgeHolderType;
	private final ObservableCollection<ObservableGraph.Node<N, E>> theNodes;
	private final ObservableCollection<ObservableGraph.Node<N, E>> theExposedNodes;
	private ObservableCollection<N> theNodeValues;

	private final Function<TypeToken<ObservableGraph.Edge<N, E>>, ? extends ObservableCollection<ObservableGraph.Edge<N, E>>> theEdgeCreator;
	private final ObservableCollection<ObservableGraph.Edge<N, E>> theEdges;
	private final ObservableCollection<ObservableGraph.Edge<N, E>> theExposedEdges;

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
		theNodeType = nodeType;
		theEdgeType = edgeType;

		TypeToken<ObservableGraph.Node<N, E>> nodesType = new TypeToken<ObservableGraph.Node<N, E>>() {}//
		.where(new TypeParameter<N>() {}, nodeType.wrap())//
		.where(new TypeParameter<E>() {}, edgeType.wrap());
		theEdgeHolderType = new TypeToken<ObservableGraph.Edge<N, E>>() {}//
		.where(new TypeParameter<N>() {}, nodeType.wrap())//
		.where(new TypeParameter<E>() {}, edgeType.wrap());

		theNodes = nodeList.apply(nodesType);
		theExposedNodes = theNodes.flow().filterMod(fm -> fm.noAdd(StdMsg.UNSUPPORTED_OPERATION)).collectPassive();
		theEdgeCreator = edgeList;
		theEdges = theNodes.flow().flatMap(theEdgeHolderType, n -> n.getOutward().flow()).collect();
		theExposedEdges = theEdges.flow().filterMod(fm -> fm.noAdd(StdMsg.UNSUPPORTED_OPERATION)).collectPassive();
		theNodes.onChange(evt -> {
			if (evt.getType() == CollectionChangeType.remove) {
				try (Transaction t = theEdges.lock(true, evt)) {
					evt.getOldValue().getOutward().clear();
					evt.getOldValue().getInward().clear();
				}
			}
		});
	}

	@Override
	public ObservableCollection<ObservableGraph.Node<N, E>> getNodes() {
		return theExposedNodes;
	}

	@Override
	public ObservableCollection<N> getNodeValues() {
		if (theNodeValues == null)
			theNodeValues = theNodes.flow().refreshEach(n -> n.changes().noInit())
			.transform(theNodeType,
				tx -> tx.map(n -> n.get())//
				.modifySource((node, nodeValue) -> node.set(nodeValue, null), //
					rvrs -> rvrs.rejectWith((nodeValue, tv) -> tv.getCurrentSource().isAcceptable(nodeValue), false, true)//
					.createWith(this::createNode)//
					// We now have the ability to reject additions based on value, but this class doesn't seem to support it
					// .rejectAddWith()
					)).collect();
		return theNodeValues;
	}

	@Override
	public ObservableCollection<ObservableGraph.Edge<N, E>> getEdges() {
		return theExposedEdges;
	}

	@Override
	public ObservableGraph.Node<N, E> addNode(N value) {
		DefaultNode node = createNode(value);
		theNodes.add(node);
		return node;
	}

	/**
	 * @param value The value for the new node
	 * @return A new node (not yet inserted) to go into this graph's node collection
	 */
	protected DefaultNode createNode(N value) {
		ObservableCollection<ObservableGraph.Edge<N, E>> nodeEdges = theEdgeCreator.apply(theEdges.getType());
		return new DefaultNode(value, nodeEdges);
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
		try (Transaction nodeT = theNodes.lock(false, null)) {
			if (!theNodes.contains(start) || !theNodes.contains(end))
				throw new IllegalArgumentException("Edges may only be created between nodes already present in the graph");
			if (start.equals(end))
				throw new IllegalArgumentException("An edge may not start and end at the same node");
			DefaultNode s = (DefaultNode) start;
			try (Transaction edgeT = s.theOutgoingEdges.lock(true, null)) {
				DefaultEdge edge = new DefaultEdge((ObservableGraph.Node<N, E>) start, (ObservableGraph.Node<N, E>) end, directed, value);
				((DefaultNode) start).theOutgoingEdges.add(edge);
				return edge;
			}
		}
	}

	@Override
	public boolean removeNode(Graph.Node<N, E> node) {
		return theNodes.remove(node);
	}

	@Override
	public boolean removeEdge(Graph.Edge<N, E> edge) {
		CollectionElement<ObservableGraph.Node<N, E>> nodeEl = theNodes.getElement((ObservableGraph.Node<N, E>) edge.getStart(), true);
		return nodeEl != null && nodeEl.get().getOutward().remove(edge);
	}

	@Override
	public ObservableGraph.Node<N, E> replaceNode(Graph.Node<N, E> node, N newValue) {
		if (!theNodes.contains(node))
			throw new IllegalArgumentException("Unrecognized node");
		((DefaultNode) node).set(newValue, null);
		return (ObservableGraph.Node<N, E>) node;
	}

	/**
	 * Fires a set event on the given node, perhaps signifying that its internal value has changed
	 *
	 * @param node The node to fire the event on
	 */
	public void reset(ObservableGraph.Node<N, E> node) {
		CollectionElement<ObservableGraph.Node<N, E>> nodeEl = theNodes.getElement(node, true);
		if (nodeEl == null)
			return;
		theNodes.mutableElement(nodeEl.getElementId()).set(nodeEl.get());
	}

	/**
	 * Fires a set event on the given edge, perhaps signifying that its internal value has changed
	 *
	 * @param edge The edge to fire the event on
	 */
	public void reset(ObservableGraph.Edge<N, E> edge) {
		CollectionElement<ObservableGraph.Node<N, E>> nodeEl = theNodes.getElement(edge.getStart(), true);
		if (nodeEl == null)
			return;
		ObservableCollection<ObservableGraph.Edge<N, E>> edges = (ObservableCollection<ObservableGraph.Edge<N, E>>) nodeEl.get()
			.getOutward();
		CollectionElement<ObservableGraph.Edge<N, E>> edgeEl = edges.getElement(edge, true);
		if (edgeEl == null)
			return;
		edges.mutableElement(edgeEl.getElementId()).set(edgeEl.get());
	}

	@Override
	public void clear() {
		try (Transaction trans = theNodes.lock(true, null)) {
			theEdges.clear();
			theNodes.clear();
		}
	}

	@Override
	public void clearEdges() {
		theEdges.clear();
	}

	@Override
	public ObservableGraph<N, E> unmodifiable() {
		return ObservableGraph.super.unmodifiable();
	}
}
