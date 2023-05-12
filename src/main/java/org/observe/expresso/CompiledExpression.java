package org.observe.expresso;

import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigValueDef;
import org.qommons.io.LocatedContentPosition;
import org.qommons.io.LocatedFilePosition;

/**
 * Returned by {@link ExpressoQIS#getValueExpression()} and other methods e.g. for attributes. This structure contains all the information
 * needed to evaluate an expression, including its environment, position, etc.
 */
public class CompiledExpression implements LocatedExpression {
	private final ObservableExpression theExpression;
	private final QonfigElement theElement;
	private final QonfigValueDef theDef;
	private final LocatedContentPosition thePosition;
	private ExpressoQIS theSession;
	private ExpressoEnv theEnv;

	/**
	 * @param expression The expression to be evaluated
	 * @param element The QonfigElement where the expression was defined
	 * @param def The {@link QonfigValueDef} containing the actual definition of the expression
	 * @param position The position in the Qonfig file of the start of the expression
	 * @param session The Expresso session in which to evaluate the expression
	 */
	public CompiledExpression(ObservableExpression expression, QonfigElement element, QonfigValueDef def, LocatedContentPosition position,
		ExpressoQIS session) {
		theExpression = expression;
		theElement = element;
		theDef = def;
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

	/** @return The {@link QonfigValueDef} containing the actual definition of the expression */
	public QonfigValueDef getDef() {
		return theDef;
	}

	@Override
	public LocatedContentPosition getFilePosition() {
		return thePosition;
	}

	@Override
	public int length() {
		return theExpression.getExpressionLength();
	}

	/** @return The model type of this expression */
	public ModelType<?> getModelType() {
		if (theEnv == null) {
			theEnv = theSession.getExpressoEnv();
			theSession = null; // Don't need it anymore--release it
		}
		return theExpression.getModelType(theEnv);
	}

	/**
	 * @param <M> The model type to evaluate the expression as
	 * @param <MV> The instance type to evaluate the expression as
	 * @param type The type to evaluate the expression as
	 * @return The evaluated expression
	 * @throws ExpressoInterpretationException If the expression could not be evaluated as the given type
	 */
	public <M, MV extends M> ModelValueSynth<M, MV> evaluate(ModelInstanceType<M, MV> type) throws ExpressoInterpretationException {
		if (theEnv == null) {
			theEnv = theSession.getExpressoEnv();
			theSession = null; // Don't need it anymore--release it
		}
		return evaluate(type, theEnv.at(getFilePosition()));
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
