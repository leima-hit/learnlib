/* Copyright (C) 2013 TU Dortmund
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

package de.learnlib.logging.filter;

import java.util.EnumSet;

import de.learnlib.logging.Category;

/**
 * only some categories. SYSTEM, CONFIG, MODEL, STATISTIC, PROFILING.
 * 
 * @author falkhowar
 */
public class ExperimentalDataFilter extends CategoryFilter {

    public ExperimentalDataFilter() {
        super(EnumSet.of(Category.SYSTEM, Category.CONFIG, 
                Category.MODEL, Category.STATISTIC, Category.PROFILING));
    }
        
}
