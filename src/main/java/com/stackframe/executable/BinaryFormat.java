package com.stackframe.executable;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.RandomAccessFile;

/**
 *
 * @author mcculley
 */
public interface BinaryFormat {

    boolean supported(RandomAccessFile file) throws IOException;

    BinaryObject load(RandomAccessFile file) throws IOException, InvalidObjectException;

}
