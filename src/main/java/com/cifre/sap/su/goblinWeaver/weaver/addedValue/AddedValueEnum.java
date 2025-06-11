package com.cifre.sap.su.goblinWeaver.weaver.addedValue;

import com.cifre.sap.su.goblinWeaver.graphEntities.nodes.NodeType;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = AddedValueEnumDeserializer.class)
public enum AddedValueEnum {
    CVE,
    CVE_AGGREGATED,
    FRESHNESS,
    FRESHNESS_AGGREGATED,
    POPULARITY_1_YEAR,
    POPULARITY_1_YEAR_AGGREGATED,
    SPEED,
    GITHUB,
    POPULARITYDEPENDENCY,
    SBOMSYFTECRASE,
    SBOMSYFTALL,
    SBOMSYFT/*,
    SBOMSMP,
    SBOMSMPECRASE,
    SBOMSMPALL*/;

    public NodeType getTargetNodeType(){
        return switch (this) {
            case CVE, CVE_AGGREGATED, FRESHNESS, FRESHNESS_AGGREGATED, POPULARITY_1_YEAR, POPULARITY_1_YEAR_AGGREGATED, GITHUB, POPULARITYDEPENDENCY, SBOMSYFTECRASE, SBOMSYFTALL, SBOMSYFT/* , SBOMSMP, SBOMSMPECRASE, SBOMSMPALL*/ -> NodeType.RELEASE;
            case SPEED -> NodeType.ARTIFACT;
        };
    }

    public Class<? extends AddedValue<?>> getAddedValueClass(){
        return switch (this) {
            case CVE -> Cve.class;
            case CVE_AGGREGATED -> CveAggregated.class;
            case FRESHNESS -> Freshness.class;
            case FRESHNESS_AGGREGATED -> FreshnessAggregated.class;
            case POPULARITY_1_YEAR -> Popularity1Year.class;
            case POPULARITY_1_YEAR_AGGREGATED -> Popularity1YearAggregated.class;
            case SPEED -> Speed.class;
            case GITHUB -> Github.class;
            case POPULARITYDEPENDENCY -> PopularityDependency.class;
            case SBOMSYFTECRASE -> SbomSyftEcrase.class;
            case SBOMSYFT -> SbomSyft.class;
            case SBOMSYFTALL -> SbomSyftAll.class;
            //case SBOMSMPECRASE -> SbomSmpEcrase.class;
            //case SBOMSMP -> SbomSmp.class;
            //case SBOMSMPALL -> SbomSmpAll.class;
        };
    }

    public boolean isAggregatedValue(){
        return switch (this) {
            case CVE_AGGREGATED, FRESHNESS_AGGREGATED, POPULARITY_1_YEAR_AGGREGATED -> true;
            default -> false;
        };
    }

    public String getJsonKey(){
        return this.name().toLowerCase();
    }
}
