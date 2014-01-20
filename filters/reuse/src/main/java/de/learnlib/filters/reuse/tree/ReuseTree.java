/* Copyright (C) 2013 TU Dortmund
 * This file is part of LearnLib, http://www.learnlib.de/.
 * 
 * LearnLib is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License version 3.0 as published by the Free Software Foundation.
 * 
 * LearnLib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with LearnLib; if not, see
 * <http://www.gnu.de/documents/lgpl.en.html>.
 */
package de.learnlib.filters.reuse.tree;

import de.learnlib.filters.reuse.ReuseCapableOracle.QueryResult;
import de.learnlib.filters.reuse.ReuseException;
import de.learnlib.filters.reuse.ReuseOracle;
import de.learnlib.filters.reuse.tree.ReuseNode.NodeResult;
import net.automatalib.graphs.abstractimpl.AbstractGraph;
import net.automatalib.graphs.dot.DOTPlottableGraph;
import net.automatalib.graphs.dot.GraphDOTHelper;
import net.automatalib.words.Alphabet;
import net.automatalib.words.Word;
import net.automatalib.words.WordBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The {@link ReuseTree} is a tree like structure consisting of nodes (see
 * {@link ReuseNode}) and edges (see {@link ReuseEdge}) that is used by the
 * {@link ReuseOracle}:
 * <ul>
 * <li>Nodes may contain a system state (see {@link ReuseNode#getSystemState()})
 * that could be used for executing suffixes of membership queries. Each node
 * consists of a (possible empty) set of outgoing edges.
 * <li>Edges consists beside source and target node of input and output
 * behavior.
 * </ul>
 * The {@link ReuseTree} is the central data structure that maintains observed
 * behavior from the SUL and maintains also available system states. The
 * {@link ReuseTree} is only 'tree like' since it may contain reflexive edges at
 * nodes (only possible if {@link ReuseTreeBuilder#withFailureOutputs(Set)} or
 * {@link ReuseTreeBuilder#withInvariantInputs(Set)} is set).
 * 
 * @author Oliver Bauer <oliver.bauer@tu-dortmund.de>
 * 
 * @param <S> system state class
 * @param <I> input symbol class
 * @param <O> output symbol class
 */
public class ReuseTree<S, I, O> extends AbstractGraph<ReuseNode<S, I, O>, ReuseEdge<S, I, O>>
	implements DOTPlottableGraph<ReuseNode<S, I, O>, ReuseEdge<S, I, O>> {
	
	public static class ReuseTreeBuilder<S,I,O> {
		// mandatory
		private final Alphabet<I> alphabet;
		
		// optional
		private boolean invalidateSystemstates = true;
		private SystemStateHandler<S> systemStateHandler;
		private Set<I> invariantInputSymbols;
		private Set<O> failureOutputSymbols;		
		
		public ReuseTreeBuilder(Alphabet<I> alphabet) {
			this.alphabet = alphabet;
			this.systemStateHandler = new SystemStateHandler<S>() {
				@Override
				public void dispose(final S state) {
				}
			};
			this.invariantInputSymbols = new HashSet<>();
			this.failureOutputSymbols = new HashSet<>();
		}
		
		public ReuseTreeBuilder<S,I,O> withSystemStateHandler(SystemStateHandler<S> systemStateHandler) {
			this.systemStateHandler = systemStateHandler;
			return this;
		}

		public ReuseTreeBuilder<S,I,O> withEnabledSystemstateInvalidation(boolean invalidate) {
			this.invalidateSystemstates = invalidate;
			return this;
		}
		
		public ReuseTreeBuilder<S,I,O> withInvariantInputs(Set<I> inputs) {
			this.invariantInputSymbols = inputs;
			return this;
		}
		
		public ReuseTreeBuilder<S,I,O> withFailureOutputs(Set<O> outputs) {
			this.failureOutputSymbols = outputs;
			return this;
		}
		
		public ReuseTree<S, I, O> build() {
			return new ReuseTree<>(this);
		}
	}
	
	private final Alphabet<I> alphabet;
	private final int alphabetSize;
	
	private final Set<I> invariantInputSymbols;
	private final Set<O> failureOutputSymbols;

	private final boolean invalidateSystemstates;
	private final SystemStateHandler<S> systemStateHandler;

	/** Maybe reset to zero, see {@link ReuseTree#clearTree()} */
	private int nodeCount = 0;
	/** Maybe reinitialized , see {@link ReuseTree#clearTree()} */
	private ReuseNode<S, I, O> root;
	
	private ReuseTree(ReuseTreeBuilder<S,I,O> builder) {
		this.alphabet = builder.alphabet;
		this.invalidateSystemstates = builder.invalidateSystemstates;
		this.systemStateHandler = builder.systemStateHandler;
		this.invariantInputSymbols = builder.invariantInputSymbols;
		this.failureOutputSymbols = builder.failureOutputSymbols;
		
		// local and not configurable
		this.alphabetSize = alphabet.size();
		this.root = new ReuseNode<>(nodeCount++, alphabetSize);
	}

	/**
	 * Returns the root {@link ReuseNode} of the {@link ReuseTree}.
	 *
	 * @return root The root of the tree, never {@code null}.
	 */
	public final ReuseNode<S, I, O> getRoot() {
		return this.root;
	}

	/**
	 * Returns the known output for the given query or {@code null} if not
	 * known.
	 * 
	 * @param query
	 *            Not allowed to be {@code null}.
	 * @return The output for {@code query} if already known from the
	 *         {@link ReuseTree} or {@code null} if unknown.
	 */
	public final synchronized Word<O> getOutput(final Word<I> query) {
		if (query == null) {
			String msg = "Query is not allowed to be null.";
			throw new IllegalArgumentException(msg);
		}

		WordBuilder<O> output = new WordBuilder<>();

		ReuseNode<S, I, O> sink = getRoot();
		ReuseNode<S, I, O> node;
		ReuseEdge<S, I, O> edge;
		for (I symbol : query) {
			int index = alphabet.getSymbolIndex(symbol);
			edge = sink.getEdgeWithInput(index);

			if (edge == null) {
				return null;
			}

			node = edge.getTarget();
			output.add(edge.getOutput());
			sink = node;
		}

		return output.toWord();
	}

	/**
	 * This method removes all system states from the tree. The tree structure
	 * remains, but there will be nothing for reusage.
	 * <p>
	 * The {@link SystemStateHandler} (
	 * {@link #setSystemStateHandler(SystemStateHandler)}) will be informed
	 * about all disposings.
	 */
	public final synchronized void disposeSystemstates() {
		disposeSystemstates(getRoot());
	}

	private void disposeSystemstates(ReuseNode<S, I, O> node) {
		if (node.getSystemState() != null) {
			systemStateHandler.dispose(node.getSystemState());
		}
		node.setSystemState(null);
		for (ReuseEdge<S, I, O> edge : node.getEdges()) {
			if (edge != null) {
				if (!edge.getTarget().equals(node)) {
					// only for non reflexive edges, there are no circles in a tree
					disposeSystemstates(edge.getTarget());
				}
			}
		}
	}

	/**
	 * Clears the whole tree which means the root will be reinitialized by a new
	 * {@link ReuseNode} and all invariant input symbols as well as all failure
	 * output symbols will be removed.
	 * <p>
	 * The {@link SystemStateHandler} (
	 * {@link #setSystemStateHandler(SystemStateHandler)}) will <b>not</b> be
	 * informed about any disposings.
	 */
	public synchronized void clearTree() {
		this.nodeCount = 0;
		this.root = new ReuseNode<>(nodeCount++, alphabetSize);
		this.invariantInputSymbols.clear();
		this.failureOutputSymbols.clear();
	}

	/**
	 * Returns a reuseable {@link NodeResult} with system state 
	 * or {@code null} if none such exists. If
	 * ''oldInvalidated'' was set to {@code true} (in the {@link ReuseOracle})
	 * the system state is already removed from the tree whenever
	 * one was available.
	 * 
	 * @param query
	 *            Not allowed to be {@code null}.
	 * @return
	 */
	public synchronized NodeResult<S,I,O> fetchSystemState(Word<I> query) {
		if (query == null) {
			String msg = "Query is not allowed to be null.";
			throw new IllegalArgumentException(msg);
		}

		int length = 0;

		ReuseNode<S, I, O> sink = getRoot();
		ReuseNode<S, I, O> lastState = null;
		if (sink.getSystemState() != null) {
			lastState = sink;
		}

		ReuseNode<S, I, O> node;
		for (int i = 0; i < query.size(); i++) {
			node = sink.getTargetNodeForInput(alphabet.getSymbolIndex(query.getSymbol(i)));

			if (node == null) {
				// we have reached longest known prefix
				break;
			}

			sink = node;
			if (sink.getSystemState() != null) {
				lastState = sink;
				length = i + 1;
			}
		}

		if (lastState == null) {
			return null;
		} else {
			S systemState = lastState.getSystemState();
			if (invalidateSystemstates) {
				lastState.setSystemState(null);
			}
			return new NodeResult<>(lastState, systemState, length);
		}
	}

	/**
	 * Inserts the given {@link Word} with {@link QueryResult} into the tree
	 * starting from the root node of the tree. For the longest known prefix of
	 * the given {@link Word} there will be no new nodes or edges created.
	 * <p>
	 * Will be called from the {@link ReuseOracle} if no system state was
	 * available for reusage for the query (otherwise
	 * {@link #insert(Word, ReuseNode, QueryResult)} would be called). The last
	 * node reached by the last symbol of the query will hold the system state
	 * from the given {@link QueryResult}.
	 * <p>
	 * This method should only be invoked internally from the
	 * {@link ReuseOracle} unless you know exactly what you are doing (you may
	 * want to create a predefined reuse tree before start learning).
	 * 
	 * @param query
	 * @param queryResult
	 * 
	 * @throws ReuseException if non deterministic behavior is detected
	 */
	public synchronized void insert(Word<I> query, QueryResult<S, O> queryResult) {
		insert(query, getRoot(), queryResult);
	}

	/**
	 * Inserts the given {@link Word} (suffix of a membership query) with
	 * {@link QueryResult} (suffix output) into the tree starting from the
	 * {@link ReuseNode} (contains prefix with prefix output) in the tree. For
	 * the longest known prefix of the suffix from the given {@link Word} there
	 * will be no new nodes or edges created.
	 * <p>
	 * Will be called from the {@link ReuseOracle} if an available system state
	 * was reused for the query (otherwise {@link #insert(Word, QueryResult)}
	 * would be called). The old system state was already removed from the
	 * {@link ReuseNode} (through {@link #fetchSystemState(Word)}) 
	 * if the ''invalidateSystemstates'' flag in the {@link ReuseOracle}
	 * was set to {@code true}.
	 * <p>
	 * This method should only be invoked internally from the
	 * {@link ReuseOracle} unless you know exactly what you are doing (you may
	 * want to create a predefined reuse tree before start learning).
	 * 
	 * @param query
	 * @param sink
	 * @param queryResult
	 * 
	 * @throws ReuseException if non deterministic behavior is detected
	 */
	public synchronized void insert(Word<I> query, ReuseNode<S, I, O> sink,
			QueryResult<S, O> queryResult) {
		if (queryResult == null) {
			String msg = "The queryResult is not allowed to be null.";
			throw new IllegalArgumentException(msg);
		}
		if (sink == null) {
			String msg = "Node is not allowed to be null, called wrong method?";
			throw new IllegalArgumentException(msg);
		}
		if (query.size() != queryResult.output.size()) {
			String msg = "Size mismatch: " + query + "/" + queryResult.output;
			throw new IllegalArgumentException(msg);
		}

		for (int i = 0; i < query.size(); i++) {
			I in = query.getSymbol(i);
			O out = queryResult.output.getSymbol(i);
			ReuseNode<S, I, O> rn;

			ReuseEdge<S, I, O> edge = sink.getEdgeWithInput(alphabet.getSymbolIndex(in));
			if (edge != null) {
				if (Objects.equals(edge.getOutput(), out)) {
					sink = edge.getTarget();
					continue;
				}

				throw new ReuseException(
						"Conflict: input '" + query + "', output '" + queryResult.output + "', i=" + i +
								", cached output '" + edge.getOutput() + "'");
			}

			if (failureOutputSymbols != null && failureOutputSymbols.contains(out)) {
				rn = sink;
			} else if (invariantInputSymbols != null && invariantInputSymbols.contains(in)) {
				rn = sink;
			} else {
				rn = new ReuseNode<>(nodeCount++, alphabetSize);
			}

			int index = alphabet.getSymbolIndex(in);
			sink.addEdge(index, new ReuseEdge<>(sink, rn, in, out));
			sink = rn;
		}
		sink.setSystemState(queryResult.newState);
	}
	
	/*
	 * (non-Javadoc)
	 * @see net.automatalib.graphs.Graph#getNodes()
	 */
	@Override
	public Collection<ReuseNode<S, I, O>> getNodes() {
		Collection<ReuseNode<S, I, O>> collection = new ArrayList<>();
		appendNodesRecursively(collection, getRoot());
		return collection;
	}
	
	private void appendNodesRecursively(Collection<ReuseNode<S, I, O>> nodes, ReuseNode<S, I, O> current) {
		nodes.add(current);
		for (int i=0; i<alphabetSize; i++) {
			ReuseEdge<S, I, O> reuseEdge = current.getEdgeWithInput(i);
			if (reuseEdge == null) {
				continue;
			}
			if (!current.equals(reuseEdge.getTarget())) {
				appendNodesRecursively(nodes, reuseEdge.getTarget());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see net.automatalib.graphs.IndefiniteGraph#getOutgoingEdges(java.lang.Object)
	 */
	@Override
	public synchronized Collection<ReuseEdge<S, I, O>> getOutgoingEdges(ReuseNode<S, I, O> node) {
		return node.getEdges();
	}

	/*
	 * (non-Javadoc)
	 * @see net.automatalib.graphs.IndefiniteGraph#getTarget(java.lang.Object)
	 */
	@Override
	public synchronized ReuseNode<S, I, O> getTarget(ReuseEdge<S, I, O> edge) {
		if (edge != null)
			return edge.getTarget();
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see net.automatalib.graphs.dot.DOTPlottableGraph#getGraphDOTHelper()
	 */
	@Override
	public synchronized GraphDOTHelper<ReuseNode<S, I, O>, ReuseEdge<S, I, O>> getGraphDOTHelper() {
		return new ReuseTreeDotHelper<>();
	}
}