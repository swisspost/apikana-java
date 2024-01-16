package org.swisspush.apikana;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.yaml.snakeyaml.Yaml;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

public abstract class AbstractApikanaMojo extends AbstractMojo {
    protected final static String OUTPUT = "target/node/dist";

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject mavenProject;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Component
    private BuildPluginManager pluginManager;

    @Component
    protected MavenProjectHelper projectHelper;

    @Component
    private RepositorySystem repositorySystem;

    @Component
    private ProjectBuilder projectBuilder;

    /**
     * The working directory for node.
     */
    @Parameter(defaultValue = "target/node", property = "apikana.node-working-dir")
    private File nodeWorkingDir;

    /**
     * The directory containing css files and images to style the swagger GUI.
     */
    @Parameter(defaultValue = "src/style", property = "apikana.style")
    protected String style;

    /**
     * <p>Override the API version</p>
     *
     * <p>Default: project.version</p>
     */
    @Parameter(defaultValue = "${project.version}", property = "apikana.apiversion")
    protected String apiVersion;

    /**
     * <p>Override apikana defaults</p>
     *
     * <p>Default: 0.0.0</p>
     */
    @Parameter(defaultValue = "0.0.80", property = "apikana.defaults.version")
    protected String apikanaDefaultsVersion;

    /**
     * The java package that should be used.
     */
    @Parameter(property = "apikana.java-package")
    protected String javaPackage;

    /**
     * The main API file (yaml or json).
     */
    @Parameter(defaultValue = "src/openapi/api.yaml", property = "apikana.api")
    protected String api;

    /**
     * The typescript version which should be added to package.json
     */
    @Parameter(property = "apikana.typescript.version")
    protected String typescriptVersion;

    /**
     * The scope used to name the package
     */
    @Parameter(property = "apikana.scope")
    protected String scope;

    /**
     * The apikana API type. One of "rest-api", "stream-api", "api".
     */
    @Parameter(defaultValue = "rest-api", property = "apikana.type")
    protected String type;

    /**
     * The organization namespace eg. example.com
     */
    @Parameter(property = "apikana.domain")
    protected String domain;

    /**
     * The full API namespace as required by apikana init
     */
    @Parameter(property = "apikana.namespace")
    protected String namespace;

    /**
     * The API shortname
     */
    @Parameter(property = "apikana.shortname")
    protected String shortname;

    private final ObjectMapper mapper = new ObjectMapper();

