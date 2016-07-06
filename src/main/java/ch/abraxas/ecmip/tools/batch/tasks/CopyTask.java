package ch.abraxas.ecmip.tools.batch.tasks;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Copy task.
 *
 * @author <a href="mailto:m.bieri@gmx.net">Michael Bieri</a>
 * @version 0.1
 * @since 05.07.2016
 */

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Autowired}))
@Component
public class CopyTask {

    private static final String PREFIX = "CopyTask";

    private static final String COPY_RUNS = PREFIX + ".executions";
    private static final String COPY_FAILED = PREFIX + ".failed";
    private static final String COPY_FILES = PREFIX + ".files";

    @Getter
    @Setter(AccessLevel.PACKAGE)
    @Value("${" + PREFIX + ".input}")
    private String inputDirectoryName;

    @Getter
    @Setter(AccessLevel.PACKAGE)
    @Value("${" + PREFIX + ".target}")
    private String targetDirectoryName;

    @Getter
    @Setter(AccessLevel.PACKAGE)
    @Value("${" + PREFIX + ".backup}")
    private String backupDirectoryName;

    @Getter
    @Setter(AccessLevel.PACKAGE)
    @Value("${" + PREFIX + ".pattern}")
    private String okFilePattern;

    private Pattern compiledPattern;

    @NonNull
    private final CounterService counterService;

    private Path inputDir;

    private Path targetDir;

    private Path backupDir;

    @PostConstruct
    public void onPostConstruct() {
        log.info("Copy process initialized:");
        log.info("- input :  {}", inputDirectoryName);
        log.info("- target:  {}", targetDirectoryName);
        log.info("- backup:  {}", backupDirectoryName);
        log.info("- pattern: {}", okFilePattern);

        compiledPattern = Pattern.compile(okFilePattern);

        inputDir = Paths.get(inputDirectoryName);
        targetDir = Paths.get(targetDirectoryName);
        backupDir = Paths.get(backupDirectoryName);

        validateDirectory(inputDir);
        validateDirectory(targetDir);
        validateDirectory(backupDir);
    }

    private void validateDirectory(final Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.warn(String.format("[%s] does not exist or is no directory", dir.toString()));
        }
    }

    @Scheduled(initialDelay = 10_000L, fixedDelay = 5_000L)
    public void copyBatches() {
        counterService.increment(COPY_RUNS);
        final Instant start = Instant.now();
        log.trace("start service execution: {}", start);

        try {
            final Stream<Path> filesToProcess = prepareFileStream(inputDir);
            filesToProcess.forEach(this::copyAndMoveFile);
        } catch (Exception e) {
            counterService.increment(COPY_FAILED);
            log.error("error walking file tree: " + e.getMessage(), e);
        }

        final Instant end = Instant.now();
        log.trace("end service execution: {}", end);
        log.debug("execution took: {}ms", Duration.between(start, end).getNano() / 1_000_000L);
    }

    private void copyAndMoveFile(final Path file) {
        try {
            FileUtils.copyFileToDirectory(file.toFile(), backupDir.toFile(), true);
            FileUtils.moveFileToDirectory(file.toFile(), targetDir.toFile(), false);
        } catch (IOException e) {
            log.error("error during file operation: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private Stream<Path> prepareFileStream(final Path inputDir) throws IOException {
        return StreamSupport.stream(Files.newDirectoryStream(inputDir, this::matchesPattern).spliterator(), false)
            .peek(path -> log.info("--- processing ok file: {}", FilenameUtils.getName(path.toString())))
            .flatMap(this::mapToBatchStream)
            .peek(path -> log.debug("processing file: {}", FilenameUtils.getName(path.toString())))
            .peek(__ -> counterService.increment(COPY_FILES));
    }

    private boolean matchesPattern(final Path file) {
        return matchesPattern(compiledPattern, file);
    }

    private boolean matchesPattern(final Pattern batchFilePattern, final Path file) {
        final String fileName = FilenameUtils.getName(file.toString());
        return batchFilePattern.matcher(fileName).matches();
    }

    private Stream<Path> mapToBatchStream(Path okFilePath) {
        try {
            final String baseName = stripAllExtensions(FilenameUtils.getName(okFilePath.toString()));
            final Pattern batchFilePattern = Pattern.compile(baseName + "(_\\p{Digit}+)?\\.zip$");
            final Stream<Path> batchFiles = StreamSupport.stream(Files.newDirectoryStream(inputDir, file -> matchesPattern(batchFilePattern, file)).spliterator(), false);
            return Stream.concat(batchFiles, Stream.of(okFilePath));
        } catch (Exception e) {
            log.error("error creating batch file stream: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private String stripAllExtensions(String fileName) {
        if (fileName.indexOf('.') == -1) {
            return fileName;
        }
        return stripAllExtensions(FilenameUtils.getBaseName(fileName));
    }

}
