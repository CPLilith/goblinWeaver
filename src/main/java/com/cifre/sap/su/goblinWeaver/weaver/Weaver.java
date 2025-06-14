package com.cifre.sap.su.goblinWeaver.weaver;

import com.cifre.sap.su.goblinWeaver.graphDatabase.GraphDatabaseSingleton;
import com.cifre.sap.su.goblinWeaver.graphEntities.InternGraph;
import com.cifre.sap.su.goblinWeaver.graphEntities.nodes.NodeObject;
import com.cifre.sap.su.goblinWeaver.graphEntities.nodes.NodeType;
import com.cifre.sap.su.goblinWeaver.weaver.addedValue.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

public class Weaver {

    public static void weaveGraph (InternGraph graph, Set<AddedValueEnum> addedValues) throws IOException, InterruptedException{
        if(addedValues.isEmpty()){
            return;
        }
        List<AddedValue<?>> computedAddedValues = new ArrayList<>();
        for(NodeType nodeType : NodeType.values()){
            Set<AddedValueEnum> nodeTypeAddedValues = addedValues.stream().filter(a -> a.getTargetNodeType().equals(nodeType)).collect(Collectors.toSet());
            if(!nodeTypeAddedValues.isEmpty()){
                for (List<NodeObject> nodeBatch : nodeIdToBatch(graph, nodeType)) {
                    Map<String, Map<AddedValueEnum, String>> resolvedNodeAddedValues = GraphDatabaseSingleton.getInstance().getNodeAddedValues(nodeBatch.stream().map(NodeObject::getId).toList(), addedValues, nodeType);
                    computedAddedValues.addAll(fillNodeAddedValues(nodeBatch, nodeTypeAddedValues, resolvedNodeAddedValues));
                    GraphDatabaseSingleton.getInstance().addAddedValues(computedAddedValues);
                    computedAddedValues.clear();
                }
            }
        }
    }

    private static List<List<NodeObject>> nodeIdToBatch(InternGraph graph, NodeType type) {
        int batchSize = 100000;
        List<List<NodeObject>> batches = new ArrayList<>();
        int i = 0;
        List<NodeObject> currentBatch = new ArrayList<>();
        List<NodeObject> nodeObjectsTyped = graph.getGraphNodes().stream().filter(node -> node.getType().equals(type)).toList();
        for (NodeObject node : nodeObjectsTyped) {
            currentBatch.add(node);
            if (++i % batchSize == 0 || i == nodeObjectsTyped.size()) {
                batches.add(new ArrayList<>(currentBatch));
                currentBatch.clear();
            }
        }
        return batches;
    }

    private static List<AddedValue<?>> fillNodeAddedValues(List<NodeObject> nodes, Set<AddedValueEnum> nodeTypeAddedValues, Map<String,Map<AddedValueEnum,String>> resolvedNodeAddedValues) throws IOException, InterruptedException {
        List<AddedValue<?>> computedAddedValues = new ArrayList<>();
        for (NodeObject node : nodes){
            for (AddedValueEnum addedValueEnum : nodeTypeAddedValues) {
                try {
                    
                    AddedValue<?> addedValue = addedValueEnum.getAddedValueClass().getDeclaredConstructor(String.class).newInstance(node.getId());
                    
                    if (addedValueEnum == AddedValueEnum.SBOMSYFTECRASE /*|| addedValueEnum == AddedValueEnum.SBOMSMPECRASE*/) {
                        addedValue.computeValue();
                        if (!addedValueEnum.isAggregatedValue()) {
                            computedAddedValues.add(addedValue);
                        }
                        node.addAddedValue(addedValue);
                        break;
                    }

                    if (addedValueEnum == AddedValueEnum.SBOMSYFTALL /*|| addedValueEnum == AddedValueEnum.SBOMSMPALL*/) {
                        
                        addedValue.computeValue();

                        if (!addedValueEnum.isAggregatedValue()) {
                            computedAddedValues.add(addedValue);
                        }
                        
                        node.addAddedValue(addedValue);
                        break;
                    }

                    // If addedValue is present on graph
                    if (resolvedNodeAddedValues.containsKey(node.getId()) && resolvedNodeAddedValues.get(node.getId()).containsKey(addedValueEnum)) {
                        addedValue.setValue(resolvedNodeAddedValues.get(node.getId()).get(addedValueEnum));
                    } else {
                        addedValue.computeValue();
                        // Aggregated values put on graph when compute
                        if (!addedValueEnum.isAggregatedValue()) {
                            computedAddedValues.add(addedValue);
                        }
                    }

                    node.addAddedValue(addedValue);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                }
            }
            resolvedNodeAddedValues.remove(node.getId());
        }
        return computedAddedValues;
    }

    private static List<AddedValue<?>> fillNodeAddedValuesOld(InternGraph graph, Set<AddedValueEnum> nodeTypeAddedValues, Map<String,Map<AddedValueEnum,String>> resolvedNodeAddedValues, NodeType nodeType) throws IOException, InterruptedException {
        List<AddedValue<?>> computedAddedValues = new ArrayList<>();
        for (NodeObject node : graph.getGraphNodes().stream().filter(node -> node.getType().equals(nodeType)).collect(Collectors.toSet())){
            String nodeId = node.getId();
            for(AddedValueEnum addedValueEnum : nodeTypeAddedValues){
                try {
                    AddedValue<?> addedValue = addedValueEnum.getAddedValueClass().getDeclaredConstructor(String.class).newInstance(nodeId);
                    // If addedValue is present on graph
                    if(resolvedNodeAddedValues.containsKey(nodeId) && resolvedNodeAddedValues.get(nodeId).containsKey(addedValueEnum)){
                        addedValue.setValue(resolvedNodeAddedValues.get(nodeId).get(addedValueEnum));
                    }
                    else{
                        addedValue.computeValue();
                        if(!addedValueEnum.isAggregatedValue()){
                            computedAddedValues.add(addedValue);
                        }
                    }
                    node.addAddedValue(addedValue);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                }
            }
        }
        return computedAddedValues;
    }
}
