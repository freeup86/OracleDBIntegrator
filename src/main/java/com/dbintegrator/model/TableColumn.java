package com.dbintegrator.model;

public class TableColumn {
    private String name;
    private String dataType;
    private int size;
    private boolean nullable;

    public TableColumn(String name, String dataType, int size, boolean nullable) {
        this.name = name;
        this.dataType = dataType;
        this.size = size;
        this.nullable = nullable;
    }

    public String getName() {
        return name;
    }

    public String getDataType() {
        return dataType;
    }

    public int getSize() {
        return size;
    }

    public boolean isNullable() {
        return nullable;
    }

    @Override
    public String toString() {
        return name + " (" + dataType +
                (size > 0 ? "(" + size + ")" : "") +
                (nullable ? "" : ", NOT NULL") + ")";
    }
}