    protected void unpackModelDependencies() throws IOException {
        for (final Artifact a : mavenProject.getArtifacts()) {
            JarFile jar = classifiedArtifactJar(a, "sources");
            if (jar != null) {
                final Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    final JarEntry entry = entries.nextElement();
                    copyModel(jar, entry, "", "ts", a.getArtifactId());
                    copyModel(jar, entry, "", "json-schema-v3", a.getArtifactId());
                    copyModel(jar, entry, "", "json-schema-v4", a.getArtifactId());
                    copyModel(jar, entry, "", "style", "");
                }
            }
        }
    }

    protected void unpackStyleDependencies(MavenProject project) throws IOException {
        if (project != null) {
            JarFile jar = classifiedArtifactJar(new ProjectArtifact(project), "style");
            if (jar != null) {
                final Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    final JarEntry entry = entries.nextElement();
                    copyModel(jar, entry, "", "style", "");
                }
            }
            unpackStyleDependencies(project.getParent());
        }
    }

    private JarFile classifiedArtifactJar(Artifact a, String classifier) throws IOException {
        Artifact artifact = classifiedArtifact(a, classifier);
        return artifact == null ? null : new JarFile(artifact.getFile());
    }

    private Artifact classifiedArtifact(Artifact a, String classifier) {
        final ArtifactResolutionRequest req = new ArtifactResolutionRequest();
        req.setArtifact(repositorySystem.createArtifactWithClassifier(a.getGroupId(), a.getArtifactId(), a.getVersion(), "jar", classifier));
        req.setRemoteRepositories(mavenProject.getRemoteArtifactRepositories());
        final ArtifactResolutionResult result = repositorySystem.resolve(req);
        final Iterator<Artifact> iter = result.getArtifacts().iterator();
        return iter.hasNext() ? iter.next() : null;
    }

    private void copyModel(JarFile jar, JarEntry entry, String basePath, String type, String targetDir) throws IOException {
        final String sourceName = (basePath.length() > 0 ? basePath + "/" : "") + type;
        if (!entry.isDirectory() && entry.getName().startsWith(sourceName)) {
            final File modelFile = apiDependencies(type + "/" + targetDir + entry.getName().substring((sourceName).length()));
            modelFile.getParentFile().mkdirs();
            try (final FileOutputStream out = new FileOutputStream(modelFile)) {
                IoUtils.copy(jar.getInputStream(entry), out);
            }
        }
    }

    private void createJson(File file, Consumer<Map<String, Object>> updater) throws IOException {
        final Map<String, Object> json = new LinkedHashMap<>();
        updater.accept(json);
        mapper.writer().withDefaultPrettyPrinter().writeValue(file, json);
    }

    protected File file(String name) {
        return new File(mavenProject.getBasedir(), name);
    }

    protected File target(String name) {
        return new File(mavenProject.getBuild().getDirectory(), name);
    }

    protected File working(String name) {
        return new File(nodeWorkingDir, name);
    }

    protected File apiDependencies(String name) {
        return target("api-dependencies/" + name);
    }

    protected String relative(File base, File f) {
        return base.toPath().relativize(f.toPath()).toString().replace('\\', '/');
    }

    protected void writeProjectProps() throws IOException {
        final Map<String, Object> propectProps = new ProjectSerializer().serialize(mavenProject);
        final File file = working("properties.json");
        file.getParentFile().mkdirs();
        mapper.writeValue(file, propectProps);
    }

    protected boolean isPom() {
        return "pom".equals(mavenProject.getPackaging());
    }

    protected void generatePackageJson(String version) throws IOException {
        createJson(working("package.json"), pack -> {
            Map<String, Object> apiSpec = parseApiSpec();
            pack.put("name", getName());
            pack.put("version", apiVersion);
            pack.put("description", getDescription(apiSpec));

            final Map<String, String> scripts = (Map) pack.merge("scripts", new LinkedHashMap<>(), (oldVal, newVal) -> oldVal);
            scripts.put("apikana", "apikana");

            String author = getAuthor(apiSpec);
            if (author != null) {
                pack.put("author", author);
            }

            pack.put("license", "Apache-2.0");

            final Map<String, String> devDependencies = (Map) pack.merge("devDependencies", new LinkedHashMap<>(), (oldVal, newVal) -> oldVal);
            devDependencies.put("apikana", version);
            devDependencies.put("apikana-defaults", apikanaDefaultsVersion);
            devDependencies.put("typescript", typescriptVersion);

            final Map<String, Object> customConfig = (Map) pack.merge("customConfig", new LinkedHashMap<>(), (oldVal, newVal) -> oldVal);
            customConfig.put("type", type);
            customConfig.put("domain", domain);
            if (author != null) {
                customConfig.put("author", author);
            }
            customConfig.put("namespace", namespace);

            if (shortname != null && shortname.length() > 0) {
                customConfig.put("shortName", shortname);
            }
            customConfig.put("projectName", getProjectName());
            customConfig.put("title", getTitle(apiSpec));

            final List<String> plugins = new ArrayList<>();
            plugins.add("maven");
            plugins.add("readme");

            customConfig.put("plugins", plugins);
            customConfig.put("javaPackage", javaPackage);
            customConfig.put("mavenGroupId", mavenProject.getGroupId());

            final Map<String, Object> avro = (Map) customConfig.merge("avro", new LinkedHashMap<>(), (oldVal, newVal) -> oldVal);
            avro.put("enumAsString", true);
            customConfig.put("avro", avro);
            pack.put("customConfig", customConfig);
        });
    }

    private String getTitle(Map<String, Object> apiSpec) {
        Map<String, Object> info = (Map<String, Object>) apiSpec.get("info");
        return (String) info.get("title");
    }

    private String getAuthor(Map<String, Object> apiSpec) {
        Map<String, Object> info = (Map<String, Object>) apiSpec.get("info");
        Map<String, Object> contact = (Map<String, Object>) info.get("contact");

        if (contact == null) {
            return null;
        }

        StringBuilder authorBuilder = new StringBuilder();

        if (contact.get("name") != null) {
            authorBuilder.append(contact.get("name"));
        }

        if (contact.get("email") != null) {
            if (authorBuilder.length() > 0) {
                authorBuilder.append(" <")
                        .append(contact.get("email"))
                        .append(">");
            } else {
                authorBuilder.append(contact.get("email"));
            }
        }

        if (contact.get("url") != null) {
            if (authorBuilder.length() > 0) {
                authorBuilder.append(" (")
                        .append(contact.get("url"))
                        .append(")");
            } else {
                authorBuilder.append(contact.get("url"));
            }
        }

        return authorBuilder.toString();
    }

    private Map<String, Object> parseApiSpec() {
        try {
            String basedir = mavenProject.getBasedir().getAbsolutePath();
            File apiSpecFile = new File(mavenProject.getBasedir(), api);
            String apiSpecFilePath = apiSpecFile.getAbsolutePath();

            getLog().info("basedir: " + basedir);
            getLog().info("api: " + api);
            getLog().info("apiSpecFile: " + apiSpecFilePath);

            if (api.endsWith(".json")) {
                return parseJsonApiSpec(apiSpecFile);
            } else {
                return parseYamlApiSpec(apiSpecFile);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> parseYamlApiSpec(File yamlSpecFile) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = Files.newInputStream(yamlSpecFile.toPath())) {
            return yaml.load(inputStream);
        }
    }

    private Map<String, Object> parseJsonApiSpec(File jsonSpecFile) throws IOException {
        return mapper.readValue(jsonSpecFile, Map.class);
    }

    private String getDescription(Map<String, Object> apiSpec) {
        Map<String, Object> info = (Map<String, Object>) apiSpec.get("info");
        return (String) info.get("description");
    }

    private String getProjectName() {
        return String.join("-", this.shortname, this.type);
    }

    private String getName() {
        StringBuilder nameBuilder = new StringBuilder();

        if (this.domain != null && this.domain.length() > 0) {
            List<String> domainParts = new ArrayList<>(Arrays.asList(this.domain.split("\\.")));
            Collections.reverse(domainParts);
            String nodeDomain = String.join("-", domainParts);
            nameBuilder.append("@").append(nodeDomain);
        }

        if (this.namespace != null && this.namespace.length() > 0) {
            String nodeNamespace = this.namespace
                .replace(".", "-")
                .replace("-" + this.shortname, "");

            if (nameBuilder.length() == 0) {
                nameBuilder.append("@");
            } else {
                nameBuilder.append("-");
            }
            nameBuilder.append(nodeNamespace).append("/");
        }

        nameBuilder.append(getProjectName());

        return nameBuilder.toString();
    }

    protected void checkNodeInstalled() throws MojoExecutionException {
        try {
            Process node = new ProcessBuilder("node", "-v").start();
            if (node.waitFor() != 0) {
                throw new IOException();
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Node is not installed on this machine.\n" +
                    "- Set <global>false</false> in this plugin's <configuration> or\n" +
                    "- Install node (https://docs.npmjs.com/getting-started/installing-node)");
        }
    }

    protected ProcessBuilder shellCommand(File workDir, String cmd) {
        getLog().info("Workdir: " + workDir);
        getLog().info("Executing: " + cmd);
        final ProcessBuilder pb = System.getProperty("os.name").toLowerCase().contains("windows")
                ? new ProcessBuilder("cmd", "/c", cmd) : new ProcessBuilder("bash", "-c", cmd);
        return pb.directory(workDir);
    }

    protected void executeFrontend(String goal, Xpp3Dom config) throws MojoExecutionException {
        final File npmrc = file(".npmrc");
        final String rc = npmrc.exists() ? "--userconfig " + npmrc.getAbsolutePath() + " " : "";
        config.addChild(element("workingDirectory", working("").getAbsolutePath()).toDom());
        final Xpp3Dom arguments = config.getChild("arguments");
        if (arguments != null) {
            arguments.setValue(rc + arguments.getValue());
        }
        execute(frontendPlugin(), goal, config);
    }

    private Plugin frontendPlugin() {
        return plugin("com.github.eirslett", "frontend-maven-plugin", "1.3");
    }

    private void execute(Plugin plugin, String goal, Xpp3Dom config) throws MojoExecutionException {
        executeMojo(plugin, goal, config, executionEnvironment(mavenProject, mavenSession, pluginManager));
    }
}
