package org.observe;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableList;
import org.observe.collect.TransactableCollection;
import org.observe.datastruct.DefaultObservableMultiMap;
import org.observe.datastruct.ObservableGraph;
import org.observe.datastruct.ObservableMap;
import org.observe.datastruct.ObservableMultiMap;
import org.observe.util.WeakReferenceObservable;

import prisms.lang.Type;

/** A utility class for debugging observables */
public final class ObservableDebug {
	/** Holds an observable structure for debugging */
	public static class ObservableDebugWrapper{
		/** The reference to the observable structure */
		public final WeakReferenceObservable<Object> observable;

		/** The functions used to derive the observable */
		public final Map<String, Object> functions;

		/** Labels that have been given to the observable */
		public final ObservableList<String> labels;

		/** Properties that have been tagged onto the observable */
		public final ObservableMultiMap<String, Object> tags;

		private final TransactableCollection<String> labelController;
		private final DefaultObservableMultiMap<String, Object> tagController;

		private ObservableDebugWrapper(Object ob, Map<String, Object> fns) {
			observable = new WeakReferenceObservable<>(new Type(Object.class), ob);
			labels = new org.observe.collect.DefaultObservableList<>(new Type(String.class));
			labelController = ((org.observe.collect.DefaultObservableList<String>) labels).control(null);
			tagController = new org.observe.datastruct.DefaultObservableMultiMap<>(new Type(String.class), new Type(Object.class));
			tags = tagController.immutable();
			functions = Collections.unmodifiableMap(fns);
		}
	}

	/** A quick interface to describe an observable's debug properties */
	public static interface DebugDescription {
		/** @return The observable being described */
		Object get();

		/**
		 * @return This observable's parents, by relationship. This map may break {@link Map}'s contract by containing multiple entries with
		 *         the same key. The map is observable to allow the use of extra utilities in that class, but this map should be constant
		 *         after the observable has been registered.
		 */
		ObservableMap<String, DebugDescription> parents();

		/** @return Any functions that may be used to generate this observable's values */
		Map<String, Object> functions();

		/** @return Any labels that have been attached to this observable */
		ObservableCollection<String> labels();

		/** @return Any properties that have been tagged onto this observable */
		ObservableMultiMap<String, Object> tags();
	}

	/**
	 * A builder that allows the specification of derivation properties and debugging properties on an observable
	 *
	 * @param <T> The type of the observable
	 */
	public static interface ObservableDerivationBuilder<T> {
		/**
		 * @param relationship The relationship of this builder's observable to the specified parent(s)
		 * @param parents The observable(s) that this builder's observable is derived from in some way
		 * @return This builder, for chaining
		 */
		ObservableDerivationBuilder<T> from(String relationship, Object... parents);

		/**
		 * @param function The function used in the derivation of the value(s) of this builder's observable
		 * @param purpose How the function is used in deriving the value
		 * @return This builder, for chaining
		 */
		ObservableDerivationBuilder<T> using(Object function, String purpose);

		/**
		 * @param label The label to apply to this observable
		 * @return This builder, for chaining
		 */
		ObservableDerivationBuilder<T> label(String label);

		/**
		 * @param tagName The name of the property to tag this builder's observable with
		 * @param value The value for the property
		 * @return This builder, for chaining
		 */
		ObservableDerivationBuilder<T> tag(String tagName, Object value);

		/** @return The observable whose debugging properties are modifiable by this builder */
		T get();
	}

	private static final class NullDerivationBuilder<T> implements ObservableDerivationBuilder<T> {
		private final T theValue;

		NullDerivationBuilder(T value) {
			theValue = value;
		}

		@Override
		public ObservableDerivationBuilder<T> from(String relationship, Object... parents) {
			return this;
		}

		@Override
		public ObservableDerivationBuilder<T> using(Object function, String purpose) {
			return this;
		}

		@Override
		public ObservableDerivationBuilder<T> label(String label) {
			return this;
		}

		@Override
		public ObservableDerivationBuilder<T> tag(String tagName, Object value) {
			return this;
		}

		@Override
		public T get() {
			return theValue;
		}
	};

	/** Whether debugging is turned on. If this is false, this class will do nothing */
	public static final boolean DEBUG_ON = "true".equalsIgnoreCase(System.getProperty("org.observe.debug"));

