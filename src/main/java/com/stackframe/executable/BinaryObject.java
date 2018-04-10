/* 
    Copyright (C) 2018 StackFrame Technologies, LLC

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as published by
    the Free Software Foundation.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License along
    with this program; if not, write to the Free Software Foundation, Inc.,
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
*/

package com.stackframe.executable;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.Collection;

/**
 *
 */
public interface BinaryObject {

    Collection<Segment> segments();

    void disassemble(PrintWriter writer);
    
    Collection<String> symbols();
    
    ByteBuffer getSymbol(String symbol);

}
