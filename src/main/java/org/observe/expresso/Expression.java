package org.observe.expresso;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.qommons.LambdaUtils;
import org.qommons.StringUtils;
import org.qommons.collect.BetterList;

/** This class compiles text parsing results from ANTLR v4 into a structure that is easy to navigate and search */
public interface Expression {
	/** Thrown from {@link Expression#of(Parser, ParseTree)} in response to text parse errors encountered by ANTLR */
	public static class ExpressoParseException extends ParseException {
		private final int theEndIndex;
		private final String theType;
		private final String theText;

		/**
		 * @param errorOffset The start index of the error
		 * @param endIndex The end index of the error
		 * @param type The token type of the error
		 * @param text The text content that is the source of the error
		 * @param message The message for the exception
		 */
		public ExpressoParseException(int errorOffset, int endIndex, String type, String text, String message) {
			super(message + " ( " + text + ", type " + type + " ) at position " + errorOffset, errorOffset);
			theEndIndex = endIndex;
			theType = type;
			theText = text;
		}

		/**
		 * @param exp The expression
		 * @param message The message for the exception
		 */
		public ExpressoParseException(Expression exp, String message) {
			this(exp.getStartIndex(), exp.getEndIndex(), exp.getType(), exp.getText(), message);
		}

		/** @return The start index of the error */
		@Override
		public int getErrorOffset() {
			return super.getErrorOffset();
		}

		/** @return The end index of the error */
		public int getEndIndex() {
			return theEndIndex;
		}

		/** @return The token type of the error */
		public String getType() {
			return theType;
		}

		/** @return The text content that is the source of the error */
		public String getText() {
			return theText;
		}
	}

	/** @return The token type of this expression */
	String getType();

	/** @return The start index of this expression in the content */
	int getStartIndex();

	/** @return The end index of this expression in the content */
	default int getEndIndex() {
		return getStartIndex() + getText().length();
	}

	/** @return The component expressions that this expression is composed of */
	BetterList<Expression> getComponents();

	/**
	 * A simplified and more performant form of {@link #search()}
	 *
	 * @param type The type name path of the component to get
	 * @return The first expression under this expression that follows the given type path
	 */
	default Expression getComponent(String... type) {
		return DirectSearch.getComponent(this, type);
	}

	/**
	 * A simplified and more performant form of {@link #search()}
	 *
	 * @param type The type name path of the component to get
	 * @return All expressions under this expression that follows the given type path
	 */
	default BetterList<Expression> getComponents(String... type) {
		return DirectSearch.getComponents(this, type);
	}

	/** @return The text that specified this expression */
	String getText();

	/**
	 * Prints this expression's structure in a multi-line format
	 *
	 * @param str The StringBuilder to append to (one will be created if null)
	 * @param indent The amount by which to indent the first line
	 * @return The StringBuilder
	 */
	StringBuilder printStructure(StringBuilder str, int indent);

	/** @return A structure to use to configure and execute a search for content within this expression tree */
	default ExpressionSearch search() {
		return new ExpressionSearch(this);
	}

	/** @return The content of this expression */
	@Override
	String toString();

	/**
	 * @param type The type for the expression
	 * @param content The content for the expression
	 * @return The new expression
	 */
	public static Expression create(String type, String content) {
		return new Terminal(type, 0, content);
	}

	/**
	 * @param parser The ANTLR parser that did the text parsing
	 * @param expression The expression to translate
	 * @return An {@link Expression} representing the same parsed information as the given expression
	 * @throws ExpressoParseException If there was an error in the expression
	 */
	public static Expression of(Parser parser, ParseTree expression) throws ExpressoParseException {
		ParseTreeWalker walker = new ParseTreeWalker();

		ExpressoAntlrCompiler compiler = new ExpressoAntlrCompiler(parser);
		try {
			walker.walk(compiler, expression);
		} catch (ExpressoAntlrCompiler.InternalExpressoErrorException e) {
			String displayType = parser.getVocabulary().getDisplayName(e.token.getType());
			throw new ExpressoParseException(e.token.getStartIndex(), e.token.getStopIndex(), displayType, e.token.getText(),
				e.getMessage());
		}
		return compiler.getRoot();
	}

