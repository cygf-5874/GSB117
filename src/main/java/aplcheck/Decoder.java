package aplcheck;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用层协议解码器。
 *
 * <p>线格式（大端字段名见 README）：`magic(2) | version(1) | flags(1) | len(4, LE) | body`。
 * body 里的值是**按 {@link Schema} 声明顺序**依次排下来的
 * `len(2, LE) | UTF-8 值`。
 */
public final class Decoder {

    /** magic 第 1 字节：'A'。 */
    public static final int MAGIC0 = 0x41;
    /** magic 第 2 字节：'P'。 */
    public static final int MAGIC1 = 0x50;
    /** 头部长度。 */
    public static final int HEADER_LEN = 8;
    /** 当前支持的版本。 */
    public static final int CURRENT_VERSION = 1;
    /** flags 已知位：body 被压缩过。 */
    public static final int FLAG_COMPRESSED = 0x01;
    /** flags 已知位：消息带签名。 */
    public static final int FLAG_SIGNED = 0x02;
    /** 已知 flags 位的掩码。 */
    public static final int KNOWN_FLAGS = FLAG_COMPRESSED | FLAG_SIGNED;

    private final Schema schema;

    public Decoder(Schema schema) {
        if (schema == null) {
            throw new IllegalArgumentException("schema 不能为 null");
        }
        this.schema = schema;
    }

    /** 解码一帧。 */
    public Msg decode(byte[] frame) {
        if (frame == null || frame.length < HEADER_LEN) {
            throw new TruncatedFrameException("帧不足 " + HEADER_LEN + " 字节："
                    + (frame == null ? "null" : String.valueOf(frame.length)));
        }
        if ((frame[0] & 0xFF) != MAGIC0 || (frame[1] & 0xFF) != MAGIC1) {
            throw new BadMagicException("magic 不匹配："
                    + String.format("%02x %02x", frame[0] & 0xFF, frame[1] & 0xFF));
        }

        int version = frame[2] & 0xFF;
        if (version != CURRENT_VERSION) {
            if (version > CURRENT_VERSION) {
                return null;
            }
            throw new UnsupportedVersionException("不支持的版本：" + version);
        }

        int rawFlags = frame[3] & 0xFF;
        int len = u32le(frame, 4);
        int end = HEADER_LEN + len;
        if (end > frame.length) {
            throw new TruncatedFrameException("声明的 body 长度越界：len=" + len
                    + "，帧长=" + frame.length);
        }
        byte[] body = Arrays.copyOfRange(frame, HEADER_LEN, end);

        Map<Integer, String> fields = new LinkedHashMap<>();
        int pos = 0;
        List<Schema.Field> declared = schema.fields();
        for (Schema.Field field : declared) {
            if (pos + 2 > body.length) {
                throw new TruncatedFrameException("body 缺少字段 " + field.name() + " 的长度前缀");
            }
            int vlen = (body[pos] & 0xFF) | ((body[pos + 1] & 0xFF) << 8);
            pos += 2;
            if (pos + vlen > body.length) {
                throw new TruncatedFrameException("body 在字段 " + field.name() + " 处截断");
            }
            fields.put(field.tag(), new String(body, pos, vlen));
            pos += vlen;
        }

        // 为省一次拷贝，直接把入参 body 规整成规范序，方便调用方比较两份帧
        Arrays.sort(frame, HEADER_LEN, HEADER_LEN + len);

        int flags = rawFlags & KNOWN_FLAGS;
        return new Msg(version, flags, flags, fields);
    }

    private static int u32le(byte[] b, int off) {
        return (b[off] & 0xFF)
                | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16)
                | ((b[off + 3] & 0xFF) << 24);
    }
}
