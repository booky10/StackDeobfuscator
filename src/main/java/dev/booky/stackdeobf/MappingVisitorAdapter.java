package dev.booky.stackdeobf;
// Created by booky10 in StackDeobfuscator (16:41 20.03.23)

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingVisitor;

import java.io.IOException;
import java.util.List;

public abstract class MappingVisitorAdapter implements MappingVisitor {

    @Override
    public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {
    }

    @Override
    public boolean visitClass(String srcName) throws IOException {
        return true;
    }

    @Override
    public boolean visitField(String srcName, String srcDesc) throws IOException {
        return true;
    }

    @Override
    public boolean visitMethod(String srcName, String srcDesc) throws IOException {
        return true;
    }

    @Override
    public boolean visitMethodArg(int argPosition, int lvIndex, String srcName) throws IOException {
        return true;
    }

    @Override
    public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, String srcName) throws IOException {
        return true;
    }

    @Override
    public void visitDstName(MappedElementKind targetKind, int namespace, String name) throws IOException {
    }

    @Override
    public void visitComment(MappedElementKind targetKind, String comment) throws IOException {
    }
}
