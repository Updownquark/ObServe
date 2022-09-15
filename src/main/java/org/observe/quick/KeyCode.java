package org.observe.quick;

/** Keyboard keys that may be represented by key events */
public enum KeyCode {
	/** The letter a on the keyboard */
	A,
	/** The letter b on the keyboard */
	B,
	/** The letter c on the keyboard */
	C,
	/** The letter d on the keyboard */
	D,
	/** The letter e on the keyboard */
	E,
	/** The letter f on the keyboard */
	F,
	/** The letter g on the keyboard */
	G,
	/** The letter h on the keyboard */
	H,
	/** The letter i on the keyboard */
	I,
	/** The letter j on the keyboard */
	J,
	/** The letter k on the keyboard */
	K,
	/** The letter l on the keyboard */
	L,
	/** The letter m on the keyboard */
	M,
	/** The letter n on the keyboard */
	N,
	/** The letter o on the keyboard */
	O,
	/** The letter p on the keyboard */
	P,
	/** The letter q on the keyboard */
	Q,
	/** The letter r on the keyboard */
	R,
	/** The letter s on the keyboard */
	S,
	/** The letter t on the keyboard */
	T,
	/** The letter u on the keyboard */
	U,
	/** The letter v on the keyboard */
	V,
	/** The letter w on the keyboard */
	W,
	/** The letter x on the keyboard */
	X,
	/** The letter y on the keyboard */
	Y,
	/** The letter z on the keyboard */
	Z,
	/** The space bar */
	SPACE,
	/** The number 1 above the character keys on the keyboard */
	NUM_1,
	/** The number 2 above the character keys on the keyboard */
	NUM_2,
	/** The number 3 above the character keys on the keyboard */
	NUM_3,
	/** The number 4 above the character keys on the keyboard */
	NUM_4,
	/** The number 5 above the character keys on the keyboard */
	NUM_5,
	/** The number 6 above the character keys on the keyboard */
	NUM_6,
	/** The number 7 above the character keys on the keyboard */
	NUM_7,
	/** The number 8 above the character keys on the keyboard */
	NUM_8,
	/** The number 9 above the character keys on the keyboard */
	NUM_9,
	/** The number 0 above the character keys on the keyboard */
	NUM_0,
	/** The double-zero in the number pad */
	PADD_00,
	/** The number 0 in the number pad */
	PAD_0,
	/** The number 1 in the number pad */
	PAD_1,
	/** The number 2 in the number pad */
	PAD_2,
	/** The number 3 in the number pad */
	PAD_3,
	/** The number 4 in the number pad */
	PAD_4,
	/** The number 5 in the number pad */
	PAD_5,
	/** The number 6 in the number pad */
	PAD_6,
	/** The number 7 in the number pad */
	PAD_7,
	/** The number 8 in the number pad */
	PAD_8,
	/** The number 9 in the number pad */
	PAD_9,
	/** The dot/period/decimal (.) key in the number pad */
	PAD_DOT,
	/** The plus key on the number pad */
	PAD_PLUS,
	/** The minus key on the number pad */
	PAD_MINUS,
	/** The star (times, *) key on the number pad */
	PAD_MULTIPLY,
	/** The slash (divide, /) key on the number pad */
	PAD_SLASH,
	/** The equal(=) key on the number pad */
	PAD_EQUAL,
	/** The enter key on the number pad */
	PAD_ENTER,
	/** The backspace key on the number pad */
	PAD_BACKSPACE,
	/** The number pad separator key */
	PAD_SEPARATOR,
	/** The clear key where Num Lock usually is on a numeric key pad on a Mac */
	CLEAR,
	/** The tab key */
	TAB,
	/** The caps lock key */
	CAPS_LOCK,
	/** The shift key on the left side of the keyboard */
	SHIFT_LEFT,
	/** The shift key on the right side of the keyboard */
	SHIFT_RIGHT,
	/** The control key on the left side of the keyboard */
	CTRL_LEFT,
	/** The control key on the right side of the keyboard */
	CTRL_RIGHT,
	/** The alt key on the left side of the keyboard */
	ALT_LEFT,
	/** The alt key on the right side of the keyboard */
	ALT_RIGHT,
	/** The windows key on PCs, or the command key on mac */
	COMMAND_KEY,
	/** The context menu key (usually next to the command key) */
	CONTEXT_MENU,
	/** The cancel key */
	CANCEL,
	/** The comma(,) or less than(<) key */
	COMMA,
	/** The dot or period(.) or greater than(.) key */
	DOT,
	/** The forward slash(/) or question mark(?) key */
	FORWARD_SLASH,
	/** The back slash (\) or vertical bar(|) key */
	BACK_SLASH,
	/** The semicolon(;) or colon(:) key */
	SEMICOLON,
	/** The single quote(') or double quote(") key */
	QUOTE,
	/** The enter key in the main keyboard */
	ENTER,
	/** The left square brace([) or curly brace({) key */
	LEFT_BRACE,
	/** The right square brace(]) or curly brace(}) key */
	RIGHT_BRACE,
	/** The dash or minus(-) or underscore(_) key */
	MINUS,
	/** The equal(=) or plus(+) key */
	EQUAL,
	/** The backspace key */
	BACKSPACE,
	/** The left arrow key in the main keyboard. Pressing 4 with num lock off generates a key code of {@link #PAD_4} */
	LEFT_ARROW,
	/** The right arrow key in the main keyboard. Pressing 6 with num lock off generates a key code of {@link #PAD_6} */
	RIGHT_ARROW,
	/** The left arrow key in the main keyboard. Pressing 8 with num lock off generates a key code of {@link #PAD_8} */
	UP_ARROW,
	/** The down arrow key in the main keyboard. Pressing 2 with num lock off generates a key code of {@link #PAD_2} */
	DOWN_ARROW,
	/** The back-quote (`) or tilde(~) key */
	BACK_QUOTE,
	/** The escape (Esc) key */
	ESCAPE,
	/** The first function key */
	F1,
	/** The second function key */
	F2,
	/** The third function key */
	F3,
	/** The forth function key */
	F4,
	/** The fifth function key */
	F5,
	/** The sixth function key */
	F6,
	/** The seventh function key */
	F7,
	/** The eighth function key */
	F8,
	/** The ninth function key */
	F9,
	/** The tenth function key */
	F10,
	/** The eleventh function key */
	F11,
	/** The twelfth function key */
	F12,
	/** The thirteenth function key */
	F13,
	/** The fourteenth function key */
	F14,
	/** The fifteenth function key */
	F15,
	/** The sixteenth function key */
	F16,
	/** The seventeenth function key */
	F17,
	/** The eighteenth function key */
	F18,
	/** The nineteenth function key */
	F19,
	/** The twentieth function key */
	F20,
	/** The twenty first function key */
	F21,
	/** The twenty second function key */
	F22,
	/** The twenty third function key */
	F23,
	/** The twenty fourth function key */
	F24,
	/** The print screen (Prnt Scrn) key */
	PRINT_SCREEN,
	/** The number lock (Num Lk) key */
	NUM_LOCK,
	/** The scroll lock (Scrl Lk) key */
	SCROLL_LOCK,
	/** The pause key */
	PAUSE,
	/** The insert key */
	INSERT,
	/** The delete key */
	DELETE,
	/** The home key in the main keyboard (pressing 7 on the key pad with num lock off generates a key code of {@link #PAD_7} */
	HOME,
	/** The end key in the main keyboard (pressing 1 on the key pad with num lock off generates a key code of {@link #PAD_1} */
	END,
	/** The page up key in the main keyboard (pressing 9 on the key pad with num lock off generates a key code of {@link #PAD_9} */
	PAGE_UP,
	/** The pagedown key in the main keyboard (pressing 3 on the key pad with num lock off generates a key code of {@link #PAD_3} */
	PAGE_DOWN,
	/** The meta key */
	META,
	/** The help key */
	HELP;
}
