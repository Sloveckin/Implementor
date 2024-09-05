import java.nio.file.Path;

public interface JarImpler {

    void implement(final Class<?> token, final Path root) throws ImplerException;

    void implementJar(final Class<?> token, final Path jarFile) throws ImplerException;
}
