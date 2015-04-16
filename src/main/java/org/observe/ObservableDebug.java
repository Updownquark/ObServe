package org.observe;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.observe.collect.ObservableCollection;

/** A utility class for debugging observables */
public final class ObservableDebug {
	public static class ObservableDerivation {
		public final List<Object> bases;
		public final Object derived;
		public final String derivation;
		public final Object function;

		public ObservableDerivation(List<Object> b, Object d, String derive, Object f) {
			super();
			bases = b;
			derived = d;
			derivation = derive;
			function = f;
		}
	}

	public static class ObservableTag {
		public final Object tagged;
		public final String tag;
		public final Object value;

		public ObservableTag(Object tagged, String tag, Object value) {
			super();
			this.tagged = tagged;
			this.tag = tag;
			this.value = value;
		}
	}

	public static class ObservableHolder{
		public final Object observable;
		public final ObservableCollection<String> labels;
		// public final
	}

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
	private static final org.observe.datastruct.DefaultObservableGraph<ObservableHolder, String> theObservables;
	private static final Map<Object, List<ObservableDerivation>> theDerivations = new IdentityHashMap<>();

	private static final Map<Object, ObservableDerivation> theDerived = new IdentityHashMap<>();
	private static final Map<Object, Map<String, ObservableTag>> theTags = new IdentityHashMap<>();

	private static final Map<String, Map<Object, List<ObservableTag>>> theTagged = new HashMap<>();
	private static final ReentrantReadWriteLock theLock = new ReentrantReadWriteLock();

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

	public static void derived(Object base, Object derived, String derivation, Object function) {
		derived(new Object[] {base}, derived, derivation, function);
	}

	public static void derived(Object [] bases, Object derived, String derivation, Object function) {
		if(!DEBUG_ON)
			return;
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			ObservableDerivation d = new ObservableDerivation(Arrays.asList(bases), derived, derivation, function);
			if(theDerived.put(derived, d) != null)
				System.err.println("WARNING! Observable " + derived + " is being reported as a derivation twice.");
			for(Object base : bases) {
				List<ObservableDerivation> derivations = theDerivations.get(base);
				if(derivations == null) {
					derivations = new ArrayList<>();
					theDerivations.put(base, derivations);
				}
				derivations.add(d);
			}
		} finally {
			lock.unlock();
		}
	}

	public static Collection<ObservableDerivation> getDerivations(Object base) {
		if(!DEBUG_ON)
			throw new IllegalStateException("Observable debugging is not enabled");
		Lock lock = theLock.readLock();
		lock.lock();
		try {
			List<ObservableDerivation> derivations = theDerivations.get(base);
			if(derivations == null)
				return Collections.EMPTY_LIST;
			return Collections.unmodifiableCollection(new ArrayList<>(derivations));
		} finally {
			lock.unlock();
		}
	}

	public static ObservableDerivation getDerived(Object derived) {
		if(!DEBUG_ON)
			throw new IllegalStateException("Observable debugging is not enabled");
		Lock lock = theLock.readLock();
		lock.lock();
		try {
			return theDerived.get(derived);
		} finally {
			lock.unlock();
		}
	}

	public static void label(Object observable, String label) {
		if(!DEBUG_ON)
			return;
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			ObservableTag tag = new ObservableTag(observable, label, null);
			Map<String, ObservableTag> tags = theTags.get(observable);
			if(tags == null) {
				tags = new HashMap<>();
				theTags.put(observable, tags);
			}
			tags.put(label, tag);
		} finally {
			lock.unlock();
		}
	}

	public Collection<String> getLabels(Object observable) {
		if(!DEBUG_ON)
			throw new IllegalStateException("Observable debugging is not enabled");
		Lock lock = theLock.readLock();
		lock.lock();
		try {
			Map<String, ObservableTag> tags = theTags.get(observable);
			if(tags == null)
				return Collections.EMPTY_LIST;
			HashSet<String> ret = new HashSet<>();
			for(ObservableTag tag : tags.values())
				if(tag.value == null)
					ret.add(tag.tag);
			return Collections.unmodifiableCollection(ret);
		} finally {
			lock.unlock();
		}
	}

	public static void tag(Object observable, String tag, Object value) {
		if(!DEBUG_ON)
			return;
		Lock lock = theLock.writeLock();
		lock.lock();
		try {
			ObservableTag obTag = new ObservableTag(observable, tag, value);
			Map<String, ObservableTag> tags = theTags.get(observable);
			if(tags == null) {
				tags = new HashMap<>();
				theTags.put(observable, tags);
			}
			if(tags.put(tag, obTag) != null)
				System.err.println("WARNING! Observable " + observable + " is being tagged with \"" + tag + "\" twice: " + value);
			Map<Object, List<ObservableTag>> tagged = theTagged.get(tag);
			if(tagged == null) {
				tagged = new IdentityHashMap<>();
				theTagged.put(tag, tagged);
			}
			List<ObservableTag> taggedList = tagged.get(value);
			if(taggedList == null) {
				taggedList = new ArrayList<>();
				tagged.put(value, taggedList);
			}
			taggedList.add(obTag);
		} finally {
			lock.unlock();
		}
	}

	public Map<String, Object> getTags(Object observable) {
		if(!DEBUG_ON)
			throw new IllegalStateException("Observable debugging is not enabled");
		Lock lock = theLock.readLock();
		lock.lock();
		try {
			Map<String, ObservableTag> tags = theTags.get(observable);
			if(tags == null)
				return Collections.EMPTY_MAP;
			Map<String, Object> ret = new HashMap<>();
			for(ObservableTag tag : tags.values())
				if(tag.value != null)
					ret.put(tag.tag, tag.value);
			return Collections.unmodifiableMap(ret);
		} finally {
			lock.unlock();
		}
	}

	public Map<Object, Collection<Object>> getTagged(String tag) {
		if(!DEBUG_ON)
			throw new IllegalStateException("Observable debugging is not enabled");
		Lock lock = theLock.readLock();
		lock.lock();
		try {
			Map<Object, List<ObservableTag>> tagged = theTagged.get(tag);
			if(tagged == null)
				return Collections.EMPTY_MAP;
			Map<Object, Collection<Object>> ret = new IdentityHashMap<>();
			for(Map.Entry<Object, List<ObservableTag>> entry : tagged.entrySet()) {
				List<Object> retValue = new ArrayList<>();
				ret.put(entry.getKey(), Collections.unmodifiableList(retValue));
				for(ObservableTag obTag : entry.getValue())
					retValue.add(obTag.tagged);
			}
			return Collections.unmodifiableMap(ret);
		} finally {
			lock.unlock();
		}
	}

	public Collection<Object> getTagged(String tag, Object value) {
		if(!DEBUG_ON)
			throw new IllegalStateException("Observable debugging is not enabled");
		Lock lock = theLock.readLock();
		lock.lock();
		try {
			Map<Object, List<ObservableTag>> tagged = theTagged.get(tag);
			if(tagged == null)
				return Collections.EMPTY_LIST;
			List<ObservableTag> tags = tagged.get(value);
			if(tags == null)
				return Collections.EMPTY_LIST;
			List<Object> ret = new ArrayList<>();
			for(ObservableTag obTag : tags)
				ret.add(obTag.tagged);
			return Collections.unmodifiableCollection(ret);
		} finally {
			lock.unlock();
		}
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
