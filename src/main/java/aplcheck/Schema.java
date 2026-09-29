package aplcheck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 字段表：说明一条消息里有哪些字段、按什么顺序排。
 *
 * <p>每个字段有一个 `tag`（1 字节）和一个名字。body 里的值是**按字段表声明的顺序**
 * 依次排下来的，所以字段表的顺序是解码的一部分。
 */
public final class Schema {

    /** 一个字段：tag 参与线格式，name 只用于人读。 */
    public record Field(int tag, String name) {

        public Field {
            if (tag < 0 || tag > 255) {
                throw new IllegalArgumentException("tag 必须在 0..255：" + tag);
            }
            if (name == null) {
                throw new IllegalArgumentException("name 不能为 null");
            }
        }
    }

    private final Map<Integer, Field> byTag = new HashMap<>();

    public Schema(List<Field> fields) {
        if (fields == null) {
            throw new IllegalArgumentException("fields 不能为 null");
        }
        for (Field f : fields) {
            byTag.put(f.tag(), f);
        }
    }

    /** 全部字段。 */
    public List<Field> fields() {
        return new ArrayList<>(byTag.values());
    }

    /** 按 tag 取字段；没有这个 tag 时返回 {@code null}。 */
    public Field field(int tag) {
        return byTag.get(tag);
    }
}
