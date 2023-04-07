package org.observe.expresso;

import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigValueDef;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.SimpleXMLParser.ContentPosition;

/**
 * Returned by {@link ExpressoQIS#getValueExpression()} and other methods e.g. for attributes. This structure contains all the information
 * needed to evaluate an expression, including its environment, position, etc.
 */
public class QonfigExpression2 {
	private final ObservableExpression theExpression;
	private final QonfigElement theElement;
	private final QonfigValueDef theDef;
	private final ContentPosition thePosition;
	private ExpressoQIS theSession;
	private ExpressoEnv theEnv;

	/**
	 * @param expression The expression to be evaluated
	 * @param element The QonfigElement where the expression was defined
	 * @param def The {@link QonfigValueDef} containing the actual definition of the expression
	 * @param position The position in the Qonfig file of the start of the expression
	 * @param session The Expresso session in which to evaluate the expression
	 */
	public QonfigExpression2(ObservableExpression expression, QonfigElement element, QonfigValueDef def, ContentPosition position,
		ExpressoQIS session) {
		theExpression = expression;
		theElement = element;
		theDef = def;
		thePosition = position;
		theSession = session;
	}

	/** @return The observable expression */
	public ObservableExpression getExpression() {
		return theExpression;
	}

	/** @return The QonfigElement where the expression was defined */
	public QonfigElement getElement() {
		return theElement;
	}

	/** @return The {@link QonfigValueDef} containing the actual definition of the expression */
	public QonfigValueDef getDef() {
		return theDef;
	}

	/** @return The position in the Qonfig file of the start of the expression */
	public ContentPosition getPosition() {
		return thePosition;
	}

	/** @return The position in the Qonfig file of the start of the expression */
	public LocatedFilePosition getFilePosition() {
		return thePosition == null ? null : new LocatedFilePosition(theElement.getDocument().getLocation(), thePosition.getPosition(0));
	}

	/** @return The length of the expression */
	public int length() {
		return theExpression.getExpressionEnd() - theExpression.getExpressionOffset();
	}

	/**
	 * @param <M> The model type to evaluate the expression as
	 * @param <MV> The instance type to evaluate the expression as
	 * @param type The type to evaluate the expression as
	 * @return The evaluated expression
	 * @throws ExpressoInterpretationException If the expression could not be evaluated as the given type
	 */
	public <M, MV extends M> ValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type) throws ExpressoInterpretationException {
		if (theEnv == null) {
			theEnv = theSession.getExpressoEnv();
			theSession = null; // Don't need it anymore--release it
		}
		return evaluate(type, theEnv);
	}

	/**
	 * @param <M> The model type to evaluate the expression as
	 * @param <MV> The instance type to evaluate the expression as
	 * @param type The type to evaluate the expression as
	 * @param env The expresso environment to use instead of the one retrieved from the session
	 * @return The evaluated expression
	 * @throws ExpressoInterpretationException If the expression could not be evaluated as the given type
	 */
	public <M, MV extends M> ValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws ExpressoInterpretationException {
		try {
			return theExpression.evaluate(type, env);
		} catch (ExpressoEvaluationException e) {
			LocatedFilePosition position;
			if (thePosition == null || e.getErrorOffset() < 0)
				position = null;
			else
				position = new LocatedFilePosition(theElement.getDocument().getLocation(), thePosition.getPosition(e.getErrorOffset()));
			throw new ExpressoInterpretationException("Could not interpret " + theDef, position, e.getEndIndex() - e.getErrorOffset(), e);
		} catch (TypeConversionException e) {
			LocatedFilePosition position;
			if (thePosition == null)
				position = null;
			else
				position = new LocatedFilePosition(theElement.getDocument().getLocation(),
					thePosition.getPosition(theExpression.getExpressionOffset()));
			throw new ExpressoInterpretationException("Could not interpret " + theDef, position,
				theExpression.getExpressionEnd() - theExpression.getExpressionOffset(), e);
		}
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
			: new LocatedFilePosition(theElement.getDocument().getLocation(), thePosition.getPosition(theExpression.getExpressionOffset()));
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
			: new LocatedFilePosition(theElement.getDocument().getLocation(), thePosition.getPosition(theExpression.getExpressionOffset()));
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
