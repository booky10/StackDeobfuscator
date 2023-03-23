package dev.booky.stackdeobf.mappings.types;
// Created by booky10 in StackDeobfuscator (16:59 23.03.23)

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class YarnMappingType extends AbstractMappingType {

    private Path yarnPath;
    private MemoryMappingTree yarn;

    YarnMappingType() {
    }

    @Override
    protected void downloadMappings(Path cacheDir) throws IOException {
        String yarnVersion = this.fetchLatestYarnVersion(MC_VERSION);
        this.yarnPath = cacheDir.resolve("yarn_" + yarnVersion + ".txt");
        if (Files.exists(this.yarnPath)) {
            return;
        }

        Path yarnJarPath = cacheDir.resolve("yarn_" + yarnVersion + ".jar");
        if (Files.notExists(yarnJarPath)) {
            URI yarnUri = URI.create("https://maven.fabricmc.net/net/fabricmc/yarn/" + yarnVersion + "/yarn-" + yarnVersion + "-v2.jar");
            this.download(yarnUri, yarnJarPath);
        }

        // yarn mappings are inside a jar file, they need to be extracted
        try (FileSystem jar = FileSystems.newFileSystem(yarnJarPath)) {
            Path mappingsPath = jar.getPath("mappings/mappings.tiny");
            Files.copy(mappingsPath, this.yarnPath);
        }
    }

    private String fetchLatestYarnVersion(String mcVersion) throws IOException {
        URI metadataUri = URI.create("https://maven.fabricmc.net/net/fabricmc/yarn/maven-metadata.xml");
        HttpResponse<InputStream> resp = HTTP.sendAsync(HttpRequest.newBuilder(metadataUri).build(), HttpResponse.BodyHandlers.ofInputStream()).join();

        try (InputStream input = resp.body()) {
            Document document;
            try {
                // https://stackoverflow.com/a/14968272
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                document = factory.newDocumentBuilder().parse(input);
            } catch (ParserConfigurationException | SAXException exception) {
                throw new IOException(exception);
            }

            NodeList versions = document.getElementsByTagName("version");
            for (int i = versions.getLength() - 1; i >= 0; i--) {
                String version = versions.item(i).getTextContent();
                if (version.startsWith(mcVersion + "+")) {
                    return version;
                }
            }
        }

        throw new IllegalArgumentException("Can't find yarn mappings for minecraft version " + mcVersion);
    }

    @Override
    protected void parseMappings(Path cacheDir) throws IOException {
        MemoryMappingTree inter2named = new MemoryMappingTree();
        MappingReader.read(this.yarnPath, MappingFormat.TINY_2, inter2named);
        this.yarn = inter2named;
    }

    @Override
    protected void visitMappings(MappingVisitor visitor) throws IOException {
        this.yarn.accept(visitor);
    }
}
