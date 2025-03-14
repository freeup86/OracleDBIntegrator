package com.dbintegrator.model;

import java.util.Objects;

public class TaskMapping {
    private Task sourceTask;
    private Task destTask;

    public TaskMapping(Task sourceTask, Task destTask) {
        this.sourceTask = sourceTask;
        this.destTask = destTask;
    }

    public Task getSourceTask() { return sourceTask; }
    public Task getDestTask() { return destTask; }

    @Override
    public String toString() {
        return sourceTask.getName() + " → " + destTask.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaskMapping that = (TaskMapping) o;
        return Objects.equals(sourceTask, that.sourceTask) &&
                Objects.equals(destTask, that.destTask);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceTask, destTask);
    }
}