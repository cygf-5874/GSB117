import aplcheck.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** GSB117 v2 fixed checker: behavioral protocol-decoder contract. Do not modify. */
public final class Checker {
    private interface Body { String run() throws Exception; }
    private record Scenario(String name, Body body) {}
    private static final class Failure extends RuntimeException {
        final String expected;
        final String actual;
        final String note;
        Failure(String expected, String actual) { this(expected, actual, "assertion failed"); }
        Failure(String expected, String actual, String note) {
            super(note);
            this.expected = expected;
            this.actual = actual;
            this.note = note;
        }
    }

    private static final Schema SCHEMA = new Schema(List.of(
            new Schema.Field(1, "user"),
            new Schema.Field(2, "host"),
            new Schema.Field(3, "note")));

    private static void expect(boolean ok, String expected, String actual) {
        if (!ok) throw new Failure(expected, actual);
    }

    private static byte[] body(String... values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String value : values) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            out.write(bytes.length & 0xFF);
            out.write((bytes.length >>> 8) & 0xFF);
            out.write(bytes, 0, bytes.length);
        }
        return out.toByteArray();
    }

    private static byte[] frame(int version, int flags, byte[] body) {
        byte[] out = new byte[8 + body.length];
        out[0] = (byte) Decoder.MAGIC0;
        out[1] = (byte) Decoder.MAGIC1;
        out[2] = (byte) version;
        out[3] = (byte) flags;
        out[4] = (byte) (body.length & 0xFF);
        out[5] = (byte) ((body.length >>> 8) & 0xFF);
        out[6] = (byte) ((body.length >>> 16) & 0xFF);
        out[7] = (byte) ((body.length >>> 24) & 0xFF);
        System.arraycopy(body, 0, out, 8, body.length);
        return out;
    }

    private static Decoder decoder() { return new Decoder(SCHEMA); }

    private static String basic() {
        Msg msg = decoder().decode(frame(1, Decoder.FLAG_SIGNED, body("alice", "h1", "n1")));
        expect(msg.version() == 1, "version=1", "version=" + msg.version());
        expect("alice".equals(msg.field(1)), "field(1)=alice", "field(1)=" + msg.field(1));
        expect("h1".equals(msg.field(2)), "field(2)=h1", "field(2)=" + msg.field(2));
        expect(List.of(1, 2, 3).equals(msg.tags()), "tags=[1,2,3]", "tags=" + msg.tags());
        return null;
    }

    private static String futureVersion() {
        try {
            decoder().decode(frame(2, 0, body("a", "b", "c")));
            return "未来版本 期望=UnsupportedVersionException 实际=正常返回";
        } catch (UnsupportedVersionException ok) {
            return null;
        } catch (Throwable t) {
            return "未来版本 期望=UnsupportedVersionException 实际=" + t;
        }
    }

    private static String lengthBeyondFrame() {
        byte[] f = frame(1, 0, body("a", "b", "c"));
        f[4] = (byte) (f.length + 1);
        f[5] = 0; f[6] = 0; f[7] = 0;
        try {
            decoder().decode(f);
            return "超长 len 期望=TruncatedFrameException 实际=正常返回";
        } catch (TruncatedFrameException ok) {
            return null;
        } catch (Throwable t) {
            return "超长 len 期望=TruncatedFrameException 实际=" + t;
        }
    }

    private static String unsignedLength() {
        byte[] f = frame(1, 0, body("a", "b", "c"));
        f[4] = (byte) 0xFF; f[5] = (byte) 0xFF; f[6] = (byte) 0xFF; f[7] = (byte) 0xFF;
        try {
            decoder().decode(f);
            return "无符号巨大 len 期望=TruncatedFrameException 实际=正常返回";
        } catch (TruncatedFrameException ok) {
            return null;
        } catch (Throwable t) {
            return "无符号巨大 len 期望=TruncatedFrameException 实际=" + t;
        }
    }

    private static String flagsPreserved() {
        int flags = 0x81;
        Msg msg = decoder().decode(frame(1, flags, body("a", "b", "c")));
        expect(msg.rawFlags() == flags, "rawFlags=" + flags, "rawFlags=" + msg.rawFlags());
        expect(msg.flags() == 1, "flags=1", "flags=" + msg.flags());
        int encoded = msg.encode()[3] & 0xFF;
        expect(encoded == flags, "encode flags=" + flags, "encode flags=" + encoded);
        return null;
    }

    private static String invalidUtf8() {
        byte[] raw = new byte[] {(byte) 0xC3, 0x28};
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(2); body.write(0); body.write(raw, 0, raw.length);
        try {
            decoder().decode(frame(1, 0, body.toByteArray()));
            return "非法 UTF-8 期望=MalformedBodyException 实际=正常返回";
        } catch (MalformedBodyException ok) {
            return null;
        } catch (Throwable t) {
            return "非法 UTF-8 期望=MalformedBodyException 实际=" + t;
        }
    }

    private static String duplicateTag() {
        try {
            new Schema(List.of(new Schema.Field(1, "a"), new Schema.Field(1, "b")));
            return "重复 tag 期望=SchemaConflictException 实际=正常构造";
        } catch (SchemaConflictException ok) {
            return null;
        } catch (Throwable t) {
            return "重复 tag 期望=SchemaConflictException 实际=" + t;
        }
    }

    private static String declarationOrder() {
        Schema schema = new Schema(List.of(new Schema.Field(2, "host"), new Schema.Field(1, "user")));
        Msg msg = new Decoder(schema).decode(frame(1, 0, body("two", "one")));
        expect(List.of(2, 1).equals(msg.tags()), "tags=[2,1]", "tags=" + msg.tags());
        expect("two".equals(msg.field(2)), "field(2)=two", "field(2)=" + msg.field(2));
        return null;
    }

    private static String inputUnmodified() {
        byte[] f = frame(1, 0, body("a", "b", "c"));
        byte[] copy = Arrays.copyOf(f, f.length);
        decoder().decode(f);
        expect(Arrays.equals(copy, f), "decode 不修改入参", "入参被修改");
        return null;
    }

    private static String encodeRoundTrip() {
        byte[] f = frame(1, 0x81, body("a", "b", "c"));
        Msg msg = decoder().decode(f);
        expect(Arrays.equals(f, msg.encode()), "encode 还原原帧", "编码不一致");
        return null;
    }

    private static String trailingBytes() {
        byte[] raw = body("a", "b", "c");
        byte[] withTail = Arrays.copyOf(raw, raw.length + 1);
        withTail[withTail.length - 1] = 0x7F;
        try {
            decoder().decode(frame(1, 0, withTail));
            return "body 尾部多余字节 期望=TruncatedFrameException 实际=正常返回";
        } catch (TruncatedFrameException ok) {
            return null;
        } catch (Throwable t) {
            return "body 尾部多余字节 期望=TruncatedFrameException 实际=" + t;
        }
    }

    private static List<Scenario> scenarios() {
        List<Scenario> list = new ArrayList<>();
        list.add(new Scenario("basic/decode", Checker::basic));
        list.add(new Scenario("version/future", Checker::futureVersion));
        list.add(new Scenario("length/beyond-frame", Checker::lengthBeyondFrame));
        list.add(new Scenario("length/unsigned", Checker::unsignedLength));
        list.add(new Scenario("flags/preserved", Checker::flagsPreserved));
        list.add(new Scenario("utf8/rejected", Checker::invalidUtf8));
        list.add(new Scenario("schema/duplicate-tag", Checker::duplicateTag));
        list.add(new Scenario("schema/declaration-order", Checker::declarationOrder));
        list.add(new Scenario("purity/input-unmodified", Checker::inputUnmodified));
        list.add(new Scenario("roundtrip/encode", Checker::encodeRoundTrip));
        list.add(new Scenario("tail/rejected", Checker::trailingBytes));
        return list;
    }

    public static void main(String[] args) {
        boolean list = false;
        List<String> only = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if ("-list".equals(args[i])) list = true;
            else if ("--only".equals(args[i]) && i + 1 < args.length) {
                for (String g : args[++i].split(",")) if (!g.isEmpty()) only.add(g);
            }
        }
        List<Scenario> all = scenarios();
        if (list) { for (Scenario s : all) System.out.println(s.name); return; }
        int pass = 0, total = 0;
        for (Scenario s : all) {
            String group = s.name.substring(0, s.name.indexOf('/'));
            if (!only.isEmpty() && !only.contains(group)) continue;
            total++;
            try {
                String err = s.body.run();
                if (err == null) { System.out.println("PASS " + s.name); pass++; }
                else System.out.println("FAIL " + s.name + "  " + err);
            } catch (Failure f) {
                System.out.println("FAIL " + s.name + "  期望=" + f.expected + " 实际=" + f.actual + "（" + f.note + "）");
            } catch (Throwable t) {
                System.out.println("FAIL " + s.name + "  期望=正常返回 实际=" + t);
            }
        }
        System.out.println("结果：通过 " + pass + "/" + total);
        if (pass != total || total == 0) System.exit(1);
    }

    private Checker() {}
}