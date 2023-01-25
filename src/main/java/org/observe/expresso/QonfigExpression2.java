package org.observe.expresso;

import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigEvaluationException;
import org.qommons.config.QonfigFilePosition;
import org.qommons.config.QonfigValueDef;
import org.qommons.io.SimpleXMLParser.ContentPosition;

public class QonfigExpression2 {
	private final ObservableExpression theExpression;
	private final QonfigElement theElement;
	private final QonfigValueDef theType;
	private final ContentPosition thePosition;
	private ExpressoQIS theSession;
	private ExpressoEnv theEnv;

	public QonfigExpression2(ObservableExpression expression, QonfigElement element, QonfigValueDef type, ContentPosition position,
		ExpressoQIS session) {
		theExpression = expression;
		theElement = element;
		theType = type;
		thePosition = position;
		theSession = session;
	}

	public ObservableExpression getExpression() {
		return theExpression;
	}

	public QonfigElement getElement() {
		return theElement;
	}

	public QonfigValueDef getType() {
		return theType;
	}

	public ContentPosition getPosition() {
		return thePosition;
	}

	public QonfigFilePosition getFilePosition() {
		return thePosition == null ? null : new QonfigFilePosition(theElement.getDocument().getLocation(), thePosition.getPosition(0));
	}

	public int length() {
		return theExpression.getExpressionEnd() - theExpression.getExpressionOffset();
	}

	public <M, MV extends M> ValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type) throws QonfigEvaluationException {
		if (theEnv == null) {
			theEnv = theSession.getExpressoEnv();
			theSession = null; // Don't need it anymore--release it
		}
		return evaluate(type, theEnv);
	}

	public <M, MV extends M> ValueContainer<M, MV> evaluate(ModelInstanceType<M, MV> type, ExpressoEnv env)
		throws QonfigEvaluationException {
		try {
			return theExpression.evaluate(type, env);
		} catch (ExpressoEvaluationException e) {
			QonfigFilePosition position;
			if (thePosition == null || e.getErrorOffset() < 0)
				position = null;
			else
				position = new QonfigFilePosition(theElement.getDocument().getLocation(), thePosition.getPosition(e.getErrorOffset()));
			throw new QonfigEvaluationException("Could not interpret " + theType, e, position, e.getEndIndex() - e.getErrorOffset());
		}
	}

	public void throwException(String message, Throwable cause) throws QonfigEvaluationException {
		QonfigFilePosition position = thePosition == null ? null
			: new QonfigFilePosition(theElement.getDocument().getLocation(), thePosition.getPosition(theExpression.getExpressionOffset()));
		int length = length();
		if (cause == null)
			throw new QonfigEvaluationException(message, position, length);
		else if (message == null)
			throw new QonfigEvaluationException(cause, position, length);
		else
			throw new QonfigEvaluationException(message, cause, position, length);
	}
}
