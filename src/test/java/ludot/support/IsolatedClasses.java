// Builds restricted test JARs to check that core and client code run without excluded tiers.
package ludot.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class IsolatedClasses {
    private IsolatedClasses() {
    }

    public static Path create(Path destination, boolean client) throws Exception {
        Path root = Path.of("target/classes");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(destination));
                var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = root.relativize(file).toString().replace('\\', '/');
                if (name.startsWith("ludot/gui/") || name.startsWith("ludot/server/")
                        || name.startsWith("ludot/persistence/") || name.startsWith("ludot/application/GameService")
                        || name.startsWith("ludot/application/GameRepository")
                        || (!client && (name.startsWith("ludot/client/") || name.startsWith("ludot/network/")))) {
                    continue;
                }
                jar.putNextEntry(new JarEntry(name));
                Files.copy(file, jar);
                jar.closeEntry();
            }
            String probe = client ? "ProcessClient" : "ProcessCore";
            String name = "ludot/" + (client ? "client/" : "game/") + probe + ".class";
            jar.putNextEntry(new JarEntry(name));
            Files.copy(Path.of("target/test-classes").resolve(name), jar);
            jar.closeEntry();
        }
        return destination;
    }
}
