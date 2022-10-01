/*
 * Copyright 2016-2018 Leon Chen
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

package com.moilioncircle.redis.replicator.rdb.iterable.datatype;

import java.util.Iterator;

import com.moilioncircle.redis.replicator.rdb.datatype.KeyValuePair;
import com.moilioncircle.redis.replicator.rdb.datatype.ZSetEntry;

/**
 * @author Leon Chen
 * @since 2.3.0
 * @see com.moilioncircle.redis.replicator.rdb.iterable.ValueIterableRdbVisitor
 * @see com.moilioncircle.redis.replicator.rdb.iterable.ValueIterableEventListener
 */
public class KeyStringValueZSetEntryIterator extends KeyValuePair<byte[], Iterator<ZSetEntry>> {
    private static final long serialVersionUID = 1L;
}
