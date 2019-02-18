/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.driver.ser.binary.types;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.apache.tinkerpop.gremlin.driver.ser.SerializationException;
import org.apache.tinkerpop.gremlin.driver.ser.binary.DataType;
import org.apache.tinkerpop.gremlin.driver.ser.binary.GraphBinaryReader;
import org.apache.tinkerpop.gremlin.driver.ser.binary.GraphBinaryWriter;
import org.apache.tinkerpop.gremlin.process.traversal.Bytecode;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class ByteCodeSerializer extends SimpleTypeSerializer<Bytecode> {
    private static final Object[] EMPTY_ARGS = new Object[0];

    public ByteCodeSerializer() {
        super(DataType.BYTECODE);
    }

    @Override
    protected Bytecode readValue(final ByteBuf buffer, final GraphBinaryReader context) throws SerializationException {
        final Bytecode result = new Bytecode();

        final int stepsLength = buffer.readInt();
        for (int i = 0; i < stepsLength; i++) {
            result.addStep(readString(buffer), getInstructionArguments(buffer, context));
        }

        final int sourcesLength = buffer.readInt();
        for (int i = 0; i < sourcesLength; i++) {
            result.addSource(readString(buffer), getInstructionArguments(buffer, context));
        }

        return result;
    }

    private static String readString(final ByteBuf buffer) {
        final byte[] bytes = new byte[buffer.readInt()];
        buffer.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static Object[] getInstructionArguments(final ByteBuf buffer, final GraphBinaryReader context) throws SerializationException {
        final int valuesLength = buffer.readInt();

        if (valuesLength == 0) {
            return EMPTY_ARGS;
        }

        final Object[] values = new Object[valuesLength];
        for (int j = 0; j < valuesLength; j++) {
            values[j] = context.read(buffer);
        }
        return values;
    }

    @Override
    protected ByteBuf writeValue(final Bytecode value, final ByteBufAllocator allocator, final GraphBinaryWriter context) throws SerializationException {
        final List<Bytecode.Instruction> steps = value.getStepInstructions();
        final List<Bytecode.Instruction> sources = value.getSourceInstructions();
        // 2 buffers for the length + plus 2 buffers per each step and source
        final BufferBuilder result = buildBuffer(2 + steps.size() * 2 + sources.size() * 2);

        try {
            writeInstructions(allocator, context, steps, result);
            writeInstructions(allocator, context, sources, result);
        } catch (Exception ex) {
            // We should release it as the ByteBuf is not going to be yielded for a reader
            result.release();
            throw ex;
        }

        return result.create();
    }

    private void writeInstructions(final ByteBufAllocator allocator, final GraphBinaryWriter context,
                                   final List<Bytecode.Instruction> instructions, final BufferBuilder result) throws SerializationException {

        result.add(context.writeValue(instructions.size(), allocator, false));

        for (Bytecode.Instruction instruction : instructions) {
            result.add(context.writeValue(instruction.getOperator(), allocator, false));
            result.add(getArgumentsBuffer(instruction.getArguments(), allocator, context));
        }
    }

    private ByteBuf getArgumentsBuffer(final Object[] arguments, final ByteBufAllocator allocator, final GraphBinaryWriter context) throws SerializationException {

        if (arguments.length == 0) {
            return context.writeValue(0, allocator, false);
        }

        final BufferBuilder builder = buildBuffer(1 + arguments.length);
        builder.add(context.writeValue(arguments.length, allocator, false));

        try {
            for (Object value : arguments) {
                builder.add(context.write(value, allocator));
            }
        } catch (Exception ex) {
            builder.release();
            throw ex;
        }

        return builder.create();
    }
}
