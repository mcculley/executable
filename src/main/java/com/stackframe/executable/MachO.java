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

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class MachO implements BinaryFormat {

    private static final int MH_MAGIC = 0xfeedface; // Big endian 32-bit Mach-O magic.
    private static final int MH_CIGAM = 0xcefaedfe; // Little endian 32-bit Mach-O magic.
    private static final int MH_MAGIC_64 = 0xfeedfacf; // Big endian 64-bit Mach-O magic.
    private static final int MH_CIGAM_64 = 0xcffaedfe; // Little endian 64-bit Mach-O magic.

    private static boolean supported(int magic) {
        return magic == MH_MAGIC || magic == MH_CIGAM || magic == MH_MAGIC_64 || magic == MH_CIGAM_64;
    }

    @Override
    public boolean supported(RandomAccessFile file) throws IOException {
        file.seek(0);
        final int magic = file.readInt();
        return supported(magic);
    }

    private static class MachOBinaryObject implements BinaryObject {

        private final Collection<Segment> segments;
        private final ByteBuffer mapping;

        public MachOBinaryObject(RandomAccessFile f) throws IOException, InvalidObjectException {
            Collection<Segment> segments = new ArrayList<>();
            f.seek(0);
            final int magic = f.readInt();
            if (!supported(magic)) {
                throw new InvalidObjectException("unexpected magic value " + magic);
            }

            final boolean littleEndian = magic == MH_CIGAM || magic == MH_CIGAM_64;
            final DataInput i = littleEndian ? new InputSwapper(f) : f;

            final CPUType cpuType = CPUType.types.get(i.readInt());

            final CPUSubType cpuSubType = CPUSubType.types.get(i.readInt());

            final FileType fileType = FileType.types.get(i.readInt());

            final int ncmds = i.readInt();

            final int sizeofcmds = i.readInt();

            final int flags = i.readInt();

            for (int c = 0; c < ncmds; c++) {
                long offset = f.getFilePointer();
                Command s = Command.load(f, i);
                segments.add(s);
                f.seek(offset + s.cmdsize);
            }

            this.segments = Collections.unmodifiableCollection(segments);
            this.mapping = f.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, f.length());
        }

        @Override
        public Collection<Segment> segments() {
            return segments;
        }

        @Override
        public void disassemble(PrintWriter writer) {
            for (Segment s : segments) {
                s.disassemble(writer);
            }
        }

        @Override
        public Collection<String> symbols() {
            Collection<String> symbols = new ArrayList<>();
            for (Segment s : segments) {
                if (s instanceof SymTabCommand) {
                    SymTabCommand symbolTable = (SymTabCommand)s;
                    for (SymTabCommand.SymbolTableEntry e : symbolTable.symbols) {
                        symbols.add(readString(mapping, symbolTable.stroff + e.n_strx));
                    }
                }
            }

            return Collections.unmodifiableCollection(symbols);
        }

        @Override
        public ByteBuffer getSymbol(String symbol) {
            for (Segment s : segments) {
                if (s instanceof SymTabCommand) {
                    SymTabCommand symbolTable = (SymTabCommand)s;
                    for (SymTabCommand.SymbolTableEntry e : symbolTable.symbols) {
                        String name = readString(mapping, symbolTable.stroff + e.n_strx);
                        if (name.equals(symbol)) {
                            mapping.position(e.n_value);
                            ByteBuffer copy = mapping.slice();
                            return copy;
                        }
                    }
                }
            }

            return null;
        }

    }

    private enum CPUType {
        ANY(-1),
        VAX(1),
        MC680x0(6),
        X86(7),
        MIPS(8);

        public final int type;
        public static final Map<Integer, CPUType> types = new HashMap<>();

        static {
            for (CPUType t : CPUType.values()) {
                types.put(t.type, t);
            }
        }

        private CPUType(int type) {
            this.type = type;
        }

    }

    private enum CPUSubType {
        X86(3),
        X86_64_HASWELL(8);

        public final int type;
        public static final Map<Integer, CPUSubType> types = new HashMap<>();

        static {
            for (CPUSubType t : CPUSubType.values()) {
                types.put(t.type, t);
            }
        }

        private CPUSubType(int type) {
            this.type = type;
        }

    }

    private enum FileType {
        OBJECT(0x1), /* relocatable object file */
        EXECUTE(0x2), /* demand paged executable file */
        FVMLIB(0x3), /* fixed VM shared library file */
        CORE(0x4), /* core file */
        PRELOAD(0x5), /* preloaded executable file */
        DYLIB(0x6), /* dynamically bound shared library */
        DYLINKER(0x7), /* dynamic link editor */
        BUNDLE(0x8), /* dynamically bound bundle file */
        DYLIB_STUB(0x9), /* shared library stub for static linking only, no section contents */
        DSYM(0xA), /* companion file with only debug sections */
        KEXT_BUNDLE(0xB) /* x86_64 kexts */;

        public final int type;
        public static final Map<Integer, FileType> types = new HashMap<>();

        static {
            for (FileType t : FileType.values()) {
                types.put(t.type, t);
            }
        }

        private FileType(int type) {
            this.type = type;
        }

    }

    private static class Command implements Segment {

        protected final int cmd;
        protected final int cmdsize;

        private static final int SEGMENT = 0x1;
        private static final int SYMTAB = 0x2;
        private static final int DYSYMTAB = 0xb;
        private static final int LOAD_DYLIB = 0xc;
        private static final int ID_DYLIB = 0xd;
        private static final int SEGMENT_64 = 0x19;
        private static final int UUID = 0x1b;
        private static final int DYLD_INFO = 0x22;
        private static final int VERSION_MIN_MACOSX = 0x24;
        private static final int FUNCTION_STARTS = 0x26;
        private static final int DATA_IN_CODE = 0x29;
        private static final int SOURCE_VERSION = 0x2a;
        private static final int REQ_DYLD = 0x80000000;

        public Command(int cmd, int cmdsize) {
            this.cmd = cmd;
            this.cmdsize = cmdsize;
        }

        @Override
        public void disassemble(PrintWriter writer) {
        }

        private static Command load(RandomAccessFile f, DataInput i) throws IOException, InvalidObjectException {
            long offsetToStart = f.getFilePointer();
            int cmd = i.readInt();
            int cmdsize = i.readInt();
            if (cmd == SEGMENT || cmd == SEGMENT_64) {
                return new SegmentCommand(cmd, cmdsize, i);
            } else if (cmd == ID_DYLIB || cmd == LOAD_DYLIB) {
                return new DylibCommand(cmd, cmdsize, f, offsetToStart, i);
            } else if (cmd == (DYLD_INFO | REQ_DYLD)) {
                return new DYLDInfoCommand(cmd, cmdsize, i);
            } else if (cmd == SYMTAB) {
                return new SymTabCommand(cmd, cmdsize, f, offsetToStart, i);
            } else if (cmd == DYSYMTAB) {
                return new DySymTabCommand(cmd, cmdsize, i);
            } else if (cmd == UUID) {
                return new UUIDCommand(cmd, cmdsize, i);
            } else if (cmd == VERSION_MIN_MACOSX) {
                return new VersionMinCommand(cmd, cmdsize, i);
            } else if (cmd == SOURCE_VERSION) {
                return new SourceVersionCommand(cmd, cmdsize, i);
            } else if (cmd == FUNCTION_STARTS) {
                return new FunctionStartsCommand(cmd, cmdsize, i);
            } else if (cmd == DATA_IN_CODE) {
                return new DataInCodeCommand(cmd, cmdsize, i);
            } else {
                throw new InvalidObjectException("unexpected cmd=" + cmd);
            }
        }
    }

    private static class SegmentCommand extends Command {

        private final String segname;
        private final long vmaddr;
        private final long vmsize;
        private final long fileoff;
        private final long filesize;
        private final int maxprot;
        private final int initprot;
        private final int nsects;
        private final int flags;

        public SegmentCommand(int cmd, int cmdsize, DataInput i) throws IOException, InvalidObjectException {
            super(cmd, cmdsize);
            byte[] segnameBytes = new byte[16];
            i.readFully(segnameBytes);
            segname = new String(segnameBytes);
            if (cmd == Command.SEGMENT_64) {
                vmaddr = i.readLong();
                vmsize = i.readLong();
                fileoff = i.readLong();
                filesize = i.readLong();
            } else {
                vmaddr = i.readInt();
                vmsize = i.readInt();
                fileoff = i.readInt();
                filesize = i.readInt();
            }

            maxprot = i.readInt();
            initprot = i.readInt();
            nsects = i.readInt();
            flags = i.readInt();
        }

        @Override
        public String toString() {
            return "SegmentCommand{" + "cmd=" + cmd + ", cmdsize=" + cmdsize + ", segname=" + segname + ", vmaddr=" + vmaddr +
                   ", vmsize=" + vmsize + ", fileoff=" + fileoff + ", filesize=" + filesize + ", maxprot=" + maxprot + ", initprot=" +
                   initprot + ", nsects=" + nsects + ", flags=" + flags + '}';
        }

    }

    private static String readString(ByteBuffer buffer, int position) {
        buffer.position(position);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            byte b = buffer.get();
            if (b == 0) {
                break;
            } else {
                baos.write(b);
            }
        }

        return new String(baos.toByteArray());
    }

    private static String readString(RandomAccessFile f) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            byte b = f.readByte();
            if (b == 0) {
                break;
            } else {
                baos.write(b);
            }
        }

        return new String(baos.toByteArray());
    }

    private static class DylibCommand extends Command {

        private final String name;
        private final int timestamp;
        private final int current_version;
        private final int compatibility_version;

        public DylibCommand(int cmd, int cmdsize, RandomAccessFile f, long offsetToStart, DataInput i) throws IOException, InvalidObjectException {
            super(cmd, cmdsize);
            int stringOffset = i.readInt();
            long mark = f.getFilePointer();
            f.seek(offsetToStart + stringOffset);
            name = readString(f);
            f.seek(mark);
            timestamp = i.readInt();
            current_version = i.readInt();
            compatibility_version = i.readInt();
        }

        @Override
        public String toString() {
            return "DylibCommand{" + "name=" + name + ", timestamp=" + timestamp + ", current_version=" + current_version +
                   ", compatibility_version=" + compatibility_version + '}';
        }

    }

    private static class DYLDInfoCommand extends Command {

        public DYLDInfoCommand(int cmd, int cmdsize, DataInput i) throws IOException, InvalidObjectException {
            super(cmd, cmdsize);
        }

        @Override
        public String toString() {
            return "DYLDInfoCommand{" + '}';
        }

    }

    private static class LinkEditDataCommand extends Command {

        public LinkEditDataCommand(int cmd, int cmdsize, DataInput i) throws IOException, InvalidObjectException {
            super(cmd, cmdsize);
        }

        @Override
        public String toString() {
            return "LinkEditDataCommand{" + '}';
        }

    }

    private static class FunctionStartsCommand extends LinkEditDataCommand {

        public FunctionStartsCommand(int cmd, int cmdsize, DataInput i) throws IOException, InvalidObjectException {
            super(cmd, cmdsize, i);
        }

        @Override
        public String toString() {
            return "FunctionStartsCommand{" + '}';
        }
    }

    private static class DataInCodeCommand extends LinkEditDataCommand {

        public DataInCodeCommand(int cmd, int cmdsize, DataInput i) throws IOException, InvalidObjectException {
            super(cmd, cmdsize, i);
        }

        @Override
        public String toString() {
            return "DataInCodeCommand{" + '}';
        }
    }

    private static class SymTabCommand extends Command {

        private final Collection<SymbolTableEntry> symbols;
        private final int symoff;
        private final int nsyms;
        private final int stroff;
        private final int strsize;

        public SymTabCommand(int cmd, int cmdsize, RandomAccessFile f, long offsetToStart, DataInput i) throws IOException, InvalidObjectException {
            super(cmd, cmdsize);
            Collection<SymbolTableEntry> symbols = new ArrayList<>();

            symoff = i.readInt();
            nsyms = i.readInt();
            stroff = i.readInt();
            strsize = i.readInt();

            long mark = f.getFilePointer();
            f.seek(symoff);
            for (int x = 0; x < nsyms; x++) {
                SymbolTableEntry e = new SymbolTableEntry(i);
                symbols.add(e);
            }

            this.symbols = Collections.unmodifiableCollection(symbols);
            f.seek(mark);
        }

        private static class SymbolTableEntry {

            private final int n_strx;
            private final byte type;
            private final int n_value;

            public SymbolTableEntry(DataInput i) throws IOException {
                n_strx = i.readInt();
                type = i.readByte();
                byte spare = i.readByte();
                short n_desc = i.readShort();
                n_value = i.readInt();
            }

            @Override
            public String toString() {
                return "SymbolTableEntry{" + "n_strx=" + n_strx + ", type=" + type + ", n_value=" + n_value + '}';
            }

        }

        @Override
        public String toString() {
            return "SymTabCommand{" + '}';
        }

    }

    private static class DySymTabCommand extends Command {

        public DySymTabCommand(int cmd, int cmdsize, DataInput i) throws IOException, InvalidObjectException {
            super(cmd, cmdsize);

            final int ilocalsym = i.readInt();
            final int nlocalsym = i.readInt();
            final int iextdefsym = i.readInt();
            final int nextdefsym = i.readInt();
            final int iundefsym = i.readInt();
            final int nundefsym = i.readInt();
            final int tocoff = i.readInt();
            final int ntoc = i.readInt();
            final int modtaboff = i.readInt();
            final int nmodtab = i.readInt();
            final int extrefsymoff = i.readInt();
            final int nextrefsyms = i.readInt();
            final int indirectsymoff = i.readInt();
            final int nindirectsyms = i.readInt();
            final int extreloff = i.readInt();
            final int nextrel = i.readInt();
            final int locreloff = i.readInt();
            final int nlocrel = i.readInt();
        }

        @Override
        public String toString() {
            return "DySymTabCommand{" + '}';
        }

    }

    private static class UUIDCommand extends Command {

        public UUIDCommand(int cmd, int cmdsize, DataInput i) throws IOException, InvalidObjectException {
            super(cmd, cmdsize);
        }

        @Override
        public String toString() {
            return "UUIDCommand{" + '}';
        }

    }

    private static class VersionMinCommand extends Command {

        public VersionMinCommand(int cmd, int cmdsize, DataInput i) throws IOException, InvalidObjectException {
            super(cmd, cmdsize);
        }

        @Override
        public String toString() {
            return "VersionMinCommand{" + '}';
        }

    }

    private static class SourceVersionCommand extends Command {

        public SourceVersionCommand(int cmd, int cmdsize, DataInput i) throws IOException, InvalidObjectException {
            super(cmd, cmdsize);
        }

        @Override
        public String toString() {
            return "SourceVersionCommand{" + '}';
        }

    }

    @Override
    public BinaryObject load(RandomAccessFile file) throws IOException, InvalidObjectException {
        final MachOBinaryObject o = new MachOBinaryObject(file);
        return o;
    }

}
