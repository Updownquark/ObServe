package org.observe.quick;

/** Just a tagging interface for mouse cursors in Quick. Implementations of this must be supported by Quick implementations. */
public interface MouseCursor {
	/** An enum of standard mouse cursors that most implementations should support. */
	public enum StandardCursors implements MouseCursor {
		/** Default pointer icon */
		DEFAULT,
		/** Text hover icon */
		TEXT,
		/** Hourglass or click icon */
		WAIT,
		/** Hand icon */
		HAND,
		/** An icon indicating that something may be moved */
		MOVE,
		/** Crosshair icon */
		CROSSHAIR,
		/** An icon indicating that something may be resized upward vertically */
		RESIZE_N,
		/** An icon indicating that something may be resized rightward horizontally */
		RESIZE_E,
		/** An icon indicating that something may be resized downward vertically */
		RESIZE_S,
		/** An icon indicating that something may be resized leftward horizontally */
		RESIZE_W,
		/** An icon indicating that someting may be resized toward the upper right */
		RESIZE_NE,
		/** An icon indicating that someting may be resized toward the lower right */
		RESIZE_SE,
		/** An icon indicating that someting may be resized toward the lower left */
		RESIZE_SW,
		/** An icon indicating that someting may be resized toward the upper left */
		RESIZE_NW;
	}
}
