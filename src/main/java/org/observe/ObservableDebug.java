package org.observe;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import org.observe.assoc.ObservableGraph;
import org.observe.assoc.ObservableGraph.Node;
import org.observe.assoc.ObservableMap;
import org.observe.assoc.ObservableMultiMap;
import org.observe.assoc.impl.ObservableMultiMapImpl;
import org.observe.collect.CollectionSession;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableList;
import org.observe.collect.ObservableSet;
import org.observe.util.WeakReferenceObservable;
import org.qommons.Transaction;

import com.google.common.reflect.TypeToken;

/** A utility class for debugging observables */
public abstract class ObservableDebug {
	/**
	 * A builder that allows the specification of derivation properties and debugging properties on an observable
	 *
	 * @param <T> The type of the observable
	 */
	public interface ObservableDerivationBuilder<T> {
		/**
		 * @param relationship The relationship of this builder's observable to the specified parent(s)
		 * @param parents The observable(s) that this builder's observable is derived from in some way
		 * @return This builder, for chaining
		 */
		ObservableDerivationBuilder<T> from(String relationship, Object... parents);

		/**
		 * @param purpose How the function is used in deriving the value
		 * @param function The function used in the derivation of the value(s) of this builder's observable
		 * @return This builder, for chaining
		 */
		ObservableDerivationBuilder<T> using(String purpose, Object function);

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

		/**
		 * @return The observable whose debugging properties are modifiable by this builder
		 */
		T get();
	}

	/** A quick interface to describe an observable's debug properties */
	public interface DebugDescription {
		/**
		 * @return The observable being described
		 */
		Object get();

		/**
		 * @return This observable's parents, by relationship. This map may break {@link Map}'s contract by containing multiple entries with
		 *         the same key. The map is observable to allow the use of extra utilities in that class, but this map should be constant
		 *         after the observable has been registered.
		 */
		ObservableMap<String, DebugDescription> parents();

		/**
		 * @return Any functions that may be used to generate this observable's values
		 */
		Map<String, Object> functions();

		/**
		 * @return Any labels that have been attached to this observable
		 */
		ObservableCollection<String> labels();

		/**
		 * @return Any properties that have been tagged onto this observable
		 */
		ObservableMultiMap<String, Object> tags();
	}

	/** Holds an observable structure for debugging */
	public static class ObservableDebugWrapper {
		/** The reference to the observable structure */
		public final WeakReferenceObservable<Object> observable;

		/** The functions used to derive the observable */
		public final Map<String, Object> functions;

		/** Labels that have been given to the observable */
		public final ObservableList<String> labels;

		/** Properties that have been tagged onto the observable */
		public final ObservableMultiMap<String, Object> tags;

		private final ObservableMultiMapImpl<String, Object> tagController;

		private ObservableDebugWrapper(Object ob, Map<String, Object> fns) {
			observable = new WeakReferenceObservable<>(TypeToken.of(Object.class), ob, false);
			labels = new org.observe.collect.impl.ObservableArrayList<>(TypeToken.of(String.class));
			tagController = new org.observe.assoc.impl.ObservableMultiMapImpl<>(TypeToken.of(String.class),
					TypeToken.of(Object.class));
			tags = tagController.immutable();
			functions = Collections.unmodifiableMap(fns);
		}
	}

	/*@FunctionalInterface
	public interface D extends AutoCloseable {
		void done();

		@Override
		default void close() {
			done();
		}
	}

	public static enum DebugType {
		next, complete, subscribe;
	}

	public interface DebugFrame {
		Object getObservable();

		DebugType getType();

		DebugFrame getParent();

		ObservableList<DebugFrame> getChildren();

		ObservableValue<Boolean> isDone();
	}*/

	/** Returned from execution debugging methods. The {@link #done(String)} method on this must be called before the method exits. */
	/*public static final class DImpl implements DebugFrame {
		private final DebugState theState;
		private final DebugPlacemark thePlace;
		private boolean isDone;

		private DImpl(DebugState state, DebugPlacemark place) {
			theState = state;
			thePlace = place;
		}

		/**
	 * Called when the method that called a debugging method exits.
	 *
	 * @param msg The message to print
	 * /
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
	}*/

