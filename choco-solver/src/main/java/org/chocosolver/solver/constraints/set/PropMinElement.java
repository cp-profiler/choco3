/**
 * Copyright (c) 2014,
 *       Charles Prud'homme (TASC, INRIA Rennes, LINA CNRS UMR 6241),
 *       Jean-Guillaume Fages (COSLING S.A.S.).
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/**
 * Created by IntelliJ IDEA.
 * User: Jean-Guillaume Fages
 * Date: 14/01/13
 * Time: 16:36
 */

package org.chocosolver.solver.constraints.set;

import gnu.trove.map.hash.THashMap;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.PropagatorPriority;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.SetVar;
import org.chocosolver.solver.variables.Variable;
import org.chocosolver.solver.variables.events.IntEventType;
import org.chocosolver.solver.variables.events.SetEventType;
import org.chocosolver.util.ESat;

/**
 * Retrieves the minimum element of the set
 * the set must not be empty
 *
 * @author Jean-Guillaume Fages
 */
public class PropMinElement extends Propagator<Variable> {

    //***********************************************************************************
    // VARIABLES
    //***********************************************************************************

    private IntVar min;
    private SetVar set;
    private int offSet;
    private int[] weights;
    private final boolean notEmpty;

    //***********************************************************************************
    // CONSTRUCTORS
    //***********************************************************************************

    /**
     * Retrieves the minimum element of the set
     * MIN(i | i in setVar) = min
     *
     * @param setVar set variable
     * @param min integer variable
     * @param notEmpty true : the set variable cannot be empty
     *                 false : the set may be empty (if so, the MIN constraint is not applied)
     */
    public PropMinElement(SetVar setVar, IntVar min, boolean notEmpty) {
        this(setVar, null, 0, min, notEmpty);
    }

    /**
     * Retrieves the minimum element induced by setVar
     * MIN{weights[i-offSet] | i in setVar} = min
     *
     * @param setVar set variable
     * @param weights array of int
     * @param offSet int
     * @param min integer variable
     * @param notEmpty true : the set variable cannot be empty
     *                 false : the set may be empty (if so, the MIN constraint is not applied)
     */
    public PropMinElement(SetVar setVar, int[] weights, int offSet, IntVar min, boolean notEmpty) {
        super(new Variable[]{setVar, min}, PropagatorPriority.BINARY, false);
        this.min = (IntVar) vars[1];
        this.set = (SetVar) vars[0];
        this.weights = weights;
        this.offSet = offSet;
        this.notEmpty = notEmpty;
    }

    //***********************************************************************************
    // METHODS
    //***********************************************************************************

    @Override
    public int getPropagationConditions(int vIdx) {
        if (vIdx == 0) return SetEventType.all();
        else return IntEventType.boundAndInst();
    }

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        for (int j = set.getKernelFirst(); j != SetVar.END; j = set.getKernelNext()) {
            min.updateUpperBound(get(j), aCause);
        }
        int minVal = Integer.MAX_VALUE;
        int lb = min.getLB();
        for (int j = set.getEnvelopeFirst(); j != SetVar.END; j = set.getEnvelopeNext()) {
            int k = get(j);
            if (k < lb) {
                set.removeFromEnvelope(j, aCause);
            } else {
                if (minVal > k) {
                    minVal = k;
                }
            }
        }
        if (notEmpty || set.getKernelSize() > 0) {
            min.updateLowerBound(minVal, aCause);
        }
    }

    @Override
    public ESat isEntailed() {
        if (set.getEnvelopeSize() == 0) {
            if (notEmpty) {
                return ESat.FALSE;
            } else {
                return ESat.TRUE;
            }
        }
        int lb = min.getLB();
        int ub = min.getUB();
        for (int j = set.getKernelFirst(); j != SetVar.END; j = set.getKernelNext()) {
            if (get(j) < lb) {
                return ESat.FALSE;
            }
        }
        int minVal = Integer.MAX_VALUE;
        for (int j = set.getEnvelopeFirst(); j != SetVar.END; j = set.getEnvelopeNext()) {
            if (minVal > get(j)) {
                minVal = get(j);
            }
        }
        if (minVal > ub && (notEmpty || set.getKernelSize() > 0)) {
            return ESat.FALSE;
        }
        if (isCompletelyInstantiated()) {
            return ESat.TRUE;
        }
        return ESat.UNDEFINED;
    }

    private int get(int j) {
        return (weights == null) ? j : weights[j - offSet];
    }

    @Override
    public void duplicate(Solver solver, THashMap<Object, Object> identitymap) {
        if (!identitymap.containsKey(this)) {
            set.duplicate(solver, identitymap);
            SetVar S = (SetVar) identitymap.get(set);

            min.duplicate(solver, identitymap);
            IntVar M = (IntVar) identitymap.get(min);

            identitymap.put(this, new PropMinElement(S, M, notEmpty));
        }
    }
}
