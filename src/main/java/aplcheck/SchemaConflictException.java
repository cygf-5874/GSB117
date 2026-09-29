package aplcheck;

/** Schema 里出现了重复的 tag。 */
public class SchemaConflictException extends AplException {

    public SchemaConflictException(String message) {
        super(message);
    }
}