	/** Implements the simple {@link Expression#getComponent(String[]) getComponent(s)(String...)} methods */
	static class DirectSearch {
		static Expression getComponent(Expression root, String[] type) {
			return getComponents(root, type, 0, null);
		}

		static BetterList<Expression> getComponents(Expression root, String[] type) {
			List<Expression> found = new ArrayList<>(3);
			getComponents(root, type, 0, found::add);
			return BetterList.of(found);
		}

		private static Expression getComponents(Expression parent, String[] type, int pathIndex, Consumer<Expression> onFind) {
			for (Expression child : parent.getComponents()) {
				if (!child.getType().equals(type[pathIndex])) {//
				} else if (pathIndex + 1 == type.length) {
					if (onFind != null)
						onFind.accept(child);
					else
						return child;
				} else {
					Expression childFound = getComponents(child, type, pathIndex + 1, onFind);
					if (childFound != null)
						return childFound;
				}
			}
			return null;
		}
	}

	/** A search operation against an expression */
	public static class ExpressionSearch {
		private final Expression theRoot;
		private final List<ExpressionSearchOp> theSequence;

		/** @param root The expression to search within */
		public ExpressionSearch(Expression root) {
			theRoot = root;
			theSequence = new ArrayList<>();
		}

		/**
		 * Causes the search to descend to a descendant of the searched expression. When this is used, any results will be at or underneath
		 * the given path.
		 *
		 * @param path A path of expression types to descend into
		 * @return This search
		 */
		public ExpressionSearch get(String... path) {
			theSequence.add(new PathSearchOp(path));
			return this;
		}

		/**
		 * Causes the search to descend to all children of the searched expression
		 *
		 * @return This search
		 */
		public ExpressionSearch children() {
			theSequence.add(new AllChildrenOp());
			return this;
		}

		/**
		 * Causes the search to descend to a child of the searched expression. When this is used, any results will be at or underneath the
		 * given child.
		 *
		 * @param child The function to select from the search node's children
		 * @return This search
		 */
		public ExpressionSearch child(Function<BetterList<Expression>, Expression> child) {
			theSequence.add(new ChildSearchOp(child));
			return this;
		}

		/**
		 * Causes the search to descend to the first child of the searched expression. When this is used, any results will be at or
		 * underneath the child.
		 *
		 * @return This search
		 */
		public ExpressionSearch firstChild() {
			theSequence.add(new ChildSearchOp(LambdaUtils.printableFn(BetterList::peekFirst, "first", null)));
			return this;
		}

		/**
		 * Applies a test to any search results matched so far
		 *
		 * @param filter A test for any expressions matched so far
		 * @return This search
		 */
		public ExpressionSearch iff(Predicate<Expression> filter) {
			theSequence.add(new SimpleSearchFilter(filter));
			return this;
		}

		/**
		 * Allows filtering of search results based on deep content. E.g.
		 * <p>
		 * <code>expression.search().get("a").where(srch->srch.get("b").text("c"))</code>
		 * </p>
		 * will search for "a"-typed elements in expression that have a "b"-typed expression with text "c". I.e., the result(s) of the
		 * search will be of type "a", and will all have at least one descendant of type "b" with text "c".
		 *
		 * @param filter The filter search
		 * @return This search
		 */
		public ExpressionSearch where(Consumer<ExpressionSearch> filter) {
			ExpressionSearch search = new ExpressionSearch(null);
			filter.accept(search);
			theSequence.add(new ComplexSearchFilter(search));
			return this;
		}

		/**
		 * Filters the current search results for text content
		 *
		 * @param text The text to match against the current search results
		 * @return This search
		 */
		public ExpressionSearch text(String text) {
			return iff(LambdaUtils.printablePred(ex -> ex.toString().equals(text), "text:" + text, null));
		}

		/**
		 * Filters the current search results for text content with a pattern
		 *
		 * @param pattern The pattern to match against the text of the current search results
		 * @return This search
		 */
		public ExpressionSearch textLike(String pattern) {
			return textLike(Pattern.compile(pattern));
		}

		/**
		 * Filters the current search results for text content with a pattern
		 *
		 * @param pattern The pattern to match against the text of the current search results
		 * @return This search
		 */
		public ExpressionSearch textLike(Pattern pattern) {
			return iff(LambdaUtils.printablePred(ex -> pattern.matcher(ex.toString()).matches(), "textLike:" + pattern.pattern(), null));
		}