	/**
	 * Whether debugging is always on. If false and {@link #DEBUG_ON} is true, then debugging must be {@link #startDebug() activated} and
	 * {@link #endDebug() deactivated} per thread
	 */
	public static final boolean DEBUG_ALL_THREADS = false;

	/** The indent used per depth level of nested debug print statements */
	public static final String INDENT = "  ";

	/** Returned from debugging methods. The {@link #done(String)} method on this must be called before the method exits. */
	public static final class D {
		private final DebugState theState;
		private final DebugPlacemark thePlace;
		private boolean isDone;

		private D(DebugState state, DebugPlacemark place) {
			theState = state;
			thePlace = place;
		}

		/**
		 * Called when the method that called a debugging method exits.
		 *
		 * @param msg The message to print
		 */
		public void done(String msg) {
			if(theState == null)
				return;
			if(isDone){
				System.err.println("Done called twice on " + thePlace);
				return;
			}
			isDone = true;
			if(theState.getPlace() != thePlace) {
				DebugPlacemark p = theState.getPlace() == null ? null : theState.getPlace().parent;
				while(p!=null && p!=thePlace)
					p=p.parent;
				if(p==thePlace){
					do {
						notFinished(theState);
						theState.pop();
					} while(theState.getPlace() != thePlace);
				} else{
					System.err.println("ERROR! done() called for unrecognized debug: " + thePlace);
					return;
				}
			}

			finished(theState, msg);
			theState.pop();
		}

		@Override
		public String toString() {
			return thePlace.toString();
		}
	}

	/** The default debug object returned when debugging is not enabled */
	public static final D DEFAULT_DEBUG = new D(null, null);

	private static enum DebugType {
		next, complete, subscribe, unsubscribe;
	}

	private static final class DebugPlacemark {
		final DebugPlacemark parent;
		final DebugType type;
		final Observable<?> observable;
		final String name;

		DebugPlacemark(DebugPlacemark p, DebugType typ, Observable<?> obs, String n) {
			parent = p;
			type = typ;
			observable = obs;
			name = n;
		}

		@Override
		public String toString() {
			return new StringBuilder(name).append('(').append(observable).append(')').append('.').append(type).toString();
		}
	}

	private static final class DebugState {
		private DebugPlacemark thePlace;
		private int theDepth;

		DebugPlacemark getPlace() {
			return thePlace;
		}

		int getDepth() {
			return theDepth;
		}

		DebugPlacemark push(Observable<?> obs, DebugType type, String name) {
			DebugPlacemark place = new DebugPlacemark(thePlace, type, obs, name);
			thePlace = place;
			theDepth++;
			return place;
		}

		DebugPlacemark pop() {
			DebugPlacemark ret = thePlace;
			if(ret != null) {
				thePlace = ret.parent;
				theDepth--;
			}
			return ret;
		}
	}

	private static final ConcurrentHashMap<Thread, DebugState> theThreadDebug = new ConcurrentHashMap<>();
	private static final org.observe.datastruct.DefaultObservableGraph<ObservableDebugWrapper, String> theObservables;

	static {
		if(DEBUG_ON)
			theObservables = new org.observe.datastruct.DefaultObservableGraph<>(new Type(ObservableDebugWrapper.class), new Type(String.class));
		else
			theObservables = null;
	}

	/** Enables debugging on the current thread until {@link #endDebug()} is called */
	public static void startDebug() {
		if(!DEBUG_ON) {
			System.err.println("Observable debugging is turned off");
			return;
		}
		if(DEBUG_ALL_THREADS) // Debugging is turned on for all threads
			return;
		Thread current = Thread.currentThread();
		DebugState state = theThreadDebug.get(current);
		if(state == null)
			theThreadDebug.put(current, new DebugState());
	}

	/** Disables debugging on the current thread. @see {@link #startDebug()} */
	public static void endDebug() {
		if(!DEBUG_ON)
			return;
		if(DEBUG_ALL_THREADS)
			return;
		Thread current = Thread.currentThread();
		theThreadDebug.remove(current);
	}

	private static DebugState debug() {
		if(!DEBUG_ON)
			return null;
		Thread current = Thread.currentThread();
		DebugState debug = theThreadDebug.get(current);
		if(debug == null) {
			if(DEBUG_ALL_THREADS) {
				debug = new DebugState();
				theThreadDebug.put(current, debug);
			} else
				return null;
		}
		return debug;
	}

