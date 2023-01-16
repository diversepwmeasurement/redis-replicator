/*
 * Copyright 2016-2017 Leon Chen
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

package com.moilioncircle.redis.replicator;

import static com.moilioncircle.redis.replicator.Constants.COLON;
import static com.moilioncircle.redis.replicator.Constants.DOLLAR;
import static com.moilioncircle.redis.replicator.Constants.MINUS;
import static com.moilioncircle.redis.replicator.Constants.PLUS;
import static com.moilioncircle.redis.replicator.Constants.STAR;

import java.io.Closeable;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.moilioncircle.redis.replicator.io.RedisInputStream;
import com.moilioncircle.redis.replicator.io.RedisOutputStream;
import com.moilioncircle.redis.replicator.util.ByteBuilder;

/**
 * @author Leon Chen
 * @since 3.7.0
 */
public class RESP2 {
    
    private RedisInputStream in;
    private RedisOutputStream out;
    
    public RESP2(RedisInputStream in, RedisOutputStream out) {
        this.in = in;
        this.out = out;
    }
    
    public void emit(byte[]... command) throws IOException {
        out.write(STAR);
        out.write(String.valueOf(command.length).getBytes());
        out.writeCrLf();
        for (final byte[] element : command) {
            out.write(DOLLAR);
            out.write(String.valueOf(element.length).getBytes());
            out.writeCrLf();
            out.write(element);
            out.writeCrLf();
        }
        out.flush();
    }
    
    public void emit(String... command) throws IOException {
        out.write(STAR);
        out.write(String.valueOf(command.length).getBytes());
        out.writeCrLf();
        for (final String element : command) {
            out.write(DOLLAR);
            byte[] bytes = element.getBytes();
            out.write(String.valueOf(bytes.length).getBytes());
            out.writeCrLf();
            out.write(bytes);
            out.writeCrLf();
        }
        out.flush();
    }
    
    public Node parse() throws IOException {
        return parse(null);
    }
    
    public Node parse(BulkConsumer consumer) throws IOException {
        while (true) {
            int c = in.read();
            switch (c) {
                case DOLLAR:
                    // RESP Bulk Strings
                    ByteBuilder builder = ByteBuilder.allocate(32);
                    while (true) {
                        while ((c = in.read()) != '\r') {
                            builder.put((byte) c);
                        }
                        if ((c = in.read()) == '\n') {
                            break;
                        } else {
                            builder.put((byte) c);
                        }
                    }
                    long len = Long.parseLong(builder.toString());
                    if (len == -1) return new Node(RESP2.Type.NULL, null);
                    Node r = null;
                    if (consumer != null) {
                        consumer.accept(len, in);
                    } else {
                        r = new Node(RESP2.Type.STRING, in.readBytes(len).first());
                    }
                    
                    if ((c = in.read()) != '\r') throw new RuntimeException("expect '\\r' but :" + (char) c);
                    if ((c = in.read()) != '\n') throw new RuntimeException("expect '\\n' but :" + (char) c);
                    return r;
                case COLON:
                    // RESP Integers
                    builder = ByteBuilder.allocate(128);
                    while (true) {
                        while ((c = in.read()) != '\r') {
                            builder.put((byte) c);
                        }
                        if ((c = in.read()) == '\n') {
                            break;
                        } else {
                            builder.put((byte) c);
                        }
                    }
                    // As integer
                    return new Node(RESP2.Type.NUMBER, Long.parseLong(builder.toString()));
                case STAR:
                    // RESP Arrays
                    builder = ByteBuilder.allocate(128);
                    while (true) {
                        while ((c = in.read()) != '\r') {
                            builder.put((byte) c);
                        }
                        if ((c = in.read()) == '\n') {
                            break;
                        } else {
                            builder.put((byte) c);
                        }
                    }
                    len = Long.parseLong(builder.toString());
                    if (len == -1) return new Node(RESP2.Type.NULL, null);
                    Node[] ary = new Node[(int) len];
                    for (int i = 0; i < len; i++) {
                        Node obj = parse(null);
                        ary[i] = obj;
                    }
                    return new Node(RESP2.Type.ARRAY, ary);
                case PLUS:
                    // RESP Simple Strings
                    builder = ByteBuilder.allocate(128);
                    while (true) {
                        while ((c = in.read()) != '\r') {
                            builder.put((byte) c);
                        }
                        if ((c = in.read()) == '\n') {
                            return new Node(RESP2.Type.STRING, builder.array());
                        } else {
                            builder.put((byte) c);
                        }
                    }
                case MINUS:
                    // RESP Errors
                    builder = ByteBuilder.allocate(32);
                    while (true) {
                        while ((c = in.read()) != '\r') {
                            builder.put((byte) c);
                        }
                        if ((c = in.read()) == '\n') {
                            return new Node(RESP2.Type.ERROR, builder.array());
                        } else {
                            builder.put((byte) c);
                        }
                    }
                default:
                    throw new ProtocolException("expect [$,:,*,+,-] but: " + (char) c);
                
            }
        }
    }
    
    public static class Node {
        public RESP2.Type type;
        public Object value;
        
        public Node(RESP2.Type type, Object value) {
            this.type = type;
            this.value = value;
        }
    }
    
    public static enum Type {
        ARRAY, NUMBER, STRING, ERROR, NULL;
    }
    
    public static class Client implements Closeable {
        
        private RESP2 resp2;
        private Configuration configuration;
        
        public Client(RedisURI uri, Configuration configuration) {
            this.configuration = configuration;
        }
        
        public RESP2.Response newCommand() {
            return new RESP2.Response(resp2, configuration);
        }
        
        @Override
        public void close() throws IOException {
            
        }
    }
    
    public static class Response {
        private RESP2 resp2;
        private Queue<Handler> responses;
        private Configuration configuration;
        
        public Response(RESP2 resp2, Configuration configuration) {
            this.resp2 = resp2;
            this.configuration = configuration;
            this.responses = new LinkedList<>();
        }
        
        public RESP2.Response send(NodeConsumer handler, byte[]... command) throws IOException {
            this.resp2.emit(command);
            this.responses.offer(handler);
            return this;
        }
        
        public RESP2.Response send(BulkConsumer handler, byte[]... command) throws IOException {
            this.resp2.emit(command);
            this.responses.offer(handler);
            return this;
        }
    
        public RESP2.Response send(NodeConsumer handler, String... command) throws IOException {
            this.resp2.emit(command);
            this.responses.offer(handler);
            return this;
        }
    
        public RESP2.Response send(BulkConsumer handler, String... command) throws IOException {
            this.resp2.emit(command);
            this.responses.offer(handler);
            return this;
        }
        
        public void get() throws IOException {
            while (!responses.isEmpty()) {
                Handler handler = responses.poll();
                if (handler instanceof NodeConsumer) {
                    ((NodeConsumer) handler).accept(resp2.parse());
                } else if (handler instanceof BulkConsumer) {
                    resp2.parse((BulkConsumer)handler);
                } else {
                    throw new AssertionError(handler);
                }
            }
        }
    }
    
    public static interface Handler {
    }
    
    public static interface NodeConsumer extends Consumer<Node>, Handler {
    }
    
    public static interface BulkConsumer extends BiConsumer<Long, RedisInputStream>, Handler {
    }
}

