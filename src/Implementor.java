import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class for implementation Interfaces and packing in Jar.
 *
 * @author Sloveckin
 * @version 1.0
 */


public class Implementor implements JarImpler {

    /**
     * Constant for public modifier.
     */
    private static final String PUBLIC = "public";
    /**
     * Constant for class.
     */
    private static final String CLASS = "class";

    /**
     * Constant for tab.
     */
    private static final String TAB = "    ";

    /**
     * Constant for semicolon.
     */
    private static final String END = ";";

    /**
     * Constant for new-line symbol.
     */
    private static final String NEW_LINE = System.lineSeparator();
    /**
     * Constant for skipping one line.
     */
    private static final String SKIP_LINE = System.lineSeparator().repeat(2);


    /**
     * Constant for folder that will contain .class files. Will be deleted after packing jar.
     */
    private static final String STACK_FOLDER = "StackFolder";

    /**
     * Empty constructor.
     */
    public Implementor() {

    }


    /**
     * Creates path to file with specific extension.
     *
     * @param token     Interface for implementation.
     * @param root      Path where token is located.
     * @param extension extension of file.
     * @return Path to file.
     */

    private Path createPathWithExtension(final Class<?> token, final Path root, final String extension) {
        return Paths.get(createPathToFile(token, root) + extension);
    }

    /**
     * Creates path to file.
     *
     * @param token Interface for implementation.
     * @param root  Path where token is located.
     * @return Path to file.
     */
    private Path createPathToFile(final Class<?> token, final Path root) {
        final Package pack = token.getPackage();
        final String[] pieceOfPack = pack.getName().split("\\.");
        final Path toPath = Paths.get(root.toString(), pieceOfPack);
        return Paths.get(toPath.toString(), token.getSimpleName() + "Impl");
    }

    /**
     * Creates implementation for interface.
     *
     * @param token type token to create implementation for.
     * @param root  root directory.
     * @throws ImplerException if token is not interface or token has private modifier.
     */
    @Override
    public void implement(final Class<?> token, final Path root) throws ImplerException {
        checkException(token);
        Path filePath = createPathWithExtension(token, root, ".java");
        createDirsForFile(filePath);
        impl(token, filePath);
    }

    /**
     * Creates jar file with implementation for interface.
     * <p>
     * Create temp folder that will be deleted after packing in jar.
     * Called {@link #implement(Class, Path)}, {@link #createDirs(Path)} {@link #compileFiles(Class, Path, String...)} and {@link #packingIntoJar(Class, Path, Path)}.
     * </p>
     *
     * @param token   type token to create implementation for.
     * @param jarFile target <var>.jar</var> file.
     * @throws ImplerException if token is not interface or token has private modifier.
     */
    @Override
    public void implementJar(final Class<?> token, final Path jarFile) throws ImplerException {
        final Path stackFolder = Paths.get(STACK_FOLDER + token.getSimpleName());
        final Path javaFile = createPathWithExtension(token, stackFolder, ".java");
        createDirs(stackFolder);
        implement(token, stackFolder);
        compileFiles(token, stackFolder, javaFile.toString());
        packingIntoJar(token, stackFolder, jarFile);
    }

    /**
     * Packing files to .jar file. After deletes temp folder.
     *
     * @param token       type token to create implementation for.
     * @param stackFolder folder which contains .java and .class files.
     * @param jarFile     jar file that will be created.
     * @throws ImplerException if throws IOException while creating jar file.
     */
    private void packingIntoJar(final Class<?> token, final Path stackFolder, final Path jarFile) throws ImplerException {
        final Path pathToFile = createPathWithExtension(token, stackFolder, ".class");
        try (final JarOutputStream jarWriter = new JarOutputStream(Files.newOutputStream(jarFile), createManifest())) {
            jarWriter.putNextEntry(new JarEntry(convertToJarPath(token)));
            try {
                Files.copy(pathToFile, jarWriter);
            } catch (final IOException e) {
                throw new ImplerException(String.format("Exception while coping file. Exceptions: %s.", e.getMessage()));
            }

        } catch (final IOException e) {
            throw new ImplerException(String.format("Exception while creating %s. Exception: %s.", jarFile, e.getMessage()));
        } finally {
            try {
                Files.walkFileTree(stackFolder, new DeleteWalker());
            } catch (final IOException ignored) {

            }
        }
    }

    /**
     * Creates correct path string in jar file.
     *
     * @param token type token to create implementation for.
     * @return correct  path string in jar file.
     */
    private String convertToJarPath(final Class<?> token) {
        return token.getPackageName().replace(".", "/") +
                "/" +
                token.getSimpleName() +
                "Impl" +
                '.' +
                CLASS;
    }


    /**
     * Returns manifest for creating jar file. Manifest contains only manifest version.
     *
     * @return manifest.
     */
    private Manifest createManifest() {
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        return manifest;
    }