		/**
		 * Same as {@link #findAny()}, but throws a {@link NoSuchElementException} if no expression matches the search.
		 *
		 * @return The first expression (depth-first) under the root that matches the search
		 * @throws NoSuchElementException If no expression matches the search
		 */
		public Expression find() throws NoSuchElementException {
			Expression found = findAny();
			if (found == null)
				throw new IllegalArgumentException("No such expression found: " + this);
			return found;
		}

		/** @return The first expression (depth-first) under the root that matches the search, or null if no expressions matched */
		public Expression findAny() {
			return findAny(theRoot);
		}

		/** @return All expressions (in depth-first order) under the root that match the search */
		public BetterList<Expression> findAll() {
			List<Expression> found = new ArrayList<>();
			findAll(Arrays.asList(theRoot), found);
			return BetterList.of(found);
		}

		Expression findAny(Expression root) {
			List<Expression> found = new ArrayList<>();
			findAll(Arrays.asList(root), found);
			if (found.isEmpty())
				return null;
			return found.get(0);
		}

		void findAll(List<Expression> root, List<Expression> found) {
			List<Expression> intermediate = new ArrayList<>();
			boolean first = true;
			for (ExpressionSearchOp search : theSequence) {
				if (first) {
					intermediate.addAll(root);
					first = false;
				} else {
					intermediate.clear();
					intermediate.addAll(found);
					found.clear();
				}
				search.findAll(intermediate, found);
			}
		}

		@Override
		public String toString() {
			return theSequence.toString();
		}

		interface ExpressionSearchOp {
			void findAll(List<Expression> intermediate, List<Expression> found);
		}

		static class PathSearchOp implements ExpressionSearchOp {
			private final String[] thePath;

			PathSearchOp(String[] path) {
				thePath = path;
			}

			@Override
			public void findAll(List<Expression> intermediate, List<Expression> found) {
				for (Expression ex : intermediate)
					find(ex, 0, found::add, true);
			}

			Expression find(Expression ex, int pathIndex, Consumer<Expression> found, boolean multi) {
				if (ex.getType().equalsIgnoreCase(thePath[pathIndex])) {
					pathIndex++;
					if (pathIndex == thePath.length) {
						if (found != null)
							found.accept(ex);
						return ex;
					}
				}
				for (Expression child : ex.getComponents()) {
					Expression childFound = find(child, pathIndex, found, multi);
					if (!multi && childFound != null)
						return childFound;
				}
				return null;
			}

			@Override
			public String toString() {
				return StringUtils.print(".", Arrays.asList(thePath), p -> p).toString();
			}
		}

		static class AllChildrenOp implements ExpressionSearchOp {
			@Override
			public void findAll(List<Expression> intermediate, List<Expression> found) {
				for (Expression e : intermediate)
					found.addAll(e.getComponents());
			}
		}

		static class ChildSearchOp implements ExpressionSearchOp {
			private final Function<BetterList<Expression>, Expression> theChild;

			ChildSearchOp(Function<BetterList<Expression>, Expression> child) {
				theChild = child;
			}

			@Override
			public void findAll(List<Expression> intermediate, List<Expression> found) {
				for (Expression ex : intermediate) {
					Expression child = theChild.apply(ex.getComponents());
					if (child != null)
						found.add(child);
				}
			}

			@Override
			public String toString() {
				return "child:" + theChild;
			}
		}

		static class SimpleSearchFilter implements ExpressionSearchOp {
			private final Predicate<Expression> theFilter;

			SimpleSearchFilter(Predicate<Expression> filter) {
				theFilter = filter;
			}

			@Override
			public void findAll(List<Expression> intermediate, List<Expression> found) {
				for (Expression ex : intermediate)
					if (theFilter.test(ex))
						found.add(ex);
			}

			@Override
			public String toString() {
				return "if:" + theFilter;
			}
		}

		static class ComplexSearchFilter implements ExpressionSearchOp {
			private final ExpressionSearch theSearch;

			ComplexSearchFilter(ExpressionSearch search) {
				theSearch = search;
			}

			@Override
			public void findAll(List<Expression> intermediate, List<Expression> found) {
				for (Expression ex : intermediate) {
					Expression exFound = theSearch.findAny(ex);
					if (exFound != null)
						found.add(ex);
				}
			}

