package aplcheck;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一条解码后的消息：版本、flags、按字段表顺序排下来的字符串字段。 */
public final class Msg {

    private final int version;
    private final int flags;
    private final int rawFlags;
    private final Map<Integer, String> fields;

    public Msg(int version, int flags, int rawFlags, Map<Integer, String> fields) {
        this.version = version;
        this.flags = flags;
        this.rawFlags = rawFlags;
        this.fields = new LinkedHashMap<>(fields);
    }

    public int version() {
        return version;
    }

    /** 已知 flags 位（压缩 / 签名）。 */
    public int flags() {
        return flags;
    }

    /** 解码时读到的 flags **原始字节**（含未知位）。 */
    public int rawFlags() {
        return rawFlags;
    }

    /** 字段的 tag，按解码顺序。 */
    public List<Integer> tags() {
        return new ArrayList<>(fields.keySet());
    }

    /** 按 tag 取值；没有这个 tag 时返回 {@code null}。 */
    public String field(int tag) {
        return fields.get(tag);
    }

    /** 重新编码成帧；flags 用 {@link #rawFlags()} 原样写回。 */
    public byte[] encode() {
        byte[] body = bodyBytes();
        byte[] out = new byte[Decoder.HEADER_LEN + body.length];
        out[0] = (byte) Decoder.MAGIC0;
        out[1] = (byte) Decoder.MAGIC1;
        out[2] = (byte) version;
        out[3] = (byte) rawFlags;
        out[4] = (byte) (body.length & 0xFF);
        out[5] = (byte) ((body.length >>> 8) & 0xFF);
        out[6] = (byte) ((body.length >>> 16) & 0xFF);
        out[7] = (byte) ((body.length >>> 24) & 0xFF);
        System.arraycopy(body, 0, out, Decoder.HEADER_LEN, body.length);
        return out;
    }

    private byte[] bodyBytes() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (Map.Entry<Integer, String> e : fields.entrySet()) {
            byte[] value = e.getValue() == null
                    ? new byte[0]
                    : e.getValue().getBytes(StandardCharsets.UTF_8);
            if (value.length > 0xFFFF) {
                throw new IllegalArgumentException("字段值超过 65535 字节：" + e.getKey());
            }
            buf.write(value.length & 0xFF);
            buf.write((value.length >>> 8) & 0xFF);
            buf.write(value, 0, value.length);
        }
        return buf.toByteArray();
    }
}
