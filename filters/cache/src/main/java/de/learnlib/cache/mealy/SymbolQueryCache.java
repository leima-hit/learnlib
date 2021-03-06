/* Copyright (C) 2017 TU Dortmund
 * This file is part of LearnLib, http://www.learnlib.de/.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.learnlib.cache.mealy;

import de.learnlib.api.SymbolQueryOracle;
import net.automatalib.automata.transout.impl.FastMealy;
import net.automatalib.automata.transout.impl.FastMealyState;
import net.automatalib.words.Alphabet;

import java.util.ArrayList;
import java.util.List;

/**
 * A cache for a {@link SymbolQueryOracle}. Upon construction, it is provided with a delegate oracle. Queries that can
 * be answered from the cache are answered directly, others are forwarded to the delegate oracle. Queried symbols that
 * have to be delegated are incorporated into the cache directly.
 *
 * Internally, an incrementally growing tree (in form of a mealy automaton) is used for caching.
 *
 * @param <I> input alphabet type
 * @param <O> output alphabet type
 * @author frohme
 */
// TODO: integrate in existing LearningCache hierarchy
public class SymbolQueryCache<I, O> implements SymbolQueryOracle<I, O> {

	private final FastMealy<I, O> cache;
	private final SymbolQueryOracle<I, O> delegate;

	private final List<I> currentTrace;
	private FastMealyState<O> currentState;
	private boolean currentTraceValid;

	public SymbolQueryCache(final SymbolQueryOracle<I, O> delegate, final Alphabet<I> alphabet) {
		this.delegate = delegate;
		this.cache = new FastMealy<>(alphabet);
		this.cache.addInitialState();
		this.currentState = this.cache.getInitialState();
		
		this.currentTrace = new ArrayList<>();
		this.currentTraceValid = false;
	}

	@Override
	public O query(I i) {

		if (this.currentTraceValid) {
			final FastMealyState<O> succ = this.cache.getSuccessor(this.currentState, i);

			if (succ != null) {
				final O output = this.cache.getOutput(this.currentState, i);
				this.currentTrace.add(i);
				this.currentState = succ;
				return output;
			}
			else {
				this.currentTraceValid = false;
				this.delegate.reset();

				this.currentTrace.forEach(this.delegate::query);
			}
		}

		final O output = this.delegate.query(i);

		final FastMealyState<O> nextState;
		final FastMealyState<O> succ = this.cache.getSuccessor(this.currentState, i);

		if (succ == null) {
			final FastMealyState<O> newState = this.cache.addState();
			this.cache.addTransition(this.currentState, i, newState, output);
			nextState = newState;
		}
		else {
			assert this.cache.getOutput(this.currentState, i).equals(output);
			nextState = succ;
		}

		this.currentState = nextState;

		return output;
	}

	@Override
	public void reset() {
		this.currentState = this.cache.getInitialState();
		this.currentTrace.clear();
		this.currentTraceValid = true;
	}
}
