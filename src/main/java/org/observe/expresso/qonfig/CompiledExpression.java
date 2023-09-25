package org.observe.expresso.qonfig;

import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ObservableExpression;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

/**
 * Returned by {@link ExpressoQIS#getValueExpression()} and other methods e.g. for attributes. This structure contains all the information
 * needed to evaluate an expression, including its environment, position, etc.
 */
public class CompiledExpression implements LocatedExpression {
	private final ObservableExpression theExpression;
	private final QonfigElement theElement;
	private final LocatedPositionedContent thePosition;
	private ExpressoQIS theSession;
	private CompiledExpressoEnv theEnv;

	/**
	 * @param expression The expression to be evaluated
	 * @param element The QonfigElement where the expression was defined
	 * @param position The position in the Qonfig file of the start of the expression
	 * @param session The Expresso session in which to evaluate the expression
	 */
	public CompiledExpression(ObservableExpression expression, QonfigElement element, LocatedPositionedContent position,
		ExpressoQIS session) {
		theExpression = expression;
		theElement = element;
		thePosition = position;
		theSession = session;
	}

	@Override
	public ObservableExpression getExpression() {
		return theExpression;
	}

	/** @return The QonfigElement where the expression was defined */
	public QonfigElement getElement() {
		return theElement;
	}

	@Override
	public LocatedPositionedContent getFilePosition() {
		return thePosition;
	}

	@Override
	public int length() {
		return theExpression.getExpressionLength();
	}

	/**
	 * @return The model type of this expression
	 * @throws ExpressoCompilationException If the model type could not be evaluated
	 */
	public ModelType<?> getModelType() throws ExpressoCompilationException {
		if (theEnv == null) {
			theEnv = theSession.getExpressoEnv();
			theSession = null; // Don't need it anymore--release it
		}
		return theExpression.getModelType(theEnv);
	}

	/**
	 * Throws an {@link QonfigInterpretationException}
	 *
	 * @param message The message for the exception
	 * @param cause The cause of the exception (may be null)
	 * @throws QonfigInterpretationException The {@link QonfigInterpretationException}
	 */
	public void throwQonfigException(String message, Throwable cause) throws QonfigInterpretationException {
		LocatedFilePosition position = thePosition == null ? null
			: new LocatedFilePosition(theElement.getDocument().getLocation(), thePosition.getPosition(0));
		int length = length();
		if (cause == null)
			throw new QonfigInterpretationException(message, position, length);
		else if (message == null)
			throw new QonfigInterpretationException(cause.getMessage(), position, length, cause);
		else
			throw new QonfigInterpretationException(message, position, length, cause);
	}

	/**
	 * Throws an {@link ExpressoInterpretationException}
	 *
	 * @param message The message for the exception
	 * @param cause The cause of the exception (may be null)
	 * @throws ExpressoInterpretationException The {@link ExpressoInterpretationException}
	 */
	public void throwException(String message, Throwable cause) throws ExpressoInterpretationException {
		LocatedFilePosition position = thePosition == null ? null
			: new LocatedFilePosition(theElement.getDocument().getLocation(), thePosition.getPosition(0));
		int length = length();
		if (cause == null)
			throw new ExpressoInterpretationException(message, position, length);
		else if (message == null)
			throw new ExpressoInterpretationException(cause.getMessage(), position, length, cause);
		else
			throw new ExpressoInterpretationException(message, position, length, cause);
	}

	@Override
	public String toString() {
		return theExpression.toString();
	}
}
