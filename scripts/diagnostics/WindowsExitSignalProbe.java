// Manual root-cause diagnostic, outside the acceptance case count.
// java -cp harness-process/target/classes scripts/diagnostics/WindowsExitSignalProbe.java .build-cache/M10.5.9
import io.jharness.process.ProcessExit;
import java.nio.file.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

class WindowsExitSignalProbe {
    public static void main(String[] arguments) throws Exception {
        if (!System.getProperty("os.name").startsWith("Windows")) throw new IllegalStateException("Windows-only diagnostic");
        Path root=Path.of(arguments[0]).toRealPath();
        Path cwd=Files.createTempDirectory(root,"owned-java-exit-");
        var process=new ProcessBuilder(Path.of(System.getenv("SystemRoot"),"System32","cmd.exe").toString(),"/d","/c","echo ready & set /p value=").directory(cwd.toFile()).start();
        try {
            if(!new BufferedReader(new InputStreamReader(process.getInputStream())).readLine().trim().equals("ready"))throw new AssertionError("not ready");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            process.destroyForcibly();
            while(process.isAlive()&&System.nanoTime()<deadline)Thread.onSpinWait();
            boolean earlyReleased;
            try {Files.delete(cwd);earlyReleased=true;}catch(IOException locked){earlyReleased=false;}
            boolean confirmed=ProcessExit.await(process,deadline);
            if(!confirmed)throw new AssertionError("termination signal not confirmed within original deadline");
            if(Files.exists(cwd))Files.delete(cwd);
            System.out.println("{\"exitCode\":"+process.exitValue()+",\"directoryReleasedByExitCode\":"+earlyReleased+",\"ownedSignalConfirmed\":"+confirmed+",\"directoryReleasedAfterSignal\":"+!Files.exists(cwd)+"}");
        } finally {
            if(process.isAlive())process.destroyForcibly();
            process.waitFor();
            process.getOutputStream().close();process.getInputStream().close();process.getErrorStream().close();
            if(Files.exists(cwd))Files.delete(cwd);
        }
    }
}
