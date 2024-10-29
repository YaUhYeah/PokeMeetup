package io.github.pokemeetup.server.deployment;

import com.badlogic.gdx.utils.Json;
import io.github.pokemeetup.multiplayer.server.config.ServerConnectionConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class DeploymentHelper {
    public static void createServerDeployment(Path deploymentDir) throws IOException {
        // Create directory structure
        Files.createDirectories(deploymentDir);
        Files.createDirectories(deploymentDir.resolve("config"));
        Files.createDirectories(deploymentDir.resolve("plugins"));
        Files.createDirectories(deploymentDir.resolve("worlds"));
        Files.createDirectories(deploymentDir.resolve("logs"));

        // Copy server jar
        Path serverJar = Paths.get("build/libs/pokemon-meetup-server.jar");
        Files.copy(serverJar, deploymentDir.resolve("server.jar"));

        // Create start scripts
        createStartScript(deploymentDir, "start.bat", "@echo off\njava -jar server.jar");
        createStartScript(deploymentDir, "start.sh", "#!/bin/bash\njava -jar server.jar");

        // Make shell script executable on Unix
        deploymentDir.resolve("start.sh").toFile().setExecutable(true);

        // Create default config
        createDefaultConfig(deploymentDir);

        // Create README
        createReadme(deploymentDir);
    }

    private static void createStartScript(Path deploymentDir, String filename, String baseCommand) throws IOException {
        Path scriptPath = deploymentDir.resolve(filename);

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
            // Unix shell script (bash)
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
                .append("# Create logs directory if it doesn't exist\n")
                .append("mkdir -p logs\n\n")
                .append("# Start server\n")
                .append("echo \"Starting Pokemon Meetup Server...\"\n")
                .append("$JAVA $JAVA_OPTS -jar server.jar \"$@\" >> logs/latest.log 2>&1 &\n\n")
                .append("# Store PID for potential script usage\n")
                .append("echo $! > server.pid\n\n")
                .append("# Optional: Tail the log file\n")
                .append("tail -f logs/latest.log\n");
        }

        // Write script file
        Files.writeString(scriptPath, script.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Make shell script executable on Unix systems
        if (!isWindows) {
            scriptPath.toFile().setExecutable(true, false);
        }

        System.out.println("Created start script: " + scriptPath.getFileName());
    }

    public static void createConsoleScript(Path deploymentDir) throws IOException {
        // Create an additional console script for server management
        String consoleScript;

        if (Files.exists(deploymentDir.resolve("start.bat"))) {
            // Windows console script
            consoleScript = """
                @echo off
                setlocal enabledelayedexpansion

                :menu
                cls
                echo Pokemon Meetup Server Console
                echo ============================
                echo 1. Start Server
                echo 2. Stop Server
                echo 3. View Logs
                echo 4. Backup World
                echo 5. Reload Plugins
                echo 6. Exit
                echo.

                set /p choice="Select an option: "

                if "%choice%"=="1" call :startServer
                if "%choice%"=="2" call :stopServer
                if "%choice%"=="3" call :viewLogs
                if "%choice%"=="4" call :backupWorld
                if "%choice%"=="5" call :reloadPlugins
                if "%choice%"=="6" exit

                goto menu

                :startServer
                start /B start.bat
                goto :eof

                :stopServer
                taskkill /F /IM java.exe
                goto :eof

                :viewLogs
                type logs\\latest.log | more
                pause
                goto :eof

                :backupWorld
                set backup_dir=backups\\%date:~-4,4%%date:~-7,2%%date:~-10,2%
                mkdir %backup_dir%
                xcopy /E /I worlds %backup_dir%\\worlds
                echo Backup created in %backup_dir%
                pause
                goto :eof

                :reloadPlugins
                echo Reloading plugins...
                rem Add plugin reload command here
                pause
                goto :eof
                """;
        } else {
            // Unix console script
            consoleScript = """
                #!/bin/bash

                function show_menu {
                    clear
                    echo "Pokemon Meetup Server Console"
                    echo "============================"
                    echo "1. Start Server"
                    echo "2. Stop Server"
                    echo "3. View Logs"
                    echo "4. Backup World"
                    echo "5. Reload Plugins"
                    echo "6. Exit"
                    echo
                }

                function start_server {
                    ./start.sh &
                }

                function stop_server {
                    if [ -f server.pid ]; then
                        kill $(cat server.pid)
                        rm server.pid
                    else
                        pkill -f "java.*server.jar"
                    fi
                }

                function view_logs {
                    less logs/latest.log
                }

                function backup_world {
                    backup_dir="backups/$(date +%Y%m%d)"
                    mkdir -p "$backup_dir"
                    cp -r worlds "$backup_dir/"
                    echo "Backup created in $backup_dir"
                    read -p "Press Enter to continue..."
                }

                function reload_plugins {
                    echo "Reloading plugins..."
                    # Add plugin reload command here
                    read -p "Press Enter to continue..."
                }

                while true; do
                    show_menu
                    read -p "Select an option: " choice
                    case $choice in
                        1) start_server ;;
                        2) stop_server ;;
                        3) view_logs ;;
                        4) backup_world ;;
                        5) reload_plugins ;;
                        6) exit 0 ;;
                        *) echo "Invalid option" ;;
                    esac
                done
                """;
        }

        Path consolePath = deploymentDir.resolve(Files.exists(deploymentDir.resolve("start.bat")) ? "console.bat" : "console.sh");
        Files.writeString(consolePath, consoleScript, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Make Unix script executable
        if (!consolePath.toString().endsWith(".bat")) {
            consolePath.toFile().setExecutable(true, false);
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
        Path configFile = deploymentDir.resolve("config/server.json");
        Files.writeString(configFile, json.prettyPrint(config));
    }

    private static void createReadme(Path deploymentDir) throws IOException {
        String readme = """
            Pokemon Meetup Server
            ====================

            Quick Start:
            1. Edit config/server.json to configure your server
            2. On Windows: Run start.bat
               On Linux/Mac: Run ./start.sh
            3. Server will create necessary directories on first run

            Plugins:
            - Place plugin .jar files in the plugins directory
            - Server will load plugins automatically on startup

            Configuration:
            - Server settings: config/server.json
            - Plugin configs: config/<plugin-id>.json

            Logs:
            - Server logs are stored in the logs directory

            Support:
            - Issues: https://github.com/yourusername/pokemon-meetup/issues
            - Wiki: https://github.com/yourusername/pokemon-meetup/wiki
            """;

        Files.writeString(deploymentDir.resolve("README.md"), readme);
    }
}