    /**
     * Compiles .java files in folder.
     *
     * @param token type token to create implementation for.
     * @param root  folder that contains .java file.
     * @param files files that will be compiled.
     */
    private static void compileFiles(final Class<?> token, final Path root, final String... files) {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final String classpath = root + File.pathSeparator + getClassPath(token);
        final String[] args = Stream.concat(Arrays.stream(files), Stream.of("-encoding", "UTF-8", "-cp", classpath))
                .toArray(String[]::new);
        compiler.run(null, null, null, args);
    }

    /**
     * Returns class path.
     *
     * @param token type token to create implementation for.
     * @return path
     */
    private static String getClassPath(final Class<?> token) {
        try {
            return Path.of(token.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (final URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Checks for legal token.
     *
     * @param token type token to create implementation for.
     * @throws ImplerException if token is not interface or token has private modifier.
     */
    private void checkException(final Class<?> token) throws ImplerException {
        if (!token.isInterface()) {
            throw new ImplerException(String.format("%s is not a interface.", token.getSimpleName()));
        }

        if (Modifier.isPrivate(token.getModifiers())) {
            throw new ImplerException("Can't implement private interface.");
        }
    }

    /**
     * Create implementation for interface.
     *
     * @param token    type token to create implementation for.
     * @param filePath path where will be created file.
     * @throws ImplerException if throws IOException while creating or writing to file.
     */
    private void impl(final Class<?> token, final Path filePath) throws ImplerException {
        try (final BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            try {
                writer.write(writeClass(token));
            } catch (final IOException e) {
                throw new ImplerException(String.format("Exception while writing in file. Exception: %s.", e.getMessage()));
            }
        } catch (final IOException e) {
            throw new ImplerException(String.format("Exception while opening file. Exception: %s.", e.getMessage()));
        }
    }

    /**
     * Return class in string.
     * <p>
     * Called {@link #writePackage(Class)}, {@link #writeClassName(Class)} and
     * {@link #getClassBody(Class)}.
     * </p>
     *
     * @param token type token to create implementation for.
     * @return class in string.
     */
    private String writeClass(final Class<?> token) {
        return String.format("%s%s%s %s", writePackage(token), SKIP_LINE, writeClassName(token), getClassBody(token));
    }

    /**
     * Return package in string.
     *
     * @param token type token to create implementation for.
     * @return package in string.
     */
    private String writePackage(final Class<?> token) {
        return token.getPackage().toString() + END;
    }

    /**
     * Returns name of class in string. Uses {@link String#format(String, Object...)}.
     *
     * @param token type token to create implementation for.
     * @return name of class in string.
     */
    private String writeClassName(final Class<?> token) {
        return String.format("%s %s %s %s %s", PUBLIC, CLASS, token.getSimpleName() + "Impl", "implements",
                token.getCanonicalName());
    }

    /**
     * Returns body of class in string.
     * <p>
     * Called {@link #writeAllMethods(Class)}. Uses {@link String#format(String, Object...)}.
     * </p>
     *
     * @param token type token to create implementation for.
     * @return body of class in string.
     */
    private String getClassBody(final Class<?> token) {
        return String.format("{%s%s%s}", SKIP_LINE, writeAllMethods(token), NEW_LINE);
    }

    /**
     * Return all methods in string.
     * <p>
     * Calls {@link #writeMethod(Method)} for each public, not default and not static method.
     * Joins them Uses {@link #SKIP_LINE} separated.
     * </p>
     *
     * @param token type token to create implementation for.
     * @return all methods in string.
     */
    private String writeAllMethods(final Class<?> token) {
        return Arrays.stream(token.getMethods())
                .filter(el -> !el.isDefault() && !Modifier.isStatic(el.getModifiers()))
                .map(this::writeMethod)
                .collect(Collectors.joining(SKIP_LINE));
    }

    /**
     * Return method in string.
     * <p>
     * Calls {@link #writeClassName(Class)}, {@link #writeMethodParams(Method)}, {@link #writeMethodExceptions(Method)}
     * and {@link #writeMethodBody(Method)}. Uses {@link String#format(String, Object...)}.
     * </p>
     *
     * @param method that will be converted in string.
     * @return method in string.
     */
    private String writeMethod(final Method method) {
        return String.format("%s%s%s%s%s %s%s", TAB, "@Override", NEW_LINE, writeMethodName(method), writeMethodParams(method),
                writeMethodExceptions(method), writeMethodBody(method));
    }

    /**
     * Returns body of method in string.
     * Calls {@link #writeMethod(Method)}.
     *
     * @param method body of which will be converted in string.
     * @return body of method converted in string.
     */
    private String writeMethodBody(final Method method) {
        return String.format("{%s%s%s%s}", NEW_LINE, writeMethodReturn(method.getReturnType()), NEW_LINE, TAB);
    }

    /**
     * Returns name of method in string.
     *
     * @param method name of which will be converted in string.
     * @return name of method converted in string.
     */
    private String writeMethodName(final Method method) {
        return String.format("%s%s %s %s", TAB, PUBLIC, method.getReturnType().getCanonicalName(), method.getName());
    }

    /**
     * Returns parameters of method in string. Uses
     *
     * @param method parameters of which will be converted in string.
     * @return parameters of method converted in string.
     */
    private String writeMethodParams(final Method method) {
        final String params = mapToStringWithDelimiterComma(method.getParameters(),
                el -> el.getType().getCanonicalName() + " " + el.getName()
        );
        return String.format("(%s)", params);
    }

    /**
     * Returns exception of method in string.
     *
     * @param method exceptions to which will be converted in string.
     * @return exception of method converted in string.
     */
    private String writeMethodExceptions(final Method method) {
        final Class<?>[] exceptions = method.getExceptionTypes();
        if (exceptions.length == 0) {
            return "";
        }
        final String exceptionsString = mapToStringWithDelimiterComma(exceptions, Class::getCanonicalName);
        return String.format("%s %s ", "throws", exceptionsString);
    }

    /**
     * Joins mapped elements using comma.
     *
     * @param src      array of something.
     * @param function that will map element of src.
     * @param <T>      type of this array.
     * @return element of stream with delimiter comma.
     */
    private <T> String mapToStringWithDelimiterComma(final T[] src, final Function<T, String> function) {
        return Arrays.stream(src)
                .map(function)
                .collect(Collectors.joining(", "));
    }


    /**
     * Returns default value of function in string.
     *
     * @param returnType type of returned value.
     * @return default value converted in string.
     */
    private String writeMethodReturn(final Class<?> returnType) {
        String val = "0";
        if (!returnType.isPrimitive()) {
            val = "null";
        } else if (returnType.equals(boolean.class)) {
            val = "false";
        } else if (returnType.equals(void.class)) {
            val = "";
        }
        return String.format("%s%s%s %s%s", TAB, TAB, "return", val, END);
    }

    /**
     * Creates parent directories for path. Ignores {@link IOException}.
     *
     * @param path path of file.
     */
    private void createDirsForFile(final Path path) {
        try {
            final Path parent = path.getParent();
            if (parent != null && Files.notExists(parent)) {
                Files.createDirectories(path.getParent());
            }
        } catch (final IOException ignored) {

        }
    }

    /**
     * Creates directory. Ignores {@link IOException}.
     *
     * @param path of directory.
     */

    private void createDirs(final Path path) {
        try {
            Files.createDirectories(path);
        } catch (final IOException ignored) {

        }
    }


    /**
     * Class for deleting folders.
     *
     * @author Sloveckin
     * @version 1.0
     * @see FileVisitResult
     */
    private static class DeleteWalker extends SimpleFileVisitor<Path> {

        /**
         * Empty constructor.
         */
        private DeleteWalker() {

        }

        /**
         * Visits files and then deletes them.
         *
         * @param file  a reference to the file.
         * @param attrs the file's basic attributes. Ignored.
         * @return continue.
         * @throws IOException if exceptions occurs while walking or deleting file.
         */
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            return delete(file);
        }

        /**
         * Visits directories and then deletes them.
         *
         * @param dir a reference to the directory.
         * @param exc I don't know what is it.
         * @return continue.
         * @throws IOException if exception occurs while walking or deleting directory.
         */
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return delete(dir);
        }

        /**
         * Delete file/directory by this path.
         *
         * @param path reference for file/directory.
         * @return continue.
         * @throws IOException if exception occurs while deleting by reference.
         */
        private FileVisitResult delete(final Path path) throws IOException {
            Files.delete(path);
            return FileVisitResult.CONTINUE;
        }

    }

    /**
     * Creates implementation for interface. Or creates jar of implementations for interface.
     *
     * <p>
     * if the number of input given is less than 2 or greater than 3, the program will exit with an incorrect output message.
     * </p>
     *
     * <p>
     * If the number of input data equals two, then the implementation for the interface is created.
     * </p>
     *
     * <p>
     * If the number of inputs is three, then an implementation is created for the interface and placed in a .jar file.
     * Folder where will be contained .java and .class will be deleted.
     * </p>
     *
     * @param args arguments of command line.
     */

    public static void main(String[] args) {

        if (args.length < 2 || args.length > 3) {
            System.out.println("Incorrect input.");
            return;
        }

        if (args.length == 2) {
            if (args[0] == null || args[1] == null) {
                System.out.println("Incorrect input. Example: <token> <file>");
                return;
            }
        } else {
            if (args[0] == null || args[1] == null || args[2] == null || !args[0].equals("-jar")) {
                System.out.println("Incorrect input. Example: -jar <token> <file.jar>");
                return;
            }
        }


        try {
            final Implementor implementor = new Implementor();
            if (args.length == 2) {
                implementor.implement(Class.forName(args[0]), Paths.get(args[1]));
            } else {
                implementor.implementJar(Class.forName(args[1]), Paths.get(args[2]));
            }

        } catch (final ImplerException e) {
            System.out.println(e.getMessage());
        } catch (final ClassNotFoundException e) {
            System.out.println("Interface for implementation not exists.");
        } catch (final InvalidPathException e) {
            System.out.println("Invalid path.");
        }
    }
}