	// Structural debugging methods

	/**
	 * Registers an observable for debugging
	 *
	 * @param <T> The type of the observable
	 * @param observable The observable to register
	 * @return A derivation builder for the observable, allowing its ancestry to be specified
	 */
	public abstract <T> ObservableDerivationBuilder<T> debug(T observable);

	/**
	 * Allows observables to be tagged in a custom way
	 *
	 * @param <T> The type of observable
	 * @param observable The observable to add debugging properties to
	 * @return A derivation builder that allows labeling and tagging, but cannot alter the observable's derivation properties
	 */
	public abstract <T> ObservableDerivationBuilder<T> label(T observable);

	/**
	 * @param observable The observable to get debug information for
	 * @return A descriptor containing detailed debugging information for the given observable (if it has been registered)
	 */
	public abstract DebugDescription desc(Object observable);

	/**
	 * @param <T> The type of the function
	 * @param lambda The lambda to label by type
	 * @param label The label for the given lambda type
	 * @return The lambda
	 */
	public abstract <T> T lambda(T lambda, String label);

	/**
	 * @param lambda The lambda to get the label of
	 * @return The label stored for the given lambda, if one exists
	 */
	public abstract String descLambda(Object lambda);

	/**
	 * @return The graph of observable dependencies that the debugging framework knows about
	 */
	public abstract ObservableGraph<ObservableDebugWrapper, String> getObservableGraph();

	/**
	 * @param observable The observable to get the graph node for
	 * @return The graph node containing all debugging information stored for the given observable
	 */
	public abstract ObservableGraph.Node<ObservableDebugWrapper, String> getGraphNode(Object observable);

	// Execution debugging methods

	/*/** Enables debugging on the current thread until {@link #endDebug()} is called * /
	public abstract void startDebug();

	/** Disables debugging on the current thread. @see {@link #startDebug()} * /
	public abstract void endDebug();

	public abstract D subscribed(Object observable, Object observer);

	public abstract D onNext(Object observable);

	public abstract D onCompleted(Object observable);

	public abstract DebugFrame getCurrentFrame();

	public abstract ObservableMap<Thread, DebugFrame> getFrames();

	public abstract ObservableMap<Thread, DebugFrame> getRoots();*/

	private static ObservableDebug instance;

	static {
		instance = createInstance(System.getProperty("org.observe.debug"));
	}

	/**
	 * @param type The type of debugger to create, either one of the default string values (null, structure, full) or the fully-qualified
	 *            type name of a custom ObservableDebug implementation
	 * @return The ObservableDebug of the given type
	 */
	public static ObservableDebug createInstance(String type) {
		if(type == null || type.equals("none"))
			return new DisabledDebugger();
		switch (type) {
		case "structural":
			return new StructuralDebugger();
		case "full":
			return new FullDebugger();
		default:
			try {
				return (ObservableDebug) Class.forName(type).newInstance();
			} catch(Throwable e) {
				System.err.println("Could not instantiate custom debugger type: " + type
						+ ".\nThe default available debugger types are null or none (for disabled debugging), structural, and full.");
				e.printStackTrace();
				return null;
			}
		}
	}

	/**
	 * @return The debugging instance to use
	 */
	public static ObservableDebug d() {
		return instance;
	}

	/** Simply returns without performing any debugging operations */
	public static class DisabledDebugger extends ObservableDebug {
		/**
		 * No-op builder. Discards all metadata.
		 *
		 * @param <T> The type of the observable
		 */
		protected static final class NullDerivationBuilder<T> implements ObservableDerivationBuilder<T> {
			private final T theValue;

			NullDerivationBuilder(T value) {
				theValue = value;
			}

			@Override
			public ObservableDerivationBuilder<T> from(String relationship, Object... parents) {
				return this;
			}