	/**
	 * Registers an observable for debugging
	 *
	 * @param <T> The type of the observable
	 * @param observable The observable to register
	 * @return A derivation builder for the observable, allowing its ancestry to be specified
	 */
	public static <T> ObservableDerivationBuilder<T> debug(T observable) {
		if(!DEBUG_ON)
			return new NullDerivationBuilder<>(observable);
		if(theObservables.getNodes().find(holder -> holder.getValue().observable.get() == observable).get() != null)
			throw new IllegalStateException("Observable " + observable + " has already been added to debugging");
		Map<String, Object> functions = new java.util.LinkedHashMap<>();
		ObservableDebugWrapper newHolder = new ObservableDebugWrapper(observable, functions);
		ObservableGraph.Node<ObservableDebugWrapper, String> newNode = theObservables.addNode(newHolder);
		newHolder.observable.completed().act(value -> theObservables.removeNode(newNode));
		return new ObservableDerivationBuilder<T>() {
			@Override
			public ObservableDerivationBuilder<T> from(String relationship, Object... parents) {
				for(Object parent : parents) {
					ObservableGraph.Node<ObservableDebugWrapper, String> node = theObservables.getNodes()
						.find(holder -> holder.getValue().observable.get() == parent).get();
					if(node == null)
						System.err.println("Derivation " + observable + "=" + parent + "." + relationship
							+ " cannot be asserted. Parent not found");
					theObservables.addEdge(node, newNode, true, relationship);
				}
				return this;
			}

			@Override
			public ObservableDerivationBuilder<T> using(Object function, String purpose) {
				if(!isFunctional(function.getClass()))
					throw new IllegalArgumentException(function + " is not a function");
				functions.put(purpose, function);
				return this;
			}

			private boolean isFunctional(Class<?> functional) {
				if(functional.getAnnotation(FunctionalInterface.class) != null)
					return true;
				for(Class<?> intf : functional.getInterfaces())
					if(isFunctional(intf))
						return true;
				return false;
			}

			@Override
			public ObservableDerivationBuilder<T> label(String label) {
				if(!newHolder.labels.contains(label))
					newHolder.labelController.add(label);
				return this;
			}

			@Override
			public ObservableDerivationBuilder<T> tag(String tagName, Object value) {
				newHolder.tagController.add(tagName, value);
				return this;
			}

			@Override
			public T get() {
				return observable;
			}
		};
	}

	/**
	 * Allows observables to be tagged in a custom way
	 *
	 * @param <T> The type of observable
	 * @param observable The observable to add debugging properties to
	 * @return A derivation builder that allows labeling and tagging, but cannot alter the observable's derivation properties
	 */
	public static <T> ObservableDerivationBuilder<T> label(T observable) {
		if(!DEBUG_ON)
			return new NullDerivationBuilder<>(observable);
		ObservableGraph.Node<ObservableDebugWrapper, String> node = theObservables.getNodes()
			.find(holder -> holder.getValue().observable.get() == observable).get();
		if(node == null) {
			System.err.println("Observable " + observable + " has not been added to debugging");
			return new NullDerivationBuilder<>(observable);
		}
		return new ObservableDerivationBuilder<T>() {
			@Override
			public ObservableDerivationBuilder<T> from(String relationship, Object... parents) {
				throw new IllegalStateException("An observable's derivation cannot be changed after it is created");
			}

			@Override
			public ObservableDerivationBuilder<T> using(Object function, String purpose) {
				throw new IllegalStateException("An observable's derivation cannot be changed after it is created");
			}

			@Override
			public ObservableDerivationBuilder<T> label(String label) {
				if(!node.getValue().labels.contains(label))
					node.getValue().labelController.add(label);
				return this;
			}

			@Override
			public ObservableDerivationBuilder<T> tag(String tagName, Object value) {
				node.getValue().tagController.add(tagName, value);
				return this;
			}

			@Override
			public T get() {
				return observable;
			}
		};
	}

