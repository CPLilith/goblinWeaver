package com.cifre.sap.su.goblinWeaver.api.controllers;

import com.cifre.sap.su.goblinWeaver.api.entities.ArtifactQuery;
import com.cifre.sap.su.goblinWeaver.graphDatabase.GraphDatabaseInterface;
import com.cifre.sap.su.goblinWeaver.graphDatabase.GraphDatabaseSingleton;
import com.cifre.sap.su.goblinWeaver.graphEntities.InternGraph;
import com.cifre.sap.su.goblinWeaver.weaver.Weaver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;

import org.json.simple.JSONObject;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Artifacts")
public class ArtifactController {

    @Operation(
            description = "Get a specific artifact from groupId:ArtifactId with added values",
            summary = "Get a specific artifact from GA"
    )
    @PostMapping("/artifact")
    public JSONObject getSpecificArtifact(@RequestBody ArtifactQuery artifactQuery) throws IOException, InterruptedException {
        GraphDatabaseInterface gdb = GraphDatabaseSingleton.getInstance();
        InternGraph graph = gdb.executeQuery(gdb.getQueryDictionary().getSpecificArtifactQuery(artifactQuery.toString()));
        Weaver.weaveGraph(graph, artifactQuery.getAddedValues());
        return graph.getJsonGraph();
    }

    @Operation(
            description = "Get all releases of an artifact from groupId:ArtifactId with added values",
            summary = "Get all releases of an artifact from GA"
    )
    @PostMapping("/artifact/releases")
    public JSONObject getArtifactReleases(@RequestBody ArtifactQuery artifactQuery) throws IOException, InterruptedException {
        GraphDatabaseInterface gdb = GraphDatabaseSingleton.getInstance();
        InternGraph graph = gdb.executeQuery(gdb.getQueryDictionary().getArtifactReleasesQuery(artifactQuery.toString()));
        Weaver.weaveGraph(graph, artifactQuery.getAddedValues());
        return graph.getJsonGraph();
    }
}
