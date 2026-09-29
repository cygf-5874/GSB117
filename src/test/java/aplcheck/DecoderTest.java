package aplcheck;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * aplcheck 的既有用例（12 个），覆盖正常路径与几条基本错误路径。
 *
 * <p>不依赖 JUnit：静态方法加 {@code main} 聚合自跑，全部通过时打印 {@code 12/12 passed}
 * 并以退出码 0 结束；有失败时打印失败清单并以退出码 1 结束。
 */
public final class DecoderTest {

    private static final Schema SCHEMA = new Schema(List.of(
            new Schema.Field(1, "user"),
            new Schema.Field(2, "host"),
            new Schema.Field(3, "note")));

    private interface Body {
        /** 返回 null 表示通过，否则返回失败原因。 */
        String run();
    }

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        run("decode-two-fields", DecoderTest::testTwoFields);
        run("decode-empty-values", DecoderTest::testEmptyValues);
        run("decode-len-zero-empty-schema", DecoderTest::testEmptySchema);
        run("bad-magic-rejected", DecoderTest::testBadMagic);
        run("short-frame-rejected", DecoderTest::testShortFrame);
        run("null-frame-rejected", DecoderTest::testNullFrame);
        run("len-beyond-frame-rejected", DecoderTest::testLenBeyondFrame);
        run("field-truncated-rejected", DecoderTest::testFieldTruncated);
        run("encode-roundtrip", DecoderTest::testRoundTrip);
        run("field-lookup-by-tag", DecoderTest::testLookup);
        run("tags-in-declaration-order", DecoderTest::testTagOrder);
        run("utf8-multibyte-field", DecoderTest::testUtf8);

