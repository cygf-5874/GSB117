package aplcheck;

/** 帧头两字节不是约定的 magic。 */
public class BadMagicException extends AplException {

    public BadMagicException(String message) {
        super(message);
    }
}
