package com.cifre.sap.su.goblinWeaver.weaver.addedValue;

import com.cifre.sap.su.goblinWeaver.graphDatabase.GraphDatabaseInterface;
import com.cifre.sap.su.goblinWeaver.graphDatabase.GraphDatabaseSingleton;
import com.cifre.sap.su.goblinWeaver.graphEntities.InternGraph;
import com.cifre.sap.su.goblinWeaver.graphEntities.ValueObject;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.util.HashMap;
import java.util.Map;

public class Github extends AbstractAddedValue<Map<String, String>> {

    public Github(String nodeId) {
        super(nodeId);
    }

    @Override
    public AddedValueEnum getAddedValueEnum() {
        return AddedValueEnum.GITHUB;
    }

    @Override
    public void computeValue() {
        value = getGithubMetricsFromGav(nodeId);
    }

    @Override
    public Map<String, String> stringToValue(String jsonString) {
        Map<String, String> resultMap = new HashMap<>();
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonString);

            JSONObject githubJson = (JSONObject) jsonObject.get(getAddedValueEnum().getJsonKey());
            resultMap.put("stars", (String) githubJson.get("stars"));
            resultMap.put("forks", (String) githubJson.get("forks"));
            resultMap.put("issues", (String) githubJson.get("issues"));
            resultMap.put("lastCommit", (String) githubJson.get("lastCommit"));
            resultMap.put("url", (String) githubJson.get("url"));
            resultMap.put("collaborators", (String) githubJson.get("collaborators"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultMap;
    }

    @Override
    public String valueToString(Map<String, String> value) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.putAll(value);
        JSONObject finalObject = new JSONObject();
        finalObject.put(getAddedValueEnum().getJsonKey(), value);
        return finalObject.toJSONString().replace("\"", "\\\"");
    }

    protected static Map<String, String> getGithubMetricsFromGav(String nodeId) {
        Map<String, String> githubMetrics = new HashMap<>();
        GraphDatabaseInterface gdb = GraphDatabaseSingleton.getInstance();
        InternGraph graph = gdb.executeQuery(gdb.getQueryDictionary().getGithubMetrics(nodeId));
    
        for (ValueObject value : graph.getGraphValues()) {
            String key = value.getKey();
            String val = value.getValue();
    
            if (key.equals("url")) {
                // Si NULL ou ne contient pas github.com → url = ""
                String url = (val == null || val.equals("NULL")) ? "" : val;
                if (!url.contains("github.com")) {
                    url = "";
                }
                githubMetrics.put("url", url);
            } else {
                String valueNotNull = (val == null || val.equals("NULL")) ? "0" : val;
                githubMetrics.put(key, valueNotNull);
            }
        }
    
        // Valeurs par défaut si champs manquants
        if (!githubMetrics.containsKey("stars")) githubMetrics.put("stars", "0");
        if (!githubMetrics.containsKey("forks")) githubMetrics.put("forks", "0");
        if (!githubMetrics.containsKey("issues")) githubMetrics.put("issues", "0");
        if (!githubMetrics.containsKey("lastCommit")) githubMetrics.put("lastCommit", "0");
        if (!githubMetrics.containsKey("url")) githubMetrics.put("url", "");
        if (!githubMetrics.containsKey("collaborators")) githubMetrics.put("collaborators", "0");
    
        return githubMetrics;
    }
}