	/**
	 * @param observable The observable to get debug information for
	 * @return A descriptor containing detailed debugging information for the given observable (if it has been registered)
	 */
	public DebugDescription desc(Object observable) {
		if(!DEBUG_ON)
			return null;
		class NodeDebugDescription implements DebugDescription {
			private final ObservableGraph.Node<ObservableDebugWrapper, String> theNode;

			NodeDebugDescription(ObservableGraph.Node<ObservableDebugWrapper, String> node) {
				theNode = node;
			}

			@Override
			public Object get() {
				return theNode.getValue();
			}

			@Override
			public ObservableMap<String, DebugDescription> parents() {
				return new ObservableMap<String, DebugDescription>() {
					private final Type keyType = new Type(String.class);

					private final Type valueType = new Type(Object.class);

					@Override
					public DebugDescription put(String key, DebugDescription value) {
						throw new UnsupportedOperationException();
					}

					@Override
					public DebugDescription remove(Object key) {
						throw new UnsupportedOperationException();
					}

					@Override
					public void putAll(Map<? extends String, ? extends DebugDescription> m) {
						throw new UnsupportedOperationException();
					}

					@Override
					public void clear() {
						throw new UnsupportedOperationException();
					}

					@Override
					public Type getKeyType() {
						return keyType;
					}

					@Override
					public Type getValueType() {
						return valueType;
					}

					@Override
					public ObservableCollection<ObservableEntry<String, DebugDescription>> observeEntries() {
						class Entry implements ObservableMap.ObservableEntry<String, DebugDescription> {
							private final ObservableGraph.Edge<ObservableDebugWrapper, String> theEdge;

							Entry(ObservableGraph.Edge<ObservableDebugWrapper, String> edge) {
								theEdge = edge;
							}

							@Override
							public Type getType() {
								return valueType;
							}

							@Override
							public Runnable observe(Observer<? super ObservableValueEvent<DebugDescription>> observer) {
								return () -> { // Should never change
								};
							}

							@Override
							public String getKey() {
								return theEdge.getValue();
							}

							@Override
							public DebugDescription getValue() {
								return new NodeDebugDescription(theEdge.getStart());
							}

							@Override
							public DebugDescription setValue(DebugDescription value) {
								throw new UnsupportedOperationException();
							}
						}
						return theNode.getEdges().filter(edge -> edge.getEnd() == theNode).map(Entry::new);
					}

					@Override
					public ObservableValue<CollectionSession> getSession() {
						return null;
					}
				};
			}

			@Override
			public Map<String, Object> functions() {
				return theNode.getValue().functions;
			}

			@Override
			public ObservableCollection<String> labels() {
				return theNode.getValue().labels;
			}

			@Override
			public ObservableMultiMap<String, Object> tags() {
				return theNode.getValue().tags;
			}

			private StringBuilder labelString() {
				StringBuilder ret = new StringBuilder();
				if(!theNode.getValue().labels.isEmpty())
					formatCollection(ret, theNode.getValue().labels);
				ret.append(": ");
				return ret;
			}

			private String toShortString(){
				StringBuilder ret = labelString();

				ObservableMap<String, DebugDescription> parents=parents();
				if(parents.isEmpty())
					ret.append(theNode.getValue().observable.get().toString());
				else if(parents.keySet().size() == 1) { // Either single-parent or some sort of flattened set of parents
					ret.append(parents.observeKeys().find(key -> true).get()).append('(');
					boolean first = true;
					for(DebugDescription parent : parents.values()) {
						if(!first)
							ret.append(", ");
						first = false;
						ret.append(parent.toString());
					}
					ret.append(')');
					return ret.toString();
				} else {
					boolean first = true;
					boolean second = false;
					for(Map.Entry<String, DebugDescription> parent : parents.entrySet()) {
						String parentStr = ((NodeDebugDescription) parent.getValue()).toShortString();
						if(first) {
							ret.append(parentStr).append(" .").append(parent.getKey()).append('(');
							first = false;
							second = true;
						} else {
							if(!second)
								ret.append(", ");
							ret.append(parent.getKey()).append(' ').append(parentStr);
							second = false;
						}
					}
					ret.append(')');
				}
				return ret.toString();
			}

			@Override
			public String toString() {
				StringBuilder ret = labelString();
				ret.append(theNode.getValue().observable.get().toString());
				formatMap(ret, parents(), "parents", (parent, sb) -> sb.append(((NodeDebugDescription) parent).toShortString()));
				formatMap(ret, functions(), "functions", (function, sb) -> sb.append(function.toString()));
				formatMap(ret, tags().asCollectionMap(), "tags", (tags, sb) -> formatCollection(sb, tags));
				return ret.toString();
			}

			private StringBuilder formatCollection(StringBuilder ret, Collection<?> coll) {
				ret.append('(');
				boolean first = true;
				for(Object value : coll) {
					if(!first)
						ret.append(", ");
					first = false;
					ret.append(value);
				}
				ret.append(')');
				return ret;
			}

			private <T> void formatMap(StringBuilder ret, Map<String, T> map, String name, BiFunction<T, StringBuilder, ?> toString) {
				if(!map.isEmpty()) {
					ret.append('\n').append(name);
					int maxLen = map.keySet().stream().mapToInt(str -> str.length()).max().getAsInt();
					for(Map.Entry<String, T> entry : map.entrySet()) {
						ret.append('\t').append(entry.getKey());
						for(int i = entry.getKey().length(); i < maxLen; i++)
							ret.append(' ');
						ret.append(" = ");
						toString.apply(entry.getValue(), ret);
					}
				}
			}
		}
		ObservableGraph.Node<ObservableDebugWrapper, String> node = theObservables.getNodes()
			.find(holder -> holder.getValue().observable.get() == observable).get();
		if(node == null)
			return null;
		return new NodeDebugDescription(node);
	}

