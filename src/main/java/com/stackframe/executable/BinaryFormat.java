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

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.RandomAccessFile;

/**
 *
 */
public interface BinaryFormat {

    boolean supported(RandomAccessFile file) throws IOException;

    BinaryObject load(RandomAccessFile file) throws IOException, InvalidObjectException;

}
