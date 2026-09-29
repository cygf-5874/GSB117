package aplcheck;

/** body 里的字符串字段不是合法的 UTF-8 字节序列。 */
public class MalformedBodyException extends AplException {

    public MalformedBodyException(String message) {
        super(message);
    }
}
