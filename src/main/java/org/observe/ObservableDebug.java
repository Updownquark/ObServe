package org.observe;

import java.util.concurrent.ConcurrentHashMap;

/** A utility class for debugging observables */
public final class ObservableDebug {
	/** Whether debugging is turned on. If this is false, this class will do nothing */
	public static final boolean DEBUG_ON = true;

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
	 * To be called from the {@link Observable#internalSubscribe(Observer)} method
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
	 * To be called from the {@link Runnable#run()} method of the Runnable returned from the {@link Observable#internalSubscribe(Observer)}
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
