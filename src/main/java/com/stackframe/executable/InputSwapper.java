package com.stackframe.executable;

import java.io.DataInput;
import java.io.IOException;

/**
 *
 * @author mcculley
 */
public class InputSwapper implements DataInput {

    private final DataInput delegate;

    public InputSwapper(DataInput delegate) {
        this.delegate = delegate;
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        delegate.readFully(b);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        delegate.readFully(b, off, len);
    }

    @Override
    public int skipBytes(int n) throws IOException {
        return delegate.skipBytes(n);
    }

    @Override
    public boolean readBoolean() throws IOException {
        return delegate.readBoolean();
    }

    @Override
    public byte readByte() throws IOException {
        return delegate.readByte();
    }

    @Override
    public int readUnsignedByte() throws IOException {
        return delegate.readUnsignedByte();
    }

    @Override
    public short readShort() throws IOException {
        return Short.reverseBytes(delegate.readShort());
    }

    @Override
    public int readUnsignedShort() throws IOException {
        return Short.reverseBytes((short)delegate.readUnsignedShort());
    }

    @Override
    public char readChar() throws IOException {
        return delegate.readChar();
    }

    @Override
    public int readInt() throws IOException {
        return Integer.reverseBytes(delegate.readInt());
    }

    @Override
    public long readLong() throws IOException {
        return Long.reverseBytes(delegate.readLong());
    }

    @Override
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(Integer.reverseBytes(delegate.readInt()));
    }

    @Override
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(Long.reverseBytes(delegate.readLong()));
    }

    @Override
    public String readLine() throws IOException {
        return delegate.readLine();
    }

    @Override
    public String readUTF() throws IOException {
        return delegate.readUTF();
    }

}
