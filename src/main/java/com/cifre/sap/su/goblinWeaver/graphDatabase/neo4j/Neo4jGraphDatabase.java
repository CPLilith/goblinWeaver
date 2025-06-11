package com.cifre.sap.su.goblinWeaver.graphDatabase.neo4j;

import com.cifre.sap.su.goblinWeaver.api.entities.ReleaseQueryList;
import com.cifre.sap.su.goblinWeaver.graphDatabase.GraphDatabaseInterface;
import com.cifre.sap.su.goblinWeaver.graphDatabase.QueryDictionary;
import com.cifre.sap.su.goblinWeaver.graphEntities.GraphObject;
import com.cifre.sap.su.goblinWeaver.graphEntities.InternGraph;
import com.cifre.sap.su.goblinWeaver.graphEntities.ValueObject;
import com.cifre.sap.su.goblinWeaver.graphEntities.edges.DependencyEdge;
import com.cifre.sap.su.goblinWeaver.graphEntities.edges.EdgeObject;
import com.cifre.sap.su.goblinWeaver.graphEntities.edges.EdgeType;
import com.cifre.sap.su.goblinWeaver.graphEntities.edges.RelationshipArEdge;
import com.cifre.sap.su.goblinWeaver.graphEntities.nodes.ArtifactNode;
import com.cifre.sap.su.goblinWeaver.graphEntities.nodes.NodeObject;
import com.cifre.sap.su.goblinWeaver.graphEntities.nodes.NodeType;
import com.cifre.sap.su.goblinWeaver.graphEntities.nodes.ReleaseNode;
import com.cifre.sap.su.goblinWeaver.weaver.addedValue.AddedValue;
import com.cifre.sap.su.goblinWeaver.weaver.addedValue.AddedValueEnum;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.neo4j.driver.types.TypeSystem;
import org.neo4j.driver.util.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class Neo4jGraphDatabase implements GraphDatabaseInterface {
    private final Driver driver;
    private final QueryDictionary queryDictionary = new Neo4jQueryDictionary();

    public Neo4jGraphDatabase(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
        //Init index for added values
        try (Session session = driver.session()) {
            session.run("CREATE CONSTRAINT addedValueConstraint IF NOT EXISTS FOR (n:AddedValue) REQUIRE n.id IS UNIQUE");
        }
    }

    public QueryDictionary getQueryDictionary() {
        return queryDictionary;
    }

    @Override
    public InternGraph executeQuery(String query) {
        try (Session session = driver.session()) {
            return treatNeo4jResult(session.run(query));
        }
    }

    @Override
    public InternGraph executeQueryWithParameters(String query, Map<String, Object> parameters) {
        try (Session session = driver.session()) {
            return treatNeo4jResult(session.run(query, parameters));
        }
    }

    private InternGraph treatNeo4jResult(Result result){
        InternGraph graph = new InternGraph();
        while (result.hasNext()) {
            Record record = result.next();
            for (Pair<String, Value> pair : record.fields()) {
                if (pair.value().hasType(TypeSystem.getDefault().NODE())){
                    NodeObject nodeObject = generateNode(pair.value().asNode());
                    if(nodeObject != null){
                        graph.addNode(nodeObject);
                    }
                }
                else if (pair.value().hasType(TypeSystem.getDefault().RELATIONSHIP())){
                    EdgeObject edgeObject = generateRelationship(pair.value().asRelationship());
                    if(edgeObject != null){
                        graph.addEdge(edgeObject);
                    }
                }
                else if (pair.value().hasType(TypeSystem.getDefault().PATH())) {
                    for(GraphObject graphObject : generatePath(pair.value().asPath())){
                        if (graphObject instanceof NodeObject) {
                            graph.addNode((NodeObject) graphObject);
                        } else {
                            graph.addEdge((EdgeObject) graphObject);
                        }
                    }
                }
                else if (pair.value().hasType(TypeSystem.getDefault().LIST())){
                    List<Object> list = pair.value().asList();
                    for (Object item : list) {
                        if (item instanceof Node) {
                            Node node = (Node) item;
                            NodeObject nodeObject = generateNode(node);
                            if(nodeObject != null) {
                                graph.addNode(nodeObject);
                            }
                        }
                        else if (item instanceof Relationship) {
                            Relationship relationship = (Relationship) item;
                            EdgeObject edgeObject = generateRelationship(relationship);
                            if(edgeObject != null) {
                                graph.addEdge(edgeObject);
                            }
                        }
                    }
                }
                else{
                    graph.addValue(new ValueObject(pair.key(), pair.value().toString().replaceAll("[\"]","")));
                }
            }
        }
        return graph;
    }

    @Override
    public Map<String,Map<AddedValueEnum,String>> getNodeAddedValues(List<String> nodeIds, Set<AddedValueEnum> addedValues, NodeType nodeType) {
        Map<String,Map<AddedValueEnum,String>> IdAndAddedValuesMap = new HashMap<>();
        String query = "MATCH (a:AddedValue) WHERE a.id IN $addedValuesIds RETURN a";
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("addedValuesIds", nodeIds.stream()
                .flatMap(nodeId -> addedValues.stream().map(addedValue -> nodeId + ":" + addedValue.toString()))
                .collect(Collectors.toList()));
        try (Session session = driver.session()) {
            Result result = session.run(query, parameters);
            while (result.hasNext()) {
                Record record = result.next();
                Map<AddedValueEnum, String> innerMap = new HashMap<>();
                Pair<String, Value> pair = record.fields().get(0);
                if (pair.value().hasType(TypeSystem.getDefault().NODE())) {
                    innerMap.put(AddedValueEnum.valueOf(pair.value().asNode().get("type").asString()), pair.value().asNode().get("value").asString().replace("\\", ""));
                    String nodeId = pair.value().asNode().get("id").asString();
                    int lastIndex = nodeId.lastIndexOf(':');
                    nodeId = nodeId.substring(0,lastIndex);
                    IdAndAddedValuesMap.computeIfAbsent(nodeId, k -> new HashMap<>()).putAll(innerMap);
                }
            }
        }
        return IdAndAddedValuesMap;
    }

    @Override
    public void addAddedValues(List<AddedValue<?>> computedAddedValues){
        try (Session session = driver.session()) {
            Transaction tx = session.beginTransaction();
            int batch = 0;
            Map<String, Object> parameters = new HashMap<>();
            Set<String> cleanedNodes = new HashSet<>();

            for (AddedValue addedValue : computedAddedValues) {
                String sourceId = addedValue.getNodeId();
                String targetLabel = addedValue.getAddedValueEnum().getTargetNodeType().enumToLabel();

                // Nettoyer une seule fois par sourceId
                if (!cleanedNodes.contains(sourceId)) {
                    parameters.clear();
                    parameters.put("sourceId", sourceId);
                    tx.run(
                        "MATCH (r:" + targetLabel + " {id: $sourceId})-[:addedValues]->(v:AddedValue) " +
                        "DETACH DELETE v",
                        parameters
                    );
                    cleanedNodes.add(sourceId);
                }

                // Ajouter la nouvelle valeur
                batch++;
                parameters.clear();
                parameters.put("sourceId", sourceId);
                parameters.put("addedValueId", sourceId + ":" + addedValue.getAddedValueEnum().toString());
                parameters.put("addedValueType", addedValue.getAddedValueEnum().toString());
                parameters.put("value", addedValue.valueToString(addedValue.getValue()));
                tx.run(
                    "MATCH (r:" + targetLabel + " {id: $sourceId}) " +
                    "CREATE (r)-[:addedValues]->(v:AddedValue {id: $addedValueId, type: $addedValueType, value: $value})",
                    parameters
                );

                if (batch % 10000 == 0) {
                    tx.commit();
                    tx.close();
                    tx = session.beginTransaction();
                    cleanedNodes.clear(); // Important pour les nouveaux lots
                }
            }

            tx.commit();
            tx.close();
        } catch (Exception e) {
            System.out.println("Fail to add added values:\n" + e.getMessage());
        }
    }

    @Override
    public void putOneAddedValueOnGraph(String nodeId, AddedValueEnum addedValueType, String value){
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("nodeId", nodeId);
        parameters.put("addedValueId", nodeId+":"+addedValueType.toString());
        parameters.put("addedValueType", addedValueType.toString());
        parameters.put("value", value);

        String query = "MATCH (r:"+addedValueType.getTargetNodeType().enumToLabel()+" {id:$nodeId}) " +
                "CREATE (r)-[l:addedValues]->(v:AddedValue {id: $addedValueId, type: $addedValueType, value: $value})";
        try (Session session = driver.session()) {
            session.run(query, parameters);
        }
    }

    @Override
    public void removeAddedValuesOnGraph(Set<AddedValueEnum> addedValuesType){
        StringBuilder cypherQuery = new StringBuilder();
        cypherQuery.append("MATCH (n:AddedValue) ")
                .append("WHERE n.type IN [");
        int i = 0;
        for (AddedValueEnum type : addedValuesType) {
            cypherQuery.append("'").append(type).append("'");
            if (++i < addedValuesType.size()) {
                cypherQuery.append(", ");
            }
        }
        cypherQuery.append("] ")
                .append("CALL { WITH n DETACH DELETE n } IN TRANSACTIONS OF 10000 ROWS;");
        try (Session session = driver.session()) {
            session.run(cypherQuery.toString());
        }
    }

    @Override
    public InternGraph getRootedGraph(Set<String> releaseIdList){
        InternGraph rootedGraph = new InternGraph();
        Set<String> releaseToTreat = new HashSet<>(releaseIdList);
        Set<String> visitedRelease = new HashSet<>();
        Map<String, Object> parameters = new HashMap<>();
        String query = "MATCH (a:Artifact)-[re:relationship_AR]->(r:Release)-[d:dependency]->(a2:Artifact)-[re2:relationship_AR]->(target:Release) " +
                "WHERE a.id = $artifactId AND r.id = $releaseId AND d.scope = 'compile' AND target.version=d.targetVersion " +
                "RETURN a,re,r,d,a2,re2,target";
        while (!releaseToTreat.isEmpty()){
            String releaseId = releaseToTreat.iterator().next();
            String[] splitedReleaseId = releaseId.split(":");
            String artifactId = splitedReleaseId[0]+":"+splitedReleaseId[1];
            parameters.put("releaseId",releaseId);
            parameters.put("artifactId",artifactId);
            InternGraph resultGraph = executeQueryWithParameters(query, parameters);
            rootedGraph.mergeGraph(resultGraph);
            visitedRelease.add(releaseId);
            releaseToTreat.remove(releaseId);
            Set<String> newReleaseToTreat = resultGraph.getGraphNodes().stream().filter(node -> node instanceof ReleaseNode).map(NodeObject::getId).collect(Collectors.toSet());
            newReleaseToTreat.removeAll(visitedRelease);
            releaseToTreat.addAll(newReleaseToTreat);
        }
        return rootedGraph;
    }

    @Override
    public InternGraph getReleaseWithLibAndDependencies(String releaseId){
        Map<String, Object> parameters = new HashMap<>();
        String[] splitedGav = releaseId.split(":");
        parameters.put("releaseId",releaseId);
        parameters.put("artifactId",splitedGav[0]+":"+splitedGav[1]);
        String query = "MATCH (a:Artifact)-[re:relationship_AR]->(r:Release) " +
                "WHERE a.id = $artifactId AND r.id = $releaseId " +
                "OPTIONAL MATCH (r)-[d:dependency]->(a2:Artifact)-[re2:relationship_AR]->(target:Release) " +
                "WHERE d.scope = 'compile' AND target.version = d.targetVersion " +
                "RETURN a, re, r, d, a2, re2, target";
        return executeQueryWithParameters(query, parameters);
    }

    @Override
    public InternGraph getArtifactReleasesGraph(String artifactId){
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("artifactId",artifactId);
        String query = "MATCH (a:Artifact)-[e:relationship_AR]->(r:Release) " +
                "WHERE a.id = $artifactId " +
                "RETURN a,e,r";
        return executeQueryWithParameters(query, parameters);
    }

    @Override
    public InternGraph getArtifactSpecificReleasesGraph(String releaseId){
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("releaseId",releaseId);
        String query = "MATCH (a:Artifact)-[e:relationship_AR]->(r:Release) " +
                "WHERE r.id = $releaseId " +
                "RETURN a,e,r";
        return executeQueryWithParameters(query, parameters);
    }

    @Override
    public InternGraph getArtifactNewReleasesGraph(String artifactId, long timestamp){
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("artifactId",artifactId);
        parameters.put("timestamp",timestamp);
        String query = "MATCH (a:Artifact)-[e:relationship_AR]->(r:Release) " +
                "WHERE a.id = $artifactId AND r.timestamp >= $timestamp " +
                "RETURN a,e,r";
        return executeQueryWithParameters(query, parameters);
    }

    @Override
    public InternGraph getAllPossibilitiesGraph(Set<String> artifactIdList){
        InternGraph graphAllPossibilities = new InternGraph();
        Set<String> artifactToTreat = new HashSet<>(artifactIdList);
        Set<String> visitedArtifact = new HashSet<>();
        Map<String, Object> parameters = new HashMap<>();
        String query = "MATCH (a:Artifact)-[e:relationship_AR*]->(r:Release) " +
                "WHERE a.id IN $artifactIdList " +
                "WITH a, r, e MATCH (r)-[d:dependency]->(a2:Artifact) " +
                "WHERE d.scope = 'compile' " +
                "RETURN a,e,r,d, a2";
        while (!artifactToTreat.isEmpty()){
            parameters.put("artifactIdList",artifactToTreat);
            InternGraph resultGraph = executeQueryWithParameters(query, parameters);
            graphAllPossibilities.mergeGraph(resultGraph);
            visitedArtifact.addAll(artifactToTreat);
            Set<String> newArtifactToTreat = resultGraph.getGraphNodes().stream().filter(node -> node instanceof ArtifactNode).map(NodeObject::getId).collect(Collectors.toSet());
            newArtifactToTreat.removeAll(visitedArtifact);
            artifactToTreat.clear();
            artifactToTreat.addAll(newArtifactToTreat);
        }
        return graphAllPossibilities;
    }

    @Override
    public InternGraph getDirectPossibilitiesGraph(Set<String> artifactIdList){
        Map<String, Object> parameters = new HashMap<>();
        String query = "MATCH (a:Artifact)-[e:relationship_AR]->(r:Release) " +
                "WHERE a.id IN $artifactIdList " +
                "RETURN a,e,r";
            parameters.put("artifactIdList", artifactIdList);
        return executeQueryWithParameters(query, parameters);
    }

    @Override
    public InternGraph getDirectNewPossibilitiesGraph(Set<ReleaseQueryList.Release> releaseIdList){
        InternGraph resultGraph = new InternGraph();
        String query = "MATCH (r:Release) WHERE r.id = $releaseId WITH r.timestamp as currentTimestamp "+
                "MATCH (a:Artifact)-[e:relationship_AR]->(r:Release) " +
                "WHERE a.id = $artifactId AND r.timestamp >= currentTimestamp " +
                "RETURN a,e,r";
        Map<String, Object> parameters = new HashMap<>();
        for(ReleaseQueryList.Release release : releaseIdList){
            parameters.put("releaseId", release.getGav());
            parameters.put("artifactId", release.getGa());
            InternGraph graph = executeQueryWithParameters(query, parameters);
            resultGraph.mergeGraph(graph);
        }
        return resultGraph;
    }

    private static NodeObject generateNode(Node neo4jNode){
        NodeType nodeType = NodeType.neo4jLabelToEnum(neo4jNode.labels().iterator().next());
        if(nodeType != null){
            if (nodeType.equals(NodeType.ARTIFACT)){
                return new ArtifactNode(neo4jNode.elementId(), neo4jNode.get("id").asString(), neo4jNode.get("found").asBoolean());
            }
            else if (nodeType.equals(NodeType.RELEASE)){
                return new ReleaseNode(neo4jNode.elementId(), neo4jNode.get("id").asString(), neo4jNode.get("timestamp").asLong(), neo4jNode.get("version").asString());
            }
        }
        return null;
    }

    private static EdgeObject generateRelationship(Relationship neo4jRelationship){
        EdgeType edgeType = EdgeType.neo4jTypeToEnum(neo4jRelationship.type());
        if(edgeType != null) {
            if (edgeType.equals(EdgeType.DEPENDENCY)) {
                return new DependencyEdge(neo4jRelationship.startNodeElementId(), neo4jRelationship.endNodeElementId(),
                        neo4jRelationship.get("targetVersion").asString(), neo4jRelationship.get("scope").asString());
            }
            else if (edgeType.equals(EdgeType.RELATIONSHIP_AR)){
                return new RelationshipArEdge(neo4jRelationship.startNodeElementId(), neo4jRelationship.endNodeElementId());
            }
        }
        return null;
    }

    private static Set<GraphObject> generatePath(Path path){
        Set<GraphObject> resultSet = new HashSet<>();
        for (Node node : path.nodes()){
            resultSet.add(generateNode(node) != null ? generateNode(node) : null);
        }
        for (Relationship relationship : path.relationships()){
            resultSet.add(generateRelationship(relationship) != null ? generateRelationship(relationship) : null);
        }
        return resultSet;
    }
}
