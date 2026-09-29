package aplcheck;

/** 帧的 version 高于当前实现支持的版本。 */
public class UnsupportedVersionException extends AplException {

    public UnsupportedVersionException(String message) {
        super(message);
    }
}