			@Override
			public ObservableDerivationBuilder<T> using(String purpose, Object function) {
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

		private final ObservableGraph<ObservableDebugWrapper, String> theGraph = ObservableGraph
				.empty(TypeToken.of(ObservableDebugWrapper.class), TypeToken.of(String.class));

		//
		// private final ObservableMap<Thread, DebugFrame> theFrameMap = ObservableMap.empty(new Type(Thread.class),
		// new Type(DebugFrame.class));

		@Override
		public <T> ObservableDerivationBuilder<T> debug(T observable) {
			return new NullDerivationBuilder<>(observable);
		}

		@Override
		public <T> ObservableDerivationBuilder<T> label(T observable) {
			return new NullDerivationBuilder<>(observable);
		}

		@Override
		public DebugDescription desc(Object observable) {
			return null;
		}

		@Override
		public <T> T lambda(T lambda, String label) {
			return lambda;
		}

		@Override
		public String descLambda(Object lambda) {
			return lambda.toString();
		}

		@Override
		public ObservableGraph<ObservableDebugWrapper, String> getObservableGraph() {
			return theGraph;
		}

		@Override
		public Node<ObservableDebugWrapper, String> getGraphNode(Object observable) {
			return null;
		}
		//
		// @Override
		// public void startDebug() {
		// }
		//
		// @Override
		// public void endDebug() {
		// }
		//
		// @Override
		// public D subscribed(Object observable) {
		// return () -> {
		// };
		// }
		//
		// @Override
		// public D onNext(Object observable) {
		// return () -> {
		// };
		// }
		//
		// @Override
		// public D onCompleted(Object observable) {
		// return () -> {
		// };
		// }
		//
		// @Override
		// public DebugFrame getCurrentFrame() {
		// return null;
		// }
		//
		// @Override
		// public ObservableMap<Thread, DebugFrame> getFrames() {
		// return theFrameMap;
		// }
		//
		// @Override
		// public ObservableMap<Thread, DebugFrame> getRoots() {
		// return theFrameMap;
		// }
	}

	/** Supports the structural debugging methods, but not execution */
	public static class StructuralDebugger extends ObservableDebug {
		private final org.observe.assoc.impl.DefaultObservableGraph<ObservableDebugWrapper, String> theObservables;

		private final ConcurrentHashMap<Class<?>, String> theModFunctions = new ConcurrentHashMap<>();

		/** Creates the debugger */
		public StructuralDebugger() {
			theObservables = new org.observe.assoc.impl.DefaultObservableGraph<>(TypeToken.of(ObservableDebugWrapper.class),
					TypeToken.of(String.class));
		}

