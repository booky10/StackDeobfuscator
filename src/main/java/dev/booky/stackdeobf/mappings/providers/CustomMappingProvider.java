package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (17:42 23.03.23)

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CustomMappingProvider extends AbstractMappingProvider {

    private final Path path;
    private final MappingFormat format;
    private MemoryMappingTree mappings;

    public CustomMappingProvider(Path path, MappingFormat format) {
        super("custom");
        this.path = path;
        this.format = format;
    }

    @Override
    protected void downloadMappings(Path cacheDir) throws IOException {
        if (Files.notExists(this.path)) {
            throw new FileNotFoundException("Custom mappings file at " + this.path + " doesn't exist");
        }
    }

    @Override
    protected void parseMappings() throws IOException {
        MemoryMappingTree mappings = new MemoryMappingTree();
        MappingReader.read(this.path, this.format, mappings);

        if (!"intermediary".equals(mappings.getSrcNamespace())) {
            if (!mappings.getDstNamespaces().contains("intermediary")) {
                throw new IllegalStateException("Custom mappings don't map from 'intermediary'");
            }

            // try to switch to have intermediary as source
            MemoryMappingTree switchedMappings = new MemoryMappingTree();
            mappings.accept(new MappingSourceNsSwitch(switchedMappings, "intermediary"));
            mappings = switchedMappings;
        }

        if (!mappings.getDstNamespaces().contains("named")) {
            throw new IllegalStateException("Custom mappings don't map to 'named'");
        }

        // 'named' needs to be the first destination namespace
        if (mappings.getDstNamespaces().indexOf("named") != 0) {
            List<String> orderedNamespaces = new ArrayList<>(mappings.getDstNamespaces());
            orderedNamespaces.remove("named");
            orderedNamespaces.add(0, "named");

            MemoryMappingTree reorderedMappings = new MemoryMappingTree();
            mappings.accept(new MappingDstNsReorder(reorderedMappings, orderedNamespaces));
            mappings = reorderedMappings;
        }

        this.mappings = mappings;
    }

    @Override
    protected void visitMappings(MappingVisitor visitor) throws IOException {
        this.mappings.accept(visitor);
    }

    public Path getPath() {
        return this.path;
    }

    public MappingFormat getFormat() {
        return this.format;
    }
}