			@Override
			public String toString() {
				return theSearch.toString();
			}
		}
	}

	/** A simple expression with no children--generally a literal or pattern match */
	public class Terminal implements Expression {
		private final String theType;
		private final int theStart;
		private final String theText;

		/**
		 * @param type The type name of the expression
		 * @param start The start index of the expression
		 * @param text The expression text
		 */
		public Terminal(String type, int start, String text) {
			theType = type;
			theStart = start;
			theText = text;
		}

		@Override
		public String getType() {
			return theType;
		}

		@Override
		public int getStartIndex() {
			return theStart;
		}

		@Override
		public BetterList<Expression> getComponents() {
			return BetterList.empty();
		}

		@Override
		public String getText() {
			return theText;
		}

		@Override
		public StringBuilder printStructure(StringBuilder str, int indent) {
			if (str == null)
				str = new StringBuilder();
			for (int i = 0; i < indent; i++)
				str.append('\t');
			str.append(theType).append(": ").append(theText);
			return str;
		}

		@Override
		public String toString() {
			return theText;
		}
	}

	/** An expression made up of other expressions */
	public class Composite implements Expression {
		private final String theType;
		private final String theText;
		private final BetterList<Expression> theComponents;

		/**
		 * @param type The type of the expression
		 * @param text The text that specified this expression
		 * @param components The components for the expression
		 */
		public Composite(String type, String text, BetterList<Expression> components) {
			theType = type;
			theText = text;
			theComponents = components;
		}

		@Override
		public String getType() {
			return theType;
		}

		@Override
		public int getStartIndex() {
			return theComponents.get(0).getStartIndex();
		}

		@Override
		public BetterList<Expression> getComponents() {
			return theComponents;
		}

		@Override
		public String getText() {
			return theText;
		}

		@Override
		public StringBuilder printStructure(StringBuilder str, int indent) {
			if (str == null)
				str = new StringBuilder();
			for (int i = 0; i < indent; i++)
				str.append('\t');
			str.append(theType).append(": ");
			boolean first = true;
			for (Expression component : theComponents) {
				component.printStructure(str, first ? 0 : indent + 1).append('\n');
				first = false;
			}
			return str;
		}

		@Override
		public String toString() {
			return StringUtils.print(new StringBuilder(), "", theComponents, StringBuilder::append).toString();
		}
	}

	/** Translates ANTLR v4 parse trees into {@link Expression}s */
	static class ExpressoAntlrCompiler implements ParseTreeListener {
		static class InternalExpressoErrorException extends RuntimeException {
			final Token token;

			public InternalExpressoErrorException(Token token) {
				super(token.toString());
				this.token = token;
			}
		}

		private final Parser theParser;
		private final LinkedList<List<Expression>> theStack;
		private Expression theRoot;

		public ExpressoAntlrCompiler(Parser parser) {
			theParser = parser;
			theStack = new LinkedList<>();
		}

		public Expression getRoot() {
			return theRoot;
		}

		@Override
		public void enterEveryRule(ParserRuleContext arg0) {
			String ruleName = theParser.getRuleNames()[arg0.getRuleIndex()];
			int childCount = arg0.getChildCount();

			List<Expression> children = new ArrayList<>(childCount);
			Expression ex = new Composite(ruleName, arg0.getText(), BetterList.of(children));
			if (!theStack.isEmpty())
				theStack.getLast().add(ex);
			theStack.add(children);
			if (theRoot == null)
				theRoot = ex;
		}

		@Override
		public void exitEveryRule(ParserRuleContext arg0) {
		}

		@Override
		public void visitErrorNode(ErrorNode arg0) {
			throw new InternalExpressoErrorException(arg0.getSymbol());
		}

		@Override
		public void visitTerminal(TerminalNode arg0) {
			if (arg0.getSymbol().getType() < 0)
				return;
			String displayType = theParser.getVocabulary().getDisplayName(arg0.getSymbol().getType());
			Expression ex = new Terminal(displayType, //
				arg0.getSymbol().getStartIndex(), arg0.getSymbol().getText());
			if (!theStack.isEmpty())
				theStack.getLast().add(ex);
			if (theRoot == null)
				theRoot = ex;
		}
	}
}
