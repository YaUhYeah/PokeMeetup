package io.github.pokemeetup.server.deployment;

import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class DeploymentHelper {
    public static void createServerDeployment(Path deploymentDir) throws IOException {
        // Create directory structure
        createDirectory(deploymentDir);
        createDirectory(Paths.get(deploymentDir.toString(), "config"));
        createDirectory(Paths.get(deploymentDir.toString(), "plugins"));
        createDirectory(Paths.get(deploymentDir.toString(), "worlds"));
        createDirectory(Paths.get(deploymentDir.toString(), "logs"));

        // Copy server jar
        Path serverJar = Paths.get("build/libs/pokemon-meetup-server.jar");
        Files.copy(serverJar, Paths.get(deploymentDir.toString(), "server.jar"));

        // Create start scripts
        createStartScript(deploymentDir, "start.bat", "@echo off\njava -jar server.jar");
        createStartScript(deploymentDir, "start.sh", "#!/bin/bash\njava -jar server.jar");

        // Make shell script executable on Unix
        Paths.get(deploymentDir.toString(), "start.sh").toFile().setExecutable(true);

        // Create default config
        createDefaultConfig(deploymentDir);

        // Create README
        createReadme(deploymentDir);
    }

    private static void createDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    private static void createStartScript(Path deploymentDir, String filename, String baseCommand) throws IOException {
        Path scriptPath = Paths.get(deploymentDir.toString(), filename);
        StringBuilder script = new StringBuilder();
        boolean isWindows = filename.endsWith(".bat");

        if (isWindows) {
            script.append("@echo off\n")
                .append("setlocal enabledelayedexpansion\n\n")
                .append(":: Set Java path if needed\n")
                .append("set JAVA_HOME=\n")
                .append("if defined JAVA_HOME (\n")
                .append("    set JAVA=\"%JAVA_HOME%/bin/java\"\n")
                .append(") else (\n")
                .append("    set JAVA=java\n")
                .append(")\n\n")
                .append(":: Set memory options\n")
                .append("set MIN_MEMORY=1G\n")
                .append("set MAX_MEMORY=4G\n\n")
                .append(":: Set Java options\n")
                .append("set JAVA_OPTS=-Xms%MIN_MEMORY% -Xmx%MAX_MEMORY% ")
                .append("-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 ")
                .append("-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC ")
                .append("-XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 ")
                .append("-XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M ")
                .append("-XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 ")
                .append("-XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 ")
                .append("-XX:G1MixedGCLiveThresholdPercent=90 ")
                .append("-XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 ")
                .append("-XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 ")
                .append("-Dfile.encoding=UTF-8\n\n")
                .append(":: Start server\n")
                .append("echo Starting Pokemon Meetup Server...\n")
                .append("%JAVA% %JAVA_OPTS% -jar server.jar\n")
                .append("pause\n");
        } else {
            script.append("#!/bin/bash\n\n")
                .append("# Set Java path if needed\n")
                .append("if [ -n \"$JAVA_HOME\" ]; then\n")
                .append("    JAVA=\"$JAVA_HOME/bin/java\"\n")
                .append("else\n")
                .append("    JAVA=\"java\"\n")
                .append("fi\n\n")
                .append("# Set memory options\n")
                .append("MIN_MEMORY=\"1G\"\n")
                .append("MAX_MEMORY=\"4G\"\n\n")
                .append("# Set Java options\n")
                .append("JAVA_OPTS=\"-Xms$MIN_MEMORY -Xmx$MAX_MEMORY \\\n")
                .append("    -XX:+UseG1GC \\\n")
                .append("    -XX:+ParallelRefProcEnabled \\\n")
                .append("    -XX:MaxGCPauseMillis=200 \\\n")
                .append("    -XX:+UnlockExperimentalVMOptions \\\n")
                .append("    -XX:+DisableExplicitGC \\\n")
                .append("    -XX:+AlwaysPreTouch \\\n")
                .append("    -XX:G1NewSizePercent=30 \\\n")
                .append("    -XX:G1MaxNewSizePercent=40 \\\n")
                .append("    -XX:G1HeapRegionSize=8M \\\n")
                .append("    -XX:G1ReservePercent=20 \\\n")
                .append("    -XX:G1HeapWastePercent=5 \\\n")
                .append("    -XX:G1MixedGCCountTarget=4 \\\n")
                .append("    -XX:InitiatingHeapOccupancyPercent=15 \\\n")
                .append("    -XX:G1MixedGCLiveThresholdPercent=90 \\\n")
                .append("    -XX:G1RSetUpdatingPauseTimePercent=5 \\\n")
                .append("    -XX:SurvivorRatio=32 \\\n")
                .append("    -XX:+PerfDisableSharedMem \\\n")
                .append("    -XX:MaxTenuringThreshold=1 \\\n")
                .append("    -Dfile.encoding=UTF-8\"\n\n")
                .append("# Start server\n")
                .append("echo \"Starting Pokemon Meetup Server...\"\n")
                .append("$JAVA $JAVA_OPTS -jar server.jar \"$@\" >> logs/latest.log 2>&1 &\n\n")
                .append("echo $! > server.pid\n")
                .append("tail -f logs/latest.log\n");
        }

        // Write script file
        Files.write(scriptPath, Arrays.asList(script.toString().split("\n")), StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Make shell script executable on Unix systems
        if (!isWindows) {
            scriptPath.toFile().setExecutable(true, false);
        }
    }

    private static void createDefaultConfig(Path deploymentDir) throws IOException {
        ServerConnectionConfig config = new ServerConnectionConfig(
            "0.0.0.0",
            54555,
            54556,
            "Pokemon Meetup Server",
            true,
            100
        );

        Json json = new Json();
        Path configFile = Paths.get(deploymentDir.toString(), "config/server.json");
        Files.write(configFile, Arrays.asList(json.prettyPrint(config).split("\n")), StandardCharsets.UTF_8);
    }

    private static void createReadme(Path deploymentDir) throws IOException {
        String readme =
            "Pokemon Meetup Server\n" +
                "====================\n\n" +
                "Quick Start:\n" +
                "1. Edit config/server.json to configure your server\n" +
                "2. On Windows: Run start.bat\n" +
                "   On Linux/Mac: Run ./start.sh\n" +
                "3. Server will create necessary directories on first run\n\n" +
                "Plugins:\n" +
                "- Place plugin .jar files in the plugins directory\n" +
                "- Server will load plugins automatically on startup\n\n" +
                "Configuration:\n" +
                "- Server settings: config/server.json\n" +
                "- Plugin configs: config/<plugin-id>.json\n\n" +
                "Logs:\n" +
                "- Server logs are stored in the logs directory\n\n" +
                "Support:\n" +
                "- Issues: https://github.com/yourusername/pokemon-meetup/issues\n" +
                "- Wiki: https://github.com/yourusername/pokemon-meetup/wiki\n";

        Path readmeFile = Paths.get(deploymentDir.toString(), "README.md");
        Files.write(readmeFile, Arrays.asList(readme.split("\n")), StandardCharsets.UTF_8);
    }
}
