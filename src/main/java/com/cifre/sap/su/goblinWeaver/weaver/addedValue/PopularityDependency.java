package com.cifre.sap.su.goblinWeaver.weaver.addedValue;

import java.util.Iterator;

import com.cifre.sap.su.goblinWeaver.graphDatabase.GraphDatabaseInterface;
import com.cifre.sap.su.goblinWeaver.graphDatabase.GraphDatabaseSingleton;
import com.cifre.sap.su.goblinWeaver.graphEntities.InternGraph;
import com.cifre.sap.su.goblinWeaver.graphEntities.ValueObject;

public class PopularityDependency extends AbstractAddedValue<Integer> {

    public PopularityDependency(String nodeId) {
        super(nodeId);
    }

    @Override
    public AddedValueEnum getAddedValueEnum() {
        return AddedValueEnum.POPULARITYDEPENDENCY;
    }

    @Override
    public Integer stringToValue(String jsonString) {
        return Integer.valueOf(jsonString);
    }

    @Override
    public String valueToString(Integer value) {
        return String.valueOf(value);
    }

    @Override
    public void computeValue() {
        super.value = fillPopularityDependency(nodeId);

    }

    protected int fillPopularityDependency(String nodeId){
        int popularityDependency = 0;        
        GraphDatabaseInterface gdb = GraphDatabaseSingleton.getInstance();
        InternGraph graph = gdb.executeQuery(gdb.getQueryDictionary().getPopularityDependency(nodeId));
        for(ValueObject value : graph.getGraphValues()){
            popularityDependency = Integer.parseInt(value.getValue().equals("NULL") ? "0" : value.getValue());
        }
        //return popularityDependency;
        return popularityDependency;
    }
}