	/** @return The graph of observable dependencies that the debugging framework knows about */
	public ObservableGraph<ObservableDebugWrapper, String> getObservableGraph() {
		if(DEBUG_ON)
			return theObservables;
		else
			return ObservableGraph.empty(new Type(ObservableDebugWrapper.class), new Type(String.class));
	}

	/**
	 * To be called from the {@link Observer#onNext(Object)} method
	 *
	 * @param obs The observable firing the value
	 * @param name The name of the observable to print
	 * @param msg The message to print
	 * @return The debug object to call {@link D#done(String)} on when the current method exits
	 */
	public static D onNext(Observable<?> obs, String name, String msg) {
		DebugState debug = debug();
		if(debug == null)
			return DEFAULT_DEBUG;
		DebugPlacemark place = debug.push(obs, DebugType.next, name);
		started(debug, msg);
		return new D(debug, place);
	}

	/**
	 * To be called from the {@link Observer#onCompleted(Object)} method
	 *
	 * @param obs The observable firing the value
	 * @param name The name of the observable to print
	 * @param msg The message to print
	 * @return The debug object to call {@link D#done(String)} on when the current method exits
	 */
	public static D onCompleted(Observable<?> obs, String name, String msg) {
		DebugState debug = debug();
		if(debug == null)
			return DEFAULT_DEBUG;
		DebugPlacemark place = debug.push(obs, DebugType.complete, name);
		started(debug, msg);
		return new D(debug, place);
	}

	/**
	 * To be called from the {@link Observable#observe(Observer)} method
	 *
	 * @param obs The observable being subscribed to
	 * @param name The name of the observable to print
	 * @param msg The message to print
	 * @return The debug object to call {@link D#done(String)} on when the current method exits
	 */
	public static D onSubscribe(Observable<?> obs, String name, String msg) {
		DebugState debug = debug();
		if(debug == null)
			return DEFAULT_DEBUG;
		DebugPlacemark place = debug.push(obs, DebugType.subscribe, name);
		started(debug, msg);
		return new D(debug, place);
	}

	/**
	 * To be called from the {@link Runnable#run()} method of the Runnable returned from the {@link Observable#observe(Observer)}
	 *
	 * @param obs The observable being unsubscribed from
	 * @param name The name of the observable to print
	 * @param msg The message to print
	 * @return The debug object to call {@link D#done(String)} on when the current method exits
	 */
	public static D onUnsubscribe(Observable<?> obs, String name, String msg) {
		DebugState debug = debug();
		if(debug == null)
			return DEFAULT_DEBUG;
		DebugPlacemark place = debug.push(obs, DebugType.unsubscribe, name);
		started(debug, msg);
		return new D(debug, place);
	}

	private static StringBuilder indent(DebugState state) {
		StringBuilder print = new StringBuilder();
		for(int i = 0; i < state.getDepth() - 1; i++)
			print.append(INDENT);
		return print;
	}

	private static void started(DebugState state, String msg) {
		StringBuilder print = indent(state);
		print.append(state.getPlace());
		if(msg != null)
			print.append(": ").append(msg);
		System.out.println(print.toString());
	}

	private static void finished(DebugState state, String msg) {
		StringBuilder print = indent(state);
		print.append('/').append(state.getPlace());
		if(msg != null)
			print.append(": ").append(msg);
		System.out.println(print.toString());
	}

	private static void notFinished(DebugState state) {
		StringBuilder print = indent(state);
		print.append('!').append(state.getPlace()).append(": ");
		System.out.println(print.toString());
	}
}