        System.out.println(passed + "/12 passed");
        if (!failures.isEmpty()) {
            for (String f : failures) {
                System.out.println("FAIL " + f);
            }
            System.exit(1);
        }
    }

    private static void run(String name, Body body) {
        try {
            String err = body.run();
            if (err == null) {
                passed++;
            } else {
                failures.add(name + "：" + err);
            }
        } catch (Throwable t) {
            failures.add(name + "：抛出 " + t);
        }
    }

    // ------------------------------------------------------------------ 用例

    private static String testTwoFields() {
        Msg msg = decoder().decode(frame(1, 0, "alice", "h1", "n1"));
        if (msg == null) {
            return "期望=解出消息 实际=null";
        }
        String e = expect("version", msg.version(), 1);
        if (e != null) {
            return e;
        }
        e = expect("field(1)", msg.field(1), "alice");
        if (e != null) {
            return e;
        }
        return expect("field(2)", msg.field(2), "h1");
    }

    private static String testEmptyValues() {
        Msg msg = decoder().decode(frame(1, 0, "", "", ""));
        if (msg == null) {
            return "期望=解出消息 实际=null";
        }
        String e = expect("field(1)", msg.field(1), "");
        if (e != null) {
            return e;
        }
        return expect("field(2)", msg.field(2), "");
    }

    private static String testEmptySchema() {
        Decoder d = new Decoder(new Schema(List.of()));
        Msg msg = d.decode(frame(1, 0));
        if (msg == null) {
            return "期望=解出消息 实际=null";
        }
        if (!msg.tags().isEmpty()) {
            return "期望=没有字段 实际=" + msg.tags();
        }
        return null;
    }

    private static String testBadMagic() {
        byte[] f = frame(1, 0, "x");
        f[0] = 0x42;
        try {
            decoder().decode(f);
            return "期望=抛 BadMagicException 实际=正常返回";
        } catch (BadMagicException ok) {
            return null;
        } catch (Throwable t) {
            return "期望=BadMagicException 实际=" + t.getClass().getName();
        }
    }

    private static String testShortFrame() {
        try {
            decoder().decode(new byte[7]);
            return "期望=抛 TruncatedFrameException 实际=正常返回";
        } catch (TruncatedFrameException ok) {
            return null;
        } catch (Throwable t) {
            return "期望=TruncatedFrameException 实际=" + t.getClass().getName();
        }
    }

    private static String testNullFrame() {
        try {
            decoder().decode(null);
            return "期望=抛 TruncatedFrameException 实际=正常返回";
        } catch (TruncatedFrameException ok) {
            return null;
        } catch (Throwable t) {
            return "期望=TruncatedFrameException 实际=" + t.getClass().getName();
        }
    }

    private static String testLenBeyondFrame() {
        byte[] f = frame(1, 0, "abc");
        // 把声明的 len 改大 1 字节（此刻帧还没跟着变长）
        f[4] = (byte) ((f.length - 8 + 1) & 0xFF);
        f[5] = 0;
        f[6] = 0;
        f[7] = 0;
        try {
            decoder().decode(f);
            return "期望=抛 TruncatedFrameException 实际=正常返回";
        } catch (TruncatedFrameException ok) {
            return null;
        } catch (Throwable t) {
            return "期望=TruncatedFrameException 实际=" + t.getClass().getName();
        }
    }

    private static String testFieldTruncated() {
        // body 说第一个字段 10 字节，实际只给了 2 字节
        byte[] body = new byte[] {10, 0, 'a', 'b'};
        byte[] f = assemble(1, 0, body);
        try {
            decoder().decode(f);
            return "期望=抛 TruncatedFrameException 实际=正常返回";
        } catch (TruncatedFrameException ok) {
            return null;
        } catch (Throwable t) {
            return "期望=TruncatedFrameException 实际=" + t.getClass().getName();
        }
    }

    private static String testRoundTrip() {
        byte[] f = frame(1, Decoder.FLAG_COMPRESSED, "alice", "h1", "n1");
        byte[] copy = Arrays.copyOf(f, f.length);
        Msg msg = decoder().decode(f);
        if (msg == null) {
            return "期望=解出消息 实际=null";
        }
        byte[] again = msg.encode();
        if (!Arrays.equals(copy, again)) {
            return "期望=encode 还原原帧 " + hex(copy) + " 实际=" + hex(again);
        }
        return null;
    }

    private static String testLookup() {
        Msg msg = decoder().decode(frame(1, 0, "a", "b", "c"));
        if (msg == null) {
            return "期望=解出消息 实际=null";
        }
        String e = expect("field(3)", msg.field(3), "c");
        if (e != null) {
            return e;
        }
        return expect("field(9)（未声明）", msg.field(9), null);
    }

    private static String testTagOrder() {
        Msg msg = decoder().decode(frame(1, 0, "a", "b", "c"));
        if (msg == null) {
            return "期望=解出消息 实际=null";
        }
        List<Integer> want = List.of(1, 2, 3);
        if (!want.equals(msg.tags())) {
            return "期望=tags()==" + want + " 实际=" + msg.tags();
        }
        return null;
    }

    private static String testUtf8() {
        String value = "记账-héllo";
        Msg msg = decoder().decode(frame(1, 0, value, "x", "y"));
        if (msg == null) {
            return "期望=解出消息 实际=null";
        }
        return expect("field(1) 多字节 UTF-8", msg.field(1), value);
    }

    // ------------------------------------------------------------------ 工具

    private static Decoder decoder() {
        return new Decoder(SCHEMA);
    }

    private static byte[] frame(int version, int flags, String... values) {
        return assemble(version, flags, body(values));
    }

    private static byte[] body(String... values) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (String v : values) {
            byte[] b = v.getBytes(StandardCharsets.UTF_8);
            buf.write(b.length & 0xFF);
            buf.write((b.length >>> 8) & 0xFF);
            buf.write(b, 0, b.length);
        }
        return buf.toByteArray();
    }

    private static byte[] assemble(int version, int flags, byte[] body) {
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

    private static String expect(String what, Object actual, Object wanted) {
        if (wanted == null ? actual == null : wanted.equals(actual)) {
            return null;
        }
        return "期望=" + what + "==" + wanted + " 实际=" + actual;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x & 0xFF));
        }
        return sb.toString();
    }

    private DecoderTest() {
    }
}
