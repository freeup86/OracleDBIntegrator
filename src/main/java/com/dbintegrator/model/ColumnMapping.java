package com.dbintegrator.model;

public class ColumnMapping {
    private String sourceTable;
    private TableColumn sourceColumn;
    private String destinationTable;
    private TableColumn destinationColumn;

    public ColumnMapping(String sourceTable, TableColumn sourceColumn,
                         String destinationTable, TableColumn destinationColumn) {
        this.sourceTable = sourceTable;
        this.sourceColumn = sourceColumn;
        this.destinationTable = destinationTable;
        this.destinationColumn = destinationColumn;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public TableColumn getSourceColumn() {
        return sourceColumn;
    }

    public String getDestinationTable() {
        return destinationTable;
    }

    public TableColumn getDestinationColumn() {
        return destinationColumn;
    }

    @Override
    public String toString() {
        return sourceTable + "." + sourceColumn.getName() + " → " +
                destinationTable + "." + destinationColumn.getName();
    }
}