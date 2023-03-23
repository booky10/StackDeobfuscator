package dev.booky.stackdeobf.mappings.types;
// Created by booky10 in StackDeobfuscator (22:08 23.03.23)

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

public class PackagedMappingType extends AbstractMappingType {

    protected final URI metaUri;
    protected final String mappingUri;

    protected Path path;
    protected MemoryMappingTree mappings;

    public PackagedMappingType(String name, String repo, String groupId, String artifactId, String classifier) {
        super(name);

        groupId = groupId.replace('.', '/');
        String baseUri = repo + "/" + groupId + "/" + artifactId + "/";

        this.metaUri = URI.create(baseUri + "maven-metadata.xml");
        this.mappingUri = baseUri + "$VER/" + artifactId + "-$VER-" + classifier + ".jar";
    }

    @Override
    protected void downloadMappings(Path cacheDir) throws IOException {
        String version = this.fetchLatestVersion(MC_VERSION);
        this.path = cacheDir.resolve(this.name + "_" + version + ".txt");
        if (Files.exists(this.path)) {
            return;
        }

        Path jarPath = cacheDir.resolve(this.name + "_" + version + ".jar");
        if (Files.notExists(jarPath)) {
            URI uri = URI.create(this.mappingUri.replace("$VER", version));
            this.download(uri, jarPath);
        }

        // the mappings are inside a jar file, they need to be extracted
        try (FileSystem jar = FileSystems.newFileSystem(jarPath)) {
            Path mappingsPath = jar.getPath("mappings/mappings.tiny");
            Files.copy(mappingsPath, this.path);
        }
    }

    private String fetchLatestVersion(String mcVersion) throws IOException {
        HttpResponse<InputStream> resp = HTTP.sendAsync(HttpRequest.newBuilder(this.metaUri).build(), HttpResponse.BodyHandlers.ofInputStream()).join();

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

        throw new IllegalArgumentException("Can't find " + this.name + " mappings for minecraft version " + mcVersion);
    }

    @Override
    protected void parseMappings() throws IOException {
        MemoryMappingTree inter2named = new MemoryMappingTree();
        MappingReader.read(this.path, MappingFormat.TINY_2, inter2named);
        this.mappings = inter2named;
    }

    @Override
    protected void visitMappings(MappingVisitor visitor) throws IOException {
        this.mappings.accept(visitor);
    }
}
