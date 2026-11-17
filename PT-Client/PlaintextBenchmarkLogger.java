/*
	Names: Pedro Malavet
	Description: This is the class for benchmarking
*/

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public class PlaintextBenchmarkLogger {

    private static final Path FILE = Path.of("plaintext_benchmark_results.csv");


    public static synchronized void log(String sessionId,String role,String operation,long durationNanos,long bytes) 
	{

        try 
		{

            boolean writeHeader = !Files.exists(FILE) || Files.size(FILE) == 0;

            if (writeHeader) 
			{
                String header = "timestamp," + "session_id," + "role," + "operation," + "duration_ns," + "duration_ms," + "bytes," + "throughput_mbps" + System.lineSeparator();
                Files.writeString(FILE,header,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            }


            double durationMs = durationNanos / 1_000_000.0;


            double throughputMbps = 0.0;


            if (bytes > 0 && durationNanos > 0) {

                double seconds = durationNanos / 1_000_000_000.0;


                throughputMbps = (bytes * 8.0) / seconds/ 1_000_000.0;
            }


            String row = Instant.now() + "," + sessionId + "," + role + "," + operation + "," + durationNanos + "," 
			+ String.format(java.util.Locale.US,"%.6f",durationMs) + "," + bytes + "," + String.format(java.util.Locale.US,"%.6f",throughputMbps) + System.lineSeparator();


            Files.writeString(FILE,row,StandardOpenOption.CREATE,StandardOpenOption.APPEND);

        }
        catch (IOException e) 
		{
            System.err.println("[Benchmark] Could not write result.");
            e.printStackTrace();
        }
    }
}
