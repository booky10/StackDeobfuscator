package dev.booky.stackdeobf.mappings;
// Created by booky10 in StackDeobfuscator (15:08 23.03.23)

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingVisitor;

import java.util.List;
import java.util.Map;

public class MappingCacheVisitor implements MappingVisitor {

    private final Map<Integer, String> classes, methods, fields;

    private String srcClassName;
    private String srcMethodName;
    private String srcFieldName;

    public MappingCacheVisitor(Map<Integer, String> classes, Map<Integer, String> methods, Map<Integer, String> fields) {
        this.classes = classes;
        this.methods = methods;
        this.fields = fields;
    }

    @Override
    public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) {
    }

    @Override
    public boolean visitClass(String srcName) {
        this.srcClassName = srcName;
        return true;
    }

    @Override
    public boolean visitField(String srcName, String srcDesc) {
        this.srcFieldName = srcName;
        return true;
    }

    @Override
    public boolean visitMethod(String srcName, String srcDesc) {
        this.srcMethodName = srcName;
        return true;
    }

    @Override
    public boolean visitMethodArg(int argPosition, int lvIndex, String srcName) {
        return true;
    }

    @Override
    public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, String srcName) {
        return true;
    }

    @Override
    public void visitDstName(MappedElementKind targetKind, int namespace, String name) {
        switch (targetKind) {
            case CLASS -> {
                if (this.srcClassName.startsWith("net/minecraft/class_")) {
                    try {
                        int classId = Integer.parseInt(this.srcClassName.substring("net/minecraft/class_".length()));
                        this.classes.put(classId, name.replace('/', '.'));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            case METHOD -> {
                if (this.srcMethodName.startsWith("method_")) {
                    int methodId = Integer.parseInt(this.srcMethodName.substring("method_".length()));
                    this.methods.put(methodId, name);
                }
            }
            case FIELD -> {
                if (this.srcFieldName.startsWith("field_")) {
                    int fieldId = Integer.parseInt(this.srcFieldName.substring("field_".length()));
                    this.fields.put(fieldId, name);
                }
            }
        }
    }

    @Override
    public void visitComment(MappedElementKind targetKind, String comment) {
    }
}
