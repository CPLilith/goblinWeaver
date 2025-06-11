package com.cifre.sap.su.goblinWeaver.weaver.addedValue;


import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Iterator;

import com.cifre.sap.su.goblinWeaver.graphDatabase.GraphDatabaseInterface;
import com.cifre.sap.su.goblinWeaver.graphDatabase.GraphDatabaseSingleton;
import com.cifre.sap.su.goblinWeaver.graphEntities.InternGraph;
import com.cifre.sap.su.goblinWeaver.graphEntities.ValueObject;

public class SbomSyftAll extends AbstractAddedValue<String> {

    public SbomSyftAll(String nodeId) {
        super(nodeId);
    }

    @Override
    public AddedValueEnum getAddedValueEnum(){
        return AddedValueEnum.SBOMSYFTALL;
    }

    @Override
    public void computeValue() throws IOException, InterruptedException{
        value = getSbomFromGav(nodeId);
    }

    private String getSbomFromGav(String gav) throws IOException, InterruptedException {
        String[] splitedGav = gav.split(":");
        if (splitedGav.length == 3) {
            String groupId = splitedGav[0];
            String artifactId = splitedGav[1];
            String releaseVersion = splitedGav[2];

            String groupPath = groupId.replace('.', '/');
            String jarUrl = String.format("https://repo1.maven.org/maven2/%s/%s/%s/%s-%s.jar", groupPath, artifactId, releaseVersion, artifactId, releaseVersion);
            String pomUrl = String.format("https://repo1.maven.org/maven2/%s/%s/%s/%s-%s.pom", groupPath, artifactId, releaseVersion, artifactId, releaseVersion);

            boolean vide = false;
            Path tempDir = Files.createTempDirectory("sbom-" + artifactId + "-" + releaseVersion);
            Path jarPath = tempDir.resolve("jar.jar");
            Path pomPath = tempDir.resolve("pom.xml");
            Path sbomPath = tempDir.resolve("sbom.json");

            try {
                try (InputStream jarIn = tryDownload(jarUrl)) {
                    if (jarIn != null) {
                        Files.copy(jarIn, jarPath, StandardCopyOption.REPLACE_EXISTING);
                        vide = true;
                    }
                }

                try (InputStream pomIn = tryDownload(pomUrl)) {
                    if (pomIn != null) {
                        Files.copy(pomIn, pomPath, StandardCopyOption.REPLACE_EXISTING);
                        vide = true;
                    }
                }

                if (!vide) {
                    return "ERROR: Pas de jar et pom";
                }

                runCommand("syft dir:. --scope all-layers -o cyclonedx-json > sbom.json", tempDir.toFile());

                GraphDatabaseInterface gdb = GraphDatabaseSingleton.getInstance();
                String nodeId = gav + ":SBOMSYFTALL";
                InternGraph graph = gdb.executeQuery(gdb.getQueryDictionary().getSbomValue(nodeId));
                System.out.println("Nombre de valeurs retournées : " + graph.getGraphValues().size());
                for (ValueObject val : graph.getGraphValues()) {
                    System.out.println("Valeur brute dans graph : " + val.getValue());
                }

                String oldSbom = "";
                Iterator<ValueObject> valueIterator = graph.getGraphValues().iterator();
                if(valueIterator.hasNext()) {
                    oldSbom = valueIterator.next().getValue();
                }

                String newSbom = Files.readString(sbomPath);

                String mergedSbom = oldSbom + "testestest\n" + newSbom;

                return mergedSbom;

            } finally {
                deleteDirectoryRecursively(tempDir.toFile());
            }
        }
        return "ERROR: Mauvais GAV";
    }

    private InputStream tryDownload(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return conn.getInputStream();
            } else {
                conn.disconnect();
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static void runCommand(String command, File workingDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder();
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            pb.command("cmd.exe", "/c", command);
        } else {
            pb.command("bash", "-c", command);
        }
        pb.directory(workingDir);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null)
                System.out.println(line);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0)
            throw new RuntimeException("Échec de la commande : " + command);
    }

    private static void deleteDirectoryRecursively(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteDirectoryRecursively(child);
            }
        }
        file.delete();
    }

    @Override
    public String stringToValue(String jsonString) {
        return String.valueOf(jsonString);
    }

    @Override
    public String valueToString(String value) {
        return String.valueOf(value);
    }
    
}
