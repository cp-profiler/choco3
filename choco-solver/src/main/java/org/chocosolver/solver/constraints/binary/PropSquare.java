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
package org.chocosolver.solver.constraints.binary;

import gnu.trove.map.hash.THashMap;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.constraints.Propagator;
import org.chocosolver.solver.constraints.PropagatorPriority;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.explanations.RuleStore;
import org.chocosolver.solver.variables.IntVar;
import org.chocosolver.solver.variables.delta.IIntDeltaMonitor;
import org.chocosolver.solver.variables.events.IEventType;
import org.chocosolver.solver.variables.events.IntEventType;
import org.chocosolver.util.ESat;
import org.chocosolver.util.procedure.UnaryIntProcedure;
import org.chocosolver.util.tools.ArrayUtils;

/**
 * Enforces X = Y^2
 * <br/>
 *
 * @author Charles Prud'homme
 * @since 18/05/11
 */
public class PropSquare extends Propagator<IntVar> {

    protected final RemProc rem_proc;
    protected final IIntDeltaMonitor[] idms;

    public PropSquare(IntVar X, IntVar Y) {
        super(ArrayUtils.toArray(X, Y), PropagatorPriority.BINARY, true);
        this.idms = new IIntDeltaMonitor[vars.length];
        for (int i = 0; i < vars.length; i++) {
            idms[i] = vars[i].hasEnumeratedDomain() ? vars[i].monitorDelta(this) : IIntDeltaMonitor.Default.NONE;
        }
        rem_proc = new RemProc(this);
    }

    @Override
    public void propagate(int evtmask) throws ContradictionException {
        // Filter on X from Y
        updateLowerBoundofX();
        updateUpperBoundofX();
        updateHolesinX();

        // Filter on Y from X
        updateLowerBoundofY();
        updateUpperBoundofY();
        updateHolesinY();
        for (int i = 0; i < idms.length; i++) {
            idms[i].unfreeze();
        }
    }

    @Override
    public void propagate(int varIdx, int mask) throws ContradictionException {
        if (varIdx == 0) { // filter from X to Y
            if (IntEventType.isInstantiate(mask) || IntEventType.isBound(mask)) {
                updateLowerBoundofY();
                updateUpperBoundofY();
                updateHolesinY();
            } else {
                idms[varIdx].freeze();
                idms[varIdx].forEachRemVal(rem_proc.set(varIdx));
                idms[varIdx].unfreeze();
//                updateHolesinY();
            }
        } else { // filter from Y to X
            // <nj> originally we had the following condition
//            if (EventType.isRemove(mask) && EventType.isRemove(getPropagationConditions(idxVarInProp))) {
            // this led to a nasty bug due to event promotion

            if (IntEventType.isInstantiate(mask) || IntEventType.isBound(mask)) {
                updateLowerBoundofX();
                updateUpperBoundofX();
                updateHolesinX();
            } else {
                idms[varIdx].freeze();
                idms[varIdx].forEachRemVal(rem_proc.set(varIdx));
                idms[varIdx].unfreeze();
//                updateHolesinX();
            }
        }
    }

    @Override
    public ESat isEntailed() {
        if (vars[0].getUB() < 0) {
            return ESat.FALSE;
        } else if (vars[0].isInstantiated()) {
            if (vars[1].isInstantiated()) {
                return ESat.eval(vars[0].getValue() == sqr(vars[1].getValue()));
            } else if (vars[1].getDomainSize() == 2 &&
                    vars[1].contains(-floor_sqrt(vars[0].getValue())) &&
                    vars[1].contains(-floor_sqrt(vars[0].getValue()))) {
                return ESat.TRUE;
            } else if (!vars[1].contains(floor_sqrt(vars[0].getValue())) &&
                    !vars[1].contains(-floor_sqrt(vars[0].getValue()))) {
                return ESat.FALSE;
            } else {
                return ESat.UNDEFINED;
            }
        } else {
            return ESat.UNDEFINED;
        }
    }

    @Override
    public String toString() {
        return String.format("%s = %s^2", vars[0].toString(), vars[1].toString());
    }

    private static int floor_sqrt(int n) {
        if (n < 0)
            return 0;
        return (int) Math.floor(Math.sqrt(n));
    }

    private static int ceil_sqrt(int n) {
        if (n < 0)
            return 0;
        return (int) Math.ceil(Math.sqrt(n));
    }

