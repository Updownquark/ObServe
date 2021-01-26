package org.observe.remote;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public interface TextTransfer {
	interface TextTransaction {
		Writer write();

		Reader send() throws IOException;
	}

	TextTransaction createTransaction();
}