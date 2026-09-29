package aplcheck;

/** 帧被截断：长度不够、声明的 body 长度越界、或 body 里的字段读不完整。 */
public class TruncatedFrameException extends AplException {

    public TruncatedFrameException(String message) {
        super(message);
    }
}
