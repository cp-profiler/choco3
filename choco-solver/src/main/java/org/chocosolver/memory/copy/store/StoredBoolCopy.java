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
package org.chocosolver.memory.copy.store;

import org.chocosolver.memory.copy.RcBool;

/**
 * <br/>
 *
 * @author Charles Prud'homme
 * @since 14/05/13
 */
public class StoredBoolCopy implements IStoredBoolCopy {

    RcBool[] objects;
    boolean[][] values;
    int position;

    public StoredBoolCopy() {
        objects = new RcBool[64];
        values = new boolean[64][];
    }


    public void add(RcBool rc) {
        if (position == objects.length) {
            int newSize = objects.length * 3 / 2 + 1;
            RcBool[] oldElements = objects;
            objects = new RcBool[newSize];
            System.arraycopy(oldElements, 0, objects, 0, oldElements.length);
        }
        objects[position++] = rc;
    }

    public void worldPush(int worldIndex) {
        if (values.length <= worldIndex) {
            boolean[][] tmp = values;
            values = new boolean[tmp.length * 3 / 2 + 1][];
            System.arraycopy(tmp, 0, values, 0, tmp.length);
        }
        boolean[] tmpboolean = new boolean[position];
        for (int i = position; --i >= 0; ) {
            tmpboolean[i] = objects[i].deepCopy();
        }
        values[worldIndex] = tmpboolean;
    }

    public void worldPop(int worldIndex) {
        boolean[] tmpboolean = values[worldIndex];
        for (int i = tmpboolean.length; --i >= 0; )
            objects[i]._set(tmpboolean[i], worldIndex);
    }

    @Override
    public void worldCommit(int worldIndex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void buildFakeHistory(RcBool v, boolean initValue, int olderStamp) {
        for (int i = 1; i <= olderStamp; i++) {
            boolean[] _values = values[i];
            int size = _values.length;
            values[i] = new boolean[position];
            System.arraycopy(_values, 0, values[i], 0, size);
            values[i][size] = initValue;
        }
    }
}