    private static int sqr(int n) {
        if (n > Integer.MAX_VALUE / 2 || n < Integer.MIN_VALUE / 2) {
            return Integer.MAX_VALUE;
        }
        return n * n;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected void updateLowerBoundofX() throws ContradictionException {
        int a0 = vars[1].nextValue(-1);
        int b0 = Math.max(Integer.MIN_VALUE + 1, vars[1].previousValue(1));
        vars[0].updateLowerBound(Math.min(sqr(a0), sqr(b0)), aCause);
    }

    protected void updateUpperBoundofX() throws ContradictionException {
        vars[0].updateUpperBound(Math.max(sqr(vars[1].getLB()), sqr(vars[1].getUB())), aCause);

    }

    protected void updateHolesinX() throws ContradictionException {
        // remove intervals to deal with consecutive value removal and upper bound modification
        if (vars[1].hasEnumeratedDomain()) {
            int left = Integer.MIN_VALUE, right = Integer.MIN_VALUE;
            int ub = vars[0].getUB();
            for (int value = vars[0].getLB(); value <= ub; value = vars[0].nextValue(value)) {
                if (!vars[1].contains(floor_sqrt(value)) && !vars[1].contains(-floor_sqrt(value))) {
                    if (value == right + 1) {
                        right = value;
                    } else {
                        vars[0].removeInterval(left, right, aCause);
                        left = right = value;
                    }
                }
            }
            vars[0].removeInterval(left, right, aCause);
        } else {
            int value = vars[0].getLB();
            int nlb = value - 1;
            while (nlb == value - 1) {
                if (!vars[1].contains(floor_sqrt(value)) && !vars[1].contains(-floor_sqrt(value))) {
                    nlb = value;
                }
                value = vars[0].nextValue(value);
            }
            vars[0].updateLowerBound(nlb, aCause);

            value = vars[0].getUB();
            int nub = value + 1;
            while (nub == value + 1) {
                if (!vars[1].contains(floor_sqrt(value)) && !vars[1].contains(-floor_sqrt(value))) {
                    nub = value;
                }
                value = vars[0].previousValue(value);
            }
            vars[0].updateUpperBound(nub, aCause);
        }
    }

    protected void updateHoleinX(int remVal) throws ContradictionException {
        if (!vars[1].contains(-remVal)) {
            vars[0].removeValue(sqr(remVal), aCause);
        }
    }

    protected void updateLowerBoundofY() throws ContradictionException {
        vars[1].updateLowerBound(-ceil_sqrt(vars[0].getUB()), aCause);
    }

    protected void updateUpperBoundofY() throws ContradictionException {
        vars[1].updateUpperBound(floor_sqrt(vars[0].getUB()), aCause);
    }

    protected void updateHolesinY() throws ContradictionException {
        // remove intervals to deal with consecutive value removal and upper bound modification
        if (vars[0].hasEnumeratedDomain()) {
            int left = Integer.MIN_VALUE, right = Integer.MIN_VALUE;
            int ub = vars[1].getUB();
            for (int value = vars[1].getLB(); value <= ub; value = vars[1].nextValue(value)) {
                if (!vars[0].contains(sqr(value))) {
                    if (value == right + 1) {
                        right = value;
                    } else {
                        vars[1].removeInterval(left, right, aCause);
                        left = right = value;
                    }
                }
            }
            vars[1].removeInterval(left, right, aCause);
        } else {
            int lb = vars[1].getLB();
            int ub = vars[1].getUB();
            while (!vars[0].contains(sqr(lb))) {
                lb = vars[1].nextValue(lb + 1);
                if (lb > ub) break;
            }
            vars[1].updateLowerBound(lb, aCause);

            while (!vars[0].contains(sqr(ub))) {
                ub = vars[1].nextValue(ub + 1);
                if (ub < lb) break;
            }
            vars[1].updateUpperBound(ub, aCause);
        }
    }

    protected void updateHoleinY(int remVal) throws ContradictionException {
        vars[1].removeValue(floor_sqrt(remVal), aCause);
        vars[1].removeValue(-ceil_sqrt(remVal), aCause);
    }

    @Override
    public boolean why(RuleStore ruleStore, IntVar var, IEventType evt, int value) {
        boolean newrules = ruleStore.addPropagatorActivationRule(this);
        if (var.equals(vars[0])) {
            int sqrt = (int) Math.sqrt(value);
            newrules |= ruleStore.addRemovalRule(vars[1], sqrt);
            newrules |= ruleStore.addRemovalRule(vars[1], -sqrt);
        } else if (var.equals(vars[1])) {
            int sqr = value ^ 2;
            newrules |= ruleStore.addRemovalRule(vars[0], sqr);
        } else {
            newrules |= super.why(ruleStore, var, evt, value);
        }
        return newrules;
    }

    @Override
    public void duplicate(Solver solver, THashMap<Object, Object> identitymap) {
        if (!identitymap.containsKey(this)) {
            this.vars[0].duplicate(solver, identitymap);
            IntVar X = (IntVar) identitymap.get(this.vars[0]);
            this.vars[1].duplicate(solver, identitymap);
            IntVar Y = (IntVar) identitymap.get(this.vars[1]);

            identitymap.put(this, new PropSquare(X, Y));
        }
    }

    private static class RemProc implements UnaryIntProcedure<Integer> {

        private final PropSquare p;
        private int idxVar;

        public RemProc(PropSquare p) {
            this.p = p;
        }

        @Override
        public UnaryIntProcedure set(Integer idxVar) {
            this.idxVar = idxVar;
            return this;
        }

        @Override
        public void execute(int i) throws ContradictionException {
            if (idxVar == 0) {
                p.updateHoleinY(i);
            } else {
                p.updateHoleinX(i);
            }
        }
    }

}
