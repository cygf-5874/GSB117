package aplcheck;

/** aplcheck 里所有帧格式 / 模式错误的基类（非受检）。 */
public class AplException extends RuntimeException {

    public AplException(String message) {
        super(message);
    }
}