		@Override
		public <T> ObservableDerivationBuilder<T> debug(T observable) {
			if(getGraphNode(observable) != null)
				throw new IllegalStateException("Observable " + observable + " has already been added to debugging");
			Map<String, Object> functions = new java.util.LinkedHashMap<>();
			ObservableDebugWrapper newHolder = new ObservableDebugWrapper(observable, functions);
			ObservableGraph.Node<ObservableDebugWrapper, String> newNode = theObservables.addNode(newHolder);
			newHolder.observable.subscribe(new Observer<Object>() {
				@Override
				public <V> void onNext(V value) {
				}

				@Override
				public <V> void onCompleted(V value) {
					theObservables.removeNode(newNode);
				}
			});
			return new ObservableDerivationBuilder<T>() {
				@Override
				public ObservableDerivationBuilder<T> from(String relationship, Object... parents) {
					for(Object parent : parents) {
						ObservableGraph.Node<ObservableDebugWrapper, String> node = getGraphNode(parent);
						if(node == null) {
							debug(parent);
							node = getGraphNode(parent);
						}
						theObservables.addEdge(node, newNode, true, relationship);
					}
					return this;
				}

				@Override
				public ObservableDerivationBuilder<T> using(String purpose, Object function) {
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
						newHolder.labels.add(label);
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

		@Override
		public <T> ObservableDerivationBuilder<T> label(T observable) {
			ObservableGraph.Node<ObservableDebugWrapper, String> node = getGraphNode(observable);
			if(node == null) {
				System.err.println("Observable " + observable + " has not been added to debugging");
				return new DisabledDebugger.NullDerivationBuilder<>(observable);
			}
			return new ObservableDerivationBuilder<T>() {
				@Override
				public ObservableDerivationBuilder<T> from(String relationship, Object... parents) {
					throw new IllegalStateException("An observable's derivation cannot be changed after it is created");
				}

				@Override
				public ObservableDerivationBuilder<T> using(String purpose, Object function) {
					throw new IllegalStateException("An observable's derivation cannot be changed after it is created");
				}

				@Override
				public ObservableDerivationBuilder<T> label(String label) {
					if(!node.getValue().labels.contains(label))
						node.getValue().labels.add(label);
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

		@Override
		public DebugDescription desc(Object observable) {
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
						private final TypeToken<String> keyType = TypeToken.of(String.class);

						private final TypeToken<DebugDescription> valueType = TypeToken.of(DebugDescription.class);

						@Override
						public TypeToken<String> getKeyType() {
							return keyType;
						}

						@Override
						public TypeToken<DebugDescription> getValueType() {
							return valueType;
						}

						@Override
						public ObservableValue<CollectionSession> getSession() {
							return ObservableValue.constant(new TypeToken<CollectionSession>() {}, null);
						}

						@Override
						public boolean isSafe() {
							return false;
						}

						@Override
						public Transaction lock(boolean write, Object cause) {
							return () -> {
							};
						}

						@Override
						public ObservableSet<String> keySet() {
							return ObservableSet.unique(
									theNode.getEdges().filter(edge -> edge.getEnd() == theNode).map(edge -> edge.getValue()),
									Objects::equals);
						}

						@Override
						public ObservableValue<DebugDescription> observe(Object key) {
							return theNode.getEdges().find(edge -> edge.getEnd() == theNode && edge.getValue().equals(key))
									.mapV(edge -> new NodeDebugDescription(edge.getStart()));
						}

						@Override
						public ObservableSet<? extends ObservableEntry<String, DebugDescription>> observeEntries() {
							return ObservableMap.defaultObserveEntries(this);
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

				private String toShortString() {
					StringBuilder ret = labelString();

					ObservableMap<String, DebugDescription> parents = parents();
					if(parents.isEmpty())
						ret.append(theNode.getValue().observable.get().toString());
					else if(parents.keySet().size() == 1) { // Either single-parent or some sort of flattened set of parents
						ret.append(parents.keySet().find(key -> true).get()).append('(');
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
			ObservableGraph.Node<ObservableDebugWrapper, String> node = getGraphNode(observable);
			if(node == null)
				return null;
			return new NodeDebugDescription(node);
		}

		@Override
		public <T> T lambda(T lambda, String label) {
			theModFunctions.putIfAbsent(lambda.getClass(), label);
			return lambda;
		}

		@Override
		public String descLambda(Object lambda) {
			return theModFunctions.get(lambda.getClass());
		}

		@Override
		public ObservableGraph.Node<ObservableDebugWrapper, String> getGraphNode(Object observable) {
			for(ObservableGraph.Node<ObservableDebugWrapper, String> node : theObservables.getNodes())
				if(node.getValue().observable == observable)
					return node;
			return null;
		}

		@Override
		public ObservableGraph<ObservableDebugWrapper, String> getObservableGraph() {
			return theObservables;
		}
		//
		// @Override
		// public void startDebug() {
		// }
		//
		// @Override
		// public void endDebug() {
		// }
	}

	/** Full support for structural and execution debugging on any number of simultaneous threads */
	public static class FullDebugger extends StructuralDebugger {
		// private final ConcurrentHashMap<Thread, DebugState> theThreadDebug = new ConcurrentHashMap<>();
		//
		// @Override
		// public void startDebug() {
		// Thread current = Thread.currentThread();
		// DebugState state = theThreadDebug.get(current);
		// if(state == null)
		// theThreadDebug.put(current, new DebugState());
		// }
		//
		// @Override
		// public void endDebug() {
		// Thread current = Thread.currentThread();
		// theThreadDebug.remove(current);
		// }
		//
		// private DebugState debug() {
		// Thread current = Thread.currentThread();
		// return theThreadDebug.get(current);
		// }
		//
		// @Override
		// public Subscription subscribed(Object observable) {
		// DebugState debug = debug();
		// if(debug == null)
		// return DEFAULT_DEBUG;
		// DebugPlacemark place = debug.push(obs, DebugType.subscribe, name);
		// started(debug, msg);
		// return new DebugFrame(debug, place);
		// }
	}

	/** The indent used per depth level of nested debug print statements */
	public static final String INDENT = "  ";

	// /** The default debug object returned when debugging is not enabled */
	// public static final DebugFrame DEFAULT_DEBUG = new DebugFrame(null, null);

	// private static final class DebugPlacemark {
	// final DebugPlacemark parent;
	// final DebugType type;
	// final Observable<?> observable;
	//
	// DebugPlacemark(DebugPlacemark p, DebugType typ, Observable<?> obs) {
	// parent = p;
	// type = typ;
	// observable = obs;
	// }
	//
	// @Override
	// public String toString() {
	// return new StringBuilder('(').append(observable).append(')').append('.').append(type).toString();
	// }
	// }

	// private static final class DebugState {
	// private DebugPlacemark thePlace;
	// private int theDepth;
	//
	// DebugPlacemark getPlace() {
	// return thePlace;
	// }
	//
	// int getDepth() {
	// return theDepth;
	// }
	//
	// DebugPlacemark push(Observable<?> obs, DebugType type, String name) {
	// DebugPlacemark place = new DebugPlacemark(thePlace, type, obs, name);
	// thePlace = place;
	// theDepth++;
	// return place;
	// }
	//
	// DebugPlacemark pop() {
	// DebugPlacemark ret = thePlace;
	// if(ret != null) {
	// thePlace = ret.parent;
	// theDepth--;
	// }
	// return ret;
	// }
	// }

	// /**
	// * To be called from the {@link Observer#onNext(Object)} method
	// *
	// * @param obs The observable firing the value
	// * @param name The name of the observable to print
	// * @param msg The message to print
	// * @return The debug object to call {@link DebugFrame#done(String)} on when the current method exits
	// */
	// public static DebugFrame onNext(Observable<?> obs, String name, String msg) {
	// DebugState debug = debug();
	// if(debug == null)
	// return DEFAULT_DEBUG;
	// DebugPlacemark place = debug.push(obs, DebugType.next, name);
	// started(debug, msg);
	// return new DebugFrame(debug, place);
	// }
	//
	// /**
	// * To be called from the {@link Observer#onCompleted(Object)} method
	// *
	// * @param obs The observable firing the value
	// * @param name The name of the observable to print
	// * @param msg The message to print
	// * @return The debug object to call {@link DebugFrame#done(String)} on when the current method exits
	// */
	// public static DebugFrame onCompleted(Observable<?> obs, String name, String msg) {
	// DebugState debug = debug();
	// if(debug == null)
	// return DEFAULT_DEBUG;
	// DebugPlacemark place = debug.push(obs, DebugType.complete, name);
	// started(debug, msg);
	// return new DebugFrame(debug, place);
	// }
	//
	// /**
	// * To be called from the {@link Observable#subscribe(Observer)} method
	// *
	// * @param obs The observable being subscribed to
	// * @param name The name of the observable to print
	// * @param msg The message to print
	// * @return The debug object to call {@link DebugFrame#done(String)} on when the current method exits
	// */
	// public static DebugFrame onSubscribe(Observable<?> obs, String name, String msg) {
	// DebugState debug = debug();
	// if(debug == null)
	// return DEFAULT_DEBUG;
	// DebugPlacemark place = debug.push(obs, DebugType.subscribe, name);
	// started(debug, msg);
	// return new DebugFrame(debug, place);
	// }
	//
	// private static StringBuilder indent(DebugState state) {
	// StringBuilder print = new StringBuilder();
	// for(int i = 0; i < state.getDepth() - 1; i++)
	// print.append(INDENT);
	// return print;
	// }
	//
	// private static void started(DebugState state, String msg) {
	// StringBuilder print = indent(state);
	// print.append(state.getPlace());
	// if(msg != null)
	// print.append(": ").append(msg);
	// System.out.println(print.toString());
	// }
	//
	// private static void finished(DebugState state, String msg) {
	// StringBuilder print = indent(state);
	// print.append('/').append(state.getPlace());
	// if(msg != null)
	// print.append(": ").append(msg);
	// System.out.println(print.toString());
	// }
	//
	// private static void notFinished(DebugState state) {
	// StringBuilder print = indent(state);
	// print.append('!').append(state.getPlace()).append(": ");
	// System.out.println(print.toString());
	// }
}
