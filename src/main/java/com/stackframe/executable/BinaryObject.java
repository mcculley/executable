package com.stackframe.executable;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.Collection;

/**
 *
 * @author mcculley
 */
public interface BinaryObject {

    Collection<Segment> segments();

    void disassemble(PrintWriter writer);
    
    Collection<String> symbols();
    
    ByteBuffer getSymbol(String symbol);

}
