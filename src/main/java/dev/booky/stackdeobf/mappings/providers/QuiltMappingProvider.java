package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (22:06 23.03.23)

import com.google.common.base.Preconditions;
import dev.booky.stackdeobf.http.HttpUtil;
import dev.booky.stackdeobf.util.CompatUtil;
import dev.booky.stackdeobf.util.MavenArtifactInfo;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class QuiltMappingProvider extends BuildBasedMappingProvider {

    private static final String REPO_URL = System.getProperty("stackdeobf.quilt.repo-url", "https://maven.quiltmc.org/repository/release");

    private static final MavenArtifactInfo MAPPINGS_ARTIFACT = MavenArtifactInfo.parse(REPO_URL,
            System.getProperty("stackdeobf.quilt.mappings-artifact", "org.quiltmc:quilt-mappings:" +
                    (CompatUtil.WORLD_VERSION >= 3120 ? "intermediary-v2" : "v2")));
    private static final MavenArtifactInfo HASHED_MAPPINGS_ARTIFACT = MavenArtifactInfo.parse(REPO_URL,
            System.getProperty("stackdeobf.quilt.mappings-artifact", "org.quiltmc:hashed"));

    // quilt mappings below 1.19.2 require special handling
    //
    // they are using their hashed inbetween step and didn't publish
    // intermediary mapped versions below 1.19.2, so more steps are
    // needed for converting hashed mappings to intermediary mappings
    private final IntermediaryMappingProvider intermediary = new IntermediaryMappingProvider();

    private Path hashedPath;
    private MemoryMappingTree hashedMappings;

    public QuiltMappingProvider() {
        super("quilt", MAPPINGS_ARTIFACT);
        Preconditions.checkState(CompatUtil.WORLD_VERSION >= 2975, "Quilt mappings are only supported for 1.18.2 and higher");
    }

    @Override
    protected CompletableFuture<Void> downloadMappings0(Path cacheDir, Executor executor) {
        CompletableFuture<Void> future = super.downloadMappings0(cacheDir, executor);

        if (CompatUtil.WORLD_VERSION >= 3120) {
            return future; // 1.19.2+
        }

        // see comment at field declaration for reason
        future = future.thenCompose($ -> this.intermediary.downloadMappings0(cacheDir, executor));

        this.hashedPath = cacheDir.resolve(this.name + "_" + CompatUtil.VERSION_ID + "_hashed.gz");
        if (Files.exists(this.hashedPath)) {
            CompatUtil.LOGGER.info("Hashed {} mappings for {} are already downloaded", this.name, CompatUtil.VERSION_ID);
            return future;
        }

        URI hashedUri = HASHED_MAPPINGS_ARTIFACT.buildUri(CompatUtil.VERSION_ID, "jar");
        CompatUtil.LOGGER.info("Downloading hashed {} mappings for {}...", this.name, CompatUtil.VERSION_ID);

        return future.thenCompose($ -> HttpUtil.getAsync(hashedUri, executor).thenAccept(jarBytes -> {
            byte[] mappingBytes = this.extractPackagedMappings(jarBytes);
            try (OutputStream fileOutput = Files.newOutputStream(this.hashedPath);
                 GZIPOutputStream gzipOutput = new GZIPOutputStream(fileOutput)) {
                gzipOutput.write(mappingBytes);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }));
    }

    @Override
    protected CompletableFuture<Void> parseMappings0(Executor executor) {
        CompletableFuture<Void> future = super.parseMappings0(executor);

        if (CompatUtil.WORLD_VERSION >= 3120) {
            return future; // 1.19.2+
        }

        return future.thenCompose($ -> this.intermediary.parseMappings0(executor)).thenRun(() -> {
            MemoryMappingTree mappings = new MemoryMappingTree();

            try (InputStream fileInput = Files.newInputStream(this.hashedPath);
                 GZIPInputStream gzipInput = new GZIPInputStream(fileInput);
                 Reader reader = new InputStreamReader(gzipInput)) {
                MappingReader.read(reader, MappingFormat.TINY_2, mappings);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }

            this.hashedMappings = mappings;
        });
    }

    @Override
    protected CompletableFuture<Void> visitMappings0(MappingVisitor visitor, Executor executor) {
        if (CompatUtil.WORLD_VERSION >= 3120) {
            return super.visitMappings0(visitor, executor); // 1.19.2+
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // source names need to be mapped back through hashed mappings to obfuscated names,
                // these then will get mapped to intermediary
                this.mappings.accept(new HashedVisitor(visitor));
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
            return null;
        }, executor);
    }

    // basically the same as MojangMappingProvider$Visitor, but with one extra step
    private final class HashedVisitor extends ForwardingMappingVisitor {

        private MappingTree.ClassMapping hashedClass;
        private MappingTree.ClassMapping intermediaryClass;

        private HashedVisitor(MappingVisitor next) {
            super(next);
        }

        @Override
        public boolean visitClass(String srcName /* hashed */) throws IOException {
            // lookup hashed class mapping by inputting the source name into the destination
            this.hashedClass = QuiltMappingProvider.this.hashedMappings.getClass(srcName, 0);
            if (this.hashedClass == null) {
                return false;
            }

            // lookup intermediary class mapping by inputting the source name of the hashedClass into the source name of intermediary
            this.intermediaryClass = QuiltMappingProvider.this.intermediary.getMappings().getClass(this.hashedClass.getSrcName());
            if (this.intermediaryClass == null) {
                return false;
            }

            return super.visitClass(this.intermediaryClass.getDstName(0));
        }

        @Override
        public boolean visitMethod(String srcName, String srcDesc) throws IOException {
            // do the same as above again
            MappingTree.MethodMapping hashedMethod = this.hashedClass.getMethod(srcName, srcDesc, 0);
            if (hashedMethod == null) {
                return false;
            }

            MappingTree.MethodMapping intermediaryMethod = this.intermediaryClass.getMethod(hashedMethod.getSrcName(), hashedMethod.getSrcDesc());
            if (intermediaryMethod == null) {
                return false;
            }

            return super.visitMethod(intermediaryMethod.getDstName(0), intermediaryMethod.getDstDesc(0));
        }

        @Override
        public boolean visitField(String srcName, String srcDesc) throws IOException {
            // and again
            MappingTree.FieldMapping hashedField = this.hashedClass.getField(srcName, srcDesc, 0);
            if (hashedField == null) {
                return false;
            }

            MappingTree.FieldMapping intermediaryField = this.intermediaryClass.getField(hashedField.getSrcName(), hashedField.getSrcDesc());
            if (intermediaryField == null) {
                return false;
            }

            return super.visitField(intermediaryField.getDstName(0), intermediaryField.getDstDesc(0));
        }
    }
}
